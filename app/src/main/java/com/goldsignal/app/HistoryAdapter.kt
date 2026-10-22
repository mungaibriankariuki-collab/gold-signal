package com.goldsignal.app

import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.goldsignal.app.databinding.ItemHistoryBinding
import java.util.Date

class HistoryAdapter(
    private val onDelete: (Signal) -> Unit,
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    private var items: List<Signal> = emptyList()

    fun submit(list: List<Signal>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val s = items[position]
        val ctx = holder.b.root.context
        val date = Date(s.time)
        holder.b.time.text = "${DateFormat.getMediumDateFormat(ctx).format(date)}  ${DateFormat.getTimeFormat(ctx).format(date)}"
        val thumb = History.thumbnail(ctx, s)
        holder.b.thumb.isVisible = thumb != null
        holder.b.thumb.setImageBitmap(thumb?.let { BitmapFactory.decodeFile(it.path) })
        holder.b.signal.show(s)
        holder.b.delete.setOnClickListener { onDelete(s) }
    }
}
