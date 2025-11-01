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
import android.widget.HorizontalScrollView;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.TouchDelegate;

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
    private HorizontalScrollView transcriptScroll;
    private HorizontalScrollView translationScroll;
    private ValueAnimator transcriptAnimator;
    private ValueAnimator translationAnimator;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_UPDATE_TEXT.equals(action) || ACTION_UPDATE_TRANSLATION.equals(action)) {
                String text = intent.getStringExtra(EXTRA_TEXT);
                if (translationView != null && text != null) {
                    translationView.setText(text);
                    if (translationScroll != null) stickToRight(translationScroll, translationView);
                }
            } else if (ACTION_UPDATE_TRANSCRIPT.equals(action)) {
                String text = intent.getStringExtra(EXTRA_TEXT);
                if (transcriptView != null && text != null) {
                    transcriptView.setText(text);
                    if (transcriptScroll != null) stickToRight(transcriptScroll, transcriptView);
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

    private void animateScrollToEnd(HorizontalScrollView scroll, TextView tv, float pxPerSec, long maxDurationMs) {
        if (scroll == null || tv == null) return;
        scroll.post(() -> {
            int viewW = scroll.getWidth();
            if (viewW <= 0) return;
            CharSequence cs = tv.getText();
            if (cs == null) return;
            float contentWf = tv.getPaint().measureText(cs.toString());
            int contentW = (int) Math.ceil(contentWf);
            int target = Math.max(0, contentW - viewW);
            int current = scroll.getScrollX();
            if (target <= current) return;

            long duration = (long) Math.min(maxDurationMs, ((target - current) / pxPerSec) * 1000L);
            ValueAnimator animator = ValueAnimator.ofInt(current, target);
            animator.setInterpolator(new LinearInterpolator());
            animator.setDuration(Math.max(80, duration));
            animator.addUpdateListener(a -> scroll.scrollTo((int) a.getAnimatedValue(), 0));

            if (scroll == transcriptScroll) {
                if (transcriptAnimator != null) transcriptAnimator.cancel();
                transcriptAnimator = animator;
                transcriptAnimator.start();
            } else {
                if (translationAnimator != null) translationAnimator.cancel();
                translationAnimator = animator;
                translationAnimator.start();
            }
        });
    }
    
    private void stickToRight(HorizontalScrollView scroll, TextView tv) {
        if (scroll == null || tv == null) return;
        scroll.post(() -> {
            int viewW = scroll.getWidth();
            if (viewW <= 0) return;
            CharSequence cs = tv.getText();
            if (cs == null) return;
            float w = tv.getPaint().measureText(cs.toString());
            int target = (int) Math.max(0, Math.ceil(w) - viewW);
            scroll.scrollTo(target, 0);
        });
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private void showOverlay() {
        if (overlayView != null) return;

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_subtitle, null);
        transcriptView = overlayView.findViewById(R.id.overlay_transcript_text);
        translationView = overlayView.findViewById(R.id.overlay_translation_text);
        transcriptScroll = overlayView.findViewById(R.id.overlay_scroll_transcript);
        translationScroll = overlayView.findViewById(R.id.overlay_scroll_translation);
        View resizeHandle = overlayView.findViewById(R.id.resize_handle);
        View btnSettings = overlayView.findViewById(R.id.btn_settings);
        View btnClose = overlayView.findViewById(R.id.btn_close);
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

        int screenW = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;
        int marginPx = Math.round(24 * density);
        int defaultWidth = Math.max(Math.round(200 * density), screenW - marginPx * 2);
        // 读取上次保存的窗口位置与宽度
        ConfigManager config = new ConfigManager(this);
        int savedW = config.getOverlayWidthPx();
        int savedX = config.getOverlayX();
        int savedY = config.getOverlayY();

        params = new WindowManager.LayoutParams(
                savedW > 0 ? Math.min(Math.max(savedW, Math.round(200 * density)), screenW - marginPx * 2) : defaultWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = savedX >= 0 ? savedX : marginPx;
        params.y = savedY >= 0 ? savedY : 100;

        // 顶部按钮：设置（返回应用）、关闭（停止识别并关闭悬浮窗）
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(i);
                } catch (Exception ignored) {}
            });
        }
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                try {
                    Intent svc = new Intent(this, RecognitionService.class);
                    svc.setAction(RecognitionService.ACTION_STOP);
                    startService(svc);
                } catch (Exception ignored) {}
                stopSelf();
            });
        }

        // 拖动移动/右下角热区缩放（整块右下角都可拖）
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int downRawX, downRawY;
            private int startX, startY; // 窗口起始位置
            private boolean resizing = false;
            private int startWidth;
            private float startTransPx, startTranscriptPx;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int rawX = (int) event.getRawX();
                int rawY = (int) event.getRawY();
                int edge = Math.round(64 * getResources().getDisplayMetrics().density); // 右下角可缩放热区（放大为64dp）
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        downRawX = rawX;
                        downRawY = rawY;
                        startX = params.x;
                        startY = params.y;
                        // 命中右下角热区：进入缩放模式
                        int localX = (int) event.getX();
                        int localY = (int) event.getY();
                        int w = overlayView.getWidth();
                        int h = overlayView.getHeight();
                        if (localX >= w - edge && localY >= h - edge) {
                            resizing = true;
                            startWidth = params.width;
                            startTransPx = translationView != null ? translationView.getTextSize() : 0f;
                            startTranscriptPx = transcriptView != null ? transcriptView.getTextSize() : 0f;
                        } else {
                            resizing = false;
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        int dx = rawX - downRawX;
                        int dy = rawY - downRawY;
                        if (resizing) {
                            // 横向：改宽度；纵向：改字号
                            int maxWidth = screenW - marginPx * 2;
                            int minWidth = Math.min(Math.round(200 * density), maxWidth);
                            int newWidth = Math.max(minWidth, Math.min(maxWidth, startWidth + dx));
                            params.width = newWidth;
                            windowManager.updateViewLayout(overlayView, params);

                            float deltaPx = dy * 0.6f; // 下拉变大
                            if (translationView != null && startTransPx > 0) {
                                float newPx = clamp(startTransPx + deltaPx, 12f * density, 56f * density);
                                translationView.setTextSize(TypedValue.COMPLEX_UNIT_PX, newPx);
                            }
                            if (transcriptView != null && startTranscriptPx > 0) {
                                float newPx2 = clamp(startTranscriptPx + deltaPx * 0.6f, 10f * density, 40f * density);
                                transcriptView.setTextSize(TypedValue.COMPLEX_UNIT_PX, newPx2);
                            }
                        } else {
                            // 移动
                            params.x = startX + dx;
                            params.y = startY + dy;
                            windowManager.updateViewLayout(overlayView, params);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 保存当前位置与尺寸/字号
                        if (resizing) {
                            config.setOverlayWidthPx(params.width);
                            if (translationView != null) config.setOverlayFontTranslationPx(translationView.getTextSize());
                            if (transcriptView != null) config.setOverlayFontTranscriptPx(transcriptView.getTextSize());
                        } else {
                            config.setOverlayX(params.x);
                            config.setOverlayY(params.y);
                        }
                        resizing = false;
                        return true;
                }
                return false;
            }
        });

        // 扩大手柄可点区域（TouchDelegate），提升命中率
        if (resizeHandle != null) {
            overlayView.post(() -> {
                Rect r = new Rect();
                resizeHandle.getHitRect(r);
                int expand = Math.round(24 * density);
                r.inset(-expand, -expand);
                overlayView.setTouchDelegate(new TouchDelegate(r, resizeHandle));
            });
        }

        // 应用已保存的字号（若有）
        float savedTransPx = config.getOverlayFontTranslationPx();
        float savedTranscriptPx = config.getOverlayFontTranscriptPx();
        if (translationView != null && savedTransPx > 0) {
            translationView.setTextSize(TypedValue.COMPLEX_UNIT_PX, savedTransPx);
        }
        if (transcriptView != null && savedTranscriptPx > 0) {
            transcriptView.setTextSize(TypedValue.COMPLEX_UNIT_PX, savedTranscriptPx);
        }

        // 保留三角图标本身的监听（可选），主要缩放逻辑已在父视图右下角区域处理，无需重复

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
            transcriptView.setTextSize(Math.max(12, (int)(config.getFontSizePixels() * 0.6)));
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
