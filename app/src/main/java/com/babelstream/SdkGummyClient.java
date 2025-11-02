package com.babelstream;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeNuiCallback;
import com.alibaba.idst.nui.KwsResult;
import com.alibaba.idst.nui.NativeNui;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 基于阿里云 Gummy Android SDK 的识别客户端封装。
 * 负责：
 * - 初始化/配置 NativeNui
 * - 从采集端获取 PCM（通过环形缓冲）
 * - 将识别/翻译结果回调给上层（与旧版 RealtimeRecognizer 的回调形态保持一致）
 */
public class SdkGummyClient {
    public interface RecognitionCallback {
        void onTranscription(String text);
        void onTranslation(String text);
        void onStatusChange(String status);
        void onError(String error);
    }

    private static final String TAG = "SdkGummyClient";

    private final Context context;
    private final ConfigManager config;
    private final int sampleRate;
    private final NativeNui nui = new NativeNui();
    private final NativeNui nuiUtils = new NativeNui(Constants.ModeType.MODE_UTILS);
    private final PcmRingBuffer ringBuffer = new PcmRingBuffer(256 * 1024); // 256KB 环形缓冲
    private volatile RecognitionCallback cb;
    private volatile boolean running = false;
    private volatile boolean inited = false;
    private volatile boolean started = false;

    public SdkGummyClient(Context ctx, ConfigManager cfg, int sr) {
        this.context = ctx.getApplicationContext();
        this.config = cfg;
        this.sampleRate = sr > 0 ? sr : 16000;
    }

    public void setCallback(RecognitionCallback callback) { this.cb = callback; }

