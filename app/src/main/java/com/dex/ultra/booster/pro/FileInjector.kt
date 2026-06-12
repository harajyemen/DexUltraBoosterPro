package com.dex.ultra.booster.pro

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileInjector {

    private const val TAG = "FileInjector"

    /**
     * نقطة الدخول الرئيسية: حقن ملفات UserCustom.ini و Active.sav لـ 120 إطاراً
     * داخل مسارات اللعبة المتاحة بدون روت عبر بيئة Shizuku.
     *
     * @param context       سياق التطبيق
     * @param versionIndex  0=العالمية, 1=الكورية, 2=الفيتنامية, 3=الهندية, 4=لايت
     * @param useShizuku    استخدام Shizuku لتخطي حماية أندرويد 11+ بدون روت
     */
    suspend fun inject120FpsFiles(
        context: Context,
        versionIndex: Int,
        useShizuku: Boolean,
        targetFps: Int = 120 // تمت إضافة قيمة افتراضية لضمان التوافق مع استدعاءات MainActivity المحدثة
    ): Boolean = withContext(Dispatchers.IO) {

        val pkg = ShizukuHelper.PUBG_PACKAGES[versionIndex]
            ?: return@withContext false

        // تحديد المسارات الصحيحة داخل مجلد Android/data بدون روت بالكامل
        val baseConfigDir = "/storage/emulated/0/Android/data/$pkg/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android"
        val baseSaveGamesDir = "/storage/emulated/0/Android/data/$pkg/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/SaveGames"

        // التحقق من توافر Shizuku
        if (useShizuku && !ShizukuHelper.isAvailable()) {
            Log.w(TAG, "Shizuku requested but not available")
        }

        var success = false

        // --- 1. توليد وحقن ملف الإعدادات UserCustom.ini ---
        val userCustomContent = generateUserCustomIni()
        val customIniPath = "$baseConfigDir/UserCustom.ini"

        val iniResult = if (useShizuku && ShizukuHelper.isAvailable()) {
            ShizukuHelper.injectFileWithoutRoot(customIniPath, userCustomContent)
        } else {
            writeToExternalFallback(context, "UserCustom.ini", userCustomContent)
        }

        if (iniResult) {
            Log.i(TAG, "UserCustom.ini injected to $customIniPath")
        }

        // --- 2. توليد وحقن ملف الحفظ والدقة Active.sav ---
        val activeSavContent = generateActiveSav()
        val activeSavPath = "$baseSaveGamesDir/Active.sav"

        val savResult = if (useShizuku && ShizukuHelper.isAvailable()) {
            ShizukuHelper.injectFileWithoutRoot(activeSavPath, activeSavContent)
        } else {
            writeToExternalFallback(context, "Active.sav", activeSavContent)
        }

        if (savResult) {
            Log.i(TAG, "Active.sav injected to $activeSavPath")
        }

        // نجاح العملية الكلية يعتمد على حقن كلا الملفين معاً بنجاح
        if (iniResult && savResult) {
            success = true
            
            // --- 3. ضبط صلاحيات الملفات عبر Shizuku لضمان قراءة اللعبة لها ---
            if (useShizuku && ShizukuHelper.isAvailable()) {
                ShizukuHelper.runCommand("chmod 660 '$customIniPath' '$activeSavPath' 2>/dev/null; true")
            }
        }

        return@withContext success
    }

    /**
     * توليد محتوى ملف UserCustom.ini المخصص لـ 120 إطاراً مع إعدادات تقليل اللغ والتقطيع.
     */
    private fun generateUserCustomIni(): String {
        return """
[UserCustom]
IsHighFrameRateVersion=True
FrameRateSetting=4
FrameRateSettingEx=4
ShadowQuality=0
AntiAliasingQuality=0
TextureQuality=3
EffectsQuality=1
FoliageQuality=0
PostProcessQuality=0
ViewDistanceQuality=3
IsAutoQualityOpen=False
IsGraphicStyleOpen=False
GraphicStyle=0
ColorTemperature=0
IsHighPrecisionShadow=False
IsRealTimeShadow=False
IsVehicleReflection=False
IsUseHDRForRenderingTarget=False
IsUseEnvCapture=False
IsAllowHiResScreenshot=False
IsForceUseCustomSettings=True
IsHdrScreenshot=False
IsAntiAliasing=False
IsBloom=False
IsMotionBlur=False
IsLensFlare=False
RagdollQuality=0
FurQuality=0
GrassQuality=0
FoliageShadows=0
DynamicAO=0
ScreenParticleQuality=0
ExplosionQuality=0
IsScreenSpaceReflection=False
DisplayRefreshRate=120
TargetFrameRate=120
CustomFrameRate=120
bIsHighFrameRate=True
bIsHighFrameRateAllow=True
GFXLevel=Ultra
GraphicsPreset=5
        """.trimIndent()
    }

    /**
     * توليد محتوى ملف Active.sav لتعديل البصمة التعريفية للجهاز لإجبار خوادم اللعبة على تفعيل الـ 120 فريم.
     */
    private fun generateActiveSav(): String {
        return """
[Device]
DeviceModel=Samsung Galaxy S24 Ultra
Manufacturer=Samsung
DeviceID=SM-S928B
SupportFrameRate120=1
SupportFrameRate90=1
SupportFrameRate60=1
MaxSupportedFrameRate=120
DevicePerformanceLevel=4
GPU=Adreno 750
CPU=Snapdragon 8 Gen 3
RAM=12288
ScreenWidth=3088
ScreenHeight=1440
DPI=500
[Settings]
FrameRate=120
GraphicsLevel=Ultra
HDR=1
AntiAliasing=0
Shadows=0
Effects=1
FoliageOff=1
AutoFPS=0
        """.trimIndent()
    }

    /**
     * المسار البديل والاحتياطي: في حال تعطل Shizuku أو عدم منحه الصلاحيات، يتم حفظ الملفات
     * في مجلد التطبيق الخارجي ليتسنى للمستخدم نقلها يدوياً للمسار المناسب.
     */
    private fun writeToExternalFallback(
        context: Context,
        filename: String,
        content: String
    ): Boolean {
        return try {
            val dir = context.getExternalFilesDir("DexUltra_Output")
                ?: return false
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, filename)
            file.writeText(content)
            Log.i(TAG, "Saved $filename to ${file.absolutePath} (manual copy required)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fallback write failed: ${e.message}")
            false
        }
    }

    /**
     * جلب مسار مجلد الحفظ الاحتياطي (عند تعطل الحقن التلقائي).
     */
    fun getOutputDirectory(context: Context): String {
        return context.getExternalFilesDir("DexUltra_Output")?.absolutePath ?: ""
    }
}
