package com.courtside.pickleball.player

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class ScreenshotImportResult(
    val newNames: List<String>,
    val skippedExisting: List<String>,
    val failedImageCount: Int
)

suspend fun importPlayerNamesFromScreenshots(
    context: Context,
    imageUris: List<Uri>,
    existingNames: Collection<String>
): ScreenshotImportResult {
    val existingLower = existingNames.map { it.trim().lowercase(Locale.ENGLISH) }.toSet()
    val newNames = LinkedHashSet<String>()
    val newNamesLower = mutableSetOf<String>()
    val skippedExisting = LinkedHashSet<String>()
    var failedImageCount = 0

    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        for (uri in imageUris) {
            val text = runCatching { recognizeText(context, uri, recognizer) }.getOrNull()
            if (text == null) {
                failedImageCount++
                continue
            }
            PlayerNameExtractor.extractNames(text).forEach { name ->
                val lower = name.lowercase(Locale.ENGLISH)
                when {
                    lower in existingLower -> skippedExisting += name
                    lower in newNamesLower -> Unit
                    else -> {
                        newNamesLower += lower
                        newNames += name
                    }
                }
            }
        }
    } finally {
        recognizer.close()
    }

    return ScreenshotImportResult(
        newNames = newNames.toList(),
        skippedExisting = skippedExisting.toList(),
        failedImageCount = failedImageCount
    )
}

private suspend fun recognizeText(
    context: Context,
    uri: Uri,
    recognizer: TextRecognizer
): String = suspendCancellableCoroutine { continuation ->
    val image = InputImage.fromFilePath(context, uri)
    recognizer.process(image)
        .addOnSuccessListener { result -> continuation.resume(result.text) }
        .addOnFailureListener { error -> continuation.resumeWithException(error) }
}
