package com.proyecto.accidentes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.proyecto.accidentes.ui.theme.DeteccionAccidentesAppTheme
import androidx.compose.material3.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import java.text.SimpleDateFormat
import java.util.*
import android.content.BroadcastReceiver
import android.content.IntentFilter

class MainActivity : ComponentActivity() {

    private var currentScreen by mutableStateOf("home")
    private var currentAccident by mutableStateOf<AccidentData?>(null)
    private var connectionStatus by mutableStateOf("Desconectado")
    private var totalAlertas by mutableStateOf(0)
    private var accidentHistory by mutableStateOf<List<AccidentData>>(emptyList())
    private var alertasActivas by mutableStateOf(false)  // ✅ NUEVO: Estado global

    // ✅ BroadcastReceiver para detectar cuando el servicio se detiene
    private val serviceStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SERVICE_STOPPED") {
                Log.d("MainActivity", "🔔 Servicio detenido detectado")
                connectionStatus = "Desconectado"
                alertasActivas = false  // ✅ APAGAR EL BOTÓN

                val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("alertas_activas", false).apply()
            }
        }
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d("MainActivity", "✅ Permiso de notificaciones concedido")
            } else {
                Log.w("MainActivity", "⚠️ Permiso de notificaciones denegado")
            }
        }

    private val requestBatteryOptimization =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d("MainActivity", "✅ Usuario respondió solicitud de batería")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        createNotificationChannel()

        // ✅ Registrar BroadcastReceiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                serviceStoppedReceiver,
                IntentFilter("SERVICE_STOPPED"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(serviceStoppedReceiver, IntentFilter("SERVICE_STOPPED"))
        }

        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)

        // ✅ SIEMPRE INICIA APAGADO (ignorar lo guardado en SharedPreferences)
        prefs.edit().putBoolean("alertas_activas", false).apply()
        val alertasActivas = false

        totalAlertas = prefs.getInt("total_alertas", 0)

        // ✅ Si el servicio está corriendo, detenerlo (por si quedó activo)
        if (AccidentDetectionService.isRunning()) {
            AccidentDetectionService.stopService(this)
            SocketManager.disconnect()
            connectionStatus = "Desconectado"
        }

        val accidentIdFromNotification = intent.getIntExtra("accident_id", -1)
        if (accidentIdFromNotification != -1) {
            currentAccident = AccidentData(
                accidentId = intent.getIntExtra("accident_id", 0),
                cameraId = intent.getIntExtra("camera_id", 0),
                cameraIp = intent.getStringExtra("camera_ip") ?: "N/A",
                latitude = intent.getDoubleExtra("latitude", 0.0),
                longitude = intent.getDoubleExtra("longitude", 0.0),
                timestamp = intent.getStringExtra("timestamp") ?: "",
                imageUrl = intent.getStringExtra("image_url") ?: "",
                message = intent.getStringExtra("message") ?: "",
                severity = intent.getStringExtra("severity") ?: "high",
                confidence = intent.getIntExtra("confidence", 0)
            )
            currentScreen = "detail"
        }

        setContent {
            DeteccionAccidentesAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0F1D)
                ) {
                    when (currentScreen) {
                        "home" -> HomeScreen(
                            alertasActivasInicial = alertasActivas,
                            connectionStatus = connectionStatus,
                            totalAlertas = totalAlertas,
                            onToggle = { activadas ->
                                handleToggleAlertas(activadas, prefs)
                            },
                            onNavigateToHistory = { currentScreen = "history" }
                        )

                        "history" -> AlertsHistoryScreen(
                            accidents = accidentHistory,
                            onBack = { currentScreen = "home" },
                            onAccidentClick = { accident ->
                                currentAccident = accident
                                currentScreen = "detail"
                            }
                        )

                        "detail" -> currentAccident?.let { accident ->
                            AccidentDetailScreen(
                                accident = accident,
                                onBack = { currentScreen = "home" },
                                onOpenMap = { acc ->
                                    val uri = Uri.parse("geo:${acc.latitude},${acc.longitude}?q=${acc.latitude},${acc.longitude}(Accidente)")
                                    startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    })
                                },
                                onPlayVideo = { acc -> playAccidentVideo(acc) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleToggleAlertas(activadas: Boolean, prefs: android.content.SharedPreferences) {
        prefs.edit().putBoolean("alertas_activas", activadas).apply()

        if (activadas) {
            solicitarPermisos()
            AccidentDetectionService.startService(this)
            connectionStatus = "Conectando..."

            SocketManager.connect(this) { accident ->
                runOnUiThread {
                    totalAlertas++
                    prefs.edit().putInt("total_alertas", totalAlertas).apply()
                    accidentHistory = listOf(accident) + accidentHistory
                    currentAccident = accident
                    showAccidentNotification(accident)

                    if (connectionStatus == "Conectando...") {
                        connectionStatus = "Conectado"
                    }
                }
            }

            Thread {
                Thread.sleep(3000)
                runOnUiThread {
                    connectionStatus = if (SocketManager.isConnected()) {
                        "Conectado"
                    } else {
                        "Error de conexión"
                    }
                }
            }.start()

        } else {
            AccidentDetectionService.stopService(this)
            SocketManager.disconnect()
            connectionStatus = "Desconectado"
        }
    }

    private fun solicitarPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                requestBatteryOptimization.launch(intent)
            }
        }
    }

    private fun playAccidentVideo(accident: AccidentData) {
        val videoUrl = buildVideoUrl(accident)
        val intent = Intent(this, VideoActivity::class.java).apply {
            putExtra("videoUrl", videoUrl)
            putExtra("accidentId", accident.accidentId)
            putExtra("cameraIp", accident.cameraIp)
        }
        startActivity(intent)
    }

    private fun buildVideoUrl(accident: AccidentData): String {
        if (accident.imageUrl.isNotEmpty() && accident.imageUrl.contains("videos")) {
            return accident.imageUrl.replace("/image/", "/videos/")
        }
        val timestamp = accident.timestamp.replace(":", "").replace("-", "").replace("T", "_").substring(0, 15)
        return "https://accident-detector.site/videos/cam${accident.cameraId}_accident_${timestamp}_ANNOTATED.mp4"
    }

    private fun showAccidentNotification(accident: AccidentData) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("accident_id", accident.accidentId)
            putExtra("camera_id", accident.cameraId)
            putExtra("camera_ip", accident.cameraIp)
            putExtra("latitude", accident.latitude)
            putExtra("longitude", accident.longitude)
            putExtra("timestamp", accident.timestamp)
            putExtra("image_url", accident.imageUrl)
            putExtra("message", accident.message)
            putExtra("severity", accident.severity)
            putExtra("confidence", accident.confidence)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this, accident.accidentId, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, "ACCIDENTES_CHANNEL")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 ACCIDENTE DETECTADO")
            .setContentText("Cámara ${accident.cameraIp} - Confianza: ${accident.confidence}%")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${accident.message}\nUbicación: ${accident.latitude}, ${accident.longitude}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        NotificationManagerCompat.from(this).notify(accident.accidentId, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ACCIDENTES_CHANNEL",
                "Alertas de Accidentes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para alertas de accidentes en tiempo real"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onResume() {
        super.onResume()

        // ✅ Verificar si el servicio se detuvo desde la notificación
        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val shouldBeActive = prefs.getBoolean("alertas_activas", false)

        if (shouldBeActive && !AccidentDetectionService.isRunning()) {
            // El servicio fue detenido desde la notificación
            prefs.edit().putBoolean("alertas_activas", false).apply()
            connectionStatus = "Desconectado"  // ✅ Forzar estado desconectado

            // ✅ NO recrear, solo actualizar el estado
            // La UI se actualizará automáticamente porque connectionStatus es mutableState
        } else if (AccidentDetectionService.isRunning()) {
            connectionStatus = if (SocketManager.isConnected()) "Conectado" else "Reconectando..."
        } else {
            // ✅ Si no hay servicio corriendo, asegurar estado desconectado
            connectionStatus = "Desconectado"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ Desregistrar BroadcastReceiver
        try {
            unregisterReceiver(serviceStoppedReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
        }
    }
}

// ==================== PANTALLA PRINCIPAL ====================

@Composable
fun HomeScreen(
    alertasActivasInicial: Boolean,
    connectionStatus: String,
    totalAlertas: Int,
    onToggle: (Boolean) -> Unit,
    onNavigateToHistory: () -> Unit
) {
    var activadas by remember { mutableStateOf(alertasActivasInicial) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0F1D), Color(0xFF1A1F2D), Color(0xFF0A0F1D))
                )
            )
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        HeaderSection(connectionStatus = connectionStatus)

        Spacer(modifier = Modifier.height(50.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            RadarButton(
                activadas = activadas,
                onClick = {
                    activadas = !activadas
                    onToggle(activadas)
                }
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        StatsSection(totalAlertas = totalAlertas, isActive = activadas)

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onNavigateToHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Ver Historial de Alertas", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun HeaderSection(connectionStatus: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SISTEMA DE DETECCIÓN",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        when (connectionStatus) {
                            "Conectado" -> Color(0xFF00FF99)
                            "Conectando..." -> Color.Yellow
                            else -> Color.Red
                        },
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = connectionStatus,
                fontSize = 15.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        Text(
            text = "accident-detector.site",
            fontSize = 13.sp,
            color = Color(0xFF00FF99).copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun RadarButton(activadas: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (activadas) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_animation"
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (activadas) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(scale)
                    .background(Color(0xFF00FF99).copy(alpha = 0.2f), CircleShape)
                    .blur(30.dp)
            )
        }

        Card(
            modifier = Modifier
                .size(200.dp)
                .scale(scale)
                .shadow(20.dp, CircleShape)
                .clickable { onClick() },
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (activadas) Color(0xFF00FF99).copy(alpha = 0.2f) else Color(0xFF2D3748)
            )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (activadas) "⚡" else "⏸",
                        fontSize = 56.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (activadas) "ACTIVO" else "INACTIVO",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activadas) Color(0xFF00FF99) else Color.Gray
                    )
                    Text(
                        text = if (activadas) "Monitoreando" else "Toca para activar",
                        fontSize = 13.sp,
                        color = if (activadas) Color(0xFF00FF99).copy(0.7f) else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun StatsSection(totalAlertas: Int, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCard(label = "Alertas", value = totalAlertas.toString(), icon = "🚨")
        StatCard(
            label = "Estado",
            value = if (isActive) "Activo" else "Inactivo",
            icon = if (isActive) "✓" else "✕"
        )
    }
}

@Composable
fun StatCard(label: String, value: String, icon: String) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 36.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00FF99)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// ==================== PANTALLA HISTORIAL ====================

