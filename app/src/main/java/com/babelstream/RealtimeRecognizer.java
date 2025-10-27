package com.babelstream;

import android.util.Log;

import java.lang.reflect.*;
import java.util.List;

/**
 * 实时语音识别器（自动检测并通过反射调用阿里云 DashScope 实时SDK）
 * - 若设备未集成 SDK，则给出友好错误提示而不崩溃
 * - 若集成了 SDK（依赖已添加），将走真实识别流程
 */
public class RealtimeRecognizer {
    private static final String TAG = "RealtimeRecognizer";

    public interface RecognitionCallback {
        void onTranscription(String text);
        void onTranslation(String text);
        void onStatusChange(String status);
        void onError(String error);
    }

    private RecognitionCallback callback;
    private boolean isRunning = false;

    private final String apiKey;
    private final String model;
    private final int sampleRate;
    private final boolean translationEnabled;
    private final String[] targetLanguages;

    // 反射持有的SDK对象
    private Object translator; // com.alibaba.dashscope.audio.asr.translation.TranslationRecognizerRealtime
    private Class<?> clazzTranslator;

    public RealtimeRecognizer(String apiKey, ConfigManager config) {
        this.apiKey = apiKey;
        this.model = config.getModel();
        this.sampleRate = config.getSampleRate();
        this.translationEnabled = config.isTranslationEnabled();
        this.targetLanguages = new String[]{config.getTargetLanguage()};
    }

    public void setCallback(RecognitionCallback callback) {
        this.callback = callback;
    }

    public boolean start() {
        if (isRunning) return true;

        try {
            // 检测SDK存在
            Class<?> clazzParam = Class.forName("com.alibaba.dashscope.audio.asr.translation.TranslationRecognizerParam");
            clazzTranslator = Class.forName("com.alibaba.dashscope.audio.asr.translation.TranslationRecognizerRealtime");
            Class<?> clazzResult;
            try {
                // 新版SDK
                clazzResult = Class.forName("com.alibaba.dashscope.audio.asr.translation.results.TranslationRecognizerResult");
            } catch (ClassNotFoundException e) {
                // 兼容旧包路径
                clazzResult = Class.forName("com.alibaba.dashscope.audio.asr.translation.TranslationRecognizerResult");
            }
            final Class<?> clazzCallback = Class.forName("com.alibaba.dashscope.common.ResultCallback");

            // 配置API Key
            System.setProperty("DASHSCOPE_API_KEY", apiKey);

            // 构建参数 param = TranslationRecognizerParam.builder()....build();
            Method builderMethod = clazzParam.getMethod("builder");
            Object builder = builderMethod.invoke(null);
            invokeChain(builder, "model", new Class[]{String.class}, new Object[]{model});
            invokeChain(builder, "format", new Class[]{String.class}, new Object[]{"pcm"});
            invokeChain(builder, "sampleRate", new Class[]{int.class}, new Object[]{sampleRate});
            invokeChain(builder, "transcriptionEnabled", new Class[]{boolean.class}, new Object[]{true});
            invokeChain(builder, "sourceLanguage", new Class[]{String.class}, new Object[]{"auto"});
            invokeChain(builder, "translationEnabled", new Class[]{boolean.class}, new Object[]{translationEnabled});
            invokeChain(builder, "translationLanguages", new Class[]{String[].class}, new Object[]{targetLanguages});
            Method build = builder.getClass().getMethod("build");
            Object param = build.invoke(builder);

            // 结果回调
            final Class<?> finalClazzResult = clazzResult;
            Object resultCallback = Proxy.newProxyInstance(
                    clazzCallback.getClassLoader(),
                    new Class[]{clazzCallback},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("onEvent") && args != null && args.length == 1) {
                            handleResult(finalClazzResult, args[0]);
                            return null;
                        } else if (name.equals("onComplete")) {
                            if (callback != null) callback.onStatusChange("识别完成");
                            isRunning = false;
                            return null;
                        } else if (name.equals("onError") && args != null && args.length == 1) {
                            Exception e = (Exception) args[0];
                            if (callback != null) callback.onError("识别错误: " + e.getMessage());
                            isRunning = false;
                            return null;
                        }
                        return null;
                    }
            );

