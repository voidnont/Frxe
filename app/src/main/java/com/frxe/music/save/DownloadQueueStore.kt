package com.frxe.music.save

import android.content.Context
import android.content.SharedPreferences
import com.frxe.music.source.DownloadedTrackRegistry
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class DownloadEnqueueResult(
    val item: DownloadQueueItem,
    val duplicate: Boolean
)

object DownloadQueueStore {
    private const val PREFS = "frxe_download_queue"
    private const val KEY_ITEMS = "items_v1"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val MAX_PERSISTED_ITEMS = 200

    private val lock = Any()

    @Volatile
    private var initialized = false

    private lateinit var preferences: SharedPreferences

    private val _items = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _wifiOnly = MutableStateFlow(false)
    val wifiOnly = _wifiOnly.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(lock) {
            if (initialized) return

            DownloadedTrackRegistry.initialize(context)

            preferences = context.applicationContext.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

            val restored = decodeItems(
                preferences.getString(KEY_ITEMS, null)
            )

            val recovered = DownloadQueuePolicy.recoverAfterRestart(
                restored,
                System.currentTimeMillis()
            )

            recovered
                .asSequence()
                .filter { item ->
                    item.state == DownloadQueueItemState.Complete &&
                        !item.savedUri.isNullOrBlank()
                }
                .forEach { item ->
                    DownloadedTrackRegistry.register(
                        sourceUrl = item.sourceUrl,
                        localUri = item.savedUri
                    )
                }

            _items.value = recovered
            _wifiOnly.value = preferences.getBoolean(KEY_WIFI_ONLY, false)
            persistLocked(recovered)
            initialized = true
        }
    }

    fun enqueue(request: SaveRequest): DownloadEnqueueResult = synchronized(lock) {
        ensureInitialized()

        val existing = DownloadQueuePolicy.findDuplicate(
            _items.value,
            request.sourceUrl,
            request.format.name,
            request.quality.name
        )

        if (existing != null) {
            return@synchronized DownloadEnqueueResult(
                item = existing,
                duplicate = true
            )
        }

        val now = System.currentTimeMillis()
        val item = DownloadQueueItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = request.sourceUrl,
            title = request.title,
            artist = request.artist,
            format = request.format.name,
            quality = request.quality.name,
            state = DownloadQueueItemState.Queued,
            progress = 0f,
            message = "Queued",
            createdAtMs = now,
            updatedAtMs = now
        )

        replaceLocked(_items.value + item)
        DownloadEnqueueResult(item, duplicate = false)
    }

    fun item(id: String): DownloadQueueItem? = synchronized(lock) {
        _items.value.firstOrNull { it.id == id }
    }

    fun nextRunnable(): DownloadQueueItem? = synchronized(lock) {
        DownloadQueuePolicy.nextRunnable(_items.value)
    }

    fun active(): DownloadQueueItem? = synchronized(lock) {
        _items.value.firstOrNull { it.state == DownloadQueueItemState.Running }
    }

    fun markRunning(id: String) {
        updateItem(id) { item ->
            item.copy(
                state = DownloadQueueItemState.Running,
                progress = 0f,
                message = "Preparing download",
                backend = null,
                updatedAtMs = System.currentTimeMillis()
            )
        }
    }

    fun updateProgress(id: String, state: SaveUiState) {
        updateItem(id) { item ->
            if (item.state != DownloadQueueItemState.Running) {
                item
            } else {
                item.copy(
                    progress = state.progress.coerceIn(0f, 1f),
                    message = state.message,
                    backend = state.backend?.label,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
    }

    fun complete(id: String, result: SaveResult) {
        var sourceUrl: String? = null

        updateItem(id) { item ->
            sourceUrl = item.sourceUrl
            item.copy(
                state = DownloadQueueItemState.Complete,
                progress = 1f,
                message = "Saved to Music/Frxe",
                savedUri = result.uri,
                savedTitle = result.title,
                updatedAtMs = System.currentTimeMillis()
            )
        }

        DownloadedTrackRegistry.register(
            sourceUrl = sourceUrl,
            localUri = result.uri
        )
    }

    fun fail(id: String, message: String) {
        updateItem(id) { item ->
            item.copy(
                state = DownloadQueueItemState.Failed,
                progress = 0f,
                message = message.ifBlank { "Download failed" },
                updatedAtMs = System.currentTimeMillis()
            )
        }
    }

    fun requeueAfterFailure(id: String, message: String) {
        updateItem(id) { item ->
            item.copy(
                state = DownloadQueueItemState.Queued,
                progress = 0f,
                message = message,
                backend = null,
                retryCount = item.retryCount + 1,
                updatedAtMs = System.currentTimeMillis()
            )
        }
    }

    fun pause(id: String) {
        updateItem(id) { item ->
            DownloadQueuePolicy.pause(
                item,
                System.currentTimeMillis()
            )
        }
    }

    fun resume(id: String) {
        updateItem(id) { item ->
            DownloadQueuePolicy.resume(
                item,
                System.currentTimeMillis()
            )
        }
    }

    fun retry(id: String) {
        updateItem(id) { item ->
            if (item.state == DownloadQueueItemState.Failed) {
                DownloadQueuePolicy.resume(
                    item.copy(retryCount = 0),
                    System.currentTimeMillis()
                )
            } else {
                item
            }
        }
    }

    fun cancel(id: String) {
        updateItem(id) { item ->
            if (item.state == DownloadQueueItemState.Complete) {
                item
            } else {
                item.copy(
                    state = DownloadQueueItemState.Cancelled,
                    progress = 0f,
                    message = "Cancelled",
                    backend = null,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
    }

    fun remove(id: String) = synchronized(lock) {
        ensureInitialized()
        val item = _items.value.firstOrNull { it.id == id } ?: return@synchronized
        if (item.state == DownloadQueueItemState.Running) return@synchronized
        replaceLocked(_items.value.filterNot { it.id == id })
    }

    fun pauseAll() = synchronized(lock) {
        ensureInitialized()
        val now = System.currentTimeMillis()
        replaceLocked(
            _items.value.map { item ->
                when (item.state) {
                    DownloadQueueItemState.Queued,
                    DownloadQueueItemState.Running ->
                        DownloadQueuePolicy.pause(item, now)

                    else -> item
                }
            }
        )
    }

    fun resumeAll() = synchronized(lock) {
        ensureInitialized()
        val now = System.currentTimeMillis()
        replaceLocked(
            _items.value.map { item ->
                if (item.state == DownloadQueueItemState.Paused) {
                    DownloadQueuePolicy.resume(item, now)
                } else {
                    item
                }
            }
        )
    }

    fun hasRunnableWork(): Boolean = synchronized(lock) {
        _items.value.any { it.state == DownloadQueueItemState.Queued }
    }

    fun hasUnfinishedWork(): Boolean = synchronized(lock) {
        _items.value.any {
            it.state in setOf(
                DownloadQueueItemState.Queued,
                DownloadQueueItemState.Running,
                DownloadQueueItemState.Paused
            )
        }
    }

    fun isDownloaded(sourceUrl: String): Boolean = synchronized(lock) {
        DownloadQueuePolicy.isDownloaded(_items.value, sourceUrl)
    }

    fun setWifiOnly(enabled: Boolean) = synchronized(lock) {
        ensureInitialized()
        _wifiOnly.value = enabled
        preferences.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    private fun updateItem(
        id: String,
        transform: (DownloadQueueItem) -> DownloadQueueItem
    ) = synchronized(lock) {
        ensureInitialized()
        var changed = false
        val updated = _items.value.map { item ->
            if (item.id == id) {
                val next = transform(item)
                changed = changed || next != item
                next
            } else {
                item
            }
        }
        if (changed) replaceLocked(updated)
    }

    private fun replaceLocked(items: List<DownloadQueueItem>) {
        val bounded = if (items.size <= MAX_PERSISTED_ITEMS) {
            items
        } else {
            val protected = items.filter {
                it.state in setOf(
                    DownloadQueueItemState.Queued,
                    DownloadQueueItemState.Running,
                    DownloadQueueItemState.Paused
                )
            }
            val history = items.filterNot { it in protected }
                .takeLast((MAX_PERSISTED_ITEMS - protected.size).coerceAtLeast(0))
            (history + protected).sortedBy { it.createdAtMs }
        }

        _items.value = bounded
        persistLocked(bounded)
    }

    private fun persistLocked(items: List<DownloadQueueItem>) {
        if (!::preferences.isInitialized) return

        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("sourceUrl", item.sourceUrl)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("format", item.format)
                    .put("quality", item.quality)
                    .put("state", item.state.name)
                    .put("progress", item.progress.toDouble())
                    .put("message", item.message)
                    .put("backend", item.backend ?: JSONObject.NULL)
                    .put("retryCount", item.retryCount)
                    .put("savedUri", item.savedUri ?: JSONObject.NULL)
                    .put("savedTitle", item.savedTitle ?: JSONObject.NULL)
                    .put("createdAtMs", item.createdAtMs)
                    .put("updatedAtMs", item.updatedAtMs)
            )
        }

        preferences.edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }

    private fun decodeItems(raw: String?): List<DownloadQueueItem> {
        if (raw.isNullOrBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val state = runCatching {
                        DownloadQueueItemState.valueOf(json.getString("state"))
                    }.getOrDefault(DownloadQueueItemState.Failed)

                    val id = json.optString("id").trim()
                    val sourceUrl = json.optString("sourceUrl").trim()
                    if (id.isBlank() || sourceUrl.isBlank()) continue

                    add(
                        DownloadQueueItem(
                            id = id,
                            sourceUrl = sourceUrl,
                            title = json.optString("title"),
                            artist = json.optString("artist"),
                            format = json.optString("format", SaveFormat.MP3.name),
                            quality = json.optString("quality", SaveQuality.Mp3K320.name),
                            state = state,
                            progress = json.optDouble("progress", 0.0)
                                .toFloat()
                                .coerceIn(0f, 1f),
                            message = json.optString("message", "Queued"),
                            backend = nullableString(json, "backend"),
                            retryCount = json.optInt("retryCount", 0).coerceAtLeast(0),
                            savedUri = nullableString(json, "savedUri"),
                            savedTitle = nullableString(json, "savedTitle"),
                            createdAtMs = json.optLong("createdAtMs", 0L),
                            updatedAtMs = json.optLong("updatedAtMs", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun nullableString(json: JSONObject, key: String): String? =
        if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() }

    private fun ensureInitialized() {
        check(initialized) {
            "DownloadQueueStore.initialize(context) must be called before use."
        }
    }
}
