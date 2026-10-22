package com.goldsignal.app

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.goldsignal.app.databinding.ViewSignalBinding

/** Fills the shared signal card used on the main screen and in the history list. */
fun ViewSignalBinding.show(s: Signal) {
    val ctx = root.context
    signal.text = s.signal
    signal.backgroundTintList = ColorStateList.valueOf(
        ContextCompat.getColor(
            ctx,
            when {
                s.isBuy -> R.color.buy
                s.isSell -> R.color.sell
                else -> R.color.no_trade
            }
        )
    )
    confidence.text = ctx.getString(R.string.confidence, s.confidence)
    confidence.setTextColor(
        ContextCompat.getColor(
            ctx,
            when (s.confidence) {
                "High" -> R.color.buy
                "Medium" -> R.color.medium
                else -> R.color.sell
            }
        )
    )
    val none = ctx.getString(R.string.no_level)
    entry.text = s.entry?.let(Gold::price) ?: none
    stopLoss.text = s.stopLoss?.let {
        ctx.getString(R.string.level_pips, Gold.price(it), s.slPips, Gold.money(Gold.pipsToPrice(s.slPips)))
    } ?: none
    listOf(tp1, tp2, tp3).forEachIndexed { i, view ->
        val price = s.takeProfits.getOrNull(i)
        val pips = s.tpPips.getOrNull(i)
        view.text = if (price == null || pips == null) none else {
            ctx.getString(R.string.level_pips, Gold.price(price), pips, Gold.money(Gold.pipsToPrice(pips)))
        }
    }
    trend.text = ctx.getString(R.string.trend_tf, s.trend, s.timeframe)
    reason.text = s.reason
}
