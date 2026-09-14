package com.frxe.music.core

const val STREAMING_CACHE_BYTES: Long = 2L * 1024L * 1024L * 1024L

fun sleepDeadline(nowMs: Long, minutes: Int): Long =
    nowMs + minutes.coerceAtLeast(0) * 60_000L

fun sleepRemaining(deadlineMs: Long, nowMs: Long): Long =
    (deadlineMs - nowMs).coerceAtLeast(0L)

fun selectAutoDjId(currentId: String?, queueIds: List<String>, candidates: List<String>): String? =
    candidates.firstOrNull { candidate ->
        candidate.isNotBlank() && candidate != currentId && candidate !in queueIds
    }
