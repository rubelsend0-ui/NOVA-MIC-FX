package com.nova.micfx.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.nova.micfx.ui.screens.CompatibilityScreen
import com.nova.micfx.ui.screens.EffectsScreen
import com.nova.micfx.ui.screens.HomeScreen
import com.nova.micfx.ui.screens.PresetsScreen
import com.nova.micfx.ui.screens.SettingsScreen
import com.nova.micfx.ui.theme.Midnight
import com.nova.micfx.ui.theme.Navy
import com.nova.micfx.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {
    private val vm: MicFxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovaTheme {
                val msg by vm.message.collectAsStateWithLifecycle()
                LaunchedEffect(msg) { msg?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show(); vm.clearMessage() } }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var startAfterPermission by rememberSaveable { mutableIntStateOf(0) }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    vm.onPermissionResult()
                    if (startAfterPermission == 1 && vm.permissionGranted.value) vm.startEngine()
                    startAfterPermission = 0
                }
                val requestAndStart = {
                    if (vm.engine.hasPermission()) vm.toggleEngine()
                    else {
                        startAfterPermission = 1
                        vm.setPermissionPending(true)
                        val perms = if (Build.VERSION.SDK_INT >= 33)
                            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                        else arrayOf(Manifest.permission.RECORD_AUDIO)
                        launcher.launch(perms)
                    }
                }
                val openSettings = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
                val items = listOf("Home" to Icons.Filled.Home, "Effects" to Icons.Filled.Star, "Presets" to Icons.Filled.Check,
                    "Compat" to Icons.Filled.Info, "Settings" to Icons.Filled.Settings)
                Scaffold(
                    containerColor = Midnight,
                    bottomBar = {
                        NavigationBar(containerColor = Navy) {
                            items.forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i, onClick = { tab = i },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors()
                                )
                            }
                        }
                    }
                ) { pad: PaddingValues ->
                    Box(
                        Modifier.fillMaxSize().padding(pad)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .background(Brush.verticalGradient(listOf(Midnight, Color(0xFF070B1A), Midnight)))
                    ) {
                        when (tab) {
                            0 -> HomeScreen(vm, requestAndStart, openSettings)
                            1 -> EffectsScreen(vm)
                            2 -> PresetsScreen(vm)
                            3 -> CompatibilityScreen(vm)
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermission()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onAppStopped()
    }
}
