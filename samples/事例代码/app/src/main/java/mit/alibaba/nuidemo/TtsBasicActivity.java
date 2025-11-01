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
// Android SDK 详细说明：https://help.aliyun.com/document_detail/174481.html
public class TtsBasicActivity extends Activity implements View.OnClickListener, OnItemSelectedListener {
    private static final String TAG = "TtsBasicActivity";

    private String g_appkey = "";
    private String g_token = "";
    private String g_sts_token = "";
    private String g_ak = "";
    private String g_sk = "";
    private String g_url = "";

    NativeNui nui_tts_instance = new NativeNui(Constants.ModeType.MODE_TTS);
    final static String CN_PREVIEW ="语音合成服务，通过先进的深度学习技术，将文本转换成自然流畅的语音。" +
            "目前有多种音色可供选择，并提供调节语速、语调、音量等功能。" +
            "适用于智能客服、语音交互、文学有声阅读和无障碍播报等场景。";
    // 以下为552字符长度的示例，用于测试长文本语音合成
//    final static String CN_PREVIEW ="近年来，随着端到端语音识别的流行，" +
//            "基于Transformer结构的语音识别系统逐渐成为了主流。" +
//            "然而，由于Transformer是一种自回归模型，需要逐个生成目标文字，计算复杂度随着目标文字数量线性增加，" +
//            "限制了其在工业生产中的应用。针对Transoformer模型自回归生成文字的低计算效率缺陷，" +
//            "学术界提出了非自回归模型来并行的输出目标文字。根据生成目标文字时，迭代轮数，非自回归模型分为：" +
//            "多轮迭代式与单轮迭代非自回归模型。其中实用的是基于单轮迭代的非自回归模型。对于单轮非自回归模型，" +
//            "现有工作往往聚焦于如何更加准确的预测目标文字个数，如CTC-enhanced采用CTC预测输出文字个数，" +
//            "尽管如此，考虑到现实应用中，语速、口音、静音以及噪声等因素的影响，" +
//            "如何准确的预测目标文字个数以及抽取目标文字对应的声学隐变量仍然是一个比较大的挑战；" +
//            "另外一方面，我们通过对比自回归模型与单轮非自回归模型在工业大数据上的错误类型" +
//            "（如下图所示，AR与vanilla NAR），发现，相比于自回归模型，非自回归模型，" +
//            "在预测目标文字个数方面差距较小，但是替换错误显著的增加，" +
//            "我们认为这是由于单轮非自回归模型中条件独立假设导致的语义信息丢失。" +
//            "于此同时，目前非自回归模型主要停留在学术验证阶段，还没有工业大数据上的相关实验与结论。";

    private final Map<String, List<String>> paramMap = new HashMap<>();
    private Button ttsStartBtn, ttsQuitBtn, ttsCancelBtn, ttsPauseBtn, ttsResumeBtn, ttsClearTextBtn;
    private Switch mSaveAudioSwitch;
    private Spinner mPitchSpin, mFontSpin, mSpeedSpin, mVolumeSpin, mFormatSpin;
    private EditText ttsEditView;
    private TextView eventView;

    private String asset_path;
    private String debug_path;
    private String mFontName = "aiqi";
    private String mEncodeType = "mp3"; // AudioPlayer中AudioTrack只能播放PCM格式，其他音频格式请另外编写播放器代码
    boolean initialized = false;
    private String curTaskId = "";
    private String mSynthesisAudioFilePath = "";
    private OutputStream mSynthesisAudioFile = null;

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
        setContentView(R.layout.activity_tts_basic);

        String version = nui_tts_instance.GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TtsBasicActivity.this, version_text, Toast.LENGTH_SHORT).show();
            }
        });

        // 获取传递的参数
        Intent intent = getIntent();
        if (intent != null) {
            g_appkey = intent.getStringExtra("appkey");
            g_token = intent.getStringExtra("token");
            g_sts_token = intent.getStringExtra("stsToken");
            g_ak = intent.getStringExtra("accessKey");
            g_sk = intent.getStringExtra("accessKeySecret");
            g_url = intent.getStringExtra("url");

            Log.i(TAG, "Get access ->\n Appkey:" + g_appkey + "\n Token:" + g_token
                    + "\n AccessKey:" + g_ak + "\n AccessKeySecret:" + g_sk
                    + "\n STS_Token:" + g_sts_token
                    + "\n URL:" + g_url);
        }

        initUIWidgets();

        //获取工作路径, 即获得拷贝后资源文件存储的cache路径, 作为workspace
        // 注意: V2.6.2版本开始纯云端功能可不需要资源文件
