package com.dex.ultra.booster.pro

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.dex.ultra.booster.pro.databinding.ActivityPermissionsBinding
import rikka.shizuku.Shizuku

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateUi() }

    private val storageLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updateUi() }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantStorage.setOnClickListener { requestStorage() }
        binding.btnGrantOverlay.setOnClickListener { requestOverlay() }
        binding.btnGrantShizuku.setOnClickListener { openShizuku() }
        binding.btnContinue.setOnClickListener { goToMain() }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        val storageOk = hasStoragePermission()
        val overlayOk = Settings.canDrawOverlays(this)
        val shizukuOk = isShizukuGranted()

        binding.ivStorageStatus.setImageResource(
            if (storageOk) R.drawable.ic_check else R.drawable.ic_close
        )
        binding.ivOverlayStatus.setImageResource(
            if (overlayOk) R.drawable.ic_check else R.drawable.ic_close
        )
        binding.ivShizukuStatus.setImageResource(
            if (shizukuOk) R.drawable.ic_check else R.drawable.ic_close
        )

        binding.tvStorageStatus.text = if (storageOk) getString(R.string.perm_granted) else getString(R.string.perm_denied)
        binding.tvOverlayStatus.text = if (overlayOk) getString(R.string.perm_granted) else getString(R.string.perm_denied)
        binding.tvShizukuStatus.text = if (shizukuOk) getString(R.string.perm_granted) else getString(R.string.perm_denied)

        binding.btnContinue.isEnabled = storageOk && overlayOk
        binding.btnContinue.alpha = if (storageOk && overlayOk) 1f else 0.5f
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PermissionChecker.PERMISSION_GRANTED
        }
    }

    private fun isShizukuGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PermissionChecker.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:$packageName"))
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            storageLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun requestOverlay() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayLauncher.launch(intent)
    }

    private fun openShizuku() {
        try {
            Shizuku.requestPermission(1001)
        } catch (e: Exception) {
            // Shizuku not installed or not running
            try {
                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "يرجى تثبيت Shizuku من Google Play أولاً", Toast.LENGTH_LONG).show()
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
                    )
                }
            } catch (ex: Exception) {
                Toast.makeText(this, "Shizuku غير متوفر", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        finish()
    }
}
