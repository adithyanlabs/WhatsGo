package com.whatsgo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.whatsgo.app.core.LoginEvent
import com.whatsgo.app.core.SessionHolder
import com.whatsgo.app.ui.home.ChatAppRoot
import com.whatsgo.app.ui.login.LoginScreen
import com.whatsgo.app.ui.theme.ChatAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val session = remember { SessionHolder.get(context) }
            var loggedIn by remember { mutableStateOf(session.isLoggedIn()) }
            val scope = rememberCoroutineScope()

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* Result ignored: service still runs if denied, notification just hidden. */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(loggedIn) {
                if (loggedIn) {
                    scope.launch(Dispatchers.IO) { session.reconnectIfNeeded() }
                }
            }

            LaunchedEffect(session) {
                session.events.collect { event ->
                    when (event) {
                        is LoginEvent.LoggedOut -> {
                            SessionHolder.reset()
                            recreate()
                        }
                        is LoginEvent.Connected -> {
                            if (!loggedIn) loggedIn = true
                        }
                        else -> {}
                    }
                }
            }

            ChatAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (loggedIn) {
                        ChatAppRoot()
                    } else {
                        LoginScreen(onLoginSuccess = { loggedIn = true })
                    }
                }
            }
        }
    }
}