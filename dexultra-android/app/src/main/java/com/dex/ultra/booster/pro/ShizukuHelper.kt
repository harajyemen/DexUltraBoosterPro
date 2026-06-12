package com.dex.ultra.booster.pro

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.File

object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    const val REQUEST_CODE = 1001

    // قائمة جميع نسخ لعبة ببجي ومعرفاتها (Package Names)
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
        } catch (e: Exception) { false }
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
            Log.w(TAG, "requestPermission error: ${e.message}")
        }
    }

    /**
     * تنفيذ الأوامر عبر Shizuku بصلاحيات ADB بدون روت
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
            Log.w(TAG, "Shizuku error: ${e.message}")
            null
        }
    }

    /**
     * دالة جلب المسار الكامل لملف UserCustom.ini لأي نسخة ببجي تختارها
     */
    fun getUserCustomPath(packageName: String): String {
        return "/storage/emulated/0/Android/data/$packageName/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android/UserCustom.ini"
    }

    /**
     * دالة جلب المسار الكامل لملف Active.sav لأي نسخة ببجي تختارها
     */
    fun getActiveSavPath(packageName: String): String {
        return "/storage/emulated/0/Android/data/$packageName/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/SaveGames/Active.sav"
    }

    /**
     * دالة فحص وتأكيد وجود مجلدات اللعبة الأساسية قبل البدء بالحقن
     */
    fun prepareDirectory(destPath: String): Boolean {
        val parentDir = destPath.substring(0, destPath.lastIndexOf("/"))
        val mkdirCmd = "mkdir -p '$parentDir'"
        return runCommand(mkdirCmd) != null
    }

    /**
     * دالة حقن الملف النصي (UserCustom.ini) أو ملف البيانات (Active.sav) بدون روت
     */
    fun injectFileWithoutRoot(destPath: String, content: String): Boolean {
        // التأكد من إنشاء المجلدات الفرعية أولاً لضمان عدم فشل أمر النسخ
        if (!prepareDirectory(destPath)) return false

        val tmpPath = "/data/local/tmp/pubg_inject_${System.currentTimeMillis()}.tmp"

        // ترميز الرموز الخاصة لتفادي تعطل سطر الأوامر أثناء النقل
        val escapedContent = content.replace("\\", "\\\\").replace("'", "'\\''")
        val writeCmd = "printf '%s' '$escapedContent' > '$tmpPath'"
        val cpCmd   = "cp -f '$tmpPath' '$destPath' && chmod 660 '$destPath' && rm -f '$tmpPath'"

        if (runCommand(writeCmd) != null && runCommand(cpCmd) != null) {
            return true
        }

        // حل بديل (Fallback) خطوة بخطوة في حال كانت الملفات تحتوي على نصوص ضخمة أو معقدة
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
     * دالة فحص ما إذا كانت النسخة المحددة مثبتة على جهاز المستخدم أم لا
     */
    fun isPackageInstalled(packageName: String): Boolean {
        val result = runCommand("pm path $packageName")
        return !result.isNullOrEmpty()
    }

    /**
     * دالة رئيسية لتطبيق الملفين معاً بضغطة زر واحدة للنسخة المختارة
     */
    fun applyFps120Booster(packageIndex: Int, userCustomContent: String, activeSavContent: String, onResult: (Boolean, String) -> Unit) {
        val packageName = PUBG_PACKAGES[packageIndex]
        
        if (packageName == null) {
            onResult(false, "نسخة اللعبة غير معروفة")
            return
        }

        if (!isAvailable()) {
            requestPermission()
            onResult(false, "الرجاء تفعيل صلاحيات Shizuku أولاً")
            return
        }

        if (!isPackageInstalled(packageName)) {
            onResult(false, "هذه النسخة من اللعبة غير مثبتة على جهازك")
            return
        }

        // جلب المسارات تلقائياً للنسخة المستهدفة
        val customIniPath = getUserCustomPath(packageName)
        val activeSavPath = getActiveSavPath(packageName)

        // البدء بعملية الحقن للملف الأول
        val successIni = injectFileWithoutRoot(customIniPath, userCustomContent)
        // البدء بعملية الحقن للملف الثاني
        val successSav = injectFileWithoutRoot(activeSavPath, activeSavContent)

        if (successIni && successSav) {
            onResult(true, "تم حقن ملفات الـ 120 FPS بنجاح وبدون روت!")
        } else {
            onResult(false, "فشل الحقن، تأكد من إعدادات وتوصيل Shizuku")
        }
    }
}
