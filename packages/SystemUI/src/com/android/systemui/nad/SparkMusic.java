/*
* Copyright (C) 2015 The Android Open Source Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.nad;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Outline;
import android.graphics.PorterDuff.Mode;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.UserHandle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.media.NotificationMediaManager;
import lineageos.util.palette.Palette;
import com.android.internal.graphics.ColorUtils;

public class SparkMusic extends RelativeLayout implements NotificationMediaManager.MediaListener, Palette.PaletteAsyncListener {
   private static final boolean DEBUG = true;
   private static final String TAG = "SparkMusic";

   private Context mContext;
   private NotificationMediaManager mMediaManager;
   private MediaController mMediaController;
   private final Handler mHandler = new Handler();

   private CharSequence mMediaTitle;
   private CharSequence mMediaArtist;
   private Drawable mMediaArtwork;
   private boolean mMediaIsVisible;

   private TextView mTitle;
   private TextView mArtist;
   private ImageView mArtwork;
   private int shadow;
   private int colorArtwork;
   private int colorTextIcons;

   private ImageButton mPrevious;
   private ImageButton mPlayPause;
   private ImageButton mNext;

   private SparkMusic mBackground;

   public SparkMusic(Context context) {
       this(context, null);
   }

   public SparkMusic(Context context, AttributeSet attrs) {
       this(context, attrs, 0);
   }

   public SparkMusic(Context context, AttributeSet attrs, int defStyleAttr) {
       this(context, attrs, defStyleAttr, 0);
   }

   public SparkMusic(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
       super(context, attrs, defStyleAttr, defStyleRes);
       if (DEBUG) Log.d(TAG, "New Instance");
   }

   public void initDependencies(NotificationMediaManager mediaManager, Context context) {
      mContext = context;
      mMediaManager = mediaManager;
      if (mMediaManager != null) {
          mMediaManager.addCallback(this);
      }
      updateObjects();
   }

   /**
    * Called whenever new media metadata is available.
    * @param metadata New metadata.
    */
   @Override
   public void onPrimaryMetadataOrStateChanged(MediaMetadata mediaMetadata, int state) {
      if (DEBUG) Log.d(TAG, "onPrimaryMetadataOrStateChanged: metadata=" + (mediaMetadata != null) + ", state=" + state);
      
      CharSequence title = null;
      CharSequence artist = null;
      Drawable artwork = null;
      
      if (mediaMetadata != null) {
          title = mediaMetadata.getText("android.media.metadata.TITLE");
          artist = mediaMetadata.getText("android.media.metadata.ARTIST");
          
          Bitmap artworkBitmap = mediaMetadata.getBitmap("android.media.metadata.ALBUM_ART");
          if (artworkBitmap != null) {
              artwork = new BitmapDrawable(mContext.getResources(), artworkBitmap);
          }
      }

      mMediaTitle = title;
      mMediaArtist = artist;
      mMediaArtwork = artwork;

      update();
   }

   public void update() {
      if (DEBUG) Log.d(TAG, "update()");
      updateObjects();
      updateButtons();
      updateViews();
      updateIconPlayPause();
   }

   public void updateIconPlayPause() {
       if (DEBUG) Log.d(TAG, "updateIconPlayPause()");
       
       if (mMediaManager != null && mPlayPause != null) {
           try {
               if (!mMediaManager.getPlaybackStateIsEqual(PlaybackState.STATE_PLAYING)) {
                   mPlayPause.setImageResource(R.drawable.ic_play_arrow_white);
               } else {
                   mPlayPause.setImageResource(R.drawable.ic_pause_white);
               }
           } catch (Exception e) {
               Log.e(TAG, "Error updating play/pause icon", e);
               mPlayPause.setImageResource(R.drawable.ic_play_arrow_white);
           }
       }
   }

   public void updateObjects() {
      if (mTitle == null || mArtist == null || mArtwork == null || mPrevious == null || mPlayPause == null || mNext == null) {
          mArtwork = findViewById(R.id.artwork);
          mTitle = (TextView) findViewById(R.id.title);
          mArtist = (TextView) findViewById(R.id.artist);
          mPrevious = findViewById(R.id.button_previous);
          mPlayPause = findViewById(R.id.button_play_pause);
          mNext = findViewById(R.id.button_next);
      }
   }

   public void updateButtons() {
       if (mMediaManager == null) return;
       
       if (mPrevious != null) {
           mPrevious.setOnClickListener(v -> {
                try {
                    mMediaManager.skipTrackPrevious();
                } catch (Exception e) {
                    Log.e(TAG, "Error skipping to previous track", e);
                }
           });
       }

       if (mPlayPause != null) {
           mPlayPause.setOnClickListener(v -> {
                try {
                    mMediaManager.playPauseTrack();
                } catch (Exception e) {
                    Log.e(TAG, "Error toggling play/pause", e);
                }
           });
       }

       if (mNext != null) {
           mNext.setOnClickListener(v -> {
                try {
                    mMediaManager.skipTrackNext();
                } catch (Exception e) {
                    Log.e(TAG, "Error skipping to next track", e);
                }
           });
       }
   }

   public void updateViews() {
       if (DEBUG) Log.d(TAG, "updateViews()");
       
       if (mContext == null) {
           Log.w(TAG, "Context is null, hiding view");
           setVisibility(View.GONE);
           return;
       }

       boolean show = Settings.System.getIntForUser(mContext.getContentResolver(),
               Settings.System.MUSIC_VOLUME_PANEL_DIALOG, 0, UserHandle.USER_CURRENT) != 0;

       if (mMediaManager != null && mMediaTitle != null && mMediaArtist != null && mMediaArtwork != null
           && mTitle != null && mArtist != null && mArtwork != null) {
           
           try {
               mTitle.setText(mMediaTitle.toString());
               mTitle.setSelected(true);
               mArtist.setText(mMediaArtist.toString());
               mArtist.setSelected(true);

               mArtwork.setImageDrawable(mMediaArtwork);

               MediaMetadata metadata = mMediaManager.getMediaMetadata();
               if (metadata != null) {
                   Bitmap artworkBitmap = metadata.getBitmap("android.media.metadata.ALBUM_ART");
                   if (artworkBitmap != null) {
                       Palette.generateAsync(artworkBitmap, this);
                   }
               }
               
               setVisibility(show ? View.VISIBLE : View.GONE);
           } catch (Exception e) {
               Log.e(TAG, "Error updating views", e);
               setVisibility(View.GONE);
           }
       } else {
           if (DEBUG) {
               Log.d(TAG, "Missing required objects: mediaManager=" + (mMediaManager != null) +
                     ", title=" + (mMediaTitle != null) + ", artist=" + (mMediaArtist != null) +
                     ", artwork=" + (mMediaArtwork != null) + ", views=" + 
                     (mTitle != null && mArtist != null && mArtwork != null));
           }
           setVisibility(View.GONE);
       }
       
       if (mArtwork != null) {
           mArtwork.setClipToOutline(true);
       }
   }

   @Override
   public void onGenerated(Palette palette) {
       if (DEBUG) Log.d(TAG, "onGenerated()");
       
       if (palette == null) {
           Log.w(TAG, "Palette is null, using default colors");
           return;
       }

       shadow = 115;
       colorArtwork = Color.BLACK;
       colorTextIcons = Color.WHITE;

       try {
           colorTextIcons = palette.getLightVibrantColor(colorTextIcons);
           colorArtwork = ColorUtils.setAlphaComponent(palette.getDarkVibrantColor(colorArtwork), shadow);

           if (mArtwork != null) {
               mArtwork.setColorFilter(colorArtwork, Mode.SRC_ATOP);
           }
           if (mTitle != null) {
               mTitle.setTextColor(colorTextIcons);
           }
           if (mArtist != null) {
               mArtist.setTextColor(colorTextIcons);
           }
           if (mPrevious != null) {
               mPrevious.setColorFilter(colorTextIcons);
           }
           if (mPlayPause != null) {
               mPlayPause.setColorFilter(colorTextIcons);
           }
           if (mNext != null) {
               mNext.setColorFilter(colorTextIcons);
           }
       } catch (Exception e) {
           Log.e(TAG, "Error applying palette colors", e);
       }
   }
}