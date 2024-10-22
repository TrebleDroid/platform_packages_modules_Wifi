/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This Class is a Work-In-Progress, intended behavior is as follows:
 * Essentially this class automates a user toggling 'Airplane Mode' when WiFi "won't work".
 * IF each available saved network has failed connecting more times than the FAILURE_THRESHOLD
 * THEN Watchdog will restart Supplicant, wifi driver and return ClientModeImpl to InitialState.
 * TODO(b/159944009): May need to rework this class to handle make before break transition on STA +
 * STA devices.
 */
public class WifiLastResortWatchdog {
    private static final String TAG = "WifiLastResortWatchdog";
    private boolean mVerboseLoggingEnabled = false;
    /**
     * Association Failure code
     */
    public static final int FAILURE_CODE_ASSOCIATION = 1;
    /**
     * Authentication Failure code
     */
    public static final int FAILURE_CODE_AUTHENTICATION = 2;
    /**
     * Dhcp Failure code
     */
    public static final int FAILURE_CODE_DHCP = 3;
    /**
     * Maximum number of scan results received since we last saw a BSSID.
     * If it is not seen before this limit is reached, the network is culled
     */
    public static final int MAX_BSSID_AGE = 10;
    /**
     * BSSID used to increment failure counts against ALL bssids associated with a particular SSID
     */
    public static final String BSSID_ANY = "any";
    /**
     * Failure count that each available networks must meet to possibly trigger the Watchdog
     */
    public static final int FAILURE_THRESHOLD = 7;
    public static final String BUGREPORT_TITLE = "Wifi watchdog triggered";
    public static final double PROB_TAKE_BUGREPORT_DEFAULT = 1;

    // Number of milliseconds to wait before re-enable Watchdog triger
    @VisibleForTesting
    public static final long LAST_TRIGGER_TIMEOUT_MILLIS = 2 * 3600 * 1000; // 2 hours


    /**
     * Cached WifiConfigurations of available networks seen within MAX_BSSID_AGE scan results
     * Key:BSSID, Value:Counters of failure types
     */
    private Map<String, AvailableNetworkFailureCount> mRecentAvailableNetworks = new HashMap<>();

    /**
     * Map of SSID to <FailureCount, AP count>, used to count failures & number of access points
     * belonging to an SSID.
     */
    private Map<String, Pair<AvailableNetworkFailureCount, Integer>> mSsidFailureCount =
            new HashMap<>();

    /* List of failure BSSID */
    private final Set<String> mBssidFailureList = new HashSet<>();

    // Is Watchdog allowed to trigger now? Set to false after triggering. Set to true after
    // successfully connecting or a new network (SSID) becomes available to connect to.
    private boolean mWatchdogAllowedToTrigger = true;
    private long mTimeLastTrigger = 0;
    private String mSsidLastTrigger = null;
    private double mBugReportProbability = PROB_TAKE_BUGREPORT_DEFAULT;
    // If any connection failure happened after watchdog triggering restart then assume watchdog
    // did not fix the problem
    private boolean mWatchdogFixedWifi = true;
    /**
     * int key: networkId
     * long value: last time we started connecting to this network, in milliseconds since boot
     *
     * Limit size to 10 to prevent it from growing without bounds.
     */
    private final LruCache<Integer, Long> mNetworkIdToLastStartConnectTimeMillisSinceBoot =
            new LruCache<>(10);
    private Boolean mWatchdogFeatureEnabled = null;

    private final WifiInjector mWifiInjector;
    private final WifiMetrics mWifiMetrics;
    private final WifiDiagnostics mWifiDiagnostics;
    private final Clock mClock;
    private final Context mContext;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final Handler mHandler;
    private final WifiThreadRunner mWifiThreadRunner;
    private final WifiMonitor mWifiMonitor;

    /**
     * Local log used for debugging any WifiLastResortWatchdog issues.
     */
    private final LocalLog mLocalLog = new LocalLog(100);

