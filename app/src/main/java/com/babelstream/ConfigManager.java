package com.babelstream;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 配置管理器
 * 负责保存和读取应用配置 (API Key, 字体大小等)
 */
public class ConfigManager {
    private static final String PREFS_NAME = "BabelStreamConfig";

    // 配置项Key
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_MODEL = "model";
    private static final String KEY_SAMPLE_RATE = "sample_rate";
    private static final String KEY_TRANSLATION_ENABLED = "translation_enabled";
    private static final String KEY_TARGET_LANGUAGE = "target_language";
    private static final String KEY_OVERLAY_BG_ALPHA = "overlay_bg_alpha";       // 0-100
    private static final String KEY_OVERLAY_TEXT_ALPHA = "overlay_text_alpha";   // 0-100
    private static final String KEY_MAIN_PREVIEW_DUAL = "main_preview_dual";     // bool
    private static final String KEY_OVERLAY_DISPLAY_MODE = "overlay_display_mode"; // 0=both,1=translation,2=transcript
    private static final String KEY_OVERLAY_CORNER_DP = "overlay_corner_dp";     // 0-32
    private static final String KEY_OVERLAY_PADDING_DP = "overlay_padding_dp";   // 0-32
    private static final String KEY_OVERLAY_ELEVATION_DP = "overlay_elev_dp";    // 0-16
    private static final String KEY_OVERLAY_POSITION = "overlay_position";       // top/bottom
    private static final String KEY_OVERLAY_MARGIN_DP = "overlay_margin_dp";     // 0-200
    private static final String KEY_AUDIO_SOURCE = "audio_source";               // playback|mic

    private final SharedPreferences prefs;

    public ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ========== API Key ==========
    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String apiKey) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    public boolean hasApiKey() {
        return !getApiKey().isEmpty();
    }

    // ========== 字体大小 ==========
    public String getFontSize() {
        return prefs.getString(KEY_FONT_SIZE, "中号"); // 默认中号
    }

    public void setFontSize(String fontSize) {
        prefs.edit().putString(KEY_FONT_SIZE, fontSize).apply();
    }

    public int getFontSizePixels() {
        String size = getFontSize();
        switch (size) {
            case "小号": return 32;
            case "中号": return 48;
            case "大号": return 64;
            case "特大号": return 80;
            default: return 48;
        }
    }

    // ========== 识别模型 ==========
    public String getModel() {
        return prefs.getString(KEY_MODEL, "gummy-realtime-v1");
    }

    public void setModel(String model) {
        prefs.edit().putString(KEY_MODEL, model).apply();
    }

    // ========== 采样率 ==========
    public int getSampleRate() {
        return prefs.getInt(KEY_SAMPLE_RATE, 16000);
    }

    public void setSampleRate(int sampleRate) {
        prefs.edit().putInt(KEY_SAMPLE_RATE, sampleRate).apply();
    }

    // ========== 翻译设置 ==========
    public boolean isTranslationEnabled() {
        return prefs.getBoolean(KEY_TRANSLATION_ENABLED, true);
    }

    public void setTranslationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRANSLATION_ENABLED, enabled).apply();
    }

    public String getTargetLanguage() {
        return prefs.getString(KEY_TARGET_LANGUAGE, "zh"); // 默认中文
    }

    public void setTargetLanguage(String language) {
        prefs.edit().putString(KEY_TARGET_LANGUAGE, language).apply();
    }

    // ========== 悬浮窗透明度 ==========
    // 背景透明度，0=全透明 100=不透明
    public int getOverlayBgAlphaPercent() {
        return prefs.getInt(KEY_OVERLAY_BG_ALPHA, 50); // 默认50%
    }

    public void setOverlayBgAlphaPercent(int percent) {
        int p = Math.max(0, Math.min(100, percent));
        prefs.edit().putInt(KEY_OVERLAY_BG_ALPHA, p).apply();
    }

    // 文字透明度，0=全透明 100=不透明
    public int getOverlayTextAlphaPercent() {
        return prefs.getInt(KEY_OVERLAY_TEXT_ALPHA, 100); // 默认100%
    }

    public void setOverlayTextAlphaPercent(int percent) {
        int p = Math.max(0, Math.min(100, percent));
        prefs.edit().putInt(KEY_OVERLAY_TEXT_ALPHA, p).apply();
    }

    public float getOverlayBgAlpha() { // 0.0-1.0
        return getOverlayBgAlphaPercent() / 100f;
    }

    public float getOverlayTextAlpha() { // 0.0-1.0
        return getOverlayTextAlphaPercent() / 100f;
    }

    // ========== 主界面双行预览 ==========
    public boolean isMainPreviewDual() {
        return prefs.getBoolean(KEY_MAIN_PREVIEW_DUAL, true);
    }

    public void setMainPreviewDual(boolean dual) {
        prefs.edit().putBoolean(KEY_MAIN_PREVIEW_DUAL, dual).apply();
    }

    // ========== 显示模式 ==========
    // 0=双行, 1=仅译文, 2=仅原文
    public int getOverlayDisplayMode() {
        return prefs.getInt(KEY_OVERLAY_DISPLAY_MODE, 0);
    }

    public void setOverlayDisplayMode(int mode) {
        prefs.edit().putInt(KEY_OVERLAY_DISPLAY_MODE, mode).apply();
    }

    // ========== 悬浮窗样式 ==========
    public int getOverlayCornerDp() { return prefs.getInt(KEY_OVERLAY_CORNER_DP, 8); }
    public void setOverlayCornerDp(int dp) { prefs.edit().putInt(KEY_OVERLAY_CORNER_DP, dp).apply(); }

    public int getOverlayPaddingDp() { return prefs.getInt(KEY_OVERLAY_PADDING_DP, 8); }
    public void setOverlayPaddingDp(int dp) { prefs.edit().putInt(KEY_OVERLAY_PADDING_DP, dp).apply(); }

    public int getOverlayElevationDp() { return prefs.getInt(KEY_OVERLAY_ELEVATION_DP, 4); }
    public void setOverlayElevationDp(int dp) { prefs.edit().putInt(KEY_OVERLAY_ELEVATION_DP, dp).apply(); }

    public String getOverlayPosition() { return prefs.getString(KEY_OVERLAY_POSITION, "top"); }
    public void setOverlayPosition(String pos) { prefs.edit().putString(KEY_OVERLAY_POSITION, pos).apply(); }

    public int getOverlayMarginDp() { return prefs.getInt(KEY_OVERLAY_MARGIN_DP, 100); }
    public void setOverlayMarginDp(int dp) { prefs.edit().putInt(KEY_OVERLAY_MARGIN_DP, dp).apply(); }

    // ========== 清除所有配置 ==========
    public void clearAll() {
        prefs.edit().clear().apply();
    }

    // ========== 音频输入源 ==========
    // playback = 系统音频 (AudioPlaybackCapture，需要 MediaProjection)
    // mic       = 麦克风 (AudioRecord)
    public String getAudioSource() {
        return prefs.getString(KEY_AUDIO_SOURCE, "playback");
    }

    public void setAudioSource(String source) {
        if (!"mic".equals(source)) source = "playback";
        prefs.edit().putString(KEY_AUDIO_SOURCE, source).apply();
    }

    public boolean isAudioSourceMic() { return "mic".equals(getAudioSource()); }
    public boolean isAudioSourcePlayback() { return !isAudioSourceMic(); }
}
