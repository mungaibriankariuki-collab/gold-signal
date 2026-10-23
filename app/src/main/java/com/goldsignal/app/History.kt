package com.goldsignal.app

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Saved signals, newest first, in history.json with a thumbnail of each screenshot. */
object History {
    private const val MAX = 500

    private fun file(ctx: Context) = File(ctx.filesDir, "history.json")

    private fun thumbs(ctx: Context) = File(ctx.filesDir, "thumbs")

    fun thumbnail(ctx: Context, s: Signal): File? =
        s.image?.let { File(thumbs(ctx), it) }?.takeIf { it.exists() }

    @Synchronized
    fun all(ctx: Context): List<Signal> = try {
        val arr = JSONArray(file(ctx).readText())
        (0 until arr.length()).map { Signal.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun add(ctx: Context, s: Signal, jpegs: List<ByteArray>): Signal {
        val name = "${s.time}.jpg"
        val saved = try {
            thumbs(ctx).mkdirs()
            File(thumbs(ctx), name).writeBytes(Images.strip(jpegs))
            s.copy(image = name)
        } catch (e: Exception) {
            s
        }
        val list = listOf(saved) + all(ctx)
        list.drop(MAX).forEach { old -> thumbnail(ctx, old)?.delete() }
        write(ctx, list.take(MAX))
        return saved
    }

    @Synchronized
    fun remove(ctx: Context, s: Signal) {
        thumbnail(ctx, s)?.delete()
        write(ctx, all(ctx).filterNot { it.time == s.time })
    }

    @Synchronized
    fun clear(ctx: Context) {
        thumbs(ctx).deleteRecursively()
        file(ctx).delete()
    }

    private fun write(ctx: Context, list: List<Signal>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(ctx.filesDir, "history.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file(ctx))
    }
}
