package com.dex.ultra.booster.pro

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    const val REQUEST_CODE = 1001

    val PUBG_PACKAGES = mapOf(
        0 to "com.tencent.ig",
        1 to "com.pubg.krmobile",
        2 to "com.vng.pubgmobile",
        3 to "com.pubg.imobile",
        4 to "com.tencent.iglite"
    )

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun isRunning(): Boolean {
        return try { Shizuku.pingBinder() } catch (e: Exception) { false }
    }

    fun isRootAvailable(): Boolean {
        return try { Shell.getShell().isRoot } catch (e: Exception) { false }
    }

    fun requestPermission() {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestPermission error: ${e.message}")
        }
    }

    /**
     * Run command — tries Shizuku (ADB-level, no root) first, then libsu (root).
     */
    fun runCommand(command: String): String? {
        if (isAvailable()) {
            val result = runViaShizukuReflection(command)
            if (result != null) return result
        }
        return try {
            val r = Shell.cmd(command).exec()
            if (r.isSuccess) r.out.joinToString("\n").trim() else null
        } catch (e: Exception) {
            Log.w(TAG, "libsu failed: ${e.message}")
            null
        }
    }

    fun writeFileViaTmp(destPath: String, content: String): Boolean {
        val tmpPath = "/data/local/tmp/dux_${System.currentTimeMillis()}.tmp"
        val escapedContent = content.replace("'", "'\\''")
        val writeCmd = "printf '%s' '$escapedContent' > '$tmpPath'"
        val copyCmd = "cp -f '$tmpPath' '$destPath' && chmod 644 '$destPath' && rm -f '$tmpPath'"

        if (runCommand(writeCmd) != null) {
            return runCommand(copyCmd) != null
        }
        // Fallback: line-by-line echo
        val lines = content.lines()
        val sb = StringBuilder()
        lines.forEachIndexed { i, line ->
            val esc = line.replace("\"", "\\\"")
            if (i == 0) sb.append("echo \"$esc\" > '$tmpPath'")
            else sb.append(" && echo \"$esc\" >> '$tmpPath'")
        }
        if (runCommand(sb.toString()) != null) {
            return runCommand(copyCmd) != null
        }
        return false
    }

    fun pathExists(path: String): Boolean {
        return runCommand("[ -e \"$path\" ] && echo yes || echo no") == "yes"
    }

    fun getPubgDataPath(packageName: String) = "/data/data/$packageName"

    // ── Internal ──────────────────────────────────────────────────────────

    private fun runViaShizukuReflection(command: String): String? {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null as Array<String>?,
                null as String?
            ) as Process
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            Log.d(TAG, "Shizuku[$exitCode]: $command")
            if (exitCode == 0) stdout.ifEmpty { "" } else null
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "Shizuku.newProcess not found in this version")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku reflection error: ${e.message}")
            null
        }
    }
}
