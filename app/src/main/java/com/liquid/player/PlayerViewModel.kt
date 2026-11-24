package com.liquid.player

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.liquid.player.data.MusicRepository
import com.liquid.player.data.Prefs
import com.liquid.player.data.Track
import com.liquid.player.download.VideoItem
import com.liquid.player.download.YouTubeDownloader
import com.liquid.player.playback.PlaybackService
import com.liquid.player.playback.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DlState {
    data object Idle : DlState
    data class Running(val progress: Float) : DlState
    data object Done : DlState
    data class Failed(val message: String) : DlState
}

data class UiState(
    val tracks: List<Track> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val playCounts: Map<String, Int> = emptyMap(),
    val currentKey: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    val searchResults: List<VideoItem> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val downloads: Map<String, DlState> = emptyMap(),
    val ytReady: Boolean = false,
    /** How many times each track plays before moving on (0 = forever). */
    val repeatTimes: Int = 1,
    val playsDone: Int = 1,
    val repeatAll: Boolean = false,
    /** Sleep timer: epoch ms when playback stops (0 = off). */
    val sleepEndsAt: Long = 0,
    val sleepAfterTrack: Boolean = false,
) {
    val current: Track? get() = tracks.firstOrNull { it.key == currentKey }
    val favoriteTracks: List<Track> get() = tracks.filter { it.key in favorites }
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val player = PlayerHolder.get(app)

    private val _state = MutableStateFlow(UiState(favorites = prefs.favorites()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Order in which the current queue was built, so we can map index -> key. */
    private var queueKeys: List<String> = emptyList()
    private val controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>

    init {
        // Start the media session service so notification / Bluetooth buttons work.
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({}, MoreExecutors.directExecutor())

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val key = mediaItem?.mediaId
                val isRepeat = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                _state.update { it.copy(playsDone = if (isRepeat) it.playsDone + 1 else 1) }
                applyRepeat()
                if (!isRepeat && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _state.value.sleepAfterTrack) {
                    player.pause()
                    _state.update { it.copy(sleepAfterTrack = false) }
                }
                if (key != null && !isRepeat) prefs.incrementPlay(key)
                _state.update {
                    it.copy(
                        currentKey = key,
                        durationMs = player.duration.coerceAtLeast(0),
                        positionMs = 0,
                        playCounts = it.playCounts.toMutableMap().also { m ->
                            if (key != null) m[key] = (m[key] ?: 0) + 1
                        }
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = player.duration.coerceAtLeast(0)) }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _state.update { it.copy(shuffle = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _state.update { it.copy(repeatMode = repeatMode) }
            }
        })

        viewModelScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _state.update { it.copy(positionMs = player.currentPosition) }
                }
                delay(250)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                val ready = YouTubeDownloader.ready
                if (ready != _state.value.ytReady) _state.update { it.copy(ytReady = ready) }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        refresh()
    }

    fun refresh() {
        val app = getApplication<Application>()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                MusicRepository.scan(app, includeDeviceMusic = _state.value.hasPermission)
            }
            val counts = prefs.allPlayCounts(list.map { it.key })
            _state.update { it.copy(tracks = list, playCounts = counts, loading = false) }
        }
    }

    // ---------- playback ----------

    fun play(track: Track, queue: List<Track> = _state.value.tracks) {
        val list = queue.ifEmpty { listOf(track) }
        val index = list.indexOfFirst { it.key == track.key }.coerceAtLeast(0)
        queueKeys = list.map { it.key }
        player.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        player.prepare()
        player.play()
    }

    fun playAllShuffled(list: List<Track>) {
        if (list.isEmpty()) return
        player.shuffleModeEnabled = true
        play(list.random(), list)
    }

    fun togglePlay() {
        if (player.currentMediaItem == null) {
            _state.value.tracks.firstOrNull()?.let { play(it) }
            return
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }

    fun previous() {
        if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) {
            player.seekTo(0)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled }

    /** Re-evaluates ExoPlayer's repeat mode from "play N times" + "repeat list". */
    private fun applyRepeat() {
        val st = _state.value
        val mode = when {
            st.repeatTimes == 0 -> Player.REPEAT_MODE_ONE
            st.playsDone < st.repeatTimes -> Player.REPEAT_MODE_ONE
            st.repeatAll -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        if (player.repeatMode != mode) player.repeatMode = mode
    }

    fun setRepeatTimes(n: Int) {
        _state.update { it.copy(repeatTimes = n.coerceIn(0, 99)) }
        applyRepeat()
    }

    fun toggleRepeatAll() {
        _state.update { it.copy(repeatAll = !it.repeatAll) }
        applyRepeat()
    }

    private var sleepJob: kotlinx.coroutines.Job? = null

    /** Stops playback after [minutes] with a gentle 6 second fade-out. 0 cancels. */
    fun setSleepMinutes(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        player.volume = 1f
        if (minutes <= 0) {
            _state.update { it.copy(sleepEndsAt = 0) }
            return
        }
        val total = minutes * 60_000L
        _state.update { it.copy(sleepEndsAt = System.currentTimeMillis() + total, sleepAfterTrack = false) }
        sleepJob = viewModelScope.launch {
            delay((total - 6_000L).coerceAtLeast(0))
            for (i in 1..30) {
                player.volume = 1f - i / 30f
                delay(200)
            }
            player.pause()
            player.volume = 1f
            _state.update { it.copy(sleepEndsAt = 0) }
        }
    }

    fun setSleepAfterTrack(on: Boolean) {
        if (on) setSleepMinutes(0)
        _state.update { it.copy(sleepAfterTrack = on) }
    }

    fun toggleFavorite(key: String) {
        val next = _state.value.favorites.toMutableSet().also {
            if (!it.add(key)) it.remove(key)
        }
        prefs.setFavorites(next)
        _state.update { it.copy(favorites = next) }
    }

    // ---------- YouTube ----------

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _state.update { it.copy(searching = true, searchError = null, searchResults = emptyList()) }
        viewModelScope.launch {
            try {
                val res = YouTubeDownloader.search(q)
                _state.update {
                    it.copy(
                        searchResults = res,
                        searching = false,
                        searchError = if (res.isEmpty()) "Ничего не найдено" else null
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(searching = false, searchError = t.message ?: "Ошибка поиска")
                }
            }
        }
    }

    fun download(item: VideoItem) {
        val cur = _state.value.downloads[item.id]
        if (cur is DlState.Running) return
        setDl(item.id, DlState.Running(0f))
        viewModelScope.launch {
            try {
                YouTubeDownloader.downloadMp3(getApplication(), item) { p ->
                    setDl(item.id, DlState.Running(p))
                }
                setDl(item.id, DlState.Done)
                refresh()
            } catch (t: Throwable) {
                setDl(item.id, DlState.Failed(t.message ?: "Ошибка загрузки"))
            }
        }
    }

    private fun setDl(id: String, st: DlState) {
        _state.update { it.copy(downloads = it.downloads + (id to st)) }
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(key)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
}
