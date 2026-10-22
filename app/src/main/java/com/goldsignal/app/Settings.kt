package com.goldsignal.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** XAUUSD units: 1 pip = $0.10 price move; 1 lot = 100 oz, so $10 per pip (0.05 lot = $0.50). */
object Gold {
    const val PIP = 0.10
    const val USD_PER_PIP_PER_LOT = 10.0

    fun pipsToPrice(pips: Int) = pips * PIP

    fun money(v: Double) = String.format(Locale.US, "$%,.2f", v)

    fun price(v: Double) = String.format(Locale.US, "%,.2f", v)
}

class Settings(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var slPips: Int
        get() = prefs.getInt("sl_pips", DEFAULT_SL)
        set(v) = prefs.edit().putInt("sl_pips", v).apply()

    /** TP1, TP2 and TP3 in pips. */
    var tpPips: List<Int>
        get() = DEFAULT_TPS.mapIndexed { i, d -> prefs.getInt("tp${i + 1}_pips", d) }
        set(v) {
            val edit = prefs.edit()
            v.forEachIndexed { i, p -> edit.putInt("tp${i + 1}_pips", p) }
            edit.apply()
        }

    var lot: String
        get() = prefs.getString("lot", "0.05")!!
        set(v) = prefs.edit().putString("lot", v).apply()

    var model: String
        get() = prefs.getString("model", null)?.ifBlank { null } ?: DEFAULT_MODEL
        set(v) = prefs.edit().putString("model", v.trim()).apply()

    /** Stored encrypted; null if none is saved or it can no longer be decrypted. */
    var apiKey: String?
        get() = prefs.getString("api_key", null)?.let(SecretBox::decrypt)
        set(v) {
            val edit = prefs.edit()
            if (v.isNullOrBlank()) edit.remove("api_key") else edit.putString("api_key", SecretBox.encrypt(v.trim()))
            edit.apply()
        }

    val hasApiKey get() = apiKey != null

    companion object {
        const val DEFAULT_SL = 120
        val DEFAULT_TPS = listOf(240, 370, 500)
        const val DEFAULT_MODEL = "gemini-flash-latest"
    }
}

/** AES-GCM with a key that lives in the Android Keystore and never leaves it. */
private object SecretBox {
    private const val ALIAS = "gemini_api_key"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String? = try {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_BYTES))
        String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES))
    } catch (e: Exception) {
        null
    }
}
