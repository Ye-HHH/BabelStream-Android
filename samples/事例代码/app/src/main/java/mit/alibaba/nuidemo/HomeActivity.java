/*
 * 测试demo入口
 */

package mit.alibaba.nuidemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class HomeActivity extends Activity {
    private static final String TAG = "HomePage";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        TextView versionView = (TextView)findViewById(R.id.versionView);

        final String demoVersion = "Demo Version: " + getGitRevision();
        Log.i(TAG, demoVersion);

        versionView.setText(
                demoVersion +
                        "\n进入具体示例后，有弹窗提示内部SDK版本号\n实际开发切不可将账号信息存储在端侧");

        Button btn_nls = findViewById(R.id.button);
        btn_nls.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

        TextView textView_nls = (TextView)findViewById(R.id.textView17);
        textView_nls.setText("智能语音交互(NLS)提供语音合成、长文本语音合成、流式语音合成、离线语音合成、一句话识别、实时语音识别、录音文件识别、听悟等服务。");

        Button btn_dash = findViewById(R.id.button2);
        btn_dash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, DashScopeMainActivity.class);
                startActivity(intent);
            }
        });

        TextView textView_dash = (TextView)findViewById(R.id.textView21);
        textView_dash.setText("大模型服务平台百炼(DashScope)提供Paraformer/Gummy实时语音识别、实时语音翻译、录音文件识别、语音合成CosyVoice/Sambert等服务。");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private String getGitRevision(){
        return BuildConfig.gitVersionId;
    }
}
