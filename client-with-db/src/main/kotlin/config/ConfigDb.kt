package org.example.config

data class ConfigDb(
    val pathWithoutFileName: String,
    val maxUnreceivedEvents: Int,
    val numberOfFiles: Int
) {
    companion object {
        private const val MAX_UNRECEIVED_EVENTS_LIMIT = 10000
        private const val MIN_NUMBER_OF_FILES = 2
        private const val MAX_NUMBER_OF_FILES = 12

        private const val ENV_PATH_WITHOUT_FILE_NAME = "URL_DB"
        private const val ENV_MAX_UNRECEIVED_EVENTS = "MAX_UNRECEIVED_EVENTS"
        private const val ENV_NUMBER_OF_FILES ="NUMBER_OF_FILES"

        fun readEnv(): ConfigDb  = ConfigDb(
            pathWithoutFileName = System.getenv(ENV_PATH_WITHOUT_FILE_NAME) ?: "",
            maxUnreceivedEvents = readMaxUnreceivedEvents(),
            numberOfFiles = readNumberOfFiles()
        )

        private fun readMaxUnreceivedEvents(): Int {
            val maxUnreceivedEventsString = System.getenv(ENV_MAX_UNRECEIVED_EVENTS)
            require(!maxUnreceivedEventsString.isNullOrBlank()) {
                "Max unreceived events '$ENV_MAX_UNRECEIVED_EVENTS' is missed in the env" }

            val maxUnreceivedEvents = maxUnreceivedEventsString.toInt()
            require(maxUnreceivedEvents in 1..MAX_UNRECEIVED_EVENTS_LIMIT) {
                "max unreceived events must be between 1 and $MAX_UNRECEIVED_EVENTS_LIMIT"
            }
            return maxUnreceivedEvents
        }

        private fun readNumberOfFiles(): Int {
            val numberOfFilesString = System.getenv(ENV_NUMBER_OF_FILES)
            require(!numberOfFilesString.isNullOrBlank()) {
                "Number of files '$ENV_NUMBER_OF_FILES' is missed in the env" }

            val numberOfFiles = numberOfFilesString.toInt()
            require(numberOfFiles in MIN_NUMBER_OF_FILES..MAX_NUMBER_OF_FILES) {
                "number of files must be between $MIN_NUMBER_OF_FILES and $MAX_NUMBER_OF_FILES"
            }
            return numberOfFiles
        }
    }
}
