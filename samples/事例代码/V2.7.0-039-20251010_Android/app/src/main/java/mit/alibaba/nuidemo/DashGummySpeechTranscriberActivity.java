package mit.alibaba.nuidemo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeNuiCallback;
import com.alibaba.idst.nui.KwsResult;
import com.alibaba.idst.nui.NativeNui;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// 本样例展示百炼在线Gummy实时语音识别、翻译使用方法
// Android SDK 详细说明：
// Gummy实时语音识别翻译WebSocket API: https://help.aliyun.com/zh/model-studio/real-time-websocket-api
public class DashGummySpeechTranscriberActivity extends Activity implements INativeNuiCallback, AdapterView.OnItemSelectedListener {
    private static final String TAG = "GummyST";

    private String g_apikey = "";
    private String g_url = "";

    private long cur_expires_at = 0;
    private String tmp_apikey = "";

    NativeNui nui_instance = new NativeNui();
    NativeNui nui_utils_instance = new NativeNui(Constants.ModeType.MODE_UTILS);
    private final Map<String, List<String>> paramMap = new HashMap<>();
    private final static int SAMPLE_RATE = 16000;
    private final static int WAVE_FRAM_SIZE = 20 * 2 * 1 * SAMPLE_RATE / 1000; //20ms audio for 16k/16bit/mono
    private AudioRecord mAudioRecorder = null;

    private Button startButton;
    private Button cancelButton;
    private Switch mTranslationSwitch, mSaveAudioSwitch;
    private TextView asrView;
    private TextView transView;
    private TextView resultView;
    private Spinner mSampleRateSpin, mFormatSpin, mModelSpin;
    private Spinner mSrcLanguageSpin, mDstLanguageSpin;
    private LinearLayout languagesLayout;

