package com.goldsignal.app

import org.json.JSONObject

data class Signal(
    val time: Long,
    /** BUY, SELL or NO TRADE. */
    val signal: String,
    val entry: Double?,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val slPips: Int,
    val tpPips: Int,
    val trend: String,
    val timeframe: String,
    val reason: String,
    /** Low, Medium or High. */
    val confidence: String,
    /** Thumbnail file name in the history folder. */
    val image: String? = null,
) {
    val isBuy get() = signal == BUY
    val isSell get() = signal == SELL

    fun toJson(): JSONObject = JSONObject()
        .put("time", time)
        .put("signal", signal)
        .put("entry", entry ?: JSONObject.NULL)
        .put("stopLoss", stopLoss ?: JSONObject.NULL)
        .put("takeProfit", takeProfit ?: JSONObject.NULL)
        .put("slPips", slPips)
        .put("tpPips", tpPips)
        .put("trend", trend)
        .put("timeframe", timeframe)
        .put("reason", reason)
        .put("confidence", confidence)
        .put("image", image ?: JSONObject.NULL)

    companion object {
        const val BUY = "BUY"
        const val SELL = "SELL"
        const val NO_TRADE = "NO TRADE"

        fun fromJson(o: JSONObject) = Signal(
            time = o.getLong("time"),
            signal = o.getString("signal"),
            entry = o.doubleOrNull("entry"),
            stopLoss = o.doubleOrNull("stopLoss"),
            takeProfit = o.doubleOrNull("takeProfit"),
            slPips = o.optInt("slPips", Settings.DEFAULT_SL),
            tpPips = o.optInt("tpPips", Settings.DEFAULT_TP),
            trend = o.optString("trend"),
            timeframe = o.optString("timeframe"),
            reason = o.optString("reason"),
            confidence = o.optString("confidence"),
            image = o.stringOrNull("image"),
        )
    }
}

fun JSONObject.doubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifEmpty { null }
