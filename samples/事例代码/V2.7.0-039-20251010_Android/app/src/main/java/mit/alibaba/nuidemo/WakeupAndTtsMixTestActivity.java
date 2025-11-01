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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.*;

import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeNuiCallback;
import com.alibaba.idst.nui.INativeTtsCallback;
import com.alibaba.idst.nui.KwsResult;
import com.alibaba.idst.nui.NativeNui;

import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// 本样例展示离线语音合成和在线语音合成等多实例的使用方法
// 离线唤醒:
//   Android SDK 详细说明：https://help.aliyun.com/zh/isi/sdk-for-android-1
//   Android SDK 接口说明：https://help.aliyun.com/zh/isi/sdk-reference-12
//   语音唤醒FAQ: https://help.aliyun.com/zh/isi/faq-about-wake-up-word-recognition
// 离线语音合成:
//   Android SDK 详细说明：https://help.aliyun.com/document_detail/204187.html
//   Android SDK 接口说明：https://help.aliyun.com/document_detail/204185.html
// 在线语音合成:
//   Android SDK 详细说明：https://help.aliyun.com/zh/isi/developer-reference/nui-sdk-for-android-2
//   Android SDK 接口说明：https://help.aliyun.com/zh/isi/developer-reference/overview-5
public class WakeupAndTtsMixTestActivity extends Activity implements View.OnClickListener, INativeNuiCallback {
    private static final String TAG = "MixTestActivity";

    // 本示例模拟两个部门(old和new)同时使用此SDK进行多实例操作, 分别同时(随机)初始化
    NativeNui old_wakeup_nui_instance = null;
    NativeNui old_sr_nui_instance = null;
    NativeNui old_local_tts_instance = null;
    NativeNui new_wakeup_nui_instance = null;
    NativeNui new_local_tts_instance = null;
    NativeNui new_cloud_tts_instance = null;
    NativeNui new_stream_input_tts_instance = new NativeNui(Constants.ModeType.MODE_STREAM_INPUT_TTS);

    // 假设离线功能和在线功能使用了2个账号, 其中离线信息由MainActivity传入, 在线的账号自行填入, 单纯为了测试
    private String g_cloud_appkey = "xxxxx";
    private String g_cloud_token = "xxxxx";
    private String g_local_appkey = "";
    private String g_sts_token = "";
    private String g_local_ak = "";
    private String g_local_sk = "";
    private String g_url = "";
    private String g_sdk_code = "software_nls_tts_offline_standard"; // 精品版为software_nls_tts_offline， 标准版为software_nls_tts_offline_standard
    private String g_access_file = "";

    private final boolean mSaveLog = true;
    private final String defaultSdkCode = "只有使用离线功能才需要填写";
    private String mDebugRootPath = "";  // 可以任意设置, 但是注意路径权限
    private String mDebugPath = "";  // demo音频文件存储路径，可以任意设置, 但是注意路径权限
    private String mAssetPath;   // SDK工作路径，该目录下含有配置文件及TTS资源，即workspace
    private Handler mHandler;
    private HandlerThread mHanderThread;

    private boolean mOldWakeupInitialized = false;
    private boolean mOldWakeupStarted = false;
    private boolean mOldSrInitialized = false;
    private boolean mOldLocalTtsInitialized = false;
    private boolean mNewWakeupInitialized = false;
    private boolean mNewWakeupStarted = false;
    private boolean mNewLocalTtsInitialized = false;
    private boolean mNewCloudTtsInitialized = false;
    private boolean mNewStreamTtsStarted = false;


    // 关于唤醒和识别
    private Button startButton, cancelButton;
    private TextView setView, kwsView, asrView, detailView;
    private EditText kwsEdit;

    private final String defaultWakupWord = "小云小云";
    private int wakeupCount = 0;
    private final int SAMPLE_RATE = 16000;
    private final int WAVE_FRAM_SIZE = 20 * 2 * 1 * SAMPLE_RATE / 1000; //20ms audio for 16k/16bit/mono
    private AudioRecord mAudioRecorder = null;
    private LinkedBlockingQueue<byte[]> tmpAudioQueue = new LinkedBlockingQueue();
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    // 关于TTS
    private final String mEncodeType = "pcm";
    private final String mFontName = "aijia";
    private String mFontNameDefaultRootPath;
    private final String CN_PREVIEW = "本样例展示离线语音合成使用方法，" +
            "\n1）设置鉴权信息：按照鉴权认证文档获取注册信息，并调用接口tts_initialize进行设置；\n" +
            "2）配置语音包：将购买语音包推到app的cache或任意可以读写的路径下，要确保SDK有读的权限；\n" +
            "3）开始合成";

