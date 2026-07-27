package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.WatchInfo
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
    fun parseHomeCity(data: String): String =
        Utils.toAsciiString(data, 2)
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
        val homeCity: String = ""
    )

    private var state = State()

    suspend fun request(slot: Int = 0): String {
        return if (WatchInfo.hasHomeTime && !WatchInfo.hasWorldCities) {
            CachedIO.request("240$slot") { key ->
                IO.request(key)
                // We don't have a specific onReceived for HomeTime, but register 0x24
                // data is structurally same as world cities.
                // For now, returning the raw data or empty and letting onReceived handle it.
                ""
            }
        } else {
            // Use pure function to parse
            val homeCity = HomeTimeIOFunctional.parseHomeCity(
                WorldCitiesIO.request(0)
            )
            state = state.copy(homeCity = homeCity)
            state.homeCity
        }
    }

    suspend fun requestRaw(slot: Int = 0): String {
        return if (WatchInfo.hasHomeTime && !WatchInfo.hasWorldCities) {
            CachedIO.request("240$slot") { key ->
                IO.request(key)
                ""
            }
        } else {
            WorldCitiesIO.request(slot)
        }
    }

    fun onReceived(data: String) {
        // Use pure function to parse
        state = state.copy(homeCity = HomeTimeIOFunctional.parseHomeCity(data))
    }
}
