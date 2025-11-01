/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mit.alibaba.nuidemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeTtsCallback;
import com.alibaba.idst.nui.NativeNui;

// 本样例展示在线语音合成使用方法
// Android SDK 详细说明：
// WebSocket API: https://help.aliyun.com/zh/model-studio/sambert-websocket-api
public class DashSambertTtsActivity extends Activity implements View.OnClickListener, OnItemSelectedListener {
    private static final String TAG = "SambertTTS";

    private String g_apikey = "";
    private String g_url = "";

    NativeNui nui_tts_instance = new NativeNui(Constants.ModeType.MODE_TTS);
    final static String CN_PREVIEW ="基于达摩院改良的自回归韵律模型，Sambert 融合了 SAMBERT+NSFGAN " +
            "深度神经网络算法与传统领域知识，提供高效的文字转语音服务。该技术具备推理速度快、" +
            "合成效果卓越、读音精准、韵律自然、声音还原度高以及表现力强等优点。";

    private final Map<String, List<String>> paramMap = new HashMap<>();
    private Button ttsStartBtn, ttsQuitBtn, ttsCancelBtn, ttsPauseBtn, ttsResumeBtn, ttsClearTextBtn;
    private Switch mSaveAudioSwitch;
    private Spinner mPitchSpin, mFontSpin, mSpeedSpin, mVolumeSpin, mFormatSpin;
    private EditText ttsEditView;
    private TextView eventView;

    private String debug_path;
    private String mFontName = "sambert-zhinan-v1";
    private String mSampleRate = "48000";
    private String mEncodeType = "pcm"; // AudioPlayer中AudioTrack只能播放PCM格式，其他音频格式请另外编写播放器代码
    boolean initialized = false;
    private String curTaskId = "";
    private String mSynthesisAudioFilePath = "";
    private OutputStream mSynthesisAudioFile = null;

    private long startTimestamp = 0;
    private long startedTimestamp = 0;
    private long firstDataTimestamp = 0;
    private boolean firstDataFlag = true;