@Composable
fun AlertsHistoryScreen(
    accidents: List<AccidentData>,
    onBack: () -> Unit,
    onAccidentClick: (AccidentData) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0F1D), Color(0xFF1A1F2D))
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .padding(top = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3748)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("← Atrás")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Historial de Alertas",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        if (accidents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📋", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sin alertas registradas",
                        fontSize = 18.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                accidents.forEach { accident ->
                    AccidentListItem(accident = accident, onClick = onAccidentClick)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun AccidentListItem(accident: AccidentData, onClick: (AccidentData) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(accident) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFFFF6B6B).copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⚠", fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cámara ${accident.cameraIp}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = formatTimestamp(accident.timestamp),
                    fontSize = 13.sp,
                    color = Color(0xFF9CA3AF)
                )
                Text(
                    text = "Confianza: ${accident.confidence}%",
                    fontSize = 13.sp,
                    color = Color(0xFF00FF99)
                )
            }

            Text(text = "›", fontSize = 28.sp, color = Color(0xFF9CA3AF))
        }
    }
}

// ==================== PANTALLA DETALLE ====================

@Composable
fun AccidentDetailScreen(
    accident: AccidentData,
    onBack: () -> Unit,
    onOpenMap: (AccidentData) -> Unit,
    onPlayVideo: (AccidentData) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0F1D), Color(0xFF1A1F2D))
                )
            )
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .padding(top = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3748)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("← Atrás")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Detalles del Accidente",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(220.dp)
                .clickable { onPlayVideo(accident) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "▶", fontSize = 64.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Reproducir video del accidente",
                        color = Color.White.copy(0.8f),
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "INFORMACIÓN DEL INCIDENTE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9CA3AF),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                DetailRow(label = "Cámara", value = accident.cameraIp)
                HorizontalDivider(
                    color = Color(0xFF2D3748),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                DetailRow(label = "Coordenadas", value = "${accident.latitude}, ${accident.longitude}")
                HorizontalDivider(
                    color = Color(0xFF2D3748),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                DetailRow(label = "Fecha y Hora", value = formatTimestamp(accident.timestamp))
                HorizontalDivider(
                    color = Color(0xFF2D3748),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                DetailRow(label = "Confianza", value = "${accident.confidence}%")
                HorizontalDivider(
                    color = Color(0xFF2D3748),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                DetailRow(label = "Severidad", value = accident.severity.uppercase())
                HorizontalDivider(
                    color = Color(0xFF2D3748),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                DetailRow(label = "ID", value = "#${accident.accidentId}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Button(
                onClick = { onOpenMap(accident) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Abrir en Google Maps", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onPlayVideo(accident) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Ver Video Completo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = Color(0xFF9CA3AF)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.End
        )
    }
}

fun formatTimestamp(timestamp: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(timestamp)
        date?.let { outputFormat.format(it) } ?: timestamp
    } catch (e: Exception) {
        timestamp
    }
}