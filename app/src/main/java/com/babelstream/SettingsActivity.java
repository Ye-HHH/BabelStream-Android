package com.babelstream;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.TextView;
import android.content.Intent;
import com.babelstream.R;
import android.net.Uri;
import android.provider.Settings;
import android.content.ActivityNotFoundException;
import android.media.projection.MediaProjectionManager;
// 移除重复的 import，保留一次即可

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.content.pm.PackageManager;

/**
 * 设置Activity
 * 配置API Key、字体大小、翻译选项等
 */
public class SettingsActivity extends AppCompatActivity {

    private EditText apiKeyInput;
    private Spinner fontSizeSpinner;
    private Switch translationSwitch;
    private Spinner targetLanguageSpinner;
    private Button saveButton;
    private Button startFloatingButton;
    private android.widget.SeekBar bgAlphaSeek;
    private android.widget.SeekBar textAlphaSeek;
    private TextView bgAlphaValue;
    private TextView textAlphaValue;
    private android.widget.Switch previewDualSwitch;
    private android.widget.RadioGroup displayModeGroup;
    private android.widget.RadioButton modeBoth;
    private android.widget.RadioButton modeTranslation;
    private android.widget.RadioButton modeTranscript;
    private android.widget.SeekBar cornerSeek;
    private android.widget.SeekBar paddingSeek;
    private android.widget.SeekBar elevationSeek;
    private TextView cornerValue;
    private TextView paddingValue;
    private TextView elevationValue;
    private android.widget.Spinner positionSpinner;
    private android.widget.SeekBar marginSeek;
    private TextView marginValue;
    private android.widget.RadioGroup audioSourceGroup;
    private android.widget.RadioButton sourcePlayback;
    private android.widget.RadioButton sourceMic;

