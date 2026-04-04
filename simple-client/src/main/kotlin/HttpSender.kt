package org.example

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import model.EventRequest
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class HttpSender(
    private val serverUri: URI,
    private val connectTimeout: Duration,
    private val requestTimeout: Duration
    ) {

    private val logger = KotlinLogging.logger {}

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout.toJavaDuration())
        .build()

    fun send(eventRequest: EventRequest) {
        val json = Json.encodeToString(eventRequest)
        logger.info { "Sending $json" }

        val request = HttpRequest.newBuilder()
            .uri(serverUri)
            .timeout(requestTimeout.toJavaDuration())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            logger.error(e) { "Failed to send $json" }
            return
        } catch (e: InterruptedException) {
            logger.error(e) { "Failed to send $json" }
            return
        }

        if (response.statusCode() != 200) {
            logger.error { "Http response code: ${response.statusCode()}, response body: ${response.body()}" }
            return
        }

        logger.info { "Http response code: ${response.statusCode()}, response body: ${response.body()}" }
    }

}