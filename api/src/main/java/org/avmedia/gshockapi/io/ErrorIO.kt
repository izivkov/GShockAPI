package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber

// ============================================================================
// Pure Functional Core: Error State Management
// ============================================================================

/**
 * Pure functional core for error state operations.
 *
 * All methods are pure: no mutable state, no side effects.
 * Handles error message validation and retrieval.
 */
object ErrorIOFunctional {
    /**
     * Pure validator: Returns the error message or default.
     *
     * No side effects - simple data accessor.
     */
    fun getErrorOrDefault(error: String): String = error.ifEmpty { "ERROR" }
}

@RequiresApi(Build.VERSION_CODES.O)
object ErrorIO {
    private data class State(
        val error: String = "",
    )

    private var state = State()

    suspend fun request(): String = ErrorIOFunctional.getErrorOrDefault(state.error)

    fun onReceived(data: String) {
        state = state.copy(error = data)
        Timber.d("ErrorIO: onReceived: $data")

        if (data.contains("81 13")) {
            SettingsIO.onRunError()
        }
        // "FF 81 xx": the watch rejected command xx. Fail a pending raw read for xx right away
        // instead of letting it wait forever (DW-H5600 does this for 0x51 / 0x15 when busy).
        runCatching { org.avmedia.gshockapi.utils.Utils.toIntArray(data) }.getOrNull()
            ?.takeIf { it.size >= 3 && it[0] == 0xFF && it[1] == 0x81 }
            ?.let { RawSettingIO.onRejected(it[2]) }
        if (data.contains("81 11")) {
            TimeAdjustmentIO.onRunError()
        }
    }
}
