/*
 * 测试demo入口
 */

package mit.alibaba.nuidemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.NativeNui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends Activity implements AdapterView.OnItemClickListener {
    private static final String TAG = "Main";

    private NativeNui nui_utils_instance = new NativeNui(Constants.ModeType.MODE_UTILS);
    private String app_code = nui_utils_instance.getAppCode();

    // 推荐终端用户使用就近地域接入域名
    private String[] url_array = {
            "wss://nls-gateway-cn-beijing.aliyuncs.com/ws/v1",
            "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1",
            "wss://nls-gateway-cn-shenzhen.aliyuncs.com/ws/v1",
            "wss://nls-gateway-singapore.aliyuncs.com/ws/v1"
    };
    private static Integer url_select = 0;
    private static String url = "";

    // 注意！阿里云账号appkey、accessKey、accessKeySecret均不要存储在端侧
    // 此处仅为方便Demo运行展示
    private static Integer access_select = 0;
    private static String appkey = "";
    private static String token = "";
    private static String accessKey = "";
    private static String accessKeySecret = "";
    // 只有使用离线功能才需要填写
    // 精品版为 software_nls_tts_offline， 标准版为 software_nls_tts_offline_standard
    private final static String defaultSdkCode = "只有使用离线功能才需要填写";
    private static String sdkCode = defaultSdkCode;
    private static String stsToken = "";
    private static String meetingJoinUrl = "wss://tingwu-realtime-cn-hangzhou-pre.aliyuncs.com/api/ws/v1?";
    private String[] access_array = {
            "Appkey+Token",
            "Appkey+AK+SK",
            "Appkey+STS_AK+STS_SK+STS_Token",
            "听悟实时推流MeetingJoinUrl"
    };

    // 注意！为方便Demo运行展示，用户设置的账号信息会存储到app的cache目录中的文件中，请注意账号安全
    private static String accessFilePath = "";

    private EditText appkeyText;
    private EditText tokenText;
    private EditText stsTokenText;
    private EditText akText;
    private EditText skText;
    private EditText sdkCodeText;
    private EditText meetingUrlText;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ListView listView = (ListView) findViewById(R.id.activity_list);
        ArrayList<HashMap<String, Object>> listItems = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> item = new HashMap<String, Object>();
        TextView versionView = (TextView)findViewById(R.id.versionView);

        final String demoVersion = "Demo Version: " + getGitRevision();
        Log.i(TAG, demoVersion);

        Log.i(TAG, "Current APP Code:" + app_code);

        Toast.makeText(
                MainActivity.this,
                "注意! 实际产品请不要在端侧保存任何账号相关的信息！",
                Toast.LENGTH_SHORT).show();

        String debug_path = getExternalCacheDir().getAbsolutePath();
        Utils.createDir(debug_path);
        parseAccessFromFile(debug_path, ".nui_access");

        TextView serverView = (TextView)findViewById(R.id.textView2);
        serverView.setText("\n服务地址：");

        Spinner urlSelectSpinner = (Spinner) findViewById(R.id.spinner);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, url_array);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        urlSelectSpinner.setAdapter(arrayAdapter);
        urlSelectSpinner.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light));
        urlSelectSpinner.setSelection(url_select);
        urlSelectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                url = parent.getItemAtPosition(position).toString();
                url_select = position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        TextView appkeyView = (TextView)findViewById(R.id.textView5);
        appkeyView.setText("AppKey：");
        appkeyView.setVisibility(View.VISIBLE);
        appkeyText = (EditText)findViewById(R.id.editView);
        appkeyText.setText(appkey);
        appkeyText.setVisibility(View.VISIBLE);

        TextView tokenView = (TextView)findViewById(R.id.textView8);
        tokenView.setText("Token：");
        tokenView.setVisibility(View.VISIBLE);
        tokenText = (EditText)findViewById(R.id.editView4);
        tokenText.setText(token);
        tokenText.setVisibility(View.VISIBLE);

        TextView stsTokenView = (TextView)findViewById(R.id.textView3);
        stsTokenView.setText("STS_Token：");
        stsTokenView.setVisibility(View.VISIBLE);
        stsTokenText = (EditText)findViewById(R.id.editView5);
        stsTokenText.setText(stsToken);
        stsTokenText.setVisibility(View.VISIBLE);

        TextView akView = (TextView)findViewById(R.id.textView6);
        akView.setText("AccessKey：");
        akView.setVisibility(View.VISIBLE);
        akText = (EditText)findViewById(R.id.editView2);
        akText.setText(accessKey);
        akText.setVisibility(View.VISIBLE);

        TextView skView = (TextView)findViewById(R.id.textView7);
        skView.setText("AccessKeySecret：");
        skView.setVisibility(View.VISIBLE);
        skText = (EditText)findViewById(R.id.editView3);
        skText.setText(accessKeySecret);
        skText.setVisibility(View.VISIBLE);

        TextView sdkCodeView = (TextView)findViewById(R.id.textView9);
        sdkCodeView.setText("SDK_CODE：");
        sdkCodeView.setVisibility(View.VISIBLE);
        sdkCodeText = (EditText)findViewById(R.id.editView6);
        sdkCodeText.setText(sdkCode);
        sdkCodeText.setVisibility(View.VISIBLE);

        TextView meetingUrlView = (TextView)findViewById(R.id.textView12);
        meetingUrlView.setText("听悟实时推流MeetingJoinUrl：");
        meetingUrlView.setVisibility(View.GONE);
        meetingUrlText = (EditText)findViewById(R.id.editView9);
        meetingUrlText.setText(meetingJoinUrl);
        meetingUrlText.setVisibility(View.GONE);

        TextView accessView = (TextView)findViewById(R.id.textView4);
        accessView.setText("账号方式：");

        Spinner accessSelectSpinner = (Spinner) findViewById(R.id.spinner2);
        ArrayAdapter<String> arrayAdapter2 = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, access_array);
        arrayAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        accessSelectSpinner.setAdapter(arrayAdapter2);
        accessSelectSpinner.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light));
        accessSelectSpinner.setSelection(access_select);
        accessSelectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                access_select = position;
                if (access_select == 0) {
                    appkeyView.setVisibility(View.VISIBLE);
                    appkeyText.setVisibility(View.VISIBLE);
                    appkeyText.setText(appkey);
                    tokenView.setVisibility(View.VISIBLE);
                    tokenText.setVisibility(View.VISIBLE);
                    tokenText.setText(token);

                    skView.setVisibility(View.GONE);
                    skText.setVisibility(View.GONE);
                    skText.setText("");
                    akView.setVisibility(View.GONE);
                    akText.setVisibility(View.GONE);
                    akText.setText("");
                    stsTokenView.setVisibility(View.GONE);
                    stsTokenText.setVisibility(View.GONE);
                    stsTokenText.setText("");
                    sdkCodeView.setVisibility(View.GONE);
                    sdkCodeText.setVisibility(View.GONE);
                    sdkCodeText.setText("");
                    meetingUrlView.setVisibility(View.GONE);
                    meetingUrlText.setVisibility(View.GONE);
                    Toast.makeText(
                            MainActivity.this,
                            "填入AppKey和Token，Token具有时效性，此方法仅用于Demo试用",
                            Toast.LENGTH_SHORT).show();
                } else if (access_select == 1) {
                    appkeyView.setVisibility(View.VISIBLE);
                    appkeyText.setVisibility(View.VISIBLE);
                    appkeyText.setText(appkey);
                    akView.setText("AccessKey：");
                    akView.setVisibility(View.VISIBLE);
                    akText.setVisibility(View.VISIBLE);
                    akText.setText(accessKey);
                    skView.setText("AccessKeySecret：");
                    skView.setVisibility(View.VISIBLE);
                    skText.setVisibility(View.VISIBLE);
                    skText.setText(accessKeySecret);
                    sdkCodeView.setVisibility(View.VISIBLE);
                    sdkCodeText.setVisibility(View.VISIBLE);
                    sdkCodeText.setText(sdkCode);

                    tokenView.setVisibility(View.GONE);
                    tokenText.setVisibility(View.GONE);
                    tokenText.setText("");
                    stsTokenView.setVisibility(View.GONE);
                    stsTokenText.setVisibility(View.GONE);
                    stsTokenText.setText("");
                    meetingUrlView.setVisibility(View.GONE);
                    meetingUrlText.setVisibility(View.GONE);
                    Toast.makeText(
                            MainActivity.this,
                            "填入AppKey、AccessKey和AccessKeySecret，此方法仅用于Demo试用",
                            Toast.LENGTH_SHORT).show();
                } else if (access_select == 2) {
                    appkeyView.setVisibility(View.VISIBLE);
                    appkeyText.setVisibility(View.VISIBLE);
                    appkeyText.setText(appkey);
                    akView.setText("STS_AccessKey：");
                    akView.setVisibility(View.VISIBLE);
                    akText.setVisibility(View.VISIBLE);
                    akText.setText(accessKey);
                    skView.setText("STS_AccessKeySecret：");
                    skView.setVisibility(View.VISIBLE);
                    skText.setVisibility(View.VISIBLE);
                    skText.setText(accessKeySecret);
                    stsTokenView.setVisibility(View.VISIBLE);
                    stsTokenText.setVisibility(View.VISIBLE);
                    stsTokenText.setText(stsToken);
                    sdkCodeView.setVisibility(View.VISIBLE);
                    sdkCodeText.setVisibility(View.VISIBLE);
                    sdkCodeText.setText(sdkCode);

                    tokenView.setVisibility(View.GONE);
                    tokenText.setVisibility(View.GONE);
                    tokenText.setText("");
                    meetingUrlView.setVisibility(View.GONE);
                    meetingUrlText.setVisibility(View.GONE);
                    Toast.makeText(
                            MainActivity.this,
                            "填入AppKey、STS_AccessKey和STS_AccessKeySecret，此方法仅用于Demo试用",
                            Toast.LENGTH_SHORT).show();
                } else if (access_select == 3) {
                    appkeyView.setVisibility(View.GONE);
                    appkeyText.setVisibility(View.GONE);
                    akView.setVisibility(View.GONE);
                    akText.setVisibility(View.GONE);
                    skView.setVisibility(View.GONE);
                    skText.setVisibility(View.GONE);
                    stsTokenView.setVisibility(View.GONE);
                    stsTokenText.setVisibility(View.GONE);
                    sdkCodeView.setVisibility(View.GONE);
                    sdkCodeText.setVisibility(View.GONE);
                    tokenView.setVisibility(View.GONE);
                    tokenText.setVisibility(View.GONE);

                    meetingUrlView.setVisibility(View.VISIBLE);
                    meetingUrlText.setVisibility(View.VISIBLE);
                    meetingUrlText.setText(meetingJoinUrl);
                    Toast.makeText(
                            MainActivity.this,
                            "填入创建听悟实时记录任务返回的MeetingJoinUrl，此方法仅用于听悟实时推流",
                            Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        item = new HashMap<String, Object>();
        item.put("activity_name", " => 一句话识别");
        item.put("activity_class", SpeechRecognizerActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", " => 实时转写/实时识别");
        item.put("activity_class", SpeechTranscriberActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", " => 实时记录/实时推流(听悟)");
        item.put("activity_class", RealtimeMeetingActivity.class);
        listItems.add(item);

        item = new HashMap<>();
        item.put("activity_name", " => 文件转写极速版");
        item.put("activity_class", FileTranscriberActivity.class);
        listItems.add(item);

        item = new HashMap<>();
        item.put("activity_name", " => 流式语音合成(流式输入流式输出)/CosyVoice大模型");
        item.put("activity_class", StreamInputTtsBasicActivity.class);
        listItems.add(item);

        item = new HashMap<>();
        item.put("activity_name", " => 语音合成(流式输出)");
        item.put("activity_class", TtsBasicActivity.class);
        listItems.add(item);

        if (app_code.equals("029") || app_code.equals("028") || app_code.equals("015") ||
                app_code.equals("038") || app_code.equals("039")) {
            item = new HashMap<>();
            item.put("activity_name", " => 离线语音合成(离线功能-部分版本支持)");
            item.put("activity_class", TtsLocalActivity.class);
            listItems.add(item);

            item = new HashMap<>();
            item.put("activity_name", " => 离在线语音合成双实例(离线功能-部分版本支持)");
            item.put("activity_class", TtsMixActivity.class);
            listItems.add(item);
        }

        if (app_code.equals("029") || app_code.equals("015") || app_code.equals("039")) {
            item = new HashMap<String, Object>();
            item.put("activity_name", " => 唤醒识别(离线功能-部分版本支持)");
            item.put("activity_class", WakeupAndSpeechRecognizerActivity.class);
            listItems.add(item);

            item = new HashMap<String, Object>();
            item.put("activity_name", " => 唤醒(离线功能-部分版本支持)");
            item.put("activity_class", OnlyWakeupActivity.class);
            listItems.add(item);

            item = new HashMap<String, Object>();
            item.put("activity_name", " => 多实例混合使用(重点在于测试)");
            item.put("activity_class", WakeupAndTtsMixTestActivity.class);
            listItems.add(item);
        }

        // 此为内部代码，我们不对外直接提供具有离线语音识别能力的SDK和Demo，如有业务需求，请联系我们的产品或者商务的同事。
//        item = new HashMap<>();
//        item.put("activity_name","本地一句话识别(暂不对外)");
//        item.put("activity_class", LocalSpeechRecognizerActivity.class);
//        listItems.add(item);

//        item = new HashMap<String, Object>();
//        item.put("activity_name", "对话");
//        item.put("activity_class", DialogActivity.class);
//        listItems.add(item);

        // 此为内部代码，请查看 SpeechTranscriberActivity.java
//        item = new HashMap<>();
//        item.put("activity_name","实时转写独立接口样例(暂不对外)");
//        item.put("activity_class", SpeechTranscriberNewActivity.class);
//        listItems.add(item);

        // 此为内部代码，请查看 SpeechRecognizerActivity.java
//        item = new HashMap<>();
//        item.put("activity_name","一句话识别独立接口样例(暂不对外)");
//        item.put("activity_class", SpeechRecognizerNewActivity.class);
//        listItems.add(item);

        SimpleAdapter adapter = new SimpleAdapter(this, listItems, R.layout.list_item,
                new String[] { "activity_name" }, new int[] { R.id.text_item });

        listView.setAdapter(adapter);
        listView.setDividerHeight(2);
        listView.setOnItemClickListener(this);

        versionView.setText("进入具体示例后，有弹窗提示内部SDK版本号\n实际开发切不可将账号信息存储在端侧");
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Map<?, ?> map = (HashMap<?, ?>) parent.getAdapter().getItem(position);
        Class<?> clazz = (Class<?>) map.get("activity_class");

        appkey = appkeyText.getText().toString();
        token = tokenText.getText().toString();
        stsToken = stsTokenText.getText().toString();
        accessKey = akText.getText().toString();
        accessKeySecret = skText.getText().toString();
        sdkCode = sdkCodeText.getText().toString();
        meetingJoinUrl = meetingUrlText.getText().toString();

        saveAccessFromFile(accessFilePath);

        Intent it = new Intent(this, clazz);

        it.putExtra("url", url); // 传递第一个参数
        it.putExtra("appkey", appkeyText.getText().toString()); // 传递第二个参数
        it.putExtra("token", tokenText.getText().toString()); // 传递第三个参数
        it.putExtra("accessKey", akText.getText().toString()); // 传递第四个参数
        it.putExtra("accessKeySecret", skText.getText().toString()); // 传递第五个参数
        it.putExtra("stsToken", stsTokenText.getText().toString()); // 传递第六个参数
        it.putExtra("sdkCode", sdkCodeText.getText().toString()); // 传递第七个参数
        it.putExtra("meetingJoinUrl", meetingUrlText.getText().toString()); // 传递第八个参数
        it.putExtra("accessFile", accessFilePath); // 传递第九个参数

        this.startActivity(it);
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private String getGitRevision(){
        return BuildConfig.gitVersionId;
    }

    private void parseAccessFromFile(String dir, String fileName) {
        Log.i(TAG, "ready to open " + fileName + " from " + dir);
        accessFilePath = dir + "/" + fileName;
        File directory = new File(dir);
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().equals(fileName)) {
                    try {
                        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
                        StringBuilder stringBuilder = new StringBuilder();
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            stringBuilder.append(line);
                        }
                        bufferedReader.close();

                        // 读取JSON内容并获取String和int对象
                        String jsonContent = stringBuilder.toString();
                        JSONObject jsonObject = JSON.parseObject(jsonContent);
                        if (jsonObject.getString("url") != null && !jsonObject.getString("url").isEmpty()) {
                            url = jsonObject.getString("url");
                            Log.i(TAG, "Get url: " + url);
                        }
                        if (jsonObject.containsKey("server_control")) {
                            url_select = jsonObject.getIntValue("server_control");
                            Log.i(TAG, "Get url select: " + url_select);
                        }
                        if (appkey.isEmpty() && jsonObject.getString("appkey") != null &&
                                !jsonObject.getString("appkey").isEmpty()) {
                            appkey = jsonObject.getString("appkey");
                            Log.i(TAG, "Get appkey: " + appkey);
                        }
                        if (token.isEmpty() && jsonObject.getString("token") != null &&
                                !jsonObject.getString("token").isEmpty()) {
                            token = jsonObject.getString("token");
                            Log.i(TAG, "Get token: " + token);
                        }
                        if (stsToken.isEmpty() && jsonObject.getString("sts_token") != null &&
                                !jsonObject.getString("sts_token").isEmpty()) {
                            stsToken = jsonObject.getString("sts_token");
                            Log.i(TAG, "Get sts_token: " + stsToken);
                        }
                        if (accessKey.isEmpty() && jsonObject.getString("ak") != null &&
                                !jsonObject.getString("ak").isEmpty()) {
                            accessKey = jsonObject.getString("ak");
                            Log.i(TAG, "Get accessKey: " + accessKey);
                        }
                        if (accessKeySecret.isEmpty() && jsonObject.getString("sk") != null
                                && !jsonObject.getString("sk").isEmpty()) {
                            accessKeySecret = jsonObject.getString("sk");
                            Log.i(TAG, "Get accessKeySecret: " + accessKeySecret);
                        }
                        if (jsonObject.containsKey("access_select")) {
                            access_select = jsonObject.getIntValue("access_select");
                            Log.i(TAG, "Get url select: " + access_select);
                        }
                        if (sdkCode.equals(defaultSdkCode) &&
                                jsonObject.getString("sdk_code") != null &&
                                !jsonObject.getString("sdk_code").isEmpty()) {
                            String oldSdkCode = jsonObject.getString("sdk_code");
                            if (!oldSdkCode.equals(defaultSdkCode)) {
                                sdkCode = jsonObject.getString("sdk_code");
                            }
                            Log.i(TAG, "Get sdk_code: " + sdkCode);
                        }
                        if (meetingJoinUrl.isEmpty() && jsonObject.getString("meeting_url") != null &&
                                !jsonObject.getString("meeting_url").isEmpty()) {
                            meetingJoinUrl = jsonObject.getString("meeting_url");
                            Log.i(TAG, "Get meeting_join_url: " + meetingJoinUrl);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            Log.e(TAG, "dir " + dir + " is invalid.");
        }
    }

    private void saveAccessFromFile(String fileName) {
        JSONObject object = new JSONObject();
        object.put("url", url);
        object.put("appkey", appkey);
        object.put("token", token);
        object.put("sts_token", stsToken);
        object.put("ak", accessKey);
        object.put("sk", accessKeySecret);
        object.put("server_control", url_select);
        object.put("access_select", access_select);
        object.put("sdk_code", sdkCode);
        object.put("meeting_url", meetingJoinUrl);
        object.put("access_file", fileName);

        Log.i(TAG, "Save info: " + object.toString() + " to " + fileName);

        try {
            FileWriter fileWriter = new FileWriter(fileName);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(object.toString());
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
