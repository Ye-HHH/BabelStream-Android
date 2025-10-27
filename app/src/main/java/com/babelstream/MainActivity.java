package com.babelstream;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.provider.Settings;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;

/**
 * 主界面Activity
 * 显示实时字幕和控制录音
 */
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO = 1;
    private static final int REQUEST_SETTINGS = 2;

    // UI组件
    private TextView subtitleText;
    private TextView subtitleTranscript;
    private TextView statusText;
    private Button startButton;
    private Button overlayButton;
    private Button settingsButton;
    private View statusIndicator;
    private ProgressBar levelBar;

    // 核心组件
    private ConfigManager configManager;
    // 识别迁移到前台服务，Activity 仅发起/停止

    private boolean isRecognizing = false;
    private static final int REQUEST_MEDIA_PROJECTION = 3;
    private static final int REQUEST_NOTIFICATIONS = 4;
    private MediaProjectionManager projectionManager;
    private int projectionResultCode = 0;
    private Intent projectionDataIntent = null;
    private boolean overlayEnabled = false;
    private BroadcastReceiver recognitionReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化配置管理器
        configManager = new ConfigManager(this);

        // 初始化UI
        initViews();

        // MediaProjection
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // 检查API Key
        if (!configManager.hasApiKey()) {
            Toast.makeText(this, "请先在设置中配置API Key", Toast.LENGTH_LONG).show();
            openSettings();
        }

        // 检查权限
        checkPermissions();

        // 监听识别结果与状态
        recognitionReceiver = new BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                String action = intent.getAction();
                if (RecognitionService.ACTION_TEXT.equals(action)) {
                    String text = intent.getStringExtra("text");
                    subtitleText.setText(text);
                } else if (RecognitionService.ACTION_TRANSLATION.equals(action)) {
                    String text = intent.getStringExtra("text");
                    subtitleText.setText(text);
                } else if (RecognitionService.ACTION_TRANSCRIPT.equals(action)) {
                    String text = intent.getStringExtra("text");
                    if (subtitleTranscript != null) subtitleTranscript.setText(text);
                    if (!configManager.isTranslationEnabled() && text != null) {
                        subtitleText.setText(text);
                    }
                } else if (RecognitionService.ACTION_STATUS.equals(action)) {
                    String status = intent.getStringExtra("status");
                    updateStatus(status);
                } else if (RecognitionService.ACTION_LEVEL.equals(action)) {
                    int level = intent.getIntExtra("level", 0);
                    if (levelBar != null) levelBar.setProgress(level);
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(RecognitionService.ACTION_TEXT);
        f.addAction(RecognitionService.ACTION_STATUS);
        f.addAction(RecognitionService.ACTION_TRANSCRIPT);
        f.addAction(RecognitionService.ACTION_TRANSLATION);
        f.addAction(RecognitionService.ACTION_LEVEL);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(recognitionReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(recognitionReceiver, f);
        }
    }

    private void initViews() {
        subtitleText = findViewById(R.id.subtitle_text);
        subtitleTranscript = findViewById(R.id.subtitle_transcript);
        statusText = findViewById(R.id.status_text);
        startButton = findViewById(R.id.start_button);
        overlayButton = findViewById(R.id.overlay_button);
        settingsButton = findViewById(R.id.settings_button);
        statusIndicator = findViewById(R.id.status_indicator);
        levelBar = findViewById(R.id.audio_level);

        // 应用字体大小设置
        int fontSize = configManager.getFontSizePixels();
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        if (subtitleTranscript != null) {
            subtitleTranscript.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(16, (int)(fontSize * 0.6)));
        }

        // 设置按钮点击事件
        startButton.setOnClickListener(v -> toggleRecognition());
        overlayButton.setOnClickListener(v -> toggleOverlay());
        settingsButton.setOnClickListener(v -> openSettings());

        // 初始状态
        updateStatus("待机中");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            openSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, REQUEST_SETTINGS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            // 重新应用设置
            int fontSize = configManager.getFontSizePixels();
            subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);

            // 如果API Key更新了,可能需要重新初始化识别器
            if (isRecognizing) {
                stopRecognition();
                Toast.makeText(this, "设置已更新,请重新开始识别", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                projectionResultCode = resultCode;
                projectionDataIntent = data;
                startRecognition();
            } else {
                Toast.makeText(this, "未授予屏幕捕获权限，无法获取系统音频", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO);
        }

        // Android 13+ 通知权限（用于前台服务通知展示）
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "麦克风权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要麦克风权限才能使用语音识别", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_NOTIFICATIONS) {
            // 可选：给出简单反馈
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void toggleRecognition() {
        if (isRecognizing) {
            stopRecognition();
        } else {
            startRecognition();
        }
    }

    private void startRecognition() {
        // 检查麦克风权限（部分设备仍需）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先授予麦克风权限", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        // 若选择系统音频：需要屏幕捕获授权；若选择麦克风：可跳过
        boolean useMic = configManager.isAudioSourceMic();
        if (!useMic) {
            if (projectionResultCode == 0 || projectionDataIntent == null) {
                Intent intent = projectionManager.createScreenCaptureIntent();
                startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
                return;
            }
        }

        // 检查API Key
        if (!configManager.hasApiKey()) {
            Toast.makeText(this, "请先配置API Key", Toast.LENGTH_SHORT).show();
            openSettings();
            return;
        }

        try {

            // 启动前台识别服务
            Intent svc = new Intent(this, RecognitionService.class);
            svc.setAction(RecognitionService.ACTION_START);
            if (!useMic) {
                svc.putExtra(RecognitionService.EXTRA_RESULT_CODE, projectionResultCode);
                svc.putExtra(RecognitionService.EXTRA_RESULT_DATA, projectionDataIntent);
            }
            ContextCompat.startForegroundService(this, svc);

            // 更新UI
            isRecognizing = true;
            startButton.setText("停止");
            updateStatus("识别中...");

        } catch (Exception e) {
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopRecognition();
        }
    }

    private void stopRecognition() {
        // 停止前台识别服务
        Intent svc = new Intent(this, RecognitionService.class);
        svc.setAction(RecognitionService.ACTION_STOP);
        startService(svc);

        isRecognizing = false;
        startButton.setText("开始");
        updateStatus("待机中");
    }

    private void toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // 个别Rom没有带包名参数的页面，退回通用页
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    startActivity(intent);
                } catch (Exception ex) {
                    Toast.makeText(this, "无法打开悬浮窗设置页，请在系统设置中手动授权", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }
        if (!overlayEnabled) {
            ContextCompat.startForegroundService(this, new Intent(this, OverlayService.class));
            overlayEnabled = true;
            overlayButton.setText("关闭悬浮窗");
        } else {
            stopService(new Intent(this, OverlayService.class));
            overlayEnabled = false;
            overlayButton.setText("开启悬浮窗");
        }
    }

    private void updateStatus(String status) {
        statusText.setText(status);

        // 更新状态指示灯颜色（VSCode 风格）
        if (status.contains("识别中") || status.contains("连接")) {
            statusIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.vscode_success));
        } else if (status.contains("错误") || status.contains("失败")) {
            statusIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.vscode_error));
        } else {
            statusIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.vscode_border));
        }
    }

    private void sendOverlayText(String text) {
        Intent intent = new Intent(OverlayService.ACTION_UPDATE_TEXT);
        intent.putExtra(OverlayService.EXTRA_TEXT, text);
        sendBroadcast(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecognition();
        if (recognitionReceiver != null) unregisterReceiver(recognitionReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 可选: 后台时暂停识别
        // stopRecognition();
    }
}
