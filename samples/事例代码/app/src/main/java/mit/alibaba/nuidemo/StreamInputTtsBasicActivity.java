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

import android.util.Log;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeStreamInputTtsCallback;
import com.alibaba.idst.nui.NativeNui;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 本样例展示在线语音合成（流式输入流式输出）使用方法
// Android SDK 详细说明：https://help.aliyun.com/zh/isi/stream-input-tts-sdk-quick-start
public class StreamInputTtsBasicActivity extends Activity implements View.OnClickListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "StreamInputTtsBasicActivity";

    private String g_appkey = "";
    private String g_token = "";
    private String g_sts_token = "";
    private String g_ak = "";
    private String g_sk = "";
    private String g_url = "";

    NativeNui stream_input_tts_instance = new NativeNui(Constants.ModeType.MODE_STREAM_INPUT_TTS);
    final static String CN_TEXT = "唧唧复唧唧，木兰当户织。不闻机杼声，唯闻女叹息。\n" +
            "问女何所思，问女何所忆。女亦无所思，女亦无所忆。\n" +
            "昨夜见军帖，可汗大点兵，军书十二卷，卷卷有爷名。\n" +
            "阿爷无大儿，木兰无长兄，愿为市鞍马，从此替爷征。\n" +
            "东市买骏马，西市买鞍鞯，南市买辔头，北市买长鞭。旦辞爷娘去，暮宿黄河边，不闻爷娘唤女声，但闻黄河流水鸣溅溅。\n" +
            "旦辞黄河去，暮至黑山头，不闻爷娘唤女声，但闻燕山胡骑鸣啾啾。万里赴戎机，关山度若飞。朔气传金柝，寒光照铁衣。将军百战死，壮士十年归。\n" +
            "归来见天子，天子坐明堂。策勋十二转，赏赐百千强。可汗问所欲，木兰不用尚书郎，愿驰千里足，送儿还故乡。爷娘闻女来，出郭相扶将；阿姊闻妹来，当户理红妆；小弟闻姊来，磨刀霍霍向猪羊。\n" +
            "开我东阁门，坐我西阁床。脱我战时袍，著我旧时裳。当窗理云鬓，对镜帖花黄。出门看火伴，火伴皆惊忙：同行十二年，不知木兰是女郎。\n" +
            "雄兔脚扑朔，雌兔眼迷离；双兔傍地走，安能辨我是雄雌？";
    final static String SINGLE_CN_TEXT = "<speak>唧唧复唧唧，木兰当户织。<break time=\"500ms\"/>不闻机杼声，唯闻女叹息。</speak>";

    private final Map<String, List<String>> paramMap = new HashMap<>();
    private Button ttsStartBtn, ttsPlayBtn, ttsStopBtn, ttsCancelBtn, ttsClearTextBtn;
    private Switch mSaveAudioSwitch;
    private Spinner mPitchSpin, mFontSpin, mSpeedSpin, mVolumeSpin, mFormatSpin;
    private EditText ttsEditView;
    private TextView eventView;

    private boolean is_started = false;
    private String debug_path;
    private String mEncodeType = "pcm"; // AudioPlayer中AudioTrack只能播放PCM格式，其他音频格式请另外编写播放器代码
    private String curTaskId = "";
    private String mSynthesisAudioFilePath = "";
    private OutputStream mSynthesisAudioFile = null;
    private boolean is_first_data = true;

    //  AudioPlayer默认采样率是16000
    private final AudioPlayer mAudioTrack = new AudioPlayer(new AudioPlayerCallback() {
        @Override
        public void playStart() {
            Log.i(TAG, "start play");
        }

        @Override
        public void playOver() {
            Log.i(TAG, "play over");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String cur = ttsEditView.getText().toString();
                    if (!cur.isEmpty()) {
                        ttsEditView.setText(CN_TEXT);
                    }
//                    ttsStartBtn.setEnabled(true);
//                    ttsEditView.setEnabled(true);
//                    ttsStopBtn.setEnabled(false);
//                    ttsCancelBtn.setEnabled(false);
                }
            });
        }

        @Override
        public void playSoundLevel(int level) {
        }
    });

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow_tts_basic);

        String version = stream_input_tts_instance.GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamInputTtsBasicActivity.this,
                        version_text, Toast.LENGTH_SHORT).show();
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

        debug_path = getExternalCacheDir().getAbsolutePath() + "/debug";
        Utils.createDir(debug_path);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAudioTrack.stop();
        mAudioTrack.releaseAudioTrack();
        if (is_started) {
            stream_input_tts_instance.stopStreamInputTts();
            is_started = false;
        }
    }

    private void getFontList() {
        // 流式文本语音合成发音人:
        //      https://help.aliyun.com/zh/isi/developer-reference/interface-description
        // 语音合成CosyVoice大模型发音人:
        //      https://help.aliyun.com/zh/isi/developer-reference/natual-tts-product-introduction
        // 语音合成CosyVoice大模型长文本发音人:
        //      https://help.aliyun.com/zh/isi/developer-reference/long-text-to-speech-synthesis-for-cosyvoice-interface-description
        List<String> Font = Utils.getVoiceList("StreamInputTts");
        ArrayAdapter<String> spinnerFont = new ArrayAdapter<String>(
                StreamInputTtsBasicActivity.this,
                android.R.layout.simple_spinner_dropdown_item, Font);
        mFontSpin.setAdapter(spinnerFont);
        mFontSpin.setSelection(1);
        paramMap.put("fontname", Font);
    }

    private void getSpeedList() {
        List<String> speed = new ArrayList<>();
        speed.add("-500");
        speed.add("0");
        speed.add("500");
        ArrayAdapter<String> spinnerSpeed = new ArrayAdapter<String>(
                StreamInputTtsBasicActivity.this,
                android.R.layout.simple_spinner_dropdown_item, speed);
        mSpeedSpin.setAdapter(spinnerSpeed);
        mSpeedSpin.setSelection(1);
        paramMap.put("speech_rate", speed);
    }

    private void getPitchList() {
        List<String> pitch = new ArrayList<>();
        pitch.add("-500");
        pitch.add("0");
        pitch.add("500");
        ArrayAdapter<String> spinnerPitch = new ArrayAdapter<String>(
                StreamInputTtsBasicActivity.this,
                android.R.layout.simple_spinner_dropdown_item, pitch);
        mPitchSpin.setAdapter(spinnerPitch);
        mPitchSpin.setSelection(1);
        paramMap.put("pitch_rate", pitch);
    }

    private void getVolumeList() {
        List<String> volume = new ArrayList<>();
        volume.add("20");
        volume.add("50");
        volume.add("100");
        ArrayAdapter<String> spinnerVolume = new ArrayAdapter<String>(
                StreamInputTtsBasicActivity.this,
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
                StreamInputTtsBasicActivity.this,
                android.R.layout.simple_spinner_dropdown_item, format);
        mFormatSpin.setAdapter(spinnerFormat);
        mFormatSpin.setSelection(0);
        paramMap.put("format", format);
    }

    private void initUIWidgets() {
        ttsEditView = (EditText) findViewById(R.id.tts_content);
        ttsStartBtn = (Button) findViewById(R.id.tts_start_btn);
        ttsPlayBtn = (Button) findViewById(R.id.tts_play_btn);
        ttsStopBtn = (Button) findViewById(R.id.tts_stop_btn);
        ttsCancelBtn = (Button) findViewById(R.id.tts_cancel_btn2);
        ttsClearTextBtn = (Button) findViewById(R.id.tts_clear_btn);
        ttsEditView.setText(CN_TEXT);
        ttsEditView.setEnabled(true);
        ttsStartBtn.setEnabled(true);
        ttsPlayBtn.setEnabled(true);
        ttsStopBtn.setEnabled(true);
        ttsCancelBtn.setEnabled(true);

        eventView = (TextView) findViewById(R.id.textView15);
        eventView.setEnabled(false);

        mSaveAudioSwitch = (Switch) findViewById(R.id.save_audio_switch6);
        mSaveAudioSwitch.setVisibility(View.VISIBLE);

        mPitchSpin = (Spinner) findViewById(R.id.tts_set_pitch_spin);
        mPitchSpin.setOnItemSelectedListener(this);
        mFontSpin = (Spinner) findViewById(R.id.tts_set_font_spin);
        mFontSpin.setOnItemSelectedListener(this);
        mSpeedSpin = (Spinner) findViewById(R.id.tts_set_speed_spin);
        mSpeedSpin.setOnItemSelectedListener(this);
        mVolumeSpin = (Spinner) findViewById(R.id.tts_set_volume_spin);
        mVolumeSpin.setOnItemSelectedListener(this);
        mFormatSpin = (Spinner) findViewById(R.id.tts_set_format_spin3);
        mFormatSpin.setOnItemSelectedListener(this);

        getFontList();
        getSpeedList();
        getPitchList();
        getVolumeList();
        getFormatList();

        ttsStartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!is_started) {
                    Log.i(TAG, "start flow tts");
                    // 每个instance一个session
                    if (Constants.NuiResultCode.SUCCESS == StartTts()) {
                        is_started = true;
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                ttsStartBtn.setEnabled(false);
//                                ttsStopBtn.setEnabled(true);
//                                ttsCancelBtn.setEnabled(true);
//                                ttsEditView.setEnabled(false);
//                            }
//                        });

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (!ttsEditView.getText().toString().isEmpty()) {
                                    String[] sentences = ttsEditView.getText().toString().split("[。？！]");
                                    // 此处模拟大模型流式返回文本结果，并进行流式语音合成
                                    for (String sentence : sentences) {
                                        if (!sentence.trim().isEmpty()) {
                                            stream_input_tts_instance.sendStreamInputTts(sentence);
                                            if (!is_started) {
                                                break;
                                            }
                                            try {
                                                Thread.sleep(500);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                    stream_input_tts_instance.stopStreamInputTts();
                                }
                            }
                        }).start();
                    }
                }
            }
        });
        ttsPlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!is_started) {
                    Log.i(TAG, "play flow tts");
                    // 每个instance一个session
                    int ret = AsyncPlayTts(ttsEditView.getText().toString());
                    if (Constants.NuiResultCode.SUCCESS == ret) {
                        is_started = true;
                    }
                    Log.i(TAG, "play flow tts done:" + ret);
                }
            }
        });
        ttsStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "stop stream input tts");
                is_started = false;
