package com.goldsignal.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.goldsignal.app.databinding.ActivityHistoryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HistoryActivity : AppCompatActivity() {

    private lateinit var b: ActivityHistoryBinding
    private val adapter = HistoryAdapter(onDelete = { s ->
        History.remove(this, s)
        refresh()
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.history)
        b.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.clear) confirmClear()
            true
        }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        refresh()
    }

    private fun refresh() {
        val items = History.all(this)
        adapter.submit(items)
        b.empty.isVisible = items.isEmpty()
        b.toolbar.subtitle = if (items.isEmpty()) null else resources.getQuantityString(R.plurals.signal_count, items.size, items.size)
    }

    private fun confirmClear() {
        if (adapter.itemCount == 0) return
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.clear_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                History.clear(this)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
