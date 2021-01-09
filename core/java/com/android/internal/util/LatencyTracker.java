/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.internal.util;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.DeviceConfig;
import android.util.EventLog;
import android.util.Log;
import android.util.SparseLongArray;

import com.android.internal.logging.EventLogTags;
import com.android.internal.os.BackgroundThread;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Class to track various latencies in SystemUI. It then writes the latency to statsd and also
 * outputs it to logcat so these latencies can be captured by tests and then used for dashboards.
 * <p>
 * This is currently only in Keyguard so it can be shared between SystemUI and Keyguard, but
 * eventually we'd want to merge these two packages together so Keyguard can use common classes
 * that are shared with SystemUI.
 */
public class LatencyTracker {
    private static final String TAG = "LatencyTracker";
    private static final String SETTINGS_ENABLED_KEY = "enabled";
    private static final String SETTINGS_SAMPLING_INTERVAL_KEY = "sampling_interval";
    /** Default to being enabled on debug builds. */
    private static final boolean DEFAULT_ENABLED = Build.IS_DEBUGGABLE;
    /** Default to collecting data for 1/5 of all actions (randomly sampled). */
    private static final int DEFAULT_SAMPLING_INTERVAL = 5;

    /**
     * Time it takes until the first frame of the notification panel to be displayed while expanding
     */
    public static final int ACTION_EXPAND_PANEL = 0;

    /**
     * Time it takes until the first frame of recents is drawn after invoking it with the button.
     */
    public static final int ACTION_TOGGLE_RECENTS = 1;

    /**
     * Time between we get a fingerprint acquired signal until we start with the unlock animation
     */
    public static final int ACTION_FINGERPRINT_WAKE_AND_UNLOCK = 2;

    /**
     * Time it takes to check PIN/Pattern/Password.
     */
    public static final int ACTION_CHECK_CREDENTIAL = 3;

    /**
     * Time it takes to check fully PIN/Pattern/Password, i.e. that's the time spent including the
     * actions to unlock a user.
     */
    public static final int ACTION_CHECK_CREDENTIAL_UNLOCKED = 4;

    /**
     * Time it takes to turn on the screen.
     */
    public static final int ACTION_TURN_ON_SCREEN = 5;

    /**
     * Time it takes to rotate the screen.
     */
    public static final int ACTION_ROTATE_SCREEN = 6;

    /*
     * Time between we get a face acquired signal until we start with the unlock animation
     */
    public static final int ACTION_FACE_WAKE_AND_UNLOCK = 7;

    /**
     * Time between the swipe-up gesture and window drawn of recents activity.
     */
    public static final int ACTION_START_RECENTS_ANIMATION = 8;

    private static final int[] STATSD_ACTION = new int[]{
            FrameworkStatsLog.UIACTION_LATENCY_REPORTED__ACTION__ACTION_EXPAND_PANEL,
            FrameworkStatsLog.UIACTION_LATENCY_REPORTED__ACTION__ACTION_TOGGLE_RECENTS,
            FrameworkStatsLog.UIACTION_LATENCY_REPORTED__ACTION__ACTION_FINGERPRINT_WAKE_AND_UNLOCK,
            FrameworkStatsLog.UIACTION_LATENCY_REPORTED__ACTION__ACTION_CHECK_CREDENTIAL,
            FrameworkStatsLog.UIACTION_LATENCY_REPORTED__ACTION__ACTION_CHECK_CREDENTIAL_UNLOCKED,
            FrameworkStatsLog.UIACTION_LATENCY_REPORTED__ACTION__ACTION_TURN_ON_SCREEN,
            FrameworkStatsLog.UIACTION_LATENCY_REPORTED__ACTION__ACTION_ROTATE_SCREEN,
            FrameworkStatsLog.UIACTION_LATENCY_REPORTED__ACTION__ACTION_FACE_WAKE_AND_UNLOCK,
            FrameworkStatsLog.UIACTION_LATENCY_REPORTED__ACTION__ACTION_START_RECENTS_ANIMATION,
    };

    private static LatencyTracker sLatencyTracker;

    private final SparseLongArray mStartRtc = new SparseLongArray();
    private final Context mContext;
    private volatile int mSamplingInterval;
    private volatile boolean mEnabled;

    public static LatencyTracker getInstance(Context context) {
        if (sLatencyTracker == null) {
            synchronized (LatencyTracker.class) {
                if (sLatencyTracker == null) {
                    sLatencyTracker = new LatencyTracker(context);
                }
            }
        }
        return sLatencyTracker;
    }

