package com.google.android.systemui.smartspace;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.TextView;

import com.android.systemui.bcsmartspace.R;

import java.util.Calendar;
import java.util.Random;

public class QuickspaceMessagingManager {
    private static final String TAG = "QuickspaceMessagingManager";
    
    private static final int MORNING_START = 5;
    private static final int MORNING_END = 12;
    private static final int NOON_START = 12;
    private static final int NOON_END = 17;
    private static final int EVENING_START = 17;
    private static final int EVENING_END = 21;
    private static final int NIGHT_START = 21;
    private static final int NIGHT_END = 5;
    
    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final Random mRandom;
    private String mCurrentMessage;
    private SettingsObserver mSettingsObserver;
    
    public interface OnMessageChangedListener {
        void onMessageChanged(String message);
    }
    
    private OnMessageChangedListener mListener;
    
    public QuickspaceMessagingManager(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mRandom = new Random();
        mSettingsObserver = new SettingsObserver(new Handler());
    }
    
    public void setOnMessageChangedListener(OnMessageChangedListener listener) {
        mListener = listener;
    }
    
    public void startListening() {
        mSettingsObserver.observe();
        updateMessage();
    }
    
    public void stopListening() {
        mContentResolver.unregisterContentObserver(mSettingsObserver);
    }
    
    public boolean isEnabled() {
        return Settings.Secure.getInt(mContentResolver, 
            Settings.Secure.QUICKSPACE_PSA_ENABLED, 0) == 1;
    }
    
    public String getCurrentMessage() {
        if (!isEnabled()) {
            return null;
        }
        if (TextUtils.isEmpty(mCurrentMessage)) {
            updateMessage();
        }
        return mCurrentMessage;
    }
    
    public void updateMessage() {
        if (!isEnabled()) {
            mCurrentMessage = null;
            notifyMessageChanged();
            return;
        }
        
        String[] messages = getMessagesForCurrentTime();
        if (messages != null && messages.length > 0) {
            mCurrentMessage = messages[mRandom.nextInt(messages.length)];
        } else {
            mCurrentMessage = null;
        }
        notifyMessageChanged();
    }
    
    private String[] getMessagesForCurrentTime() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        
        if (hour >= MORNING_START && hour < MORNING_END) {
            return mContext.getResources().getStringArray(R.array.quickspace_psa_morning);
        } else if (hour >= NOON_START && hour < NOON_END) {
            return mContext.getResources().getStringArray(R.array.quickspace_psa_noon);
        } else if (hour >= EVENING_START && hour < EVENING_END) {
            return mContext.getResources().getStringArray(R.array.quickspace_psa_early_evening);
        } else if (hour >= NIGHT_START || hour < NIGHT_END) {
            return mContext.getResources().getStringArray(R.array.quickspace_psa_midnight);
        }
        
        return mContext.getResources().getStringArray(R.array.quickspace_psa_random);
    }
    
    private void notifyMessageChanged() {
        if (mListener != null) {
            mListener.onMessageChanged(mCurrentMessage);
        }
    }
    
    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        
        void observe() {
            mContentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.QUICKSPACE_PSA_ENABLED),
                false, this
            );
        }
        
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateMessage();
        }
    }
}