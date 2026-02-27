package com.example.locationmapapp.core

/**
 * Sealed hierarchy for all domain-level exceptions in LocationMapApp.
 *
 * Using a sealed class keeps error handling exhaustive and keeps the
 * business layer decoupled from raw platform exceptions.
 */
sealed class AppException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** Thrown when location permission is absent or GMS registration fails. */
    class LocationException(message: String, cause: Throwable? = null) :
        AppException(message, cause)

    /** Thrown when a network call returns a non-2xx status or times out. */
    class NetworkException(message: String, cause: Throwable? = null) :
        AppException(message, cause)

    /** Thrown when JSON/XML parsing of an API response fails. */
    class ParseException(message: String, cause: Throwable? = null) :
        AppException(message, cause)
}
