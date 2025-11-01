package mit.alibaba.nuidemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
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
import com.alibaba.idst.nui.INativeFileTransCallback;
import com.alibaba.idst.nui.NativeNui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class DashParaformerFileTranscriberActivity extends Activity implements INativeFileTransCallback, View.OnClickListener, OnItemSelectedListener {
    private static final String TAG = "ParaformerFT";

    private String g_apikey = "";

    private long cur_expires_at = 0;
    private String tmp_apikey = "";

    NativeNui nui_instance = new NativeNui();
    NativeNui nui_utils_instance = new NativeNui(Constants.ModeType.MODE_UTILS);
    private final int MAX_TASKS = 1; // 这里同时只跑一个任务
    private final Map<String, List<String>> paramMap = new HashMap<>();
    private Button startButton;
    private Button queryButton;
    private Button cancelButton;
    private Switch mWorkModeSwitch;
    private TextView asrView;
    private Spinner mModelSpin;
    private EditText linkText;

    private String mDebugPath = "";  // demo音频文件存储路径，可以任意设置, 但是注意路径权限
    private String file_wav = "";
    private final String default_wav_address = "https://gw.alipayobjects.com/os/bmw-prod/0574ee2e-f494-45a5-820f-63aee583045a.wav";
    private final String default_mp3_address = "https://dashscope.oss-cn-beijing.aliyuncs.com/samples/audio/sensevoice/long_audio_demo_cn.mp3";
    private final String default_mp4_address = "https://dashscope.oss-cn-beijing.aliyuncs.com/samples/audio/sensevoice/sample_video_poetry.mp4";
    private final String default_other_address = "https://<无演示音频>";
    private List<String> file_urls_list = new ArrayList<>();
    private String file_address = default_wav_address;
    private boolean mInit = false;
    private final List<String> task_list = new ArrayList<String>();
    private Handler mHandler;
    private HandlerThread mHanderThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paraformer_ft);

        String version = nui_instance.GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DashParaformerFileTranscriberActivity.this,
                        version_text, Toast.LENGTH_SHORT).show();
            }
        });

        // 获取传递的参数
        Intent intent = getIntent();
        if (intent != null) {
            g_apikey = intent.getStringExtra("apikey");
            Log.i(TAG, "Get access ->\n API Key:" + g_apikey);
        }

        mDebugPath = getExternalCacheDir().getAbsolutePath() + "/debug";
        Utils.createDir(mDebugPath);

        file_urls_list.add(default_wav_address);
        file_urls_list.add(default_mp3_address);
        file_urls_list.add(default_mp4_address);
        file_address = String.join("\n", file_urls_list);

        initUIWidgets();

        mHanderThread = new HandlerThread("process_thread");
        mHanderThread.start();
        mHandler = new Handler(mHanderThread.getLooper());
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();
        doInit ();
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

    private void getModelList() {
        List<String> model = new ArrayList<>();
        model.add("paraformer-v2");
        model.add("paraformer-v1");
        model.add("paraformer-8k-v2");
        model.add("paraformer-8k-v1");
        model.add("paraformer-mtl-v1");
        ArrayAdapter<String> spinnerModel = new ArrayAdapter<String>(
                DashParaformerFileTranscriberActivity.this,
                android.R.layout.simple_spinner_dropdown_item, model);
        mModelSpin.setAdapter(spinnerModel);
        mModelSpin.setSelection(0);
        paramMap.put("model", model);
    }

    private void initUIWidgets() {
        asrView = (TextView)findViewById(R.id.textView16);
        asrView.setMovementMethod(new ScrollingMovementMethod());

        startButton = (Button)findViewById(R.id.button_start);
        queryButton = (Button)findViewById(R.id.button_query);
        cancelButton = (Button)findViewById(R.id.button_cancel);

        mWorkModeSwitch = (Switch) findViewById(R.id.switch_work_mode);

        mModelSpin = (Spinner) findViewById(R.id.set_model);
        mModelSpin.setOnItemSelectedListener(this);

        getModelList();

        TextView t1View = (TextView)findViewById(R.id.textView10);
        t1View.setText("音频链接：");
        linkText = (EditText)findViewById(R.id.editView7);
        linkText.setText(file_address);

        setButtonState(startButton, true);
        setButtonState(queryButton, false);
        setButtonState(cancelButton, false);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "start!!!");

                setButtonState(startButton, false);
                setButtonState(cancelButton, true);
                if (mWorkModeSwitch.isChecked()) {
                    setButtonState(queryButton, true);
                } else {
                    setButtonState(queryButton, false);
                }
                showText(asrView, "");
                startTranscriber();
            }
        });

        queryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "query!!!");
                synchronized(task_list) {
                    int size = task_list.size();
                    for (int i = 0; i < size; i++) {
                        String task = task_list.get(i);
                         nui_instance.queryFileTranscriber(task);
                    }
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
                setButtonState(queryButton, false);
                setButtonState(cancelButton, false);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized(task_list) {
                            int size = task_list.size();
                            for (int i = 0; i < size; i++) {
                                String task = task_list.get(i);
                                int ret = nui_instance.cancelFileTranscriber(task);
                                Log.i(TAG, "cancel dialog " + ret + " end");
                            }
                            task_list.clear();
                        }
                    }
                });

            }
        });

        mWorkModeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mWorkModeSwitch.setText("异步请求，手动查询结果");
                    setButtonState(queryButton, true);
                } else {
                    mWorkModeSwitch.setText("同步请求，直到返回结果");
                    setButtonState(queryButton, false);
                }
            }
        });
    }

    private void doInit() {
        setButtonState(startButton, true);
        setButtonState(queryButton, false);
        setButtonState(cancelButton, false);

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
                    Toast.makeText(DashParaformerFileTranscriberActivity.this,
                            msg_text, Toast.LENGTH_SHORT).show();
                }
            });
        }

        //设置相关识别参数，具体参考API文档
        nui_instance.setParams(genParams());
    }

    private String genParams() {
        String params = "";
        try {
            JSONObject nls_config = new JSONObject();
            JSONObject tmp = new JSONObject();
            tmp.put("nls_config", nls_config);
            params = tmp.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    private void startTranscriber() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized(task_list) {
                    task_list.clear();
                    for (int i = 0; i < MAX_TASKS; i++) {
                        byte[] task_id = new byte[32];
                        String params = genDialogParams();
                        if (!params.isEmpty()) {
                            int ret = nui_instance.startFileTranscriber(params, task_id);
                            if (ret != Constants.NuiResultCode.SUCCESS) {
                                final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(DashParaformerFileTranscriberActivity.this,
                                                msg_text, Toast.LENGTH_SHORT).show();
                                        asrView.setText(msg_text);
                                    }
                                });
                                Log.e(TAG, "start FileTrans failed with " + ret);
                                // Failed
                                setButtonState(startButton, true);
                                setButtonState(queryButton, false);
                                setButtonState(cancelButton, false);
                            } else {
                                String taskId = new String(task_id);
                                task_list.add(taskId);
                                Log.i(TAG, "start task id " + taskId + " done");
                            }
                        } else {
                            // Failed
                            setButtonState(startButton, true);
                            setButtonState(queryButton, false);
                            setButtonState(cancelButton, false);
                        }
                    }
                }
            }
        });
    }

    private String genInitParams(String debugpath) {
        String str = "";
        try{
            //获取账号访问凭证：
            JSONObject object = new JSONObject();

            // 若此处设置长效apikey, 则不需要每次startFileTranscriber()都设置
            // 注意！不推荐在这里设置apikey。推荐每次在startFileTranscriber()时设置临时鉴权token。
//            object.put("apikey", g_apikey);

            object.put("url", "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription"); // 必填

            object.put("device_id", "empty_device_id"); // 必填, 推荐填入具有唯一性的id, 方便定位问题。

            //debug目录。当初始化SDK时的save_log参数取值为true时，该目录用于保存中间音频文件
            object.put("debug_path", debugpath);

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
            /**
             * 参数详见
             * 接口说明：
             *
             * 移动端SDK说明：
             *
             */
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

            // 音视频文件转写的URL列表，支持HTTP / HTTPS协议，单次请求最多支持100个URL。
            JSONArray file_urls_array = (JSONArray) JSON.toJSON(
                    Arrays.stream(linkText.getText().toString().split("\n"))
                            .map(String::trim)                    // 去除前后空格
                            .filter(s -> !s.isEmpty())            // 过滤空行
                            .collect(Collectors.toList())
            );
            if (file_urls_array.size() > 0) {
                dialog_param.put("file_urls", file_urls_array);
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(DashParaformerFileTranscriberActivity.this, "请输入音频链接",
                                Toast.LENGTH_SHORT).show();
                    }
                });
                return "";
            }

            dialog_param.put("async_request", mWorkModeSwitch.isChecked());

            JSONObject nls_config = new JSONObject();

            // 模型选择, 注意模型对应的采样率要求。
            nls_config.put("model", mModelSpin.getSelectedItem().toString());

            // 设置热词ID，若未设置则不生效。v2及更高版本模型设置热词ID时使用该字段。
            // 在本次语音识别中，将应用与该热词ID对应的热词信息。