    public LatencyTracker(Context context) {
        mContext = context;
        mEnabled = DEFAULT_ENABLED;
        mSamplingInterval = DEFAULT_SAMPLING_INTERVAL;

        // Post initialization to the background in case we're running on the main thread.
        BackgroundThread.getHandler().post(() -> this.updateProperties(
                DeviceConfig.getProperties(DeviceConfig.NAMESPACE_LATENCY_TRACKER)));
        DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_LATENCY_TRACKER,
                BackgroundThread.getExecutor(), this::updateProperties);
    }

    private void updateProperties(DeviceConfig.Properties properties) {
        mSamplingInterval = properties.getInt(SETTINGS_SAMPLING_INTERVAL_KEY,
                DEFAULT_SAMPLING_INTERVAL);
        mEnabled = properties.getBoolean(SETTINGS_ENABLED_KEY, DEFAULT_ENABLED);
    }

    /**
     * A helper method to translate action type to name.
     *
     * @param action the action type defined in AtomsProto.java
     * @return the name of the action
     */
    public static String getNameOfAction(int action) {
        // Defined in AtomsProto.java
        switch (action) {
            case 0:
                return "UNKNOWN";
            case 1:
                return "ACTION_EXPAND_PANEL";
            case 2:
                return "ACTION_TOGGLE_RECENTS";
            case 3:
                return "ACTION_FINGERPRINT_WAKE_AND_UNLOCK";
            case 4:
                return "ACTION_CHECK_CREDENTIAL";
            case 5:
                return "ACTION_CHECK_CREDENTIAL_UNLOCKED";
            case 6:
                return "ACTION_TURN_ON_SCREEN";
            case 7:
                return "ACTION_ROTATE_SCREEN";
            case 8:
                return "ACTION_FACE_WAKE_AND_UNLOCK";
            case 9:
                return "ACTION_START_RECENTS_ANIMATION";
            default:
                throw new IllegalArgumentException("Invalid action");
        }
    }

    private String getTraceNameOfAcion(int action) {
        return "L<" + getNameOfAction(action) + ">";
    }

    public static boolean isEnabled(Context ctx) {
        return getInstance(ctx).isEnabled();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Notifies that an action is starting. This needs to be called from the main thread.
     *
     * @param action The action to start. One of the ACTION_* values.
     */
    public void onActionStart(int action) {
        if (!isEnabled()) {
            return;
        }
        Trace.asyncTraceBegin(Trace.TRACE_TAG_APP, getTraceNameOfAcion(action), 0);
        mStartRtc.put(action, SystemClock.elapsedRealtime());
    }

    /**
     * Notifies that an action has ended. This needs to be called from the main thread.
     *
     * @param action The action to end. One of the ACTION_* values.
     */
    public void onActionEnd(int action) {
        if (!isEnabled()) {
            return;
        }
        long endRtc = SystemClock.elapsedRealtime();
        long startRtc = mStartRtc.get(action, -1);
        if (startRtc == -1) {
            return;
        }
        mStartRtc.delete(action);
        Trace.asyncTraceEnd(Trace.TRACE_TAG_APP, getTraceNameOfAcion(action), 0);
        logAction(action, (int) (endRtc - startRtc));
    }

    /**
     * Logs an action that has started and ended. This needs to be called from the main thread.
     *
     * @param action   The action to end. One of the ACTION_* values.
     * @param duration The duration of the action in ms.
     */
    public void logAction(int action, int duration) {
        boolean shouldSample = ThreadLocalRandom.current().nextInt() % mSamplingInterval == 0;
        logActionDeprecated(action, duration, shouldSample);
    }

    /**
     * Logs an action that has started and ended. This needs to be called from the main thread.
     *
     * @param action          The action to end. One of the ACTION_* values.
     * @param duration        The duration of the action in ms.
     * @param writeToStatsLog Whether to write the measured latency to FrameworkStatsLog.
     */
    public static void logActionDeprecated(int action, int duration, boolean writeToStatsLog) {
        Log.i(TAG, "action=" + action + " latency=" + duration);
        EventLog.writeEvent(EventLogTags.SYSUI_LATENCY, action, duration);

        if (writeToStatsLog) {
            FrameworkStatsLog.write(
                    FrameworkStatsLog.UI_ACTION_LATENCY_REPORTED, STATSD_ACTION[action], duration);
        }
    }
}
