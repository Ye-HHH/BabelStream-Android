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
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;

import java.io.*;

import android.util.Log;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeTtsCallback;
import com.alibaba.idst.nui.NativeNui;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import android.widget.ArrayAdapter;

// 本样例展示离线语音合成和在线语音合成双实例的使用方法
// 离线语音合成:
//   Android SDK 详细说明：https://help.aliyun.com/document_detail/204187.html
//   Android SDK 接口说明：https://help.aliyun.com/document_detail/204185.html
// 在线语音合成:
//   Android SDK 详细说明：https://help.aliyun.com/zh/isi/developer-reference/nui-sdk-for-android-2
//   Android SDK 接口说明：https://help.aliyun.com/zh/isi/developer-reference/overview-5
public class TtsMixActivity extends Activity implements View.OnClickListener, OnItemSelectedListener {
    private static final String TAG = "TtsMixActivity";

    private String g_appkey = "";
    private String g_token = "";
    private String g_sts_token = "";
    private String g_ak = "";
    private String g_sk = "";
    private String g_url = "";
    private String g_sdk_code = "software_nls_tts_offline_standard"; // 精品版为software_nls_tts_offline， 标准版为software_nls_tts_offline_standard
    private String g_access_file = "";

    // 本地合成目前仅支持单路合成
    NativeNui local_tts_instance = new NativeNui(Constants.ModeType.MODE_TTS);
    NativeNui cloud_tts_instance = new NativeNui(Constants.ModeType.MODE_TTS);
    private String mDebugRootPath = "";  // 可以任意设置, 但是注意路径权限
    private String mDebugPath = "";  // demo音频文件存储路径，可以任意设置, 但是注意路径权限
    private String mAssetPath;   // SDK工作路径，该目录下含有配置文件及TTS资源，即workspace
    private String mFontNameDefaultRootPath;
    private String mFontName = "aijia";
    private final String CN_PREVIEW = "本样例展示离线语音合成使用方法，" +
            "\n1）设置鉴权信息：按照鉴权认证文档获取注册信息，并调用接口tts_initialize进行设置；\n" +
            "2）配置语音包：将购买语音包推到app的cache或任意可以读写的路径下，要确保SDK有读的权限；\n" +
            "3）开始合成";
    private final String downloadingOthers = "下载更多语音包";
    private final String defaultSdkCode = "只有使用离线功能才需要填写";

    private final Map<String, List<String>> paramMap = new HashMap<>();
    private Button ttsStartBtn, ttsQuitBtn, ttsCancelBtn, ttsPauseBtn, ttsResumeBtn, ttsClearTextBtn;
    private Spinner mPitchSpin, mFontSpin, mSpeedSpin, mVolumeSpin, mFormatSpin;
    private EditText mEditView;
    private Switch mSaveAudioSwitch;
    private TextView eventView;

    boolean mInitialized = false;
    private String mEncodeType = "pcm"; // AudioPlayer中AudioTrack只能播放PCM格式，其他音频格式请另外编写播放器代码。且离线语音合成只支持PCM。
    private boolean mSyntheticing = false;  // TTS是否处于合成中
    private String curTaskId = "";
    private String mSynthesisAudioFilePath = "";
    private OutputStream mSynthesisAudioFile = null;

    private String mCloudFontName = "aijia";
    private Button cloudTtsStartBtn, cloudTtsQuitBtn, cloudTtsCancelBtn, cloudTtsPauseBtn, cloudTtsResumeBtn;
    private Spinner mCloudPitchSpin, mCloudFontSpin, mCloudSpeedSpin, mCloudVolumeSpin, mCloudFormatSpin;
    boolean mCloudInitialized = false;
    private String mCloudEncodeType = "pcm";
    private String curCloudTaskId = "";
    private String mCloudSynthesisAudioFilePath = "";
    private OutputStream mCloudSynthesisAudioFile = null;