//            nls_config.put("vocabulary_id", "1234567890");

            // 热词ID，此次语音识别中生效此热词ID对应的热词信息。默认不启用。
//            JSONArray resources = new JSONArray();
//            JSONObject resource = new JSONObject();
//            resource.put("resource_id", "111");
//            resource.put("resource_type", "asr_phrase");
//            resources.add(resource);
//            nls_config.put("resources", resources);

            // 指定在多音轨文件中需要进行语音识别的音轨索引，以List的形式给出，
            // 例如[0]表示仅识别第一条音轨，[0, 1]表示同时识别前两条音轨。
//            JSONArray channel_id_array = new JSONArray();
//            channel_id_array.add(0);
//            nls_config.put("channel_id", channel_id_array);

            // 过滤语气词，默认关闭。
//            nls_config.put("disfluency_removal_enabled", true);

            // 是否启用时间戳校准功能，默认关闭。
//            nls_config.put("timestamp_alignment_enabled", true);

            // 指定在语音识别过程中需要处理的敏感词，并支持对不同敏感词设置不同的处理方式。
            // 若未传入该参数，系统将启用系统内置的敏感词过滤逻辑
//            JSONObject special_word_filter = new JSONObject();
//            JSONObject filter_with_signed = new JSONObject();
//            JSONArray filter_with_signed_array = new JSONArray();
//            filter_with_signed_array.add("测试");
//            filter_with_signed.put("word_list", filter_with_signed_array);
//            special_word_filter.put("filter_with_signed", filter_with_signed);
//            JSONObject filter_with_empty = new JSONObject();
//            JSONArray filter_with_empty_array = new JSONArray();
//            filter_with_empty_array.add("开始");
//            filter_with_empty_array.add("发送");
//            filter_with_empty.put("word_list", filter_with_empty_array);
//            special_word_filter.put("filter_with_empty", filter_with_empty);
//            special_word_filter.put("system_reserved_filter", true);
//            nls_config.put("special_word_filter", special_word_filter);

            // 设置待识别语言代码。如果无法提前确定语种，可不设置，模型会自动识别语种。
