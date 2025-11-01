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
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeFileTransCallback;
import com.alibaba.idst.nui.NativeNui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FileTranscriberActivity extends Activity implements INativeFileTransCallback, View.OnClickListener, OnItemSelectedListener {
    private static final String TAG = "FileTranscriberActivity";

    private String g_appkey = "";
    private String g_token = "";
    private String g_sts_token = "";
    private String g_ak = "";
    private String g_sk = "";
    private String g_url = "";

    NativeNui nui_instance = new NativeNui();
    private final int MAX_TASKS = 1; // 这里同时只跑一个任务
    private final Map<String, List<String>> paramMap = new HashMap<>();
    private Button startButton;
    private Button cancelButton;
    private TextView asrView;
    private Spinner mSampleRateSpin, mFormatSpin;
    private EditText linkText, pathText;
    private RadioGroup radioGroupMethod;

    private String mDebugPath = "";  // demo音频文件存储路径，可以任意设置, 但是注意路径权限
    private String file_wav = "";
    private final String default_wav_address = "https://gw.alipayobjects.com/os/bmw-prod/0574ee2e-f494-45a5-820f-63aee583045a.wav";
    private final String default_mp3_address = "https://dashscope.oss-cn-beijing.aliyuncs.com/samples/audio/sensevoice/long_audio_demo_cn.mp3";
    private final String default_mp4_address = "https://dashscope.oss-cn-beijing.aliyuncs.com/samples/audio/sensevoice/sample_video_poetry.mp4";
    private final String default_other_address = "https://<无演示音频>";
    private String file_address = default_wav_address;
    private boolean use_audio_address = false;
    private boolean mInit = false;
    private final List<String> task_list = new ArrayList<String>();
    private Handler mHandler;
    private HandlerThread mHanderThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_trans);

        String version = nui_instance.GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileTranscriberActivity.this,
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

        mDebugPath = getExternalCacheDir().getAbsolutePath() + "/debug";
        Utils.createDir(mDebugPath);

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

    private void getSampleRateList() {
        List<String> sr = new ArrayList<>();
        sr.add("16000");
        sr.add("8000");
        ArrayAdapter<String> spinnerSR = new ArrayAdapter<String>(FileTranscriberActivity.this,
                android.R.layout.simple_spinner_dropdown_item, sr);
        mSampleRateSpin.setAdapter(spinnerSR);
        mSampleRateSpin.setSelection(0);
        paramMap.put("sample_rate", sr);
    }

    private void getFormatList() {
        List<String> format = new ArrayList<>();
        format.add("wav");
        format.add("mp3");
        format.add("mp4");
        format.add("opus");
        format.add("aac");
        ArrayAdapter<String> spinnerFormat = new ArrayAdapter<String>(
                FileTranscriberActivity.this,
                android.R.layout.simple_spinner_dropdown_item, format);
        mFormatSpin.setAdapter(spinnerFormat);
        mFormatSpin.setSelection(0);
        paramMap.put("format", format);
    }

    private void initUIWidgets() {
        asrView = (TextView)findViewById(R.id.textView);
        asrView.setMovementMethod(new ScrollingMovementMethod());

        startButton = (Button)findViewById(R.id.button_start);
        cancelButton = (Button)findViewById(R.id.button_cancel);

        mSampleRateSpin = (Spinner) findViewById(R.id.set_sample_rate);
        mSampleRateSpin.setOnItemSelectedListener(this);
        mFormatSpin = (Spinner) findViewById(R.id.set_format);
        mFormatSpin.setOnItemSelectedListener(this);
        getSampleRateList();
        getFormatList();

        TextView t1View = (TextView)findViewById(R.id.textView10);
        t1View.setText("音频链接：");
        linkText = (EditText)findViewById(R.id.editView7);
        linkText.setText(default_wav_address);
        TextView t2View = (TextView)findViewById(R.id.textView11);
        t2View.setText("下载至本地，或直接设置本地文件路径：");
        pathText = (EditText)findViewById(R.id.editView8);
        pathText.setText(mDebugPath);

        radioGroupMethod = findViewById(R.id.radioGroupMethod);
        radioGroupMethod.check(R.id.radioButtonAudioFile);
        radioGroupMethod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radioButtonAudioFile) {
                    linkText.setText(default_wav_address);
                    t2View.setText("下载至本地，或直接设置本地文件路径：");
                    t2View.setVisibility(View.VISIBLE);
                    pathText.setText(mDebugPath);
                    pathText.setVisibility(View.VISIBLE);
                    asrView.setText("如果此本地文件存在，则直接进行录音文件识别。\n如果不存在，为了演示，则通过音频链接下载至本地再进行识别。");
                    use_audio_address = false;
                } else if (checkedId == R.id.radioButtonAudioAddress) {
                    t2View.setVisibility(View.GONE);
                    pathText.setVisibility(View.GONE);
                    asrView.setText("直接通过音频链接进行识别。");
                    use_audio_address = true;
                }
            }
        });

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "start!!!");

                setButtonState(startButton, false);
                setButtonState(cancelButton, true);
                showText(asrView, "");
                startTranscriber();
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
    }

    private void doInit() {
        setButtonState(startButton, true);
        setButtonState(cancelButton, false);

        //这里主动调用完成SDK配置文件的拷贝, 即将nuisdk.aar中assets中资源文件拷贝到cache目录
        // 注意: V2.6.2版本开始纯云端功能可不需要资源文件
//        if (CommonUtils.copyAssetsData(this)) {
//            Log.i(TAG, "copy assets data done");
//        } else {
//            Log.i(TAG, "copy assets failed");
//            return;
//        }

        //获取工作路径, 即获得拷贝后资源文件存储的cache路径, 作为workspace
        // 注意: V2.6.2版本开始纯云端功能可不需要资源文件
        String assets_path = "";
//        assets_path = CommonUtils.getModelPath(this);
//        Log.i(TAG, "use workspace " + assets_path);

        //初始化SDK，注意用户需要在Auth.getTicket中填入相关ID信息才可以使用。
        int ret = nui_instance.initialize(this, genInitParams(assets_path, mDebugPath),
                Constants.LogLevel.LOG_LEVEL_VERBOSE, true);
        Log.i(TAG, "result = " + ret);
        if (ret == Constants.NuiResultCode.SUCCESS) {
            mInit = true;
        } else {
            final String msg_text = Utils.getMsgWithErrorCode(ret, "init");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(FileTranscriberActivity.this,
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
//            如果有HttpDns则可进行设置
//            tmp.put("direct_ip", Utils.getDirectIp());
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
                            int ret = nui_instance.startFileTranscriber(genDialogParams(), task_id);
                            if (ret != Constants.NuiResultCode.SUCCESS) {
                                final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(FileTranscriberActivity.this,
                                                msg_text, Toast.LENGTH_SHORT).show();
                                        asrView.setText(msg_text);
                                    }
                                });
                                Log.e(TAG, "start FileTrans failed with " + ret);
                                // Failed
                                setButtonState(startButton, true);
                                setButtonState(cancelButton, false);
                            } else {
                                String taskId = new String(task_id);
                                task_list.add(taskId);
                                Log.i(TAG, "start task id " + taskId + " done");
                            }
                        } else {
                            // Failed
                            setButtonState(startButton, true);
                            setButtonState(cancelButton, false);
                        }
                    }
                }
            }
        });
    }

    private String genInitParams(String workpath, String debugpath) {
        String str = "";
        try{
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
                        Toast.makeText(FileTranscriberActivity.this,
                                "未获得有效临时凭证！", Toast.LENGTH_LONG).show();
                    }
                });
            }

            /*
             * 服务地址可选择：
             *  上海：https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/FlashRecognizer
             *  北京：https://nls-gateway-cn-beijing.aliyuncs.com/stream/v1/FlashRecognizer
             *  深圳：https://nls-gateway-cn-shenzhen.aliyuncs.com/stream/v1/FlashRecognizer
             */
            object.put("url", "https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/FlashRecognizer"); // 必填

            //工作目录路径，SDK从该路径读取配置文件
