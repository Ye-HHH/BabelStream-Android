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
    private final int sampleRate;
    private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private final int bufferSize;

    public interface AudioDataCallback {
        void onAudioData(byte[] data, int length);
        void onError(String error);
    }

    public AudioCaptureManager(int sampleRate) {
        this.sampleRate = sampleRate;
        this.bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
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
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                return false;
            }

            audioRecord.startRecording();
            isRecording = true;

            // 启动录音线程
            recordingThread = new Thread(this::recordingLoop);
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

    private void recordingLoop() {
        byte[] buffer = new byte[bufferSize];

        while (isRecording) {
            int readSize = audioRecord.read(buffer, 0, bufferSize);

            if (readSize > 0 && callback != null) {
                // 回调音频数据
                callback.onAudioData(buffer, readSize);
            } else if (readSize < 0) {
                Log.e(TAG, "Audio read error: " + readSize);
                if (callback != null) {
                    callback.onError("音频读取错误");
                }
                break;
            }
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
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        Log.i(TAG, "Recording stopped");
    }

    public boolean isRecording() {
        return isRecording;
    }

    public int getSampleRate() {
        return sampleRate;
    }
}
