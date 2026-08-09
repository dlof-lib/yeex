package com.yeex.dlof.util

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device caption translation backing [com.yeex.dlof.ui.components.ParagraphCard]'s
 * "ترجمة" rail action — wraps ML Kit's Task-based Language ID + Translate
 * APIs in suspend functions. Everything runs locally on the device: the
 * first use of a given language pair downloads a small (a few MB) model,
 * and every translation after that works fully offline with no API key.
 */
object TranslationUtil {

    /** Best-effort BCP-47 language tag for [text] (e.g. "ar", "en"), or null if undetermined. */
    private suspend fun identifyLanguageTag(text: String): String? = suspendCancellableCoroutine { cont ->
        val client = LanguageIdentification.getClient()
        client.identifyLanguage(text)
            .addOnSuccessListener { tag -> cont.resume(if (tag == "und") null else tag) }
            .addOnFailureListener { cont.resume(null) }
    }

    /**
     * Translates [text] into [targetLanguageTag] (a BCP-47 tag, e.g. from
     * `Locale.getDefault().language`). Auto-detects the source language;
     * returns [text] unchanged (wrapped in success) if it's already in the
     * target language. Fails if the source language can't be identified or
     * either language isn't one ML Kit's Translate API supports.
     */
    suspend fun translate(text: String, targetLanguageTag: String): Result<String> {
        if (text.isBlank()) return Result.success(text)

        val detectedTag = identifyLanguageTag(text)
            ?: return Result.failure(IllegalStateException("Couldn't detect the caption's language"))
        val sourceLang = TranslateLanguage.fromLanguageTag(detectedTag)
            ?: return Result.failure(IllegalStateException("Unsupported source language: $detectedTag"))
        val targetLang = TranslateLanguage.fromLanguageTag(targetLanguageTag)
            ?: TranslateLanguage.ENGLISH

        if (sourceLang == targetLang) return Result.success(text)

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
        )

        return try {
            val translated = suspendCancellableCoroutine<String> { cont ->
                // No Wi-Fi requirement: these models are small and translation
                // is core to reading a post, not a background nice-to-have.
                val conditions = DownloadConditions.Builder().build()
                translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener {
                        translator.translate(text)
                            .addOnSuccessListener { result -> cont.resume(result) }
                            .addOnFailureListener { e -> cont.resumeWithException(e) }
                    }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
                cont.invokeOnCancellation { translator.close() }
            }
            Result.success(translated)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            translator.close()
        }
    }
}
