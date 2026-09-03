package com.gymbuddy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymbuddy.app.voice.ElevenLabsConstants.FIRST_SOUND_LINE

/**
 * First Sound is an experiment harness. It does **not** implement the
 * contract-35 window policy. The mic is armed for the whole screen (always-on
 * Scribe), which costs roughly nineteen times the windowed design. When the
 * real session runner exists, this screen does not become it. Debug-only.
 */
@Composable
fun FirstSoundScreen(
    status: String,
    onSpeak: () -> Unit,
    onCancel: () -> Unit,
    onCopy: () -> Unit,
    onBack: () -> Unit,
) {
    val bg = Color(0xFF111111)
    val ink = Color(0xFFF5F5F5)
    val mute = Color(0xFF9A9A9A)
    val accent = Color(0xFF7CFFB2)
    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .padding(16.dp),
    ) {
        Text("First sound", color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Harness, not the product window. Music stays up until the coach talks or you say hey coach. Then it ducks — not pause — and stays down while you talk.",
            color = mute,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(20.dp))
        Text(FIRST_SOUND_LINE, color = ink, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text(status, color = mute, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSpeak,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics { contentDescription = "Speak a line" },
        ) {
            Text("Speak a line", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333), contentColor = ink),
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics { contentDescription = "Cancel speech" },
        ) {
            Text("Cancel", fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onCopy,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333), contentColor = ink),
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics { contentDescription = "Copy diagnostics" },
        ) {
            Text("Copy diagnostics", fontSize = 18.sp)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) {
            Text("Back", color = accent, fontSize = 16.sp)
        }
    }
}
