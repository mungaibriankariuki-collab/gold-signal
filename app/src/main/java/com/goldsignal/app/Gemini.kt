package com.goldsignal.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class GeminiException(message: String) : IOException(message)

/** Sends chart screenshots (one or more timeframes) to the Gemini API and turns the answer into a [Signal]. */
object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    fun analyze(apiKey: String, model: String, jpegs: List<ByteArray>, slPips: Int, tpPips: List<Int>): Signal {
        val parts = JSONArray().put(JSONObject().put("text", prompt(jpegs.size, slPips, tpPips)))
        jpegs.forEachIndexed { i, jpeg ->
            parts.put(JSONObject().put("text", "Screenshot ${i + 1}:"))
            parts.put(JSONObject().put("inline_data", JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))))
        }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.2)
                .put("responseMimeType", "application/json")
                .put("responseSchema", schema()))

        val answer = JSONObject(post(apiKey, model, body.toString()))
        val candidate = answer.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw GeminiException(
                answer.optJSONObject("promptFeedback")?.stringOrNull("blockReason")
                    ?.let { "Gemini refused the image ($it)." } ?: "Gemini returned no answer. Try again."
            )
        val out = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = (0 until out.length())
            .map { out.getJSONObject(it) }
            .filterNot { it.optBoolean("thought") }
            .joinToString("") { it.optString("text") }
            .trim().removePrefix("```json").removeSuffix("```").trim()
        if (text.isEmpty()) {
            throw GeminiException("Gemini returned an empty answer (${candidate.optString("finishReason")}). Try again.")
        }
        val r = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw GeminiException("Gemini's answer couldn't be read. Try again.")
        }
        return toSignal(r, slPips, tpPips)
    }

    private fun toSignal(r: JSONObject, slPips: Int, tpPips: List<Int>): Signal {
        val signal = when (r.optString("signal").uppercase(Locale.US).replace('_', ' ').trim()) {
            Signal.BUY -> Signal.BUY
            Signal.SELL -> Signal.SELL
            else -> Signal.NO_TRADE
        }
        val entry = r.doubleOrNull("entry")?.takeIf { it > 0 }
        // Stop and targets are set from the pip settings so they are always exactly the chosen distance.
        val dir = when {
            entry == null -> 0
            signal == Signal.BUY -> 1
            signal == Signal.SELL -> -1
            else -> 0
        }
        val stop = if (dir == 0) null else entry!! - dir * Gold.pipsToPrice(slPips)
        val targets = if (dir == 0) emptyList() else tpPips.map { entry!! + dir * Gold.pipsToPrice(it) }
        return Signal(
            time = System.currentTimeMillis(),
            signal = signal,
            entry = entry,
            stopLoss = stop,
            takeProfits = targets,
            slPips = slPips,
            tpPips = tpPips,
            trend = r.optString("trend").ifBlank { "Unclear" },
            timeframe = timeframes(r.optJSONArray("timeframes")),
            reason = r.optString("reason").trim(),
            confidence = r.optString("confidence").ifBlank { "Low" },
        )
    }

    private val tfOrder = listOf("M1", "M5", "M15", "M30", "H1", "H4", "D1", "W1", "MN")

    /** "D1 Up · H4 Up · M15 Sideways", highest timeframe first. */
    private fun timeframes(arr: JSONArray?): String = arr.toList { getJSONObject(it) }
        .map { it.optString("timeframe").uppercase(Locale.US).trim() to it.optString("trend") }
        .filter { it.first.isNotEmpty() }
        .sortedByDescending { tfOrder.indexOf(it.first) }
        .joinToString(" · ") { (tf, trend) ->
            tf + " " + when (trend) {
                "Uptrend" -> "Up"
                "Downtrend" -> "Down"
                else -> "Sideways"
            }
        }
        .ifEmpty { "?" }

    private fun prompt(count: Int, slPips: Int, tpPips: List<Int>): String {
        val sl = Gold.price(Gold.pipsToPrice(slPips))
        val tps = tpPips.map { Gold.price(Gold.pipsToPrice(it)) }
        val tp = tps.first()
        val tpList = tpPips.indices.joinToString(", ") { "TP${it + 1} = ${tpPips[it]} pips (${tps[it]})" }
        return """
            You are a careful trading analyst. You are given $count MetaTrader 5 screenshot(s) that should show gold (XAUUSD) price charts, usually the same moment on different timeframes (M1, M5, M15, M30, H1, H4, D1).

            For each screenshot, read the timeframe from the chart label and the trend and market structure (higher highs and higher lows, or lower highs and lower lows). Then do a top-down analysis:
            - D1 and H4 set the direction. Only trade in that direction.
            - H1 and M30 give the key support and resistance zones.
            - M15, M5 and M1 time the entry: price at or reacting from a zone, with the lower timeframe turning in the trade direction.
            Read the current price (the price label or line on the right-hand axis) from the lowest-timeframe screenshot.

            Units: 1 pip = 0.10 of price (a move from 2400.00 to 2400.10 is 1 pip).
            The trader uses a fixed stop loss of $slPips pips ($sl in price) and three take profits: $tpList.
            For a BUY: stop = entry - $sl, each target = entry + its distance. For a SELL: stop = entry + $sl, each target = entry - its distance.

            Decide BUY, SELL or NO TRADE. Choose NO TRADE when:
            - the trend is unclear, the higher timeframes disagree, or price is in the middle of a range,
            - the lower timeframes have not confirmed an entry yet,
            - a support or resistance level sits between the entry and TP1 ($tp away),
            - a stop $sl away would sit inside recent normal price swings,
            - or no screenshot is a readable gold chart (say so in the reason).

            entry: the price to enter at, normally the current price. Read it from the price axis and do not invent precision. Use null if no price can be read.
            trend: the overall direction from the higher timeframes: Uptrend, Downtrend or Sideways.
            timeframes: one entry per screenshot with its timeframe (M1, M5, M15, M30, H1, H4, D1, or "?" if not visible) and its own trend.
            reason: 2 to 4 short sentences about what is visible on the chart that supports the decision, including which take profits look reachable.
            confidence: High only when trend, level and recent candles all agree; Medium when most agree; Low otherwise.
        """.trimIndent()
    }

    private fun schema(): JSONObject {
        fun str(vararg values: String) = JSONObject().put("type", "STRING").apply {
            if (values.isNotEmpty()) put("enum", JSONArray(values.toList()))
        }
        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject()
                .put("signal", str(Signal.BUY, Signal.SELL, Signal.NO_TRADE))
                .put("entry", JSONObject().put("type", "NUMBER").put("nullable", true))
                .put("trend", str("Uptrend", "Downtrend", "Sideways"))
                .put("timeframes", JSONObject()
                    .put("type", "ARRAY")
                    .put("items", JSONObject()
                        .put("type", "OBJECT")
                        .put("properties", JSONObject()
                            .put("timeframe", str())
                            .put("trend", str("Uptrend", "Downtrend", "Sideways")))
                        .put("required", JSONArray(listOf("timeframe", "trend")))))
                .put("reason", str())
                .put("confidence", str("Low", "Medium", "High")))
            .put("required", JSONArray(listOf("signal", "entry", "trend", "timeframes", "reason", "confidence")))
            .put("propertyOrdering", JSONArray(listOf("timeframes", "trend", "reason", "signal", "entry", "confidence")))
    }

    private fun post(apiKey: String, model: String, body: String): String {
        val url = URL(BASE + URLEncoder.encode(model.trim(), "UTF-8") + ":generateContent")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 120_000
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        // Sent as a header so the key never appears in a URL.
        conn.setRequestProperty("x-goog-api-key", apiKey)
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw GeminiException(errorMessage(code, text, model))
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(code: Int, body: String, model: String): String {
        val msg = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) {
            ""
        }
        return when {
            msg.contains("API key", ignoreCase = true) || code == 401 || code == 403 ->
                "Gemini rejected your API key. Check it in Settings."
            code == 404 -> "Gemini model \"$model\" wasn't found. Change the model in Settings."
            code == 429 -> "Gemini's free limit was reached. Wait a minute and try again."
            code >= 500 -> "Gemini is busy or down (error $code). Try again shortly."
            else -> "Gemini error $code: ${msg.ifEmpty { "unknown" }}"
        }
    }
}
