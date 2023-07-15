package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object WatchConditionIO {

    class WatchConditionValue (val batteryLevel: Int, val temperature: Int)

    suspend fun request(): WatchConditionValue {
        return CachedIO.request("28", ::getWatchCondition) as WatchConditionValue
    }

    private suspend fun getWatchCondition(key: String): WatchConditionValue {

        CasioIO.request(key)

        val deferredResult = CompletableDeferred<WatchConditionValue>()
        CachedIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        CachedIO.subscribe("CASIO_WATCH_CONDITION") { keyedData: JSONObject ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            CachedIO.resultQueue.dequeue(key)?.complete(WatchConditionDecoder.decodeValue(data))
        }

        return deferredResult.await()
    }

    fun toJson(data: String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", CachedIO.createKey(data)).put("value", data)
        json.put("CASIO_WATCH_CONDITION", dataJson)
        return json
    }

    object WatchConditionDecoder {
        fun decodeValue(data: String): WatchConditionValue {
            if (data == null) {
                return WatchConditionValue(0, 0)
            }
            val intArr = Utils.toIntArray(data)
            val bytes = Utils.byteArrayOfIntArray(intArr.drop(1).toIntArray())

            if (bytes != null && bytes.size >= 2) {
                // Battery level between 15 and 20 fot B2100 and between 13 and 18 for B5600. Scale accordingly to %
                var batteryLevel = bytes[0].toInt() - if (WatchInfo.model == WatchInfo.WATCH_MODEL.B2100) 15 else 13

                val batteryLevelPercent: Int = (batteryLevel * 20).coerceIn(0, 100)
                val temperature: Int = bytes[1].toInt()

                return WatchConditionValue(batteryLevelPercent, temperature)
            }

            return WatchConditionValue(0, 0)
        }
    }
}