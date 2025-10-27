package com.babelstream;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

/**
 * 系统音频采集（扬声器）管理器
 * 使用 AudioPlaybackCapture + MediaProjection 捕获系统播放音频
 * 将采样率重采样为 targetSampleRate（默认16000Hz）并以 PCM 16bit 单声道回调
 */
public class PlaybackCaptureManager {
    private static final String TAG = "PlaybackCapture";

    public interface AudioDataCallback {
        void onAudioData(byte[] data, int length);
        void onError(String error);
    }

    private final int targetSampleRate;
    private final int inputSampleRate; // 采用48k优先
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private AudioDataCallback callback;
    private final MediaProjection mediaProjection;

    public PlaybackCaptureManager(MediaProjection projection, int targetSampleRate) {
        this.mediaProjection = projection;
        this.targetSampleRate = targetSampleRate;
        this.inputSampleRate = 48000; // 大多数设备输出48k
    }

    public void setCallback(AudioDataCallback cb) {
        this.callback = cb;
    }

    public boolean start() {
        if (isRecording) return true;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (callback != null) callback.onError("系统音频捕获仅支持Android10+");
            return false;
        }

        try {
            AudioPlaybackCaptureConfiguration config =
                    new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .build();

            AudioFormat inputFormat = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(inputSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build();

            int bufferSize = AudioRecord.getMinBufferSize(
                    inputSampleRate,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            bufferSize = Math.max(bufferSize, inputSampleRate * 2 * 2 / 10); // 至少100ms缓冲

            audioRecord = new AudioRecord.Builder()
                    .setAudioFormat(inputFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build();

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                if (callback != null) callback.onError("AudioRecord初始化失败");
                return false;
            }

            audioRecord.startRecording();
            isRecording = true;
            recordingThread = new Thread(this::recordingLoop, "PlaybackCaptureThread");
            recordingThread.start();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
            if (callback != null) callback.onError("系统音频捕获启动失败: " + e.getMessage());
            return false;
        }
    }

    private void recordingLoop() {
        byte[] buffer = new byte[4096 * 4]; // stereo 16bit
        short[] shortBuf = new short[buffer.length / 2];
        short[] monoBuf = new short[shortBuf.length / 2];

        Resampler resampler = new Resampler(inputSampleRate, targetSampleRate);
        short[] resampleOut = new short[monoBuf.length];

        while (isRecording) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read <= 0) continue;

            // bytes -> short
            int samples = read / 2;
            for (int i = 0; i < samples; i++) {
                int lo = buffer[i * 2] & 0xFF;
                int hi = buffer[i * 2 + 1] << 8;
                shortBuf[i] = (short) (hi | lo);
            }

            // stereo -> mono (平均)
            int monoSamples = samples / 2;
            for (int i = 0, j = 0; i < monoSamples; i++, j += 2) {
                int s = (shortBuf[j] + shortBuf[j + 1]) / 2;
                monoBuf[i] = (short) s;
            }

            // 重采样到 targetSampleRate
            int outSamples = resampler.process(monoBuf, monoSamples, resampleOut);

            // short -> bytes
            int outBytes = outSamples * 2;
            byte[] out = new byte[outBytes];
            for (int i = 0; i < outSamples; i++) {
                out[i * 2] = (byte) (resampleOut[i] & 0xFF);
                out[i * 2 + 1] = (byte) ((resampleOut[i] >> 8) & 0xFF);
            }

            if (callback != null) callback.onAudioData(out, out.length);
        }
    }

    public void stop() {
        isRecording = false;
        if (recordingThread != null) {
            try { recordingThread.join(1000); } catch (InterruptedException ignored) {}
        }
        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (Exception ignored) {}
            audioRecord.release();
            audioRecord = null;
        }
    }

    public boolean isRecording() { return isRecording; }

    // 简单重采样器（最近邻/累积步进），效率优先
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
            return outCount;
        }
    }
}

