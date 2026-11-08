package com.example.persiandub

import android.content.Context
import android.net.Uri
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Segment(val startMs: Long, val endMs: Long, val en: String, val fa: String)

class Pipeline(private val context: Context) {
    private var model: Model? = null

    private suspend fun loadModel(): Model = model ?: suspendCancellableCoroutine { cont ->
        StorageService.unpack(
            context, "model-en-us", "model",
            StorageService.Callback<Model> { m -> model = m; cont.resume(m) },
            StorageService.Callback<IOException> { e -> cont.resumeWithException(e) }
        )
    }

    /** English speech -> timed English text -> Persian text. */
    suspend fun run(uri: Uri, onStatus: (String) -> Unit): List<Segment> =
        withContext(Dispatchers.Default) {
            onStatus("در حال بارگذاری مدل تشخیص گفتار…")
            val rec = Recognizer(loadModel(), 16000f).apply { setWords(true) }

            val raw = mutableListOf<Triple<Long, Long, String>>()
            fun handle(json: String) {
                val obj = JSONObject(json)
                val text = obj.optString("text")
                val words = obj.optJSONArray("result")
                if (text.isBlank() || words == null || words.length() == 0) return
                val start = (words.getJSONObject(0).getDouble("start") * 1000).toLong()
                val end = (words.getJSONObject(words.length() - 1).getDouble("end") * 1000).toLong()
                raw += Triple(start, end, text)
            }

            onStatus("در حال استخراج و تشخیص گفتار انگلیسی…")
            AudioExtractor.extractPcm16k(context, uri) { bytes, len ->
                if (rec.acceptWaveForm(bytes, len)) handle(rec.result)
            }
            handle(rec.finalResult)
            rec.close()

            onStatus("در حال آماده‌سازی ترجمه (بار اول مدل دانلود می‌شود)…")
            val translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.PERSIAN)
                    .build()
            )
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()

            onStatus("در حال ترجمه…")
            val result = raw.map { (s, e, en) -> Segment(s, e, en, translator.translate(en).await()) }
            translator.close()
            result
        }
}
