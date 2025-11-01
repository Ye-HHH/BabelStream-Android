package com.babelstream;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.AudioManager;
import android.hardware.SensorPrivacyManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.content.pm.ServiceInfo;

/**
 * 前台识别服务：
 * - 通过 MediaProjection 捕获系统播放音频 或 麦克风采集
 * - 送入 SdkGummyClient（阿里云 Gummy Android SDK）做识别/翻译
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
    private SdkGummyClient recognizer;
    private ConfigManager config;
    private boolean running = false;
    private long lastLevelTs = 0L;
    private long lastLevelLogTs = 0L;
    private AudioManager audioManager;
    private int prevAudioMode = AudioManager.MODE_NORMAL;
    private boolean audioModeChanged = false;
    private long silenceStartMs = 0L;
    private boolean silenceNotified = false;

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        config = new ConfigManager(this);
        createNotificationChannel();
        try { android.util.Log.i(TAG, "onCreate"); } catch (Throwable ignore) {}
        try { sendStatus("RecognitionService onCreate"); } catch (Throwable ignore) {}
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
            try {
                android.util.Log.i(TAG, "startPipeline: useMic=" + useMic + ", cfgSampleRate=" + sampleRate + ", model=" + config.getModel() + ", wsEndpoint=" + config.getWsEndpoint());
            } catch (Throwable ignore) {}

            // 1) 先构建采集链路
            if (useMic) {
                micCapture = new AudioCaptureManager(sampleRate);
                micCapture.setCallback(new AudioCaptureManager.AudioDataCallback() {
                    @Override public void onAudioData(byte[] data, int length) {
                        if (recognizer != null) recognizer.offerPcm(data, length);
                        dispatchLevel(data, length);
                    }
                    @Override public void onError(String error) { sendStatus("音频错误:" + error); }
                });
                try { micCapture.preferBuiltInMic(this); } catch (Throwable ignore) {}
            } else {
                playback = new PlaybackCaptureManager(mediaProjection, sampleRate);
                playback.setCallback(new PlaybackCaptureManager.AudioDataCallback() {
                    @Override public void onAudioData(byte[] data, int length) {
                        if (recognizer != null) recognizer.offerPcm(data, length);
                        dispatchLevel(data, length);
                    }
                    @Override public void onError(String error) { sendStatus("音频错误:" + error); }
                });
            }

            // 2) 先启动采集，保证电平可见
            if (useMic) {
                try {
                    audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                    if (audioManager != null) {
                        prevAudioMode = audioManager.getMode();
                        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        audioModeChanged = true;
                    }
                    // 记录并尝试解除全局静音（不影响系统隐私总开关）
                    try {
                        boolean micMuted = audioManager.isMicrophoneMute();
                        Log.i(TAG, "micMuted(before)=" + micMuted + ", audioMode=" + prevAudioMode);
                        if (micMuted) {
                            try { audioManager.setMicrophoneMute(false); } catch (Throwable ignore) {}
                        }
                    } catch (Throwable ignore) {}
                } catch (Throwable ignore) {}
                // 检测系统“麦克风隐私开关”（Android 12+），使用反射避免编译期依赖系统API
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        Object spm = getSystemService(Class.forName("android.hardware.SensorPrivacyManager"));
                        boolean blocked = false;
                        if (spm != null) {
                            try {
                                Class<?> sensors = Class.forName("android.hardware.SensorPrivacyManager$Sensors");
                                int mic = sensors.getField("MICROPHONE").getInt(null);
                                java.lang.reflect.Method m = spm.getClass().getMethod("isSensorPrivacyEnabled", int.class);
                                Object ret = m.invoke(spm, mic);
                                if (ret instanceof Boolean) blocked = (Boolean) ret;
                            } catch (Throwable ignore) {}
                        }
                        Log.i(TAG, "micPrivacyBlocked=" + blocked);
                        if (blocked) {
                            sendStatus("系统已关闭“麦克风访问”，请在快捷设置或 设置→隐私→麦克风 开启");
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "check mic privacy failed", t);
                }
            }
            boolean captureStarted = useMic ? micCapture.startRecording() : playback.start();
            android.util.Log.i(TAG, "startPipeline: captureStarted=" + captureStarted + ", useMic=" + useMic);
            if (!captureStarted) {
                sendStatus(useMic ? "麦克风采集启动失败" : "系统音频捕获启动失败");
                stopSelfSafe();
                return;
            }
            if (useMic) {
                try { micCapture.preferBuiltInMic(this); } catch (Throwable ignore) {}
                try { sendStatus("麦克风采集已启动: " + (micCapture != null ? (micCapture.getSampleRate() + "Hz") : "")); } catch (Throwable ignore) {}
            }

            // 3) 再启动识别器；识别失败也不影响电平测试
            android.util.Log.i(TAG, "startPipeline: creating recognizer");
            int outSr;
            try {
                if (useMic && micCapture != null) {
                    try {
                        java.lang.reflect.Method m = micCapture.getClass().getMethod("getOutputSampleRate");
                        Object v = m.invoke(micCapture);
                        outSr = (v instanceof Integer) ? (Integer) v : micCapture.getSampleRate();
                    } catch (Throwable ignore) {
                        outSr = micCapture.getSampleRate();
                    }
                } else if (!useMic && playback != null) {
                    // PlaybackCaptureManager 固定输出 targetSampleRate（与 config.getSampleRate 一致）
                    outSr = config.getSampleRate();
                } else {
                    outSr = config.getSampleRate();
                }
            } catch (Throwable t) { outSr = config.getSampleRate(); }

            recognizer = new SdkGummyClient(this, config, outSr);
            recognizer.setCallback(new SdkGummyClient.RecognitionCallback() {
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
                @Override public void onStatusChange(String status) {
                    android.util.Log.i(TAG, "status=" + status);
                    sendStatus(status);
                }
                @Override public void onError(String error) {
                    android.util.Log.e(TAG, "error=" + error);
                    sendStatus(error);
                }
            });
            boolean recogOk = recognizer.start();
            android.util.Log.i(TAG, "recognizer.start returned=" + recogOk);
            running = true;
            if (recogOk) {
                sendStatus("识别中...");
            } else {
                sendStatus("识别器未启动，仅电平测试");
            }
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
        try { overlay.setPackage(getPackageName()); } catch (Throwable ignore) {}
        sendBroadcast(overlay);
        // 发给主界面
        Intent i2 = new Intent(ACTION_TEXT);
        i2.putExtra("text", text);
        try { i2.setPackage(getPackageName()); } catch (Throwable ignore) {}
        sendBroadcast(i2);
    }

    private void dispatchTranscript(String text) {
        Intent o = new Intent(OverlayService.ACTION_UPDATE_TRANSCRIPT);
        o.putExtra(OverlayService.EXTRA_TEXT, text);
        try { o.setPackage(getPackageName()); } catch (Throwable ignore) {}
        sendBroadcast(o);

        Intent ui = new Intent(ACTION_TRANSCRIPT);
        ui.putExtra("text", text);
        try { ui.setPackage(getPackageName()); } catch (Throwable ignore) {}
        sendBroadcast(ui);
    }

    private void dispatchTranslation(String text) {
        Intent o = new Intent(OverlayService.ACTION_UPDATE_TRANSLATION);
        o.putExtra(OverlayService.EXTRA_TEXT, text);
        try { o.setPackage(getPackageName()); } catch (Throwable ignore) {}
        sendBroadcast(o);

        Intent ui = new Intent(ACTION_TRANSLATION);
        ui.putExtra("text", text);
        try { ui.setPackage(getPackageName()); } catch (Throwable ignore) {}
        sendBroadcast(ui);
    }

    private void dispatchLevel(byte[] data, int length) {
        long now = System.currentTimeMillis();
        if (now - lastLevelTs < 100) return; // 节流 ~10Hz
        lastLevelTs = now;
        int level = calcLevelPercent(data, length);
        Intent i = new Intent(ACTION_LEVEL);
        i.putExtra("level", level);
        // 显式限定本应用接收，提升在新系统上的广播可见性
        try { i.setPackage(getPackageName()); } catch (Throwable ignore) {}
        sendBroadcast(i);

        if (now - lastLevelLogTs > 1000) { // 每秒打一次日志
            android.util.Log.i(TAG, "level=" + level);
            lastLevelLogTs = now;
        }

        // 连续静音提示（>3s）
        int silenceThreshold = 1; // 0-100 之间，1 视为接近静音
        if (level <= silenceThreshold) {
            if (silenceStartMs == 0L) silenceStartMs = now;
            if (!silenceNotified && (now - silenceStartMs) > 3000) {
                sendStatus("捕获到静音，可能被系统禁用录制或被其它应用占用");
                silenceNotified = true;
            }
        } else {
            silenceStartMs = 0L;
            silenceNotified = false;
        }
    }

    // 计算16bit PCM电平：dBFS(-60..0) 映射到 0-100（更贴近日常听感）
    private int calcLevelPercent(byte[] data, int length) {
        if (data == null || length < 2) return 0;
        long sum = 0;
        int samples = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            int lo = data[i] & 0xFF;
            int hi = data[i + 1];
            int sample = (hi << 8) | lo;
            sum += (long) sample * (long) sample;
            samples++;
        }
        if (samples == 0) return 0;
        double mean = (double) sum / samples;
        double rms = Math.sqrt(mean);
        // 归一化并转换到 dBFS
        double rmsNorm = Math.max(1e-9, rms / 32768.0);
        double dbfs = 20.0 * Math.log10(rmsNorm); // 0 dBFS = 满刻度
        double clamped = Math.max(-60.0, Math.min(0.0, dbfs)); // 限定动态范围
        int level = (int) Math.round((clamped + 60.0) / 60.0 * 100.0);
        if (level < 0) level = 0; if (level > 100) level = 100;
        return level;
    }

    private void sendStatus(String status) {
        Intent i = new Intent(ACTION_STATUS);
        i.putExtra("status", status);
        sendBroadcast(i);
        try { android.util.Log.i(TAG, "ACTION_STATUS: " + status); } catch (Throwable ignore) {}
    }

    private void stopSelfSafe() {
        running = false;
        try { if (playback != null) playback.stop(); } catch (Throwable ignore) {}
        try { if (micCapture != null) micCapture.stopRecording(); } catch (Throwable ignore) {}
        try { if (recognizer != null) recognizer.stop(); } catch (Throwable ignore) {}
        try { if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; } } catch (Throwable ignore) {}
        try {
            if (audioManager != null && audioModeChanged) {
                audioManager.setMode(prevAudioMode);
                audioModeChanged = false;
            }
        } catch (Throwable ignore) {}
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