//            object.put("workspace", workpath); // V2.6.2版本开始纯云端功能可不设置workspace

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

            object.put("device_id", "empty_device_id"); // 必填, 推荐填入具有唯一性的id, 方便定位问题。

            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 注意! str中包含ak_id ak_secret token app_key等敏感信息, 实际产品中请勿在Log中输出这类信息！
        Log.i(TAG, "InsideUserContext:" + str);
        return str;
    }

    private String genDialogParams() {
        String params = "";
        try {
            /**
             * 参数详见
             * 接口说明：
             *  https://help.aliyun.com/zh/isi/developer-reference/sdk-reference-9
             * 移动端SDK说明：
             *  https://help.aliyun.com/zh/isi/developer-reference/sdks-for-mobile-clients
             */
            JSONObject dialog_param = new JSONObject();
            //若想在运行时切换app_key
            //dialog_param.put("app_key", "");

            file_wav = pathText.getText().toString();
            file_address = linkText.getText().toString();
            if (use_audio_address) {
                // 音频文件链接识别
                if (!file_address.isEmpty()) {
                    dialog_param.put("audio_address", file_address);
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(FileTranscriberActivity.this, "请输入音频链接",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    return "";
                }
            } else {
                // 本地录音文件识别
                if (false) {
                    //app无操作/sdcard/test.wav权限，可以把文件推送到cache下测试
                    dialog_param.put("file_path", "/sdcard/test.wav");
                    file_wav = getExternalCacheDir().getAbsolutePath() + "/test.wav";
                } else {
                    if (Utils.isFileExists(file_wav)) {
                        // 找到此录音文件
                        dialog_param.put("file_path", file_wav);
                    } else {
                        // 未找到此文件，先进行下载
                        if (!file_address.isEmpty()) {
                            //下载示例音频到本地，用于录音文件转写极速版。
                            showText(asrView, "正在获得用于识别的音频 ...");
                            String SampleDownloadLink = linkText.getText().toString();
                            file_wav = Utils.downloadFile(SampleDownloadLink, mDebugPath);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pathText.setText(file_wav);
                                }
                            });
                        }

                        if (Utils.isFileExists(file_wav)) {
                            dialog_param.put("file_path", file_wav);
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(FileTranscriberActivity.this, "未成功下载文件到本地",
                                            Toast.LENGTH_SHORT).show();
                                    asrView.setText("未成功下载文件到本地！");
                                }
                            });
                            return "";
                        }
                    }
                }
            }

            JSONObject nls_config = new JSONObject();

            /*音频编码格式。支持格式：MP4、AAC、MP3、OPUS、WAV。*/
            String fileExtension = Utils.getFileExtension(file_wav);
            if (fileExtension.isEmpty()) {
                nls_config.put("format", "wav");
            } else {
                nls_config.put("format", fileExtension);
            }

            /*
             * 表示语音识别模型的采样率，上传的音频如果不符合其取值会被自动升/降采样率至8000或16000。
             * 取值：16000（非电话）/8000（电话）。默认：16000。
             */
            String sample_rate = mSampleRateSpin.getSelectedItem().toString();
            nls_config.put("sample_rate", Integer.parseInt(sample_rate));

            /*添加热词表ID。默认：不添加。*/
