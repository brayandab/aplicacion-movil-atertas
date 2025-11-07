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

    // ✅ Este se ejecuta cuando llega una notificación FCM
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "Alerta de Accidente"
        val message = remoteMessage.notification?.body ?: "Se ha detectado un accidente"

        val lat = remoteMessage.data["lat"]
        val lon = remoteMessage.data["lon"]

        showNotification(title, message, lat, lon)
    }

    // ✅ Este se ejecuta cuando se genera o actualiza el token FCM del dispositivo
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d("FCM_TOKEN", "Nuevo token: $token")
        // Aquí podrías enviar el token a tu backend Flask si lo deseas
    }

    private fun showNotification(title: String, message: String, lat: String?, lon: String?) {
        val channelId = "accidentes_alertas"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = if (lat != null && lon != null) {
            try {
                val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Accidente detectado)")
                Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (e: Exception) {
                val webUri = Uri.parse("https://www.google.com/maps?q=$lat,$lon")
                Intent(Intent.ACTION_VIEW, webUri)
            }
        } else {
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
