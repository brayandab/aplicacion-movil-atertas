package com.proyecto.accidentes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.messaging.FirebaseMessaging
import com.proyecto.accidentes.ui.theme.DeteccionAccidentesAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val alertasActivas = prefs.getBoolean("alertas_activas", true)

        val lat = intent.getStringExtra("lat")
        val lon = intent.getStringExtra("lon")

        // ✅ Si viene desde una notificación con coordenadas, abrir Google Maps
        if (lat != null && lon != null) {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Accidente detectado)")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            startActivity(mapIntent)
            finish()
            return
        }

        setContent {
            DeteccionAccidentesAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RadarScreen(
                        modifier = Modifier.padding(innerPadding),
                        alertasActivasInicial = alertasActivas
                    ) { activadas ->
                        // Guardar preferencia
                        prefs.edit().putBoolean("alertas_activas", activadas).apply()

                        if (activadas) {
                            // Volver a registrar token si se reactivan
                            FirebaseMessaging.getInstance().token.addOnCompleteListener {
                                val token = it.result
                                android.util.Log.d("FCM", "Token activo: $token")
                            }
                        } else {
                            // Simular desactivación (en un sistema real podrías avisar al backend)
                            android.util.Log.d("FCM", "Alertas desactivadas, token ignorado")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RadarScreen(
    modifier: Modifier = Modifier,
    alertasActivasInicial: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var activadas by remember { mutableStateOf(alertasActivasInicial) }

    val infiniteTransition = rememberInfiniteTransition(label = "")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = ""
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (activadas) Color(0xFF0A0F1D) else Color(0xFF1C1C1C)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🚨 Detección de Accidentes",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(40.dp))

        // Radar animado
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(if (activadas) scale else 1f)
                .background(
                    if (activadas) Color(0xFF00FF99).copy(alpha = 0.3f)
                    else Color.Gray.copy(alpha = 0.3f),
                    shape = CircleShape
                )
                .clickable {
                    activadas = !activadas
                    onToggle(activadas)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (activadas) "Alertas Activas" else "Desactivadas",
                color = if (activadas) Color(0xFF00FF99) else Color.LightGray,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = if (activadas)
                "Recibirás notificaciones de accidentes cercanos."
            else
                "No recibirás alertas hasta que vuelvas a activarlas.",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
