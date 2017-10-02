package furymonkey.lightsensorcontrolbrightness;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity  implements View.OnClickListener{
    private DetectService mService;
    private boolean isBind = false;

    private Button mStartBtn;
    private Button mStopBtn;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DetectService.DetectorBinder mBinder = (DetectService.DetectorBinder) service;
            mService = mBinder.getService();
            isBind = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            isBind = false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //버튼에 대한 참조
        mStartBtn = (Button) findViewById(R.id.start);
        mStopBtn = (Button) findViewById(R.id.stop);

        //각 버튼에 대한 리스너 연결 - OnClickListener를 확장했으므로 onClick 오버라이딩 후 this사용
        mStartBtn.setOnClickListener(this);
        mStopBtn.setOnClickListener(this);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start:
                startService(new Intent(MainActivity.this, DetectService.class)); // 서비스 시작
                break;
            case R.id.stop:
                stopService(new Intent(MainActivity.this, DetectService.class)); // 서비스 종료
                break;
       }
    }
}