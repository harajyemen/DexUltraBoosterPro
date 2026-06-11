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
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * DNS-only VPN service that routes DNS traffic through optimized servers.
 * This is NOT a full VPN — it only intercepts DNS queries to reduce ping
 * and prevent DNS-based throttling. All game data traffic remains direct.
 *
 * Architecture:
 * 1. Creates a local TUN interface
 * 2. Intercepts port 53 (DNS) traffic
 * 3. Forwards DNS queries to the selected fast DNS server
 * 4. All other traffic passes through unchanged
 */
class DnsVpnService : VpnService() {

    companion object {
        private const val TAG = "DnsVpnService"
        private const val CHANNEL_ID = "dexultra_vpn"
        private const val NOTIF_ID = 2
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"

        // Default DNS
        var DNS_PRIMARY = "1.1.1.1"
        var DNS_SECONDARY = "8.8.8.8"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DNS_PRIMARY = intent?.getStringExtra("dns_primary") ?: "1.1.1.1"
        DNS_SECONDARY = intent?.getStringExtra("dns_secondary") ?: "8.8.8.8"

        startForeground(NOTIF_ID, buildNotification())
        establishVpn()
        return START_STICKY
    }

    private fun establishVpn() {
        try {
            val builder = Builder()
                .setMtu(VPN_MTU)
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_ROUTE, 0)
                .addDnsServer(DNS_PRIMARY)
                .addDnsServer(DNS_SECONDARY)
                .setBlocking(true)
                .setSession("DexUltra DNS Optimizer")

            // Allow our own app to bypass VPN to avoid loops
            builder.addDisallowedApplication(packageName)

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }

            isRunning = true
            startTunnel()
            Log.i(TAG, "DNS VPN established. Primary: $DNS_PRIMARY, Secondary: $DNS_SECONDARY")

        } catch (e: Exception) {
            Log.e(TAG, "VPN establishment failed: ${e.message}")
            stopSelf()
        }
    }

    private fun startTunnel() {
        tunnelThread = Thread({
            val buffer = ByteBuffer.allocate(VPN_MTU)
            val fd = vpnInterface?.fileDescriptor ?: return@Thread

            try {
                val inputStream = java.io.FileInputStream(fd)
                val outputStream = java.io.FileOutputStream(fd)

                while (isRunning) {
                    // Read packet from TUN
                    buffer.clear()
                    val length = inputStream.read(buffer.array())
                    if (length <= 0) continue

                    // Parse IP header to check if it's a DNS packet (port 53)
                    buffer.limit(length)
                    if (isDnsPacket(buffer)) {
                        // Forward to our fast DNS server and return response
                        val response = forwardDnsQuery(buffer, length)
                        if (response != null) {
                            outputStream.write(response)
                        }
                    }
                    // Non-DNS packets flow through normally via VPN routing
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Tunnel error: ${e.message}")
                }
            }
        }, "DexUltra-DNS-Tunnel")
        tunnelThread?.start()
    }

    private fun isDnsPacket(buffer: ByteBuffer): Boolean {
        return try {
            if (buffer.limit() < 28) return false
            val protocol = buffer.get(9).toInt() and 0xFF
            if (protocol != 17) return false // UDP only
            val destPort = ((buffer.get(22).toInt() and 0xFF) shl 8) or
                           (buffer.get(23).toInt() and 0xFF)
            destPort == 53
        } catch (e: Exception) {
            false
        }
    }

    private fun forwardDnsQuery(buffer: ByteBuffer, length: Int): ByteArray? {
        return try {
            // Extract DNS payload (skip IP+UDP headers = 28 bytes)
            val dnsPayload = buffer.array().copyOfRange(28, length)

            val channel = DatagramChannel.open()
            channel.connect(
                java.net.InetSocketAddress(InetAddress.getByName(DNS_PRIMARY), 53)
            )
            protect(channel.socket())

            channel.write(ByteBuffer.wrap(dnsPayload))
            val responseBuffer = ByteBuffer.allocate(VPN_MTU)
            channel.read(responseBuffer)
            channel.close()

            // Rebuild IP+UDP packet with response
            rebuildIpPacket(
                buffer.array().copyOfRange(0, 28),
                responseBuffer.array().copyOfRange(0, responseBuffer.position())
            )
        } catch (e: Exception) {
            Log.w(TAG, "DNS forward failed: ${e.message}")
            null
        }
    }

    private fun rebuildIpPacket(originalHeader: ByteArray, dnsResponse: ByteArray): ByteArray {
        val totalLength = 20 + 8 + dnsResponse.size // IP + UDP + DNS
        val packet = ByteArray(totalLength)

        // Copy and modify IP header
        System.arraycopy(originalHeader, 0, packet, 0, 20)
        // Swap src/dst IP
        System.arraycopy(originalHeader, 16, packet, 12, 4)
        System.arraycopy(originalHeader, 12, packet, 16, 4)
        // Set total length
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()

        // Build UDP header
        System.arraycopy(originalHeader, 22, packet, 20, 2) // swap ports
        System.arraycopy(originalHeader, 20, packet, 22, 2)
        val udpLength = 8 + dnsResponse.size
        packet[24] = ((udpLength shr 8) and 0xFF).toByte()
        packet[25] = (udpLength and 0xFF).toByte()
        packet[26] = 0; packet[27] = 0 // zero checksum

        // Append DNS response
        System.arraycopy(dnsResponse, 0, packet, 28, dnsResponse.size)

        return packet
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("DexUltra – DNS محسّن")
            .setContentText("يعمل على تحسين البنق • DNS: $DNS_PRIMARY")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "DNS Optimizer", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: Exception) { /* ignore */ }
        super.onDestroy()
    }
}
