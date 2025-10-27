package com.babelstream;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.content.pm.ServiceInfo;

/**
 * 前台悬浮窗服务：在其它应用之上显示实时字幕
 */
public class OverlayService extends Service {
    public static final String ACTION_UPDATE_TEXT = "com.babelstream.OVERLAY_UPDATE_TEXT"; // 兼容旧版
    public static final String ACTION_UPDATE_TRANSCRIPT = "com.babelstream.OVERLAY_UPDATE_TRANSCRIPT";
    public static final String ACTION_UPDATE_TRANSLATION = "com.babelstream.OVERLAY_UPDATE_TRANSLATION";
    public static final String ACTION_UPDATE_STYLE = "com.babelstream.OVERLAY_UPDATE_STYLE";
    public static final String ACTION_HIDE_OVERLAY = "com.babelstream.OVERLAY_HIDE";
    public static final String ACTION_SHOW_OVERLAY = "com.babelstream.OVERLAY_SHOW";
    public static final String EXTRA_TEXT = "text";

    private static final String CHANNEL_ID = "overlay_channel";

    private WindowManager windowManager;
    private View overlayView;
    private TextView transcriptView;
    private TextView translationView;
    private WindowManager.LayoutParams params;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_UPDATE_TEXT.equals(action) || ACTION_UPDATE_TRANSLATION.equals(action)) {
                String text = intent.getStringExtra(EXTRA_TEXT);
                if (translationView != null && text != null) {
                    translationView.setText(text);
                }
            } else if (ACTION_UPDATE_TRANSCRIPT.equals(action)) {
                String text = intent.getStringExtra(EXTRA_TEXT);
                if (transcriptView != null && text != null) {
                    transcriptView.setText(text);
                }
            } else if (ACTION_UPDATE_STYLE.equals(action)) {
                applyStyle();
            } else if (ACTION_HIDE_OVERLAY.equals(action)) {
                if (overlayView != null) overlayView.setVisibility(View.GONE);
            } else if (ACTION_SHOW_OVERLAY.equals(action)) {
                if (overlayView != null) overlayView.setVisibility(View.VISIBLE);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, buildNotification());
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showOverlay();

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_TEXT),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_TRANSLATION),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_TRANSCRIPT),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_STYLE),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(updateReceiver, new IntentFilter(ACTION_HIDE_OVERLAY),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(updateReceiver, new IntentFilter(ACTION_SHOW_OVERLAY),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_TEXT));
            registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_TRANSLATION));
            registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_TRANSCRIPT));
            registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_STYLE));
            registerReceiver(updateReceiver, new IntentFilter(ACTION_HIDE_OVERLAY));
            registerReceiver(updateReceiver, new IntentFilter(ACTION_SHOW_OVERLAY));
        }
    }

    private void showOverlay() {
        if (overlayView != null) return;

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_subtitle, null);
        transcriptView = overlayView.findViewById(R.id.overlay_transcript_text);
        translationView = overlayView.findViewById(R.id.overlay_translation_text);
        applyStyle();

        int type;
        if (Build.VERSION.SDK_INT >= 26) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            // Android 7.x 及以下需要回退
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        params.x = 0;
        params.y = 100;

        // 拖动以改变位置
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int lastX, lastY;
            private int paramX, paramY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();
                        paramX = params.x;
                        paramY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) event.getRawX() - lastX;
                        int dy = (int) event.getRawY() - lastY;
                        params.x = paramX + dx;
                        params.y = paramY + dy;
                        windowManager.updateViewLayout(overlayView, params);
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(overlayView, params);
        } catch (SecurityException se) {
            android.widget.Toast.makeText(this, "添加悬浮窗失败: 请检查悬浮窗权限", android.widget.Toast.LENGTH_LONG).show();
            stopSelf();
        } catch (Throwable t) {
            android.widget.Toast.makeText(this, "添加悬浮窗失败: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void applyStyle() {
        ConfigManager config = new ConfigManager(this);
        // 字体与透明度
        if (translationView != null) {
            translationView.setTextSize(config.getFontSizePixels());
            translationView.setAlpha(config.getOverlayTextAlpha());
        }
        if (transcriptView != null) {
            transcriptView.setTextSize(Math.max(16, (int)(config.getFontSizePixels() * 0.6)));
            transcriptView.setAlpha(config.getOverlayTextAlpha());
        }

        // 显示模式
        int mode = config.getOverlayDisplayMode();
        if (transcriptView != null && translationView != null) {
            switch (mode) {
                case 1: // 仅译文
                    transcriptView.setVisibility(View.GONE);
                    translationView.setVisibility(View.VISIBLE);
                    break;
                case 2: // 仅原文
                    transcriptView.setVisibility(View.VISIBLE);
                    translationView.setVisibility(View.GONE);
                    break;
                default: // 双行
                    transcriptView.setVisibility(View.VISIBLE);
                    translationView.setVisibility(View.VISIBLE);
            }
        }

        // 背景圆角/阴影/内边距
        if (overlayView != null) {
            float density = getResources().getDisplayMetrics().density;
            int cornerPx = Math.round(config.getOverlayCornerDp() * density);
            int paddingPx = Math.round(config.getOverlayPaddingDp() * density);
            int elevPx = Math.round(config.getOverlayElevationDp() * density);
            float bgAlpha = config.getOverlayBgAlpha();
            int color = android.graphics.Color.argb((int)(bgAlpha * 255), 0, 0, 0);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(color);
            bg.setCornerRadius(cornerPx);
            overlayView.setBackground(bg);
            overlayView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                overlayView.setElevation(elevPx);
            }

            // 顶部/底部吸附与外边距
            String pos = config.getOverlayPosition();
            int marginPx = Math.round(config.getOverlayMarginDp() * density);
            if (params != null) {
                params.gravity = "bottom".equalsIgnoreCase(pos) ? Gravity.BOTTOM : Gravity.TOP;
                params.y = marginPx;
                try {
                    windowManager.updateViewLayout(overlayView, params);
                } catch (Exception ignored) {}
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "字幕悬浮窗", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("在其他应用之上显示字幕");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setContentTitle("字幕悬浮窗已开启")
                .setContentText("点击返回应用设置")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(updateReceiver);
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
