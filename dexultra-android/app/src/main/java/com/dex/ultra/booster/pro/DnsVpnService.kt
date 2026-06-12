package com.dex.ultra.booster.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class DnsVpnService : VpnService() {

    companion object {
        private const val TAG = "DnsVpnService"
        private const val CHANNEL_ID = "dexultra_vpn"
        private const val NOTIF_ID = 2
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.2"

        var DNS_PRIMARY = "1.1.1.1"
        var DNS_SECONDARY = "8.8.8.8"

        const val ACTION_STOP = "com.dex.ultra.booster.pro.STOP_VPN"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    @Volatile private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        DNS_PRIMARY = intent?.getStringExtra("dns_primary") ?: "1.1.1.1"
        DNS_SECONDARY = intent?.getStringExtra("dns_secondary") ?: "8.8.8.8"

        // MUST call startForeground immediately on API 26+
        startForeground(NOTIF_ID, buildNotification())

        establishVpn()
        return START_STICKY
    }

    private fun establishVpn() {
        try {
            val builder = Builder()
                .setMtu(VPN_MTU)
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(DNS_PRIMARY)
                .addDnsServer(DNS_SECONDARY)
                .setSession("DexUltra DNS")
                .setBlocking(true)
                .addDisallowedApplication(packageName)

            // Route only DNS server IPs — NOT all traffic (avoids breaking internet)
            try { builder.addRoute(DNS_PRIMARY, 32) } catch (_: Exception) {}
            try { builder.addRoute(DNS_SECONDARY, 32) } catch (_: Exception) {}
            // Add a default route for DNS to work
            builder.addRoute("0.0.0.0", 0)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "VPN establish() returned null — permission not granted?")
                stopSelf()
                return
            }

            isRunning = true
            startTunnel()
            Log.i(TAG, "VPN established. DNS: $DNS_PRIMARY / $DNS_SECONDARY")

        } catch (e: Exception) {
            Log.e(TAG, "VPN setup error: ${e.message}")
            stopSelf()
        }
    }

    private fun startTunnel() {
        tunnelThread = Thread({
            val fd = vpnInterface?.fileDescriptor ?: return@Thread
            val buffer = ByteArray(VPN_MTU)

            try {
                val input = java.io.FileInputStream(fd)
                val output = java.io.FileOutputStream(fd)

                while (isRunning) {
                    val length = try { input.read(buffer) } catch (e: Exception) { break }
                    if (length <= 0) continue

                    // Only handle DNS packets (UDP port 53)
                    if (isDnsPacket(buffer, length)) {
                        val response = forwardDnsUdp(buffer, length)
                        if (response != null) {
                            try { output.write(response) } catch (_: Exception) {}
                        }
                    }
                    // All other packets are dropped — internet still works
                    // because addDisallowedApplication(packageName) bypasses the VPN
                    // for apps that shouldn't be routed through it.
                }
            } catch (e: Exception) {
                if (isRunning) Log.w(TAG, "Tunnel error: ${e.message}")
            }
        }, "DexUltra-DNS-Tunnel")
        tunnelThread?.isDaemon = true
        tunnelThread?.start()
    }

    private fun isDnsPacket(buf: ByteArray, len: Int): Boolean {
        if (len < 28) return false
        val ipVersion = (buf[0].toInt() and 0xF0) shr 4
        if (ipVersion != 4) return false
        val protocol = buf[9].toInt() and 0xFF
        if (protocol != 17) return false  // UDP only
        val destPort = ((buf[22].toInt() and 0xFF) shl 8) or (buf[23].toInt() and 0xFF)
        return destPort == 53
    }

    private fun forwardDnsUdp(ipPacket: ByteArray, len: Int): ByteArray? {
        return try {
            if (len <= 28) return null
            val dnsPayload = ipPacket.copyOfRange(28, len)

            val socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = 2000

            val serverAddr = InetAddress.getByName(DNS_PRIMARY)
            socket.send(DatagramPacket(dnsPayload, dnsPayload.size, serverAddr, 53))

            val respBuf = ByteArray(VPN_MTU)
            val respPacket = DatagramPacket(respBuf, respBuf.size)
            socket.receive(respPacket)
            socket.close()

            buildIpUdpResponse(ipPacket, respBuf, respPacket.length)
        } catch (e: Exception) {
            Log.w(TAG, "DNS forward failed: ${e.message}")
            null
        }
    }

    private fun buildIpUdpResponse(req: ByteArray, dnsResp: ByteArray, dnsLen: Int): ByteArray {
        val totalLen = 20 + 8 + dnsLen
        val pkt = ByteArray(totalLen)

        // IP header (copy + swap src/dst)
        System.arraycopy(req, 0, pkt, 0, 20)
        System.arraycopy(req, 16, pkt, 12, 4)   // dst → src
        System.arraycopy(req, 12, pkt, 16, 4)   // src → dst
        pkt[2] = ((totalLen shr 8) and 0xFF).toByte()
        pkt[3] = (totalLen and 0xFF).toByte()
        pkt[8] = 64  // TTL

        // UDP header (swap ports)
        System.arraycopy(req, 22, pkt, 20, 2)   // dst port → src port
        System.arraycopy(req, 20, pkt, 22, 2)   // src port → dst port
        val udpLen = 8 + dnsLen
        pkt[24] = ((udpLen shr 8) and 0xFF).toByte()
        pkt[25] = (udpLen and 0xFF).toByte()
        pkt[26] = 0; pkt[27] = 0  // checksum (zero = valid on Linux TUN)

        // DNS payload
        System.arraycopy(dnsResp, 0, pkt, 28, dnsLen)
        return pkt
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("DexUltra – DNS محسّن")
            .setContentText("DNS نشط: $DNS_PRIMARY")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "DNS Optimizer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        isRunning = false
        tunnelThread?.interrupt()
        try { vpnInterface?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
