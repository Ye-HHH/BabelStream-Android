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
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeNuiCallback;
import com.alibaba.idst.nui.KwsResult;
import com.alibaba.idst.nui.NativeNui;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;

// 本样例展示本地唤醒及在线一句话语音识别使用方法
// 语音唤醒定制化程度比较高，需要根据使用设备/应用场景先确定唤醒词，再采集数据进行模型训练。初期可以提供demo集成和体验，但性能无法保证。
// 我们不对外直接提供具有唤醒能力的SDK和Demo，如有业务需求，请联系我们的产品或者商务的同事。
public class WakeupAndSpeechRecognizerActivity extends Activity implements INativeNuiCallback {
    private static final String TAG = "WakeupAndRecognizer";

    private String g_appkey = "";
    private String g_token = "";
    private String g_sts_token = "";
    private String g_ak = "";
    private String g_sk = "";
    private String g_url = "";
    private String g_sdk_code = "software_nls_tts_offline_standard"; // 精品版为software_nls_tts_offline， 标准版为software_nls_tts_offline_standard
    private String g_access_file = "";

    NativeNui nui_instance = new NativeNui();
    public final static int SAMPLE_RATE = 16000;
    final static int WAVE_FRAM_SIZE = 20 * 2 * 1 * SAMPLE_RATE / 1000; //20ms audio for 16k/16bit/mono
    private AudioRecord mAudioRecorder = null;
    private final String defaultSdkCode = "只有使用离线功能才需要填写";

    private Button startButton, cancelButton;
    private Switch mLoopSwitch, mSaveAudioSwitch;
    private TextView setView, kwsView, asrView, detailView;
    private EditText kwsEdit;

    private final String defaultWakupWord = "小云小云";
    private boolean mInit = false;
    private String mDebugPath = "";
    private String curTaskId = "";
    private LinkedBlockingQueue<byte[]> tmpAudioQueue = new LinkedBlockingQueue();
    private String mRecordingAudioFilePath = "";
    private OutputStream mRecordingAudioFile = null;
    private Handler mHandler;
    private HandlerThread mHanderThread;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wakeup_demo);

        String version = nui_instance.GetVersion();
        final String sdk_ver = Utils.extractVersion(version);
        Log.i(TAG, "current sdk version: " + version + " sdk_ver: " + sdk_ver);
        if (!sdk_ver.equals("029") && !sdk_ver.equals("015") && !sdk_ver.equals("039")) {
            final String version_text = "SDK版本:" + version + "不支持离线唤醒功能，请到官网下载对应SDK。";
            ToastText(version_text, Toast.LENGTH_LONG);
            Intent intent_bak = new Intent(WakeupAndSpeechRecognizerActivity.this, MainActivity.class);
            startActivity(intent_bak);
            finish();
            return;
        } else {
            final String version_text = "内部SDK版本号:" + version;
            ToastText(version_text, Toast.LENGTH_LONG);
        }

        // 获取传递的参数
        Intent intent = getIntent();
        if (intent != null) {
            g_appkey = intent.getStringExtra("appkey");
            g_token = intent.getStringExtra("token");
            g_sts_token = intent.getStringExtra("stsToken");
            g_ak = intent.getStringExtra("accessKey");
            g_sk = intent.getStringExtra("accessKeySecret");
            g_url = intent.getStringExtra("url");
            g_sdk_code = intent.getStringExtra("sdkCode");
            g_access_file = intent.getStringExtra("accessFile");

            Log.i(TAG, "Get access ->\n Appkey:" + g_appkey + "\n Token:" + g_token
                    + "\n AccessKey:" + g_ak + "\n AccessKeySecret:" + g_sk
                    + "\n SdkCode:" + g_sdk_code
                    + "\n STS_Token:" + g_sts_token
                    + "\n URL:" + g_url
                    + "\n AccessFile:" + g_access_file);
        }

        if (g_sdk_code.isEmpty() || g_sdk_code.equals(defaultSdkCode)) {
            ToastText("请设置SDK_CODE", Toast.LENGTH_LONG);
            Intent intent_bak = new Intent(WakeupAndSpeechRecognizerActivity.this, MainActivity.class);
            startActivity(intent_bak);
            finish();
            return;
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

    private void initUIWidgets() {
        setView = (TextView) findViewById(R.id.kws_set);
        kwsView = (TextView) findViewById(R.id.kws_text);
        kwsEdit = (EditText) findViewById(R.id.editKwsEdit);
        asrView = (TextView) findViewById(R.id.asr_text);
        detailView = (TextView) findViewById(R.id.detail_text);
        detailView.setMovementMethod(new ScrollingMovementMethod());

        startButton = (Button) findViewById(R.id.button_start);
        cancelButton = (Button) findViewById(R.id.button_cancel);

        mLoopSwitch = (Switch) findViewById(R.id.loop_switch);
        mLoopSwitch.setVisibility(View.VISIBLE);
        mLoopSwitch.setChecked(true);
        mSaveAudioSwitch = (Switch) findViewById(R.id.save_audio_switch);
        mSaveAudioSwitch.setVisibility(View.VISIBLE);

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "start!!!");

                setButtonState(startButton, false);
                setButtonState(cancelButton, true);
                showText(kwsView, "");
                showText(asrView, "");
                showText(detailView, "");
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
                        long ret = nui_instance.stopDialog();
                        Log.i(TAG, "cancel dialog " + ret + " end");
                    }
                });
            }
        });
    }

    private void doInit() {
        showText(setView, "自定义唤醒词，多个词用 ; 分割");
        showText(kwsEdit, defaultWakupWord);
        showText(kwsView, "唤醒事件");
        showText(asrView, "识别内容");
        showText(detailView, "详细");

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);

        //这里主动调用完成SDK配置文件的拷贝, 即将nuisdk.aar中assets中资源文件拷贝到cache目录
        if (CommonUtils.copyAssetsData(this)) {
            Log.i(TAG, "copy assets data done");
        } else {
            Log.e(TAG, "copy assets failed");
            return;
        }

        //如果需要使用外置的唤醒模型，把文件放在assets存储的cache目录，通过以下接口设置
