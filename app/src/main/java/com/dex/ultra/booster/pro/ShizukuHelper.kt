package com.dex.ultra.booster.pro

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private const val REQUEST_CODE = 1001

    /**
     * Check if Shizuku is available and permission is granted.
     */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku not available: ${e.message}")
            false
        }
    }

    /**
     * Check if Shizuku service is running (even without permission).
     */
    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Run a shell command via Shizuku (with elevated ADB-level permissions).
     * Returns stdout or null on failure.
     */
    fun runCommand(command: String): String? {
        if (!isAvailable()) return null
        return try {
            val process: ShizukuRemoteProcess = Shizuku.newProcess(
                arrayOf("sh", "-c", command), null, null
            )
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val exitCode = process.exitValue()
            if (exitCode == 0) output.trim() else {
                val err = process.errorStream.bufferedReader().readText()
                Log.e(TAG, "Command failed (exit $exitCode): $err")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku command error: ${e.message}")
            null
        }
    }

    /**
     * Copy a file into a protected path using Shizuku (ADB-level cp).
     */
    fun copyFile(sourcePath: String, destPath: String): Boolean {
        val result = runCommand("cp -f \"$sourcePath\" \"$destPath\" && chmod 660 \"$destPath\"")
        return result != null
    }

    /**
     * Write content to a protected file path.
     */
    fun writeProtectedFile(destPath: String, content: String): Boolean {
        // Write via echo through Shizuku shell
        val escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("`", "\\`")
        val cmd = "echo \"$escaped\" > \"$destPath\" && chmod 660 \"$destPath\""
        return runCommand(cmd) != null
    }

    /**
     * Write binary/multiline content via a temp file approach.
     * Writes to /data/local/tmp first, then moves to protected path.
     */
    fun writeFileViaTmp(destPath: String, content: String): Boolean {
        val tmpPath = "/data/local/tmp/dexultra_tmp_${System.currentTimeMillis()}"
        return try {
            // Write to accessible tmp
            val tmpFile = java.io.File(tmpPath)
            tmpFile.writeText(content)

            // Move via Shizuku
            val result = runCommand("cp -f \"$tmpPath\" \"$destPath\" && chmod 660 \"$destPath\" && rm -f \"$tmpPath\"")
            result != null
        } catch (e: Exception) {
            Log.e(TAG, "writeFileViaTmp failed: ${e.message}")
            false
        }
    }

    /**
     * Read a protected file content via Shizuku.
     */
    fun readProtectedFile(path: String): String? {
        return runCommand("cat \"$path\"")
    }

    /**
     * List directory contents via Shizuku.
     */
    fun listDirectory(path: String): List<String> {
        val output = runCommand("ls \"$path\"") ?: return emptyList()
        return output.lines().filter { it.isNotBlank() }
    }

    /**
     * Check if a path exists via Shizuku.
     */
    fun pathExists(path: String): Boolean {
        return runCommand("[ -e \"$path\" ] && echo 1 || echo 0") == "1"
    }

    /**
     * Get the PUBG data directory based on package name.
     */
    fun getPubgDataPath(packageName: String): String {
        return "/data/data/$packageName"
    }

    /**
     * Known PUBG package names across all versions.
     */
    val PUBG_PACKAGES = mapOf(
        0 to "com.tencent.ig",               // Global
        1 to "com.pubg.krmobile",             // KR
        2 to "com.vng.pubgmobile",            // VN
        3 to "com.pubg.imobile",              // BGMI
        4 to "com.tencent.iglite"             // Lite
    )
}
