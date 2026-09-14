package com.frxe.music.updates

object RuntimeUpdatePolicy {
    const val AUTO_UPDATE_INTERVAL_MS: Long =
        24L * 60L * 60L * 1000L

    fun shouldCheck(
        lastAttemptMs: Long,
        nowMs: Long
    ): Boolean =
        lastAttemptMs <= 0L ||
            nowMs < lastAttemptMs ||
            nowMs - lastAttemptMs >=
            AUTO_UPDATE_INTERVAL_MS
}

object FrxeSupportLinks {
    const val KO_FI =
        "https://ko-fi.com/voidnont"

    const val GITHUB =
        "https://github.com/voidnont/frxe"
}
