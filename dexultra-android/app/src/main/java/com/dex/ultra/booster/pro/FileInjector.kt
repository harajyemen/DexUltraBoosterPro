package com.dex.ultra.booster.pro

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileInjector {

    private const val TAG = "FileInjector"

    /**
     * حقن ملفات UserCustom.ini + Active.sav داخل نسخة ببجي المحددة بدون روت.
     * استراتيجية العمل:
     *   1. الكتابة المباشرة إذا كان الهاتف بنظام أندرويد قديم أو ممتلكاً لصلاحية MANAGE_EXTERNAL_STORAGE.
     *   2. استخدام بيئة Shizuku (صلاحيات ADB) لتخطي قيود أندرويد 11+ وحقن الملفات في مسارات اللعبة مباشرة.
     *   3. حفظ الملفات في مجلد التطبيق الخارجي كخيار احتياطي متاح للمستخدم لنقلها يدوياً.
     *
     * @param versionIndex  0=العالمية, 1=الكورية, 2=الفيتنامية, 3=الهندية, 4=لايت
     * @param targetFps     60 | 90 | 120 | 144
     */
    suspend fun inject120FpsFiles(
        context: Context,
        versionIndex: Int,
        useShizuku: Boolean,
        targetFps: Int = 120
    ): Boolean = withContext(Dispatchers.IO) {

        val pkg = ShizukuHelper.PUBG_PACKAGES[versionIndex] ?: return@withContext false
        val iniContent = generateUserCustomIni(targetFps)
        val savContent = generateActiveSav(targetFps)

        // تحديد المسارات الصحيحة داخل مجلدات اللعبة لجميع النسخ
        val baseConfigDir = "/storage/emulated/0/Android/data/$pkg/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android"
        val baseSaveGamesDir = "/storage/emulated/0/Android/data/$pkg/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/SaveGames"

        // ── الاستراتيجية 1: الكتابة المباشرة (للأنظمة القديمة أو عند منح صلاحية الوصول لجميع الملفات) ─────────
        if (writeSdcardDirectly(baseConfigDir, baseSaveGamesDir, iniContent, savContent)) {
            Log.i(TAG, "✅ Injected directly via Storage File API")
            return@withContext true
        }

        // ── الاستراتيجية 2: الحقن الذكي عبر Shizuku (تخطي قيود أندرويد 11+ بدون روت) ──────────────────────────────
        if (useShizuku && ShizukuHelper.isAvailable()) {
            Log.i(TAG, "🔄 Attempting Shizuku injection for package: $pkg")

            // 1. إنشاء المجلدات الفرعية للعبة أولاً لضمان عدم فشل عمليات النقل
            ShizukuHelper.runCommand("mkdir -p '$baseConfigDir' '$baseSaveGamesDir' 2>/dev/null; true")

            // 2. حقن ملف الإعدادات UserCustom.ini
            val r1 = ShizukuHelper.injectFileWithoutRoot("$baseConfigDir/UserCustom.ini", iniContent)

            // 3. حقن ملف الحفظ Active.sav
            val r2 = ShizukuHelper.injectFileWithoutRoot("$baseSaveGamesDir/Active.sav", savContent)

            if (r1 && r2) {
                // ضبط صلاحيات الملفات لتتمكن اللعبة من قراءتها وتطبيق الـ 120 فريم
                ShizukuHelper.runCommand("chmod 660 '$baseConfigDir/UserCustom.ini' '$baseSaveGamesDir/Active.sav' 2>/dev/null; true")
                Log.i(TAG, "✅ Injected successfully via Shizuku (No-Root)")
                return@withContext true
            }
        }

        // ── الاستراتيجية 3: مجلد الحفظ الاحتياطي (في حال فشل الطرق السابقة بالكامل) ─
        val fallbackDir = context.getExternalFilesDir("DexUltra_Output")
        if (fallbackDir != null) {
            fallbackDir.mkdirs()
            try {
                File(fallbackDir, "UserCustom.ini").writeText(iniContent)
                File(fallbackDir, "Active.sav").writeText(savContent)
                Log.i(TAG, "⚠️ Files saved to ${fallbackDir.absolutePath} — copy manually if needed")
            } catch (e: Exception) {
                Log.w(TAG, "Fallback write failed: ${e.message}")
            }
        }

        return@withContext false
    }

    /**
     * دالة مساعدة للكتابة المباشرة عبر الـ Storage API التقليدي
     */
    private fun writeSdcardDirectly(configPath: String, savePath: String, iniContent: String, savContent: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) return false
            }
            
            val configDir = File(configPath)
            val saveDir = File(savePath)
            
            if (!configDir.exists()) configDir.mkdirs()
            if (!saveDir.exists()) saveDir.mkdirs()
            
            if (configDir.canWrite() && saveDir.canWrite()) {
                File(configDir, "UserCustom.ini").writeText(iniContent)
                File(saveDir, "Active.sav").writeText(savContent)
                return true
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Direct storage write failed: ${e.message}")
            false
        }
    }

    fun getOutputDirectory(context: Context): String =
        context.getExternalFilesDir("DexUltra_Output")?.absolutePath ?: ""

    // ── مولدات نصوص الملفات الداعمة للـ 120 إطار ────────────────────────────────────────────────

    fun generateUserCustomIni(fps: Int = 120): String {
        val fpsValue = fps.coerceIn(30, 144)
        val frameSetting = when {
            fpsValue >= 120 -> 4
            fpsValue >= 90  -> 3
            fpsValue >= 60  -> 2
            else            -> 1
        }
        return """
[UserCustom]
IsHighFrameRateVersion=${fpsValue >= 90}
FrameRateSetting=$frameSetting
FrameRateSettingEx=$frameSetting
DisplayRefreshRate=$fpsValue
TargetFrameRate=$fpsValue
CustomFrameRate=$fpsValue
bIsHighFrameRate=${fpsValue >= 90}
bIsHighFrameRateAllow=True
IsForceUseCustomSettings=True
IsAutoQualityOpen=False
IsGraphicStyleOpen=False
ShadowQuality=0
AntiAliasingQuality=0
TextureQuality=3
EffectsQuality=1
FoliageQuality=0
PostProcessQuality=0
ViewDistanceQuality=3
GraphicStyle=0
ColorTemperature=0
IsHighPrecisionShadow=False
IsRealTimeShadow=False
IsVehicleReflection=False
IsUseHDRForRenderingTarget=False
IsUseEnvCapture=False
IsAllowHiResScreenshot=False
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
GFXLevel=Ultra
GraphicsPreset=5
        """.trimIndent()
    }

    private fun generateActiveSav(fps: Int = 120): String {
        return """
[Device]
DeviceModel=Samsung Galaxy S24 Ultra
Manufacturer=Samsung
DeviceID=SM-S928B
SupportFrameRate120=${if (fps >= 120) 1 else 0}
SupportFrameRate90=${if (fps >= 90) 1 else 0}
SupportFrameRate60=1
MaxSupportedFrameRate=$fps
DevicePerformanceLevel=4
GPU=Adreno 750
CPU=Snapdragon 8 Gen 3
RAM=12288
ScreenWidth=3088
ScreenHeight=1440
DPI=500
[Settings]
FrameRate=$fps
GraphicsLevel=Ultra
HDR=1
AntiAliasing=0
Shadows=0
Effects=1
FoliageOff=1
AutoFPS=0
        """.trimIndent()
    }
}
