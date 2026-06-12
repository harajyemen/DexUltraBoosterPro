package com.dex.ultra.booster.pro

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dex.ultra.booster.pro.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isBoosting = false
    private var overlayActive = false

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            Toast.makeText(this, "✅ تم منح إذن الملفات — يمكنك الآن حقن الملفات", Toast.LENGTH_SHORT).show()
            binding.tvInjectStatus.text = "✨ إذن الملفات مفعّل، جاهز للحقن المباشر"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav()
        setupBoostButton()
        setupFileInjection()
        setupOverlayToggle()
        startStatsUpdate()
        checkStoragePermission()
    }

    override fun onResume() {
        super.onResume()
        // تحديث حالة اتصال الخدمة للمستخدم في كل مرة يعود فيها إلى التطبيق
        updateShizukuStatus()
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home        -> true
                R.id.nav_sensitivity -> {
                    startActivity(Intent(this, SensitivityActivity::class.java)); false
                }
                R.id.nav_tips        -> {
                    startActivity(Intent(this, TipsActivity::class.java)); false
                }
                R.id.nav_ping        -> {
                    startActivity(Intent(this, PingOptimizerActivity::class.java)); false
                }
                else -> false
            }
        }
    }

    private fun setupBoostButton() {
        binding.btnBoost.setOnClickListener {
            if (!isBoosting) startBoosting() else stopBoosting()
        }
    }

    private fun startBoosting() {
        isBoosting = true
        binding.btnBoost.text = getString(R.string.boost_active)
        binding.tvStatus.text = getString(R.string.status_boosting)

        lifecycleScope.launch {
            binding.tvStatus.text = "🧹 تنظيف الذاكرة المؤقتة…"
            PerformanceOptimizer.clearSystemCache(applicationContext)
            delay(500)

            binding.tvStatus.text = "⚡ تحسين أداء المعالج…"
            PerformanceOptimizer.optimizeCpuGovernor()
            delay(500)

            binding.tvStatus.text = "🧠 تحسين إدارة الذاكرة…"
            PerformanceOptimizer.optimizeMemory(applicationContext)
            delay(500)

            binding.tvStatus.text = "🌐 تحسين الشبكة…"
            delay(500)

            binding.tvStatus.text = getString(R.string.status_done)
            binding.btnBoost.text = getString(R.string.boost_stop)
        }
    }

    private fun stopBoosting() {
        isBoosting = false
        binding.btnBoost.text = getString(R.string.boost_button)
        binding.tvStatus.text = getString(R.string.status_ready)
    }

    private fun setupFileInjection() {
        // قائمة نسخ ببجي للتعديل
        binding.spinnerPubgVersion.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                "PUBG Mobile (Global)",
                "PUBG Mobile KR (كوريا)",
                "PUBG Mobile VN (فيتنام)",
                "Battlegrounds Mobile India",
                "PUBG Mobile LITE"
            )
        )

        // خيارات معدل تحديث الشاشة فريم
        binding.spinnerFps.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("60 FPS", "90 FPS", "120 FPS ⭐", "144 FPS")
        )
        binding.spinnerFps.setSelection(2) // افتراضياً 120 فريم

        binding.btnInjectFiles.setOnClickListener { injectPubgFiles() }
    }

    private fun injectPubgFiles() {
        val version = binding.spinnerPubgVersion.selectedItemPosition
        val fps = when (binding.spinnerFps.selectedItemPosition) {
            0 -> 60; 1 -> 90; 2 -> 120; else -> 144
        }

        val shizukuActive = ShizukuHelper.isAvailable()

        // ميزة ذكية: إذا كان تطبيق Shizuku غير نشط، نتحقق من توفر إذن إدارة الملفات العام كبديل
        if (!shizukuActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showStoragePermissionDialog()
            return
        }

        lifecycleScope.launch {
            binding.tvInjectStatus.text = "⏳ جاري حقن ملفات ${fps} FPS بدون روت…"
            binding.btnInjectFiles.isEnabled = false

            // استدعاء ملف الفايل إنجيكتور المطور
            val success = FileInjector.inject120FpsFiles(
                applicationContext,
                version,
                shizukuActive,
                fps
            )

            binding.btnInjectFiles.isEnabled = true

            if (success) {
                binding.tvInjectStatus.text = "✅ تم الحقن بنجاح – ${fps} FPS"
                Toast.makeText(
                    this@MainActivity,
                    "✅ تم تطبيق ملفات ${fps}FPS بنجاح!\nقم بتشغيل اللعبة الآن.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val outPath = FileInjector.getOutputDirectory(applicationContext)
                binding.tvInjectStatus.text = "⚠️ لم يتم الحقن المباشر\nالملفات بداخل: $outPath"
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("تنبيه حول الحقن")
                    .setMessage("فشل الحقن التلقائي.\n\nالسبب: قد لا تكون النسخة المختارة مثبتة على جهازك، أو تحتاج لتشغيل تطبيق Shizuku أولاً.\n\nتم حفظ الملفات احتياطياً في المجلد الخارجي للتطبيق.")
                    .setPositiveButton("حسناً", null)
                    .show()
            }
        }
    }

    private fun setupOverlayToggle() {
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    startService(Intent(this, OverlayService::class.java))
                    overlayActive = true
                } else {
                    binding.switchOverlay.isChecked = false
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"))
                    startActivity(intent)
                    Toast.makeText(this, "فعّل إذن العرض فوق التطبيقات ثم أعد المحاولة", Toast.LENGTH_SHORT).show()
                }
            } else {
                stopService(Intent(this, OverlayService::class.java))
                overlayActive = false
            }
        }
    }

    private fun updateShizukuStatus() {
        val connected = ShizukuHelper.isAvailable()
        binding.tvShizukuStatus.text = if (connected) "✅ Shizuku متصل" else "❌ Shizuku غير مفعّل"
        binding.tvShizukuStatus.setTextColor(
            getColor(if (connected) R.color.success else R.color.warning)
        )

        if (!connected) {
            binding.btnConnectShizuku.visibility = android.view.View.VISIBLE
            binding.btnConnectShizuku.setOnClickListener {
                try {
                    ShizukuHelper.requestPermission()
                } catch (e: Exception) {
                    Toast.makeText(this, "Shizuku غير مثبّت على هذا الجهاز", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.btnConnectShizuku.visibility = android.view.View.GONE
        }
    }

    private fun startStatsUpdate() {
        lifecycleScope.launch {
            while (true) {
                delay(2000)
                binding.tvRamValue.text = "${DeviceStats.getRamUsagePercent(applicationContext)}%"
                binding.tvTempValue.text = "${DeviceStats.getCpuTemperature()}°C"
            }
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager() && !ShizukuHelper.isAvailable()) {
            binding.tvInjectStatus.text = "💡 يمكنك تشغيل Shizuku أو تفعيل إذن الملفات للحقن التلقائي"
        }
    }

    private fun showStoragePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("إذن الوصول للملفات")
            .setMessage(
                "في حال عدم تشغيل تطبيق Shizuku، يحتاج التطبيق إلى إذن 'إدارة جميع الملفات' البديل لتعديل جرافيكس اللعبة بنجاح.\n\n" +
                "في الصفحة التالية، تذكر تفعيل الخيار لـ DexUltra Booster Pro."
            )
            .setPositiveButton("منح الإذن البديل") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    manageStorageLauncher.launch(intent)
                }
            }
            .setNegativeButton("لاحقاً") { _, _ ->
                Toast.makeText(this, "يمكنك تشغيل خدمة Shizuku لتجنب هذا الإذن بالكامل", Toast.LENGTH_LONG).show()
            }
            .show()
    }
}
