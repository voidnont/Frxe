package com.frxe.music.voice

sealed interface VoiceCommand {
    data object Play : VoiceCommand
    data object Pause : VoiceCommand
    data object Next : VoiceCommand
    data object Previous : VoiceCommand
    data class SeekBy(val deltaMs: Long) : VoiceCommand
    data class Search(val query: String) : VoiceCommand
    data object Unknown : VoiceCommand
}

object VoiceCommandParser {
    fun parse(raw: String): VoiceCommand {
        val text = raw.lowercase().trim()
        return when {
            text == "play" || text.contains("resume") -> VoiceCommand.Play
            text == "pause" || text.contains("stop music") -> VoiceCommand.Pause
            text.contains("next") || text.contains("skip song") -> VoiceCommand.Next
            text.contains("previous") || text.contains("go back") -> VoiceCommand.Previous
            text.contains("forward") -> VoiceCommand.SeekBy(15_000)
            text.contains("rewind") -> VoiceCommand.SeekBy(-15_000)
            text.startsWith("play ") -> VoiceCommand.Search(text.removePrefix("play ").trim())
            text.startsWith("search ") -> VoiceCommand.Search(text.removePrefix("search ").trim())
            else -> VoiceCommand.Unknown
        }
    }
}
