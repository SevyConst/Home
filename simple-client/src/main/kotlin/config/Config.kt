package org.example.config

import java.net.URI
import kotlin.time.Duration

data class Config (
    val serverUri: URI,
    val connectTimeout: Duration,
    val requestTimeout: Duration,
    val deviceId: String
) {
    companion object {
        private const val ENV_SERVER_URL: String = "SERVER_URL"
        private const val ENV_CONNECT_TIMEOUT: String = "CONNECT_TIMEOUT"
        private const val ENV_REQUEST_TIMEOUT: String = "REQUEST_TIMEOUT"
        private const val ENV_DEVICE_ID: String = "DEVICE_ID"

        fun readEnv(): Config = Config(
                serverUri = readURI(),
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