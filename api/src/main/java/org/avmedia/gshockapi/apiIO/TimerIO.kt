package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.TimerDecoder
import org.avmedia.gshockapi.casio.TimerEncoder
import org.avmedia.gshockapi.casio.WatchFactory
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object TimerIO {

    suspend fun request(): Int {
        return ApiIO.request("18", ::getTimer) as Int
    }

    private suspend fun getTimer(key: String): Int {

        CasioIO.request(key)

        fun getTimer(data: String): String {
            return TimerDecoder.decodeValue(data)
        }

        var deferredResult = CompletableDeferred<Int>()
        ApiIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        ApiIO.subscribe("CASIO_TIMER") { keyedData: JSONObject ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            ApiIO.resultQueue.dequeue(key)?.complete(getTimer(data).toInt())
        }

        return deferredResult.await()
    }

    fun set(timerValue: Int) {
        ApiIO.cache.remove("18")
        Connection.sendMessage("{action: \"SET_TIMER\", value: $timerValue}")
    }

    fun toJson(data: String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", ApiIO.createKey(data)).put("value", data)
        json.put("CASIO_TIMER", dataJson)
        return json
    }
    fun sendToWatch(message:String) {
        WatchFactory.watch.writeCmd(
            0x000c,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_TIMER.code.toByte())
        )
    }
    fun sendToWatchSet(message:String) {
        val seconds = JSONObject(message).get("value").toString()
        WatchFactory.watch.writeCmd(0x000e, TimerEncoder.encode(seconds))
    }
}