package com.babelstream;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * 音频采集管理器
 * 负责从麦克风录制音频并提供PCM数据
 */
public class AudioCaptureManager {
    private static final String TAG = "AudioCaptureManager";

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private AudioDataCallback callback;

    // 音频参数
    private final int requestedSampleRate;
    private int actualSampleRate;
    private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize;
    private long lastLevelLogTs = 0L;
    // 重采样到请求采样率（如实际为48k而请求为16k，则在回调前重采样）
    private Resampler resampler = null;
    private short[] shortBuf = null;
    private short[] resampleOut = null;
    // 动态自适应：音源/采样率候选与当前位置
    private final int[] sourceCandidates = new int[] {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
    };
    private int currentSourceIndex = -1;
    private java.util.List<Integer> rateCandidates = new java.util.ArrayList<>();
    private int currentRateIndex = -1;
    // 静音检测与自动切换
    private long silentSinceMs = 0L;
    private static final int SILENCE_LEVEL_THRESHOLD = 1; // 0-100
    private static final long SILENCE_WINDOW_MS = 2000;   // 连续静音2秒触发切换
    private boolean reconfiguring = false;

    public interface AudioDataCallback {
        void onAudioData(byte[] data, int length);
        void onError(String error);
    }

    public AudioCaptureManager(int sampleRate) {
        this.requestedSampleRate = sampleRate;
        this.actualSampleRate = 0;
        // 先占位一个安全的 buffer，真正初始化后会按实际采样率再调整
        this.bufferSize = 8192;
    }

    public void setCallback(AudioDataCallback callback) {
        this.callback = callback;
    }

    public boolean startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return false;
        }

        try {
            // 逐个音源 + 采样率回退，提升兼容性（小米/Android 15）
            audioRecord = buildAudioRecordAny();

            if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed for all sources");
                return false;
            }

            audioRecord.startRecording();
            // 打印实际路由到的输入设备，便于确认是否使用内置麦克风/其它设备
            try {
                android.media.AudioDeviceInfo dev = audioRecord.getRoutedDevice();
                if (dev != null) {
                    Log.i(TAG, "Routed device: type=" + dev.getType() + ", name=" + dev.getProductName());
                }
            } catch (Throwable ignore) {}
            isRecording = true;

            // 若实际采样率与请求不同，则建立重采样器到请求采样率
            if (actualSampleRate > 0 && requestedSampleRate > 0 && actualSampleRate != requestedSampleRate) {
                try {
                    resampler = new Resampler(actualSampleRate, requestedSampleRate);
                    // 预分配转换用缓冲区
                    int maxInSamples = Math.max(bufferSize / 2, actualSampleRate / 5); // 粗略估计
                    shortBuf = new short[maxInSamples];
                    resampleOut = new short[maxInSamples * requestedSampleRate / Math.max(1, actualSampleRate) + 8];
                    Log.i(TAG, "Resampling mic from " + actualSampleRate + " to " + requestedSampleRate);
                } catch (Throwable t) {
                    Log.w(TAG, "Init resampler failed, keep original rate: " + t.getMessage());
                    resampler = null;
                }
            } else {
                resampler = null;
            }

            // 启动录音线程
            recordingThread = new Thread(this::recordingLoop, "MicCaptureThread");
            recordingThread.start();

            Log.i(TAG, "Recording started");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            if (callback != null) {
                callback.onError("录音启动失败: " + e.getMessage());
            }
            return false;
        }
    }

    private AudioRecord buildAudioRecord(int source, int sr) {
        try {
            int minBuf = AudioRecord.getMinBufferSize(sr, channelConfig, audioFormat);
            if (minBuf <= 0) return null;
            int buf = Math.max(minBuf * 2, 8192);
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sr)
                    .setChannelMask(channelConfig)
                    .build();
            AudioRecord ar = new AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(buf)
                    .build();
            if (ar.getState() == AudioRecord.STATE_INITIALIZED) {
                this.actualSampleRate = sr;
                this.bufferSize = buf;
                return ar;
            }
            try { ar.release(); } catch (Throwable ignore) {}
            return null;
        } catch (Throwable t) {
            Log.e(TAG, "buildAudioRecord failed: " + t.getMessage());
            return null;
        }
    }

    private AudioRecord buildAudioRecordWithRateFallback(int source) {
        // 初始化采样率候选：优先 48k/44.1k，再融合请求值/16k，去重
        java.util.LinkedHashSet<Integer> set = new java.util.LinkedHashSet<>();
        set.add(48000);
        set.add(44100);
        if (requestedSampleRate > 0) set.add(requestedSampleRate);
        set.add(16000);
        set.add(8000);
        rateCandidates = new java.util.ArrayList<>(set);
        // 遍历候选进行初始化
        int idx = 0;
        for (Integer sr : rateCandidates) {
            AudioRecord ar = buildAudioRecord(source, sr);
            if (ar != null) {
                currentRateIndex = idx;
                Log.i(TAG, "AudioRecord initialized: source=" + source + ", sampleRate=" + sr + ", buffer=" + bufferSize);
                return ar;
            }
            idx++;
        }
        return null;
    }

    private AudioRecord buildAudioRecordAny() {
        for (int i = 0; i < sourceCandidates.length; i++) {
            int src = sourceCandidates[i];
            AudioRecord ar = buildAudioRecordWithRateFallback(src);
            if (ar != null && ar.getState() == AudioRecord.STATE_INITIALIZED) {
                currentSourceIndex = i;
                Log.i(TAG, "AudioRecord source selected: " + src + ", sr=" + actualSampleRate);
                return ar;
            }
        }
        return null;
    }


    // 优先路由到内置麦克风，避免外设/蓝牙抢占
    public void preferBuiltInMic(android.content.Context ctx) {
        if (audioRecord == null) return;
        try {
            android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(android.content.Context.AUDIO_SERVICE);
            if (am == null) return;
            for (android.media.AudioDeviceInfo dev : am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)) {
                if (dev.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    try { audioRecord.setPreferredDevice(dev); } catch (Throwable ignore) {}
                    break;
                }
            }
        } catch (Throwable ignore) {}
    }


    

    // 计算16bit PCM电平：dBFS(-60..0) 映射到 0-100（仅用于日志观察）
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
        double rmsNorm = Math.max(1e-9, rms / 32768.0);
        double dbfs = 20.0 * Math.log10(rmsNorm);
        double clamped = Math.max(-60.0, Math.min(0.0, dbfs));
        int level = (int) Math.round((clamped + 60.0) / 60.0 * 100.0);
        if (level < 0) level = 0; if (level > 100) level = 100;
        return level;
    }