    public boolean start() {
        if (running) return true;
        try {
            String deviceId;
            try {
                deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            } catch (Throwable t) { deviceId = "android"; }

            String url = config.getWsEndpoint();
            if (url == null || url.trim().isEmpty()) {
                url = "wss://dashscope.aliyuncs.com/api-ws/v1/inference/";
            }
            if (!url.endsWith("/")) url = url + "/";

            // 准备 SDK 资源到本地目录（优先外部缓存目录），避免 native 层找不到 assets 造成崩溃
            java.io.File ext = context.getExternalCacheDir();
            java.io.File workDir = (ext != null) ? new java.io.File(ext, "debug") : new java.io.File(context.getFilesDir(), "nui");
            if (!workDir.exists()) { try { workDir.mkdirs(); } catch (Throwable ignore) {} }
            copySdkAssetsIfNeeded(workDir);

            JSONObject parameters = new JSONObject();
            parameters.put("url", url);
            parameters.put("device_id", deviceId);
            // 按官方示例：有的 AAR 以字符串常量给出，优先使用之，回退到整型 1
            try {
                Object modeConst = com.alibaba.idst.nui.Constants.ModeFullCloud;
                if (modeConst != null) {
                    parameters.put("service_mode", modeConst);
                } else {
                    parameters.put("service_mode", 1);
                }
            } catch (Throwable ignore) {
                parameters.put("service_mode", 1);
            }
            // 提供调试与资源路径
            parameters.put("save_log", true);
            parameters.put("debug_path", workDir.getAbsolutePath());
            // 可选：保存音频与日志回传等级（示例使用字符串型）
            try { parameters.put("save_wav", "true"); } catch (Throwable ignore) {}
            try {
                int lvlIntParam = 0;
                try {
                    com.alibaba.idst.nui.Constants.LogLevel lv = null;
                    try { lv = com.alibaba.idst.nui.Constants.LogLevel.valueOf("INFO"); } catch (Throwable ignore) {}
                    if (lv == null) {
                        try { lv = com.alibaba.idst.nui.Constants.LogLevel.valueOf("DEBUG"); } catch (Throwable ignore) {}
                    }
                    if (lv != null) {
                        lvlIntParam = com.alibaba.idst.nui.Constants.LogLevel.toInt(lv);
                    }
                } catch (Throwable ignore) {}
                parameters.put("log_track_level", String.valueOf(lvlIntParam));
            } catch (Throwable ignore) {}
            // 避免在 initialize 携带 apikey 触发参数校验失败；apikey 于 startDialog 传入

            emitStatus("初始化SDK...");
            Constants.LogLevel level = null;
            try { level = Constants.LogLevel.valueOf("INFO"); } catch (Throwable ignore) {}
            if (level == null) {
                try { level = Constants.LogLevel.valueOf("DEBUG"); } catch (Throwable ignore) {}
            }
            if (level == null) {
                try {
                    Object[] arr = Constants.LogLevel.class.getEnumConstants();
                    if (arr != null && arr.length > 0) level = (Constants.LogLevel) arr[0];
                } catch (Throwable ignore) {}
            }
            int initRet = nui.initialize(nuiCallback, parameters.toString(), level, true);
            Log.i(TAG, "initialize ret=" + initRet);
            emitStatus("initialize ret=" + initRet);
            try { Log.i(TAG, "INIT params url=" + url + ", deviceId=" + deviceId + ", workDir=" + workDir.getAbsolutePath()); } catch (Throwable ignore) {}
            if (initRet != 0) {
                emitError("SDK初始化失败: ret=" + initRet);
                return false;
            }
            inited = true;

            JSONObject nls = new JSONObject();
            nls.put("model", config.getModel());
            nls.put("sr_format", "pcm");
            nls.put("sample_rate", sampleRate);
            nls.put("transcription_enabled", true);
            nls.put("translation_enabled", config.isTranslationEnabled());
            // 将 apikey 也写入 nls_config，兼容设备从此处读取
            try { nls.put("apikey", config.getApiKey()); } catch (Throwable ignore) {}
            try { nls.put("app_key", config.getApiKey()); } catch (Throwable ignore) {}
            // 提高易用性：自动语种 + 结束静音阈值
            try { nls.put("source_language", "auto"); } catch (Throwable ignore) {}
            try { nls.put("max_end_silence", 800); } catch (Throwable ignore) {}
            if (config.isTranslationEnabled()) {
                JSONArray arr = new JSONArray();
                arr.put(config.getTargetLanguage());
                // 与示例保持一致：传字符串形式的 JSON 数组
                nls.put("translation_target_languages", arr.toString());
            }

            JSONObject params = new JSONObject();
            params.put("service_type", 4);
            // 明确指定 DashScope 协议
            try { params.put("service_protocol", 1); } catch (Throwable ignore) {}
            // 直接传递对象（兼容性更好）；部分版本对字符串形式解析后读取不到 model
            params.put("nls_config", nls);
            // 一并在 setParams 声明基础连接信息，避免后续缺上下文
            try { params.put("url", url); } catch (Throwable ignore) {}
            try { params.put("device_id", deviceId); } catch (Throwable ignore) {}
            int setRet = nui.setParams(params.toString());
            Log.i(TAG, "setParams ret=" + setRet);
            emitStatus("setParams ret=" + setRet);
            try { Log.i(TAG, "setParams json=" + params.toString()); } catch (Throwable ignore) {}
            if (setRet != 0) {
                emitError("设置参数失败: ret=" + setRet);
                return false;
            }

            // 直接使用 API Key，避免在目标设备上访问 tokens 接口失败（SSL/url illegal）
            String key = config.getApiKey();
            try { params.put("apikey", key); } catch (Throwable ignore) {}
            org.json.JSONObject dialog = new org.json.JSONObject();
            try { dialog.put("apikey", key); } catch (Throwable ignore) {}
            // 兜底：在 dialog 参数中也携带 model，规避某些版本丢失 model 的问题
            try { dialog.put("model", config.getModel()); } catch (Throwable ignore) {}
            int startRet = nui.startDialog(Constants.VadMode.TYPE_P2T, dialog.toString());
            Log.i(TAG, "startDialog ret=" + startRet);
            emitStatus("startDialog ret=" + startRet);
            try { Log.i(TAG, "dialog json(apikey masked)=" + dialog.toString()); } catch (Throwable ignore) {}
            if (startRet != 0) {
                emitError("启动识别失败: ret=" + startRet);
                return false;
            }
            emitStatus("识别中...");
            started = true;
            running = true;
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "start error", t);
            emitError("启动失败: " + t.getMessage());
            return false;
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        try { if (started) nui.stopDialog(); } catch (Throwable ignore) {}
        try { if (inited) nui.release(); } catch (Throwable ignore) {}
        inited = false;
        started = false;
        emitStatus("识别已停止");
    }

