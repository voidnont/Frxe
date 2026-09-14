package com.frxe.music.ui

enum class PlayerAction {
    Shuffle,
    Repeat,
    PlayNext,
    AddToQueue,
    AddToPlaylist,
    Download,
    AddToLibrary,
    Like,
    Details,
    ViewArtist,
    Equalizer,
    ListenTogether,
    AmbientMode
}

data class PlayerActionSpec(
    val action: PlayerAction,
    val label: String,
    val subtitle: String? = null
)

fun playerActionGrid(): List<PlayerActionSpec> = listOf(
    PlayerActionSpec(PlayerAction.Shuffle, "Shuffle"),
    PlayerActionSpec(PlayerAction.Repeat, "Repeat"),
    PlayerActionSpec(PlayerAction.PlayNext, "Play next"),
    PlayerActionSpec(PlayerAction.AddToQueue, "Add to queue"),
    PlayerActionSpec(PlayerAction.AddToPlaylist, "Add to playlist"),
    PlayerActionSpec(PlayerAction.Download, "Herunterladen"),
    PlayerActionSpec(PlayerAction.AddToLibrary, "Zur Bibliothek hinzufügen"),
    PlayerActionSpec(PlayerAction.Like, "Like"),
    PlayerActionSpec(PlayerAction.Details, "Details", "Informationen zum Song anzeigen"),
    PlayerActionSpec(PlayerAction.ViewArtist, "Künstler ansehen"),
    PlayerActionSpec(PlayerAction.Equalizer, "Equalizer", "Den Audio-Equalizer einstellen"),
    PlayerActionSpec(PlayerAction.ListenTogether, "Gemeinsam hören"),
    PlayerActionSpec(PlayerAction.AmbientMode, "Ambient Mode", "Für ein immersives Klangerlebnis")
)
