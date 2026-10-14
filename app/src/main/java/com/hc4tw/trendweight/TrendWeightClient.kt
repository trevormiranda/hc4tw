package com.hc4tw.trendweight

import com.hc4tw.logging.AppLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class MeasurementPayload(
    val date: String,
    val weightKg: Double,
    val fatRatio: Double? = null // 0.0 - 1.0 (e.g. 0.198 for 19.8%)
) {
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("date", date)
        json.put("weight", weightKg)
        if (fatRatio != null) {
            json.put("fat", fatRatio)
        }
        return json
    }

    fun toJsonString(): String {
        val array = JSONArray()
        array.put(toJsonObject())
        return array.toString()
    }
}

class TrendWeightClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://trendweight.com/api/v1/measurements/manual"
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Validates the provided API key by calling the TrendWeight GET endpoint.
     */
    fun verifyApiKey(apiKey: String): Result<Boolean> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("TrendWeight API key is empty"))
        }

        val request = Request.Builder()
            .url(baseUrl)
            .get()
            .header("Authorization", "Bearer $apiKey")
            .header("X-Api-Key", apiKey)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        AppLogger.d(TAG, "Verifying TrendWeight API key...")

        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    AppLogger.i(TAG, "TrendWeight API key verified successfully (HTTP $code)")
                    Result.success(true)
                } else if (code == 401 || code == 403) {
                    AppLogger.w(TAG, "TrendWeight API key rejected (HTTP $code): Unauthorized")
                    Result.failure(IOException("Invalid API key (HTTP $code): Unauthorized"))
                } else {
                    AppLogger.e(TAG, "TrendWeight API error (HTTP $code): $body")
                    Result.failure(IOException("TrendWeight server error (HTTP $code): $body"))
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Connection failed while verifying API key", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches existing manual measurements from TrendWeight.
     */
    fun getExistingMeasurements(apiKey: String): Result<Map<String, MeasurementPayload>> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("TrendWeight API key is empty"))
        }

        val request = Request.Builder()
            .url(baseUrl)
            .get()
            .header("Authorization", "Bearer $apiKey")
            .header("X-Api-Key", apiKey)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val respBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val resultMap = mutableMapOf<String, MeasurementPayload>()

                    val jsonArray = when {
                        respBody.trim().startsWith("[") -> JSONArray(respBody)
                        respBody.trim().startsWith("{") -> {
                            val obj = JSONObject(respBody)
                            when {
                                obj.has("entries") -> obj.getJSONArray("entries")
                                obj.has("measurements") -> obj.getJSONArray("measurements")
                                obj.has("data") -> obj.getJSONArray("data")
                                else -> JSONArray()
                            }
                        }
                        else -> JSONArray()
                    }

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val date = item.optString("date")
                        if (date.isNotBlank()) {
                            val weight = item.optDouble("weight", 0.0)
                            val fat = if (item.has("fat") && !item.isNull("fat")) item.optDouble("fat") else null
                            resultMap[date] = MeasurementPayload(
                                date = date,
                                weightKg = weight,
                                fatRatio = fat
                            )
                        }
                    }

                    AppLogger.i(TAG, "Fetched ${resultMap.size} existing measurement(s) from TrendWeight")
                    Result.success(resultMap)
                } else {
                    AppLogger.w(TAG, "Failed to fetch existing measurements from TrendWeight (HTTP $code)")
                    Result.failure(IOException("HTTP $code from TrendWeight: $respBody"))
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception fetching existing measurements from TrendWeight", e)
            Result.failure(e)
        }
    }

    /**
     * Posts a list of weight/fat measurements to TrendWeight manual measurements API.
     */
    fun postMeasurements(apiKey: String, payloads: List<MeasurementPayload>): Result<Unit> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("TrendWeight API key is empty"))
        }
        if (payloads.isEmpty()) {
            return Result.success(Unit)
        }

        val entriesArray = JSONArray()
        for (payload in payloads) {
            entriesArray.put(payload.toJsonObject())
        }

        val body = entriesArray.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(baseUrl)
            .post(body)
            .header("Authorization", "Bearer $apiKey")
            .header("X-Api-Key", apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        AppLogger.i(TAG, "Posting ${payloads.size} measurement(s) to TrendWeight")

        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val respBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    AppLogger.i(TAG, "Successfully synced ${payloads.size} measurement(s) to TrendWeight (HTTP $code)")
                    Result.success(Unit)
                } else {
                    val errorMsg = "HTTP $code from TrendWeight: $respBody"
                    AppLogger.e(TAG, "Failed to post measurements: $errorMsg")
                    Result.failure(IOException(errorMsg))
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Network exception while posting measurements", e)
            Result.failure(e)
        }
    }

    /**
     * Posts a single weight/fat measurement to TrendWeight manual measurements API.
     */
    fun postMeasurement(apiKey: String, payload: MeasurementPayload): Result<Unit> {
        return postMeasurements(apiKey, listOf(payload))
    }

    companion object {
        private const val TAG = "TrendWeightClient"
        private const val USER_AGENT = "TrendWeightHealthConnectBridge/1.0.0 (Android)"
    }
}
