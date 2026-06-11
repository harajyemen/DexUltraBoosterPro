package com.dex.ultra.booster.pro

import android.content.pm.PackageManager
import android.util.Log
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    const val REQUEST_CODE = 1001

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
     * Request Shizuku permission if not yet granted.
     */
    fun requestPermission() {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku requestPermission error: ${e.message}")
        }
    }

    /**
     * Run a shell command using libsu (root shell).
     * Falls back to a non-root shell for non-destructive reads.
     * Returns stdout or null on failure.
     */
    fun runCommand(command: String): String? {
        return try {
            val result = Shell.cmd(command).exec()
            if (result.isSuccess) {
                result.out.joinToString("\n").trim()
            } else {
                val err = result.err.joinToString("\n")
                Log.e(TAG, "Command failed: $err")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shell command error: ${e.message}")
            null
        }
    }

    /**
     * Copy a file into a protected path using elevated shell.
     */
    fun copyFile(sourcePath: String, destPath: String): Boolean {
        val result = runCommand("cp -f \"$sourcePath\" \"$destPath\" && chmod 660 \"$destPath\"")
        return result != null
    }

    /**
     * Write content to a protected file path via tmp.
     */
    fun writeFileViaTmp(destPath: String, content: String): Boolean {
        val tmpPath = "/data/local/tmp/dexultra_tmp_${System.currentTimeMillis()}"
        return try {
            val tmpFile = java.io.File(tmpPath)
            tmpFile.writeText(content)
            val result = runCommand(
                "cp -f \"$tmpPath\" \"$destPath\" && chmod 660 \"$destPath\" && rm -f \"$tmpPath\""
            )
            result != null
        } catch (e: Exception) {
            Log.e(TAG, "writeFileViaTmp failed: ${e.message}")
            false
        }
    }

    /**
     * Write content to a protected file path using shell.
     */
    fun writeProtectedFile(destPath: String, content: String): Boolean {
        return writeFileViaTmp(destPath, content)
    }

    /**
     * Read a protected file content via elevated shell.
     */
    fun readProtectedFile(path: String): String? {
        return runCommand("cat \"$path\"")
    }

    /**
     * List directory contents via elevated shell.
     */
    fun listDirectory(path: String): List<String> {
        val output = runCommand("ls \"$path\"") ?: return emptyList()
        return output.lines().filter { it.isNotBlank() }
    }

    /**
     * Check if a path exists.
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
        0 to "com.tencent.ig",
        1 to "com.pubg.krmobile",
        2 to "com.vng.pubgmobile",
        3 to "com.pubg.imobile",
        4 to "com.tencent.iglite"
    )
}