private void recordingLoop() {
        byte[] buffer = new byte[bufferSize];

        while (isRecording) {
            int readSize;
            try {
                // 阻塞式读取更稳定
                readSize = audioRecord.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            } catch (Throwable t) {
                readSize = audioRecord.read(buffer, 0, buffer.length);
            }

            if (readSize > 0) {
                // 每秒打印一次电平，便于在 AudioCaptureManager 下观察
                long now = System.currentTimeMillis();
                int lvl = calcLevelPercent(buffer, readSize);
                if (now - lastLevelLogTs > 1000) {
                    Log.i(TAG, "level=" + lvl);
                    lastLevelLogTs = now;
                }
                // 静音检测：连续静音超过窗口则尝试切换下一个组合
                if (lvl <= SILENCE_LEVEL_THRESHOLD) {
                    if (silentSinceMs == 0L) silentSinceMs = now;
                    if (!reconfiguring && (now - silentSinceMs) >= SILENCE_WINDOW_MS) {
                        trySwitchNextCombination();
                        silentSinceMs = 0L; // 重置计时
                        // 继续下一轮读取
                        continue;
                    }
                } else {
                    silentSinceMs = 0L;
                }
                if (callback != null) {
                    if (resampler != null) {
                        // bytes -> short（小端）
                        int inSamples = Math.min(readSize / 2, shortBuf.length);
                        for (int i = 0; i < inSamples; i++) {
                            int lo = buffer[i * 2] & 0xFF;
                            int hi = buffer[i * 2 + 1] << 8;
                            shortBuf[i] = (short) (hi | lo);
                        }
                        int outSamples = resampler.process(shortBuf, inSamples, resampleOut);
                        int outBytes = outSamples * 2;
                        byte[] out = new byte[outBytes];
                        for (int i = 0; i < outSamples; i++) {
                            out[i * 2] = (byte) (resampleOut[i] & 0xFF);
                            out[i * 2 + 1] = (byte) ((resampleOut[i] >> 8) & 0xFF);
                        }
                        callback.onAudioData(out, out.length);
                    } else {
                        // 原样回调
                        callback.onAudioData(buffer, readSize);
                    }
                }
            } else if (readSize < 0) {
                Log.e(TAG, "Audio read error: " + readSize);
                if (callback != null) {
                    callback.onError("音频读取错误");
                }
                break;
            }
        }
    }


    // 在录音线程内调用：切换到下一个（音源,采样率）组合
    private void trySwitchNextCombination() {
        if (reconfiguring) return;
        reconfiguring = true;
        try {
            int startSourceIdx = currentSourceIndex < 0 ? 0 : currentSourceIndex;
            int startRateIdx = currentRateIndex < 0 ? 0 : currentRateIndex;

            int srcIdx = startSourceIdx;
            int rateIdx = startRateIdx + 1; // 从下一个采样率开始

            // 若可获得当前路由设备的支持采样率，优先采用其列表
            try {
                android.media.AudioDeviceInfo curDev = (audioRecord != null) ? audioRecord.getRoutedDevice() : null;
                if (curDev != null) {
                    int[] devRates = curDev.getSampleRates();
                    if (devRates != null && devRates.length > 0) {
                        java.util.LinkedHashSet<Integer> set = new java.util.LinkedHashSet<>();
                        for (int r : devRates) if (r > 0) set.add(r);
                        // 追加常见采样率作为兜底
                        set.add(48000);
                        set.add(44100);
                        if (requestedSampleRate > 0) set.add(requestedSampleRate);
                        set.add(16000);
                        set.add(8000);
                        java.util.ArrayList<Integer> newList = new java.util.ArrayList<>(set);
                        // 计算“当前采样率”在新列表中的位置
                        int currentRate = (currentRateIndex >= 0 && currentRateIndex < rateCandidates.size())
                                ? rateCandidates.get(currentRateIndex)
                                : actualSampleRate;
                        int idxInNew = newList.indexOf(currentRate);
                        rateCandidates = newList;
                        if (idxInNew >= 0) {
                            rateIdx = idxInNew + 1; // 从当前的下一个开始
                        }
                    }
                }
            } catch (Throwable ignore) {}

            int maxAttempts = Math.max(1, sourceCandidates.length * Math.max(rateCandidates.size(), 1));
            int attempts = 0;

            while (attempts++ < maxAttempts && isRecording) {
                int src = sourceCandidates[srcIdx];

                if (rateIdx >= rateCandidates.size()) {
                    // 进入下一个音源，重建默认候选采样率
                    srcIdx = (srcIdx + 1) % sourceCandidates.length;
                    java.util.LinkedHashSet<Integer> set = new java.util.LinkedHashSet<>();
                    set.add(48000);
                    set.add(44100);
                    if (requestedSampleRate > 0) set.add(requestedSampleRate);
                    set.add(16000);
                    set.add(8000);
                    rateCandidates = new java.util.ArrayList<>(set);
                    rateIdx = 0;
                    if (srcIdx == startSourceIdx && rateIdx == 0) {
                        // 已经完整绕一圈
                        break;
                    }
                    continue;
                }

                int sr = rateCandidates.get(rateIdx);
                Log.i(TAG, "Reconfiguring... try source=" + src + ", sr=" + sr);
                AudioRecord newAR = buildAudioRecord(src, sr);
                if (newAR != null && newAR.getState() == AudioRecord.STATE_INITIALIZED) {
                    try { if (audioRecord != null) audioRecord.stop(); } catch (Throwable ignore) {}
                    try { if (audioRecord != null) audioRecord.release(); } catch (Throwable ignore) {}
                    audioRecord = newAR;
                    actualSampleRate = sr;
                    currentSourceIndex = srcIdx;
                    currentRateIndex = rateIdx;
                    try { audioRecord.startRecording(); } catch (Throwable t) {
                        Log.w(TAG, "startRecording after reconfig failed", t);
                        try { audioRecord.release(); } catch (Throwable ignore) {}
                        audioRecord = null;
                        rateIdx++;
                        continue;
                    }
                    // 打印路由设备
                    try {
                        android.media.AudioDeviceInfo dev = audioRecord.getRoutedDevice();
                        if (dev != null) {
                            Log.i(TAG, "Switched to source=" + src + ", sr=" + sr + ", routedDevice=" + dev.getType() + "/" + dev.getProductName());
                        } else {
                            Log.i(TAG, "Switched to source=" + src + ", sr=" + sr);
                        }
                    } catch (Throwable ignore) {
                        Log.i(TAG, "Switched to source=" + src + ", sr=" + sr);
                    }
                    lastLevelLogTs = 0L; // 立刻打印新路径的 level
                    return; // 切换成功
                }

                // 尝试下一个采样率
                rateIdx++;
            }

            Log.w(TAG, "Reconfiguring exhausted combinations; keep current path");
        } catch (Throwable t) {
            Log.w(TAG, "trySwitchNextCombination error", t);
        } finally {
            reconfiguring = false;
        }
    }

    public void stopRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;

        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop recording thread", e);
            }
        }

        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (Throwable ignore) {}
            try { audioRecord.release(); } catch (Throwable ignore) {}
            audioRecord = null;
        }

        Log.i(TAG, "Recording stopped");
    }

    public boolean isRecording() {
        return isRecording;
    }

    public int getSampleRate() { return (actualSampleRate > 0) ? actualSampleRate : requestedSampleRate; }

    // 输出给识别器的采样率（若发生重采样，则为请求采样率）
    public int getOutputSampleRate() {
        if (actualSampleRate > 0 && requestedSampleRate > 0 && actualSampleRate != requestedSampleRate) return requestedSampleRate;
        return getSampleRate();
    }

    // 简单重采样器（最近邻/累积步进），与 PlaybackCaptureManager 的实现保持一致
    static class Resampler {
        private final int inRate;
        private final int outRate;
        private final double step;
        private double pos = 0;

        Resampler(int inRate, int outRate) {
            this.inRate = inRate;
            this.outRate = outRate;
            this.step = (double) inRate / outRate;
        }

        int process(short[] input, int inSamples, short[] output) {
            int outCount = 0;
            while (pos < inSamples && outCount < output.length) {
                int idx = (int) pos;
                output[outCount++] = input[idx];
                pos += step;
            }
            pos -= inSamples;
            if (pos < 0) pos = 0; // 防御
            return outCount;
        }
    }
}
