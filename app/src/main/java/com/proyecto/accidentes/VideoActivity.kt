package com.proyecto.accidentes

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity

class VideoActivity : ComponentActivity() {

    private val TAG = "VideoActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.video_activity)

        // Obtener VideoView del layout
        val videoView = findViewById<VideoView>(R.id.videoView)

        // Controles de reproducción (play/pause/seek)
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        // Recibir datos desde el intent
        val videoUrl = intent.getStringExtra("videoUrl")
        val accidentId = intent.getIntExtra("accidentId", 0)
        val cameraIp = intent.getStringExtra("cameraIp")

        Log.d(TAG, "📹 Abriendo video de accidente #$accidentId")
        Log.d(TAG, "🎥 URL: $videoUrl")
        Log.d(TAG, "📹 Cámara: $cameraIp")

        if (videoUrl.isNullOrEmpty()) {
            Log.e(TAG, "❌ URL del video vacía")
            Toast.makeText(this, "Error: No se proporcionó URL del video", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            val uri = Uri.parse(videoUrl)
            videoView.setVideoURI(uri)

            // Listeners para manejar eventos
            videoView.setOnPreparedListener { mediaPlayer ->
                Log.d(TAG, "✅ Video preparado, iniciando reproducción")
                Toast.makeText(this, "Reproduciendo video del accidente", Toast.LENGTH_SHORT).show()
                mediaPlayer.start()
            }

            videoView.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "❌ Error reproduciendo video: what=$what, extra=$extra")
                Toast.makeText(this, "Error al cargar el video. Verifica la conexión.", Toast.LENGTH_LONG).show()

                // Intentar URL alternativa (sin ANNOTATED)
                tryAlternativeUrl(videoView, videoUrl)

                true // Indica que manejamos el error
            }

            videoView.setOnCompletionListener {
                Log.d(TAG, "✅ Video finalizado")
                Toast.makeText(this, "Video completado", Toast.LENGTH_SHORT).show()
            }

            // Iniciar carga del video
            videoView.requestFocus()
            Log.d(TAG, "🔄 Cargando video...")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al cargar video: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Intenta cargar una URL alternativa si la primera falla
     */
    private fun tryAlternativeUrl(videoView: VideoView, originalUrl: String) {
        try {
            // Intentar sin "_ANNOTATED"
            val alternativeUrl = originalUrl.replace("_ANNOTATED", "")

            if (alternativeUrl != originalUrl) {
                Log.d(TAG, "🔄 Intentando URL alternativa: $alternativeUrl")
                val uri = Uri.parse(alternativeUrl)
                videoView.setVideoURI(uri)
                videoView.start()
            } else {
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error con URL alternativa: ${e.message}")
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // Pausar video cuando la activity no está visible
        findViewById<VideoView>(R.id.videoView)?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Liberar recursos
        findViewById<VideoView>(R.id.videoView)?.stopPlayback()
        Log.d(TAG, "🛑 VideoActivity destruida")
    }
}