    WifiLastResortWatchdog(
            WifiInjector wifiInjector,
            Context context, Clock clock,
            WifiMetrics wifiMetrics,
            WifiDiagnostics wifiDiagnostics,
            Looper clientModeImplLooper,
            DeviceConfigFacade deviceConfigFacade,
            WifiThreadRunner wifiThreadRunner,
            WifiMonitor wifiMonitor) {
        mWifiInjector = wifiInjector;
        mClock = clock;
        mWifiMetrics = wifiMetrics;
        mWifiDiagnostics = wifiDiagnostics;
        mContext = context;
        mDeviceConfigFacade = deviceConfigFacade;
        mWifiThreadRunner = wifiThreadRunner;
        mWifiMonitor = wifiMonitor;
        mHandler = new Handler(clientModeImplLooper) {
            public void handleMessage(Message msg) {
                processMessage(msg);
            }
        };
    }

    private static final int[] WIFI_MONITOR_EVENTS = {
            WifiMonitor.NETWORK_CONNECTION_EVENT
    };

    public void registerForWifiMonitorEvents(String ifaceName) {
        for (int event : WIFI_MONITOR_EVENTS) {
            mWifiMonitor.registerHandler(ifaceName, event, mHandler);
        }
    }

    public void deregisterForWifiMonitorEvents(String ifaceName) {
        for (int event : WIFI_MONITOR_EVENTS) {
            mWifiMonitor.deregisterHandler(ifaceName, event, mHandler);
        }
    }

    @NonNull
    private WifiInfo getPrimaryWifiInfo() {
        // This is retrieved lazily since there is a non-trivial circular dependency between
        // ActiveModeWarden & WifiLastResortWatchdog.
        ActiveModeWarden activeModeWarden = mWifiInjector.getActiveModeWarden();
        if (activeModeWarden == null) return new WifiInfo();
        // Cannot be null.
        ClientModeManager primaryCmm = activeModeWarden.getPrimaryClientModeManager();
        return primaryCmm.getConnectionInfo();
    }

    /**
     * Refreshes when the last CMD_START_CONNECT is triggered.
     */
    public void noteStartConnectTime(int networkId) {
        mHandler.post(() ->
                mNetworkIdToLastStartConnectTimeMillisSinceBoot.put(
                        networkId, mClock.getElapsedSinceBootMillis()));
    }

    private void processMessage(Message msg) {
        switch (msg.what) {
            case WifiMonitor.NETWORK_CONNECTION_EVENT: {
                NetworkConnectionEventInfo connectionInfo = (NetworkConnectionEventInfo) msg.obj;
                int networkId = connectionInfo.networkId;
                // Trigger bugreport for successful connections that take abnormally long
                Long lastStartConnectTimeNullable =
                        mNetworkIdToLastStartConnectTimeMillisSinceBoot.get(networkId);
                if (mDeviceConfigFacade.isAbnormalConnectionBugreportEnabled()
                        && lastStartConnectTimeNullable != null) {
                    long durationMs =
                            mClock.getElapsedSinceBootMillis() - lastStartConnectTimeNullable;
                    long abnormalConnectionDurationMs =
                            mDeviceConfigFacade.getAbnormalConnectionDurationMs();
                    if (durationMs > abnormalConnectionDurationMs) {
                        final String bugTitle = "Wi-Fi Bugreport: Abnormal connection time";
                        final String bugDetail = "Expected connection to take less than "
                                + abnormalConnectionDurationMs + " milliseconds. "
                                + "Actually took " + durationMs + " milliseconds.";
                        logv("Triggering bug report for abnormal connection time.");
                        mWifiThreadRunner.post(() ->
                                mWifiDiagnostics.takeBugReport(bugTitle, bugDetail),
                                TAG + "#" + msg.what);
                    }
                }
                // Should reset last connection time after each connection regardless if bugreport
                // is enabled or not.
                mNetworkIdToLastStartConnectTimeMillisSinceBoot.remove(networkId);
                break;
            }
            default:
                return;
        }
    }

