package com.voidlinux

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.voidlinux.feature.linux.LinuxBootstrapViewModel
import com.voidlinux.databinding.ActivityMainBinding
import com.voidlinux.service.VoidForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val notifGranted = result[Manifest.permission.POST_NOTIFICATIONS] ?: true
        if (notifGranted) {
            VoidForegroundService.start(this)
        }
        initializeLinux()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (perms.isNotEmpty()) {
            requestPermissions.launch(perms.toTypedArray())
        } else {
            VoidForegroundService.start(this)
            initializeLinux()
        }
    }

    private fun initializeLinux() {
        ViewModelProvider(this)[LinuxBootstrapViewModel::class.java].ensureInitialized()
    }
}