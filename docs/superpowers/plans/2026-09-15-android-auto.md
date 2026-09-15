# FRXE v0.6.10 Android Auto Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Android Auto / Android Automotive browsing and playback to FRXE using the existing Media3 playback engine and persisted FRXE data.

**Architecture:** Upgrade the existing playback service/session to `MediaLibraryService` / `MediaLibrarySession`, add a small browse adapter over Room plus `PlaybackQueueStore`, and resolve car-selected media IDs through FRXE's existing `PlaybackStreamResolver`. Preserve the existing ExoPlayer, system-media bridge, queue, recovery, Cast, TV, downloads, lyrics, and phone UI.

**Tech Stack:** Kotlin, Android Media3 1.11.0, ExoPlayer, MediaLibraryService, MediaLibrarySession, Room, Kotlin coroutines/Flow, JUnit 4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-15-android-auto-design.md`

## Global Constraints

- Existing FRXE functionality must not be removed.
- Keep Media3 at the repository's current `1.11.0` dependency line.
- Android Auto browse root has exactly four top-level categories: Library, Recently Played, Playlists, Queue.
- Do not depend on Android Auto pagination; bound category/search results inside FRXE.
- `versionName = "0.6.10"`, `versionCode = 28`.
- Release artifact name is `FRXE-0.6.10-APK`.

---

### Task 1: Android Auto browse ID policy

**Files:**
- Create: `app/src/main/java/com/frxe/music/playback/AndroidAutoBrowsePolicy.kt`
- Test: `app/src/test/java/com/frxe/music/playback/AndroidAutoBrowsePolicyTest.kt`

**Interfaces:**
- Produces: `AndroidAutoBrowsePolicy.ROOT`, `.LIBRARY`, `.RECENT`, `.PLAYLISTS`, `.QUEUE`, `playlistId(Long)`, `trackId(String)`, `queueItemId(String)`, `parse(String): AndroidAutoBrowseTarget?`, and `bound(List<T>, limit: Int): List<T>`.
- Produces sealed targets `Root`, `Library`, `Recent`, `Playlists`, `Queue`, `Playlist(id: Long)`, `Track(id: String)`, `QueueItem(entryId: String)`.

- [ ] **Step 1: Write failing tests** verifying the four root IDs, playlist ID round-trip, track/queue ID round-trip, unknown-ID rejection, and result bounding.

```kotlin
@Test
fun playlistIdRoundTrips() {
    val mediaId = AndroidAutoBrowsePolicy.playlistId(42L)
    assertEquals(
        AndroidAutoBrowseTarget.Playlist(42L),
        AndroidAutoBrowsePolicy.parse(mediaId)
    )
}

