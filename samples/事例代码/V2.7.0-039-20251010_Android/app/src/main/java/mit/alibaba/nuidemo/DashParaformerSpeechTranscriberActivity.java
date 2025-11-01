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
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeNuiCallback;
import com.alibaba.idst.nui.KwsResult;
import com.alibaba.idst.nui.NativeNui;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

// 本样例展示百炼在线Paraformer实时语音识别使用方法
// Android SDK 详细说明：
// Paraformer实时语音识别WebSocket API: https://help.aliyun.com/zh/model-studio/websocket-for-paraformer-real-time-service
public class DashParaformerSpeechTranscriberActivity extends Activity implements INativeNuiCallback, AdapterView.OnItemSelectedListener {
    private static final String TAG = "ParaformerST";

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
    private Switch mVadSwitch, mSaveAudioSwitch;
    private TextView resultView;
    private TextView asrView;
    private Spinner mSampleRateSpin, mFormatSpin, mModelSpin;

    private boolean mInit = false;
    private boolean mStopping = false;
    private String mDebugPath = "";
    private String curTaskId = "";
    private LinkedBlockingQueue<byte[]> tmpAudioQueue = new LinkedBlockingQueue();
    private String mRecordingAudioFilePath = "";
    private OutputStream mRecordingAudioFile = null;
    private HandlerThread mHanderThread;
    private Handler mHandler;
    private final String[] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paraformer_st);

        String version = nui_instance.GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DashParaformerSpeechTranscriberActivity.this,
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

    private void getSampleRateList() {
        List<String> sr = new ArrayList<>();
        sr.add("16000");
        sr.add("8000");
        ArrayAdapter<String> spinnerSR = new ArrayAdapter<String>(
                DashParaformerSpeechTranscriberActivity.this,
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
                DashParaformerSpeechTranscriberActivity.this,
                android.R.layout.simple_spinner_dropdown_item, format);
        mFormatSpin.setAdapter(spinnerFormat);
        mFormatSpin.setSelection(0);
        paramMap.put("format", format);
    }

    private void getModelList() {
        List<String> model = new ArrayList<>();
        model.add("paraformer-realtime-v2");
        model.add("paraformer-realtime-v1");
        model.add("paraformer-realtime-8k-v2");
        model.add("paraformer-realtime-8k-v1");
        ArrayAdapter<String> spinnerModel = new ArrayAdapter<String>(
                DashParaformerSpeechTranscriberActivity.this,
                android.R.layout.simple_spinner_dropdown_item, model);
        mModelSpin.setAdapter(spinnerModel);
        mModelSpin.setSelection(0);
        paramMap.put("model", model);
    }

    private void initUIWidgets() {
        asrView = (TextView) findViewById(R.id.textView);
        asrView.setMovementMethod(new ScrollingMovementMethod());
        resultView = (TextView) findViewById(R.id.kws_text);

        mVadSwitch = (Switch) findViewById(R.id.vad_switch);
        mVadSwitch.setVisibility(View.VISIBLE);
        mSaveAudioSwitch = (Switch) findViewById(R.id.save_audio_switch);
        mSaveAudioSwitch.setVisibility(View.VISIBLE);

        mSampleRateSpin = (Spinner) findViewById(R.id.set_sample_rate);
        mSampleRateSpin.setOnItemSelectedListener(this);
        mFormatSpin = (Spinner) findViewById(R.id.set_format);
        mFormatSpin.setOnItemSelectedListener(this);
        mModelSpin = (Spinner) findViewById(R.id.set_model);
        mModelSpin.setOnItemSelectedListener(this);

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
                showText(resultView, "");
                boolean ret = startDialog();
                if (ret == false) {
                    mStopping = false;
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

        mVadSwitch.setText("开启语音活动检测断句");
        mVadSwitch.setChecked(true);
        mVadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mVadSwitch.setText("开启语音活动检测断句");
                } else {
                    mVadSwitch.setText("开启语义断句");
                }
            }
        });
    }

    private void doInit() {
        showText(asrView, "");
        showText(resultView, "");

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);

        mDebugPath = getExternalCacheDir().getAbsolutePath() + "/debug";

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
                    Toast.makeText(DashParaformerSpeechTranscriberActivity.this,
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
            //接口说明可见https:

            // 设置待识别音频格式。
            nls_config.put("sr_format", mFormatSpin.getSelectedItem().toString());
            // 模型选择, 注意模型对应的采样率要求。
            nls_config.put("model", mModelSpin.getSelectedItem().toString());
            // 设置待识别音频采样率（单位Hz）。因模型而异。
            nls_config.put("sample_rate", Integer.parseInt(mSampleRateSpin.getSelectedItem().toString()));

            // 设置热词ID，若未设置则不生效。v2及更高版本模型设置热词ID时使用该字段。
            // 在本次语音识别中，将应用与该热词ID对应的热词信息。
//            nls_config.put("vocabulary_id", "1234567890");

            // 热词ID，此次语音识别中生效此热词ID对应的热词信息。默认不启用。
//            JSONArray resources = new JSONArray();
//            JSONObject resource = new JSONObject();
//            resource.put("resource_id", "111");
//            resource.put("resource_type", "asr_phrase");
//            resources.add(resource);
//            nls_config.put("resources", resources.toString());

            // 当需要与服务端保持长连接时，可通过该开关进行控制
//            nls_config.put("heartbeat", true);

            // 设置是否过滤语气词
//            nls_config.put("disfluency_removal_enabled", true);
            // 设置是否开启ITN（Inverse Text Normalization，逆文本正则化）。
//            nls_config.put("inverse_text_normalization_enabled", true);

            // 设置待识别语言代码。如果无法提前确定语种，可不设置，模型会自动识别语种。
//            JSONArray language_hints_array = new JSONArray();
//            language_hints_array.add("zh");
//            language_hints_array.add("en");
//            nls_config.put("language_hints", language_hints_array.toString());

            if (mVadSwitch.isChecked()) {
                // 设置开启VAD（Voice Activity Detection，语音活动检测）断句，默认关闭。
                nls_config.put("semantic_punctuation_enabled", false);
                // 设置VAD（Voice Activity Detection，语音活动检测）断句的静音时长阈值（单位为ms）。
//                nls_config.put("max_sentence_silence", 800);
                // 该开关打开时（true）可以防止VAD断句切割过长。默认关闭。
//                nls_config.put("multi_threshold_mode_enabled", true);
            } else {
                // 设置开启语义断句。
                nls_config.put("semantic_punctuation_enabled", true);
            }

            // 设置是否在识别结果中自动添加标点。
//            nls_config.put("punctuation_prediction_enabled", true);

            /*若文档中不包含某些参数，但是此功能支持这个参数，可以用如下万能接口设置参数*/
//            JSONObject extend_config = new JSONObject();
//            extend_config.put("custom_test", true);
//            nls_config.put("extend_config", extend_config);

            JSONObject tmp = new JSONObject();
            tmp.put("nls_config", nls_config);
            tmp.put("service_type", Constants.kServiceTypeSpeechTranscriber); // 必填

            params = tmp.toString();
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
                Log.d(TAG, "AudioRecorder new with sr " + mSampleRateSpin.getSelectedItem().toString());
            } else {
                Log.w(TAG, "AudioRecorder has been new with sr" + mSampleRateSpin.getSelectedItem().toString());
            }
        } else {
            Log.e(TAG, "AudioRecorder donnot get RECORD_AUDIO permission!");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashParaformerSpeechTranscriberActivity.this,
                            "未获得录音权限，无法正常运行。请通过设置界面重新开启权限。",
                            Toast.LENGTH_LONG).show();
                }
            });
            showText(asrView, "未获得录音权限，无法正常运行。通过设置界面重新开启权限。");
            return false;
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //设置相关识别参数，具体参考API文档，在startDialog前调用
                String setParamsString = genParams();
                Log.i(TAG, "nui set params " + setParamsString);
                nui_instance.setParams(setParamsString);
                //开始实时识别
                int ret = nui_instance.startDialog(Constants.VadMode.TYPE_P2T,
                        genDialogParams());
                Log.i(TAG, "start done with " + ret);
                if (ret == Constants.NuiResultCode.SUCCESS) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DashParaformerSpeechTranscriberActivity.this,
                                    "点击<停止>结束实时识别", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });

        return true;
    }

    private String genInitParams(String debug_path) {
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
            object.put("debug_path", debug_path);
            //设置本地存储日志文件的最大字节数, 最大将会在本地存储2个设置字节大小的日志文件
            object.put("max_log_file_size", 50 * 1024 * 1024);

            //过滤SDK内部日志通过回调送回到用户层
            object.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_INFO)));

            // FullMix = 0   // 选用此模式开启本地功能并需要进行鉴权注册
            // FullCloud = 1
            // FullLocal = 2 // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrMix = 3    // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrCloud = 4
            // AsrLocal = 5  // 选用此模式开启本地功能并需要进行鉴权注册
            // 这里只能选择FullMix和FullCloud
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

            // 注意！！！不要在端侧使用长效API Key！！！
            // 注意！！！不要在端侧使用长效API Key！！！
            // 注意！！！不要在端侧使用长效API Key！！！
            // 将长效API Key硬编码在端侧代码中，会导致安全风险！！！
            // 请在自建服务端获得临时鉴权Token（有效期60s，最长可设置1800s），再下发到端侧进行使用。
            // 临时鉴权Token: https://help.aliyun.com/zh/model-studio/obtain-temporary-authentication-token
            //
            // 服务只需要在临时Token(API Key)快过期前刷新一次。各端侧在Token(API Key)快过期前从服务获得新的
            // 临时Token(API Key)。
            dialog_param.put("apikey", g_apikey);

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
                    Toast.makeText(DashParaformerSpeechTranscriberActivity.this,
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
                    who.setText("识别文本");
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
        } else if (view == mModelSpin) {
            int position = mSampleRateSpin.getSelectedItemPosition();
            int new_position = 0;
            if (mModelSpin.getSelectedItem().toString().contains("8k")) {
                new_position = 1;
            } else {
                new_position = 0;
            }
            mSampleRateSpin.setSelection(new_position);
            Log.d(TAG, "sample rate id is " + position + " and new is " + new_position);
            // 调整录音器的采样率
            if (mAudioRecorder != null && position != new_position) {
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
            showText(asrView, "EVENT_TRANSCRIBER_STARTED");
            if (asrResult != null) {
                JSONObject jsonObject = JSON.parseObject(asrResult.allResponse);
                JSONObject header = jsonObject.getJSONObject("header");
                curTaskId = header.getString("task_id");
            }
        } else if (event == Constants.NuiEvent.EVENT_TRANSCRIBER_COMPLETE) {
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
            appendText(asrView, asrResult.allResponse);
            mStopping = false;
        } else if (event == Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT || event == Constants.NuiEvent.EVENT_SENTENCE_END) {
            if (mStopping) {
                appendText(asrView, asrResult.asrResult);
            } else {
                showText(asrView, asrResult.asrResult);
            }
            JSONObject jsonObject = JSON.parseObject(asrResult.allResponse);
            JSONObject payload = jsonObject.getJSONObject("payload");
            JSONObject output = payload.getJSONObject("output");
            JSONObject sentence = output.getJSONObject("sentence");
            String result = sentence.getString("text");
            showText(resultView, result);
        } else if (event == Constants.NuiEvent.EVENT_VAD_START) {
            showText(asrView, "EVENT_VAD_START");
        } else if (event == Constants.NuiEvent.EVENT_VAD_END) {
            appendText(asrView, "EVENT_VAD_END");
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR) {
            // asrResult在EVENT_ASR_ERROR中为错误信息，搭配错误码resultCode和其中的task_id更易排查问题，请用户进行记录保存。
            appendText(asrView, asrResult.asrResult);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashParaformerSpeechTranscriberActivity.this,
                            "ERROR with " + resultCode,
                            Toast.LENGTH_SHORT).show();
                }
            });
            final String msg_text = Utils.getMsgWithErrorCode(resultCode, "start");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DashParaformerSpeechTranscriberActivity.this,
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
                    Toast.makeText(DashParaformerSpeechTranscriberActivity.this,
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
                        mRecordingAudioFilePath = mDebugPath + "/" + "st_task_id_" + curTaskId + ".pcm";
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
                    appendText(asrView, show);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DashParaformerSpeechTranscriberActivity.this,
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
                    appendText(asrView, show);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DashParaformerSpeechTranscriberActivity.this,
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



