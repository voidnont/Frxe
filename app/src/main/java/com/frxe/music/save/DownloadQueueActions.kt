package com.frxe.music.save

import android.content.Context
import android.net.Uri
import java.io.File

object DownloadQueueActions {

    fun removeDownloadedFile(
        context: Context,
        itemId: String
    ): Boolean {
        val item = DownloadQueueStore.item(itemId) ?: return false
        val savedUri = item.savedUri ?: return false

        val deleted = runCatching {
            val uri = Uri.parse(savedUri)
            when (uri.scheme?.lowercase()) {
                "content" -> context.contentResolver.delete(uri, null, null) >= 0
                "file" -> uri.path?.let(::File)?.let { file ->
                    !file.exists() || file.delete()
                } ?: false
                else -> false
            }
        }.getOrDefault(false)

        if (deleted) {
            DownloadQueueStore.remove(itemId)
        }

        return deleted
    }

    fun redownload(
        context: Context,
        itemId: String
    ): DownloadEnqueueResult? {
        val item = DownloadQueueStore.item(itemId) ?: return null
        val request = item.toSaveRequest() ?: return null

        item.savedUri?.let {
            removeDownloadedFile(context, itemId)
        }

        // If the file was already missing, remove the completed queue record anyway
        // so duplicate prevention allows a fresh request.
        DownloadQueueStore.remove(itemId)
        return FrxeDownloadService.enqueue(context, request)
    }

    private fun DownloadQueueItem.toSaveRequest(): SaveRequest? {
        val parsedFormat = runCatching {
            SaveFormat.valueOf(format)
        }.getOrNull() ?: return null

        val parsedQuality = runCatching {
            SaveQuality.valueOf(quality)
        }.getOrNull() ?: return null

        return SaveRequest(
            sourceUrl = sourceUrl,
            title = title,
            artist = artist,
            format = parsedFormat,
            quality = parsedQuality
        )
    }
}