    private boolean mInit = false;
    private boolean mStopping = false;
    private String mDebugPath = "";
    private String curTaskId = "";
    private LinkedBlockingQueue<byte[]> tmpAudioQueue = new LinkedBlockingQueue();
    private String mRecordingAudioFilePath = "";
    private OutputStream mRecordingAudioFile = null;
    private Handler mHandler;
    private HandlerThread mHanderThread;
    private final String[] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gummy_sr_demo);

        String version = nui_instance.GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DashGummySpeechTranscriberActivity.this, version_text, Toast.LENGTH_SHORT).show();
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

        mHanderThread = new HandlerThread("process_thread");
        mHanderThread.start();
        mHandler = new Handler(mHanderThread.getLooper());
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();
        doInit();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
        nui_instance.release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void getSrcLanguageList() {
        List<String> lans = new ArrayList<>();
        lans.add("auto-默认");
        lans.add("zh-中文");
        lans.add("en-英文");
        lans.add("ja-日语");
        lans.add("yue-粤语");
        lans.add("ko-韩语");
        lans.add("de-德语");
        lans.add("fr-法语");
        lans.add("ru-俄语");
        lans.add("it-意大利语");
        lans.add("es-西班牙语");
        ArrayAdapter<String> spinnerLans = new ArrayAdapter<String>(
                DashGummySpeechTranscriberActivity.this,
                android.R.layout.simple_spinner_dropdown_item, lans);
        mSrcLanguageSpin.setAdapter(spinnerLans);
        mSrcLanguageSpin.setSelection(0);
        paramMap.put("src_language", lans);
    }

    private void getDstLanguageList() {
        List<String> lans = new ArrayList<>();
        lans.add("en-英文");
        lans.add("zh-中文");
        lans.add("ja-日语");
        lans.add("ko-韩语");
        ArrayAdapter<String> spinnerLans = new ArrayAdapter<String>(
                DashGummySpeechTranscriberActivity.this,
                android.R.layout.simple_spinner_dropdown_item, lans);
        mDstLanguageSpin.setAdapter(spinnerLans);
        mDstLanguageSpin.setSelection(0);
        paramMap.put("dst_language", lans);
    }

    private void getSampleRateList() {
        List<String> sr = new ArrayList<>();
        sr.add("16000");
        ArrayAdapter<String> spinnerSR = new ArrayAdapter<String>(
                DashGummySpeechTranscriberActivity.this,
                android.R.layout.simple_spinner_dropdown_item, sr);
        mSampleRateSpin.setAdapter(spinnerSR);
        mSampleRateSpin.setSelection(0);
        paramMap.put("sample_rate", sr);
    }

    private void getFormatList() {
        List<String> format = new ArrayList<>();
        format.add("opus");
        format.add("pcm");
        ArrayAdapter<String> spinnerFormat = new ArrayAdapter<String>(
                DashGummySpeechTranscriberActivity.this,
                android.R.layout.simple_spinner_dropdown_item, format);
        mFormatSpin.setAdapter(spinnerFormat);
        mFormatSpin.setSelection(0);
        paramMap.put("format", format);
    }

    private void getModelList() {
        List<String> model = new ArrayList<>();
        model.add("gummy-realtime-v1");
        ArrayAdapter<String> spinnerModel = new ArrayAdapter<String>(
                DashGummySpeechTranscriberActivity.this,
                android.R.layout.simple_spinner_dropdown_item, model);
        mModelSpin.setAdapter(spinnerModel);
        mModelSpin.setSelection(0);
        paramMap.put("model", model);
    }

    private void initUIWidgets() {
        asrView = (TextView) findViewById(R.id.asrText);
        transView = (TextView) findViewById(R.id.transText);
        resultView = (TextView) findViewById(R.id.responseText);
        resultView.setMovementMethod(new ScrollingMovementMethod());

        languagesLayout = findViewById(R.id.layout_params0);

        mTranslationSwitch = (Switch) findViewById(R.id.translation_switch);
        mTranslationSwitch.setVisibility(View.VISIBLE);
        mSaveAudioSwitch = (Switch) findViewById(R.id.save_audio_switch);
        mSaveAudioSwitch.setVisibility(View.VISIBLE);

        mSrcLanguageSpin = (Spinner) findViewById(R.id.set_src_lan);
        mSrcLanguageSpin.setOnItemSelectedListener(this);
        mDstLanguageSpin = (Spinner) findViewById(R.id.set_dst_lan);
        mDstLanguageSpin.setOnItemSelectedListener(this);

        mSampleRateSpin = (Spinner) findViewById(R.id.set_sample_rate);
        mSampleRateSpin.setOnItemSelectedListener(this);
        mFormatSpin = (Spinner) findViewById(R.id.set_format);
        mFormatSpin.setOnItemSelectedListener(this);
        mModelSpin = (Spinner) findViewById(R.id.set_model);
        mModelSpin.setOnItemSelectedListener(this);

        getSrcLanguageList();
        getDstLanguageList();
        getSampleRateList();
        getFormatList();
        getModelList();

        startButton = (Button) findViewById(R.id.button_start);
        cancelButton = (Button) findViewById(R.id.button_cancel);
        setButtonState(startButton, true);
        setButtonState(cancelButton, false);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "start!!!");

                setButtonState(startButton, false);
                setButtonState(cancelButton, true);

                showText(asrView, "");
                showText(transView, "");
                showText(resultView, "");
                boolean ret = startDialog();
                if (ret == false) {
                    setButtonState(startButton, true);
                    setButtonState(cancelButton, false);
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "cancel");
                if (!checkNotInitToast()) {
                    return;
                }

                setButtonState(startButton, true);
                setButtonState(cancelButton, false);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mStopping = true;
                        long ret = nui_instance.stopDialog();
                        Log.i(TAG, "cancel dialog " + ret + " end");
                    }
                });

            }
        });

        mTranslationSwitch.setText("关闭翻译功能");
        mTranslationSwitch.setChecked(false);
        mTranslationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mTranslationSwitch.setText("开启翻译功能");
                    languagesLayout.setVisibility(View.VISIBLE);
                } else {
                    mTranslationSwitch.setText("关闭翻译功能");
                    languagesLayout.setVisibility(View.GONE);
                }
            }
        });
    }

    private void doInit() {
        showText(asrView, "");
        showText(transView, "");
        showText(resultView, "");

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);

        mDebugPath = getExternalCacheDir().getAbsolutePath() + "/debug";
        Utils.createDir(mDebugPath);

        //初始化SDK，注意用户需要在Auth.getTicket中填入相关ID信息才可以使用。
        int ret = nui_instance.initialize(this, genInitParams(mDebugPath),
                Constants.LogLevel.LOG_LEVEL_DEBUG, true);
        Log.i(TAG, "result = " + ret);
        if (ret == Constants.NuiResultCode.SUCCESS) {
            mInit = true;
        } else {
            final String msg_text = Utils.getMsgWithErrorCode(ret, "init");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashGummySpeechTranscriberActivity.this,
                            msg_text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private String genParams() {
        String params = "";
        try {
            JSONObject nls_config = new JSONObject();

            //参数可根据实际业务进行配置
            //接口说明可见:

            // 设置待识别音频格式。
            nls_config.put("sr_format", mFormatSpin.getSelectedItem().toString());
            // 模型选择, 注意模型对应的采样率要求。
            nls_config.put("model", mModelSpin.getSelectedItem().toString());
            // 设置待识别音频采样率（单位Hz）。只支持16000Hz。
            nls_config.put("sample_rate", Integer.parseInt(mSampleRateSpin.getSelectedItem().toString()));


            // 设置热词ID，若未设置则不生效。v2及更高版本模型设置热词ID时使用该字段。
            // 在本次语音识别中，将应用与该热词ID对应的热词信息。
//            nls_config.put("vocabulary_id", "1234567890");

            // 设置是否启用识别功能。
            // 模型支持单独开启识别或翻译功能，也可同时启用两种功能，但至少需要开启其中一种能力。
            nls_config.put("transcription_enabled", true);
            // 设置是否启用翻译功能。要正常输出翻译结果，需配置translation_target_languages参数。
            // 模型支持单独开启识别或翻译功能，也可同时启用两种功能，但至少需要开启其中一种能力。
            nls_config.put("translation_enabled", mTranslationSwitch.isChecked());
            if (mTranslationSwitch.isChecked()) {
                String[] src_language = mSrcLanguageSpin.getSelectedItem().toString().split("-");
                if (src_language[0] != "auto") {
                    // 设置源（待识别/翻译语言）语言代码。如果无法提前确定语种，可不设置，默认为auto。
                    nls_config.put("source_language", src_language[0]);
                }

                String[] dst_language = mDstLanguageSpin.getSelectedItem().toString().split("-");
                // 设置翻译目标语言代码。目标语言的代码与source_language参数一致。
                // 目前暂不支持同时翻译为多种语言，请仅设置一个目标语言以完成翻译。
                JSONArray target_src = new JSONArray();
                target_src.add(dst_language[0]);
                nls_config.put("translation_target_languages", target_src.toString());
            }

            // 设置最大结束静音时长，单位为毫秒（ms），取值范围为200ms至6000ms。
            // 若语音结束后静音时长超过该预设值，系统将判定当前语句已结束。
//            nls_config.put("max_end_silence", 800);

            // DNS解析的超时时间设置(单位ms)，默认5000
            //nls_config.put("dns_timeout", 500);


            /*若文档中不包含某些参数，但是此功能支持这个参数，可以用如下万能接口设置参数*/
//            JSONObject extend_config = new JSONObject();
//            JSONObject vocab = new JSONObject();
//            vocab.put("热词1", 2);
//            vocab.put("热词2", 2);
//            extend_config.put("vocabulary", vocab);
//            nls_config.put("extend_config", extend_config);

            JSONObject parameters = new JSONObject();

            parameters.put("nls_config", nls_config);
            parameters.put("service_type", Constants.kServiceTypeSpeechTranscriber); // 必填

            params = parameters.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    private boolean startDialog() {
        /*
         * 首先，录音权限动态申请
         * */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 检查该权限是否已经获取
            int i = ContextCompat.checkSelfPermission(this, permissions[0]);
            // 权限是否已经 授权 GRANTED---授权  DINIED---拒绝
            if (i != PackageManager.PERMISSION_GRANTED) {
                // 如果没有授予该权限，就去提示用户请求
                this.requestPermissions(permissions, 321);
            }
        }
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (mAudioRecorder == null) {
                //录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
                //使用者请根据实际情况选择Android设备的MediaRecorder.AudioSource
                //录音麦克风如何选择,可查看https://developer.android.google.cn/reference/android/media/MediaRecorder.AudioSource
                mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                        Integer.parseInt(mSampleRateSpin.getSelectedItem().toString()),
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        WAVE_FRAM_SIZE * 4);
                Log.d(TAG, "AudioRecorder new ...");
            } else {
                Log.w(TAG, "AudioRecord has been new ...");
            }
        } else {
            Log.e(TAG, "donnot get RECORD_AUDIO permission!");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashGummySpeechTranscriberActivity.this,
                            "未获得录音权限，无法正常运行。请通过设置界面重新开启权限。", Toast.LENGTH_LONG).show();
                }
            });
            showText(resultView, "未获得录音权限，无法正常运行。通过设置界面重新开启权限。");
            return false;
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //设置相关识别参数，具体参考API文档
                //  initialize()之后startDialog之前调用
                String setParamsString = genParams();
                Log.i(TAG, "nui set params " + setParamsString);
                nui_instance.setParams(setParamsString);
                //开始一句话识别
                int ret = nui_instance.startDialog(Constants.VadMode.TYPE_P2T,
                        genDialogParams());
                Log.i(TAG, "start done with " + ret);
                if (ret != 0) {
                    final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DashGummySpeechTranscriberActivity.this,
                                    msg_text, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });

        return true;
    }

    private String genInitParams(String debugpath) {
        String str = "";
        try{
            //获取账号访问凭证：
            JSONObject object = new JSONObject();

            // 不推荐在这里设置apikey。推荐在startDialog时通过获得临时token的方式获得临时鉴权token并设置。
//            object.put("apikey", g_apikey);

            object.put("device_id", "empty_device_id"); // 必填, 推荐填入具有唯一性的id, 方便定位问题
            if (g_url.isEmpty()) {
                g_url = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
            }
            object.put("url", g_url);

            //当初始化SDK时的save_log参数取值为true时，该参数生效。表示是否保存音频debug，该数据保存在debug目录中，需要确保debug_path有效可写。
            object.put("save_wav", "true");
            //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存中间音频文件。
            object.put("debug_path", debugpath);

            //过滤SDK内部日志通过回调送回到用户层
            object.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_INFO)));

            // FullMix = 0   // 选用此模式开启本地功能并需要进行鉴权注册
            // FullCloud = 1
            // FullLocal = 2 // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrMix = 3    // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrCloud = 4
            // AsrLocal = 5  // 选用此模式开启本地功能并需要进行鉴权注册
            object.put("service_mode", Constants.ModeFullCloud); // 必填
            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "InsideUserContext:" + str);
        return str;
    }

    private String genDialogParams() {
        String params = "";
        try {
            JSONObject dialog_param = new JSONObject();

            // 运行过程中可以在startDialog()时更新临时参数，尤其是更新过期apikey。
            // 注意！若初始化时未设置长效apikey，则startDialog()时需每次都设置有效的apikey。
            // 获得临时token结果示例：
            // error response: {"code":"InvalidApiKey","message":"Invalid API-key provided.","request_id":"1cf13817-e892-9d98-913b-7e8b7a83ff95"}
            // success response: {"token":"st-xxxxxxxxxxx","expires_at":1756205665}
            long cur_timestamp = System.currentTimeMillis() / 1000;
            if (tmp_apikey.isEmpty() || cur_timestamp > (cur_expires_at - 20)) {
                String response = nui_utils_instance.refreshApiKey(g_apikey, "");
                Log.i(TAG, "get temp apikey: " + response + " and current expires_at:" + cur_expires_at);
                JSONObject apikey_info = JSONObject.parseObject(response);
                if (apikey_info.containsKey("token")) {
                    tmp_apikey = apikey_info.getString("token");
                    cur_expires_at = apikey_info.getInteger("expires_at");
                } else {
                    Log.e(TAG, "get apikey failed: " + response);
                }
            }
            dialog_param.put("apikey", tmp_apikey);

            params = dialog_param.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "dialog params: " + params);
        return params;
    }

    private boolean checkNotInitToast() {
        if (!mInit) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashGummySpeechTranscriberActivity.this,
                            "SDK未成功初始化.", Toast.LENGTH_LONG).show();
                }
            });
            return false;
        } else {
            return true;
        }
    }

    private void setButtonState(final Button btn, final boolean state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "setBtn state " + btn.getText() + " state=" + state);
                btn.setEnabled(state);
            }
        });
    }

    private void showText(final TextView who, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "showText text=" + text);
                if (TextUtils.isEmpty(text)) {
                    Log.w(TAG, "asr text is empty");
                    if (who == asrView) {
                        who.setText("识别内容");
                    } else if (who == transView) {
                        who.setText("翻译内容");
                    } else {
                        who.setText("响应文本");
                    }
                } else {
                    who.setText(text);
                }
            }
        });
    }

    private void appendText(final TextView who, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "append text=" + text);
                if (TextUtils.isEmpty(text)) {
                    return;
                } else {
                    String orign = who.getText().toString();
                    who.setText(orign + "\n---\n" + text);
                }
            }
        });
    }

    @Override
    public void onItemSelected(AdapterView<?> view, View arg1, int arg2, long arg3) {
        if (view == mFormatSpin) {
        } else if (view == mSampleRateSpin) {
            if (mAudioRecorder != null) {
                Log.d(TAG, "AudioRecorder release.");
                mAudioRecorder.release();
                mAudioRecorder = null;
            }
        }
    }
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    //当回调事件发生时调用
    @Override
    public void onNuiEventCallback(Constants.NuiEvent event, final int resultCode,
                                   final int arg2, KwsResult kwsResult,
                                   AsrResult asrResult) {
        Log.i(TAG, "event=" + event + " resultCode=" + resultCode);
        // asrResult包含task_id，task_id有助于排查问题，请用户进行记录保存。

        if (event == Constants.NuiEvent.EVENT_TRANSCRIBER_STARTED) {
            // EVENT_TRANSCRIBER_STARTED 为V2.6.3版本新增
            showText(resultView, "EVENT_TRANSCRIBER_STARTED");
            JSONObject jsonObject = JSON.parseObject(asrResult.allResponse);
            JSONObject header = jsonObject.getJSONObject("header");
            curTaskId = header.getString("task_id");
        } else if (event == Constants.NuiEvent.EVENT_TRANSCRIBER_COMPLETE) {
            appendText(resultView, asrResult.allResponse);
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
            mStopping = false;

            JSONObject jsonObject = JSON.parseObject(asrResult.allResponse);
            JSONObject payload = jsonObject.getJSONObject("payload");
            JSONObject output = payload.getJSONObject("output");
            JSONObject transcription = output.getJSONObject("transcription");
            if (transcription != null) {
                String asr = transcription.getString("text");
                showText(asrView, asr);
            }

            JSONArray translations = output.getJSONArray("translations");
            StringBuilder sb = new StringBuilder();
            if (translations != null) {
                for (int i = 0; i < translations.size(); i++) {
                    JSONObject item = translations.getJSONObject(i);  // 获取每个元素
                    String text = item.getString("text");             // 获取 "text" 字段
                    if (text != null) {
                        sb.append(text);
                    }
                }
            }
            String trans = sb.toString();
            showText(transView, trans);
        } else if (event == Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT || event == Constants.NuiEvent.EVENT_SENTENCE_END) {
            if (mStopping) {
                appendText(resultView, asrResult.allResponse);
            } else {
                showText(resultView, asrResult.allResponse);
            }
            JSONObject jsonObject = JSON.parseObject(asrResult.allResponse);
            JSONObject payload = jsonObject.getJSONObject("payload");
            JSONObject output = payload.getJSONObject("output");
            JSONObject transcription = output.getJSONObject("transcription");
            if (transcription != null) {
                String asr = transcription.getString("text");
                showText(asrView, asr);
            }
            JSONArray translations = output.getJSONArray("translations");
            StringBuilder sb = new StringBuilder();
            if (translations != null) {
                for (int i = 0; i < translations.size(); i++) {
                    JSONObject item = translations.getJSONObject(i);  // 获取每个元素
                    String text = item.getString("text");             // 获取 "text" 字段
                    if (text != null) {
                        sb.append(text);
                    }
                }
            }
            String trans = sb.toString();
            showText(transView, trans);
        } else if (event == Constants.NuiEvent.EVENT_VAD_START) {
            showText(resultView, "EVENT_VAD_START");
        } else if (event == Constants.NuiEvent.EVENT_VAD_END) {
            appendText(resultView, "EVENT_VAD_END");
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR) {
            // asrResult在EVENT_ASR_ERROR中为错误信息，搭配错误码resultCode和其中的task_id更易排查问题，请用户进行记录保存。
            appendText(resultView, asrResult.allResponse);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashGummySpeechTranscriberActivity.this, "ERROR with " + resultCode,
                            Toast.LENGTH_SHORT).show();
                }
            });
            final String msg_text = Utils.getMsgWithErrorCode(resultCode, "start");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashGummySpeechTranscriberActivity.this,
                            msg_text, Toast.LENGTH_SHORT).show();
                }
            });

            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
            mStopping = false;
        } else if (event == Constants.NuiEvent.EVENT_MIC_ERROR) {
            // EVENT_MIC_ERROR表示2s未传入音频数据，请检查录音相关代码、权限或录音模块是否被其他应用占用。
            final String msg_text = Utils.getMsgWithErrorCode(resultCode, "start");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashGummySpeechTranscriberActivity.this,
                            msg_text, Toast.LENGTH_SHORT).show();
                }
            });

            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
            mStopping = false;
            // 此处也可重新启动录音模块
        }
    }

    //当调用NativeNui的start后，会一定时间反复回调该接口，底层会提供buffer并告知这次需要数据的长度
    //返回值告知底层读了多少数据，应该尽量保证return的长度等于需要的长度，如果返回<=0，则表示出错
    @Override
    public int onNuiNeedAudioData(byte[] buffer, int len) {
        if (mAudioRecorder == null) {
            return -1;
        }
        if (mAudioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "audio recorder not init");
            return -1;
        }

        // 送入SDK
        int audio_size = mAudioRecorder.read(buffer, 0, len);

        // 音频存储到本地
        if (mSaveAudioSwitch.isChecked() && audio_size > 0) {
            if (mRecordingAudioFile == null) {
                // 音频存储文件未打开，则等获得task_id后打开音频存储文件，否则数据存储到tmpAudioQueue
                if (!curTaskId.isEmpty() && mRecordingAudioFile == null) {
                    try {
                        mRecordingAudioFilePath = mDebugPath + "/" + "sr_task_id_" + curTaskId + ".pcm";
                        Log.i(TAG, "save recorder data into " + mRecordingAudioFilePath);
                        mRecordingAudioFile = new FileOutputStream(mRecordingAudioFilePath, true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    tmpAudioQueue.offer(buffer);
                }
            }
            if (mRecordingAudioFile != null) {
                // 若tmpAudioQueue有存储的音频，先存到音频存储文件中
                if (tmpAudioQueue.size() > 0) {
                    try {
                        // 将未打开recorder前的音频存入文件中
                        byte[] audioData = tmpAudioQueue.take();
                        try {
                            mRecordingAudioFile.write(audioData);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // 当前音频数据存到音频存储文件
                try {
                    mRecordingAudioFile.write(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return audio_size;
    }

    //当录音状态发送变化的时候调用
    @Override
    public void onNuiAudioStateChanged(Constants.AudioState state) {
        Log.i(TAG, "onNuiAudioStateChanged");
        if (state == Constants.AudioState.STATE_OPEN) {
            Log.i(TAG, "audio recorder start");
            if (mAudioRecorder == null) {
                //录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
                //使用者请根据实际情况选择Android设备的MediaRecorder.AudioSource
                //录音麦克风如何选择,可查看https://developer.android.google.cn/reference/android/media/MediaRecorder.AudioSource
                mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                        Integer.parseInt(mSampleRateSpin.getSelectedItem().toString()),
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        WAVE_FRAM_SIZE * 4);
                Log.d(TAG, "AudioRecorder new ...");
            }
            if (mAudioRecorder != null) {
                mAudioRecorder.startRecording();
            }
            Log.i(TAG, "audio recorder start done");
        } else if (state == Constants.AudioState.STATE_CLOSE) {
            Log.i(TAG, "audio recorder close");
            if (mAudioRecorder != null) {
                mAudioRecorder.release();
                mAudioRecorder = null;
            }

            try {
                if (mRecordingAudioFile != null) {
                    mRecordingAudioFile.close();
                    mRecordingAudioFile = null;
                    String show = "存储录音音频到 " + mRecordingAudioFilePath;
                    appendText(resultView, show);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DashGummySpeechTranscriberActivity.this,
                                    show, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (state == Constants.AudioState.STATE_PAUSE) {
            Log.i(TAG, "audio recorder pause");
            if (mAudioRecorder != null) {
                mAudioRecorder.stop();
            }

            try {
                if (mRecordingAudioFile != null) {
                    mRecordingAudioFile.close();
                    mRecordingAudioFile = null;
                    String show = "存储录音音频到 " + mRecordingAudioFilePath;
                    appendText(resultView, show);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DashGummySpeechTranscriberActivity.this,
                                    show, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onNuiAudioRMSChanged(float val) {
//        Log.i(TAG, "onNuiAudioRMSChanged vol " + val);
    }

    @Override
    public void onNuiVprEventCallback(Constants.NuiVprEvent event) {
        Log.i(TAG, "onNuiVprEventCallback event " + event);
    }

    @Override
    public void onNuiLogTrackCallback(Constants.LogLevel level, String log) {
//        Log.i(TAG, "onNuiLogTrackCallback log level:" + level + ", message -> " + log);
    }
}
