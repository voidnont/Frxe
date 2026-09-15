# FRXE v0.6.10 Android Auto Design

## Goal

Add first-class Android Auto / Android Automotive browsing and playback to FRXE without removing or replacing any existing phone, Android TV, Cast, voice, download, offline, lyrics, queue, resolver, or system-media behavior.

## Current foundation

FRXE already has a background Media3 playback service, ExoPlayer, `FrxeSystemMediaPlayer`, persisted `PlaybackQueueStore`, playback restoration, queue recovery, Media3 metadata, and the car metadata resource declaring `<uses name="media" />`. v0.6.10 builds on those pieces instead of creating a second playback engine.

## Architecture

Upgrade `FrxePlaybackService` from `MediaSessionService` to `MediaLibraryService` and its session from `MediaSession` to `MediaLibrarySession`. The same ExoPlayer and `FrxeSystemMediaPlayer` remain the underlying player, so phone notifications, Bluetooth controls, queue handling, audio focus, background playback, and v0.6.9 behavior continue unchanged.

Add a focused `AndroidAutoLibrary` browse adapter. It exposes a small driver-safe root with exactly four categories:

1. Library
2. Recently Played
3. Playlists
4. Queue

The adapter reads existing Room/queue data and returns Media3 `MediaItem`s. Browsable nodes set `isBrowsable=true, isPlayable=false`; playable tracks set `isBrowsable=false, isPlayable=true`. Android Auto/AAOS must not depend on pagination because car clients may ignore it, so FRXE returns bounded lists per category.

## Media IDs

Use opaque stable prefixes owned by FRXE:

- Root: `frxe:auto:root`
- Library: `frxe:auto:library`
- Recent: `frxe:auto:recent`
- Playlists: `frxe:auto:playlists`
- Queue: `frxe:auto:queue`
- Playlist: `frxe:auto:playlist:<id>`
- Track: `frxe:auto:track:<trackId>`
- Queue entry: `frxe:auto:queue-item:<entryId>`

A pure `AndroidAutoBrowsePolicy` owns ID parsing and bounded list rules so this behavior is unit-testable without Android framework code.

## Browse behavior

`MediaLibrarySession.Callback` implements:

- `onGetLibraryRoot`: return the FRXE root immediately.
- `onGetChildren`: load the requested category asynchronously from existing data.
- `onGetItem`: return a specific category, playlist, queue entry, or track when resolvable.
- `onSearch` / `onGetSearchResult`: search local library, recent history, current queue, and playlist tracks; deduplicate by track ID and return a bounded result set.

Library uses `LibraryDao.observeAll().first()`. Recent uses `observeHistory().first()` and deduplicates newest-first. Playlists use `observePlaylists().first()` and `playlistTracks(id)`. Queue reads `PlaybackQueueStore.state.value`.

## Playback from Android Auto

Browsed playable items intentionally carry metadata and FRXE IDs, not stale stream URLs. `onAddMediaItems` resolves each requested browse ID back to an existing `Track`, then sends it through the existing `PlaybackStreamResolver` and returns the resolved `Track.toMediaItem()`.

When an external controller changes ExoPlayer to a track that does not match the current FRXE queue item, the existing player listener mirrors that selected track into `PlaybackQueueStore` using normal play-now semantics. It marks the new queue entry as already loaded before the queue flow reacts, preventing a duplicate resolver/set-media cycle. Internal FRXE queue transitions already match the queue and are left untouched.

## Manifest / compatibility

The playback service advertises both:

- `androidx.media3.session.MediaLibraryService`
- `android.media.browse.MediaBrowserService`

The existing `com.google.android.gms.car.application` metadata and `automotive_app_desc.xml` stay intact. Existing foreground-media permissions and media-button receiver stay intact.

## Error handling

Unknown media IDs return a Media3 library/player error rather than throwing through the service. Resolver failures return a failed future so the car surface does not start an invalid stream. Empty categories return an empty successful list. Existing playback recovery remains responsible for network/expired-stream failures after playback starts.

## Version and release

- `versionName`: `0.6.10`
- `versionCode`: `28`
- release filename: `Frxe-0.6.10-android.apk`
- artifact name: `FRXE-0.6.10-APK`

## Verification

Use TDD for browse-ID parsing/list bounding and external-selection queue synchronization policy. Then run `:app:testDebugUnitTest :app:assembleDebug`, review the full PR diff for accidental deletion, squash-merge only after green CI, and verify both the post-merge main test/debug workflow and release APK workflow.

## Global constraint

This release is additive. Existing FRXE functionality must not be removed.
