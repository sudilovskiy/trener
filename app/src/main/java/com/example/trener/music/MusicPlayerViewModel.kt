package com.example.trener.music

import android.app.Application
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trener.logTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class MusicPlaybackState(
    val isAvailable: Boolean = false,
    val isPlaying: Boolean = false,
    val trackCount: Int = 0,
    val currentTrackTitle: String? = null
)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val playbackDirectory = MusicFolderResolver.resolvePlaybackDirectory(appContext)
    private val mediaPlayerLock = Any()
    private val playlistLock = Any()

    private var playlist: List<File> = emptyList()
    private var currentTrackIndex: Int = -1
    private var mediaPlayer: MediaPlayer? = null

    private val _playbackState = MutableStateFlow(MusicPlaybackState())
    val playbackState: StateFlow<MusicPlaybackState> = _playbackState.asStateFlow()

    init {
        refreshPlaylist()
    }

    fun refreshPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshPlaylistInternal()
        }
    }

    fun playPause() {
        val canResumeCurrentTrack = hasCurrentTrack()
        synchronized(mediaPlayerLock) {
            val player = mediaPlayer
            if (player?.isPlaying == true) {
                runCatching { player.pause() }
                    .onSuccess {
                        updatePlaybackFlags(isPlaying = false)
                    }
                return
            }

            if (player != null && canResumeCurrentTrack) {
                runCatching { player.start() }
                    .onSuccess {
                        updateStateFromCurrentTrack(isPlaying = true)
                    }
                    .onFailure { throwable ->
                        Log.w(logTag, "Failed to resume music playback", throwable)
                    }
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (!ensurePlaylistLoaded()) {
                updatePlaybackFlags(isPlaying = false)
                return@launch
            }
            if (!playCurrentTrackOrFirst()) {
                updatePlaybackFlags(isPlaying = false)
            }
        }
    }

    fun nextTrack() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!ensurePlaylistLoaded()) {
                updatePlaybackFlags(isPlaying = false)
                return@launch
            }
            if (!moveByOneAndPlay(step = 1)) {
                updatePlaybackFlags(isPlaying = false)
            }
        }
    }

    fun previousTrack() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!ensurePlaylistLoaded()) {
                updatePlaybackFlags(isPlaying = false)
                return@launch
            }
            if (!moveByOneAndPlay(step = -1)) {
                updatePlaybackFlags(isPlaying = false)
            }
        }
    }

    fun pausePlayback() {
        synchronized(mediaPlayerLock) {
            val player = mediaPlayer ?: return
            if (!player.isPlaying) {
                return
            }

            runCatching { player.pause() }
                .onSuccess {
                    updatePlaybackFlags(isPlaying = false)
                }
        }
    }

    fun releasePlayback() {
        releaseMediaPlayer()
    }

    override fun onCleared() {
        releaseMediaPlayer()
        super.onCleared()
    }

    private fun ensurePlaylistLoaded(): Boolean {
        if (playlist.isNotEmpty()) {
            return true
        }

        return refreshPlaylistInternal().isNotEmpty()
    }

    private fun discoverPlaylist(): List<File> {
        runCatching {
            if (!playbackDirectory.exists()) {
                playbackDirectory.mkdirs()
            }
        }.onFailure { throwable ->
            Log.w(logTag, "Failed to prepare music directory", throwable)
        }

        if (!playbackDirectory.exists() || !playbackDirectory.isDirectory) {
            return emptyList()
        }

        val files = runCatching { playbackDirectory.listFiles() }
            .getOrNull()
            .orEmpty()
            .filter { file ->
                file.isFile && file.extension.lowercase(Locale.ROOT) in SUPPORTED_AUDIO_EXTENSIONS
            }

        return files.sortedWith(
            compareBy<File> { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.absolutePath.lowercase(Locale.ROOT) }
        )
    }

    private fun refreshPlaylistInternal(): List<File> {
        val updatedPlaylist = discoverPlaylist()
        val currentTrackPath = synchronized(playlistLock) {
            playlist.getOrNull(currentTrackIndex)?.absolutePath
        }

        val resolvedCurrentIndex = if (updatedPlaylist.isEmpty()) {
            -1
        } else {
            currentTrackPath?.let { path ->
                updatedPlaylist.indexOfFirst { it.absolutePath == path }
            }?.takeIf { it >= 0 } ?: -1
        }

        synchronized(playlistLock) {
            playlist = updatedPlaylist
            currentTrackIndex = resolvedCurrentIndex
        }

        if (updatedPlaylist.isEmpty()) {
            releaseMediaPlayer()
        }

        updateState(
            isAvailable = true,
            isPlaying = mediaPlayer?.isPlaying == true,
            trackCount = updatedPlaylist.size,
            currentTrackTitle = updatedPlaylist.getOrNull(resolvedCurrentIndex)?.displayName()
        )

        return updatedPlaylist
    }

    private fun playCurrentTrackOrFirst(): Boolean {
        val resolvedIndex = synchronized(playlistLock) {
            if (playlist.isEmpty()) {
                -1
            } else {
                currentTrackIndex.takeIf { it in playlist.indices } ?: 0
            }
        }

        return resolvedIndex >= 0 && playTrackAt(resolvedIndex, autoPlay = true)
    }

    private fun moveByOneAndPlay(step: Int): Boolean {
        val size = synchronized(playlistLock) { playlist.size }
        if (size == 0) {
            return false
        }

        val startIndex = synchronized(playlistLock) {
            if (currentTrackIndex in 0 until size) {
                currentTrackIndex
            } else if (step >= 0) {
                -1
            } else {
                0
            }
        }

        repeat(size) { offset ->
            val targetIndex = wrapIndex(startIndex + step * (offset + 1), size)
            if (playTrackAt(targetIndex, autoPlay = true)) {
                return true
            }
        }

        return false
    }

    private fun wrapIndex(index: Int, size: Int): Int {
        if (size <= 0) return 0
        val normalized = index % size
        return if (normalized < 0) normalized + size else normalized
    }

    private fun playTrackAt(index: Int, autoPlay: Boolean): Boolean {
        val track = synchronized(playlistLock) {
            playlist.getOrNull(index)
        } ?: return false

        val prepared = synchronized(mediaPlayerLock) {
            val player = ensureMediaPlayer()
            runCatching {
                player.reset()
                player.setDataSource(track.absolutePath)
                player.setOnCompletionListener {
                    viewModelScope.launch(Dispatchers.IO) {
                        advanceToNextTrackFromCompletion()
                    }
                }
                player.setOnErrorListener { _, what, extra ->
                    Log.w(logTag, "Music playback error what=$what extra=$extra for ${track.absolutePath}")
                    viewModelScope.launch(Dispatchers.IO) {
                        advanceToNextTrackFromCompletion()
                    }
                    true
                }
                player.prepare()
                if (autoPlay) {
                    player.start()
                }
            }.onFailure { throwable ->
                Log.w(logTag, "Failed to load music track ${track.absolutePath}", throwable)
            }.isSuccess
        }

        if (!prepared) {
            return false
        }

        synchronized(playlistLock) {
            currentTrackIndex = index
        }

        if (autoPlay) {
            updateState(
                isAvailable = true,
                isPlaying = true,
                trackCount = synchronized(playlistLock) { playlist.size },
                currentTrackTitle = track.displayName()
            )
        } else {
            updateStateFromCurrentTrack(isPlaying = false)
        }

        return true
    }

    private suspend fun advanceToNextTrackFromCompletion() {
        if (!ensurePlaylistLoaded()) {
            updatePlaybackFlags(isPlaying = false, trackCount = 0, currentTrackTitle = null)
            return
        }

        val size = synchronized(playlistLock) { playlist.size }
        if (size == 0) {
            updatePlaybackFlags(isPlaying = false, trackCount = 0, currentTrackTitle = null)
            return
        }

        if (!moveByOneAndPlay(step = 1)) {
            updatePlaybackFlags(isPlaying = false)
        }
    }

    private fun ensureMediaPlayer(): MediaPlayer {
        synchronized(mediaPlayerLock) {
            mediaPlayer?.let { return it }

            return MediaPlayer().also { player ->
                player.setOnCompletionListener {
                    viewModelScope.launch(Dispatchers.IO) {
                        advanceToNextTrackFromCompletion()
                    }
                }
                player.setOnErrorListener { _, what, extra ->
                    Log.w(logTag, "Music player error what=$what extra=$extra")
                    viewModelScope.launch(Dispatchers.IO) {
                        advanceToNextTrackFromCompletion()
                    }
                    true
                }
                mediaPlayer = player
            }
        }
    }

    private fun releaseMediaPlayer() {
        synchronized(mediaPlayerLock) {
            val player = mediaPlayer ?: return
            mediaPlayer = null
            runCatching { player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        updatePlaybackFlags(isPlaying = false)
    }

    private fun updatePlaybackFlags(
        isAvailable: Boolean = true,
        isPlaying: Boolean = _playbackState.value.isPlaying,
        trackCount: Int = _playbackState.value.trackCount,
        currentTrackTitle: String? = _playbackState.value.currentTrackTitle
    ) {
        _playbackState.value = MusicPlaybackState(
            isAvailable = isAvailable,
            isPlaying = isPlaying,
            trackCount = trackCount,
            currentTrackTitle = currentTrackTitle
        )
    }

    private fun updateStateFromCurrentTrack(isPlaying: Boolean) {
        val currentTrack = synchronized(playlistLock) {
            playlist.getOrNull(currentTrackIndex)
        }

        updateState(
            isAvailable = true,
            isPlaying = isPlaying,
            trackCount = synchronized(playlistLock) { playlist.size },
            currentTrackTitle = currentTrack?.displayName()
        )
    }

    private fun hasCurrentTrack(): Boolean {
        return synchronized(playlistLock) {
            currentTrackIndex in playlist.indices && playlist.isNotEmpty()
        }
    }

    private fun updateState(
        isAvailable: Boolean,
        isPlaying: Boolean,
        trackCount: Int,
        currentTrackTitle: String?
    ) {
        _playbackState.value = MusicPlaybackState(
            isAvailable = isAvailable,
            isPlaying = isPlaying,
            trackCount = trackCount,
            currentTrackTitle = currentTrackTitle
        )
    }

    private fun File.displayName(): String {
        return nameWithoutExtension.ifBlank { name }
    }

    private companion object {
        private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "aac",
            "flac",
            "m4a",
            "mid",
            "midi",
            "mp3",
            "ogg",
            "wav",
            "3gp"
        )
    }
}
