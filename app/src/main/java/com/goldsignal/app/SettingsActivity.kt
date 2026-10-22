package com.goldsignal.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.goldsignal.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        settings = Settings(this)
        b.toolbar.setNavigationOnClickListener { finish() }

        showKeyState()
        b.getKey.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        }
        b.removeKey.setOnClickListener {
            settings.apiKey = null
            b.keyInput.text = null
            showKeyState()
            Toast.makeText(this, R.string.key_removed, Toast.LENGTH_SHORT).show()
        }

        b.modelInput.setText(settings.model)
        b.slInput.setText(settings.slPips.toString())
        b.tpInput.setText(settings.tpPips.toString())
        explainPips(b.slInput, b.slHelp)
        explainPips(b.tpInput, b.tpHelp)

        b.save.setOnClickListener { save() }
    }

    private fun showKeyState() {
        val has = settings.hasApiKey
        b.keyLayout.helperText = getString(if (has) R.string.key_saved else R.string.key_none)
        b.removeKey.isVisible = has
    }

    private fun explainPips(input: EditText, help: TextView) {
        val update = {
            val pips = input.text.toString().toIntOrNull()
            help.text = if (pips == null) "" else getString(R.string.pips_help, pips, Gold.money(Gold.pipsToPrice(pips)))
        }
        update()
        input.doAfterTextChanged { update() }
    }

    private fun save() {
        val sl = b.slInput.text.toString().toIntOrNull()
        val tp = b.tpInput.text.toString().toIntOrNull()
        if (sl == null || tp == null || sl !in 1..10_000 || tp !in 1..10_000) {
            Toast.makeText(this, R.string.err_pips, Toast.LENGTH_LONG).show()
            return
        }
        settings.slPips = sl
        settings.tpPips = tp
        settings.model = b.modelInput.text.toString()
        val key = b.keyInput.text.toString().trim()
        if (key.isNotEmpty()) settings.apiKey = key
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
