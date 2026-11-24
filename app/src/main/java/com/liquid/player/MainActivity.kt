package com.liquid.player

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.liquid.player.ui.AppRoot
import com.liquid.player.ui.LiquidTheme

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    private val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private val launcher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionResult(audioGranted()) }

    private fun audioGranted(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        vm.onPermissionResult(audioGranted())
        if (!audioGranted()) launcher.launch(permissions)

        setContent {
            LiquidTheme {
                AppRoot(vm, onRequestPermission = { launcher.launch(permissions) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (audioGranted() != vm.state.value.hasPermission) vm.onPermissionResult(audioGranted())
    }
}
