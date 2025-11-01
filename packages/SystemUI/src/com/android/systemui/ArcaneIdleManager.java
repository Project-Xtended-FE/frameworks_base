/*
 * Copyright (C) 2019 Descendant
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

package com.android.systemui;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ArcaneIdleManager {
    private static final String TAG = "ArcaneIdleManager";
    private static final long IDLE_TIME_NEEDED = TimeUnit.HOURS.toMillis(1); // 1 hour
    private static final long ALARM_BUFFER_TIME = TimeUnit.MINUTES.toMillis(15); // 15 minutes
    private static final long MIN_DELAY = 100; // Minimum delay in ms

    private static Set<String> PROTECTED_PACKAGE_PATTERNS = new HashSet<>(Arrays.asList(
        ".android",
        "android",
        ".settings",
        ".google",
        ".mgoogle",
        "gms",
        ".GoogleCamera",
        ".whatsapp",
        ".telegram",
        ".dialer",
        ".phone",
        ".contacts",
        ".messaging",
        ".mms",
        ".ims",
        ".launcher",
        ".systemui",
        ".inputmethod",
        ".keyboard",
        ".camera",
        ".gallery",
        ".photos",
        ".music",
        ".security",
        ".faceunlock",
        ".gamespace",
        ".gamebar",
        ".dolby",
        ".glyph",
        ".miui",
        ".oneplus",
        ".samsung",
        ".xiaomi",
        ".nfc",
        ".bluetooth",
        ".location",
        ".provider",
        ".zhihu",
        ".ugc"
    ));

    private final Context mContext;
    private final Handler mHandler;
    private final ActivityManager mActivityManager;
    private final AlarmManager mAlarmManager;

    private Runnable mServiceKillerRunnable;
    private Runnable mHaltManagerRunnable;

    private static volatile ArcaneIdleManager sInstance;
    private static final Object sLock = new Object();

    private boolean mIsExecuting = false;

    private ArcaneIdleManager(@NonNull Context context) {
        this.mContext = context.getApplicationContext();
        this.mHandler = new Handler(Looper.getMainLooper());
        this.mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        initializeRunnables();
    }

    public static void initManager(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (sLock) {
                if (sInstance == null) {
                    sInstance = new ArcaneIdleManager(context);
                }
            }
        }
    }

    @Nullable
    public static ArcaneIdleManager getInstance() {
        return sInstance;
    }

    private void initializeRunnables() {
        mServiceKillerRunnable = this::killBackgroundServices;
        mHaltManagerRunnable = this::haltManager;
    }

    public void executeManager() {
        if (mActivityManager == null || mAlarmManager == null) {
            Log.e(TAG, "Required system services not available");
            return;
        }

        if (mIsExecuting) {
            Log.d(TAG, "Manager already executing, skipping duplicate execution");
            return;
        }

        mIsExecuting = true;
        cancelPendingCallbacks();

        long timeUntilAlarm = getMillisecondsUntilNextAlarm();
        long delayForServiceKiller;

        if (timeUntilAlarm > 0 && timeUntilAlarm < IDLE_TIME_NEEDED) {
            delayForServiceKiller = MIN_DELAY;
            Log.d(TAG, "Alarm in " + timeUntilAlarm + "ms, scheduling immediate service kill");
        } else {
            delayForServiceKiller = IDLE_TIME_NEEDED;
            Log.d(TAG, "No imminent alarm, scheduling service kill after " + 
                  TimeUnit.MILLISECONDS.toMinutes(IDLE_TIME_NEEDED) + " minutes");
        }

        mHandler.postDelayed(mServiceKillerRunnable, delayForServiceKiller);

        if (timeUntilAlarm > ALARM_BUFFER_TIME) {
            long haltDelay = timeUntilAlarm - ALARM_BUFFER_TIME;
            mHandler.postDelayed(mHaltManagerRunnable, haltDelay);
            Log.d(TAG, "Scheduling halt " + TimeUnit.MILLISECONDS.toMinutes(ALARM_BUFFER_TIME) + 
                  " minutes before alarm");
        }
    }

    public void haltManager() {
        Log.d(TAG, "Halting manager");
        cancelPendingCallbacks();
        mIsExecuting = false;
    }

    private void cancelPendingCallbacks() {
        if (mHandler != null && mServiceKillerRunnable != null && mHaltManagerRunnable != null) {
            mHandler.removeCallbacks(mServiceKillerRunnable);
            mHandler.removeCallbacks(mHaltManagerRunnable);
        }
    }

    private long getMillisecondsUntilNextAlarm() {
        if (mAlarmManager == null) {
            return 0;
        }

        try {
            AlarmManager.AlarmClockInfo alarmInfo = mAlarmManager.getNextAlarmClock();
            if (alarmInfo != null) {
                long alarmTime = alarmInfo.getTriggerTime();
                long currentTime = System.currentTimeMillis();
                long timeUntilAlarm = alarmTime - currentTime;
                return Math.max(0, timeUntilAlarm);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting next alarm", e);
        }

        return 0;
    }

    private void killBackgroundServices() {
        if (mActivityManager == null) {
            Log.e(TAG, "ActivityManager not available");
            mIsExecuting = false;
            return;
        }

        List<ActivityManager.RunningAppProcessInfo> runningProcesses;
        try {
            runningProcesses = mActivityManager.getRunningAppProcesses();
        } catch (Exception e) {
            Log.e(TAG, "Error getting running processes", e);
            mIsExecuting = false;
            return;
        }

        if (runningProcesses == null || runningProcesses.isEmpty()) {
            Log.d(TAG, "No running processes found");
            mIsExecuting = false;
            return;
        }

        int killedCount = 0;
        int protectedCount = 0;

        for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
            if (processInfo.pkgList == null || processInfo.pkgList.length == 0) {
                continue;
            }

            String packageName = processInfo.pkgList[0];
            
            if (shouldKillProcess(packageName)) {
                try {
                    mActivityManager.killBackgroundProcesses(packageName);
                    killedCount++;
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Killed background process: " + packageName);
                    }
                } catch (SecurityException e) {
                    Log.w(TAG, "No permission to kill process: " + packageName);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to kill process: " + packageName, e);
                }
            } else {
                protectedCount++;
            }
        }

        Log.i(TAG, "Process cleanup complete: " + killedCount + " killed, " + 
              protectedCount + " protected");
        mIsExecuting = false;
    }

    private boolean shouldKillProcess(@NonNull String packageName) {
        for (String pattern : PROTECTED_PACKAGE_PATTERNS) {
            if (packageName.contains(pattern)) {
                return false;
            }
        }
        return true;
    }

    public void cleanup() {
        haltManager();
        synchronized (sLock) {
            sInstance = null;
        }
    }

    public boolean isExecuting() {
        return mIsExecuting;
    }

    public static void addProtectedPattern(@NonNull String pattern) {
        PROTECTED_PACKAGE_PATTERNS.add(pattern);
    }

    public static void removeProtectedPattern(@NonNull String pattern) {
        PROTECTED_PACKAGE_PATTERNS.remove(pattern);
    }
}