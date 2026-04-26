package org.example.config

import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class Config (
    val serverUri: URI,
    val interval: Duration,
    val connectTimeout: Duration,
    val requestTimeout: Duration,
    val deviceId: String
) {
    companion object {
        private const val ENV_SERVER_URL: String = "SERVER_URL"
        private const val ENV_INTERVAL: String = "INTERVAL"
        private const val ENV_CONNECT_TIMEOUT: String = "CONNECT_TIMEOUT"
        private const val ENV_REQUEST_TIMEOUT: String = "REQUEST_TIMEOUT"
        private const val ENV_DEVICE_ID: String = "DEVICE_ID"

        val MINIMUM_INTERVAL: Duration = 30.seconds

        fun readEnv(): Config = Config(
                serverUri = readURI(),
                interval = readInterval(),
                connectTimeout = readConnectTimeout(),
                requestTimeout = readRequestTimeout(),
                deviceId = readDeviceId()
        )
    
        private fun readURI(): URI {
            val serverUrlString = System.getenv(ENV_SERVER_URL)
            require(!serverUrlString.isNullOrBlank())  {
                "Server URL '$ENV_SERVER_URL' is missed"
            }

            return URI.create(serverUrlString)
        }

        private fun readInterval(): Duration {
            val intervalString = System.getenv(ENV_INTERVAL)
            require(!intervalString.isNullOrBlank()) {
                "The interval '$ENV_INTERVAL' is missed"
            }

            val intervalDuration = Duration.parse(intervalString)
            require(intervalDuration >= MINIMUM_INTERVAL) {
                "Interval must be at least $MINIMUM_INTERVAL"
            }

            return intervalDuration
        }

        private fun readConnectTimeout(): Duration {
            val connectTimeoutString = System.getenv(ENV_CONNECT_TIMEOUT)
            require(!connectTimeoutString.isNullOrBlank()) {
                "connectTimeout '$ENV_CONNECT_TIMEOUT' is missed"
            }

            return Duration.parse(connectTimeoutString)
        }

        private fun readRequestTimeout(): Duration {
            val requestTimeout = System.getenv(ENV_REQUEST_TIMEOUT)
            require(!requestTimeout.isNullOrBlank()) {
                "requestTimeout '$ENV_REQUEST_TIMEOUT' is missed"
            }

            return Duration.parse(requestTimeout)
        }

        private fun readDeviceId(): String {
            val deviceId = System.getenv(ENV_DEVICE_ID)
            require(!deviceId.isNullOrBlank()) {
                "deviceId '$ENV_DEVICE_ID' is missed"
            }

            return deviceId
        }
    }
}