    /** 提供给采集端写入 PCM 16bit LE mono 数据 */
    public void offerPcm(byte[] data, int length) {
        if (data == null || length <= 0) return;
        ringBuffer.write(data, 0, Math.min(length, data.length));
    }

    private void emitStatus(String s) { try { if (cb != null) cb.onStatusChange(s); } catch (Throwable ignore) {} }
    private void emitError(String s) { try { if (cb != null) cb.onError(s); } catch (Throwable ignore) {} }
    private void emitTranscription(String t) { try { if (cb != null && t != null) cb.onTranscription(t); } catch (Throwable ignore) {} }
    private void emitTranslation(String t) { try { if (cb != null && t != null) cb.onTranslation(t); } catch (Throwable ignore) {} }

    private final INativeNuiCallback nuiCallback = new INativeNuiCallback() {
        @Override
        public void onNuiEventCallback(Constants.NuiEvent event, int resultCode, int arg2, KwsResult kwsResult, AsrResult asrResult) {
            try {
                try { emitStatus("NuiEvent=" + String.valueOf(event) + ", code=" + resultCode); } catch (Throwable ignore) {}
                // 优先解析 allResponse（与官方示例一致）
                String resp = null;
                if (asrResult != null) {
                    try {
                        java.lang.reflect.Field f = asrResult.getClass().getField("allResponse");
                        Object v = f.get(asrResult);
                        if (v instanceof String) resp = (String) v;
                    } catch (Throwable ignore) {
                        try { resp = invokeString(asrResult, "getAllResponse"); } catch (Throwable ignore2) {}
                    }
                }

                if (resp != null && !resp.isEmpty()) {
                    org.json.JSONObject o = new org.json.JSONObject(resp);
                    org.json.JSONObject payload = o.optJSONObject("payload");
                    org.json.JSONObject output = (payload != null) ? payload.optJSONObject("output") : null;
                    String asrText = null;
                    String trText = null;
                    if (output != null) {
                        org.json.JSONObject transcription = output.optJSONObject("transcription");
                        if (transcription != null) asrText = firstNonEmpty(transcription, "text", "result", "transcript");
                        org.json.JSONArray translations = output.optJSONArray("translations");
                        if (translations != null && translations.length() > 0) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < translations.length(); i++) {
                                org.json.JSONObject it = translations.optJSONObject(i);
                                if (it != null) {
                                    String t = firstNonEmpty(it, "text", "result", "translation");
                                    if (t != null && !t.isEmpty()) sb.append(t);
                                }
                            }
                            trText = sb.toString();
                        }
                        if ((asrText == null || asrText.isEmpty())) asrText = firstNonEmpty(output, "text", "result");
                    }
                    if ((asrText == null || asrText.isEmpty())) { asrText = deepFindText(o); }
                    if (asrText != null && !asrText.isEmpty()) {
                        try { emitStatus("transcription:" + (asrText.length()>20?asrText.substring(0,20)+"…":asrText)); } catch (Throwable ignore) {}
                        if (config.isTranslationEnabled() && (trText == null || trText.isEmpty())) { emitTranslation(asrText); } else { emitTranscription(asrText);
                        try { Log.i(TAG, "EXTRACT asrText=" + asrText); } catch (Throwable ignore) {} }
                    }
                    if (trText != null && !trText.isEmpty()) {
                        try { emitStatus("translation:" + (trText.length()>20?trText.substring(0,20)+"…":trText)); } catch (Throwable ignore) {}
                        emitTranslation(trText);
                        try { Log.i(TAG, "EXTRACT trText=" + trText); } catch (Throwable ignore) {}
                    }
                    org.json.JSONObject header = o.optJSONObject("header");
                    if (header != null && header.optString("event", "").contains("failed")) {
                        String em = header.optString("error_message", resp);
                        emitError("SDK错误:" + em);
                    }
                } else if (asrResult != null) {
                    // 退化：尝试常见 getter
                    String text = null;
                    try { text = invokeString(asrResult, "getResult"); } catch (Throwable ignore) {}
                    if (text == null || text.isEmpty()) {
                        try { text = invokeString(asrResult, "getText"); } catch (Throwable ignore) {}
                    }
                    if (text != null && !text.isEmpty()) {
                        try { emitStatus("text:" + (text.length()>20?text.substring(0,20)+"…":text)); } catch (Throwable ignore) {}
                        if (config.isTranslationEnabled()) emitTranslation(text); else emitTranscription(text);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "onNuiEvent parse error", t);
            }
        }

