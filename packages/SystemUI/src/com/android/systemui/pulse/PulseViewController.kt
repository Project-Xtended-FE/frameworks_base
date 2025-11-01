/*
 * Copyright (C) 2025 The AxionAOSP Project
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
package com.android.systemui.pulse

import android.content.Context
import com.android.systemui.SystemUIApplication
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.media.MediaSessionManager
import com.android.systemui.util.ScrimUtils
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@SysUISingleton
class PulseViewController @Inject constructor(
    private val context: Context
) : PulseAudioDataProcessor.DataListener,
    MediaSessionManager.MediaDataListener,
    ScrimUtils.ScrimEventListener {

    private val mainScope = MainScope()

    private val settingsRepository: PulseSettingsRepository =
        PulseSettingsRepository(context).apply {
            startObserving()
            setOnSettingsChangedListener { onSettingsChanged() }
        }

    private val pulseView: PulseView =
        PulseView(context).apply {
            initialize(settingsRepository)
            setVisibility(false)
        }

    private val audioProcessor: PulseAudioDataProcessor =
        PulseAudioDataProcessor(context).apply {
            setDataListener(this@PulseViewController)
        }

    private var pulseRunning: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            updatePulseDisplay(value)
        }

    private var showDelayJob: Job? = null
    private var hideDelayJob: Job? = null
    
    private val PULSE_SHOW_DELAY_MS = 300L
    private val PULSE_HIDE_DELAY_MS = 100L
    private val PULSE_FADE_IN_DURATION_MS = 200L
    private val PULSE_FADE_OUT_DURATION_MS = 150L

    init {
        ScrimUtils.get().addListener(this)
        MediaSessionManager.get().addListener(this)
    }

    fun getPulseView(): PulseView = pulseView

    private fun updatePulseState() {
        val shouldShow = shouldShowPulse
        showDelayJob?.cancel()
        hideDelayJob?.cancel()
        
        if (shouldShow && !pulseRunning) {
            showDelayJob = mainScope.launch {
                delay(PULSE_SHOW_DELAY_MS)
                if (shouldShowPulse && !pulseRunning) {
                    pulseRunning = true
                }
            }
        } else if (!shouldShow && pulseRunning) {
            hideDelayJob = mainScope.launch {
                delay(PULSE_HIDE_DELAY_MS)
                if (!shouldShowPulse && pulseRunning) {
                    pulseRunning = false
                }
            }
        }
    }

    private fun updatePulseDisplay(show: Boolean) {
        mainScope.launch {
            if (show) {
                audioProcessor.startCapture()
                pulseView.fadeIn(PULSE_FADE_IN_DURATION_MS)
            } else {
                pulseView.fadeOut(PULSE_FADE_OUT_DURATION_MS) {
                    audioProcessor.stopCapture()
                }
            }
        }
    }

    private fun onSettingsChanged() {
        mainScope.launch { updatePulseState() }
    }

    val isRunning: Boolean
        get() = pulseRunning

    val shouldShowPulse: Boolean
        get() {
            val pulseEnabled = settingsRepository.isPulseEnabled()
            val keyguardShowing = ScrimUtils.get().isKeyguardShowing()
            val mediaPlaying = MediaSessionManager.get().isMediaPlaying
            val isDozing = ScrimUtils.get().isDozing()
            val isPulsing = ScrimUtils.get().isPulsing()
            
            if (!pulseEnabled || !mediaPlaying) return false
            
            if (isDozing || isPulsing) {
                return settingsRepository.isPulseShowOnAmbient()
            }
            
            return keyguardShowing
        }

    override fun onDataUpdate(data: PulseData) {
        if (shouldShowPulse) {
            mainScope.launch { pulseView.updateVisualizerData(data) }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        mainScope.launch { updatePulseState() }
    }

    override fun onMediaColorsChanged(color: Int) {
        if (settingsRepository.isPulseEnabled()) {
            pulseView.onMediaColorsChanged(color)
        }
    }

    override fun onKeyguardShowingChanged(showing: Boolean) {
        mainScope.launch { updatePulseState() }
    }

    override fun onKeyguardFadingAwayChanged(fadingAway: Boolean) {
        showDelayJob?.cancel()
        hideDelayJob?.cancel()
        pulseRunning = false
    }

    override fun onKeyguardGoingAwayChanged(goingAway: Boolean) {
        showDelayJob?.cancel()
        hideDelayJob?.cancel()
        pulseRunning = false
    }

    override fun onScreenTurnedOff() {
        showDelayJob?.cancel()
        hideDelayJob?.cancel()
        pulseRunning = false
    }

    override fun onStartedWakingUp() {
        mainScope.launch { updatePulseState() }
    }

    override fun onDozingChanged() {
        mainScope.launch { updatePulseState() }
    }

    override fun setPulsing(pulsing: Boolean) {
        mainScope.launch { updatePulseState() }
    }

    fun destroy() {
        showDelayJob?.cancel()
        hideDelayJob?.cancel()
        mainScope.cancel()
        settingsRepository.stopObserving()
        ScrimUtils.get().removeListener(this)
        MediaSessionManager.get().removeListener(this)
    }

    companion object {
        private const val TAG = "PulseViewController"

        @JvmStatic
        fun get(context: Context): PulseViewController {
            val app = context.applicationContext as SystemUIApplication
            return app.sysUIComponent.pulseViewController()
        }
    }
}