    private ConfigManager configManager;
    private boolean hasChanges = false;
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private MediaProjectionManager projectionManager;
    private int projectionResultCode = 0;
    private Intent projectionDataIntent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 显示返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("设置");
        }

        configManager = new ConfigManager(this);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        initViews();
        loadSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 避免悬浮窗遮挡设置页
        sendBroadcast(new Intent(OverlayService.ACTION_HIDE_OVERLAY));
    }

    @Override
    protected void onPause() {
        super.onPause();
        sendBroadcast(new Intent(OverlayService.ACTION_SHOW_OVERLAY));
    }

    private void initViews() {
        apiKeyInput = findViewById(R.id.api_key_input);
        fontSizeSpinner = findViewById(R.id.font_size_spinner);
        translationSwitch = findViewById(R.id.translation_switch);
        targetLanguageSpinner = findViewById(R.id.target_language_spinner);
        bgAlphaSeek = findViewById(R.id.bg_alpha_seek);
        textAlphaSeek = findViewById(R.id.text_alpha_seek);
        bgAlphaValue = findViewById(R.id.bg_alpha_value);
        textAlphaValue = findViewById(R.id.text_alpha_value);
        saveButton = findViewById(R.id.save_button);
        startFloatingButton = findViewById(R.id.start_floating_button);
        audioSourceGroup = findViewById(R.id.audio_source_group);
        sourcePlayback = findViewById(R.id.source_playback);
        sourceMic = findViewById(R.id.source_mic);
        previewDualSwitch = findViewById(R.id.preview_dual_switch);
        displayModeGroup = findViewById(R.id.display_mode_group);
        modeBoth = findViewById(R.id.mode_both);
        modeTranslation = findViewById(R.id.mode_translation);
        modeTranscript = findViewById(R.id.mode_transcript);
        cornerSeek = findViewById(R.id.corner_seek);
        paddingSeek = findViewById(R.id.padding_seek);
        elevationSeek = findViewById(R.id.overlay_elevation_seek);
        cornerValue = findViewById(R.id.corner_value);
        paddingValue = findViewById(R.id.padding_value);
        elevationValue = findViewById(R.id.elevation_value);
        positionSpinner = findViewById(R.id.position_spinner);
        marginSeek = findViewById(R.id.margin_seek);
        marginValue = findViewById(R.id.margin_value);

        // 配置字体大小下拉框
        String[] fontSizes = {"小号", "中号", "大号", "特大号"};
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_dark, fontSizes);
        fontAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
        fontSizeSpinner.setAdapter(fontAdapter);

        // 配置目标语言下拉框
        String[] languages = {"中文 (zh)", "英文 (en)", "日文 (ja)", "韩文 (ko)"};
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_dark, languages);
        langAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
        targetLanguageSpinner.setAdapter(langAdapter);

        // 透明度滑块
        bgAlphaSeek.setProgress(configManager.getOverlayBgAlphaPercent());
        textAlphaSeek.setProgress(configManager.getOverlayTextAlphaPercent());
        bgAlphaValue.setText(configManager.getOverlayBgAlphaPercent() + "%");
        textAlphaValue.setText(configManager.getOverlayTextAlphaPercent() + "%");

        bgAlphaSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                bgAlphaValue.setText(progress + "%");
                hasChanges = true;
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        textAlphaSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                textAlphaValue.setText(progress + "%");
                hasChanges = true;
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        // 主界面预览 + 显示模式
        previewDualSwitch.setChecked(configManager.isMainPreviewDual());
        int mode = configManager.getOverlayDisplayMode();
        if (mode == 0) modeBoth.setChecked(true);
        else if (mode == 1) modeTranslation.setChecked(true);
        else modeTranscript.setChecked(true);

        // 位置
        String[] positions = {"顶部", "底部"};
        ArrayAdapter<String> posAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_dark, positions);
        posAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
        positionSpinner.setAdapter(posAdapter);
        positionSpinner.setSelection("bottom".equalsIgnoreCase(configManager.getOverlayPosition()) ? 1 : 0);

        // 样式滑块
        cornerSeek.setProgress(configManager.getOverlayCornerDp());
        paddingSeek.setProgress(configManager.getOverlayPaddingDp());
        elevationSeek.setProgress(configManager.getOverlayElevationDp());
        marginSeek.setProgress(configManager.getOverlayMarginDp());
        cornerValue.setText(configManager.getOverlayCornerDp() + "dp");
        paddingValue.setText(configManager.getOverlayPaddingDp() + "dp");
        elevationValue.setText(configManager.getOverlayElevationDp() + "dp");
        marginValue.setText(configManager.getOverlayMarginDp() + "dp");

        cornerSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) { cornerValue.setText(progress + "dp"); hasChanges = true; }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        paddingSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) { paddingValue.setText(progress + "dp"); hasChanges = true; }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        elevationSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) { elevationValue.setText(progress + "dp"); hasChanges = true; }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        marginSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) { marginValue.setText(progress + "dp"); hasChanges = true; }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        // 监听输入变化
        apiKeyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hasChanges = true;
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 保存按钮
        saveButton.setOnClickListener(v -> saveSettings());
        startFloatingButton.setOnClickListener(v -> startFloatingRecognition());
    }

    private void loadSettings() {
        // 加载API Key
        String apiKey = configManager.getApiKey();
        if (!apiKey.isEmpty()) {
            apiKeyInput.setText(apiKey);
        }

        // 加载字体大小
        String fontSize = configManager.getFontSize();
        switch (fontSize) {
            case "小号":
                fontSizeSpinner.setSelection(0);
                break;
            case "中号":
                fontSizeSpinner.setSelection(1);
                break;
            case "大号":
                fontSizeSpinner.setSelection(2);
                break;
            case "特大号":
                fontSizeSpinner.setSelection(3);
                break;
        }

        // 加载翻译设置
        translationSwitch.setChecked(configManager.isTranslationEnabled());

        // 加载目标语言
        String targetLang = configManager.getTargetLanguage();
        switch (targetLang) {
            case "zh":
                targetLanguageSpinner.setSelection(0);
                break;
            case "en":
                targetLanguageSpinner.setSelection(1);
                break;
            case "ja":
                targetLanguageSpinner.setSelection(2);
                break;
            case "ko":
                targetLanguageSpinner.setSelection(3);
                break;
        }

        // 加载音频输入源
        if (configManager.isAudioSourceMic()) {
            sourceMic.setChecked(true);
        } else {
            sourcePlayback.setChecked(true);
        }
    }

    private void saveSettings() {
        // 保存API Key
        String apiKey = apiKeyInput.getText().toString().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入API Key", Toast.LENGTH_SHORT).show();
            return;
        }
        configManager.setApiKey(apiKey);

        // 保存字体大小
        String fontSize = fontSizeSpinner.getSelectedItem().toString();
        configManager.setFontSize(fontSize);

        // 保存翻译设置
        boolean translationEnabled = translationSwitch.isChecked();
        configManager.setTranslationEnabled(translationEnabled);

        // 保存目标语言
        String targetLangStr = targetLanguageSpinner.getSelectedItem().toString();
        String targetLang = extractLanguageCode(targetLangStr);
        configManager.setTargetLanguage(targetLang);

        // 保存透明度
        configManager.setOverlayBgAlphaPercent(bgAlphaSeek.getProgress());
        configManager.setOverlayTextAlphaPercent(textAlphaSeek.getProgress());

        // 保存主界面预览与显示模式
        configManager.setMainPreviewDual(previewDualSwitch.isChecked());
        int modeSel = 0;
        if (modeTranslation.isChecked()) modeSel = 1; else if (modeTranscript.isChecked()) modeSel = 2;
        configManager.setOverlayDisplayMode(modeSel);

        // 保存样式
        configManager.setOverlayCornerDp(cornerSeek.getProgress());
        configManager.setOverlayPaddingDp(paddingSeek.getProgress());
        configManager.setOverlayElevationDp(elevationSeek.getProgress());
        configManager.setOverlayMarginDp(marginSeek.getProgress());
        configManager.setOverlayPosition(positionSpinner.getSelectedItemPosition() == 1 ? "bottom" : "top");

        // 保存音频输入源
        configManager.setAudioSource(sourceMic.isChecked() ? "mic" : "playback");

        // 提示保存成功
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();

        // 通知悬浮窗刷新样式
        sendBroadcast(new Intent(OverlayService.ACTION_UPDATE_STYLE));

        // 返回结果
        setResult(RESULT_OK);
        finish();
    }

    private void startFloatingRecognition() {
        // 就地保存设置（不关闭页面）
        String apiKey = apiKeyInput.getText().toString().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入API Key", Toast.LENGTH_SHORT).show();
            return;
        }
        configManager.setApiKey(apiKey);

        String fontSize = fontSizeSpinner.getSelectedItem().toString();
        configManager.setFontSize(fontSize);
        configManager.setTranslationEnabled(translationSwitch.isChecked());
        String targetLangStr = targetLanguageSpinner.getSelectedItem().toString();
        String targetLang = extractLanguageCode(targetLangStr);
        configManager.setTargetLanguage(targetLang);
        configManager.setOverlayBgAlphaPercent(bgAlphaSeek.getProgress());
        configManager.setOverlayTextAlphaPercent(textAlphaSeek.getProgress());

        // 音频源
        configManager.setAudioSource(sourceMic.isChecked() ? "mic" : "playback");
        boolean useMic = configManager.isAudioSourceMic();

        // 麦克风权限（仅在选择麦克风时检查）
        if (useMic) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "请先授予麦克风权限", Toast.LENGTH_SHORT).show();
                try {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 2001);
                } catch (Exception ignored) {}
                return;
            }
        }

        // 悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (ActivityNotFoundException e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                } catch (Exception ex) {
                    Toast.makeText(this, "无法打开悬浮窗设置页", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }

        // 若选择系统音频且还未取得屏幕捕获授权，则申请
        if (!useMic) {
            if (projectionResultCode == 0 || projectionDataIntent == null) {
                Intent intent = projectionManager.createScreenCaptureIntent();
                startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
                return;
            }
        }

        // 开启悬浮窗服务（如果尚未启动）
        try {
            startForegroundService(new Intent(this, OverlayService.class));
        } catch (IllegalStateException e) {
            // 低版本设备 fallback
            startService(new Intent(this, OverlayService.class));
        }

        // 启动前台识别服务
        Intent svc = new Intent(this, RecognitionService.class);
        svc.setAction(RecognitionService.ACTION_START);
        if (!useMic) {
            svc.putExtra(RecognitionService.EXTRA_RESULT_CODE, projectionResultCode);
            svc.putExtra(RecognitionService.EXTRA_RESULT_DATA, projectionDataIntent);
        }
        try {
            startForegroundService(svc);
        } catch (IllegalStateException e) {
            startService(svc);
        }
        // 保持在当前页面，避免让用户误以为闪退
        Toast.makeText(this, "已启动识别与悬浮窗", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                projectionResultCode = resultCode;
                projectionDataIntent = data;
                // 拿到授权后再次发起
                startFloatingRecognition();
            } else {
                Toast.makeText(this, "未授予屏幕捕获权限，无法获取系统音频", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String extractLanguageCode(String languageString) {
        // 从 "中文 (zh)" 提取 "zh"
        int start = languageString.indexOf('(');
        int end = languageString.indexOf(')');
        if (start != -1 && end != -1 && end > start) {
            return languageString.substring(start + 1, end);
        }
        return "zh"; // 默认中文
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // 返回按钮
            if (hasChanges) {
                // 可以添加确认对话框
                Toast.makeText(this, "设置未保存", Toast.LENGTH_SHORT).show();
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (hasChanges) {
            Toast.makeText(this, "设置未保存", Toast.LENGTH_SHORT).show();
        }
        super.onBackPressed();
    }
}