        @Override
        public void onNuiAudioStateChanged(Constants.AudioState state) {
            try { Log.i(TAG, "audioState=" + state); } catch (Throwable ignore) {}
        }

        @Override
        public int onNuiNeedAudioData(byte[] buffer, int len) {
            if (buffer == null || len <= 0) return 0;
            int total = 0;
            long startTs = System.currentTimeMillis();
            int maxWaitMs = 100; // 最多等待100ms以尽量凑齐数据
            while (total < len && (System.currentTimeMillis() - startTs) <= maxWaitMs) {
                int got = ringBuffer.read(buffer, total, len - total);
                if (got > 0) {
                    total += got;
                } else {
                    try { Thread.sleep(5); } catch (InterruptedException ignore) {}
                }
            }
            if (total <= 0) {
                // 避免返回0导致SDK报错，填充一小段静音
                int pad = Math.min(len, 320); // 约10ms@16kHz
                for (int i = 0; i < pad; i++) buffer[i] = 0;
                total = pad;
            }
            return total;
        }

        @Override
        public void onNuiLogTrackCallback(Constants.LogLevel level, String log) {
            try { Log.d(TAG, "sdklog[" + level + "]: " + log); } catch (Throwable ignore) {}
            try { emitStatus("SDKLog[" + level + "]: " + log); } catch (Throwable ignore) {}
            try {
                if (log != null) {
                    String low = log.toLowerCase();
                    if (low.contains("null sdk request")) {
                        emitError("SDK会话未建立：请检查API Key/网络/设备时间");
                    }
                }
            } catch (Throwable ignore) {}
        }

        @Override
        public void onNuiVprEventCallback(Constants.NuiVprEvent event) {
            // 语音密码/说话人验证等事件，当前不使用
        }

