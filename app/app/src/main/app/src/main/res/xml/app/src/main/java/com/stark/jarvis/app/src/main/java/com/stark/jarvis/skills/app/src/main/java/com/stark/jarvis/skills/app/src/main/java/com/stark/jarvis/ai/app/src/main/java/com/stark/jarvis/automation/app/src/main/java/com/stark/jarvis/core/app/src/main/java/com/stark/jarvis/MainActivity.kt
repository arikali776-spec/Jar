package com.stark.jarvis

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stark.jarvis.core.JarvisVoiceService

class MainActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.CAMERA, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        launcher.launch(perms.toTypedArray())

        setContent {
            JarvisHudScreen(
                onStart = {
                    val intent = Intent(this, JarvisVoiceService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                },
                onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
        }
    }
}

@Composable
fun JarvisHudScreen(onStart: () -> Unit, onAccessibility: () -> Unit) {
    val status by JarvisVoiceService.statusLog.collectAsState()
    val transcript by JarvisVoiceService.lastTranscript.collectAsState()
    val isOnline by JarvisVoiceService.isOnlineMode.collectAsState()
    val cyan = Color(0xFF00E5FF)

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF050B14)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("STARK OS v4.2", color = cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(if (isOnline) "● ONLINE (CLOUD)" else "● OFFLINE (LOCAL)", color = if (isOnline) Color(0xFF00E676) else Color(0xFFFFB300), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp).padding(10.dp)) {
            ArcReactorCanvas(isListening = status.contains("LISTENING"), isSpeaking = status.contains("SPEAKING"), glowColor = cyan)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("J.A.R.V.I.S.", color = cyan, fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(status, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Column(modifier = Modifier.fillMaxWidth().border(1.dp, cyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(16.dp)) {
            Text("LAST VOICE COMMAND:", color = cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(4.dp))
            Text(transcript, color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = cyan), modifier = Modifier.fillMaxWidth()) {
                Text("ENGAGE J.A.R.V.I.S. CORE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) {
                Text("ENABLE SCREEN TOUCH CONTROL", color = cyan)
            }
        }
    }
}

@Composable
fun ArcReactorCanvas(isListening: Boolean, isSpeaking: Boolean, glowColor: Color) {
    val transition = rememberInfiniteTransition(label = "Reactor")
    val speed = if (isSpeaking) 3000 else if (isListening) 6000 else 12000
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(speed, easing = LinearEasing), RepeatMode.Restart), label = "Angle")
    val scale by transition.animateFloat(if (isSpeaking) 0.90f else 0.96f, if (isSpeaking || isListening) 1.06f else 1.02f, infiniteRepeatable(tween(if (isSpeaking) 400 else 1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Scale")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = (size.minDimension / 2f) * scale

        drawCircle(glowColor.copy(alpha = 0.15f), r * 0.95f, center)
        rotate(angle, center) {
            drawCircle(glowColor.copy(alpha = 0.8f), r * 0.82f, center, style = Stroke(width = 4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f))))
        }
        rotate(-angle * 1.5f, center) {
            drawArc(glowColor, 0f, 270f, false, Offset(center.x - r * 0.65f, center.y - r * 0.65f), androidx.compose.ui.geometry.Size(r * 1.3f, r * 1.3f), style = Stroke(width = 6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 20f))))
        }
        rotate(angle * 0.5f, center) {
            for (i in 0 until 10) rotate((i * 36).toFloat(), center) {
                drawLine(glowColor.copy(alpha = 0.9f), Offset(center.x, center.y - r * 0.25f), Offset(center.x, center.y - r * 0.52f), 3.dp.toPx())
            }
        }
        drawCircle(Brush.radialGradient(listOf(Color.White, glowColor, Color.Transparent), center, r * 0.35f), r * 0.35f, center)
    }
}
