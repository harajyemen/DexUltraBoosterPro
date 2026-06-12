package com.dex.ultra.booster.pro

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    const val REQUEST_CODE = 1001

    // قائمة جميع نسخ لعبة ببجي ومعرفاتها الرسمية
    val PUBG_PACKAGES = mapOf(
        0 to "com.tencent.ig",        // العالمية (Global)
        1 to "com.pubg.krmobile",     // الكورية (Korea)
        2 to "com.vng.pubgmobile",    // الفيتنامية (Vietnam)
        3 to "com.pubg.imobile",      // الهندية (BGMI)
        4 to "com.tencent.iglite"     // لايت (Lite)
    )

    /**
     * التحقق من أن بيئة Shizuku متصلة ومصرح لها بالعمل
     */
    fun isAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { 
            Log.w(TAG, "Shizuku not available: ${e.message}")
            false 
        }
    }

    /**
     * التحقق من تشغيل خدمة Shizuku في الخلفية
     */
    fun isRunning(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try { Shizuku.pingBinder() } catch (e: Exception) { false }
    }

    /**
     * طلب صلاحيات الـ Shizuku من المستخدم إذا لم تكن ممنوحة
     */
    fun requestPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku requestPermission error: ${e.message}")
        }
    }

    /**
     * تنفيذ الأوامر عبر Shizuku بصلاحيات سطر أوامر ADB (بدون روت)
     */
    fun runCommand(command: String): String? {
        if (!isAvailable()) return null
        return try {
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", command),
                null,
                null
            )
            val stdout   = process.inputStream.bufferedReader().readText().trim()
            val stderr   = process.errorStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (stderr.isNotEmpty()) Log.d(TAG, "Shizuku stderr: $stderr")
            Log.d(TAG, "Shizuku[$exitCode]: $command")
            
            if (exitCode == 0) stdout.ifEmpty { "" } else null
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku runCommand error: ${e.message}")
            null
        }
    }

    /**
     * دالة فحص وتأكيد وجود مجلدات اللعبة الأساسية قبل البدء بالحقن
     */
    private fun prepareDirectory(destPath: String): Boolean {
        val parentDir = destPath.substring(0, destPath.lastIndexOf("/"))
        val mkdirCmd = "mkdir -p '$parentDir'"
        return runCommand(mkdirCmd) != null
    }

    /**
     * دالة حقن الملفات النصية والبيانات ومزامنتها مباشرة بدون روت للعبة
     */
    fun injectFileWithoutRoot(destPath: String, content: String): Boolean {
        // التأكد من بناء مسار المجلد التلقائي للعبة أولاً
        if (!prepareDirectory(destPath)) return false

        val tmpPath = "/data/local/tmp/dexultra_tmp_${System.currentTimeMillis()}"

        // ترميز الرموز لتجنب مشاكل علامات التنصيص والأقواس في الأوامر
        val escapedContent = content.replace("\\", "\\\\").replace("'", "'\\''")
        val writeCmd = "printf '%s' '$escapedContent' > '$tmpPath'"
        val cpCmd   = "cp -f '$tmpPath' '$destPath' && chmod 660 '$destPath' && rm -f '$tmpPath'"

        if (runCommand(writeCmd) != null && runCommand(cpCmd) != null) {
            return true
        }

        // حل احتياطي (Fallback) في حال تعطل أمر الطابعة للملفات الطويلة
        val sb = StringBuilder()
        content.lines().forEachIndexed { i, line ->
            val esc = line.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`").replace("$", "\\$")
            if (i == 0) sb.append("printf '%s\\n' \"$esc\" > '$tmpPath'")
            else sb.append(" && printf '%s\\n' \"$esc\" >> '$tmpPath'")
        }
        
        if (runCommand(sb.toString()) != null && runCommand(cpCmd) != null) {
            return true
        }

        return false
    }

    /**
     * دالة متوافقة مع استدعاءات الكود القديم لضمان عدم حدوث خطأ أثناء البناء (Compilation)
     */
    fun writeFileViaTmp(destPath: String, content: String): Boolean {
        return injectFileWithoutRoot(destPath, content)
    }

    /**
     * التحقق مما إذا كان الملف أو المجلد موجوداً داخل النظام
     */
    fun pathExists(path: String): Boolean {
        return runCommand("[ -e \"$path\" ] && echo 1 || echo 0") == "1"
    }

    /**
     * جلب مسار الحفظ المتوافق مع التعديل بدون روت
     */
    fun getPubgDataPath(packageName: String): String {
        return "/storage/emulated/0/Android/data/$packageName/files"
    }
}