    // 播报模块示例：展示合成音频播放
    // AudioPlayer默认采样率是16000
    private AudioPlayer mAudioTrack = new AudioPlayer(new AudioPlayerCallback() {
        @Override
        public void playStart() {
            Log.i(TAG, "playStart");
        }

        @Override
        public void playOver() {
            Log.i(TAG, "playOver");
            appendText(eventView, "播放完成");
        }
        @Override
        public void playSoundLevel(int level) {}
    });

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts_mix);

        String version = local_tts_instance.GetVersion();
        final String sdk_ver = Utils.extractVersion(version);
        Log.i(TAG, "current sdk version: " + version + " sdk_ver: " + sdk_ver);
        if (!sdk_ver.equals("028") && !sdk_ver.equals("029") && !sdk_ver.equals("015")) {
            final String version_text = "SDK版本:" + version + "不支持离线语音合成功能，请到官网下载对应SDK。";
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(TtsMixActivity.this,
                            version_text, Toast.LENGTH_LONG).show();
                }
            });
            Intent intent_bak = new Intent(TtsMixActivity.this, MainActivity.class);
            startActivity(intent_bak);
            finish();
            return;
        } else {
            final String version_text = "内部SDK版本号:" + version;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(TtsMixActivity.this,
                            version_text, Toast.LENGTH_SHORT).show();
                }
            });
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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(TtsMixActivity.this,
                            "请设置SDK_CODE", Toast.LENGTH_LONG).show();
                }
            });
            Intent intent_bak = new Intent(TtsMixActivity.this, MainActivity.class);
            startActivity(intent_bak);
            finish();
            return;
        }

        String path = "";
        if (true) {
            //设置默认目的地路径最下级目录名字
            // 比如设置当前 /data/user/0/mit.alibaba.nuidemo/files/asr_my
            // 未调用此接口, 则默认为 /data/user/0/mit.alibaba.nuidemo/files/asr_my
            CommonUtils.setTargetDataDir("asr_my");

            //获取工作路径, 即获得拷贝后资源文件存储的cache路径, 作为workspace
            // 比如 /data/user/0/mit.alibaba.nuidemo/files/asr_my
            path = CommonUtils.getModelPath(this);
            Log.i(TAG, "workpath:" + path);
            mAssetPath = path;

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

        } else {
            //这里以鉴权文件存储在/sdcard/auth中为示例，但是可能无权限，强烈不推荐
            // 本DEMO未获得读写权限
            path = "/sdcard/auth";
            Log.i(TAG, "workpath:" + path);
            mAssetPath = path;
            if (CommonUtils.copyAssetsToExplicitPath(this, path)) {
                Log.i(TAG, "copy assets data done");
            } else {
                Log.e(TAG, "copy assets failed");
                ToastText("从aar的assets拷贝资源文件失败, 请检查资源文件是否存在, 详情可通过日志确认。");
                return;
            }
        }

        // mFontNameDefaultRootPath : /data/user/0/mit.alibaba.nuidemo/files/asr_my/tts/voices/
        mFontNameDefaultRootPath = mAssetPath + "/tts/voices/";
        // mDebugRootPath : /storage/emulated/0/Android/data/mit.alibaba.nuidemo/cache/
        mDebugRootPath = getExternalCacheDir().getAbsolutePath();
        mDebugPath = mDebugRootPath + "/debug";
        Utils.createDir(mDebugPath);

        initUIWidgets();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAudioTrack.stop();
        mAudioTrack.releaseAudioTrack();
        local_tts_instance.tts_release();
        cloud_tts_instance.tts_release();
        mInitialized = false;
        mCloudInitialized = false;
    }

    private List<String> GetFontListFromPath() {
        List<String> FontList = new ArrayList<>();
        Log.i(TAG, "mFontNameDefaultRootPath: " + mFontNameDefaultRootPath);
        File file = new File(mFontNameDefaultRootPath);
        File[] tempList = file.listFiles();
        if (tempList != null) {
            for (int i = 0; i < tempList.length; i++) {
                if (tempList[i].isFile()) {
                    String[] arr = tempList[i].toString().split("/");
                    String name = arr[arr.length - 1];
                    if (!name.contains(".")) {
                        Log.i(TAG, "find name:" + name);
                        FontList.add(name);
                    }
                }
            }
        }
        FontList.add(downloadingOthers);
        return FontList;
    }

    private void getFontList() {
        List<String> Font0 =  Utils.getVoiceList("NlsTts");
        ArrayAdapter<String> spinnerFont0 = new ArrayAdapter<String>(TtsMixActivity.this, android.R.layout.simple_spinner_dropdown_item, Font0);
        mCloudFontSpin.setAdapter(spinnerFont0);
        mCloudFontSpin.setSelection(0);
        paramMap.put("cloudfontname", Font0);

        List<String> Font = GetFontListFromPath();
        ArrayAdapter<String> spinnerFont = new ArrayAdapter<String>(TtsMixActivity.this, android.R.layout.simple_spinner_dropdown_item, Font);
        mFontSpin.setAdapter(spinnerFont);
        mFontSpin.setSelection(0);
        paramMap.put("fontname", Font);
    }

    private void getSpeedList() {
        List<String> speed = new ArrayList<>();
        speed.add("0.5");
        speed.add("0.6");
        speed.add("1.0");
        speed.add("2.0");
        ArrayAdapter<String> spinnerSpeed = new ArrayAdapter<String>(TtsMixActivity.this,
                android.R.layout.simple_spinner_dropdown_item, speed);
        mSpeedSpin.setAdapter(spinnerSpeed);
        mSpeedSpin.setSelection(2);
        paramMap.put("speed_level", speed);

        mCloudSpeedSpin.setAdapter(spinnerSpeed);
        mCloudSpeedSpin.setSelection(2);
        paramMap.put("cloud_speed_level", speed);
    }

    private void getPitchList() {
        List<String> pitch = new ArrayList<>();
        pitch.add("-500");
        pitch.add("-250");
        pitch.add("0");
        pitch.add("250");
        pitch.add("500");
        ArrayAdapter<String> spinnerPitch = new ArrayAdapter<String>(TtsMixActivity.this, android.R.layout.simple_spinner_dropdown_item, pitch);
        mPitchSpin.setAdapter(spinnerPitch);
        mPitchSpin.setSelection(2);
        paramMap.put("pitch_level", pitch);

        mCloudPitchSpin.setAdapter(spinnerPitch);
        mCloudPitchSpin.setSelection(2);
        paramMap.put("cloud_pitch_level", pitch);
    }

    private void getVolumeList() {
        List<String> volume = new ArrayList<>();
        volume.add("0.6");
        volume.add("1.0");
        volume.add("1.5");
        volume.add("2.0");
        volume.add("3.0");
        volume.add("6.0");
        volume.add("8.0");
        ArrayAdapter<String> spinnerVolume = new ArrayAdapter<String>(TtsMixActivity.this,
                android.R.layout.simple_spinner_dropdown_item, volume);
        mVolumeSpin.setAdapter(spinnerVolume);
        mVolumeSpin.setSelection(1);
        paramMap.put("volume", volume);

        mCloudVolumeSpin.setAdapter(spinnerVolume);
        mCloudVolumeSpin.setSelection(1);
        paramMap.put("cloud_volume", volume);
    }

    private void getFormatList() {
        List<String> format = new ArrayList<>();
        format.add("pcm");
        ArrayAdapter<String> spinnerFormat = new ArrayAdapter<String>(
                TtsMixActivity.this,
                android.R.layout.simple_spinner_dropdown_item, format);
        mFormatSpin.setAdapter(spinnerFormat);
        mFormatSpin.setSelection(0);
        paramMap.put("format", format);

        mCloudFormatSpin.setAdapter(spinnerFormat);
        mCloudFormatSpin.setSelection(0);
        paramMap.put("cloud_format", format);
    }

    private void initUIWidgets() {
        mEditView = (EditText) findViewById(R.id.tts_content);
        mEditView.setText(CN_PREVIEW);

        cloudTtsStartBtn = (Button) findViewById(R.id.cloud_tts_start_btn);
        cloudTtsCancelBtn = (Button) findViewById(R.id.cloud_tts_cancel_btn);
        cloudTtsPauseBtn = (Button) findViewById(R.id.cloud_tts_pause_btn);
        cloudTtsResumeBtn = (Button) findViewById(R.id.cloud_tts_resume_btn);
        cloudTtsQuitBtn = (Button) findViewById(R.id.cloud_tts_quit_btn);

        mCloudPitchSpin = (Spinner) findViewById(R.id.cloud_tts_set_pitch_spin);
        mCloudPitchSpin.setOnItemSelectedListener(this);
        mCloudFontSpin = (Spinner) findViewById(R.id.cloud_tts_set_font_spin);
        mCloudFontSpin.setOnItemSelectedListener(this);
        mCloudSpeedSpin = (Spinner) findViewById(R.id.cloud_tts_set_speed_spin);
        mCloudSpeedSpin.setOnItemSelectedListener(this);
        mCloudVolumeSpin = (Spinner) findViewById(R.id.cloud_tts_set_volume_spin);
        mCloudVolumeSpin.setOnItemSelectedListener(this);
        mCloudFormatSpin = (Spinner) findViewById(R.id.cloud_tts_set_format_spin);
        mCloudFormatSpin.setOnItemSelectedListener(this);

        ttsStartBtn = (Button) findViewById(R.id.tts_start_btn);
        ttsCancelBtn = (Button) findViewById(R.id.tts_cancel_btn);
        ttsPauseBtn = (Button) findViewById(R.id.tts_pause_btn);
        ttsResumeBtn = (Button) findViewById(R.id.tts_resume_btn);
        ttsQuitBtn = (Button) findViewById(R.id.tts_quit_btn);
        ttsClearTextBtn = (Button) findViewById(R.id.tts_clear_btn);

        mSaveAudioSwitch = (Switch) findViewById(R.id.save_audio_switch5);
        mSaveAudioSwitch.setVisibility(View.VISIBLE);

        mPitchSpin = (Spinner) findViewById(R.id.tts_set_pitch_spin);
        mPitchSpin.setOnItemSelectedListener(this);
        mFontSpin = (Spinner) findViewById(R.id.tts_set_font_spin);
        mFontSpin.setOnItemSelectedListener(this);
        mSpeedSpin = (Spinner) findViewById(R.id.tts_set_speed_spin);
        mSpeedSpin.setOnItemSelectedListener(this);
        mVolumeSpin = (Spinner) findViewById(R.id.tts_set_volume_spin);
        mVolumeSpin.setOnItemSelectedListener(this);
        mFormatSpin = (Spinner) findViewById(R.id.tts_set_format_spin2);
        mFormatSpin.setOnItemSelectedListener(this);

        eventView = (TextView) findViewById(R.id.textView18);
        eventView.setEnabled(false);

        getFontList();
        getSpeedList();
        getPitchList();
        getVolumeList();
        getFormatList();

        ttsStartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String mText = mEditView.getText().toString();
                if (TextUtils.isEmpty(mText)) {
                    Log.e(TAG, "text is empty");
                    ToastText("文本为空！");
                    return;
                }
                if (!mInitialized) {
                    Log.i(TAG, "tts-init");
                    LocalInitialize(mAssetPath, mDebugPath);
                }
                Log.i(TAG, "start tts-play");
                // 该接口是异步的
                // taskid（32位uui）可以自己设置；为空时，SDK内部会会自动产生
                // 每个instance一个task，若想同时处理多个task，请启动多instance
                int ret = local_tts_instance.startTts("1", "", mText);
                if (ret != Constants.NuiResultCode.SUCCESS) {
                    String error_msg = local_tts_instance.getparamTts("error_msg");
                    showText(eventView, "error_code:" + ret + " errmsg:" + error_msg);
                    Log.e(TAG, "startTts failed. errmsg:" + error_msg);
                    ToastText(Utils.getMsgWithErrorCode(ret, "start"));
                } else {
                    Log.i(TAG, "start tts-play done");
                    if (mEncodeType.equals("mp3")) {
                        showText(eventView, "当前DEMO无法播放MP3, 需用户在实际产品中自行实现。仅打开<音频保存>存下MP3文件。");
                    }
                }
            }
        });
        ttsQuitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts-release");
                mAudioTrack.stop();
                int ret = local_tts_instance.tts_release();
                if (ret != 0) {
                    Log.e(TAG, "tts_release failed, error:" + ret);
                    ToastText(Utils.getMsgWithErrorCode(ret, "release"));
                }
                mInitialized = false;
                Log.i(TAG, "tts-release done");
            }
        });
        ttsCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts-cancel");
                int ret = local_tts_instance.cancelTts("");
                if (ret != 0) {
                    Log.e(TAG, "cancelTts failed, error:" + ret);
                    ToastText(Utils.getMsgWithErrorCode(ret, "cancel"));
                }
                Log.i(TAG, "tts-cancel done");
                mAudioTrack.stop();
            }
        });
        ttsPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts-pause");
                if (mSyntheticing) {
                    int ret = local_tts_instance.pauseTts();
                    if (ret != 0) {
                        Log.e(TAG, "pauseTts failed, error:" + ret);
                        ToastText(Utils.getMsgWithErrorCode(ret, "pause"));
                        showText(eventView, Utils.getMsgWithErrorCode(ret, "pause"));
                    } else {
                        showText(eventView, "暂停");
                    }
                }
                mAudioTrack.pause();
            }
        });
        ttsResumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts-resume");
                if (mSyntheticing) {
                    int ret = local_tts_instance.resumeTts();
                    if (ret != 0) {
                        Log.e(TAG, "resumeTts failed, error:" + ret);
                        ToastText(Utils.getMsgWithErrorCode(ret, "resume"));
                        showText(eventView, Utils.getMsgWithErrorCode(ret, "resume"));
                    } else {
                        showText(eventView, "恢复");
                    }
                }
                mAudioTrack.play();
            }
        });
        ttsClearTextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEditView.setText("");
            }
        });

        cloudTtsStartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String mText = mEditView.getText().toString();
                if (TextUtils.isEmpty(mText)) {
                    Log.e(TAG, "text is empty");
                    ToastText("文本为空！");
                    return;
                }
                if (!mCloudInitialized) {
                    Log.i(TAG, "tts-init");
                    CloudInitialize(mAssetPath, mDebugPath);
                }
                Log.i(TAG, "start cloud-tts-play");
                // 支持一次性合成300字符以内的文字，其中1个汉字、1个英文字母或1个标点均算作1个字符，
                // 超过300个字符的内容将会截断。所以请确保传入的text小于300字符(不包含ssml格式)。
                // 长短文本语音合成收费不同，须另外开通长文本语音服务，请注意。
                // 不需要长文本语音合成功能则无需考虑以下操作。
                int charNum = cloud_tts_instance.getUtf8CharsNum(mText);
                Log.i(TAG, "chars:" + charNum + " of text:" + mText);
                if (charNum > 300) {
                    Log.w(TAG, "text exceed 300 chars.");
                    // 超过300字符设置成 长文本语音合成 模式
                    cloud_tts_instance.setparamTts("tts_version", "1");
                    showText(eventView, "共" + charNum + "字符，使用长文本语音合成模式");
                } else {
                    // 未超过300字符设置成 短文本语音合成 模式
                    cloud_tts_instance.setparamTts("tts_version", "0");
                    showText(eventView, "共" + charNum + "字符，使用短文本语音合成模式");
                }

                // 该接口是异步的
                // taskid（32位uui）可以自己设置；为空时，SDK内部会会自动产生
                // 每个instance一个task，若想同时处理多个task，请启动多instance
                int ret = cloud_tts_instance.startTts("1", "", mText);
                if (ret != Constants.NuiResultCode.SUCCESS) {
                    String error_msg = cloud_tts_instance.getparamTts("error_msg");
                    showText(eventView, "error_code:" + ret + " errmsg:" + error_msg);
                    Log.e(TAG, "startTts failed. errmsg:" + error_msg);
                    ToastText(Utils.getMsgWithErrorCode(ret, "start"));
                } else {
                    Log.i(TAG, "start cloud-tts-play done");
                    if (mCloudEncodeType.equals("mp3")) {
                        showText(eventView, "当前DEMO无法播放MP3, 需用户在实际产品中自行实现。仅打开<音频保存>存下MP3文件。");
                    }
                }
            }
        });
        cloudTtsQuitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "cloud-tts-release");
                mAudioTrack.stop();
                int ret = cloud_tts_instance.tts_release();
                if (ret != 0) {
                    Log.e(TAG, "tts_release failed, error:" + ret);
                    ToastText(Utils.getMsgWithErrorCode(ret, "release"));
                }
                mCloudInitialized = false;
                Log.i(TAG, "tts-release done");
            }
        });
        cloudTtsCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "cloud-tts-cancel");
                int ret = cloud_tts_instance.cancelTts("");
                if (ret != 0) {
                    Log.e(TAG, "cancelTts failed, error:" + ret);
                    ToastText(Utils.getMsgWithErrorCode(ret, "cancel"));
                }
                Log.i(TAG, "tts-cancel done");
                mAudioTrack.stop();
            }
        });
        cloudTtsPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts-pause");
                if (mSyntheticing) {
                    int ret = cloud_tts_instance.pauseTts();
                    if (ret != 0) {
                        Log.e(TAG, "pauseTts failed, error:" + ret);
                        ToastText(Utils.getMsgWithErrorCode(ret, "pause"));
                        showText(eventView, Utils.getMsgWithErrorCode(ret, "pause"));
                    } else {
                        showText(eventView, "暂停");
                    }
                }
                mAudioTrack.pause();
            }
        });
        cloudTtsResumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts-resume");
                if (mSyntheticing) {
                    int ret = cloud_tts_instance.resumeTts();
                    if (ret != 0) {
                        Log.e(TAG, "resumeTts failed, error:" + ret);
                        ToastText(Utils.getMsgWithErrorCode(ret, "resume"));
                        showText(eventView, Utils.getMsgWithErrorCode(ret, "resume"));
                    } else {
                        showText(eventView, "恢复");
                    }
                }
                mAudioTrack.play();
            }
        });
    }

    @Override
    public void onClick(View v) {}

    private int LocalInitialize(String path, String debug_path) {
        boolean save_log = true;
        Constants.LogLevel log_level = Constants.LogLevel.LOG_LEVEL_VERBOSE;

        String localInitParams = genLocalInitParams(path, debug_path);
        if (localInitParams == null) {
            Log.e(TAG, "no valid authentication information was obtained");
            ToastText("鉴权信息无效, 请查看Demo和官网中对账号的说明。");
            return -1;
        }

        int ret = local_tts_instance.tts_initialize(new INativeTtsCallback() {
            @Override
            public void onTtsEventCallback(INativeTtsCallback.TtsEvent event, String task_id, int ret_code) {
                Log.i(TAG, "event:" + event + " task id:" + task_id + " ret:" + ret_code);
                // 请妥善保存好task_id、错误码ret_code和错误信息，用于定位问题。
                // 错误信息可通过nui_tts_instance.getparamTts("error_msg")获得。
                if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_START) {
                    Log.i(TAG, "event Start");
                    showText(eventView, "开始合成 ...");

                    // 标记合成中，方便暂停/恢复的功能实现
                    mSyntheticing = true;

                    // 用于音频存储
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

                    // 打开播放器开始播放TTS合成数据
                    mAudioTrack.play();
                } else if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_END ||
                        event == TtsEvent.TTS_EVENT_CANCEL ||
                        event == TtsEvent.TTS_EVENT_ERROR) {
                    /*
                     * 提示: TTS_EVENT_END事件表示TTS已经合成完并通过回调传回了所有音频数据, 而不是表示播放器已经播放完了所有音频数据。
                     */
                    Log.i(TAG, "event End");

                    showText(eventView, "合成结束");

                    // 标记合成结束，方便暂停/恢复的功能实现
                    mSyntheticing = false;

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
                        String error_msg = local_tts_instance.getparamTts("error_msg");
                        Log.e(TAG, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                        showText(eventView, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
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
                    Log.d(TAG, "write:" + data.length);
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
            public void onTtsVolCallback(int volume) {
                Log.i(TAG, "volume " + volume);
            }

            @Override
            public void onTtsLogTrackCallback(Constants.LogLevel level, String log) {
                Log.i(TAG, "onTtsLogTrackCallback local log level:" + level + ", message -> " + log);
            }
        }, localInitParams, log_level, save_log);  // 注意初始化信息的完整性，通过genTicket生成

        if (Constants.NuiResultCode.SUCCESS == ret) {
            mInitialized = true;
        } else {
            Log.e(TAG, "tts create failed");
            mInitialized = false;
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

            // 这里假设用户将发音人文件放在 /sdcard/idst/aijia, 且拥有读写文件的权限
//            String mExtStorage = "/sdcard/idst/"; // 此DEMO无操作此路径的权限
//            String mFontPath = mExtStorage; // 语音包保存路径，可以任意设置, 但是注意路径权限
//            fullName = mFontPath + mFontName;
//            if (!Utils.isExist(fullName)) {
//                Log.e(TAG, fullName + " does not exist");
//                ToastText("语音包文件:" + fullName + " 不存在, 请查看Demo中的说明, 确认路径是否正确.");
//            }
        }

        // 切换发音人：一定要输入全路径名称
        Log.i(TAG, "use extend_font_name:" + fullName);
        ret = local_tts_instance.setparamTts("extend_font_name", fullName);
        if (ret != Constants.NuiResultCode.SUCCESS) {
            Log.e(TAG, "setparamTts extend_font_name " + fullName + " failed, ret:" + ret);
            ToastText(Utils.getMsgWithErrorCode(ret, "init"));
            String errmsg = local_tts_instance.getparamTts("error_msg");
            ToastText("初始化失败, 错误码:" + ret + " 错误信息:" + errmsg);
            return ret;
        }

        UpdateAudioPlayerSampleRate();

        // 音频编码格式, 离线语音合成只支持PCM
        local_tts_instance.setparamTts("encode_type", mEncodeType);
        // 调整语速, 语速倍速区间为[0.5, 1.0, 2.0]
        local_tts_instance.setparamTts("speed_level", mSpeedSpin.getSelectedItem().toString());
        // 调整音调, [-500,500]，默认0
        local_tts_instance.setparamTts("pitch_level", mPitchSpin.getSelectedItem().toString());
        // 调整音量, (0，2]，默认1.0. 最大可达8, 但是超过1.5容易出现破音.
        local_tts_instance.setparamTts("volume", mVolumeSpin.getSelectedItem().toString());
        return ret;
    }

    private int CloudInitialize(String path, String debug_path) {
        boolean save_log = true;
        Constants.LogLevel log_level = Constants.LogLevel.LOG_LEVEL_VERBOSE;

        String cloudInitParams = genCloudInitParams(path, debug_path);
        int ret = cloud_tts_instance.tts_initialize(new INativeTtsCallback() {
            @Override
            public void onTtsEventCallback(INativeTtsCallback.TtsEvent event, String task_id, int ret_code) {
                Log.i(TAG, "event:" + event + " task id:" + task_id + " ret:" + ret_code);
                // 请妥善保存好task_id、错误码ret_code和错误信息，用于定位问题。
                // 错误信息可通过nui_tts_instance.getparamTts("error_msg")获得。
                if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_START) {
                    Log.i(TAG, "event Start");
                    showText(eventView, "开始合成 ...");

                    // 标记合成中，方便暂停/恢复的功能实现
                    mSyntheticing = true;

                    // 用于音频存储
                    curCloudTaskId = task_id;
                    if (mSaveAudioSwitch.isChecked()) {
                        try {
                            if (mCloudSynthesisAudioFile != null) {
                                mCloudSynthesisAudioFile.close();
                                mCloudSynthesisAudioFile = null;
                            }
                            if (mCloudSynthesisAudioFile == null && !curCloudTaskId.isEmpty()) {
                                try {
                                    mCloudSynthesisAudioFilePath = debug_path + "/" + "tts_task_id_" + curCloudTaskId + "." + mCloudEncodeType;
                                    Log.i(TAG, "save tts data into " + mCloudSynthesisAudioFilePath);
                                    mCloudSynthesisAudioFile = new FileOutputStream(mCloudSynthesisAudioFilePath, true);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    // 打开播放器开始播放TTS合成数据
                    mAudioTrack.play();
                } else if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_END ||
                        event == TtsEvent.TTS_EVENT_CANCEL ||
                        event == TtsEvent.TTS_EVENT_ERROR) {
                    /*
                     * 提示: TTS_EVENT_END事件表示TTS已经合成完并通过回调传回了所有音频数据, 而不是表示播放器已经播放完了所有音频数据。
                     */
                    Log.i(TAG, "event End");

                    showText(eventView, "合成结束");

                    // 标记合成结束，方便暂停/恢复的功能实现
                    mSyntheticing = false;

                    // 表示推送完数据, 当播放器播放结束则会有playOver回调
                    mAudioTrack.isFinishSend(true);

                    // 调试使用, 若希望存下音频文件, 如下
                    try {
                        if (mCloudSynthesisAudioFile != null) {
                            mCloudSynthesisAudioFile.close();
                            mCloudSynthesisAudioFile = null;
                            if (mCloudEncodeType.equals("wav")) {
                                Utils.fixWavHeader(mCloudSynthesisAudioFilePath);
                            }
                            String show = "存储TTS音频到 " + mCloudSynthesisAudioFilePath;
                            Log.i(TAG, show);
                            showText(eventView, show);
                            ToastText(show);
                        }
                        curCloudTaskId = "";
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (event == TtsEvent.TTS_EVENT_ERROR) {
                        String error_msg = cloud_tts_instance.getparamTts("error_msg");
                        Log.e(TAG, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                        showText(eventView, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
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
                    if (mCloudEncodeType.equals("pcm") || mCloudEncodeType.equals("wav")) {
                        mAudioTrack.setAudioData(data);
                    }
                    Log.d(TAG, "write:" + data.length);
                    try {
                        if (mCloudSynthesisAudioFile != null) {
                            mCloudSynthesisAudioFile.write(data);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onTtsVolCallback(int volume) {
                Log.i(TAG, "volume " + volume);
            }

            @Override
            public void onTtsLogTrackCallback(Constants.LogLevel level, String log) {
                Log.i(TAG, "onTtsLogTrackCallback cloud log level:" + level + ", message -> " + log);
            }
        }, cloudInitParams, log_level, save_log);  // 注意初始化信息的完整性，通过genTicket生成

        if (Constants.NuiResultCode.SUCCESS == ret) {
            mCloudInitialized = true;
        } else {
            Log.e(TAG, "tts create failed");
            mCloudInitialized = false;
            return ret;
        }

        if (ret != Constants.NuiResultCode.SUCCESS) {
            ToastText(Utils.getMsgWithErrorCode(ret, "init"));
            String errmsg = cloud_tts_instance.getparamTts("error_msg");
            ToastText("初始化失败, 错误码:" + ret + " 错误信息:" + errmsg);
            return ret;
        }

        // 在线语音合成发音人可以参考阿里云官网
        // https://help.aliyun.com/document_detail/84435.html
        String fontName = mCloudFontSpin.getSelectedItem().toString();
        String[] parts = fontName.split("-");
        if (parts.length > 1) {
            mCloudFontName = parts[0];
        } else {
            mCloudFontName = fontName;
        }
        cloud_tts_instance.setparamTts("font_name", mCloudFontName);
        // 详细参数可见: https://help.aliyun.com/document_detail/173642.html
        cloud_tts_instance.setparamTts("sample_rate", "16000");
        // 模型采样率设置16K，则播放器也得设置成相同采样率16K.
        mAudioTrack.setSampleRate(16000);
        // 字级别音素边界功能开关，该参数只对支持字级别音素边界接口的发音人有效。“1”表示打开，“0”表示关闭。
        cloud_tts_instance.setparamTts("enable_subtitle", "1");

        // 音频编码格式, 离线语音合成只支持PCM
        cloud_tts_instance.setparamTts("encode_type", mCloudEncodeType);
        // 调整语速, 语速倍速区间为[0.5, 1.0, 2.0]
        cloud_tts_instance.setparamTts("speed_level", mCloudSpeedSpin.getSelectedItem().toString());
        // 调整音调, [-500,500]，默认0
        cloud_tts_instance.setparamTts("pitch_level", mCloudPitchSpin.getSelectedItem().toString());
        // 调整音量, (0，2]，默认1.0. 最大可达8, 但是超过1.5容易出现破音.
        cloud_tts_instance.setparamTts("volume", mCloudVolumeSpin.getSelectedItem().toString());
        return ret;
    }

    private String genLocalInitParams(String workpath, String debugpath) {
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
            Auth.GetTicketMethod method = Auth.GetTicketMethod.GET_STS_ACCESS_FROM_SERVER_FOR_OFFLINE_FEATURES;
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
                        method = Auth.GetTicketMethod.GET_ACCESS_IN_CLIENT_FOR_OFFLINE_FEATURES;
                    } else {
                        method = Auth.GetTicketMethod.GET_STS_ACCESS_IN_CLIENT_FOR_OFFLINE_FEATURES;
                    }
                }
            }
            Log.i(TAG, "Use method:" + method);
            JSONObject initObject = Auth.getTicket(method);
            String ak_secret = initObject.getString("ak_secret");
            if (ak_secret.equals("")) {
                ToastText("无法获取有效鉴权信息，请检查账号信息ak_id和ak_secret. 或者将鉴权信息以json格式保存至本地文件(/sdcard/idst/auth.txt)");

                // 如果接口没有设置鉴权信息，尝试从本地鉴权文件加载（方便测试人员多账号验证）
//                initObject = null;
                // 假设本地有存了鉴权信息的文件, 注意账号安全
//                String fileName = "/sdcard/idst/auth.txt";
//                if (Utils.isExist(fileName)) {
//                    initObject = Auth.getTicketFromJsonFile(fileName);
//                }
//                if (initObject == null) {
//                    ToastText("无法获取有效鉴权信息，请检查账号信息ak_id和ak_secret. 或者将鉴权信息以json格式保存至本地文件(/sdcard/idst/auth.txt)");
//                    return null;
//                }
            }

            //当初始化SDK时的save_log参数取值为true时，该参数生效。表示是否保存音频debug，该数据保存在debug目录中，需要确保debug_path有效可写。
            initObject.put("save_wav", "true");
            //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存中间音频文件。
            initObject.put("debug_path", debugpath);

            //工作目录路径，SDK从该路径读取配置文件
            initObject.put("workspace", workpath); // 必填

            // 设置为离线合成
            //  Local = 0,
            //  Mix = 1,  // init local and cloud
            //  Cloud = 2,
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

    private String genCloudInitParams(String workpath, String debugpath) {
        File folder = new File(workpath);
        if (!folder.exists() && !folder.isDirectory()) {
            Log.e(TAG, "工作目录:" + workpath + " , 不存在, 会导致无法初始化");
            ToastText("工作目录:" + workpath + " , 不存在, 会导致无法初始化");
            return null;
        }

        String str = "";
        try {
            //获取账号访问凭证：
//            Auth.GetTicketMethod method = Auth.GetTicketMethod.GET_TOKEN_FROM_SERVER_FOR_ONLINE_FEATURES;
//            if (!g_appkey.isEmpty()) {
//                Auth.setAppKey(g_appkey);
//            }
//            if (!g_token.isEmpty()) {
//                Auth.setToken(g_token);
//            }
//            if (!g_ak.isEmpty()) {
//                Auth.setAccessKey(g_ak);
//            }
//            if (!g_sk.isEmpty()) {
//                Auth.setAccessKeySecret(g_sk);
//            }
//            Auth.setStsToken(g_sts_token);
//            // 此处展示将用户传入账号信息进行交互，实际产品不可以将任何账号信息存储在端侧
//            if (!g_appkey.isEmpty()) {
//                if (!g_ak.isEmpty() && !g_sk.isEmpty()) {
//                    if (g_sts_token.isEmpty()) {
//                        method = Auth.GetTicketMethod.GET_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES;
//                    } else {
//                        method = Auth.GetTicketMethod.GET_STS_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES;
//                    }
//                }
//                if (!g_token.isEmpty()) {
//                    method = Auth.GetTicketMethod.GET_TOKEN_IN_CLIENT_FOR_ONLINE_FEATURES;
//                }
//            }

            Auth.GetTicketMethod method = Auth.GetTicketMethod.GET_TOKEN_IN_CLIENT_FOR_ONLINE_FEATURES;
            Auth.setAppKey("<支持云端TTS的appkey>");
            Auth.setToken("<支持云端TTS的token>");

            Log.i(TAG, "Use method:" + method);
            JSONObject initObject = Auth.getTicket(method);

            //当初始化SDK时的save_log参数取值为true时，该参数生效。表示是否保存音频debug，该数据保存在debug目录中，需要确保debug_path有效可写。
            initObject.put("save_wav", "true");
            //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存中间音频文件。
            initObject.put("debug_path", debugpath);

            //工作目录路径，SDK从该路径读取配置文件
            initObject.put("workspace", workpath); // 必填

            // 设置为离线合成
            //  Local = 0,
            //  Mix = 1,  // init local and cloud
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

    @Override
    public void onItemSelected(AdapterView<?> view, View arg1, int arg2, long arg3) {
        if (view == mFontSpin) {
            local_tts_instance.cancelTts("");
            mAudioTrack.stop();

            String fontName = mFontSpin.getSelectedItem().toString();
            if (fontName.equals(downloadingOthers)) {
                if (mInitialized) {
                    // 表示鉴权通过, 下载sdk_code对应的离线语音包，便于演示
                    // 注意：此工程中语音包的下载链接可能会发生变化，用户集成SDK需自行管理这些离线语音包
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Map<String, String> voice_files_map = Utils.getVoiceFilesMap(g_sdk_code);
                            int totalCount = voice_files_map.size();
                            Log.i(TAG, "voice files count:" + totalCount);
                            int curCount = 0;
                            for (Map.Entry<String, String> entry : voice_files_map.entrySet()) {
                                String fileName = entry.getKey();
                                String downloadLink = entry.getValue();

                                String showDownload = "当前下载 " + curCount + "/" + totalCount + " 离线语音包...";
                                Log.i(TAG, showDownload);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mEditView.setText(showDownload);
                                    }
                                });

                                Log.i(TAG, "Downloading ...");
                                boolean ret = Utils.downloadZipFile(fileName, downloadLink, mFontNameDefaultRootPath);
                                if (ret) {
                                    curCount++;
                                }
                            }

                            String showDownload = "当前下载完成 " + curCount + "/" + totalCount + " 离线语音包";
                            ToastText(showDownload);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mEditView.setText(CN_PREVIEW);
                                }
                            });
                        }
                    });

                    thread.start();

                    try {
                        // 等待离线语音包下载完成
                        thread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // 重新加载当前可用离线语音包
                    getFontList();
                    // 默认选择第一个发音人
                    mFontSpin.setSelection(0);
                    fontName = mFontSpin.getSelectedItem().toString();
                }
            }

            // 设置发音人文件的绝对路径
            // 例如：/data/user/0/mit.alibaba.nuidemo/files/asr_my/tts/voices/aijia
            String fullName = mFontNameDefaultRootPath + fontName;
            if (!Utils.isExist(fullName)) {
                Log.e(TAG, fullName + " does not exist");
                ToastText("文件:" + fullName + " 不存在, 请查看Demo中的说明, 确认路径是否正确.");
                return;
            } else {
                Log.i(TAG, "select font_name to " + fullName);
            }
            mFontName = fontName;
            // 切换发音人：一定要输入全路径名称
            local_tts_instance.setparamTts("extend_font_name", fullName);
            UpdateAudioPlayerSampleRate();
        } else if (view == mSpeedSpin) {
            // [0.5, 2], 默认1.0. 低于0.5则重置为0.5, 高于2则重置为2
            // 语速, 值越大语速越快.
            // 0.5可理解为正常语速的0.5倍速
            // 2 可理解为正常语速的2倍速
            local_tts_instance.setparamTts("speed_level", mSpeedSpin.getSelectedItem().toString());
        } else if (view == mPitchSpin) {
            // 声调，值越大声音越尖锐。
            local_tts_instance.setparamTts("pitch_level", mPitchSpin.getSelectedItem().toString());
        } else if (view == mVolumeSpin) {
            // (0，2]，默认1.0
            // 音量，值越大音量越大。
            // 为避免截幅引入“吱吱”噪声，建议取值限定在1.5以下。
            String volume = mVolumeSpin.getSelectedItem().toString();
            local_tts_instance.setparamTts("volume", volume);
        } else if (view == mFormatSpin) {
            // 离线语音合成只支持PCM
            mEncodeType = mFormatSpin.getSelectedItem().toString();
            local_tts_instance.cancelTts("");
            mAudioTrack.stop();
            local_tts_instance.setparamTts("encode_type", mEncodeType);
            if (mEncodeType.equals("mp3")) {
                showText(eventView, "当前DEMO无法播放MP3, 需用户在实际产品中自行实现。仅打开<音频保存>存下MP3文件。");
                ToastText("当前DEMO无法播放MP3, 需用户在实际产品中自行实现。仅打开<音频保存>存下MP3文件。");
            }
        } else if (view == mCloudFontSpin) {
            cloud_tts_instance.cancelTts("");
            mAudioTrack.stop();
            String font_name = mCloudFontSpin.getSelectedItem().toString();
            String[] parts = font_name.split("-");
            if (parts.length > 1) {
                mCloudFontName = parts[0];
            } else {
                mCloudFontName = font_name;
            }
            cloud_tts_instance.setparamTts("font_name", mFontName);
        } else if (view == mCloudSpeedSpin) {
            // [0.5, 2], 默认1.0. 低于0.5则重置为0.5, 高于2则重置为2
            // 语速, 值越大语速越快.
            // 0.5可理解为正常语速的0.5倍速
            // 2 可理解为正常语速的2倍速
            cloud_tts_instance.setparamTts("speed_level", mCloudSpeedSpin.getSelectedItem().toString());
        } else if (view == mCloudPitchSpin) {
            // 声调，值越大声音越尖锐。
            cloud_tts_instance.setparamTts("pitch_level", mCloudPitchSpin.getSelectedItem().toString());
        } else if (view == mCloudVolumeSpin) {
            // (0，2]，默认1.0
            // 音量，值越大音量越大。
            // 为避免截幅引入“吱吱”噪声，建议取值限定在1.5以下。
            String volume = mCloudVolumeSpin.getSelectedItem().toString();
            cloud_tts_instance.setparamTts("volume", volume);
        } else if (view == mCloudFormatSpin) {
            mCloudEncodeType = mCloudFormatSpin.getSelectedItem().toString();
            cloud_tts_instance.cancelTts("");
            mAudioTrack.stop();
            cloud_tts_instance.setparamTts("encode_type", mCloudEncodeType);
            if (mCloudEncodeType.equals("mp3")) {
                showText(eventView, "当前DEMO无法播放MP3, 需用户在实际产品中自行实现。仅打开<音频保存>存下MP3文件。");
                ToastText("当前DEMO无法播放MP3, 需用户在实际产品中自行实现。仅打开<音频保存>存下MP3文件。");
            }
        }
    }
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {}

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

    private void ToastText(String text) {
        final String str = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TtsMixActivity.this, str, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void UpdateAudioPlayerSampleRate() {
        // 获取当前模型采样率
        String samplerate_s = local_tts_instance.getparamTts("model_sample_rate");
        if (samplerate_s != null) {
            mAudioTrack.setSampleRate(Integer.parseInt(samplerate_s));
        }
    }
}
