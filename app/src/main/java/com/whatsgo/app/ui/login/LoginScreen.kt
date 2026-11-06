package com.whatsgo.app.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.whatsgo.app.core.LoginEvent
import com.whatsgo.app.core.SessionHolder

enum class LoginMethod {
    QR_CODE, PAIRING_CODE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var darkOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val isDark = darkOverride ?: isSystemInDarkTheme()

    val context = LocalContext.current
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
    }

    val session = remember { SessionHolder.get(context) }
    val loginEvent by session.events.collectAsState()

    LaunchedEffect(loginEvent) {
        if (loginEvent is LoginEvent.Connected) {
            onLoginSuccess()
        }
    }

    LoginTheme(darkTheme = isDark) {
        var loginMethod by rememberSaveable { mutableStateOf(LoginMethod.QR_CODE) }

        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalIconButton(
                        onClick = { darkOverride = !isDark },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle theme",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "WhatsGo",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "Link a device",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                MethodSwitcher(
                    selected = loginMethod,
                    onSelect = { loginMethod = it }
                )

                Spacer(Modifier.height(28.dp))

                AnimatedContent(
                    targetState = loginMethod,
                    transitionSpec = {
                        (fadeIn(tween(350)) + scaleIn(initialScale = 0.9f, animationSpec = ExpressiveSpring))
                            .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(150)))
                    },
                    label = "login_method_switch",
                    modifier = Modifier.fillMaxWidth()
                ) { method ->
                    when (method) {
                        LoginMethod.QR_CODE -> QrCodeSection(session = session, event = loginEvent)
                        LoginMethod.PAIRING_CODE -> PairingCodeSection(session = session, event = loginEvent)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MethodSwitcher(
    selected: LoginMethod,
    onSelect: (LoginMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        SwitcherPill(
            label = "QR Code",
            icon = Icons.Filled.QrCodeScanner,
            selected = selected == LoginMethod.QR_CODE,
            modifier = Modifier.weight(1f),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSelect(LoginMethod.QR_CODE)
            }
        )
        SwitcherPill(
            label = "Pairing Code",
            icon = Icons.Filled.Smartphone,
            selected = selected == LoginMethod.PAIRING_CODE,
            modifier = Modifier.weight(1f),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSelect(LoginMethod.PAIRING_CODE)
            }
        )
    }
}

@Composable
private fun SwitcherPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(250),
        label = "pill_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "pill_fg"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = ExpressiveSpring,
        label = "pill_scale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxHeight()
            .scale(scale),
        shape = RoundedCornerShape(24.dp),
        color = bg,
        contentColor = fg
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}