package com.proyecto.accidentes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.URL

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.data["title"] ?: "Accidente detectado"
        val body = remoteMessage.data["body"] ?: "Se ha detectado un posible accidente"
        val lat = remoteMessage.data["lat"]
        val lon = remoteMessage.data["lon"]
        val imageUrl = remoteMessage.data["imageUrl"]

        sendNotification(title, body, lat, lon, imageUrl)
    }

    private fun sendNotification(
        title: String,
        body: String,
        lat: String?,
        lon: String?,
        imageUrl: String?
    ) {

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("lat", lat)
            putExtra("lon", lon)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "accidentes_alertas"

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // --------------------------
        // 🔹 Imagen → BigPicture (sin bigLargeIcon)
        // --------------------------
        if (!imageUrl.isNullOrEmpty()) {
            try {
                val url = URL(imageUrl)
                val bitmap: Bitmap =
                    BitmapFactory.decodeStream(url.openConnection().getInputStream())

                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)  // 👈 SÓLO ESTO → SIN ERROR
                )

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alertas de accidentes",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val hasPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true

        if (hasPermission) {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
