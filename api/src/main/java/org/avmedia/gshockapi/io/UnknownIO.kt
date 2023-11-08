package org.avmedia.gshockapi.io

object UnknownIO {

    suspend fun request(): String {
        return "UNKNOWN"
    }

    fun onReceived(data: String) {
    }
}