    //  AudioPlayer默认采样率是16000
    private final AudioPlayer mAudioTrack =  new AudioPlayer(new AudioPlayerCallback() {
        @Override
        public void playStart() {
            Log.i(TAG, "start play");
        }
        @Override
        public void playOver() {
            Log.i(TAG, "play over");
        }
        @Override
        public void playSoundLevel(int level) {}
    });

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sambert_tts);

        String version = nui_tts_instance.GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DashSambertTtsActivity.this, version_text, Toast.LENGTH_SHORT).show();
            }
        });

        // 获取传递的参数
        Intent intent = getIntent();
        if (intent != null) {
            g_apikey = intent.getStringExtra("apikey");
            g_url = intent.getStringExtra("url");
            Log.i(TAG, "Get access ->\n API Key:" + g_apikey + "\n URL:" + g_url);
        }

        initUIWidgets();

        debug_path = getExternalCacheDir().getAbsolutePath() + "/debug";
        Utils.createDir(debug_path);

        int ret = Initialize(debug_path);
        if (Constants.NuiResultCode.SUCCESS == ret) {
            initialized = true;
        } else {
            initialized = false;
            Log.e(TAG, "tts init failed");
            final String msg_text = Utils.getMsgWithErrorCode(ret, "init");
            showText(eventView, msg_text);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashSambertTtsActivity.this,
                            msg_text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAudioTrack.stop();
        mAudioTrack.releaseAudioTrack();
        nui_tts_instance.tts_release();
        initialized = false;
    }

    private void getFontList() {
        // 发音人：https://help.aliyun.com/zh/model-studio/sambert-websocket-api
        List<String> Font = Utils.getVoiceList("SambertTts");
        ArrayAdapter<String> spinnerFont = new ArrayAdapter<String>(DashSambertTtsActivity.this, android.R.layout.simple_spinner_dropdown_item, Font);
        mFontSpin.setAdapter(spinnerFont);
        mFontSpin.setSelection(0);
        paramMap.put("fontname", Font);
    }

    private void getSpeedList() {
        List<String> speed = new ArrayList<>();
        speed.add("0.5");
        speed.add("0.8");
        speed.add("1.0");
        speed.add("1.5");
        speed.add("2.0");
        ArrayAdapter<String> spinnerSpeed = new ArrayAdapter<String>(DashSambertTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, speed);
        mSpeedSpin.setAdapter(spinnerSpeed);
        mSpeedSpin.setSelection(2);
        paramMap.put("speed_level", speed);
    }

    private void getPitchList() {
        List<String> pitch = new ArrayList<>();
        pitch.add("0.5");
        pitch.add("0.8");
        pitch.add("1.0");
        pitch.add("1.5");
        pitch.add("2.0");
        ArrayAdapter<String> spinnerPitch = new ArrayAdapter<String>(DashSambertTtsActivity.this, android.R.layout.simple_spinner_dropdown_item, pitch);
        mPitchSpin.setAdapter(spinnerPitch);
        mPitchSpin.setSelection(2);
        paramMap.put("pitch_level", pitch);
    }

    private void getVolumeList() {
        List<String> volume = new ArrayList<>();
        volume.add("10");
        volume.add("30");
        volume.add("50");
        volume.add("70");
        volume.add("80");
        volume.add("100");
        ArrayAdapter<String> spinnerVolume = new ArrayAdapter<String>(DashSambertTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, volume);
        mVolumeSpin.setAdapter(spinnerVolume);
        mVolumeSpin.setSelection(2);
        paramMap.put("volume", volume);
    }

    private void getFormatList() {
        List<String> format = new ArrayList<>();
        format.add("mp3");
        format.add("pcm");
        format.add("wav");
        ArrayAdapter<String> spinnerFormat = new ArrayAdapter<String>(
                DashSambertTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, format);
        mFormatSpin.setAdapter(spinnerFormat);
        mFormatSpin.setSelection(0);
        paramMap.put("format", format);
    }

    private void initUIWidgets() {
        ttsEditView = (EditText) findViewById(R.id.tts_content);
        ttsStartBtn = (Button)findViewById(R.id.tts_start_btn);
        ttsCancelBtn = (Button)findViewById(R.id.tts_cancel_btn);
        ttsPauseBtn = (Button)findViewById(R.id.tts_pause_btn);
        ttsResumeBtn = (Button)findViewById(R.id.tts_resume_btn);
        ttsQuitBtn = (Button)findViewById(R.id.tts_quit_btn);
        ttsClearTextBtn = (Button)findViewById(R.id.tts_clear_btn);

        eventView = (TextView) findViewById(R.id.textView13);
        eventView.setEnabled(false);

        mSaveAudioSwitch = (Switch) findViewById(R.id.save_audio_switch3);
        mSaveAudioSwitch.setVisibility(View.VISIBLE);

        mPitchSpin = (Spinner) findViewById(R.id.tts_set_pitch_spin);
        mPitchSpin.setOnItemSelectedListener(this);
        mFontSpin = (Spinner) findViewById(R.id.tts_set_font_spin);
        mFontSpin.setOnItemSelectedListener(this);
        mSpeedSpin = (Spinner) findViewById(R.id.tts_set_speed_spin);
        mSpeedSpin.setOnItemSelectedListener(this);
        mVolumeSpin = (Spinner) findViewById(R.id.tts_set_volume_spin);
        mVolumeSpin.setOnItemSelectedListener(this);
        mFormatSpin = (Spinner) findViewById(R.id.tts_set_format_spin);
        mFormatSpin.setOnItemSelectedListener(this);

        getFontList();
        getSpeedList();
        getPitchList();
        getVolumeList();
        getFormatList();

        ttsEditView.setText(CN_PREVIEW);
        ttsStartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ttsText = ttsEditView.getText().toString();
                if (TextUtils.isEmpty(ttsText)) {
                    Log.e(TAG, "tts empty");
                    return;
                }
                if (!initialized) {
                    Log.i(TAG, "init tts");
                    Initialize(debug_path);
                }

                Log.i(TAG, "start play tts");

                startTimestamp = System.currentTimeMillis();
                firstDataFlag = true;
                Log.i(TAG, "startTimestamp" + startTimestamp);

                // 注意！！！不要在端侧使用长效API Key！！！
                // 注意！！！不要在端侧使用长效API Key！！！
                // 注意！！！不要在端侧使用长效API Key！！！
                // 将长效API Key硬编码在端侧代码中，会导致安全风险！！！
                // 请在自建服务端获得临时鉴权Token（有效期60s，最长可设置1800s），再下发到端侧进行使用。
                // 临时鉴权Token: https://help.aliyun.com/zh/model-studio/obtain-temporary-authentication-token
                //
                // 服务只需要在临时Token(API Key)快过期前刷新一次。各端侧在Token(API Key)快过期前从服务获得新的
                // 临时Token(API Key)。
                nui_tts_instance.setparamTts("apikey", g_apikey);

                // 每个instance一个task，若想同时处理多个task，请启动多instance
                int ret = nui_tts_instance.startTts("1", "", ttsText);
                if (Constants.NuiResultCode.SUCCESS != ret) {
                    String error_msg = nui_tts_instance.getparamTts("error_msg");
                    showText(eventView, "error_code:" + ret + " errmsg:" + error_msg);
                    final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
                    ToastText(msg_text);
                }
            }
        });
        ttsQuitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts release");
                mAudioTrack.stop();
                nui_tts_instance.tts_release();
                initialized = false;
            }
        });
        ttsCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "cancel tts");
                nui_tts_instance.cancelTts("");
                mAudioTrack.stop();
            }
        });
        ttsPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "pause tts");
                nui_tts_instance.pauseTts();
                mAudioTrack.pause();
            }
        });
        ttsResumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "resume tts");
                nui_tts_instance.resumeTts();
                mAudioTrack.play();
            }
        });
        ttsClearTextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ttsEditView.setText("");
            }
        });
    }

    @Override
    public void onClick(View v) {
    }

    private int Initialize(String debug_path) {
        int ret = nui_tts_instance.tts_initialize(new INativeTtsCallback() {
            @Override
            public void onTtsEventCallback(INativeTtsCallback.TtsEvent event, String task_id, int ret_code) {
                Log.i(TAG, "tts event:" + event + " task id " + task_id + " ret " + ret_code);
                // 请妥善保存好task_id、错误码ret_code和错误信息，用于定位问题。
                // 错误信息可通过nui_tts_instance.getparamTts("error_msg")获得。
                if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_START) {
                    startedTimestamp = System.currentTimeMillis();
                    Log.i(TAG, "startedTimestamp" + startedTimestamp);
                    curTaskId = task_id;
                    if (mSaveAudioSwitch.isChecked()) {
                        try {
                            if (mSynthesisAudioFile != null) {
                                mSynthesisAudioFile.close();
                                mSynthesisAudioFile = null;
                            }
                            if (mSynthesisAudioFile == null && !curTaskId.isEmpty()) {
                                try {
                                    mSynthesisAudioFilePath = debug_path + "/" + "tts_task_id_" + curTaskId + "." + mEncodeType;
                                    Log.i(TAG, "save tts data into " + mSynthesisAudioFilePath);
                                    mSynthesisAudioFile = new FileOutputStream(mSynthesisAudioFilePath, true);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    mAudioTrack.play();
                    Log.i(TAG, "start play");
                } else if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_END ||
                        event == TtsEvent.TTS_EVENT_CANCEL ||
                        event == TtsEvent.TTS_EVENT_ERROR) {
                    /*
                     * 提示: TTS_EVENT_END事件表示TTS已经合成完并通过回调传回了所有音频数据, 而不是表示播放器已经播放完了所有音频数据。
                     */
                    Log.i(TAG, "play end");

                    // 表示推送完数据, 当播放器播放结束则会有playOver回调
                    mAudioTrack.isFinishSend(true);

                    // 调试使用, 若希望存下音频文件, 如下
                    try {
                        if (mSynthesisAudioFile != null) {
                            mSynthesisAudioFile.close();
                            mSynthesisAudioFile = null;
                            if (mEncodeType.equals("wav")) {
                                Utils.fixWavHeader(mSynthesisAudioFilePath);
                            }
                            String show = "存储TTS音频到 " + mSynthesisAudioFilePath;
                            Log.i(TAG, show);
                            showText(eventView, show);
                            ToastText(show);
                        }
                        curTaskId = "";
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (event == TtsEvent.TTS_EVENT_ERROR) {
                        String error_msg = nui_tts_instance.getparamTts("error_msg");
                        Log.e(TAG, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                        showText(eventView, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                        ToastText(Utils.getMsgWithErrorCode(ret_code, "error"));
                        ToastText("错误码:" + ret_code + " 错误信息:" + error_msg);
                    } else {
                        long startedLatency = startedTimestamp - startTimestamp;
                        long firstLatency = firstDataTimestamp - startedTimestamp;
                        showText(eventView, "建连:" + startedLatency + "ms+首包:" + firstLatency + "=" + (startedLatency + firstLatency) + "ms.");
                    }
                } else if (event == TtsEvent.TTS_EVENT_PAUSE) {
                    mAudioTrack.pause();
                    Log.i(TAG, "play pause");
                } else if (event == TtsEvent.TTS_EVENT_RESUME) {
                    mAudioTrack.play();
                }
            }
            @Override
            public void onTtsDataCallback(String info, int info_len, byte[] data) {
                if (info.length() > 0) {
                     Log.i(TAG, "info: " + info);
                }
                if (data.length > 0) {
                    if (firstDataFlag) {
                        firstDataFlag = false;
                        firstDataTimestamp = System.currentTimeMillis();
                        Log.i(TAG, "firstDataTimestamp" + firstDataTimestamp);
                    }
                    mAudioTrack.setAudioData(data);
//                    Log.d(TAG, "write:" + data.length);
                    try {
                        if (mSynthesisAudioFile != null) {
                            mSynthesisAudioFile.write(data);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            @Override
            public void onTtsVolCallback(int vol) {
                // 此处TTS音量并不是和播放数据实时对应
//                Log.i(TAG, "tts vol " + vol);
            }

            @Override
            public void onTtsLogTrackCallback(Constants.LogLevel level, String log) {
//                Log.i(TAG, "onTtsLogTrackCallback cloud log level:" + level + ", message -> " + log);
            }
        }, genTicket(debug_path), Constants.LogLevel.LOG_LEVEL_DEBUG, true);

        if (Constants.NuiResultCode.SUCCESS != ret) {
            Log.e(TAG, "tts create failed");
            return ret;
        }

        // Sambert语音合成发音人可以参考阿里云官网:
        // https://help.aliyun.com/zh/model-studio/sambert-websocket-api
        nui_tts_instance.setparamTts("model", mFontName);

        // 音频编码格式，支持pcm、wav和mp3格式。
        nui_tts_instance.setparamTts("format", mFormatSpin.getSelectedItem().toString());
        if (mFormatSpin.getSelectedItem().toString().equals("mp3")) {
            // 若设置mp3格式, 可enable_audio_decoder打开内部解码器, 将mp3编码成pcm
            nui_tts_instance.setparamTts("enable_audio_decoder", "1");
        }

        // 合成音频的采样率（单位：Hz）。
        // 建议使用模型默认采样率（参见模型列表），如果不匹配，服务会进行必要的升降采样处理。
        nui_tts_instance.setparamTts("sample_rate", mSampleRate);
        // 模型采样率设置16K，则播放器也得设置成相同采样率16K.
        mAudioTrack.setSampleRate(Integer.parseInt(mSampleRate));

        // 音量，取值范围：0～100。默认值：50。
        nui_tts_instance.setparamTts("volume", mVolumeSpin.getSelectedItem().toString());

        // 语速, 语速倍速区间为[0.5, 1.0, 2.0], 默认1.0
        // 0.5：表示默认语速的0.5倍速。
        // 1：表示默认语速。默认语速是指模型默认输出的合成语速，语速会依据每个发音人略有不同，约每秒钟4个字。
        // 2：表示默认语速的2倍速。
        nui_tts_instance.setparamTts("rate", mSpeedSpin.getSelectedItem().toString());

        // 合成音频的语调，取值范围：0.5~2。
        // 默认值：1.0。
        nui_tts_instance.setparamTts("pitch", mPitchSpin.getSelectedItem().toString());

        // 字级别音素边界功能开关。“1”表示打开，“0”表示关闭。默认关闭。
//        nui_tts_instance.setparamTts("word_timestamp_enabled", "1");

        // 是否在开启字级别时间戳的基础上，显示音素级别时间戳。
        // 和word_timestamp_enabled搭配使用，“1”表示打开，“0”表示关闭。默认关闭。
//        nui_tts_instance.setparamTts("phoneme_timestamp_enabled", "1");

        // 设置文档中不存在的参数, key为custom_params, value以json string的形式设置参数
//        nui_tts_instance.setparamTts("custom_params", "{\"enable_phoneme_timestamp\":true}");

        // 打开音量回调onNuiTtsVolumeCallback。
        // 注意！此音频是SDK刚收到合成数据的音量值，而非正在播放的音量值。
        // 正在播放音频的音量值可参考AudioPlayer.java中的calculateRMSLevel
//        nui_tts_instance.setparamTts("enable_callback_vol", "1");

        // 设置dns超时时间, 当DNS解析超时时返回错误事件
//        nui_tts_instance.setparamTts("dns_timeout", "500");

        return ret;
    }

    private String genTicket(String debugpath) {
        String str = "";
        try {
            //获取账号访问凭证：
            JSONObject object = new JSONObject();

            // 注意！不推荐在这里设置长效apikey。推荐每次设置临时鉴权token。
            // 注意！不推荐在这里设置长效apikey。推荐每次设置临时鉴权token。
            // 注意！不推荐在这里设置长效apikey。推荐每次设置临时鉴权token。
//            object.put("apikey", g_apikey);

            if (g_url.isEmpty()) {
                g_url = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
            }
            object.put("url", g_url);

            object.put("device_id", "empty_device_id"); // 必填, 推荐填入具有唯一性的id, 方便定位问题

            //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存中间音频文件。
            object.put("debug_path", debugpath);
            //过滤SDK内部日志通过回调送回到用户层
            object.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_INFO)));

            // 设置为在线合成
            //  Local = 0,
            //  Mix = 1,  // init local and cloud
            //  Cloud = 2,
            object.put("mode_type", Constants.TtsModeTypeCloud); // 必填
            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 注意! str中包含ak_id ak_secret token app_key等敏感信息, 实际产品中请勿在Log中输出这类信息！
        Log.i(TAG, "UserContext:" + str);
        return str;
    }

    @Override
    public void onItemSelected(AdapterView<?> view, View arg1, int arg2, long arg3) {
        if (view == mFontSpin) {
            nui_tts_instance.cancelTts("");
            mAudioTrack.stop();

            String font_name = mFontSpin.getSelectedItem().toString();
            String[] parts = font_name.split(";");
            if (parts.length > 1) {
                mFontName = parts[0];
                if (parts.length > 4) {
                    mSampleRate = parts[4];
                }
            } else {
                mFontName = font_name;
            }
            nui_tts_instance.setparamTts("model", mFontName);
        } else if (view == mSpeedSpin) {
            // [0.5, 2], 默认1.0. 低于0.5则重置为0.5, 高于2则重置为2
            // 语速, 值越大语速越快.
            // 0.5可理解为正常语速的0.5倍速
            // 2 可理解为正常语速的2倍速
            nui_tts_instance.setparamTts("rate", mSpeedSpin.getSelectedItem().toString());
        } else if (view == mPitchSpin) {
            // 声调，值越大声音越尖锐。
            nui_tts_instance.setparamTts("pitch", mPitchSpin.getSelectedItem().toString());
        } else if (view == mVolumeSpin) {
            // 音量，取值范围：0～100。默认值：50。
            String volume = mVolumeSpin.getSelectedItem().toString();
            nui_tts_instance.setparamTts("volume", volume);
        } else if (view == mFormatSpin) {
            mEncodeType = mFormatSpin.getSelectedItem().toString();
            nui_tts_instance.cancelTts("");
            mAudioTrack.stop();
            nui_tts_instance.setparamTts("format", mEncodeType);
            if (mEncodeType.equals("mp3")) {
                // 若设置mp3格式, 可enable_audio_decoder打开内部解码器, 将mp3编码成pcm
                nui_tts_instance.setparamTts("enable_audio_decoder", "1");
            } else {
                nui_tts_instance.setparamTts("enable_audio_decoder", "0");
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    private void ToastText(String text) {
        final String str = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DashSambertTtsActivity.this, str, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showText(final TextView who, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (TextUtils.isEmpty(text)) {
                    who.setText("");
                } else {
                    who.setText(text);
                }
            }
        });
    }
}
