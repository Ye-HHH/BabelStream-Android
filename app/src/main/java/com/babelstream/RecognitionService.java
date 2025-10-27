package com.babelstream;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.content.pm.ServiceInfo;

/**
 * 前台识别服务：
 * - 通过 MediaProjection 捕获系统播放音频
 * - 送入 RealtimeRecognizer（阿里云实时 SDK）做识别/翻译
 * - 通过广播把文本发给 OverlayService 和 MainActivity
 */
public class RecognitionService extends Service {
    public static final String ACTION_START = "com.babelstream.RECOGNITION_START";
    public static final String ACTION_STOP = "com.babelstream.RECOGNITION_STOP";
    public static final String ACTION_STATUS = "com.babelstream.RECOGNITION_STATUS";
    public static final String ACTION_TEXT = "com.babelstream.RECOGNITION_TEXT"; // 主界面 UI 文本（简化）
    public static final String ACTION_TRANSCRIPT = "com.babelstream.RECOGNITION_TRANSCRIPT";
    public static final String ACTION_TRANSLATION = "com.babelstream.RECOGNITION_TRANSLATION";
    public static final String ACTION_LEVEL = "com.babelstream.RECOGNITION_LEVEL"; // 音频电平 0-100

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "recognition_channel";
    private static final String TAG = "RecognitionService";

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private PlaybackCaptureManager playback;
    private AudioCaptureManager micCapture;
    private RealtimeRecognizer recognizer;
    private ConfigManager config;
    private boolean running = false;
    private long lastLevelTs = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        config = new ConfigManager(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelfSafe();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_START.equals(intent.getAction())) {
            boolean useMic = config.isAudioSourceMic();
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);

            if (!useMic) {
                if (resultCode == 0 || data == null) {
                    sendStatus("未提供屏幕捕获凭据");
                    stopSelfSafe();
                    return START_NOT_STICKY;
                }
            }

            // Android 14+：根据输入源指定前台服务类型
            try {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    int type = useMic ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                                      : ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                    startForeground(2, buildNotification("识别服务运行中"), type);
                } else {
                    startForeground(2, buildNotification("识别服务运行中"));
                }
            } catch (Throwable t) {
                // 兼容旧设备或类型未声明：退回无类型启动，避免崩溃
                startForeground(2, buildNotification("识别服务运行中"));
            }

            if (!useMic) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            }
            startPipeline();
        }

        return START_STICKY;
    }

    private void startPipeline() {
        try {
            int sampleRate = config.getSampleRate();
            boolean useMic = config.isAudioSourceMic();
            if (useMic) {
                micCapture = new AudioCaptureManager(sampleRate);
                micCapture.setCallback(new AudioCaptureManager.AudioDataCallback() {
                    @Override public void onAudioData(byte[] data, int length) {
                        if (recognizer != null) recognizer.sendAudio(data, length);
                        dispatchLevel(data, length);
                    }
                    @Override public void onError(String error) { sendStatus("音频错误:" + error); }
                });
            } else {
                playback = new PlaybackCaptureManager(mediaProjection, sampleRate);
                playback.setCallback(new PlaybackCaptureManager.AudioDataCallback() {
                    @Override public void onAudioData(byte[] data, int length) {
                        if (recognizer != null) recognizer.sendAudio(data, length);
                        dispatchLevel(data, length);
                    }
                    @Override public void onError(String error) { sendStatus("音频错误:" + error); }
                });
            }

            recognizer = new RealtimeRecognizer(config.getApiKey(), config);
            recognizer.setCallback(new RealtimeRecognizer.RecognitionCallback() {
                @Override public void onTranscription(String text) {
                    dispatchTranscript(text);
                    if (!config.isTranslationEnabled()) dispatchTextUI(text);
                }
                @Override public void onTranslation(String text) {
                    if (config.isTranslationEnabled()) {
                        dispatchTranslation(text);
                        dispatchTextUI(text);
                    }
                }
                @Override public void onStatusChange(String status) { sendStatus(status); }
                @Override public void onError(String error) { sendStatus(error); }
            });

            if (!recognizer.start()) {
                sendStatus("识别器启动失败");
                stopSelfSafe();
                return;
            }
            boolean started;
            if (useMic) {
                started = micCapture.startRecording();
            } else {
                started = playback.start();
            }
            if (!started) {
                sendStatus(useMic ? "麦克风采集启动失败" : "系统音频捕获启动失败");
                stopSelfSafe();
                return;
            }
            running = true;
            sendStatus("识别中...");
        } catch (Throwable t) {
            Log.e(TAG, "startPipeline", t);
            sendStatus("启动失败:" + t.getMessage());
            stopSelfSafe();
        }
    }

    private void dispatchTextUI(String text) {
        // 发给悬浮窗
        Intent overlay = new Intent(OverlayService.ACTION_UPDATE_TRANSLATION);
        overlay.putExtra(OverlayService.EXTRA_TEXT, text);
        sendBroadcast(overlay);
        // 发给主界面
        Intent i2 = new Intent(ACTION_TEXT);
        i2.putExtra("text", text);
        sendBroadcast(i2);
    }

    private void dispatchTranscript(String text) {
        Intent o = new Intent(OverlayService.ACTION_UPDATE_TRANSCRIPT);
        o.putExtra(OverlayService.EXTRA_TEXT, text);
        sendBroadcast(o);

        Intent ui = new Intent(ACTION_TRANSCRIPT);
        ui.putExtra("text", text);
        sendBroadcast(ui);
    }

    private void dispatchTranslation(String text) {
        Intent o = new Intent(OverlayService.ACTION_UPDATE_TRANSLATION);
        o.putExtra(OverlayService.EXTRA_TEXT, text);
        sendBroadcast(o);

        Intent ui = new Intent(ACTION_TRANSLATION);
        ui.putExtra("text", text);
        sendBroadcast(ui);
    }

    private void dispatchLevel(byte[] data, int length) {
        long now = System.currentTimeMillis();
        if (now - lastLevelTs < 100) return; // 节流 ~10Hz
        lastLevelTs = now;
        int level = calcLevelPercent(data, length);
        Intent i = new Intent(ACTION_LEVEL);
        i.putExtra("level", level);
        sendBroadcast(i);
    }

    // 计算16bit PCM电平：取峰值映射到0-100
    private int calcLevelPercent(byte[] data, int length) {
        if (data == null || length < 2) return 0;
        int peak = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            int lo = data[i] & 0xFF;
            int hi = data[i + 1];
            int sample = (hi << 8) | lo;
            if (sample < 0) sample = -sample;
            if (sample > peak) peak = sample;
        }
        int level = (int) (peak * 100L / 32767L);
        if (level < 0) level = 0; if (level > 100) level = 100;
        return level;
    }

    private void sendStatus(String status) {
        Intent i = new Intent(ACTION_STATUS);
        i.putExtra("status", status);
        sendBroadcast(i);
    }

    private void stopSelfSafe() {
        running = false;
        try { if (playback != null) playback.stop(); } catch (Throwable ignore) {}
        try { if (micCapture != null) micCapture.stopRecording(); } catch (Throwable ignore) {}
        try { if (recognizer != null) recognizer.stop(); } catch (Throwable ignore) {}
        try { if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; } } catch (Throwable ignore) {}
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "识别服务", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
                .setContentTitle("BabelStream 识别")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override public IBinder onBind(Intent intent) { return null; }
}
