package com.linkscanner.app

import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

data class Analysis(
    val host: String,
    val verdict: String,
    val score: Int,
    val findings: List<String>,
    val contentScan: String,
    val scanId: String?,
)

/** @param detail mensaje del backend para mostrarle al usuario, si lo mandó */
class BackendException(val detail: String?, cause: Throwable? = null) : Exception(detail, cause)

/** Habla con la API del backend. Nunca registra el link en los logs: es dato privado del usuario. */
class BackendClient(private val baseUrl: String) {

    suspend fun analyze(url: String, deepScan: Boolean): Analysis {
        val body = JSONObject().put("url", url).put("deepScan", deepScan).toString()
        return request(ANALYZE_PATH, body)
    }

    suspend fun poll(scanId: String): Analysis = request("$ANALYZE_PATH/${Uri.encode(scanId)}", null)

    private suspend fun request(path: String, body: String?): Analysis = withContext(Dispatchers.IO) {
        try {
            val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    throw BackendException(errorDetail(text))
                }
                parseAnalysis(JSONObject(text))
            } finally {
                connection.disconnect()
            }
        } catch (ex: IOException) {
            throw BackendException(null, ex)
        } catch (ex: JSONException) {
            throw BackendException(null, ex)
        }
    }

    private fun errorDetail(text: String): String? = try {
        JSONObject(text).optString("detail").ifEmpty { null }
    } catch (_: JSONException) {
        null
    }

    private fun parseAnalysis(json: JSONObject): Analysis {
        val findings = json.getJSONArray("findings")
        return Analysis(
            host = json.getString("host"),
            verdict = json.getString("verdict"),
            score = json.getInt("score"),
            findings = List(findings.length()) { findings.getJSONObject(it).getString("message") },
            contentScan = json.getString("contentScan"),
            scanId = if (json.isNull("scanId")) null else json.getString("scanId"),
        )
    }

    private companion object {
        const val ANALYZE_PATH = "/api/v1/analyze"
        const val CONNECT_TIMEOUT_MS = 5_000

        // El POST puede esperar a los dos servicios externos, con 5 s de timeout cada uno.
        const val READ_TIMEOUT_MS = 20_000
    }
}