    // 播报模块示例：展示合成音频播放
    // AudioPlayer默认采样率是16000
    private AudioPlayer mAudioTrack = new AudioPlayer(new AudioPlayerCallback() {
        @Override
        public void playStart() {
            Log.i(TAG, "playStart");
        }
        @Override
        public void playOver() { Log.i(TAG, "playOver"); }
        @Override
        public void playSoundLevel(int level) {}
    });

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_mix_test);

        String version = new_stream_input_tts_instance.GetVersion();
        final String sdk_ver = Utils.extractVersion(version);
        Log.i(TAG, "current sdk version: " + version + " sdk_ver: " + sdk_ver);
        if (!sdk_ver.equals("029") && !sdk_ver.equals("015")) {
            final String version_text = "SDK版本:" + version + "不支持离线语音合成功能，请到官网下载对应SDK。";
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(WakeupAndTtsMixTestActivity.this,
                            version_text, Toast.LENGTH_LONG).show();
                }
            });
            Intent intent_bak = new Intent(WakeupAndTtsMixTestActivity.this, MainActivity.class);
            startActivity(intent_bak);
            finish();
            return;
        } else {
            final String version_text = "内部SDK版本号:" + version;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(WakeupAndTtsMixTestActivity.this,
                            version_text, Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 获取传递的参数
        Intent intent = getIntent();
        String cloud_token = "";
        if (intent != null) {
            g_local_appkey = intent.getStringExtra("appkey");
            cloud_token = intent.getStringExtra("token");
            g_sts_token = intent.getStringExtra("stsToken");
            g_local_ak = intent.getStringExtra("accessKey");
            g_local_sk = intent.getStringExtra("accessKeySecret");
            g_url = intent.getStringExtra("url");
            g_sdk_code = intent.getStringExtra("sdkCode");
            g_access_file = intent.getStringExtra("accessFile");

            Log.i(TAG, "Get access ->\n Appkey:" + g_local_appkey + "\n Token:" + g_cloud_token
                    + "\n AccessKey:" + g_local_ak + "\n AccessKeySecret:" + g_local_sk
                    + "\n SdkCode:" + g_sdk_code
                    + "\n STS_Token:" + g_sts_token
                    + "\n URL:" + g_url
                    + "\n AccessFile:" + g_access_file);
        }

        if (g_cloud_appkey.isEmpty()) {
            g_cloud_appkey = g_local_appkey;
        }
        if (!cloud_token.isEmpty()) {
            g_cloud_token = cloud_token;
        }

        if (g_sdk_code.isEmpty() || g_sdk_code.equals(defaultSdkCode)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(WakeupAndTtsMixTestActivity.this,
                            "请设置SDK_CODE", Toast.LENGTH_LONG).show();
                }
            });
            Intent intent_bak = new Intent(WakeupAndTtsMixTestActivity.this, MainActivity.class);
            startActivity(intent_bak);
            finish();
            return;
        }

        //设置默认目的地路径最下级目录名字
        // 比如设置当前 /data/user/0/mit.alibaba.nuidemo/files/asr_my
        // 未调用此接口, 则默认为 /data/user/0/mit.alibaba.nuidemo/files/asr_my
        CommonUtils.setTargetDataDir("asr_my");

        //获取工作路径, 即获得拷贝后资源文件存储的cache路径, 作为workspace
        // 比如 /data/user/0/mit.alibaba.nuidemo/files/asr_my
        mAssetPath = CommonUtils.getModelPath(this);
        Log.i(TAG, "workpath:" + mAssetPath);

        //这里主动调用完成SDK配置文件的拷贝, 即将nuisdk.aar中assets中资源文件拷贝到cache目录
        // 比如当前为 /data/user/0/mit.alibaba.nuidemo/files/asr_my
        if (CommonUtils.copyTtsAssetsDataAndCover(this)) {
            Log.i(TAG, "copy assets data done");
        } else {
            Log.e(TAG, "copy assets failed");
            ToastText("从aar的assets拷贝资源文件失败, 请检查资源文件是否存在, 详情可通过日志确认。");
        }
        // 注意: 鉴权文件为 /data/user/0/mit.alibaba.nuidemo/files/asr_my/tts/tdata.bin
        // 切勿覆盖和删除asr_my内文件

        // mFontNameDefaultRootPath : /data/user/0/mit.alibaba.nuidemo/files/asr_my/tts/voices/
        mFontNameDefaultRootPath = mAssetPath + "/tts/voices/";
        // mDebugRootPath : /storage/emulated/0/Android/data/mit.alibaba.nuidemo/cache/
        mDebugRootPath = getExternalCacheDir().getAbsolutePath();
        mDebugPath = mDebugRootPath + "/debug";
        Utils.createDir(mDebugPath);

        initUIWidgets();

        mHanderThread = new HandlerThread("process_thread");
        mHanderThread.start();
        mHandler = new Handler(mHanderThread.getLooper());
    }

    @Override
    protected void onDestroy() {
        Log.w(TAG, "onDestroy ->");
        super.onDestroy();
        mAudioTrack.stop();
        mAudioTrack.releaseAudioTrack();
        if (old_local_tts_instance != null) {
            old_local_tts_instance.tts_release();
        }
        if (new_local_tts_instance != null) {
            new_local_tts_instance.tts_release();
        }
        if (new_cloud_tts_instance != null) {
            new_cloud_tts_instance.tts_release();
        }
    }

    private void initUIWidgets() {
        setView = (TextView) findViewById(R.id.kws_set);
        kwsView = (TextView) findViewById(R.id.kws_text);
        kwsEdit = (EditText) findViewById(R.id.editKwsEdit);
        asrView = (TextView) findViewById(R.id.asr_text);
        detailView = (TextView) findViewById(R.id.detail_text);
        detailView.setMovementMethod(new ScrollingMovementMethod());

        showText(kwsEdit, defaultWakupWord);

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
                showText(kwsView, "");
                showText(asrView, "");
                showText(detailView, "");

                if (mOldWakeupInitialized == false) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            int randomSleepTime = new Random().nextInt(100);
                            try {
                                Thread.sleep(randomSleepTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            if (old_wakeup_nui_instance == null) {
                                Log.i(TAG, "new old_wakeup_nui_instance ->");
                                old_wakeup_nui_instance = new NativeNui();
                            }
                            int ret = LocalWakeupInitialize(old_wakeup_nui_instance, mAssetPath, mDebugPath);
                            if (ret != Constants.NuiResultCode.SUCCESS) {
                                mOldWakeupInitialized = false;
                            } else {
                                mOldWakeupInitialized = true;
                            }
                        }
                    }).start();
                }

                if (mOldLocalTtsInitialized == false) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            int randomSleepTime = new Random().nextInt(100);
                            try {
                                Thread.sleep(randomSleepTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            if (old_local_tts_instance == null) {
                                Log.i(TAG, "new old_local_tts_instance ->");
                                old_local_tts_instance = new NativeNui(Constants.ModeType.MODE_TTS);
                            }
                            int ret = LocalTtsInitialize(old_local_tts_instance, mAssetPath, mDebugPath);
                            if (ret != Constants.NuiResultCode.SUCCESS) {
                                mOldLocalTtsInitialized = false;
                            } else {
                                mOldLocalTtsInitialized = true;
                            }
                        }
                    }).start();
                }

                if (mNewWakeupInitialized == false) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            int randomSleepTime = new Random().nextInt(100);
                            try {
                                Thread.sleep(randomSleepTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            if (new_wakeup_nui_instance == null) {
                                Log.i(TAG, "new new_wakeup_nui_instance ->");
                                new_wakeup_nui_instance = new NativeNui();
                            }
                            int ret = LocalWakeupInitialize(new_wakeup_nui_instance, mAssetPath, mDebugPath);
                            if (ret != Constants.NuiResultCode.SUCCESS) {
                                mNewWakeupInitialized = false;
                            } else {
                                mNewWakeupInitialized = true;
                            }
                        }
                    }).start();
                }

                if (mNewLocalTtsInitialized == false) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            int randomSleepTime = new Random().nextInt(100);
                            try {
                                Thread.sleep(randomSleepTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            if (new_local_tts_instance == null) {
                                Log.i(TAG, "new new_local_tts_instance ->");
                                new_local_tts_instance = new NativeNui(Constants.ModeType.MODE_TTS);
                            }
                            int ret = LocalTtsInitialize(new_local_tts_instance, mAssetPath, mDebugPath);
                            if (ret != Constants.NuiResultCode.SUCCESS) {
                                mNewLocalTtsInitialized = false;
                            } else {
                                mNewLocalTtsInitialized = true;
                            }
                        }
                    }).start();
                }

                if (mNewCloudTtsInitialized == false) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            int randomSleepTime = new Random().nextInt(100);
                            try {
                                Thread.sleep(randomSleepTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            if (new_cloud_tts_instance == null) {
                                Log.i(TAG, "new new_cloud_tts_instance ->");
                                new_cloud_tts_instance = new NativeNui(Constants.ModeType.MODE_TTS);
                            }
                            int ret = CloudTtsInitialize(new_cloud_tts_instance, mDebugPath);
                            if (ret != Constants.NuiResultCode.SUCCESS) {
                                mNewCloudTtsInitialized = false;
                            } else {
                                mNewCloudTtsInitialized = true;
                            }
                        }
                    }).start();
                }

                // 等2个实例中至少一个初始化完成
                while (mOldWakeupInitialized == false && mNewWakeupInitialized == false) {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // 用其中一个进行交互
                if (mOldWakeupInitialized) {
                    if (mOldWakeupStarted == false) {
                        mOldWakeupStarted = startDialog(old_wakeup_nui_instance, Constants.VadMode.TYPE_ONLY_KWS);
                    }
                } else {
                    if (mNewWakeupStarted == false) {
                        mNewWakeupStarted = startDialog(new_wakeup_nui_instance, Constants.VadMode.TYPE_ONLY_KWS);
                    }
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "cancel");

                setButtonState(startButton, true);
                setButtonState(cancelButton, false);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mOldWakeupInitialized) {
                            if (mOldWakeupStarted) {
                                old_wakeup_nui_instance.stopDialog();
                                mOldWakeupStarted = false;
                            }
                            old_wakeup_nui_instance.release();
                            mOldWakeupInitialized = false;
                        }
                        if (mNewWakeupInitialized) {
                            if (mNewWakeupStarted) {
                                new_wakeup_nui_instance.stopDialog();
                                mNewWakeupStarted = false;
                            }
                            new_wakeup_nui_instance.release();
                            mNewWakeupInitialized = false;
                        }

                        if (mOldSrInitialized) {
                            old_sr_nui_instance.stopDialog();
                            old_sr_nui_instance.release();
                            mOldSrInitialized = false;
                        }

                        if (mOldLocalTtsInitialized) {
                            old_local_tts_instance.tts_release();
                            mOldLocalTtsInitialized = false;
                        }
                        if (mNewLocalTtsInitialized) {
                            new_local_tts_instance.tts_release();
                            mNewLocalTtsInitialized = false;
                        }
                        if (mNewCloudTtsInitialized) {
                            new_cloud_tts_instance.tts_release();
                            mNewCloudTtsInitialized = false;
                        }
                        if (mNewStreamTtsStarted) {
                            new_stream_input_tts_instance.cancelStreamInputTts();
                            mNewStreamTtsStarted = false;
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onClick(View v) {}

    private String genRecognizerParams() {
        String params = "";
        try {
            JSONObject nls_config = new JSONObject();

            //参数可根据实际业务进行配置
            //接口说明可见: https://help.aliyun.com/document_detail/173298.html
            //查看 2.开始识别

            // 是否返回中间识别结果，默认值：False。
            nls_config.put("enable_intermediate_result", true);
            // 是否在后处理中添加标点，默认值：False。
            nls_config.put("enable_punctuation_prediction", true);

            nls_config.put("sample_rate", SAMPLE_RATE);
            nls_config.put("sr_format", "opus");

            nls_config.put("enable_voice_detection", true);
            nls_config.put("max_start_silence", 10000);
            nls_config.put("max_end_silence", 800);

            JSONObject parameters = new JSONObject();

            parameters.put("nls_config", nls_config);
            parameters.put("service_type", Constants.kServiceTypeASR); // 必填

            params = parameters.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    private String genWakeupParams() {
        String params = "";
        try {
            // 纯唤醒模式无需传入参数
            JSONObject nls_config = new JSONObject();
            JSONObject parameters = new JSONObject();

            parameters.put("nls_config", nls_config);
            parameters.put("service_type", Constants.kServiceTypeASR);

            params = parameters.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    private String genWakeupDialogParams() {
        String params = "";
        try {
            // 纯唤醒模式无需传入参数
            JSONObject dialog_param = new JSONObject();
            dialog_param.put("single_round", true); // 唤醒一次就停止
            params = dialog_param.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    private String genDialogParams() {
        String params = "";
        try {
            // 纯唤醒模式无需传入参数
            JSONObject dialog_param = new JSONObject();
            params = dialog_param.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "dialog params: " + params);
        return params;
    }

    private boolean startDialog(NativeNui cur_instance, Constants.VadMode mode) {
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
                int ret = Constants.NuiResultCode.SUCCESS;

                if (mode == Constants.VadMode.TYPE_ONLY_KWS || mode == Constants.VadMode.TYPE_KWS) {
                    //设置相关识别参数，具体参考API文档
                    cur_instance.setParams(genWakeupParams());
                    ret = cur_instance.startDialog(mode, genWakeupDialogParams());
                } else {
                    cur_instance.setParams(genRecognizerParams());
                    ret = cur_instance.startDialog(mode, genDialogParams());
                }

                Log.i(TAG, "start " + mode + " done with " + ret);
                if (ret != Constants.NuiResultCode.SUCCESS) {
                    final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
                    ToastText(msg_text, Toast.LENGTH_LONG);
                } else {
                    if (mode == Constants.VadMode.TYPE_ONLY_KWS || mode == Constants.VadMode.TYPE_KWS) {
                        showText(kwsView, "等待唤醒 ...");
                    }
                }
            }
        });

        return true;
    }

    private String genLocalWakeupInitParams(String workpath, String debugpath) {
        String str = "";
        try {
            // 需要特别注意：ak_id/ak_secret/app_key/sdk_code/device_id等参数必须传入SDK
            // 离线语音合成sdk_code取值：精品版为software_nls_tts_offline， 标准版为software_nls_tts_offline_standard
            // 离线语音合成账户的sdk_code也可用于唤醒
            // 鉴权信息获取参：https://help.aliyun.com/document_detail/69835.htm

            //获取账号访问凭证：
            Auth.GetTicketMethod method = Auth.GetTicketMethod.GET_ACCESS_IN_CLIENT_FOR_OFFLINE_FEATURES;
            if (!g_local_appkey.isEmpty()) {
                Auth.setAppKey(g_local_appkey);
            }
            if (!g_local_ak.isEmpty()) {
                Auth.setAccessKey(g_local_ak);
            }
            if (!g_local_sk.isEmpty()) {
                Auth.setAccessKeySecret(g_local_sk);
            }
            Auth.setSdkCode(g_sdk_code);

            Log.i(TAG, "Use method:" + method);
            JSONObject object = Auth.getTicket(method);

            if (g_url.isEmpty()) {
                g_url = "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1"; // 默认
            }
            object.put("url", g_url);

            //工作目录路径，SDK从该路径读取配置文件
            object.put("workspace", workpath); // 必填
            object.put("debug_path", debugpath);

            //过滤SDK内部日志通过回调送回到用户层
            object.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_INFO)));

            // FullLocal = 2 // 选用此模式开启本地功能并需要进行鉴权注册
            object.put("service_mode", Constants.ModeFullLocal); // 必填

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

            object.put("upgrade_file", kws_bin_dest_name);
            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 注意! str中包含ak_id ak_secret token app_key等敏感信息, 实际产品中请勿在Log中输出这类信息！
        Log.i(TAG, "Local wakeup insideUserContext:" + str);
        return str;
    }

    private int LocalWakeupInitialize(NativeNui cur_instance, String workspace_path, String debug_path) {
        //初始化SDK，注意用户需要在Auth.getAliYunTicket中填入相关ID信息才可以使用。
        //由于唤醒功能为本地功能, 涉及鉴权, 故genLocalWakeupInitParams中需要填写ak_id、ak_secret
        Log.i(TAG, "LocalWakeupInitialize ->");
        int ret = cur_instance.initialize(this, genLocalWakeupInitParams(workspace_path, debug_path),
                Constants.LogLevel.LOG_LEVEL_VERBOSE, true);
        Log.i(TAG, "LocalWakeupInitialize result = " + ret);
        if (ret != Constants.NuiResultCode.SUCCESS) {
            final String msg_text = Utils.getMsgWithErrorCode(ret, "init");
            ToastText(msg_text, Toast.LENGTH_LONG);
        }

        return ret;
    }

    private String genCloudRecognizerInitParams(String debugpath) {
        String str = "";
        try{
            //获取账号访问凭证：
            JSONObject object = new JSONObject();
            object.put("app_key", g_cloud_appkey);
            object.put("token", g_cloud_token);

            object.put("device_id", "empty_device_id"); // 必填, 推荐填入具有唯一性的id, 方便定位问题
            if (g_url.isEmpty()) {
                g_url = "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1"; // 默认
            }
            object.put("url", g_url);

            //当初始化SDK时的save_log参数取值为true时，该参数生效。表示是否保存音频debug，该数据保存在debug目录中，需要确保debug_path有效可写。
            object.put("save_wav", "true");
            //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存中间音频文件。
            object.put("debug_path", debugpath);

            //过滤SDK内部日志通过回调送回到用户层
            object.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_INFO)));

            // AsrCloud = 4
            object.put("service_mode", Constants.ModeAsrCloud); // 必填
            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 注意! str中包含ak_id ak_secret token app_key等敏感信息, 实际产品中请勿在Log中输出这类信息！
        Log.i(TAG, "Cloud recognizer insideUserContext:" + str);
        return str;
    }

    private int CloudRecognizerInitialize(NativeNui cur_instance, String debug_path) {
        //初始化SDK，注意用户需要在Auth.getTicket中填入相关ID信息才可以使用。
        Log.i(TAG, "CloudRecognizerInitialize ->");
        int ret = cur_instance.initialize(this, genCloudRecognizerInitParams(debug_path),
                Constants.LogLevel.LOG_LEVEL_VERBOSE, true);
        Log.i(TAG, "CloudRecognizerInitialize result = " + ret);
        if (ret != Constants.NuiResultCode.SUCCESS) {
            final String msg_text = Utils.getMsgWithErrorCode(ret, "init");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(WakeupAndTtsMixTestActivity.this,
                            msg_text, Toast.LENGTH_SHORT).show();
                }
            });
        }
        return ret;
    }

    private void UpdateAudioPlayerSampleRate(NativeNui cur_instance) {
        // 获取当前模型采样率
        String samplerate_s = cur_instance.getparamTts("model_sample_rate");
        if (samplerate_s != null && mAudioTrack != null) {
            mAudioTrack.setSampleRate(Integer.parseInt(samplerate_s));
        }
    }

    private String genLocalTtsInitParams(String workpath, String debugpath) {
        File folder = new File(workpath);
        if (!folder.exists() && !folder.isDirectory()) {
            Log.e(TAG, "工作目录:" + workpath + " , 不存在, 会导致无法初始化");
            ToastText("工作目录:" + workpath + " , 不存在, 会导致无法初始化");
            return null;
        }

        String str = "";
        try {
            // 需要特别注意：ak_id/ak_secret/app_key/sdk_code/device_id等参数必须传入SDK
            // 离线语音合成sdk_code取值：精品版为software_nls_tts_offline， 标准版为software_nls_tts_offline_standard
            // 鉴权信息获取参：https://help.aliyun.com/document_detail/69835.htm

            //获取账号访问凭证：
            Auth.GetTicketMethod method = Auth.GetTicketMethod.GET_ACCESS_IN_CLIENT_FOR_OFFLINE_FEATURES;
            if (!g_local_appkey.isEmpty()) {
                Auth.setAppKey(g_local_appkey);
            }
            if (!g_local_ak.isEmpty()) {
                Auth.setAccessKey(g_local_ak);
            }
            if (!g_local_sk.isEmpty()) {
                Auth.setAccessKeySecret(g_local_sk);
            }
            Auth.setSdkCode(g_sdk_code);

            Log.i(TAG, "Use method:" + method);
            JSONObject initObject = Auth.getTicket(method);
            String ak_secret = initObject.getString("ak_secret");

            //当初始化SDK时的save_log参数取值为true时，该参数生效。表示是否保存音频debug，该数据保存在debug目录中，需要确保debug_path有效可写。
            initObject.put("save_wav", "true");
            //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存中间音频文件。
            initObject.put("debug_path", debugpath);

            //工作目录路径，SDK从该路径读取配置文件
            initObject.put("workspace", workpath); // 必填

            // 设置为离线合成
            //  Local = 0,
            initObject.put("mode_type", Constants.TtsModeTypeLocal); // 必填

            // 特别说明: 鉴权所用的id是由device_id，与手机内部的一些唯一码进行组合加密生成的。
            //   更换手机或者更换device_id都会导致重新鉴权计费。
            //   此外, 以下device_id请设置有意义且具有唯一性的id, 比如用户账号(手机号、IMEI等),
            //   传入相同或随机变换的device_id会导致鉴权失败或重复收费。
            //   Utils.getDeviceId() 并不能保证生成不变的device_id，请不要使用
            initObject.put("device_id", "empty_device_id"); // 必填, 推荐填入具有唯一性的id, 方便定位问题。

            //过滤SDK内部日志通过回调送回到用户层
            initObject.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_INFO)));

            str = initObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // 注意! str中包含ak_id ak_secret token app_key等敏感信息, 实际产品中请勿在Log中输出这类信息！
        Log.i(TAG, "local tts userContext:" + str);
        return str;
    }

    private int LocalTtsInitialize(NativeNui cur_instance, String workspace_path, String debug_path) {
        Constants.LogLevel log_level = Constants.LogLevel.LOG_LEVEL_VERBOSE;

        String localInitParams = genLocalTtsInitParams(workspace_path, debug_path);
        if (localInitParams == null) {
            Log.e(TAG, "no valid authentication information was obtained");
            ToastText("鉴权信息无效, 请查看Demo和官网中对账号的说明。");
            return -1;
        }

        Log.i(TAG, "LocalTtsInitialize ->");
        int ret = cur_instance.tts_initialize(new INativeTtsCallback() {
            @Override
            public void onTtsEventCallback(INativeTtsCallback.TtsEvent event, String task_id, int ret_code) {
                Log.i(TAG, "event:" + event + " task id:" + task_id + " ret:" + ret_code);
                // 请妥善保存好task_id、错误码ret_code和错误信息，用于定位问题。
                // 错误信息可通过nui_tts_instance.getparamTts("error_msg")获得。
                if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_START) {
                    Log.i(TAG, "event Start");
                    // 打开播放器开始播放TTS合成数据
                    mAudioTrack.play();
                } else if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_END ||
                        event == TtsEvent.TTS_EVENT_CANCEL ||
                        event == TtsEvent.TTS_EVENT_ERROR) {
                    /*
                     * 提示: TTS_EVENT_END事件表示TTS已经合成完并通过回调传回了所有音频数据, 而不是表示播放器已经播放完了所有音频数据。
                     */
                    Log.i(TAG, "event End");

                    // 表示推送完数据, 当播放器播放结束则会有playOver回调
                    mAudioTrack.isFinishSend(true);

                    if (event == TtsEvent.TTS_EVENT_ERROR) {
                        String error_msg = cur_instance.getparamTts("error_msg");
                        Log.e(TAG, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                        showText(detailView, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                        ToastText(Utils.getMsgWithErrorCode(ret_code, "error"));
                        ToastText("错误码:" + ret_code + " 错误信息:" + error_msg);
                    }
                } else if (event == TtsEvent.TTS_EVENT_PAUSE) {
                    mAudioTrack.pause();
                    Log.i(TAG, "event Pause");
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
                    if (mEncodeType.equals("pcm") || mEncodeType.equals("wav")) {
                        mAudioTrack.setAudioData(data);
                    }
//                    Log.d(TAG, "write:" + data.length);
                }
            }

            @Override
            public void onTtsVolCallback(int volume) {
                Log.i(TAG, "volume " + volume);
            }
            @Override
            public void onTtsLogTrackCallback(Constants.LogLevel level, String log) {
//                Log.i(TAG, "onTtsLogTrackCallback local log level:" + level + ", message -> " + log);
            }
        }, localInitParams, log_level, mSaveLog);  // 注意初始化信息的完整性，通过genTicket生成

        Log.i(TAG, "LocalTtsInitialize result = " + ret);

        if (Constants.NuiResultCode.SUCCESS != ret) {
            Log.e(TAG, "tts create failed");
            return ret;
        }

        // 语音包和SDK是隔离的，需要先设置语音包
        // 如果切换发音人：SDK可使用语音包与鉴权账号相关，由购买时获得的语音包使用权限决定
        // 如已经购买aijia，按下边方式调用后，发音人将切为aijia
        // 语音包下载地址：https://help.aliyun.com/document_detail/204185.html
        // 语音包试听：https://www.aliyun.com/activity/intelligent/offline_tts
        // 特别说明：离线语音合成的发音人, 并不一定也存在于在线语音合成；同理, 在线语音合成的发音人, 并不一定也存在于离线语音合成

        // aar中的资源目录中自带了一个发音人aijia, /data/user/0/mit.alibaba.nuidemo/files/asr_my/tts/voices/aijia
        String fullName = mFontNameDefaultRootPath + mFontName;
        if (!Utils.isExist(fullName)) {
            Log.e(TAG, fullName + " does not exist");
            ToastText("范例语音包文件:" + fullName + " 不存在, 尝试用Demo中设置的外部发音人.");
        }

        // 切换发音人：一定要输入全路径名称
        Log.i(TAG, "use extend_font_name:" + fullName);
        ret = cur_instance.setparamTts("extend_font_name", fullName);
        if (ret != Constants.NuiResultCode.SUCCESS) {
            Log.e(TAG, "setparamTts extend_font_name " + fullName + " failed, ret:" + ret);
            ToastText(Utils.getMsgWithErrorCode(ret, "init"));
            String errmsg = cur_instance.getparamTts("error_msg");
            ToastText("初始化失败, 错误码:" + ret + " 错误信息:" + errmsg);
            return ret;
        }

//        UpdateAudioPlayerSampleRate(cur_instance);

        // 音频编码格式, 离线语音合成只支持PCM
        cur_instance.setparamTts("encode_type", mEncodeType);
        // 调整语速, 语速倍速区间为[0.5, 1.0, 2.0]
        return ret;
    }

    private String genCloudTtsInitParams(String debugpath) {
        String str = "";
        try {
            //获取账号访问凭证：
            //获取账号访问凭证：
            JSONObject initObject = new JSONObject();
            initObject.put("app_key", g_cloud_appkey);
            initObject.put("token", g_cloud_token);

            //当初始化SDK时的save_log参数取值为true时，该参数生效。表示是否保存音频debug，该数据保存在debug目录中，需要确保debug_path有效可写。
            initObject.put("save_wav", "true");
            //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存中间音频文件。
            initObject.put("debug_path", debugpath);

            //  Cloud = 2,
            initObject.put("mode_type", Constants.TtsModeTypeCloud); // 必填

            // 特别说明: 鉴权所用的id是由device_id，与手机内部的一些唯一码进行组合加密生成的。
            //   更换手机或者更换device_id都会导致重新鉴权计费。
            //   此外, 以下device_id请设置有意义且具有唯一性的id, 比如用户账号(手机号、IMEI等),
            //   传入相同或随机变换的device_id会导致鉴权失败或重复收费。
            //   Utils.getDeviceId() 并不能保证生成不变的device_id，请不要使用
            initObject.put("device_id", "empty_device_id"); // 必填, 推荐填入具有唯一性的id, 方便定位问题。

            if (g_url.isEmpty()) {
                g_url = "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1"; // 默认
            }
            initObject.put("url", g_url);

            //过滤SDK内部日志通过回调送回到用户层
            initObject.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_INFO)));

            str = initObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // 注意! str中包含ak_id ak_secret token app_key等敏感信息, 实际产品中请勿在Log中输出这类信息！
        Log.i(TAG, "cloud tts userContext:" + str);
        return str;
    }

    private int CloudTtsInitialize(NativeNui cur_instance, String debug_path) {
        Constants.LogLevel log_level = Constants.LogLevel.LOG_LEVEL_VERBOSE;

        String cloudInitParams = genCloudTtsInitParams(debug_path);
        Log.i(TAG, "CloudTtsInitialize ->");
        int ret = cur_instance.tts_initialize(new INativeTtsCallback() {
            @Override
            public void onTtsEventCallback(INativeTtsCallback.TtsEvent event, String task_id, int ret_code) {
                Log.i(TAG, "event:" + event + " task id:" + task_id + " ret:" + ret_code);
                // 请妥善保存好task_id、错误码ret_code和错误信息，用于定位问题。
                // 错误信息可通过nui_tts_instance.getparamTts("error_msg")获得。
                if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_START) {
                    Log.i(TAG, "event Start");
                    // 打开播放器开始播放TTS合成数据
                    mAudioTrack.play();
                } else if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_END ||
                        event == TtsEvent.TTS_EVENT_CANCEL ||
                        event == TtsEvent.TTS_EVENT_ERROR) {
                    /*
                     * 提示: TTS_EVENT_END事件表示TTS已经合成完并通过回调传回了所有音频数据, 而不是表示播放器已经播放完了所有音频数据。
                     */
                    Log.i(TAG, "event End");

                    // 表示推送完数据, 当播放器播放结束则会有playOver回调
                    mAudioTrack.isFinishSend(true);

                    if (event == TtsEvent.TTS_EVENT_ERROR) {
                        String error_msg = cur_instance.getparamTts("error_msg");
                        Log.e(TAG, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                        showText(detailView, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                        ToastText(Utils.getMsgWithErrorCode(ret_code, "error"));
                        ToastText("错误码:" + ret_code + " 错误信息:" + error_msg);
                    }
                } else if (event == TtsEvent.TTS_EVENT_PAUSE) {
                    mAudioTrack.pause();
                    Log.i(TAG, "event Pause");
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
                    mAudioTrack.setAudioData(data);
//                    Log.d(TAG, "write:" + data.length);
                }
            }

            @Override
            public void onTtsVolCallback(int volume) {
//                Log.i(TAG, "volume " + volume);
            }

            @Override
            public void onTtsLogTrackCallback(Constants.LogLevel level, String log) {
//                Log.i(TAG, "onTtsLogTrackCallback cloud log level:" + level + ", message -> " + log);
            }
        }, cloudInitParams, log_level, mSaveLog);  // 注意初始化信息的完整性，通过genTicket生成

        Log.i(TAG, "CloudTtsInitialize result = " + ret);

        if (Constants.NuiResultCode.SUCCESS != ret) {
            Log.e(TAG, "tts create failed");
            ToastText(Utils.getMsgWithErrorCode(ret, "init"));
            String errmsg = cur_instance.getparamTts("error_msg");
            ToastText("初始化失败, 错误码:" + ret + " 错误信息:" + errmsg);
            return ret;
        }

        // 在线语音合成发音人可以参考阿里云官网
        // https://help.aliyun.com/document_detail/84435.html
        cur_instance.setparamTts("font_name", mFontName);
        // 详细参数可见: https://help.aliyun.com/document_detail/173642.html
        cur_instance.setparamTts("sample_rate", "16000");
        // 模型采样率设置16K，则播放器也得设置成相同采样率16K.
        mAudioTrack.setSampleRate(16000);
        // 字级别音素边界功能开关，该参数只对支持字级别音素边界接口的发音人有效。“1”表示打开，“0”表示关闭。
        cur_instance.setparamTts("enable_subtitle", "1");
        // 音频编码格式, 离线语音合成只支持PCM
        cur_instance.setparamTts("encode_type", mEncodeType);
        return ret;
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
                    who.setText(orign + "\n---\n" + text);
                }
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

    private void ToastText(String text) {
        final String str = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(WakeupAndTtsMixTestActivity.this, str, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void ToastText(String text, int length) {
        final String str = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(WakeupAndTtsMixTestActivity.this, str, length).show();
            }
        });
    }

    /*************** 唤醒和识别 *****************/
    //当回调事件发生时调用
    @Override
    public void onNuiEventCallback(Constants.NuiEvent event, final int resultCode,
                                   final int arg2, KwsResult kwsResult,
                                   AsrResult asrResult) {
        Log.i(TAG, "event=" + event + " resultCode=" + resultCode);
        if (event == Constants.NuiEvent.EVENT_WUW_TRUSTED) {
            wakeupCount++;
            showText(asrView,"唤醒次数: " + wakeupCount);
            JSONObject jsonObject = JSON.parseObject(kwsResult.kws);
            String result = jsonObject.getString("word");
            if (!result.isEmpty()) {
                showText(kwsView,"激活词: " + result);
            }
            appendText(detailView, "EVENT_WUW_TRUSTED");
        } else if (event == Constants.NuiEvent.EVENT_WUW_END) {
            appendText(detailView, "EVENT_WUW_END");

            // 触发唤醒，此处启用识别的实例
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (old_sr_nui_instance == null) {
                        Log.i(TAG, "new old_sr_nui_instance ->");
                        old_sr_nui_instance = new NativeNui();
                    }
                    if (mOldSrInitialized == false) {
                        int ret = CloudRecognizerInitialize(old_sr_nui_instance, mDebugPath);
                        if (ret != Constants.NuiResultCode.SUCCESS) {
                            mOldSrInitialized = false;
                        } else {
                            mOldSrInitialized = true;
                        }
                    }
                    startDialog(old_sr_nui_instance, Constants.VadMode.TYPE_P2T);
                }
            }).start();
        } else if (event == Constants.NuiEvent.EVENT_ASR_STARTED) {
        } else if (event == Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT) {
            appendText(asrView, asrResult.asrResult);
        } else if (event == Constants.NuiEvent.EVENT_ASR_RESULT) {
            appendText(asrView, asrResult.asrResult);

            JSONObject jsonObject = JSON.parseObject(asrResult.asrResult);
            JSONObject payload = jsonObject.getJSONObject("payload");
            String result = payload.getString("result");
            showText(detailView, result);

            // 停止识别实例
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (mOldSrInitialized) {
                        old_sr_nui_instance.cancelDialog();
                        Log.i(TAG, "cancel old_sr_nui_instance done.");
                    }
                }
            }).start();
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR ||
                event == Constants.NuiEvent.EVENT_MIC_ERROR) {
            ToastText("ERROR with " + resultCode, Toast.LENGTH_SHORT);
            final String msg_text = Utils.getMsgWithErrorCode(resultCode, "start");
            ToastText(msg_text, Toast.LENGTH_SHORT);

            wakeupCount = 0;
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
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
        return mAudioRecorder.read(buffer, 0, len);
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
                mAudioRecorder.stop();
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
    public void onNuiVprEventCallback(Constants.NuiVprEvent event) {}

    @Override
    public void onNuiLogTrackCallback(Constants.LogLevel level, String log) {
//        Log.i(TAG, "onNuiLogTrackCallback log level:" + level + ", message -> " + log);
    }
}
