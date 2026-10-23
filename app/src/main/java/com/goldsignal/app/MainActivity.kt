package com.goldsignal.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.goldsignal.app.databinding.ActivityMainBinding
import com.goldsignal.app.databinding.ItemShotBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.UnknownHostException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var settings: Settings

    /** Screenshots to analyze together, e.g. one per timeframe. */
    private val images = mutableListOf<ByteArray>()
    private var result: Signal? = null
    private var job: Job? = null

    private val pickImages =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { uris ->
            if (uris.isNotEmpty()) load(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        settings = Settings(this)

        b.toolbar.inflateMenu(R.menu.main)
        b.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.history -> startActivity(Intent(this, HistoryActivity::class.java))
                R.id.settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }

        b.pick.setOnClickListener {
            if (images.size >= MAX_IMAGES) {
                toast(getString(R.string.too_many, MAX_IMAGES))
            } else {
                pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        b.clear.setOnClickListener {
            images.clear()
            imagesChanged()
        }
        b.analyze.setOnClickListener { analyze() }

        b.lotInput.setText(settings.lot)
        b.lotInput.doAfterTextChanged {
            settings.lot = it.toString()
            updateCalculator()
        }

        if (savedInstanceState == null) {
            handleShare(intent)
        } else {
            repeat(savedInstanceState.getInt(KEY_IMAGE_COUNT)) { i ->
                currentFile(i).takeIf { it.exists() }?.let { images += it.readBytes() }
            }
            renderImages()
            savedInstanceState.getString(KEY_RESULT)?.let { showResult(Signal.fromJson(JSONObject(it))) }
        }
        if (savedInstanceState == null) renderImages()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        // SL/TP may have changed in Settings.
        updateCalculator()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_IMAGE_COUNT, images.size)
        result?.let { outState.putString(KEY_RESULT, it.toJson().toString()) }
    }

    private fun currentFile(i: Int) = File(cacheDir, "current_$i.jpg")

    private fun handleShare(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            )
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }
                    ?: emptyList()
            else -> return
        }
        if (uris.isNotEmpty()) load(uris) else toast(getString(R.string.err_image, "nothing was shared"))
    }

    /** Adds screenshots to the current set, up to [MAX_IMAGES]. */
    private fun load(uris: List<Uri>) {
        lifecycleScope.launch {
            val room = MAX_IMAGES - images.size
            if (uris.size > room) toast(getString(R.string.too_many, MAX_IMAGES))
            if (room <= 0) return@launch
            try {
                val loaded = withContext(Dispatchers.IO) {
                    uris.take(room).map { Images.loadJpeg(this@MainActivity, it) }
                }
                images += loaded
                imagesChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(getString(R.string.err_image, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun imagesChanged() {
        job?.cancel()
        result = null
        b.result.root.isVisible = false
        b.status.isVisible = false
        renderImages()
        val snapshot = images.toList()
        lifecycleScope.launch(Dispatchers.IO) {
            snapshot.forEachIndexed { i, bytes -> currentFile(i).writeBytes(bytes) }
            for (i in snapshot.size until MAX_IMAGES) currentFile(i).delete()
        }
    }

    private fun renderImages() {
        b.strip.removeAllViews()
        images.forEachIndexed { i, bytes ->
            val item = ItemShotBinding.inflate(layoutInflater, b.strip, false)
            item.image.setImageBitmap(Images.decodeSmall(bytes, 480))
            item.number.text = (i + 1).toString()
            item.remove.setOnClickListener {
                images.removeAt(i)
                imagesChanged()
            }
            b.strip.addView(item.root)
        }
        val has = images.isNotEmpty()
        b.stripScroll.isVisible = has
        b.previewHint.isVisible = !has
        b.clear.isVisible = has
        b.count.isVisible = has
        b.count.text = getString(R.string.image_count, images.size, MAX_IMAGES)
        b.pick.setText(if (has) R.string.add_more else R.string.pick)
    }

    private fun analyze() {
        if (images.isEmpty()) return toast(getString(R.string.need_image))
        val key = settings.apiKey
        if (key == null) {
            toast(getString(R.string.need_key))
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        if (job?.isActive == true) return
        val shots = images.toList()
        val model = settings.model
        val sl = settings.slPips
        val tp = settings.tpPips

        setBusy(true)
        job = lifecycleScope.launch {
            try {
                val signal = withContext(Dispatchers.IO) {
                    val s = Gemini.analyze(key, model, shots, sl, tp)
                    History.add(this@MainActivity, s, shots)
                }
                b.status.isVisible = false
                showResult(signal)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnknownHostException) {
                showError(getString(R.string.err_offline))
            } catch (e: Exception) {
                showError(e.message ?: e.javaClass.simpleName)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun showResult(s: Signal) {
        result = s
        b.result.show(s)
        b.result.root.isVisible = true
    }

    private fun setBusy(busy: Boolean) {
        b.progress.isVisible = busy
        b.analyze.isEnabled = !busy
        b.pick.isEnabled = !busy
        b.clear.isEnabled = !busy
        if (busy) {
            b.status.text = getString(R.string.analyzing, images.size)
            b.status.setTextColor(b.disclaimer.currentTextColor)
            b.status.isVisible = true
            b.result.root.isVisible = false
        }
    }

    private fun showError(msg: String) {
        b.status.text = msg
        b.status.setTextColor(getColor(R.color.sell))
        b.status.isVisible = true
    }

    private fun updateCalculator() {
        val lot = b.lotInput.text?.toString()?.toDoubleOrNull()
        if (lot == null || lot <= 0) {
            b.calcResult.setText(R.string.calc_invalid)
            return
        }
        val sl = settings.slPips
        val perPip = lot * Gold.USD_PER_PIP_PER_LOT
        val lines = mutableListOf(
            getString(R.string.calc_per_pip, Gold.money(perPip)),
            getString(R.string.calc_risk, Gold.money(perPip * sl), sl),
        )
        settings.tpPips.forEachIndexed { i, tp ->
            lines += getString(
                R.string.calc_reward, i + 1, Gold.money(perPip * tp), tp,
                String.format(Locale.US, "1 : %.1f", tp.toDouble() / sl),
            )
        }
        b.calcResult.text = lines.joinToString("\n")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        /** M1, M5, M15, M30, H1, H4, D1. */
        const val MAX_IMAGES = 7
        private const val KEY_IMAGE_COUNT = "image_count"
        private const val KEY_RESULT = "result"
    }
}
