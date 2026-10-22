package com.goldsignal.app

import android.content.Intent
import android.graphics.BitmapFactory
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
    private var jpeg: ByteArray? = null
    private var result: Signal? = null
    private var job: Job? = null

    /** The current screenshot, kept so it survives screen rotation. */
    private val currentFile by lazy { File(cacheDir, "current.jpg") }

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::load)
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
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
            if (savedInstanceState.getBoolean(KEY_HAS_IMAGE) && currentFile.exists()) {
                setImage(currentFile.readBytes())
            }
            savedInstanceState.getString(KEY_RESULT)?.let { showResult(Signal.fromJson(JSONObject(it))) }
        }
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
        outState.putBoolean(KEY_HAS_IMAGE, jpeg != null)
        result?.let { outState.putString(KEY_RESULT, it.toJson().toString()) }
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri != null) load(uri) else toast(getString(R.string.err_image, "nothing was shared"))
    }

    private fun load(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    Images.loadJpeg(this@MainActivity, uri).also { currentFile.writeBytes(it) }
                }
                job?.cancel()
                setImage(bytes)
                result = null
                b.result.root.isVisible = false
                b.status.isVisible = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(getString(R.string.err_image, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun setImage(bytes: ByteArray) {
        jpeg = bytes
        b.preview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        b.preview.isVisible = true
        b.previewHint.isVisible = false
    }

    private fun analyze() {
        val image = jpeg ?: return toast(getString(R.string.need_image))
        val key = settings.apiKey
        if (key == null) {
            toast(getString(R.string.need_key))
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        if (job?.isActive == true) return
        val model = settings.model
        val sl = settings.slPips
        val tp = settings.tpPips

        setBusy(true)
        job = lifecycleScope.launch {
            try {
                val signal = withContext(Dispatchers.IO) {
                    val s = Gemini.analyze(key, model, image, sl, tp)
                    History.add(this@MainActivity, s, image)
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
        if (busy) {
            b.status.setText(R.string.analyzing)
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
        val tp = settings.tpPips
        val perPip = lot * Gold.USD_PER_PIP_PER_LOT
        b.calcResult.text = getString(
            R.string.calc_result,
            Gold.money(perPip),
            Gold.money(perPip * sl), sl,
            Gold.money(perPip * tp), tp,
            String.format(Locale.US, "1 : %.1f", tp.toDouble() / sl),
        )
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val KEY_HAS_IMAGE = "has_image"
        private const val KEY_RESULT = "result"
    }
}
