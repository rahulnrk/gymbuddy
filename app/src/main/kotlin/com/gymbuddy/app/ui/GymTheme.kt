package com.gymbuddy.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val MinTap = 44.dp

val Bg = Color(0xFF0B0D10)
val Card = Color(0xFF171B22)
val Ink = Color(0xFFF5F7FA)
val Mute = Color(0xFFD0D6DE)
val Accent = Color(0xFF7DFFB3)
val AccentInk = Color(0xFF04140A)
val Danger = Color(0xFFFF8A80)

fun gymColors(): ColorScheme = darkColorScheme(
    background = Bg,
    surface = Card,
    onBackground = Ink,
    onSurface = Ink,
    primary = Accent,
    onPrimary = AccentInk,
    error = Danger,
    onError = AccentInk,
)
