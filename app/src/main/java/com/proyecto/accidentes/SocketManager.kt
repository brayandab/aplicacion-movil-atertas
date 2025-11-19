package com.proyecto.accidentes

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

object SocketManager {

    private const val TAG = "SocketManager"
    private const val SERVER_URL = "https://accident-detector.site"   // ✔ Tu servidor real

    private var socket: Socket? = null
    private var onAccidentReceived: ((AccidentData) -> Unit)? = null

    fun connect(context: Context, onAccident: (AccidentData) -> Unit) {
        this.onAccidentReceived = onAccident

        try {
            Log.d(TAG, "🔌 Intentando conectar a $SERVER_URL")

            val opts = IO.Options().apply {
                transports = arrayOf("websocket", "polling")
                secure = true
                reconnection = true
                reconnectionDelay = 1000
                reconnectionDelayMax = 6000
                reconnectionAttempts = Int.MAX_VALUE
                timeout = 10000
            }

            socket = IO.socket(SERVER_URL, opts)

            // ============================================================
            // EVENTO CONECTADO
            // ============================================================
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "======================================")
                Log.d(TAG, "✅ CONECTADO AL SERVIDOR")
                Log.d(TAG, "🆔 ID: ${socket?.id()}")
                Log.d(TAG, "======================================")

                // 👇 Enviar mobile_connect al servidor Flask
                val data = JSONObject().apply {
                    put("user_id", "mobile_${System.currentTimeMillis()}")
                    put("platform", "android")
                    put("version", "1.0")
                }

                Log.d(TAG, "📤 Enviando mobile_connect...")
                socket?.emit("mobile_connect", data)
            }


            // ============================================================
            // EVENTO DESCONEXIÓN
            // ============================================================
            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.e(TAG, "❌ Socket desconectado")
            }


            // ============================================================
            // ERROR DE CONEXIÓN
            // ============================================================
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.getOrNull(0)
                Log.e(TAG, "❌ Error de conexión: $error")
            }


            // ============================================================
            // RESPUESTA DEL SERVIDOR A CONEXIÓN
            // ============================================================
            socket?.on("mobile_connected") { args ->
                try {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "")
                    Log.d(TAG, "📲 mobile_connected recibido!")
                    Log.d(TAG, "Status: ${data.optString("status")}")
                    Log.d(TAG, "Mensaje: ${data.optString("message")}")
                    Log.d(TAG, "")
                    Log.d(TAG, "📌 Ya estamos en el room 'mobile_emergency'")
                    Log.d(TAG, "📡 Esperando alertas...")
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando mobile_connected: ${e.message}")
                }
            }


            // ============================================================
            // ALERTA REAL DE ACCIDENTE
            // ============================================================
            socket?.on("mobile_emergency_alert") { args ->
                try {
                    val json = args[0] as JSONObject

                    Log.e(TAG, "")
                    Log.e(TAG, "🚨".repeat(30))
                    Log.e(TAG, "🚨 ALERTA DE EMERGENCIA RECIBIDA")
                    Log.e(TAG, "🚨".repeat(30))
                    Log.e(TAG, json.toString(2))

                    val accident = AccidentData(
                        accidentId = json.optInt("accident_id", 0),
                        cameraId = json.optInt("camera_id", 0),
                        cameraIp = json.optString("camera_ip", "N/A"),
                        latitude = json.optDouble("latitude", 0.0),
                        longitude = json.optDouble("longitude", 0.0),
                        timestamp = json.optString("timestamp", ""),
                        imageUrl = json.optString("image_url", ""),
                        message = json.optString("message", "Accidente detectado"),
                        severity = json.optString("severity", "high"),
                        confidence = json.optInt("confidence", 0)
                    )

                    // Notificar a la Activity o Service
                    onAccidentReceived?.invoke(accident)

                    Log.d(TAG, "🔔 Callback ejecutado correctamente")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error procesando alerta: ${e.message}")
                    e.printStackTrace()
                }
            }


            // ============================================================
            // INICIAR CONEXIÓN
            // ============================================================
            socket?.connect()
            Log.d(TAG, "⏳ Conectando...")

        } catch (e: URISyntaxException) {
            Log.e(TAG, "❌ URL inválida: ${e.message}")
        }
    }


    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        Log.d(TAG, "🔌 Socket desconectado")
    }

    fun isConnected(): Boolean = socket?.connected() ?: false
}


// ============================================================
// DATA CLASS FINAL PARA ACCIDENTES
// ============================================================

data class AccidentData(
    val accidentId: Int,
    val cameraId: Int,
    val cameraIp: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val imageUrl: String,
    val message: String,
    val severity: String,
    val confidence: Int
)