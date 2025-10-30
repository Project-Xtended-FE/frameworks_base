/*
 * Copyright (C) 2024-2025 Lunaris AOSP
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

package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.State;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;

import java.util.List;

import javax.inject.Inject;

public class MediaInfoTile extends QSTileImpl<State> 
        implements com.android.systemui.media.MediaSessionManager.MediaDataListener {

    public static final String TILE_SPEC = "mediainfo";
    private static final String TAG = "MediaInfoTile";
    private static final int MAX_TEXT_LENGTH = 50;

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_music_note);
    private final com.android.systemui.media.MediaSessionManager mMediaSessionManager;
    private final MediaSessionManager mSystemMediaSessionManager;
    private boolean mIsPlaying = false;
    private String mLastMediaPackage = null;
    private String mCachedTrack = null;
    private String mCachedArtist = null;

    @Inject
    public MediaInfoTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mMediaSessionManager = com.android.systemui.media.MediaSessionManager.Companion.get();
        mSystemMediaSessionManager = (MediaSessionManager) 
                host.getContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @Override
    public State newTileState() {
        return new State();
    }

    @Override
    public void handleClick(@Nullable Expandable expandable) {
        MediaController controller = getActiveMediaController();
        if (controller != null) {
            PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState != null) {
                int state = playbackState.getState();
                if (state == PlaybackState.STATE_PLAYING) {
                    controller.getTransportControls().pause();
                } else if (state == PlaybackState.STATE_PAUSED 
                        || state == PlaybackState.STATE_STOPPED) {
                    controller.getTransportControls().play();
                }
            }
        } else {
            Intent intent = getLongClickIntent();
            mActivityStarter.postStartActivityDismissingKeyguard(intent, 0);
        }
    }

    @Override
    public Intent getLongClickIntent() {
        if (mLastMediaPackage != null) {
            Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(mLastMediaPackage);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                return intent;
            }
        }
        return new Intent(Settings.ACTION_SOUND_SETTINGS);
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        String trackTitle = mMediaSessionManager.getTrackTitle();
        String artist = mMediaSessionManager.getArtist();
        
        boolean hasMedia = mIsPlaying || (trackTitle != null && !trackTitle.equals("Unknown"));
        
        state.icon = mIcon;
        
        if (!hasMedia) {
            state.state = Tile.STATE_UNAVAILABLE;
        } else {
            state.state = mIsPlaying ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        }
        
        if (hasMedia && trackTitle != null) {
            state.label = truncateText(trackTitle, MAX_TEXT_LENGTH);
        } else {
            state.label = mContext.getString(R.string.quick_settings_media_info_no_music);
        }
        
        if (hasMedia && artist != null && !artist.equals("Unknown")) {
            state.secondaryLabel = truncateText(artist, MAX_TEXT_LENGTH);
        } else {
            state.secondaryLabel = "";
        }
        
        if (mIsPlaying && trackTitle != null) {
            state.contentDescription = "Now playing: " + trackTitle 
                    + (artist != null && !artist.equals("Unknown") ? " by " + artist : "");
        } else if (hasMedia && trackTitle != null) {
            state.contentDescription = "Paused: " + trackTitle;
        } else {
            state.contentDescription = mContext.getString(R.string.quick_settings_media_info_no_music);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        if (mMediaSessionManager.isMediaPlaying()) {
            String trackTitle = mMediaSessionManager.getTrackTitle();
            return trackTitle != null ? trackTitle : mContext.getString(R.string.quick_settings_media_info_label);
        } else {
            String trackTitle = mMediaSessionManager.getTrackTitle();
            if (trackTitle != null && !trackTitle.equals("Unknown")) {
                return trackTitle;
            }
        }
        return mContext.getString(R.string.quick_settings_media_info_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening) {
            mMediaSessionManager.addListener(this);
            mIsPlaying = mMediaSessionManager.isMediaPlaying();
            updateLastMediaPackage();
            refreshState();
        } else {
            mMediaSessionManager.removeListener(this);
        }
    }

    @Override
    public void destroy() {
        mMediaSessionManager.removeListener(this);
        super.destroy();
    }

    private MediaController getActiveMediaController() {
        try {
            List<MediaController> controllers = mSystemMediaSessionManager.getActiveSessions(null);
            if (controllers != null) {
                for (MediaController controller : controllers) {
                    PlaybackState playbackState = controller.getPlaybackState();
                    if (playbackState != null) {
                        int state = playbackState.getState();
                        if (state == PlaybackState.STATE_PLAYING 
                                || state == PlaybackState.STATE_PAUSED 
                                || state == PlaybackState.STATE_BUFFERING) {
                            return controller;
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to access media sessions", e);
        } catch (Exception e) {
            Log.e(TAG, "Error getting active media controller", e);
        }
        return null;
    }

    private void updateLastMediaPackage() {
        MediaController controller = getActiveMediaController();
        if (controller != null) {
            mLastMediaPackage = controller.getPackageName();
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text != null && text.length() > maxLength) {
            return text.substring(0, maxLength - 1) + "…";
        }
        return text;
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        boolean wasPlaying = mIsPlaying;
        mIsPlaying = (state == PlaybackState.STATE_PLAYING);
        if (wasPlaying != mIsPlaying) {
            updateLastMediaPackage();
            mHandler.post(() -> refreshState());
        }
    }

    @Override
    public void onMetadataChanged(String track, String artist) {
        boolean trackChanged = (track != null && !track.equals(mCachedTrack)) 
                || (track == null && mCachedTrack != null);
        boolean artistChanged = (artist != null && !artist.equals(mCachedArtist)) 
                || (artist == null && mCachedArtist != null);
        
        if (trackChanged || artistChanged) {
            mCachedTrack = track;
            mCachedArtist = artist;
            updateLastMediaPackage();
            mHandler.post(() -> refreshState());
        }
    }
}