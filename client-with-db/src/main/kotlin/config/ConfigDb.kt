package org.example.config

data class ConfigDb(
    val pathWithoutFileName: String,
    val maxUnreceivedEvents: Int,
    val numberOfFiles: Int
) {
    companion object {
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
                "Max unreceived events '$ENV_MAX_UNRECEIVED_EVENTS' is missed" }
            return maxUnreceivedEventsString.toInt()
        }

        private fun readNumberOfFiles(): Int {
            val numberOfFilesString = System.getenv(ENV_NUMBER_OF_FILES)
            require(!numberOfFilesString.isNullOrBlank()) {
                "Max unreceived events '$ENV_NUMBER_OF_FILES' is missed" }

            val numberOfFiles = numberOfFilesString.toInt()
            require(numberOfFiles in 2..12) {
                "number of files must be between 2 and 12"
            }
            return numberOfFiles
        }
    }
}