//            nls_config.put("vocabulary_id", "xxx");

            /*添加自学习模型ID。默认：不添加。*/
//            nls_config.put("customization_id", "xxx");

            /*
             * ITN（逆文本inverse text normalization）中文数字转换阿拉伯数字。
             * 设置为True时，中文数字将转为阿拉伯数字输出，默认值：False。
             */
//            nls_config.put("enable_inverse_text_normalization", true);

            /*是否返回词级别信息。取值：true或false。默认：false（不开启）。*/
//            nls_config.put("enable_word_level_result", true);

            /*是否启用时间戳校准功能，取值：true或false，默认：false（不开启）。*/
//            nls_config.put("enable_timestamp_alignment", true);

            /*
             * 是否只识别首个声道，取值：true/false。（如果录音识别结果重复，您可以开启此参数。）
             * 默认为空：8k处理双声道，16k处理单声道。
             *  false：8k处理双声道，16k处理双声道。
             *  true：8k处理单声道，16k处理单声道。
             */
//            nls_config.put("first_channel_only", true);

            /*
             * 噪音参数阈值，取值范围：[-1, 1]。取值说明如下：
             * 取值越趋于-1，噪音被判定为语音的概率越大。
             * 取值越趋于+1，语音被判定为噪音的概率越大。
             */
