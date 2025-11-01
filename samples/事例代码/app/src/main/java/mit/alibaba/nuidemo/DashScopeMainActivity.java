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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DashScopeMainActivity extends Activity implements AdapterView.OnItemClickListener {
    private static final String TAG = "DashMain";

    // 推荐终端用户使用就近地域接入域名
    private String[] url_array = {
            "wss://dashscope.aliyuncs.com/api-ws/v1/inference",
    };
    private static Integer url_select = 0;
    private static String url = "";
    private static String apikey = "";

    private static Integer access_select = 0;
    private String[] access_array = {
            "API Key",
    };

    // 注意！为方便Demo运行展示，用户设置的账号信息会存储到app的cache目录中的文件中，请注意账号安全
    private static String accessFilePath = "";

    private EditText apikeyText;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_main);
        ListView listView = (ListView) findViewById(R.id.activity_dash_list);
        ArrayList<HashMap<String, Object>> listItems = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> item = new HashMap<String, Object>();
        TextView versionView = (TextView)findViewById(R.id.versionView);

        final String demoVersion = "Demo Version: " + getGitRevision();
        Log.i(TAG, demoVersion);

        Toast.makeText(
                DashScopeMainActivity.this,
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

        TextView apikeyView = (TextView)findViewById(R.id.textView5);
        apikeyView.setText("API Key：");
        apikeyView.setVisibility(View.VISIBLE);
        apikeyText = (EditText)findViewById(R.id.editView);
        apikeyText.setText(apikey);
        apikeyText.setVisibility(View.VISIBLE);

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
                    apikeyView.setVisibility(View.VISIBLE);
                    apikeyText.setVisibility(View.VISIBLE);
                    apikeyText.setText(apikey);
                    Toast.makeText(
                            DashScopeMainActivity.this,
                            "填入API Key，此方法仅用于Demo试用",
                            Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        item = new HashMap<String, Object>();
        item.put("activity_name", " => Gummy一句话识别、翻译");
        item.put("activity_class", DashGummySpeechRecognizerActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", " => Gummy实时识别、翻译");
        item.put("activity_class", DashGummySpeechTranscriberActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", " => Paraformer实时语音识别");
        item.put("activity_class", DashParaformerSpeechTranscriberActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", " => Paraformer录音文件识别");
        item.put("activity_class", DashParaformerFileTranscriberActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", " => Fun-ASR实时语音识别");
        item.put("activity_class", DashFunAsrSpeechTranscriberActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", " => Fun-ASR录音文件识别");
        item.put("activity_class", DashFunAsrFileTranscriberActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", " => Sambert单向流式语音合成");
        item.put("activity_class", DashSambertTtsActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", " => CosyVoice/Sambert双向流式语音合成");
        item.put("activity_class", DashCosyVoiceStreamTtsActivity.class);
        listItems.add(item);

        SimpleAdapter adapter = new SimpleAdapter(this, listItems, R.layout.list_item,
                new String[] { "activity_name" }, new int[] { R.id.text_item });

        listView.setAdapter(adapter);
        listView.setDividerHeight(2);
        listView.setOnItemClickListener(this);

        versionView.setText(
                demoVersion +
                        "\n进入具体示例后，有弹窗提示内部SDK版本号\n实际开发切不可将账号信息存储在端侧");
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Map<?, ?> map = (HashMap<?, ?>) parent.getAdapter().getItem(position);
        Class<?> clazz = (Class<?>) map.get("activity_class");

        apikey = apikeyText.getText().toString();

        saveAccessFromFile(accessFilePath);

        Intent it = new Intent(this, clazz);

        it.putExtra("url", url); // 传递第一个参数
        it.putExtra("apikey", apikeyText.getText().toString()); // 传递第二个参数
        it.putExtra("accessFile", accessFilePath); // 传递第三个参数

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
                        if (jsonObject.getString("dash_url") != null &&
                                !jsonObject.getString("dash_url").isEmpty()) {
                            url = jsonObject.getString("dash_url");
                            Log.i(TAG, "Get url: " + url);
                        }
                        if (jsonObject.containsKey("dash_server_control")) {
                            url_select = jsonObject.getIntValue("dash_server_control");
                            Log.i(TAG, "Get url select: " + url_select);
                        }
                        if (apikey.isEmpty() && jsonObject.getString("dash_apikey") != null &&
                                !jsonObject.getString("dash_apikey").isEmpty()) {
                            apikey = jsonObject.getString("dash_apikey");
                            Log.i(TAG, "Get apikey: " + apikey);
                        }
                        if (jsonObject.containsKey("dash_access_select")) {
                            access_select = jsonObject.getIntValue("dash_access_select");
                            Log.i(TAG, "Get url select: " + access_select);
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
        object.put("dash_url", url);
        object.put("dash_apikey", apikey);
        object.put("dash_server_control", url_select);
        object.put("dash_access_select", access_select);
        object.put("dash_access_file", fileName);

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
