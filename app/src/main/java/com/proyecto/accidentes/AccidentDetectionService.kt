package com.proyecto.accidentes

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class AccidentDetectionService : Service() {

    private val TAG = "AccidentService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "ACCIDENT_SERVICE_CHANNEL"

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false

    companion object {
        private var serviceInstance: AccidentDetectionService? = null

        fun isRunning(): Boolean {
            return serviceInstance != null
        }

        fun startService(context: Context) {
            val intent = Intent(context, AccidentDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AccidentDetectionService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🟢 Servicio creado")
        serviceInstance = this

        // Crear canal de notificación
        createNotificationChannel()

        // Adquirir WakeLock para mantener CPU activa
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 Servicio iniciado")

        isServiceRunning = true

        // Crear notificación persistente
        val notification = createNotification(
            title = "Sistema de Detección Activo",
            text = "Monitoreando alertas en tiempo real",
            isConnected = true
        )

        // Iniciar servicio en primer plano
        startForeground(NOTIFICATION_ID, notification)

        Log.d(TAG, "✅ Servicio en primer plano activo")

        // START_STICKY: Si el sistema mata el servicio, lo reinicia automáticamente
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🔴 Servicio destruido")

        isServiceRunning = false
        serviceInstance = null

        // Liberar WakeLock
        releaseWakeLock()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Detección",
                NotificationManager.IMPORTANCE_LOW // LOW para que no haga ruido
            ).apply {
                description = "Mantiene la app monitoreando en segundo plano"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            Log.d(TAG, "📢 Canal de notificación creado")
        }
    }

    private fun createNotification(title: String, text: String, isConnected: Boolean): Notification {
        // Intent para abrir la app al tocar la notificación
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent para detener el servicio desde la notificación
        val stopIntent = Intent(this, AccidentDetectionService::class.java).apply {
            action = "STOP_SERVICE"
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // No se puede deslizar para cerrar
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(if (isConnected) 0xFF00FF99.toInt() else 0xFF9CA3AF.toInt())
            // Botón para detener desde la notificación
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Detener",
                stopPendingIntent
            )
            .build()
    }

    fun updateNotification(title: String, text: String, isConnected: Boolean) {
        val notification = createNotification(title, text, isConnected)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "🔄 Notificación actualizada: $title")
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AccidentDetection::ServiceWakeLock"
            ).apply {
                acquire(10 * 60 * 60 * 1000L) // 10 horas máximo
            }
            Log.d(TAG, "🔋 WakeLock adquirido")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adquiriendo WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "🔋 WakeLock liberado")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error liberando WakeLock: ${e.message}")
        }
    }
}