    /**
     * Refreshes recentAvailableNetworks with the latest available networks
     * Adds new networks, removes old ones that have timed out. Should be called after Wifi
     * framework decides what networks it is potentially connecting to.
     * @param availableNetworks ScanDetail & Config list of potential connection
     * candidates
     */
    public void updateAvailableNetworks(
            List<Pair<ScanDetail, WifiConfiguration>> availableNetworks) {
        // Add new networks to mRecentAvailableNetworks
        if (availableNetworks != null) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "updateAvailableNetworks: size = " + availableNetworks.size());
            }
            for (Pair<ScanDetail, WifiConfiguration> pair : availableNetworks) {
                final ScanDetail scanDetail = pair.first;
                final WifiConfiguration config = pair.second;
                ScanResult scanResult = scanDetail.getScanResult();
                if (scanResult == null) continue;
                String bssid = scanResult.BSSID;
                String ssid = "\"" + scanDetail.getSSID() + "\"";
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, " " + bssid + ": " + scanDetail.getSSID());
                }
                // Cache the scanResult & WifiConfig
                AvailableNetworkFailureCount availableNetworkFailureCount =
                        mRecentAvailableNetworks.get(bssid);
                if (availableNetworkFailureCount == null) {
                    // New network is available
                    availableNetworkFailureCount = new AvailableNetworkFailureCount(config);
                    availableNetworkFailureCount.ssid = ssid;

                    // Count AP for this SSID
                    Pair<AvailableNetworkFailureCount, Integer> ssidFailsAndApCount =
                            mSsidFailureCount.get(ssid);
                    if (ssidFailsAndApCount == null) {
                        // This is a new SSID, create new FailureCount for it and set AP count to 1
                        ssidFailsAndApCount = Pair.create(new AvailableNetworkFailureCount(config),
                                1);
                        // Do not re-enable Watchdog in LAST_TRIGGER_TIMEOUT_MILLIS
                        // after last time Watchdog be triggered
                        if (!mWatchdogAllowedToTrigger && (mTimeLastTrigger == 0
                                || (mClock.getElapsedSinceBootMillis() - mTimeLastTrigger)
                                    >= LAST_TRIGGER_TIMEOUT_MILLIS)) {
                            localLog("updateAvailableNetworks: setWatchdogTriggerEnabled to true");
                            setWatchdogTriggerEnabled(true);
                        }
                    } else {
                        final Integer numberOfAps = ssidFailsAndApCount.second;
                        // This is not a new SSID, increment the AP count for it
                        ssidFailsAndApCount = Pair.create(ssidFailsAndApCount.first,
                                numberOfAps + 1);
                    }
                    mSsidFailureCount.put(ssid, ssidFailsAndApCount);
                }
                // refresh config if it is not null
                if (config != null) {
                    availableNetworkFailureCount.config = config;
                }
                // If we saw a network, set its Age to -1 here, aging iteration will set it to 0
                availableNetworkFailureCount.age = -1;
                mRecentAvailableNetworks.put(bssid, availableNetworkFailureCount);
            }
        }

        // Iterate through available networks updating timeout counts & removing networks.
        Iterator<Map.Entry<String, AvailableNetworkFailureCount>> it =
                mRecentAvailableNetworks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AvailableNetworkFailureCount> entry = it.next();
            if (entry.getValue().age < MAX_BSSID_AGE - 1) {
                entry.getValue().age++;
            } else {
                // Decrement this SSID : AP count
                String ssid = entry.getValue().ssid;
                Pair<AvailableNetworkFailureCount, Integer> ssidFails =
                            mSsidFailureCount.get(ssid);
                if (ssidFails != null) {
                    Integer apCount = ssidFails.second - 1;
                    if (apCount > 0) {
                        ssidFails = Pair.create(ssidFails.first, apCount);
                        mSsidFailureCount.put(ssid, ssidFails);
                    } else {
                        mSsidFailureCount.remove(ssid);
                    }
                } else {
                    Log.d(TAG, "updateAvailableNetworks: SSID to AP count mismatch for " + ssid);
                }
                it.remove();
            }
        }
        if (mVerboseLoggingEnabled) Log.v(TAG, toString());
    }

    /**
     * Increments the failure reason count for the given bssid. Performs a check to see if we have
     * exceeded a failure threshold for all available networks, and executes the last resort restart
     * @param bssid of the network that has failed connection, can be "any"
     * @param reason Message id from ClientModeImpl for this failure
     * @param isConnected whether the ClientModeImpl is currently connected
     * @return true if watchdog triggers, returned for test visibility
     */
    public boolean noteConnectionFailureAndTriggerIfNeeded(String ssid, String bssid, int reason,
            boolean isConnected) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "noteConnectionFailureAndTriggerIfNeeded: [" + ssid + ", " + bssid + ", "
                    + reason + "]");
        }

        // Update failure count for the failing network
        updateFailureCountForNetwork(ssid, bssid, reason);

        // If watchdog is not allowed to trigger it means a wifi restart is already triggered
        if (!mWatchdogAllowedToTrigger) {
            mWifiMetrics.incrementWatchdogTotalConnectionFailureCountAfterTrigger();
            mWatchdogFixedWifi = false;
        }
        // Have we met conditions to trigger the Watchdog Wifi restart?
        boolean isRestartNeeded = checkTriggerCondition(isConnected);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "isRestartNeeded = " + isRestartNeeded);
        }
        if (isRestartNeeded) {
            if (getWifiWatchdogFeature()) {
                // Stop the watchdog from triggering until re-enabled
                localLog("Trigger recovery: setWatchdogTriggerEnabled to false");
                setWatchdogTriggerEnabled(false);
                mWatchdogFixedWifi = true;
                loge("Watchdog triggering recovery");
                mSsidLastTrigger = ssid;
                mTimeLastTrigger = mClock.getElapsedSinceBootMillis();
                localLog(toString());
                mWifiInjector.getSelfRecovery().trigger(SelfRecovery.REASON_LAST_RESORT_WATCHDOG);
                incrementWifiMetricsTriggerCounts();
            } else {
                // auto bugreport if issue happens
                loge("bugreport notification");
                setWatchdogTriggerEnabled(false);
                takeBugReportWithCurrentProbability("Wifi Watchdog bite");
            }
        }
        return isRestartNeeded;
    }

    /**
     * Handles transitions entering and exiting ClientModeImpl ConnectedState
     * Used to track wifistate, and perform watchdog count resetting
     * @param isEntering true if called from ConnectedState.enter(), false for exit()
     */
    public void connectedStateTransition(boolean isEntering) {
        logv("connectedStateTransition: isEntering = " + isEntering);

        if (!isEntering) {
            return;
        }
        WifiInfo wifiInfo = getPrimaryWifiInfo();
        if (!mWatchdogAllowedToTrigger && mWatchdogFixedWifi
                && getWifiWatchdogFeature()
                && checkIfAtleastOneNetworkHasEverConnected()
                && checkIfConnectedBackToSameSsid(wifiInfo)
                && checkIfConnectedBssidHasEverFailed(wifiInfo)) {
            takeBugReportWithCurrentProbability("Wifi fixed after restart");
            // WiFi has connected after a Watchdog trigger, without any new networks becoming
            // available, log a Watchdog success in wifi metrics
            mWifiMetrics.incrementNumLastResortWatchdogSuccesses();
            long durationMs = mClock.getElapsedSinceBootMillis() - mTimeLastTrigger;
            mWifiMetrics.setWatchdogSuccessTimeDurationMs(durationMs);
        }
        // If the watchdog trigger was disabled (it triggered), connecting means we did
        // something right, re-enable it so it can fire again.
        localLog("connectedStateTransition: setWatchdogTriggerEnabled to true");
        setWatchdogTriggerEnabled(true);
    }

    /**
     * Helper function to check if device connected to BSSID
     * which is in BSSID failure list after watchdog trigger.
     */
    private boolean checkIfConnectedBssidHasEverFailed(@NonNull WifiInfo wifiInfo) {
        return mBssidFailureList.contains(wifiInfo.getBSSID());
    }

    /**
     * Helper function to check if device connect back to same
     * SSID after watchdog trigger
     */
    private boolean checkIfConnectedBackToSameSsid(@NonNull WifiInfo wifiInfo) {
        if (TextUtils.equals(mSsidLastTrigger, wifiInfo.getSSID())) {
            return true;
        }
        localLog("checkIfConnectedBackToSameSsid: different SSID be connected");
        return false;
    }

    /**
     * Triggers a wifi specific bugreport with a based on the current trigger probability.
     * @param bugDetail description of the bug
     */
    private void takeBugReportWithCurrentProbability(String bugDetail) {
        if (mBugReportProbability <= Math.random()) {
            return;
        }
        mHandler.post(() -> mWifiDiagnostics.takeBugReport(BUGREPORT_TITLE, bugDetail));
    }

    /**
     * Increments the failure reason count for the given network, in 'mSsidFailureCount'
     * Failures are counted per SSID, either; by using the ssid string when the bssid is "any"
     * or by looking up the ssid attached to a specific bssid
     * An unused set of counts is also kept which is bssid specific, in 'mRecentAvailableNetworks'
     * @param ssid of the network that has failed connection
     * @param bssid of the network that has failed connection, can be "any"
     * @param reason Message id from ClientModeImpl for this failure
     */
    private void updateFailureCountForNetwork(String ssid, String bssid, int reason) {
        logv("updateFailureCountForNetwork: [" + ssid + ", " + bssid + ", "
                + reason + "]");
        if (BSSID_ANY.equals(bssid)) {
            incrementSsidFailureCount(ssid, reason);
        } else {
            // Bssid count is actually unused except for logging purposes
            // SSID count is incremented within the BSSID counting method
            incrementBssidFailureCount(ssid, bssid, reason);
            mBssidFailureList.add(bssid);
        }
    }

    /**
     * Update the per-SSID failure count
     * @param ssid the ssid to increment failure count for
     * @param reason the failure type to increment count for
     */
    private void incrementSsidFailureCount(String ssid, int reason) {
        Pair<AvailableNetworkFailureCount, Integer> ssidFails = mSsidFailureCount.get(ssid);
        if (ssidFails == null) {
            Log.d(TAG, "updateFailureCountForNetwork: No networks for ssid = " + ssid);
            return;
        }
        AvailableNetworkFailureCount failureCount = ssidFails.first;
        failureCount.incrementFailureCount(reason);
    }

    /**
     * Update the per-BSSID failure count
     * @param bssid the bssid to increment failure count for
     * @param reason the failure type to increment count for
     */
    private void incrementBssidFailureCount(String ssid, String bssid, int reason) {
        AvailableNetworkFailureCount availableNetworkFailureCount =
                mRecentAvailableNetworks.get(bssid);
        if (availableNetworkFailureCount == null) {
            Log.d(TAG, "updateFailureCountForNetwork: Unable to find Network [" + ssid
                    + ", " + bssid + "]");
            return;
        }
        if (!availableNetworkFailureCount.ssid.equals(ssid)) {
            Log.d(TAG, "updateFailureCountForNetwork: Failed connection attempt has"
                    + " wrong ssid. Failed [" + ssid + ", " + bssid + "], buffered ["
                    + availableNetworkFailureCount.ssid + ", " + bssid + "]");
            return;
        }
        if (availableNetworkFailureCount.config == null) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "updateFailureCountForNetwork: network has no config ["
                        + ssid + ", " + bssid + "]");
            }
        }
        availableNetworkFailureCount.incrementFailureCount(reason);
        incrementSsidFailureCount(ssid, reason);
    }

    /**
     * Helper function to check if we should ignore BSSID update.
     * @param bssid BSSID of the access point
     * @return true if we should ignore BSSID update
     */
    public boolean shouldIgnoreBssidUpdate(String bssid) {
        return mWatchdogAllowedToTrigger
                && isBssidOnlyApOfSsid(bssid)
                && isSingleSsidRecorded()
                && checkIfAtleastOneNetworkHasEverConnected();
    }

    /**
     * Helper function to check if we should ignore SSID update.
     * @return true if should ignore SSID update
     */
    public boolean shouldIgnoreSsidUpdate() {
        return mWatchdogAllowedToTrigger
                && isSingleSsidRecorded()
                && checkIfAtleastOneNetworkHasEverConnected();
    }

    /**
     * Check the specified BSSID is the only BSSID for its corresponding SSID.
     * @param bssid BSSID of the access point
     * @return true if only BSSID for its corresponding SSID be observed
     */
    public boolean isBssidOnlyApOfSsid(String bssid) {
        AvailableNetworkFailureCount availableNetworkFailureCount =
                mRecentAvailableNetworks.get(bssid);
        if (availableNetworkFailureCount == null) {
            return false;
        }
        String ssid = availableNetworkFailureCount.ssid;
        Pair<AvailableNetworkFailureCount, Integer> ssidFails = mSsidFailureCount.get(ssid);
        if (ssidFails == null) {
            Log.d(TAG, "isOnlyBssidAvailable: Could not find SSID count for " + ssid);
            return false;
        }
        if (ssidFails.second != 1) {
            return false;
        }
        return true;
    }

    /**
     * Check there is only single SSID be observed.
     * @return true if only single SSID be observed.
     */
    private boolean isSingleSsidRecorded() {
        return (mSsidFailureCount.size() == 1);
    }

    /**
     * Check trigger condition: For all available networks, have we met a failure threshold for each
     * of them, and have previously connected to at-least one of the available networks
     * @return is the trigger condition true
     */
    private boolean checkTriggerCondition(boolean isConnected) {
        if (mVerboseLoggingEnabled) Log.v(TAG, "checkTriggerCondition.");
        // Don't check Watchdog trigger if wifi is in a connected state
        // (This should not occur, but we want to protect against any race conditions)
        if (isConnected) return false;
        // Don't check Watchdog trigger if trigger is not enabled
        if (!mWatchdogAllowedToTrigger) return false;

        for (Map.Entry<String, AvailableNetworkFailureCount> entry
                : mRecentAvailableNetworks.entrySet()) {
            if (!isOverFailureThreshold(entry.getKey())) {
                // This available network is not over failure threshold, meaning we still have a
                // network to try connecting to
                return false;
            }
        }
        // We have met the failure count for every available network.
        // Trigger restart if there exists at-least one network that we have previously connected.
        boolean atleastOneNetworkHasEverConnected = checkIfAtleastOneNetworkHasEverConnected();
        logv("checkTriggerCondition: return = " + atleastOneNetworkHasEverConnected);
        return checkIfAtleastOneNetworkHasEverConnected();
    }

    private boolean checkIfAtleastOneNetworkHasEverConnected() {
        for (Map.Entry<String, AvailableNetworkFailureCount> entry
                : mRecentAvailableNetworks.entrySet()) {
            if (entry.getValue().config != null
                    && entry.getValue().config.getNetworkSelectionStatus().hasEverConnected()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Update WifiMetrics with various Watchdog stats (trigger counts, failed network counts)
     */
    private void incrementWifiMetricsTriggerCounts() {
        if (mVerboseLoggingEnabled) Log.v(TAG, "incrementWifiMetricsTriggerCounts.");
        mWifiMetrics.incrementNumLastResortWatchdogTriggers();
        mWifiMetrics.addCountToNumLastResortWatchdogAvailableNetworksTotal(
                mSsidFailureCount.size());
        // Number of networks over each failure type threshold, present at trigger time
        int badAuth = 0;
        int badAssoc = 0;
        int badDhcp = 0;
        int badSum = 0;
        for (Map.Entry<String, Pair<AvailableNetworkFailureCount, Integer>> entry
                : mSsidFailureCount.entrySet()) {
            badSum = entry.getValue().first.associationRejection
                    + entry.getValue().first.authenticationFailure
                    + entry.getValue().first.dhcpFailure;
            // count as contributor if over half of badSum.
            if (badSum >= FAILURE_THRESHOLD) {
                badAssoc += (entry.getValue().first.associationRejection >= badSum / 2) ? 1 : 0;
                badAuth += (entry.getValue().first.authenticationFailure >= badSum / 2) ? 1 : 0;
                badDhcp += (entry.getValue().first.dhcpFailure >= badSum / 2) ? 1 : 0;
            }
        }
        if (badAuth > 0) {
            mWifiMetrics.addCountToNumLastResortWatchdogBadAuthenticationNetworksTotal(badAuth);
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadAuthentication();
        }
        if (badAssoc > 0) {
            mWifiMetrics.addCountToNumLastResortWatchdogBadAssociationNetworksTotal(badAssoc);
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadAssociation();
        }
        if (badDhcp > 0) {
            mWifiMetrics.addCountToNumLastResortWatchdogBadDhcpNetworksTotal(badDhcp);
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadDhcp();
        }
    }

    /**
     * Clear all failure counts
     */
    public void clearAllFailureCounts() {
        if (mVerboseLoggingEnabled) Log.v(TAG, "clearAllFailureCounts.");
        for (Map.Entry<String, AvailableNetworkFailureCount> entry
                : mRecentAvailableNetworks.entrySet()) {
            final AvailableNetworkFailureCount failureCount = entry.getValue();
            failureCount.resetCounts();
        }
        for (Map.Entry<String, Pair<AvailableNetworkFailureCount, Integer>> entry
                : mSsidFailureCount.entrySet()) {
            final AvailableNetworkFailureCount failureCount = entry.getValue().first;
            failureCount.resetCounts();
        }
        mBssidFailureList.clear();
    }
    /**
     * Gets the buffer of recently available networks
     */
    @VisibleForTesting
    Map<String, AvailableNetworkFailureCount> getRecentAvailableNetworks() {
        return mRecentAvailableNetworks;
    }

    /**
     * Activates or deactivates the Watchdog trigger. Counting and network buffering still occurs
     * @param enable true to enable the Watchdog trigger, false to disable it
     */
    private void setWatchdogTriggerEnabled(boolean enable) {
        if (mVerboseLoggingEnabled) Log.v(TAG, "setWatchdogTriggerEnabled: enable = " + enable);
        // Reset failure counts before actives watchdog
        if (enable) {
            clearAllFailureCounts();
        }
        mWatchdogAllowedToTrigger = enable;
    }

    /**
     * Prints all networks & counts within mRecentAvailableNetworks to string
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("mWatchdogFeatureEnabled: ").append(getWifiWatchdogFeature());
        sb.append("\nmWatchdogAllowedToTrigger: ").append(mWatchdogAllowedToTrigger);
        sb.append("\nmRecentAvailableNetworks: ").append(mRecentAvailableNetworks.size());
        for (Map.Entry<String, AvailableNetworkFailureCount> entry
                : mRecentAvailableNetworks.entrySet()) {
            sb.append("\n ").append(entry.getKey()).append(": ").append(entry.getValue())
                .append(", Age: ").append(entry.getValue().age);
        }
        sb.append("\nmSsidFailureCount:");
        for (Map.Entry<String, Pair<AvailableNetworkFailureCount, Integer>> entry :
                mSsidFailureCount.entrySet()) {
            final AvailableNetworkFailureCount failureCount = entry.getValue().first;
            final Integer apCount = entry.getValue().second;
            sb.append("\n").append(entry.getKey()).append(": ").append(apCount).append(",")
                    .append(failureCount.toString());
        }
        return sb.toString();
    }

    /**
     * @param bssid bssid to check the failures for
     * @return true if sum of failure count is over FAILURE_THRESHOLD
     */
    @VisibleForTesting
    boolean isOverFailureThreshold(String bssid) {
        return (getFailureCount(bssid, FAILURE_CODE_ASSOCIATION)
                + getFailureCount(bssid, FAILURE_CODE_AUTHENTICATION)
                + getFailureCount(bssid, FAILURE_CODE_DHCP)) >= FAILURE_THRESHOLD;
    }

    /**
     * Get the failure count for a specific bssid. This actually checks the ssid attached to the
     * BSSID and returns the SSID count
     * @param reason failure reason to get count for
     */
    @VisibleForTesting
    int getFailureCount(String bssid, int reason) {
        AvailableNetworkFailureCount availableNetworkFailureCount =
                mRecentAvailableNetworks.get(bssid);
        if (availableNetworkFailureCount == null) {
            return 0;
        }
        String ssid = availableNetworkFailureCount.ssid;
        Pair<AvailableNetworkFailureCount, Integer> ssidFails = mSsidFailureCount.get(ssid);
        if (ssidFails == null) {
            Log.d(TAG, "getFailureCount: Could not find SSID count for " + ssid);
            return 0;
        }
        final AvailableNetworkFailureCount failCount = ssidFails.first;
        switch (reason) {
            case FAILURE_CODE_ASSOCIATION:
                return failCount.associationRejection;
            case FAILURE_CODE_AUTHENTICATION:
                return failCount.authenticationFailure;
            case FAILURE_CODE_DHCP:
                return failCount.dhcpFailure;
            default:
                return 0;
        }
    }

    /**
     * Sets whether wifi watchdog should trigger recovery
     */
    public void setWifiWatchdogFeature(boolean enable) {
        logv("setWifiWatchdogFeature: " + enable);
        mWatchdogFeatureEnabled = enable;
        // for debugging purpose, reset mWatchdogAllowedToTrigger as well
        setWatchdogTriggerEnabled(true);
    }

    /**
     * Returns whether wifi watchdog should trigger recovery.
     */
    public boolean getWifiWatchdogFeature() {
        if (mWatchdogFeatureEnabled == null) {
            mWatchdogFeatureEnabled = mContext.getResources().getBoolean(
                    R.bool.config_wifi_watchdog_enabled);
        }
        return mWatchdogFeatureEnabled;
    }

    /** Enable/disable verbose logging. */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    @VisibleForTesting
    void setBugReportProbability(double newProbability) {
        mBugReportProbability = newProbability;
    }

    /**
     * This class holds the failure counts for an 'available network' (one of the potential
     * candidates for connection, as determined by framework).
     */
    public static class AvailableNetworkFailureCount {
        /**
         * WifiConfiguration associated with this network. Can be null for Ephemeral networks
         */
        public WifiConfiguration config;
        /**
        * SSID of the network (from ScanDetail)
        */
        public String ssid = "";
        /**
         * Number of times network has failed due to Association Rejection
         */
        public int associationRejection = 0;
        /**
         * Number of times network has failed due to Authentication Failure or SSID_TEMP_DISABLED
         */
        public int authenticationFailure = 0;
        /**
         * Number of times network has failed due to DHCP failure
         */
        public int dhcpFailure = 0;
        /**
         * Number of scanResults since this network was last seen
         */
        public int age = 0;

        AvailableNetworkFailureCount(WifiConfiguration configParam) {
            this.config = configParam;
        }

        /**
         * @param reason failure reason to increment count for
         */
        public void incrementFailureCount(int reason) {
            switch (reason) {
                case FAILURE_CODE_ASSOCIATION:
                    associationRejection++;
                    break;
                case FAILURE_CODE_AUTHENTICATION:
                    authenticationFailure++;
                    break;
                case FAILURE_CODE_DHCP:
                    dhcpFailure++;
                    break;
                default: //do nothing
            }
        }

        /**
         * Set all failure counts for this network to 0
         */
        void resetCounts() {
            associationRejection = 0;
            authenticationFailure = 0;
            dhcpFailure = 0;
        }

        public String toString() {
            return  ssid + " HasEverConnected: " + ((config != null)
                    ? config.getNetworkSelectionStatus().hasEverConnected() : "null_config")
                    + ", Failures: {"
                    + "Assoc: " + associationRejection
                    + ", Auth: " + authenticationFailure
                    + ", Dhcp: " + dhcpFailure
                    + "}";
        }
    }

    /**
     * Helper function for logging into local log buffer.
     */
    private void localLog(String s) {
        mLocalLog.log(s);
    }

    private void logv(String s) {
        mLocalLog.log(s);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, s, null);
        }
    }

    private void loge(String s) {
        mLocalLog.log(s);
        Log.e(TAG, s, null);
    }

    /**
     * Dump the local log buffer and other internal state of WifiLastResortWatchdog.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiLastResortWatchdog");
        pw.println("WifiLastResortWatchdog - Log Begin ----");
        mLocalLog.dump(fd, pw, args);
        pw.println("WifiLastResortWatchdog - Log End ----");
    }
}