//            JSONArray language_hints_array = new JSONArray();
//            language_hints_array.add("zh");
//            language_hints_array.add("en");
//            nls_config.put("language_hints", language_hints_array);

            // 自动说话人分离，默认关闭。
            // 仅适用于单声道音频，多声道音频不支持说话人分离。
            // 启用该功能后，识别结果中将显示speaker_id字段，用于区分不同说话人。
//            nls_config.put("diarization_enabled", true);

            // 说话人数量参考值。取值范围为2至100的整数（包含2和100）。
            // 开启说话人分离功能后（diarization_enabled设置为true）生效。
            // 默认自动判断说话人数量，如果配置此项，只能辅助算法尽量输出指定人数，无法保证一定会输出此人数。
//            nls_config.put("speaker_count", 2);

            /*若文档中不包含某些参数，但是录音文件极速转写功能支持这个参数，可以用如下万能接口设置参数*/
//            JSONObject extend_config = new JSONObject();
//            extend_config.put("xxxxxx", true);
//            nls_config.put("extend_config", extend_config);

            dialog_param.put("nls_config", nls_config);
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
                    Toast.makeText(DashParaformerFileTranscriberActivity.this, "SDK未成功初始化.", Toast.LENGTH_LONG).show();
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

    @Override
    public void onItemSelected(AdapterView<?> view, View arg1, int arg2, long arg3) {
        if (view == mModelSpin) {
            String model = mModelSpin.getSelectedItem().toString();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    @Override
    public void onClick(View v) {
    }

    @Override
    public void onFileTransEventCallback(Constants.NuiEvent event, int resultCode,
                                         int finish, AsrResult asrResult, String taskId) {
        Log.i(TAG, "event=" + event + " task_id " + taskId);
        if (event == Constants.NuiEvent.EVENT_FILE_TRANS_CONNECTED) {
            showText(asrView, "成功链接服务...");
        } else if (event == Constants.NuiEvent.EVENT_FILE_TRANS_UPLOADED) {
            showText(asrView, "完成上传，正在转写... task_id:" + taskId);
        } else if (event == Constants.NuiEvent.EVENT_FILE_TRANS_QUERY_RESULT) {
            showText(asrView, "正在识别... task_id:" + taskId + "\n\n" + asrResult.asrResult);
        } else if (event == Constants.NuiEvent.EVENT_FILE_TRANS_RESULT) {
            setButtonState(startButton, true);
            setButtonState(queryButton, false);
            setButtonState(cancelButton, false);

            JSONObject jsonObject = JSON.parseObject(asrResult.asrResult);
            JSONObject output = jsonObject.getJSONObject("output");
            JSONObject task_metrics = output.getJSONObject("task_metrics");
            String task_metrics_str = task_metrics.toString();

            showText(asrView, "识别完成！ \ntask_id:" + taskId + "\n" + task_metrics_str + "\n\n识别结果请关注transcription_url");
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR) {
            Log.i(TAG, "error happened: " + resultCode);
            setButtonState(startButton, true);
            setButtonState(queryButton, false);
            setButtonState(cancelButton, false);

            String msg_text = Utils.getMsgWithErrorCode(resultCode, "start");
            msg_text += "\n" + asrResult.allResponse;
            if (resultCode == 240002) {
                msg_text += " : " + file_wav;
            }
            showText(asrView, msg_text);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final String msg_show = Utils.getMsgWithErrorCode(resultCode, "start");
                    Toast.makeText(DashParaformerFileTranscriberActivity.this, msg_show,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    @Override
    public void onFileTransLogTrackCallback(Constants.LogLevel level, String log) {
//        Log.i(TAG, "onFileTransLogTrackCallback log level:" + level + ", message -> " + log);
    }
}