//                stream_input_tts_instance.stopStreamInputTts();
                stream_input_tts_instance.asyncStopStreamInputTts();
//                mAudioTrack.stop();
                showText(eventView, "以上内容已经合成，将会在播放完毕后停止。若想立即停止，可在代码中增加关闭播放器的逻辑。");
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        ttsStartBtn.setEnabled(true);
//                        ttsStopBtn.setEnabled(false);
//                        ttsEditView.setEnabled(true);
//                    }
//                });
            }
        });
        ttsCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "cancel stream input tts");
                is_started = false;
                stream_input_tts_instance.cancelStreamInputTts();
                mAudioTrack.stop();
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        ttsStartBtn.setEnabled(true);
//                        ttsStopBtn.setEnabled(false);
//                        ttsCancelBtn.setEnabled(false);
//                        ttsEditView.setEnabled(true);
//                    }
//                });
            }
        });
        ttsClearTextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ttsEditView.setText("");
            }
        });
    }

    private String genTicket() {
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
                        Toast.makeText(StreamInputTtsBasicActivity.this,
                                "未获得有效临时凭证！", Toast.LENGTH_LONG).show();
                    }
                });
            }

            if (g_url.isEmpty()) {
                g_url = "wss://nls-gateway-cn-beijing.aliyuncs.com/ws/v1"; // 默认
            }
            object.put("url", g_url);

            if (!debug_path.isEmpty()) {
                //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存日志等调试信息
                object.put("debug_path", debug_path);
                //设置本地存储日志文件的最大字节数, 最大将会在本地存储2个设置字节大小的日志文件
                object.put("max_log_file_size", 50 * 1024 * 1024);
            }

            //过滤SDK内部日志通过回调送回到用户层
            object.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_NONE)));

            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "user ticket:" + str);
        return str;
    }

    private String genParameters() {
        String str = "";
        try {
            String font_name = mFontSpin.getSelectedItem().toString();
            String[] parts = font_name.split("-");
            if (parts.length > 1) {
                font_name = parts[0];
            } else {
                font_name = "zhixiaoxia";
            }

            JSONObject object = new JSONObject();
            object.put("enable_subtitle", true);
            object.put("voice", font_name);
            object.put("format", mFormatSpin.getSelectedItem().toString());
            if (mFormatSpin.getSelectedItem().toString().equals("opus") ||
                    mFormatSpin.getSelectedItem().toString().equals("mp3")) {
                // 若设置mp3/opus格式, 可enable_audio_decoder打开内部解码器, 将mp3/opus编码成pcm
                object.put("enable_audio_decoder", true);
            } else {
                object.put("enable_audio_decoder", false);
            }
            object.put("sample_rate", 16000);
            object.put("volume", Integer.parseInt(mVolumeSpin.getSelectedItem().toString()));
            object.put("speech_rate", Integer.parseInt(mSpeedSpin.getSelectedItem().toString()));
            object.put("pitch_rate", Integer.parseInt(mPitchSpin.getSelectedItem().toString()));
            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "user parameters:" + str);
        return str;
    }

    @Override
    public void onClick(View v) {
    }

    private int StartTts() {
        int ret = stream_input_tts_instance.startStreamInputTts(new INativeStreamInputTtsCallback() {
            @Override
            public void onStreamInputTtsEventCallback(INativeStreamInputTtsCallback.StreamInputTtsEvent event, String task_id, String session_id, int ret_code, String error_msg, String timestamp, String all_response) {
                Log.i(TAG, "stream input tts event(" + event + ") session id(" + session_id + ") task id(" + task_id + ") retCode(" + ret_code + ") errMsg(" + error_msg + ")");
                if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SYNTHESIS_STARTED) {
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SYNTHESIS_STARTED");
                    is_first_data = true;
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
                } else if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SENTENCE_SYNTHESIS) {
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SENTENCE_SYNTHESIS:" + timestamp);
                } else if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SYNTHESIS_COMPLETE || event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_TASK_FAILED) {
                    is_started = false;
                    /*
                     * 提示: STREAM_INPUT_TTS_EVENT_SYNTHESIS_COMPLETE事件表示TTS已经合成完并通过回调传回了所有音频数据, 而不是表示播放器已经播放完了所有音频数据。
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

                    if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_TASK_FAILED) {
                        final String show_mesg = "error_code(" + ret_code + ") error_message(" + error_msg + ")";
                        Log.e(TAG, "STREAM_INPUT_TTS_EVENT_TASK_FAILED: " + show_mesg);
                        final String msg_text = Utils.getMsgWithErrorCode(ret_code, error_msg);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(StreamInputTtsBasicActivity.this, msg_text, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SENTENCE_BEGIN) {
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SENTENCE_BEGIN:" + all_response);
                } else if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SENTENCE_END) {
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SENTENCE_END:" + all_response);
                }
            }

            @Override
            public void onStreamInputTtsDataCallback(byte[] data) {
                if (data.length > 0) {
                    if (is_first_data) {
                        is_first_data = false;
                        Log.i(TAG, "Get first audio data.");
                    }
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
            public void onStreamInputTtsLogTrackCallback(Constants.LogLevel level, String log) {
                Log.i(TAG, "onStreamInputTtsLogTrackCallback log level:" + level + ", message -> " + log);
            }
        }, genTicket(), genParameters(), "", Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_VERBOSE), true);

        if (Constants.NuiResultCode.SUCCESS != ret) {
            Log.i(TAG, "start tts failed " + ret);
            is_started = false;
            final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(StreamInputTtsBasicActivity.this, msg_text, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            is_started = true;
        }
        return ret;
    }

    private int AsyncPlayTts(String text) {
        int ret = stream_input_tts_instance.asyncPlayStreamInputTts(new INativeStreamInputTtsCallback() {
            @Override
            public void onStreamInputTtsEventCallback(INativeStreamInputTtsCallback.StreamInputTtsEvent event, String task_id, String session_id, int ret_code, String error_msg, String timestamp, String all_response) {
                Log.i(TAG, "stream input tts event(" + event + ") session id(" + session_id + ") task id(" + task_id + ") retCode(" + ret_code + ") errMsg(" + error_msg + ")");
                if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SYNTHESIS_STARTED) {
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SYNTHESIS_STARTED");
                    is_first_data = true;
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
                } else if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SENTENCE_SYNTHESIS) {
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SENTENCE_SYNTHESIS:" + timestamp);
                } else if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SYNTHESIS_COMPLETE || event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_TASK_FAILED) {
                    is_started = false;
                    /*
                     * 提示: STREAM_INPUT_TTS_EVENT_SYNTHESIS_COMPLETE事件表示TTS已经合成完并通过回调传回了所有音频数据, 而不是表示播放器已经播放完了所有音频数据。
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

                    if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_TASK_FAILED) {
                        final String show_mesg = "error_code(" + ret_code + ") error_message(" + error_msg + ")";
                        Log.e(TAG, "STREAM_INPUT_TTS_EVENT_TASK_FAILED: " + show_mesg);
                        final String msg_text = Utils.getMsgWithErrorCode(ret_code, error_msg);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(StreamInputTtsBasicActivity.this, msg_text, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SENTENCE_BEGIN) {
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SENTENCE_BEGIN:" + all_response);
                } else if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SENTENCE_END) {
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SENTENCE_END:" + all_response);
                }
            }

            @Override
            public void onStreamInputTtsDataCallback(byte[] data) {
                if (data.length > 0) {
                    if (is_first_data) {
                        is_first_data = false;
                        Log.i(TAG, "Get first audio data.");
                    }
                    if (mEncodeType.equals("pcm") || mEncodeType.equals("wav")) {
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
            public void onStreamInputTtsLogTrackCallback(Constants.LogLevel level, String log) {
                Log.i(TAG, "onStreamInputTtsLogTrackCallback log level:" + level + ", message -> " + log);
            }
        }, genTicket(), genParameters(), text, "", Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_VERBOSE), true);

        if (Constants.NuiResultCode.SUCCESS != ret) {
            Log.i(TAG, "play tts failed " + ret);
            is_started = false;
            final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(StreamInputTtsBasicActivity.this, msg_text, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            is_started = true;
        }
        return ret;
    }

    @Override
    public void onItemSelected(AdapterView<?> view, View arg1, int arg2, long arg3) {
        if (view == mFontSpin) {
        } else if (view == mSpeedSpin) {
        } else if (view == mPitchSpin) {
        } else if (view == mVolumeSpin) {
        } else if (view == mFormatSpin) {
            mEncodeType = mFormatSpin.getSelectedItem().toString();
            stream_input_tts_instance.stopStreamInputTts();
            mAudioTrack.stop();
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
                Toast.makeText(StreamInputTtsBasicActivity.this, str, Toast.LENGTH_LONG).show();
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