//        CommonUtils.setExternalAssetFile(this, "pack_kws.bin");

        //获取工作路径, 即获得拷贝后资源文件存储的cache路径, 作为workspace
        String asset_path = CommonUtils.getModelPath(this);
        Log.i(TAG, "use workspace " + asset_path);

        mDebugPath = getExternalCacheDir().getAbsolutePath() + "/debug";
        Utils.createDir(mDebugPath);

        //初始化SDK，注意用户需要在Auth.getAliYunTicket中填入相关ID信息才可以使用。
        //由于唤醒功能为本地功能, 涉及鉴权, 故genInitParams中需要填写ak_id、ak_secret
        int ret = nui_instance.initialize(this, genInitParams(asset_path, mDebugPath),
                Constants.LogLevel.LOG_LEVEL_VERBOSE, true);
        Log.i(TAG, "result = " + ret);
        if (ret == Constants.NuiResultCode.SUCCESS) {
            mInit = true;
        } else {
            final String msg_text = Utils.getMsgWithErrorCode(ret, "init");
            ToastText(msg_text, Toast.LENGTH_LONG);
        }
    }

    private String genParams() {
        String params = "";
        try {
            JSONObject nls_config = new JSONObject();

            // 是否返回中间识别结果，默认值：False。
            nls_config.put("enable_intermediate_result", true);
            // 是否在后处理中添加标点，默认值：False。
            nls_config.put("enable_punctuation_prediction", true);

            //参数可根据实际业务进行配置
//            nls_config.put("enable_inverse_text_normalization", true);
//            nls_config.put("enable_voice_detection", true);
//            nls_config.put("customization_id", "test_id");
//            nls_config.put("vocabulary_id", "test_id");
//            nls_config.put("max_start_silence", 10000);
//            nls_config.put("max_end_silence", 800);
            nls_config.put("sample_rate", SAMPLE_RATE);
//            nls_config.put("sr_format", "opus");

            /*若文档中不包含某些参数，但是此功能支持这个参数，可以用如下万能接口设置参数*/
//            JSONObject extend_config = new JSONObject();
//            extend_config.put("custom_test", true);
//            nls_config.put("extend_config", extend_config);

            JSONObject parameters = new JSONObject();

            parameters.put("nls_config", nls_config);
            parameters.put("service_type", Constants.kServiceTypeASR);

            //可以通过以下方式设置自定义唤醒词进行体验，如果需要更好的唤醒效果请进行唤醒词定制
            //注意：动态唤醒词只有在设置了唤醒模型的前提下才可以使用
            JSONArray dynamic_wuw = new JSONArray();
            String[] allWakeupWords = SplitWakupWords(kwsEdit.getText().toString());
            if (allWakeupWords.length == 0) {
                JSONObject wuw = new JSONObject();
                wuw.put("name", "小白小白");
                wuw.put("type", "main");
                dynamic_wuw.add(wuw);
            } else {
                for (String part : allWakeupWords) {
                    JSONObject wuw = new JSONObject();
                    wuw.put("name", part.trim());
                    wuw.put("type", "main");
                    dynamic_wuw.add(wuw);
                }
            }
            parameters.put("wuw", dynamic_wuw);

            //如果有HttpDns则可进行设置
//            parameters.put("direct_ip", Utils.getDirectIp());
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
                mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        WAVE_FRAM_SIZE * 4);
            } else {
                Log.w(TAG, "AudioRecord has been new ...");
            }
        } else {
            Log.e(TAG, "donnot get RECORD_AUDIO permission!");
            ToastText("未获得录音权限，无法正常运行。请通过设置界面重新开启权限。", Toast.LENGTH_LONG);
            showText(asrView, "未获得录音权限，无法正常运行。通过设置界面重新开启权限。");
            return false;
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //设置相关识别参数，具体参考API文档
                nui_instance.setParams(genParams());
                //唤醒后进行识别模式
                int ret = nui_instance.startDialog(
                        Constants.VadMode.TYPE_KWS, genDialogParams());
                Log.i(TAG, "start done with " + ret);
                if (ret != 0) {
                    final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
                    ToastText(msg_text, Toast.LENGTH_LONG);
                } else {
                    showText(kwsView, "等待唤醒 ...");
                }
            }
        });

        return true;
    }

    private String genInitParams(String workpath, String debugpath) {
        String str = "";
        try {
            // 需要特别注意：ak_id/ak_secret/app_key/sdk_code/device_id等参数必须传入SDK
            // 离线语音合成sdk_code取值：精品版为software_nls_tts_offline， 标准版为software_nls_tts_offline_standard
            // 离线语音合成账户的sdk_code也可用于唤醒
            // 鉴权信息获取参：https://help.aliyun.com/document_detail/69835.htm

            //获取账号访问凭证：
            // 注意！此activity是唤醒+一句话识别的功能演示，包含离线唤醒功能和在线识别功能，
            // 所以账号方案需要mixed方案，或者创建两个Appkey分别用户唤醒功能和在线功能。
            Auth.GetTicketMethod method = Auth.GetTicketMethod.GET_STS_ACCESS_FROM_SERVER_FOR_MIXED_FEATURES;
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
            Auth.setSdkCode(g_sdk_code);
            // 此处展示将用户传入账号信息进行交互，实际产品不可以将任何账号信息存储在端侧
            if (!g_appkey.isEmpty()) {
                if (!g_ak.isEmpty() && !g_sk.isEmpty()) {
                    if (g_sts_token.isEmpty()) {
                        method = Auth.GetTicketMethod.GET_ACCESS_IN_CLIENT_FOR_MIXED_FEATURES;
                    } else {
                        method = Auth.GetTicketMethod.GET_STS_ACCESS_IN_CLIENT_FOR_MIXED_FEATURES;
                    }
                }
            }
            Log.i(TAG, "Use method:" + method);
            JSONObject object = Auth.getTicket(method);
            String ak_secret = object.getString("ak_secret");
            if (ak_secret.equals("")) {
                // 如果接口没有设置鉴权信息，尝试从本地鉴权文件加载（方便测试人员多账号验证）
                object = null;
                // 假设本地有存了鉴权信息的文件, 注意账号安全
                String fileName = "/sdcard/idst/auth.txt";
                if (Utils.isExist(fileName)) {
                    object = Auth.getTicketFromJsonFile(fileName);
                }
                if (object == null) {
                    ToastText("无法获取有效鉴权信息，请检查账号信息ak_id和ak_secret. 或者将鉴权信息以json格式保存至本地文件(/sdcard/idst/auth.txt)",
                            Toast.LENGTH_LONG);
                    return null;
                }
            }

            if (g_url.isEmpty()) {
                g_url = "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1"; // 默认
            }
            object.put("url", g_url);

            //工作目录路径，SDK从该路径读取配置文件
            object.put("workspace", workpath); // 必填
            object.put("debug_path", debugpath);

            //过滤SDK内部日志通过回调送回到用户层
            object.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_INFO)));

            // FullMix = 0   // 选用此模式开启本地功能并需要进行鉴权注册
            // FullCloud = 1
            // FullLocal = 2 // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrMix = 3    // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrCloud = 4
            // AsrLocal = 5  // 选用此模式开启本地功能并需要进行鉴权注册
            object.put("service_mode", Constants.ModeFullMix);

            // 特别说明: 鉴权所用的id是由device_id，与手机内部的一些唯一码进行组合加密生成的。
            //   更换手机或者更换device_id都会导致重新鉴权计费。
            //   此外, 以下device_id请设置有意义且具有唯一性的id, 比如用户账号(手机号、IMEI等),
            //   传入相同或随机变换的device_id会导致鉴权失败或重复收费。
            //   Utils.getDeviceId() 并不能保证生成不变的device_id，请不要使用
            object.put("device_id", "empty_device_id"); // 必填, 推荐填入具有唯一性的id, 方便定位问题。


            //如果使用外置唤醒资源，可以进行设置文件路径。通过upgrade_file参数传入唤醒模型文件的绝对路径。

            // 举例1：模型文件kws.bin可以放在assets，这里需要主动拷贝到App的data目录，并获得绝对路径。
            String kws_bin_name = "kws.bin";
            String kws_bin_dest_name = CommonUtils.getModelPath(this) + "/" + kws_bin_name;
            CommonUtils.copyAsset(this, kws_bin_name, kws_bin_dest_name);

            // 举例2：模型文件kws.bin放在/sdcard/目录，请确认读写权限