//        asset_path = CommonUtils.getModelPath(this);
//        Log.i(TAG, "use workspace " + asset_path);

        //这里主动调用完成SDK配置文件的拷贝, 即将nuisdk.aar中assets中资源文件拷贝到cache目录
        // 注意: V2.6.2版本开始纯云端功能可不需要资源文件
//        if (CommonUtils.copyAssetsData(this)) {
//            Log.i(TAG, "copy assets data done");
//        } else {
//            Log.i(TAG, "copy assets failed");
//            return;
//        }

        debug_path = getExternalCacheDir().getAbsolutePath() + "/debug";
        Utils.createDir(debug_path);

        int ret = Initialize(asset_path, debug_path);
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
                    Toast.makeText(TtsBasicActivity.this,
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
        // 发音人：https://help.aliyun.com/zh/isi/developer-reference/overview-5
        List<String> Font = Utils.getVoiceList("NlsTts");
        ArrayAdapter<String> spinnerFont = new ArrayAdapter<String>(TtsBasicActivity.this, android.R.layout.simple_spinner_dropdown_item, Font);
        mFontSpin.setAdapter(spinnerFont);
        mFontSpin.setSelection(0);
        paramMap.put("fontname", Font);
    }

    private void getSpeedList() {
        List<String> speed = new ArrayList<>();
        speed.add("0.6");
        speed.add("1.0");
        speed.add("2.0");
        ArrayAdapter<String> spinnerSpeed = new ArrayAdapter<String>(TtsBasicActivity.this,
                android.R.layout.simple_spinner_dropdown_item, speed);
        mSpeedSpin.setAdapter(spinnerSpeed);
        mSpeedSpin.setSelection(1);
        paramMap.put("speed_level", speed);
    }

    private void getPitchList() {
        List<String> pitch = new ArrayList<>();
        pitch.add("-500");
        pitch.add("0");
        pitch.add("500");
        ArrayAdapter<String> spinnerPitch = new ArrayAdapter<String>(TtsBasicActivity.this, android.R.layout.simple_spinner_dropdown_item, pitch);
        mPitchSpin.setAdapter(spinnerPitch);
        mPitchSpin.setSelection(1);
        paramMap.put("pitch_level", pitch);
    }

    private void getVolumeList() {
        List<String> volume = new ArrayList<>();
        volume.add("0.6");
        volume.add("1.0");
        volume.add("2.0");
        ArrayAdapter<String> spinnerVolume = new ArrayAdapter<String>(TtsBasicActivity.this,
                android.R.layout.simple_spinner_dropdown_item, volume);
        mVolumeSpin.setAdapter(spinnerVolume);
        mVolumeSpin.setSelection(1);
        paramMap.put("volume", volume);
    }

    private void getFormatList() {
        List<String> format = new ArrayList<>();
        format.add("mp3");
        format.add("pcm");
        format.add("wav");
        ArrayAdapter<String> spinnerFormat = new ArrayAdapter<String>(
                TtsBasicActivity.this,
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
                    Initialize(asset_path, debug_path);
                }

                Log.i(TAG, "start play tts");
                // 支持一次性合成300字符以内的文字，其中1个汉字、1个英文字母或1个标点均算作1个字符，
                // 超过300个字符的内容将会截断。所以请确保传入的text小于300字符(不包含ssml格式)。
                // 长短文本语音合成收费不同，须另外开通长文本语音服务，请注意。
                // 不需要长文本语音合成功能则无需考虑以下操作。
                int charNum = nui_tts_instance.getUtf8CharsNum(ttsText);
                Log.i(TAG, "chars:" + charNum + " of text:" + ttsText);
                if (charNum > 300) {
                    Log.w(TAG, "text exceed 300 chars.");
                    // 超过300字符设置成 长文本语音合成 模式
                    nui_tts_instance.setparamTts("tts_version", "1");
                    showText(eventView, "共" + charNum + "字符，使用长文本语音合成模式");
                } else {
                    // 未超过300字符设置成 短文本语音合成 模式
                    nui_tts_instance.setparamTts("tts_version", "0");
                    showText(eventView, "共" + charNum + "字符，使用短文本语音合成模式");
                }

                if (mEncodeType.equals("mp3")) {
                    // 若设置mp3/opus格式, 可enable_audio_decoder打开内部解码器, 将mp3/opus编码成pcm
                    nui_tts_instance.setparamTts("enable_audio_decoder", "1");
                } else {
                    nui_tts_instance.setparamTts("enable_audio_decoder", "0");
                }

                // 判断token是否接近过期，刷新token
                JSONObject new_param = new JSONObject();
                long distance_expire_time_5m = 300;
                new_param = Auth.refreshTokenIfNeed(new_param, distance_expire_time_5m);
                if (new_param.containsKey("app_key") &&
                        !new_param.getString("app_key").isEmpty()) {
                    String app_key = new_param.getString("app_key");
                    nui_tts_instance.setparamTts("app_key", app_key);
                }
                if (new_param.containsKey("token") && !new_param.getString("token").isEmpty()) {
                    String token = new_param.getString("token");
                    nui_tts_instance.setparamTts("token", token);
                }

                //如果有HttpDns则可进行设置
//                nui_tts_instance.setparamTts("host", "1.1.1.1");
//                nui_tts_instance.setparamTts("direct_host", "true");

                // 每个instance一个task，若想同时处理多个task，请启动多instance
                int ret = nui_tts_instance.startTts("1", "", ttsText);
                if (Constants.NuiResultCode.SUCCESS != ret) {
                    String error_msg = nui_tts_instance.getparamTts("error_msg");
                    showText(eventView, "error_code:" + ret + " errmsg:" + error_msg);
                    final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
                    ToastText(msg_text);
                } else {
//                    if (mEncodeType.equals("mp3")) {
//                        showText(eventView, "当前DEMO无法播放MP3, 需用户在实际产品中自行实现。仅打开<音频保存>存下MP3文件。");
//                    }
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

    private int Initialize(String path, String debug_path) {
        int ret = nui_tts_instance.tts_initialize(new INativeTtsCallback() {
            @Override
            public void onTtsEventCallback(INativeTtsCallback.TtsEvent event, String task_id, int ret_code) {
                Log.i(TAG, "tts event:" + event + " task id " + task_id + " ret " + ret_code);
                // 请妥善保存好task_id、错误码ret_code和错误信息，用于定位问题。
                // 错误信息可通过nui_tts_instance.getparamTts("error_msg")获得。
                if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_START) {
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
                    if (mEncodeType.equals("pcm") || mEncodeType.equals("wav") ||
                            mEncodeType.equals("mp3")) {
                        mAudioTrack.setAudioData(data);
                    }
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
                Log.i(TAG, "onTtsLogTrackCallback cloud log level:" + level + ", message -> " + log);
            }
        }, genTicket(path, debug_path), Constants.LogLevel.LOG_LEVEL_VERBOSE, true);

        if (Constants.NuiResultCode.SUCCESS != ret) {
            Log.e(TAG, "tts create failed");
            return ret;
        }

        // 在线语音合成发音人可以参考阿里云官网
        // https://help.aliyun.com/document_detail/84435.html
        nui_tts_instance.setparamTts("font_name", mFontName);

        // 详细参数可见: https://help.aliyun.com/document_detail/173642.html
        nui_tts_instance.setparamTts("sample_rate", "16000");
        // 模型采样率设置16K，则播放器也得设置成相同采样率16K.
        mAudioTrack.setSampleRate(16000);

        // 字级别音素边界功能开关，该参数只对支持字级别音素边界接口的发音人有效。“1”表示打开，“0”表示关闭。
        nui_tts_instance.setparamTts("enable_subtitle", "1");

        // 设置文档中不存在的参数, key为custom_params, value以json string的形式设置参数
//        nui_tts_instance.setparamTts("custom_params", "{\"enable_phoneme_timestamp\":true}");

        // 打开音量回调onNuiTtsVolumeCallback。
        // 注意！此音频是SDK刚收到合成数据的音量值，而非正在播放的音量值。
        // 正在播放音频的音量值可参考AudioPlayer.java中的calculateRMSLevel
//        nui_tts_instance.setparamTts("enable_callback_vol", "1");

        // 调整语速, 语速倍速区间为[0.5, 1.0, 2.0]
//        nui_tts_instance.setparamTts("speed_level", "1");
        // 调整音调, [-500,500]，默认0
//        nui_tts_instance.setparamTts("pitch_level", "0");
        // 调整音量, (0，2]，默认1.0
//        nui_tts_instance.setparamTts("volume", "1.0");

        // 设置dns超时时间, 当DNS解析超时时返回错误事件
//        nui_tts_instance.setparamTts("dns_timeout", "500");

        return ret;
    }

    private String genTicket(String workpath, String debugpath) {
        String str = "";
        try {
            //获取账号访问凭证：
            Auth.GetTicketMethod method = Auth.GetTicketMethod.GET_TOKEN_FROM_SERVER_FOR_ONLINE_FEATURES;
            if (!g_appkey.isEmpty()) {
                Auth.setAppKey(g_appkey);
            }
            if (!g_token.isEmpty()) {
                Auth.setToken(g_token);
            }
            if (!g_ak.isEmpty()) {
                Auth.setAccessKey(g_ak);
            }
            if (!g_sk.isEmpty()) {
                Auth.setAccessKeySecret(g_sk);
            }
            Auth.setStsToken(g_sts_token);
            // 此处展示将用户传入账号信息进行交互，实际产品不可以将任何账号信息存储在端侧
            if (!g_appkey.isEmpty()) {
                if (!g_ak.isEmpty() && !g_sk.isEmpty()) {
                    if (g_sts_token.isEmpty()) {
                        method = Auth.GetTicketMethod.GET_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES;
                    } else {
                        method = Auth.GetTicketMethod.GET_STS_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES;
                    }
                }
                if (!g_token.isEmpty()) {
                    method = Auth.GetTicketMethod.GET_TOKEN_IN_CLIENT_FOR_ONLINE_FEATURES;
                }
            }
            Log.i(TAG, "Use method:" + method);
            JSONObject object = Auth.getTicket(method);
            if (!object.containsKey("token") && !object.containsKey("sts_token")) {
                Log.e(TAG, "Cannot get token or sts_token!!!");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(TtsBasicActivity.this,
                                "未获得有效临时凭证！", Toast.LENGTH_LONG).show();
                    }
                });
            }

            object.put("device_id", "empty_device_id"); // 必填, 推荐填入具有唯一性的id, 方便定位问题

            if (g_url.isEmpty()) {
                g_url = "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1"; // 默认
            }
            object.put("url", g_url);

            //工作目录路径，SDK从该路径读取配置文件
//            object.put("workspace", workpath); // V2.6.2版本开始纯云端功能可不设置workspace

            //当初始化SDK时的save_log参数取值为true时，该参数生效。表示是否保存音频debug，该数据保存在debug目录中，需要确保debug_path有效可写。
            object.put("save_wav", "true");
            //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存中间音频文件。
            object.put("debug_path", debugpath);
            //过滤SDK内部日志通过回调送回到用户层
            object.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_NONE)));

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
            String[] parts = font_name.split("-");
            if (parts.length > 1) {
                mFontName = parts[0];
            } else {
                mFontName = font_name;
            }
            nui_tts_instance.setparamTts("font_name", mFontName);
        } else if (view == mSpeedSpin) {
            // [0.5, 2], 默认1.0. 低于0.5则重置为0.5, 高于2则重置为2
            // 语速, 值越大语速越快.
            // 0.5可理解为正常语速的0.5倍速
            // 2 可理解为正常语速的2倍速
            nui_tts_instance.setparamTts("speed_level", mSpeedSpin.getSelectedItem().toString());
        } else if (view == mPitchSpin) {
            // 声调，值越大声音越尖锐。
            nui_tts_instance.setparamTts("pitch_level", mPitchSpin.getSelectedItem().toString());
        } else if (view == mVolumeSpin) {
            // (0，2]，默认1.0
            // 音量，值越大音量越大。
            // 为避免截幅引入“吱吱”噪声，建议取值限定在1.5以下。
            String volume = mVolumeSpin.getSelectedItem().toString();
            nui_tts_instance.setparamTts("volume", volume);
        } else if (view == mFormatSpin) {
            mEncodeType = mFormatSpin.getSelectedItem().toString();
            nui_tts_instance.cancelTts("");
            mAudioTrack.stop();
            nui_tts_instance.setparamTts("encode_type", mEncodeType);
            if (mEncodeType.equals("mp3")) {
                // 若设置mp3/opus格式, 可enable_audio_decoder打开内部解码器, 将mp3/opus编码成pcm
                nui_tts_instance.setparamTts("enable_audio_decoder", "1");

//                showText(eventView, "当前DEMO无法播放MP3, 需用户在实际产品中自行实现。仅打开<音频保存>存下MP3文件。");
//                ToastText("当前DEMO无法播放MP3, 需用户在实际产品中自行实现。仅打开<音频保存>存下MP3文件。");
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
                Toast.makeText(TtsBasicActivity.this, str, Toast.LENGTH_LONG).show();
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