//            nls_config.put("speech_noise_threshold", -0.1);

            /*
             * 敏感词过滤功能，支持开启或关闭，支持自定义敏感词。该参数可实现：
             * 不处理（默认，即展示原文）、过滤、替换为*。
             * */
            // 以实时转写为例
//            JSONObject filter_root = new JSONObject();
//            filter_root.put("system_reserved_filter", true);
//            // 将以下词语替换成空
//            JSONObject filter_root1 = new JSONObject();
//            JSONArray filter_array1 = new JSONArray();
//            filter_array1.add("开始");
//            filter_array1.add("发生");
//            filter_root1.put("word_list", filter_array1);
//            // 将以下词语替换成*
//            JSONObject filter_root2 = new JSONObject();
//            JSONArray filter_array2 = new JSONArray();
//            filter_array2.add("测试");
//            filter_root2.put("word_list", filter_array2);
//            // 可以全部设置，也可以部分设置
//            filter_root.put("filter_with_empty", filter_root1);
//            filter_root.put("filter_with_signed", filter_root2);
//            nls_config.put("special_word_filter", filter_root.toString());

            /*
             * 每句最多展示字数，取值范围：[4，50]。默认不启用该功能。启用后如不填写字数，则按照长句断句。
             * 该参数可用于字幕生成场景，控制单行字幕最大字数。
             */
//            nls_config.put("sentence_max_length", 10);

            /*若文档中不包含某些参数，但是录音文件极速转写功能支持这个参数，可以用如下万能接口设置参数*/
//            JSONObject extend_config = new JSONObject();
//            extend_config.put("first_channel_only", true);
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
                    Toast.makeText(FileTranscriberActivity.this, "SDK未成功初始化.", Toast.LENGTH_LONG).show();
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
        if (view == mFormatSpin) {
            String format = mFormatSpin.getSelectedItem().toString();
            if (format.equals("wav")) {
                linkText.setText(default_wav_address);
                pathText.setText(mDebugPath);
            } else if (format.equals("mp3")) {
                linkText.setText(default_mp3_address);
                pathText.setText(mDebugPath);
            } else if (format.equals("mp4")) {
                linkText.setText(default_mp4_address);
                pathText.setText(mDebugPath);
            } else if (format.equals("opus")) {
                linkText.setText(default_other_address);
                pathText.setText(mDebugPath);
            } else if (format.equals("aac")) {
                linkText.setText(default_other_address);
                pathText.setText(mDebugPath);
            }
        } else if (view == mSampleRateSpin) {

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
        if (event == Constants.NuiEvent.EVENT_FILE_TRANS_UPLOADED) {
            showText(asrView, "完成上传，正在转写...");
        } else if (event == Constants.NuiEvent.EVENT_FILE_TRANS_RESULT) {
            showText(asrView, asrResult.asrResult);
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR) {
            Log.i(TAG, "error happened: " + resultCode);
            setButtonState(startButton, true);
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
                    Toast.makeText(FileTranscriberActivity.this, msg_show,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
//        if (finish == 1) {
//            synchronized(task_list) {
//                task_list.remove(taskId);
//            }
//        }
    }
    @Override
    public void onFileTransLogTrackCallback(Constants.LogLevel level, String log) {
        Log.i(TAG, "onFileTransLogTrackCallback log level:" + level + ", message -> " + log);
    }
}