//            kws_bin_name = "kws.bin";
//            String kws_bin_dest_name = "/sdcard/" + kws_bin_name;

            object.put("upgrade_file", kws_bin_dest_name);
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
            JSONObject dialog_param = new JSONObject();
            // 运行过程中可以在startDialog时更新临时参数，尤其是更新过期token
            // 注意: 若下一轮对话不再设置参数，则继续使用初始化时传入的参数
            long distance_expire_time_5m = 300;
            dialog_param = Auth.refreshTokenIfNeed(dialog_param, distance_expire_time_5m);

            // 注意: 若需要更换appkey和token，可以直接传入参数
//            dialog_param.put("app_key", "");
//            dialog_param.put("token", "");
            params = dialog_param.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "dialog params: " + params);
        return params;
    }

    private boolean checkNotInitToast() {
        if (!mInit) {
            ToastText("SDK未正确初始化.", Toast.LENGTH_LONG);
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

    private void appendText(final TextView who, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "append text=" + text);
                if (TextUtils.isEmpty(text)) {
                    return;
                } else {
                    String orign = who.getText().toString();
                    who.setText(text + "\n---\n" + orign);
                }
            }
        });
    }

    private void showText(final TextView who, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "showText text=" + text);
                if (TextUtils.isEmpty(text)) {
                    who.setText("");
                } else {
                    who.setText(text);
                }
            }
        });
    }

    private void ToastText(String text, int length) {
        final String str = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(WakeupAndSpeechRecognizerActivity.this, str, length).show();
            }
        });
    }

    private String[] SplitWakupWords(String wakeup) {
        String[] parts = wakeup.split("[;；]");
        return parts;
    }

    //当回调事件发生时调用
    @Override
    public void onNuiEventCallback(Constants.NuiEvent event, final int resultCode,
                                   final int arg2, KwsResult kwsResult,
                                   AsrResult asrResult) {
        Log.i(TAG, "event=" + event);
        if (event == Constants.NuiEvent.EVENT_WUW_TRUSTED) {
            JSONObject jsonObject = JSON.parseObject(kwsResult.kws);
            String result = jsonObject.getString("word");
            if (!result.isEmpty()) {
                showText(kwsView,"激活词: " + result);
            }
            appendText(detailView, "EVENT_WUW_TRUSTED");
        } else if (event == Constants.NuiEvent.EVENT_ASR_RESULT) {
            JSONObject jsonObject = JSON.parseObject(asrResult.allResponse);
            JSONObject payload = jsonObject.getJSONObject("payload");
            String result = payload.getString("result");
            if (!result.isEmpty()) {
                showText(asrView, result);
            }
            appendText(detailView, asrResult.asrResult);

            // 获得task_id，并保存音频
            if (mSaveAudioSwitch.isChecked()) {
                JSONObject header = jsonObject.getJSONObject("header");
                curTaskId = header.getString("task_id");
                if (!curTaskId.isEmpty() && tmpAudioQueue.size() > 0) {
                    try {
                        mRecordingAudioFilePath = mDebugPath + "/" + "wakeupSR_task_id_" + curTaskId + ".pcm";
                        Log.i(TAG, "save recorder data into " + mRecordingAudioFilePath);
                        mRecordingAudioFile = new FileOutputStream(mRecordingAudioFilePath, true);
                        try {
                            byte[] audioData = tmpAudioQueue.take();
                            try {
                                mRecordingAudioFile.write(audioData);
                                mRecordingAudioFile.close();
                                mRecordingAudioFile = null;
                                String show = "存储录音音频到 " + mRecordingAudioFilePath;
                                appendText(detailView, show);
                                ToastText(show, Toast.LENGTH_SHORT);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                curTaskId = "";
                mRecordingAudioFilePath = "";
                tmpAudioQueue.clear();
            }

            if (mLoopSwitch.isChecked()) {
                showText(kwsView, "等待唤醒 ...");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        nui_instance.startDialog(Constants.VadMode.TYPE_KWS, genDialogParams());
                    }
                });
            } else {
                showText(kwsView,"");
                setButtonState(startButton, true);
                setButtonState(cancelButton, false);
            }
        } else if (event == Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT) {
            JSONObject jsonObject = JSON.parseObject(asrResult.allResponse);
            JSONObject payload = jsonObject.getJSONObject("payload");
            String result = payload.getString("result");
            if (!result.isEmpty()) {
                showText(asrView, result);
            }
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR ||
                event == Constants.NuiEvent.EVENT_MIC_ERROR) {
            // asrResult在EVENT_ASR_ERROR中为错误信息，搭配错误码resultCode和其中的task_id更易排查问题，请用户进行记录保存。
            appendText(detailView, asrResult.asrResult);

            ToastText("ERROR with " + resultCode, Toast.LENGTH_SHORT);
            final String msg_text = Utils.getMsgWithErrorCode(resultCode, "start");
            ToastText(msg_text, Toast.LENGTH_SHORT);

            if (tmpAudioQueue.size() > 0) {
                tmpAudioQueue.clear();
            }

            setButtonState(startButton, true);
            setButtonState(cancelButton, false);

            if (event == Constants.NuiEvent.EVENT_MIC_ERROR) {
                // EVENT_MIC_ERROR表示2s未传入音频数据，请检查录音相关代码、权限或录音模块是否被其他应用占用。
                // 此处也可重新启动录音模块
            }
        } else if (event == Constants.NuiEvent.EVENT_DIALOG_EX) { /* unused */
            Log.i(TAG, "dialog extra message = " + asrResult.asrResult);
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

        // 音频存储到缓存
        if (mSaveAudioSwitch.isChecked() && audio_size > 0) {
            tmpAudioQueue.offer(buffer);
        }

        return audio_size;
    }

    //当录音状态发送变化的时候调用
    @Override
    public void onNuiAudioStateChanged(Constants.AudioState state) {
        Log.i(TAG, "onNuiAudioStateChanged");
        if (state == Constants.AudioState.STATE_OPEN) {
            Log.i(TAG, "audio recorder start");
            appendText(detailView, "RECORDER STATE_OPEN");
            if (mAudioRecorder != null) {
                mAudioRecorder.startRecording();
            }
            Log.i(TAG, "audio recorder start done");
        } else if (state == Constants.AudioState.STATE_CLOSE) {
            Log.i(TAG, "audio recorder close");
            appendText(detailView, "RECORDER STATE_CLOSE");
            if (mAudioRecorder != null) {
                mAudioRecorder.release();
            }
        } else if (state == Constants.AudioState.STATE_PAUSE) {
            Log.i(TAG, "audio recorder pause");
            appendText(detailView, "RECORDER STATE_PAUSE");
            if (mAudioRecorder != null) {
                mAudioRecorder.stop();
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
        Log.i(TAG, "onNuiLogTrackCallback log level:" + level + ", message -> " + log);
    }
}