            // 创建识别器并启动
            translator = clazzTranslator.getConstructor().newInstance();
            Method call = clazzTranslator.getMethod("call", clazzParam, clazzCallback);
            call.invoke(translator, param, resultCallback);

            isRunning = true;
            if (callback != null) callback.onStatusChange("识别中");
            return true;

        } catch (ClassNotFoundException e) {
            if (callback != null) callback.onError("未找到阿里云实时SDK，请在Gradle中添加官方依赖后重试");
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "start failed", t);
            if (callback != null) callback.onError("启动失败: " + t.getMessage());
            return false;
        }
    }

    public void sendAudio(byte[] audioData, int length) {
        if (!isRunning || translator == null || clazzTranslator == null) return;
        try {
            byte[] frame = new byte[length];
            System.arraycopy(audioData, 0, frame, 0, length);
            Method m = clazzTranslator.getMethod("sendAudioFrame", byte[].class);
            m.invoke(translator, frame);
        } catch (Throwable t) {
            if (callback != null) callback.onError("音频发送失败: " + t.getMessage());
        }
    }

    public void stop() {
        if (!isRunning || translator == null || clazzTranslator == null) return;
        try {
            Method m = clazzTranslator.getMethod("stop");
            m.invoke(translator);
        } catch (Throwable ignore) {}
        isRunning = false;
        if (callback != null) callback.onStatusChange("已停止");
    }

    public boolean isRunning() { return isRunning; }

    private static Object invokeChain(Object obj, String method, Class<?>[] types, Object[] args)
            throws Exception {
        Method m = obj.getClass().getMethod(method, types);
        return m.invoke(obj, args);
    }

    private void handleResult(Class<?> clazzResult, Object result) {
        if (callback == null || result == null) return;
        try {
            // 转写
            try {
                Method getTrans = clazzResult.getMethod("getTranscriptionResult");
                Object trans = getTrans.invoke(result);
                if (trans != null) {
                    Method getText = trans.getClass().getMethod("getText");
                    Object txt = getText.invoke(trans);
                    if (txt instanceof String && !((String) txt).isEmpty()) {
                        callback.onTranscription((String) txt);
                    }
                }
            } catch (NoSuchMethodException ignore) {}

            // 翻译（新版API：getTranslationResult().getTranslation(lang).getText()）
            try {
                Method getTranslationResult = clazzResult.getMethod("getTranslationResult");
                Object tr = getTranslationResult.invoke(result);
                if (tr != null && targetLanguages != null && targetLanguages.length > 0) {
                    String lang = targetLanguages[0];
                    Method getTranslation = tr.getClass().getMethod("getTranslation", String.class);
                    Object perLang = getTranslation.invoke(tr, lang);
                    if (perLang != null) {
                        Method getText = perLang.getClass().getMethod("getText");
                        Object txt = getText.invoke(perLang);
                        if (txt instanceof String && !((String) txt).isEmpty()) {
                            callback.onTranslation((String) txt);
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                // 兼容旧API：getTranslationResults() -> List
                try {
                    Method getTransList = clazzResult.getMethod("getTranslationResults");
                    Object listObj = getTransList.invoke(result);
                    if (listObj instanceof List) {
                        List<?> list = (List<?>) listObj;
                        if (!list.isEmpty()) {
                            Object tr = list.get(0);
                            Method getText2 = tr.getClass().getMethod("getText");
                            Object txt2 = getText2.invoke(tr);
                            if (txt2 instanceof String && !((String) txt2).isEmpty()) {
                                callback.onTranslation((String) txt2);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            if (callback != null) callback.onError("结果处理失败: " + t.getMessage());
        }
    }
}
