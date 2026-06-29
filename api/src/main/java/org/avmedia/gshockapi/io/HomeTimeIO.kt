package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

// ============================================================================
// Pure Functional Core: Home Time Data Processing
// ============================================================================

/**
 * Pure functional core for home time processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles home city data transformations.
 */
@RequiresApi(Build.VERSION_CODES.O)
object HomeTimeIOFunctional {
    /**
     * Pure parser: Extracts home city name from world cities data.
     * 
     * Converts raw city data (starting at index 2) to ASCII string.
     * No side effects - pure string transformation.
     */
    fun parseHomeCity(data: String): String {
        // Data format: 2400xx..xx or 2401xx..xx where xx is the city name
        // The city name starts at byte 2 (index 4 in hex string)
        return Utils.toAsciiString(data, 2)
    }
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Home Time IO handler with state management.
 * 
 * Provides access to the primary home city timezone.
 * Uses pure functional core for data parsing.
 */
@RequiresApi(Build.VERSION_CODES.O)
object HomeTimeIO {
    private data class State(
        val homeCity: String = "",
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(slot: Int = 0): String {
        val key = "240$slot"
        val homeCity = HomeTimeIOFunctional.parseHomeCity(
            CachedIO.request(key) { getHomeTime(key) }
        )
        state = state.copy(homeCity = homeCity)
        return state.homeCity
    }

    private suspend fun getHomeTime(key: String): String {
        state = state.copy(deferredResult = CompletableDeferred())
        IO.request(key)
        return state.deferredResult?.await() ?: ""
    }

    fun onReceived(data: String) {
        state.deferredResult?.complete(data)
        // Use pure function to parse
        state = state.copy(homeCity = HomeTimeIOFunctional.parseHomeCity(data))
    }
}
