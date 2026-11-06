package com.whatsgo.app.ui.home

import androidx.compose.animation.core.CubicBezierEasing

/**
 * M3 motion tokens.
 * https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
 *
 * internal so other files in `ui.home` can reference them
 * (e.g. ChatTopBar, FloatingBottomNav) without an import.
 */
internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
internal val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
