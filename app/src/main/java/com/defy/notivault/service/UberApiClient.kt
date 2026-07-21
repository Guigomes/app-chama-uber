package com.defy.notivault.service

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.defy.notivault.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object UberApiClient {

    private const val TAG = "UberApiClient"
    private const val UBER_REQUESTS_URL = "https://api.uber.com/v1.2/requests"
    private const val PICKUP_ADDRESS = "Rua Serra Azul, 780, Campo Grande"
    private const val DROPOFF_ADDRESS = "Rua Guararapes, 174, Coophamat, Campo Grande"

    fun createRideFromFixedAddresses(context: Context): Result<String?> {
        val token = UberAuthManager
            .getValidAccessToken(context)
            .getOrElse { return Result.failure(it) }

        val pickup = geocodeAddress(context, PICKUP_ADDRESS)
            ?: return Result.failure(IllegalStateException("Não foi possível geocodificar origem"))
        val dropoff = geocodeAddress(context, DROPOFF_ADDRESS)
            ?: return Result.failure(IllegalStateException("Não foi possível geocodificar destino"))

        return runCatching {
            sendRideRequest(
                token = token,
                productId = BuildConfig.UBER_PRODUCT_ID,
                pickup = pickup,
                dropoff = dropoff
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun geocodeAddress(context: Context, address: String): LatLng? {
        if (!Geocoder.isPresent()) {
            Log.e(TAG, "Geocoder indisponível no dispositivo")
            return null
        }

        val geocoder = Geocoder(context, Locale("pt", "BR"))
        val matches = geocoder.getFromLocationName(address, 1)
        val first = matches?.firstOrNull() ?: return null
        return LatLng(first.latitude, first.longitude)
    }

    private fun sendRideRequest(
        token: String,
        productId: String,
        pickup: LatLng,
        dropoff: LatLng
    ): String? {
        val payload = JSONObject().apply {
            put("start_latitude", pickup.latitude)
            put("start_longitude", pickup.longitude)
            put("end_latitude", dropoff.latitude)
            put("end_longitude", dropoff.longitude)
            if (productId.isNotBlank()) {
                put("product_id", productId)
            }
        }

        val connection = (URL(UBER_REQUESTS_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseBody = try {
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } catch (_: IOException) {
                ""
            }

            if (responseCode !in 200..299) {
                throw IOException("Uber API erro HTTP $responseCode: $responseBody")
            }

            JSONObject(responseBody).optString("request_id").ifBlank { null }
        } finally {
            connection.disconnect()
        }
    }

    private data class LatLng(val latitude: Double, val longitude: Double)
}
