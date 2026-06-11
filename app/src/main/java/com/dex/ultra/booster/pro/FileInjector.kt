package com.dex.ultra.booster.pro

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileInjector {

    private const val TAG = "FileInjector"

    /**
     * Main entry point: inject 120fps UserCustom.ini and Active.sav
     * into the selected PUBG version's data directory.
     *
     * @param context   Application context
     * @param versionIndex  0=Global, 1=KR, 2=VN, 3=BGMI, 4=Lite
     * @param useShizuku    Use Shizuku for protected /data/data access
     */
    suspend fun inject120FpsFiles(
        context: Context,
        versionIndex: Int,
        useShizuku: Boolean
    ): Boolean = withContext(Dispatchers.IO) {

        val pkg = ShizukuHelper.PUBG_PACKAGES[versionIndex]
            ?: return@withContext false

        val baseDataPath = "/data/data/$pkg"
        val filesPath = "$baseDataPath/files"

        // Verify Shizuku access or warn
        if (useShizuku && !ShizukuHelper.isAvailable()) {
            Log.w(TAG, "Shizuku requested but not available")
        }

        var success = false

        // --- 1. Inject UserCustom.ini (120 FPS settings) ---
        val userCustomContent = generateUserCustomIni()
        val userCustomPaths = listOf(
            "$filesPath/UserCustom.ini",
            "$baseDataPath/shared_prefs/UserCustom.ini",
            "$baseDataPath/UserCustom.ini"
        )

        for (path in userCustomPaths) {
            val result = if (useShizuku && ShizukuHelper.isAvailable()) {
                ShizukuHelper.writeFileViaTmp(path, userCustomContent)
            } else {
                writeToExternalFallback(context, "UserCustom.ini", userCustomContent)
            }
            if (result) {
                Log.i(TAG, "UserCustom.ini injected to $path")
                success = true
                break
            }
        }

        // --- 2. Inject Active.sav (120 FPS device fingerprint) ---
        val activeSavContent = generateActiveSav()
        val activeSavPaths = listOf(
            "$filesPath/Active.sav",
            "$baseDataPath/Active.sav"
        )

        for (path in activeSavPaths) {
            val result = if (useShizuku && ShizukuHelper.isAvailable()) {
                ShizukuHelper.writeFileViaTmp(path, activeSavContent)
            } else {
                writeToExternalFallback(context, "Active.sav", activeSavContent)
            }
            if (result) {
                Log.i(TAG, "Active.sav injected to $path")
                success = true
                break
            }
        }

        // --- 3. Set file permissions via Shizuku ---
        if (useShizuku && ShizukuHelper.isAvailable()) {
            ShizukuHelper.runCommand("chown $pkg:$pkg \"$filesPath/UserCustom.ini\" 2>/dev/null; true")
            ShizukuHelper.runCommand("chown $pkg:$pkg \"$filesPath/Active.sav\" 2>/dev/null; true")
        }

        return@withContext success
    }

    /**
     * Generate UserCustom.ini content for 120 FPS + reduced lag settings.
     * These are the custom graphics/performance settings for PUBG Mobile.
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
     * Generate Active.sav content — device identity spoofed as 120fps-capable.
     * This tells PUBG Mobile's servers that the device supports 120fps.
     */
    private fun generateActiveSav(): String {
        return """
[Device]
DeviceModel=Samsung Galaxy S23 Ultra
Manufacturer=Samsung
DeviceID=SM-S918B
SupportFrameRate120=1
SupportFrameRate90=1
SupportFrameRate60=1
MaxSupportedFrameRate=120
DevicePerformanceLevel=4
GPU=Adreno 740
CPU=Snapdragon 8 Gen 2
RAM=12288
ScreenWidth=2340
ScreenHeight=1080
DPI=393
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
     * Fallback: write to external storage (OBB/documents folder)
     * for manual copying when Shizuku is unavailable.
     */
    private fun writeToExternalFallback(
        context: Context,
        filename: String,
        content: String
    ): Boolean {
        return try {
            val dir = context.getExternalFilesDir("DexUltra_Output")
                ?: return false
            dir.mkdirs()
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
     * Get output path for manually-injected files (when Shizuku unavailable).
     */
    fun getOutputDirectory(context: Context): String {
        return context.getExternalFilesDir("DexUltra_Output")?.absolutePath ?: ""
    }
}
