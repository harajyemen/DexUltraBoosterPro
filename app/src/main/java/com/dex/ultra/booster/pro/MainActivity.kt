package com.dex.ultra.booster.pro

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dex.ultra.booster.pro.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isBoosting = false
    private var overlayActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav()
        setupBoostButton()
        setupFileInjection()
        setupOverlayToggle()
        updateShizukuStatus()
        startStatsUpdate()
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_sensitivity -> {
                    startActivity(Intent(this, SensitivityActivity::class.java))
                    false
                }
                R.id.nav_tips -> {
                    startActivity(Intent(this, TipsActivity::class.java))
                    false
                }
                R.id.nav_ping -> {
                    startActivity(Intent(this, PingOptimizerActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    private fun setupBoostButton() {
        binding.btnBoost.setOnClickListener {
            if (!isBoosting) {
                startBoosting()
            } else {
                stopBoosting()
            }
        }
    }

    private fun startBoosting() {
        isBoosting = true
        binding.btnBoost.text = getString(R.string.boost_active)
        binding.tvStatus.text = getString(R.string.status_boosting)

        lifecycleScope.launch {
            // Phase 1: Clear cache
            binding.tvStatus.text = "🧹 تنظيف الذاكرة المؤقتة…"
            PerformanceOptimizer.clearSystemCache(applicationContext)
            delay(500)

            // Phase 2: Optimize CPU governor
            binding.tvStatus.text = "⚡ تحسين أداء المعالج…"
            PerformanceOptimizer.optimizeCpuGovernor()
            delay(500)

            // Phase 3: Adjust RAM
            binding.tvStatus.text = "🧠 تحسين إدارة الذاكرة…"
            PerformanceOptimizer.optimizeMemory(applicationContext)
            delay(500)

            // Phase 4: Network QoS
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

        binding.btnInjectFiles.setOnClickListener {
            injectPubgFiles()
        }
    }

    private fun injectPubgFiles() {
        val version = binding.spinnerPubgVersion.selectedItemPosition
        lifecycleScope.launch {
            binding.tvInjectStatus.text = "⏳ جاري حقن الملفات…"

            val success = FileInjector.inject120FpsFiles(
                applicationContext,
                version,
                isShizukuAvailable()
            )

            if (success) {
                binding.tvInjectStatus.text = getString(R.string.inject_success)
                Toast.makeText(
                    this@MainActivity,
                    "✅ تم حقن ملفات 120FPS بنجاح!\nأعد تشغيل اللعبة",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                binding.tvInjectStatus.text = getString(R.string.inject_fail)
                Toast.makeText(
                    this@MainActivity,
                    "❌ ${getString(R.string.inject_fail)}\nتحقق من تشغيل Shizuku",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupOverlayToggle() {
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (android.provider.Settings.canDrawOverlays(this)) {
                    startService(Intent(this, OverlayService::class.java))
                    overlayActive = true
                } else {
                    binding.switchOverlay.isChecked = false
                    Toast.makeText(this, "يجب منح إذن العرض فوق التطبيقات", Toast.LENGTH_SHORT).show()
                }
            } else {
                stopService(Intent(this, OverlayService::class.java))
                overlayActive = false
            }
        }
    }

    private fun updateShizukuStatus() {
        val connected = isShizukuAvailable()
        binding.tvShizukuStatus.text = if (connected) "✅ Shizuku متصل" else "⚠️ Shizuku غير متصل"
        binding.tvShizukuStatus.setTextColor(
            getColor(if (connected) R.color.success else R.color.warning)
        )

        if (!connected) {
            binding.btnConnectShizuku.visibility = android.view.View.VISIBLE
            binding.btnConnectShizuku.setOnClickListener {
                try {
                    Shizuku.requestPermission(1001)
                } catch (e: Exception) {
                    Toast.makeText(this, "Shizuku غير متاح", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.btnConnectShizuku.visibility = android.view.View.GONE
        }
    }

    private fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun startStatsUpdate() {
        lifecycleScope.launch {
            while (true) {
                delay(2000)
                val ramUsage = DeviceStats.getRamUsagePercent(applicationContext)
                val cpuTemp = DeviceStats.getCpuTemperature()
                binding.tvRamValue.text = "$ramUsage%"
                binding.tvTempValue.text = "${cpuTemp}°C"
            }
        }
    }
}