@Test
fun boundKeepsNewestPrefixOnly() {
    assertEquals(listOf(1, 2, 3), AndroidAutoBrowsePolicy.bound(listOf(1, 2, 3, 4), 3))
}
```

- [ ] **Step 2: Run `gradle --no-daemon :app:testDebugUnitTest --tests '*AndroidAutoBrowsePolicyTest'` and verify RED** because the policy does not exist.
- [ ] **Step 3: Implement the minimal pure Kotlin policy** with prefix parsing and `take(limit.coerceAtLeast(0))` bounding.
- [ ] **Step 4: Run the focused test and verify GREEN.**
- [ ] **Step 5: Commit** with `feat: add Android Auto browse ID policy`.

### Task 2: Driver-safe FRXE media library adapter

**Files:**
- Create: `app/src/main/java/com/frxe/music/playback/AndroidAutoLibrary.kt`
- Modify only if needed for a one-shot query helper: `app/src/main/java/com/frxe/music/data/AppDatabase.kt`
- Test: extend `AndroidAutoBrowsePolicyTest.kt` only for pure dedupe/bounding helpers; do not introduce Android framework unit-test dependencies.

**Interfaces:**
- Consumes: `AndroidAutoBrowsePolicy`, `FrxeDatabase`, `PlaybackQueueStore`, `Track.toMediaItem()` metadata conventions.
- Produces: `rootItem(): MediaItem`, `suspend fun children(parentId: String): List<MediaItem>`, `suspend fun item(mediaId: String): MediaItem?`, `suspend fun track(mediaId: String): Track?`, `suspend fun search(query: String): List<MediaItem>`.

- [ ] **Step 1: Implement four browsable root category MediaItems** with valid media IDs and `MediaMetadata.isBrowsable=true`, `isPlayable=false`.
- [ ] **Step 2: Implement Library** from `libraryDao().observeAll().first()` and map tracks to playable browse items.
- [ ] **Step 3: Implement Recently Played** from `observeHistory().first()`, newest-first dedupe by `trackId`, maximum 50.
- [ ] **Step 4: Implement Playlists** from `observePlaylists().first()` and playlist children from existing `playlistTracks(id)`.
- [ ] **Step 5: Implement Queue** from `PlaybackQueueStore.state.value.entries`, using queue-entry media IDs so duplicate tracks remain distinguishable.
- [ ] **Step 6: Implement `track(mediaId)`** for track IDs, queue entries, and playlist-contained tracks without creating a second playback source model.
- [ ] **Step 7: Implement local search** across library, recent, queue, and playlist tracks; match title/artist/album case-insensitively, dedupe by track ID, cap at 50.
- [ ] **Step 8: Run `:app:testDebugUnitTest :app:assembleDebug` and commit** as `feat: add Android Auto browse library`.

### Task 3: Upgrade playback service to MediaLibraryService

**Files:**
- Modify: `app/src/main/java/com/frxe/music/playback/FrxePlaybackService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AndroidAutoLibrary`, existing `FrxeSystemMediaPlayer`, `PlaybackStreamResolver`, `PlaybackStateStore`, `PlaybackQueueStore`.
- Produces: a `MediaLibrarySession` returned by `onGetSession()` and car browse callbacks.

- [ ] **Step 1: Change the service superclass** from `MediaSessionService` to `MediaLibraryService`, and session field from `MediaSession?` to `MediaLibrarySession?`; leave the same ExoPlayer and `FrxeSystemMediaPlayer` construction intact.
- [ ] **Step 2: Change callback type** to `MediaLibrarySession.Callback` while retaining the existing `onPlaybackResumption` implementation unchanged.
- [ ] **Step 3: Add `onGetLibraryRoot`** returning `LibraryResult.ofItem(androidAutoLibrary.rootItem(), params)` through `Futures.immediateFuture`.
- [ ] **Step 4: Add asynchronous `onGetChildren` / `onGetItem`** using `SettableFuture`, `serviceScope.launch`, and `LibraryResult.ofItemList(...)` / `LibraryResult.ofItem(...)`; invalid IDs return a parameter/bad-value library error instead of throwing.
- [ ] **Step 5: Add `onSearch` and `onGetSearchResult`** backed by `AndroidAutoLibrary.search(query)`, notifying result count where the Media3 API requires it.
- [ ] **Step 6: Replace `MediaSession.Builder` with `MediaLibrarySession.Builder`** and preserve the existing session activity PendingIntent and callback.
- [ ] **Step 7: Update manifest intent filters** so the service advertises `androidx.media3.session.MediaLibraryService` plus existing `android.media.browse.MediaBrowserService`; retain permissions, media-button receiver, car metadata, and all other services.
- [ ] **Step 8: Run unit/debug build and commit** as `feat: expose FRXE library to Android Auto`.

### Task 4: Resolve car selections through FRXE and keep queue state coherent

**Files:**
- Modify: `app/src/main/java/com/frxe/music/playback/FrxePlaybackService.kt`
- Create: `app/src/main/java/com/frxe/music/playback/ExternalPlaybackSelectionPolicy.kt`
- Test: `app/src/test/java/com/frxe/music/playback/ExternalPlaybackSelectionPolicyTest.kt`

**Interfaces:**
- Produces: pure `ExternalPlaybackSelectionPolicy.shouldMirror(currentQueueTrackId: String?, selectedMediaId: String?): Boolean`.
- Service callback produces resolved MediaItems from `onAddMediaItems`.

- [ ] **Step 1: Write RED tests**: same queue/media ID does not mirror; different nonblank ID mirrors; null/blank selected ID does not mirror.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Implement minimal policy and verify GREEN.**
- [ ] **Step 4: Override `onAddMediaItems`** in the library callback. For each requested item, resolve `androidAutoLibrary.track(mediaId)`, then call existing `playbackResolver.resolve(track)`, returning `resolvedTrack.toMediaItem()` in the future. Return failed future for unknown/unresolvable selections.
- [ ] **Step 5: In the existing player listener, on media-item transition only, mirror an externally selected resolved track into `PlaybackQueueStore.replace(listOf(track), currentTrackId = track.id)` when the pure policy says the selected media item does not match the queue. Set `loadedQueueEntryId` to the returned current entry before the queue flow can re-resolve it.**
- [ ] **Step 6: Run full unit/debug build and commit** as `feat: play Android Auto selections through FRXE resolver`.

### Task 5: Version and release packaging

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/build-apk.yml`

**Interfaces:**
- Produces FRXE `0.6.10` / code `28` and `FRXE-0.6.10-APK`.

- [ ] **Step 1: Set `versionCode = 28` and `versionName = "0.6.10"`.**
- [ ] **Step 2: Rename release copy target to `Frxe-0.6.10-android.apk`.**
- [ ] **Step 3: Rename uploaded artifact to `FRXE-0.6.10-APK`.**
- [ ] **Step 4: Run `:app:testDebugUnitTest :app:assembleDebug` and commit** as `chore: bump FRXE to 0.6.10`.

### Task 6: Review, merge, and main-branch release verification

**Files:**
- Review every changed file in the PR; no new implementation file required.

**Interfaces:**
- Produces a green merged `main` and an installable `FRXE-0.6.10-APK` artifact.

- [ ] **Step 1: Review the full PR patch** and verify no existing FRXE feature, manifest component, permission, service, queue behavior, or UI path was removed.
- [ ] **Step 2: Verify branch GitHub Actions passes `:app:testDebugUnitTest :app:assembleDebug`.**
- [ ] **Step 3: Mark PR ready and squash-merge to `main` using the exact verified head SHA.**
- [ ] **Step 4: Verify the post-merge main test/debug workflow succeeds.**
- [ ] **Step 5: Verify the post-merge release workflow succeeds and uploads `FRXE-0.6.10-APK`.**
- [ ] **Step 6: Record the merge SHA and artifact ID before starting v0.6.11.**
