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
import com.voidlinux.databinding.ActivityMainBinding
import com.voidlinux.feature.linux.LinuxBootstrapViewModel
import com.voidlinux.service.VoidForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Une permission facultative (localisation) ne doit jamais empêcher
        // le terminal Linux de démarrer. Les notifications sont nécessaires
        // uniquement pour les notifications réellement affichées.
        VoidForegroundService.start(this)
        initializeLinux()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHost.navController)

        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        val requested = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }

            // La localisation est utilisée uniquement par les fonctions de
            // localisation/diagnostic. Le refus ne bloque pas le terminal.
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }

        if (requested.isNotEmpty()) {
            requestPermissions.launch(requested.toTypedArray())
        } else {
            VoidForegroundService.start(this)
            initializeLinux()
        }
    }

    private fun initializeLinux() {
        ViewModelProvider(this)[LinuxBootstrapViewModel::class.java].ensureInitialized()
    }
}
