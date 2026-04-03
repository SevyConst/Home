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
                readURI(),
                readInterval(),
                readConnectTimeout(),
                readRequestTimeout(),
                readDeviceId()
        )
    
        private fun readURI(): URI {
             return System.getenv(ENV_SERVER_URL)
                 ?.let { URI.create(it) }
                 ?: throw IllegalArgumentException("Server URL is missed")
        }

        private fun readInterval(): Duration {
            return System.getenv(ENV_INTERVAL)
                ?.let { val intervalDuration = Duration.parse(it)
                    require(intervalDuration >= MINIMUM_INTERVAL) {
                        "Interval must be at least $MINIMUM_INTERVAL "
                    }
                    intervalDuration
                }
                ?: throw IllegalArgumentException("Interval is missed")
        }

        private fun readConnectTimeout(): Duration {
            return System.getenv(ENV_CONNECT_TIMEOUT)
                ?.let { Duration.parse(it) }
                ?: throw IllegalArgumentException("connectTimeout is missed")
        }

        private fun readRequestTimeout(): Duration {
            return System.getenv(ENV_REQUEST_TIMEOUT)
                ?.let { Duration.parse(it) }
                ?: throw IllegalArgumentException("requestTimeout is missed")

        }

        private fun readDeviceId(): String {
            val deviceId = System.getenv(ENV_DEVICE_ID)
            if (deviceId.isNullOrBlank()) {
                throw IllegalArgumentException("Device ID is missed")
            }
            return deviceId
        }
    }
}