        @Override
        public void onNuiAudioRMSChanged(float rms) {
            // 音频 RMS 回调（电平变化），当前仅日志观测；若需要可对接 UI 电平
            try { Log.v(TAG, "rms=" + rms); } catch (Throwable ignore) {}
        }
    };

    // 通过反射尝试调用无参 getter，返回 String
    private static String invokeString(Object target, String method) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(method);
            Object v = m.invoke(target);
            return (v instanceof String) ? (String) v : (v != null ? v.toString() : null);
        } catch (Throwable t) {
            return null;
        }
    }

    private void copySdkAssetsIfNeeded(java.io.File destRoot) {
        try {
            android.content.res.AssetManager am = context.getAssets();
            // 读取 copylist
            java.io.InputStream is = am.open("copylist.txt");
            java.util.List<String> lines = new java.util.ArrayList<>();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                String l; while ((l = br.readLine()) != null) { l = l.trim(); if (!l.isEmpty()) lines.add(l); }
            }
            for (String item : lines) {
                if (item.equals("tts")) {
                    copyAssetDir(am, "tts", new java.io.File(destRoot, "tts"));
                } else {
                    copyAssetFile(am, item, new java.io.File(destRoot, item));
                }
            }
        } catch (Throwable t) {
            try { Log.w(TAG, "copySdkAssetsIfNeeded error: " + t.getMessage()); } catch (Throwable ignore) {}
        }
    }

    private void copyAssetDir(android.content.res.AssetManager am, String assetDir, java.io.File outDir) throws java.io.IOException {
        String[] list = am.list(assetDir);
        if (list == null) return;
        if (!outDir.exists()) outDir.mkdirs();
        for (String name : list) {
            String path = assetDir + "/" + name;
            String[] sub = am.list(path);
            if (sub != null && sub.length > 0) {
                copyAssetDir(am, path, new java.io.File(outDir, name));
            } else {
                copyAssetFile(am, path, new java.io.File(outDir, name));
            }
        }
    }

    private void copyAssetFile(android.content.res.AssetManager am, String assetName, java.io.File outFile) throws java.io.IOException {
        // 若已存在且大小一致则跳过
        try (java.io.InputStream in = am.open(assetName)) {
            if (outFile.exists() && outFile.length() == in.available()) return;
            java.io.File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (java.io.OutputStream os = new java.io.FileOutputStream(outFile)) {
                byte[] buf = new byte[8192]; int r; while ((r = in.read(buf)) != -1) os.write(buf, 0, r);
            }
        }
    }

    /** 简单线程安全环形缓冲 */
    static final class PcmRingBuffer {
        private final byte[] buf;
        private int r = 0, w = 0, size = 0;
        PcmRingBuffer(int cap) { buf = new byte[Math.max(8192, cap)]; }
        synchronized int read(byte[] out, int off, int len) {
            int n = Math.min(len, size);
            for (int i = 0; i < n; i++) {
                out[off + i] = buf[r];
                r = (r + 1) % buf.length;
            }
            size -= n;
            return n;
        }
        synchronized void write(byte[] in, int off, int len) {
            int n = Math.min(len, in.length - off);
            for (int i = 0; i < n; i++) {
                buf[w] = in[off + i];
                w = (w + 1) % buf.length;
                if (size < buf.length) size++; else { // 覆盖最旧数据
                    r = (r + 1) % buf.length;
                }
            }
        }
    }

    // 在对象上按候选key取第一个非空字符串
    private static String firstNonEmpty(org.json.JSONObject obj, String... keys) {
        for (String k : keys) {
            String v = obj.optString(k, null);
            if (v != null && !v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
        }
        return null;
    }

    // 深度搜索 JSON，返回第一个看起来像文本的字段
    private static String deepFindText(org.json.JSONObject obj) {
        java.util.Deque<Object> stack = new java.util.ArrayDeque<>();
        stack.push(obj);
        while (!stack.isEmpty()) {
            Object cur = stack.pop();
            if (cur instanceof org.json.JSONObject) {
                org.json.JSONObject jo = (org.json.JSONObject) cur;
                String v = firstNonEmpty(jo, "text", "result", "transcript", "display_text");
                if (v != null && !v.isEmpty()) return v;
                java.util.Iterator<String> it = jo.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    Object nv = jo.opt(k);
                    if (nv instanceof org.json.JSONObject || nv instanceof org.json.JSONArray) stack.push(nv);
                }
            } else if (cur instanceof org.json.JSONArray) {
                org.json.JSONArray arr = (org.json.JSONArray) cur;
                for (int i = 0; i < arr.length(); i++) {
                    Object nv = arr.opt(i);
                    if (nv instanceof org.json.JSONObject || nv instanceof org.json.JSONArray) stack.push(nv);
                    else if (nv instanceof String) {
                        String sv = (String) nv;
                        if (!sv.isEmpty() && sv.length() < 4096) return sv;
                    }
                }
            }
        }
        return null;
    }
    

    private static String mask(String key) {
        if (key == null) return "null";
        int n = key.length();
        if (n <= 6) return key;
        return key.substring(0, 6) + "***" + key.substring(n - 2);
    }
}
