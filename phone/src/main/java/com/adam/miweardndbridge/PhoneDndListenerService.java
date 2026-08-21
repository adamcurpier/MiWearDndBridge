package com.adam.miweardndbridge;

import android.app.Notification;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public class PhoneDndListenerService extends NotificationListenerService {
    private static final String TAG = "MiWearDndBridge";

    private static final String PATH = "/dnd-sync";
    private static final String ALARM_RING_PATH = "/alarm-ring";
    private static final String ALARM_STOP_PATH = "/alarm-stop";

    private static final String CLOCK_PACKAGE = "com.android.deskclock";
    private static final String ALARM_CHANNEL = "channel_id_deskclock_alarm";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ContentObserver zenObserver;

    private final Object stateLock = new Object();
    private Boolean lastRequestedState = null;

    private final Object alarmLock = new Object();
    private String activeAlarmNotificationKey = null;

    @Override
    public void onCreate() {
        super.onCreate();

        zenObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);

                Log.i(TAG, "zen_mode ContentObserver fired");
                checkAndSendZenMode();
            }
        };

        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor("zen_mode"),
                false,
                zenObserver
        );
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();

        Log.i(TAG, "Notification listener connected");

        synchronized (stateLock) {
            lastRequestedState = null;
        }

        checkAndSendZenMode();
        checkExistingAlarmState();
    }

    @Override
    public void onInterruptionFilterChanged(int interruptionFilter) {
        super.onInterruptionFilterChanged(interruptionFilter);

        Log.i(TAG, "Interruption filter callback=" + interruptionFilter);
        checkAndSendZenMode();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        if (!isRingingAlarmNotification(sbn)) {
            return;
        }

        String key = sbn.getKey();

        synchronized (alarmLock) {
            if (key.equals(activeAlarmNotificationKey)) {
                return;
            }

            activeAlarmNotificationKey = key;
        }

        Log.i(TAG, "POCO alarm started: key=" + key + " id=" + sbn.getId());
        sendAlarmCommand(ALARM_RING_PATH, "ALARM_RING");
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);

        if (sbn == null) {
            return;
        }

        boolean shouldStop = false;

        synchronized (alarmLock) {
            if (sbn.getKey().equals(activeAlarmNotificationKey)) {
                activeAlarmNotificationKey = null;
                shouldStop = true;
            } else if (isRingingAlarmNotification(sbn)) {
                activeAlarmNotificationKey = null;
                shouldStop = true;
            }
        }

        if (!shouldStop) {
            return;
        }

        Log.i(TAG, "POCO alarm stopped: key=" + sbn.getKey() + " id=" + sbn.getId());
        sendAlarmCommand(ALARM_STOP_PATH, "ALARM_STOP");
    }

    private boolean isRingingAlarmNotification(StatusBarNotification sbn) {
        if (sbn == null) {
            return false;
        }

        if (!CLOCK_PACKAGE.equals(sbn.getPackageName())) {
            return false;
        }

        Notification notification = sbn.getNotification();

        if (notification == null) {
            return false;
        }

        if (!ALARM_CHANNEL.equals(notification.getChannelId())) {
            return false;
        }

        return (notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
    }

    private void checkExistingAlarmState() {
        try {
            StatusBarNotification[] activeNotifications = getActiveNotifications();

            if (activeNotifications != null) {
                for (StatusBarNotification sbn : activeNotifications) {
                    if (isRingingAlarmNotification(sbn)) {
                        synchronized (alarmLock) {
                            activeAlarmNotificationKey = sbn.getKey();
                        }

                        Log.i(TAG, "Existing POCO alarm found: key=" + sbn.getKey());
                        sendAlarmCommand(ALARM_RING_PATH, "ALARM_RING");
                        return;
                    }
                }
            }

            synchronized (alarmLock) {
                activeAlarmNotificationKey = null;
            }

            sendAlarmCommand(ALARM_STOP_PATH, "ALARM_STOP");

        } catch (Exception e) {
            Log.e(TAG, "Unable to inspect active alarm state", e);
        }
    }

    private void checkAndSendZenMode() {
        final int zenMode;

        try {
            zenMode = Settings.Global.getInt(getContentResolver(), "zen_mode", 0);
        } catch (Exception e) {
            Log.e(TAG, "Unable to read zen_mode", e);
            return;
        }

        final boolean dndOn = zenMode != 0;

        synchronized (stateLock) {
            if (lastRequestedState != null && lastRequestedState.booleanValue() == dndOn) {
                return;
            }

            lastRequestedState = dndOn;
        }

        Log.i(TAG, "DND state changed: zen_mode=" + zenMode + " DND=" + dndOn);
        sendState(dndOn);
    }

    private void sendState(final boolean dndOn) {
        new Thread(() -> {
            try {
                List<Node> nodes = Tasks.await(Wearable.getNodeClient(this).getConnectedNodes());

                Log.i(TAG, "Connected Wear nodes=" + nodes.size());

                if (nodes.isEmpty()) {
                    synchronized (stateLock) {
                        if (lastRequestedState != null && lastRequestedState.booleanValue() == dndOn) {
                            lastRequestedState = null;
                        }
                    }

                    Log.w(TAG, "No connected Wear nodes");
                    return;
                }

                byte[] payload = new byte[] { (byte) (dndOn ? 1 : 0) };

                for (Node node : nodes) {
                    Tasks.await(Wearable.getMessageClient(this).sendMessage(node.getId(), PATH, payload));
                    Log.i(TAG, "Sent DND=" + dndOn + " to " + node.getDisplayName());
                }

            } catch (Exception e) {
                synchronized (stateLock) {
                    if (lastRequestedState != null && lastRequestedState.booleanValue() == dndOn) {
                        lastRequestedState = null;
                    }
                }

                Log.e(TAG, "Failed to send DND state", e);
            }
        }, "DndSyncSender").start();
    }

    private void sendAlarmCommand(final String path, final String description) {
        new Thread(() -> {
            try {
                List<Node> nodes = Tasks.await(Wearable.getNodeClient(this).getConnectedNodes());

                Log.i(TAG, description + " connected Wear nodes=" + nodes.size());

                if (nodes.isEmpty()) {
                    Log.w(TAG, description + ": no connected Wear nodes");
                    return;
                }

                byte[] payload = new byte[] { 1 };

                for (Node node : nodes) {
                    Tasks.await(Wearable.getMessageClient(this).sendMessage(node.getId(), path, payload));
                    Log.i(TAG, "Sent " + description + " to " + node.getDisplayName());
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to send " + description, e);
            }
        }, "AlarmSyncSender").start();
    }

    @Override
    public void onDestroy() {
        if (zenObserver != null) {
            getContentResolver().unregisterContentObserver(zenObserver);
        }

        super.onDestroy();
    }
}
