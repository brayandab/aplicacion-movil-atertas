package com.proyecto.accidentes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Título y mensaje del aviso
        val title = remoteMessage.notification?.title ?: "Alerta de Accidente"
        val message = remoteMessage.notification?.body ?: "Se ha detectado un accidente"

        // Coordenadas si llegan en el payload
        val lat = remoteMessage.data["lat"]
        val lon = remoteMessage.data["lon"]

        showNotification(title, message, lat, lon)
    }

    private fun showNotification(title: String, message: String, lat: String?, lon: String?) {
        val channelId = "accidentes_alertas"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ✅ Si hay coordenadas, abrimos directamente Google Maps
        val intent = if (lat != null && lon != null) {
            try {
                val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Accidente detectado)")
                Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps") // Fuerza abrir en Google Maps
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (e: Exception) {
                // Si algo falla, abre en el navegador
                val webUri = Uri.parse("https://www.google.com/maps?q=$lat,$lon")
                Intent(Intent.ACTION_VIEW, webUri)
            }
        } else {
            // Si no hay coordenadas, abre la app normal
            Intent(this, MainActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(
                if (lat != null && lon != null)
                    "$message\nUbicación: $lat, $lon"
                else
                    message
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alertas de Accidentes",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
