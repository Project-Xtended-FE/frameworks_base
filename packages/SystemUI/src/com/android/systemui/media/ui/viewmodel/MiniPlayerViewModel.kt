/*
 * Copyright (C) 2025 Rising-Revived OSS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.media.ui.viewmodel

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import androidx.lifecycle.ViewModel
import com.android.systemui.res.R
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MiniPlayerViewModel"

data class MediaState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val hasActiveMedia: Boolean = false,
    val packageName: String? = null
)

class MiniPlayerViewModel @AssistedInject constructor(
    private val context: Context,
    private val mediaSessionManager: MediaSessionManager,
) : ViewModel() {

    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState: StateFlow<MediaState> = _mediaState.asStateFlow()

    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaState()
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            updateMediaState()
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers)
    }

    init {
        runCatching {
            val controllers = mediaSessionManager.getActiveSessions(null)
            updateActiveController(controllers)
        }.onFailure { e ->
            Log.e(TAG, "Failed to get active media sessions", e)
            _mediaState.value = MediaState()
        }

        runCatching {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, null)
        }.onFailure { e ->
            Log.e(TAG, "Failed to register session listener", e)
        }
    }

    private fun updateActiveController(controllers: MutableList<MediaController>?) {
        activeController?.unregisterCallback(controllerCallback)

        activeController = controllers?.firstOrNull()

        activeController?.registerCallback(controllerCallback)

        updateMediaState()
    }

    private fun updateMediaState() {
        val controller = activeController
        if (controller != null) {
            val metadata = controller.metadata
            val playbackState = controller.playbackState

            _mediaState.value = MediaState(
                title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                    ?: context.getString(R.string.media_unknown_track),
                artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                    ?: context.getString(R.string.media_unknown_artist),
                isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
                hasActiveMedia = true,
                packageName = controller.packageName
            )
        } else {
            _mediaState.value = MediaState()
        }
    }

    fun playPause() {
        val controller = activeController ?: return
        val playbackState = controller.playbackState?.state

        runCatching {
            when (playbackState) {
                PlaybackState.STATE_PLAYING -> controller.transportControls.pause()
                PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_STOPPED,
                null -> controller.transportControls.play()
                else -> controller.transportControls.play()
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to toggle playback", e)
        }
    }

    fun skipToNext() {
        runCatching {
            activeController?.transportControls?.skipToNext()
        }.onFailure { e ->
            Log.e(TAG, "Failed to skip to next", e)
        }
    }

    fun skipToPrevious() {
        runCatching {
            activeController?.transportControls?.skipToPrevious()
        }.onFailure { e ->
            Log.e(TAG, "Failed to skip to previous", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeController?.unregisterCallback(controllerCallback)
        runCatching {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        }.onFailure { e ->
            Log.e(TAG, "Failed to unregister session listener", e)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): MiniPlayerViewModel
    }
}