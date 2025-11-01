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

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeStreamInputTtsCallback;
import com.alibaba.idst.nui.NativeNui;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 本样例展示在线语音合成CosyVoice（流式输入流式输出）使用方法
// Android SDK 详细说明：
// WebSocket API: https://help.aliyun.com/zh/model-studio/cosyvoice-websocket-api
public class DashCosyVoiceStreamTtsActivity extends Activity implements View.OnClickListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "CosyVoiceStreamTts";

    private String g_apikey = "";
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
    private Spinner mFormatSpin, mModelSpin, mFontSpin;
    private Spinner mPitchSpin, mSpeedSpin, mVolumeSpin, mBitRateSpin;
    private Spinner mSeedSpin, mStyleSpin, mLanguageHintsSpin, mInstructionSpin;
    private EditText ttsEditView;
    private TextView eventView;

    private boolean is_started = false;
    private String debug_path;
    private String mEncodeType = "longanrou"; // AudioPlayer中AudioTrack只能播放PCM格式，其他音频格式请另外编写播放器代码
    private String curTaskId = "";
    private String mSynthesisAudioFilePath = "";
    private OutputStream mSynthesisAudioFile = null;

    private long startTimestamp = 0;
    private long startedTimestamp = 0;
    private long firstDataTimestamp = 0;
    private boolean firstDataFlag = true;

    //  AudioPlayer默认采样率是16000, 需要重设置采样率
    private int SAMPLE_RATE = 24000;
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
                }
            });
        }

        @Override
        public void playSoundLevel(int level) {
        }
    });

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cosyvoice_flow_tts);

        String version = stream_input_tts_instance.GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DashCosyVoiceStreamTtsActivity.this,
                        version_text, Toast.LENGTH_SHORT).show();
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

        mAudioTrack.setSampleRate(SAMPLE_RATE);
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
        stream_input_tts_instance.release();
        stream_input_tts_instance = null;
    }

    private void getModelList() {
        List<String> model = new ArrayList<>();
        model.add("cosyvoice-v2");
        model.add("cosyvoice-v3");
        model.add("cosyvoice-v3-plus");
        model.add("cosyvoice-v1");
        model.add("cosyvoice-v1");
        ArrayAdapter<String> spinnerFormat = new ArrayAdapter<String>(
                DashCosyVoiceStreamTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, model);
        mModelSpin.setAdapter(spinnerFormat);
        mModelSpin.setSelection(0);
        paramMap.put("model", model);
    }

    private void getFontList() {
        // 流式文本语音合成发音人:
        //    V3/V2/V1发音人有差异，且并不是所有发音人均支持SSML，详尽信息请查看说明文档。
        //    部分发音人需要申请开通。
        // 语音合成CosyVoice-V3/V2/V1大模型发音人:
        //    https://help.aliyun.com/zh/model-studio/cosyvoice-websocket-api
        // 复刻的音色:
        //    https://help.aliyun.com/zh/model-studio/cosyvoice-clone-api
        List<String> Font = Utils.getVoiceList(mModelSpin.getSelectedItem().toString());
        ArrayAdapter<String> spinnerFont = new ArrayAdapter<String>(
                DashCosyVoiceStreamTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, Font);
        mFontSpin.setAdapter(spinnerFont);
        mFontSpin.setSelection(0);
        paramMap.put("fontname", Font);
    }

    private void getSpeedList() {
        List<String> speed = new ArrayList<>();
        speed.add("0.5");
        speed.add("1");
        speed.add("1.5");
        speed.add("2");
        ArrayAdapter<String> spinnerSpeed = new ArrayAdapter<String>(
                DashCosyVoiceStreamTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, speed);
        mSpeedSpin.setAdapter(spinnerSpeed);
        mSpeedSpin.setSelection(1);
        paramMap.put("speech_rate", speed);
    }

    private void getPitchList() {
        List<String> pitch = new ArrayList<>();
        pitch.add("0.5");
        pitch.add("1");
        pitch.add("1.5");
        pitch.add("2");
        ArrayAdapter<String> spinnerPitch = new ArrayAdapter<String>(
                DashCosyVoiceStreamTtsActivity.this,
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
                DashCosyVoiceStreamTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, volume);
        mVolumeSpin.setAdapter(spinnerVolume);
        mVolumeSpin.setSelection(1);
        paramMap.put("volume", volume);
    }

    private void getFormatList() {
        List<String> format = new ArrayList<>();
        format.add("mp3");
        format.add("opus");
        format.add("pcm");
        format.add("wav");
        ArrayAdapter<String> spinnerFormat = new ArrayAdapter<String>(
                DashCosyVoiceStreamTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, format);
        mFormatSpin.setAdapter(spinnerFormat);
        mFormatSpin.setSelection(0);
        paramMap.put("format", format);
    }

    private void getBitRateList() {
        List<String> bitRate = new ArrayList<>();
        bitRate.add(" ");
        bitRate.add("6 kbps");
        bitRate.add("60 kbps");
        bitRate.add("120 kbps");
        bitRate.add("240 kbps");
        bitRate.add("360 kbps");
        bitRate.add("510 kbps");
        ArrayAdapter<String> spinnerBitRate = new ArrayAdapter<String>(
                DashCosyVoiceStreamTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, bitRate);
        mBitRateSpin.setAdapter(spinnerBitRate);
        mBitRateSpin.setSelection(0);
        paramMap.put("bit_rate", bitRate);
    }

    private void getSeedList() {
        List<String> seed = new ArrayList<>();
        seed.add(" ");
        seed.add("0");
        seed.add("4096");
        seed.add("8192");
        seed.add("16384");
        seed.add("32768");
        seed.add("65535");
        ArrayAdapter<String> spinnerSeed = new ArrayAdapter<String>(
                DashCosyVoiceStreamTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, seed);
        mSeedSpin.setAdapter(spinnerSeed);
        mSeedSpin.setSelection(0);
        paramMap.put("seed", seed);
    }

    private void getStyleList() {
        List<String> style = new ArrayList<>();
        style.add(" ");
        style.add("0");
        style.add("4096");
        style.add("8192");
        style.add("16384");
        style.add("32768");
        style.add("65535");
        ArrayAdapter<String> spinnerStyle = new ArrayAdapter<String>(
                DashCosyVoiceStreamTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, style);
        mStyleSpin.setAdapter(spinnerStyle);
        mStyleSpin.setSelection(0);
        paramMap.put("style", style);
    }

    private void getLanguageHintsList() {
        List<String> hints = new ArrayList<>();
        hints.add(" ");
        hints.add("zh");
        hints.add("en");
        ArrayAdapter<String> spinnerLanguageHints = new ArrayAdapter<String>(
                DashCosyVoiceStreamTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, hints);
        mLanguageHintsSpin.setAdapter(spinnerLanguageHints);
        mLanguageHintsSpin.setSelection(0);
        paramMap.put("language_hints", hints);
    }

    private void getInstructionList() {
        List<String> instruction = new ArrayList<>();
        instruction.add(" ");
        instruction.add("Neutral");
        instruction.add("Fearful");
        instruction.add("Angry");
        instruction.add("Sad");
        instruction.add("Surprised");
        instruction.add("Happy");
        instruction.add("Disgusted");
        ArrayAdapter<String> spinnerInstruct = new ArrayAdapter<String>(
                DashCosyVoiceStreamTtsActivity.this,
                android.R.layout.simple_spinner_dropdown_item, instruction);
        mInstructionSpin.setAdapter(spinnerInstruct);
        mInstructionSpin.setSelection(0);
        paramMap.put("instruction", instruction);
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
        mModelSpin = (Spinner) findViewById(R.id.tts_set_model_spin);
        mModelSpin.setOnItemSelectedListener(this);

        mBitRateSpin = (Spinner) findViewById(R.id.tts_bit_rate);
        mBitRateSpin.setOnItemSelectedListener(this);
        mBitRateSpin.setVisibility(View.GONE);
        mSeedSpin = (Spinner) findViewById(R.id.tts_seed);
        mSeedSpin.setOnItemSelectedListener(this);
        mSeedSpin.setVisibility(View.GONE);
        mStyleSpin = (Spinner) findViewById(R.id.tts_style);
        mStyleSpin.setOnItemSelectedListener(this);
        mStyleSpin.setVisibility(View.GONE);
        mInstructionSpin = (Spinner) findViewById(R.id.tts_instruction);
        mInstructionSpin.setOnItemSelectedListener(this);
        mInstructionSpin.setVisibility(View.GONE);
        mLanguageHintsSpin = (Spinner) findViewById(R.id.tts_language_hints);
        mLanguageHintsSpin.setOnItemSelectedListener(this);
        mLanguageHintsSpin.setVisibility(View.GONE);

        getModelList();
        getFontList();
        getSpeedList();
        getPitchList();
        getVolumeList();
        getFormatList();

        getBitRateList();
        getSeedList();
        getStyleList();
        getInstructionList();
        getLanguageHintsList();

        if (!mModelSpin.getSelectedItem().toString().contains("cosyvoice-v1") &&
                mFormatSpin.getSelectedItem().toString().equals("opus")) {
            mBitRateSpin.setVisibility(View.VISIBLE);
        }
        if (mModelSpin.getSelectedItem().toString().contains("cosyvoice-v3")) {
            mSeedSpin.setVisibility(View.VISIBLE);
            mStyleSpin.setVisibility(View.VISIBLE);
            mInstructionSpin.setVisibility(View.VISIBLE);
            mLanguageHintsSpin.setVisibility(View.VISIBLE);
        }


        ttsStartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!is_started) {
                    startTimestamp = System.currentTimeMillis();
                    firstDataFlag = true;
                    Log.i(TAG, "start flow tts");
                    // 每个instance一个session
                    if (Constants.NuiResultCode.SUCCESS == StartTts()) {
                        is_started = true;

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (!ttsEditView.getText().toString().isEmpty()) {
                                    String[] sentences = ttsEditView.getText().toString().split("[。？！]");
                                    // 此处模拟大模型流式返回文本结果，并进行流式语音合成
                                    for (String sentence : sentences) {
                                        if (!sentence.trim().isEmpty()) {
                                            // 单次文本长度和多次累计文本长度均有限制，详细请查下以下文档。
                                            // 文本长度限制: https://help.aliyun.com/zh/model-studio/cosyvoice-websocket-api
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
                    startTimestamp = System.currentTimeMillis();
                    firstDataFlag = true;
                    Log.i(TAG, "play flow tts");
                    // 单次文本长度和多次累计文本长度均有限制，详细请查下以下文档。
                    // 文本长度限制: https://help.aliyun.com/zh/model-studio/cosyvoice-websocket-api
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
                int ret = stream_input_tts_instance.asyncStopStreamInputTts();
                Log.i(TAG, "stop stream input tts done with " + ret);
                showText(eventView, "以上内容已经合成，将会在播放完毕后停止。若想立即停止，可在代码中增加关闭播放器的逻辑。");
            }
        });
        ttsCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "cancel stream input tts");
                is_started = false;
                stream_input_tts_instance.cancelStreamInputTts();
                mAudioTrack.stop();
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
            JSONObject object = new JSONObject();

            // 注意！！！不要在端侧使用长效API Key！！！
            // 注意！！！不要在端侧使用长效API Key！！！
            // 注意！！！不要在端侧使用长效API Key！！！
            // 将长效API Key硬编码在端侧代码中，会导致安全风险！！！
            // 请在自建服务端获得临时鉴权Token（有效期60s，最长可设置1800s），再下发到端侧进行使用。
            // 临时鉴权Token: https://help.aliyun.com/zh/model-studio/obtain-temporary-authentication-token
            //
            // 服务只需要在临时Token(API Key)快过期前刷新一次。各端侧在Token(API Key)快过期前从服务获得新的
            // 临时Token(API Key)。
            object.put("apikey", g_apikey);

            object.put("device_id", "empty_device_id"); // 必填, 推荐填入具有唯一性的id, 方便定位问题

            if (g_url.isEmpty()) {
                g_url = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
            }
            object.put("url", g_url);

            if (!debug_path.isEmpty()) {
                //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存日志等调试信息
                object.put("debug_path", debug_path);
                //设置本地存储日志文件的最大字节数, 最大将会在本地存储2个设置字节大小的日志文件
                object.put("max_log_file_size", 50 * 1024 * 1024);
            }

            //过滤SDK内部日志通过回调送回到用户层
            object.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_INFO)));

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
                mEncodeType = parts[0];
            }

            JSONObject object = new JSONObject();
            object.put("model", mModelSpin.getSelectedItem().toString());
            object.put("voice", mEncodeType);
            /**
             * 音频编码格式。
             * 所有模型均支持的编码格式：pcm、wav和mp3（默认）
             * 除cosyvoice-v1外，其他模型支持的编码格式：opus
             * 音频格式为opus时，支持通过bit_rate参数调整码率。
             * */
            object.put("format", mFormatSpin.getSelectedItem().toString());
            if (mFormatSpin.getSelectedItem().toString().equals("opus") ||
                    mFormatSpin.getSelectedItem().toString().equals("mp3")) {
                // 若设置mp3/opus格式, 可enable_audio_decoder打开内部解码器, 将mp3/opus编码成pcm
                object.put("enable_audio_decoder", true);
            } else {
                object.put("enable_audio_decoder", false);
            }
            /**
             * 音频采样率，支持下述采样率（单位：Hz）：
             * 8000, 16000, 22050（默认）, 24000, 44100, 48000。
             * */
            object.put("sample_rate", SAMPLE_RATE);
            /**
             * 音量，取值范围：0～100。默认值：50。
             * */
            object.put("volume", Integer.parseInt(mVolumeSpin.getSelectedItem().toString()));
            /**
             * 合成音频的语速，取值范围：0.5~2。
             * 0.5：表示默认语速的0.5倍速。
             * 1：表示默认语速。默认语速是指模型默认输出的合成语速，语速会依据每一个音色略有不同，约每秒钟4个字。
             * 2：表示默认语速的2倍速。
             * 默认值：1.0。
             * */
            object.put("rate", Float.parseFloat(mSpeedSpin.getSelectedItem().toString()));
            /**
             * 合成音频的语调，取值范围：0.5~2。
             * 默认值：1.0。
             * */
            object.put("pitch", Float.parseFloat(mPitchSpin.getSelectedItem().toString()));

            /**
             * bit_rate:
             * 指定音频的码率，取值范围：6~510kbps。
             * 码率越大，音质越好，音频文件体积越大。
             * 仅在音频格式（format）为opus时可用。
             * cosyvoice-v1模型不支持该参数。
             * */
            String bit_rate = mBitRateSpin.getSelectedItem().toString();
            if (!TextUtils.isEmpty(bit_rate) && !bit_rate.trim().isEmpty()) {
                String[] parts2 = bit_rate.split(" ");
                if (parts2.length > 0) {
                    object.put("bit_rate", Integer.parseInt(parts2[0]));
                }
            }

            if (mModelSpin.getSelectedItem().toString().equals("cosyvoice-v3") ||
                    mModelSpin.getSelectedItem().toString().equals("cosyvoice-v3-plus")) {
                /**
                 * seed:
                 * 生成时使用的随机数种子，使合成的效果产生变化。默认值0。取值范围：0~65535。
                 * 仅cosyvoice-v3、cosyvoice-v3-plus支持该功能。
                 * */
                String seed = mSeedSpin.getSelectedItem().toString();
                if (!TextUtils.isEmpty(seed) && !seed.trim().isEmpty()) {
                    object.put("seed", Integer.parseInt(seed));
                }

                /**
                 * style:
                 * 调整风格。默认值0。取值应为大于等于0的整数。
                 * 仅cosyvoice-v3、cosyvoice-v3-plus支持该功能。
                 * */
                String style = mStyleSpin.getSelectedItem().toString();
                if (!TextUtils.isEmpty(style) && !style.trim().isEmpty()) {
                    object.put("style", Integer.parseInt(style));
                }

                /**
                 * instruction:
                 * 设置提示词。
                 * 仅cosyvoice-v3、cosyvoice-v3-plus支持该功能。
                 * 目前仅支持设置情感。
                 * 格式：“你说话的情感是<情感值>。”。
                 * 支持的情感值：Neutral、Fearful、Angry、Sad、Surprised、Happy、Disgusted。
                 * */
                String instruction = mInstructionSpin.getSelectedItem().toString();
                if (!TextUtils.isEmpty(instruction) && !instruction.trim().isEmpty()) {
                    String instruction_prompt = "你说话的情感是" + instruction + "。"; /* 必须要有句号 */
                    object.put("instruction", instruction_prompt);
                }

                /**
                 * language_hints:
                 * 设置合成语种。
                 * 仅cosyvoice-v3、cosyvoice-v3-plus支持该功能。
                 * 当前只支持同时配置一个语种。
                 * 取值范围：
                 * "zh"：中文
                 * "en"：英文
                 * */
                String languageHints = mLanguageHintsSpin.getSelectedItem().toString();
                if (!TextUtils.isEmpty(languageHints) && !languageHints.trim().isEmpty()) {
                    JSONArray language_hints_array = new JSONArray();
                    language_hints_array.add(languageHints);
                    object.put("language_hints", language_hints_array.toString());
                }
            }

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
            public void onStreamInputTtsEventCallback(INativeStreamInputTtsCallback.StreamInputTtsEvent event,
                                                      String task_id, String session_id, int ret_code,
                                                      String error_msg, String timestamp, String all_response) {
                Log.i(TAG, "stream input tts event(" + event + ") task id(" + task_id + ") retCode(" + ret_code + ") errMsg(" + error_msg + ")");
                if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SYNTHESIS_STARTED) {
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SYNTHESIS_STARTED");
                    startedTimestamp = System.currentTimeMillis();
                    firstDataFlag = true;
                    curTaskId = task_id;
                    showText(eventView, "STREAM_INPUT_TTS_EVENT_SYNTHESIS_STARTED");
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
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SENTENCE_SYNTHESIS:" + all_response);
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
                        String errorCode = "";
                        JSONObject jsonObject = JSONObject.parseObject(all_response);
                        JSONObject header = jsonObject.getJSONObject("header");
                        if (header != null && header.getString("error_code") != null) {
                            errorCode = header.getString("error_code");
                        }

                        showText(eventView, all_response);

                        final String show_mesg = "error_code(" + ret_code + ") error_message(" + error_msg + ") error_code(" + errorCode + ")";
                        Log.e(TAG, "STREAM_INPUT_TTS_EVENT_TASK_FAILED: " + show_mesg);
                        final String msg_text = errorCode + "\n" + error_msg;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(DashCosyVoiceStreamTtsActivity.this, msg_text, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        long startedLatency = startedTimestamp - startTimestamp;
                        long firstLatency = firstDataTimestamp - startedTimestamp;
                        final String show_latency = "建连:" + startedLatency + "ms+首包:" + firstLatency + "=" + (startedLatency + firstLatency) + "ms.";
                        Log.i(TAG, "show latency ->" + show_latency);
                        showText(eventView, show_latency);
                    }
                }
            }

            @Override
            public void onStreamInputTtsDataCallback(byte[] data) {
                if (data.length > 0) {
                    if (firstDataFlag) {
                        firstDataFlag = false;
                        firstDataTimestamp = System.currentTimeMillis();
                        Log.i(TAG, "Get first audio data.");
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
            public void onStreamInputTtsLogTrackCallback(Constants.LogLevel level, String log) {
//                Log.i(TAG, "onStreamInputTtsLogTrackCallback log level:" + level + ", message -> " + log);
            }
        }, genTicket(), genParameters(), "", Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_DEBUG), true);

        if (Constants.NuiResultCode.SUCCESS != ret) {
            Log.i(TAG, "start tts failed " + ret);
            is_started = false;
            final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashCosyVoiceStreamTtsActivity.this, msg_text, Toast.LENGTH_SHORT).show();
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
            public void onStreamInputTtsEventCallback(INativeStreamInputTtsCallback.StreamInputTtsEvent event,
                                                      String task_id, String session_id, int ret_code,
                                                      String error_msg, String timestamp, String all_response) {
                Log.i(TAG, "stream input tts event(" + event + ") session id(" + session_id + ") task id(" + task_id + ") retCode(" + ret_code + ") errMsg(" + error_msg + ")");
                if (event == StreamInputTtsEvent.STREAM_INPUT_TTS_EVENT_SYNTHESIS_STARTED) {
                    Log.i(TAG, "STREAM_INPUT_TTS_EVENT_SYNTHESIS_STARTED");
                    startedTimestamp = System.currentTimeMillis();
                    firstDataFlag = true;
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
                        String errorCode = "";
                        JSONObject jsonObject = JSONObject.parseObject(all_response);
                        JSONObject header = jsonObject.getJSONObject("header");
                        if (header != null && header.getString("error_code") != null) {
                            errorCode = header.getString("error_code");
                        }

                        final String show_mesg = "error_code(" + ret_code + ") error_message(" + error_msg + ") error_code(" + errorCode + ")";
                        Log.e(TAG, "STREAM_INPUT_TTS_EVENT_TASK_FAILED: " + show_mesg);
                        final String msg_text = errorCode + "\n" + error_msg;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(DashCosyVoiceStreamTtsActivity.this, msg_text, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        long startedLatency = startedTimestamp - startTimestamp;
                        long firstLatency = firstDataTimestamp - startedTimestamp;
                        final String show_latency = "建连:" + startedLatency + "ms+首包:" + firstLatency + "=" + (startedLatency + firstLatency) + "ms.";
                        Log.i(TAG, "show latency ->" + show_latency);
                        showText(eventView, show_latency);
                    }
                }
            }

            @Override
            public void onStreamInputTtsDataCallback(byte[] data) {
                if (data.length > 0) {
                    if (firstDataFlag) {
                        firstDataFlag = false;
                        firstDataTimestamp = System.currentTimeMillis();
                        Log.i(TAG, "Get first audio data.");
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
            public void onStreamInputTtsLogTrackCallback(Constants.LogLevel level, String log) {
//                Log.i(TAG, "onStreamInputTtsLogTrackCallback log level:" + level + ", message -> " + log);
            }
        }, genTicket(), genParameters(), text, "", Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_DEBUG), true);

        if (Constants.NuiResultCode.SUCCESS != ret) {
            Log.i(TAG, "play tts failed " + ret);
            is_started = false;
            final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashCosyVoiceStreamTtsActivity.this, msg_text, Toast.LENGTH_SHORT).show();
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
            if (!mModelSpin.getSelectedItem().toString().contains("cosyvoice-v1") &&
                    mEncodeType.equals("opus")) {
                mBitRateSpin.setVisibility(View.VISIBLE);
                getBitRateList();
            } else {
                mBitRateSpin.setVisibility(View.GONE);
            }
        } else if (view == mModelSpin) {
            getFontList();
            if (mModelSpin.getSelectedItem().toString().contains("cosyvoice-v3")) {
                mSeedSpin.setVisibility(View.VISIBLE);
                getSeedList();
                mStyleSpin.setVisibility(View.VISIBLE);
                getStyleList();
                mInstructionSpin.setVisibility(View.VISIBLE);
                getInstructionList();
                mLanguageHintsSpin.setVisibility(View.VISIBLE);
                getLanguageHintsList();
            } else {
                mSeedSpin.setVisibility(View.GONE);
                mStyleSpin.setVisibility(View.GONE);
                mInstructionSpin.setVisibility(View.GONE);
                mLanguageHintsSpin.setVisibility(View.GONE);
            }
            if (!mModelSpin.getSelectedItem().toString().contains("cosyvoice-v1") &&
                    mFormatSpin.getSelectedItem().toString().equals("opus")) {
                mBitRateSpin.setVisibility(View.VISIBLE);
                getBitRateList();
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
                Toast.makeText(DashCosyVoiceStreamTtsActivity.this, str, Toast.LENGTH_LONG).show();
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
