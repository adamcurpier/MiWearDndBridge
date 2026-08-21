package com.adam.miweardndbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;

public class AlarmPlaybackService extends Service {

    public static final String ACTION_START_ALARM =
            "com.adam.miweardndbridge.START_ALARM";

    public static final String ACTION_STOP_ALARM =
            "com.adam.miweardndbridge.STOP_ALARM";

    private static final String CHANNEL_ID = "miwear_alarm_playback";
    private static final int NOTIFICATION_ID = 1001;
    private static final String DEBUG_KEY = "miwear_bridge_alarm_debug";

    private final Object alarmLock = new Object();
    private Ringtone alarmRingtone;

    @Override
    public void onCreate() {
        super.onCreate();
        appendDebugMarker("fgs_created");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_STOP_ALARM.equals(action)) {
            appendDebugMarker("fgs_stop_received");
            stopAlarmAudio();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START_ALARM.equals(action)) {
            appendDebugMarker("fgs_start_received");

            Notification notification = buildForegroundNotification();

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            );

            appendDebugMarker("fgs_foreground_started");
            startAlarmAudio();
        }

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (manager == null) {
            appendDebugMarker("fgs_notification_manager_null");
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Phone alarm playback",
                NotificationManager.IMPORTANCE_LOW
        );

        channel.setDescription("Keeps synchronized phone alarms audible on the watch");
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);

        appendDebugMarker("fgs_channel_ready");
    }

    private Notification buildForegroundNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Phone alarm")
                .setContentText("Playing synchronized phone alarm")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
    }

    private void startAlarmAudio() {
        synchronized (alarmLock) {
            try {
                appendDebugMarker("fgs_audio_start_entered");

                if (alarmRingtone != null) {
                    try {
                        if (alarmRingtone.isPlaying()) {
                            appendDebugMarker("fgs_audio_already_playing");
                            return;
                        }
                    } catch (Exception ignored) {
                    }

                    try {
                        alarmRingtone.stop();
                    } catch (Exception ignored) {
                    }

                    alarmRingtone = null;
                }

                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

                if (alarmUri == null) {
                    appendDebugMarker("fgs_alarm_uri_null");
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }

                if (alarmUri == null) {
                    appendDebugMarker("fgs_no_usable_uri");
                    return;
                }

                appendDebugMarker("fgs_alarm_uri_found");

                Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);

                if (ringtone == null) {
                    appendDebugMarker("fgs_ringtone_create_failed");
                    return;
                }

                appendDebugMarker("fgs_ringtone_created");

                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();

                ringtone.setAudioAttributes(attributes);
                ringtone.setLooping(true);

                alarmRingtone = ringtone;
                appendDebugMarker("fgs_before_play");
                alarmRingtone.play();

                appendDebugMarker(
                        alarmRingtone.isPlaying()
                                ? "fgs_playing_true"
                                : "fgs_playing_false"
                );

            } catch (Exception e) {
                appendDebugMarker("fgs_audio_error_" + e.getClass().getSimpleName());
                alarmRingtone = null;
            }
        }
    }

    private void stopAlarmAudio() {
        synchronized (alarmLock) {
            if (alarmRingtone == null) {
                appendDebugMarker("fgs_stop_no_ringtone");
                return;
            }

            try {
                alarmRingtone.stop();
                appendDebugMarker("fgs_audio_stopped");
            } catch (Exception e) {
                appendDebugMarker("fgs_stop_error_" + e.getClass().getSimpleName());
            } finally {
                alarmRingtone = null;
            }
        }
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
        appendDebugMarker("fgs_destroyed");
        stopAlarmAudio();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
