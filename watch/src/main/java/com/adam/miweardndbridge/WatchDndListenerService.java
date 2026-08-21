package com.adam.miweardndbridge;

import android.app.NotificationManager;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class WatchDndListenerService extends WearableListenerService {
    private static final String TAG = "MiWearDndBridge";

    private static final String PATH = "/dnd-sync";
    private static final String ALARM_RING_PATH = "/alarm-ring";
    private static final String ALARM_STOP_PATH = "/alarm-stop";

    private static final String DEBUG_KEY = "miwear_bridge_alarm_debug";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        String path = messageEvent.getPath();

        if (ALARM_RING_PATH.equals(path)) {
            appendDebugMarker("ring_received");

            Intent intent = new Intent(this, AlarmPlaybackService.class);
            intent.setAction(AlarmPlaybackService.ACTION_START_ALARM);

            try {
                appendDebugMarker("starting_alarm_fgs");
                startForegroundService(intent);
                appendDebugMarker("alarm_fgs_start_requested");
            } catch (Exception e) {
                appendDebugMarker("alarm_fgs_start_error_" + e.getClass().getSimpleName());
                Log.e(TAG, "Unable to start alarm foreground service", e);
            }

            return;
        }

        if (ALARM_STOP_PATH.equals(path)) {
            appendDebugMarker("stop_received");

            Intent intent = new Intent(this, AlarmPlaybackService.class);
            intent.setAction(AlarmPlaybackService.ACTION_STOP_ALARM);

            try {
                appendDebugMarker("sending_alarm_stop");
                startService(intent);
                appendDebugMarker("alarm_stop_requested");
            } catch (Exception e) {
                appendDebugMarker("alarm_stop_error_" + e.getClass().getSimpleName());
                Log.e(TAG, "Unable to stop alarm playback service", e);
            }

            return;
        }

        if (!PATH.equals(path)) {
            return;
        }

        byte[] payload = messageEvent.getData();

        if (payload == null || payload.length == 0) {
            Log.w(TAG, "Received empty DND payload");
            return;
        }

        boolean dndOn = payload[0] != 0;

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (nm == null) {
            Log.e(TAG, "NotificationManager unavailable");
            return;
        }

        if (!nm.isNotificationPolicyAccessGranted()) {
            Log.e(TAG, "Notification Policy access not granted");
            return;
        }

        int filter = dndOn
                ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                : NotificationManager.INTERRUPTION_FILTER_ALL;

        nm.setInterruptionFilter(filter);

        boolean mcuWrite = Settings.Secure.putInt(
                getContentResolver(),
                "mcu.persist.disturb_manual_state",
                dndOn ? 1 : 0
        );

        Log.i(TAG,
                "Applied Watch DND=" + dndOn
                        + " filter=" + filter
                        + " mcuWrite=" + mcuWrite);
    }

    private void appendDebugMarker(String value) {
        try {
            String existing = Settings.Secure.getString(getContentResolver(), DEBUG_KEY);
            String entry = value + ":" + System.currentTimeMillis();
            String updated = (existing == null || existing.isEmpty())
                    ? entry
                    : existing + " > " + entry;

            Settings.Secure.putString(getContentResolver(), DEBUG_KEY, updated);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        appendDebugMarker("listener_destroyed");
        super.onDestroy();
    }
}
