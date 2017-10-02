package furymonkey.lightsensorcontrolbrightness;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;

import java.io.IOException;

import furymonkey.lightsensorcontrolbrightness.utils.Kalman;
import furymonkey.lightsensorcontrolbrightness.utils.SlidingWindow;

/**
 * Created by furymonkey on 2017-06-20.
 */

public class DetectService extends Service {
    private final int CONST_BLINK_THREASHOLD = 10;
    private final int CONST_INTERVAL_TIME = 1; // 분 단위

    // 이전 프레임을 카메라로 부터 특정 rate로 받아오고 해당 프레임들을 빠르게 프로세서에게 보낸다.
    private CameraSource mCameraSource = null;
    private Kalman mLightKalman;
    private float mMax = 500;   // Set Max value as 500 for normal condition

    private Handler mHandler;
    private Runnable mRunnable;

    private SensorManager mSensorManager;
    private SlidingWindow mSlidingWindow;
    private long mSetMinute = 0;
    private boolean isSetMinute = false;
    private int state = 0;
    private int prevState = 0;
    private int mNumOfBlink = 0;

    private IBinder mIBinder = new DetectorBinder();

    private SensorEventListener mLightSensorListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        // called when sensor value have changed
        @Override
        public void onSensorChanged(SensorEvent event) {
            // The light sensor returns a single value.
            // Many sensors return 3 values, one for each axis.
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                float light = event.values[0];

                setWindowBrightness(light);
            }
        }

        private void setWindowBrightness(float light) {
            float windowBrightness = 0;
            // Log.d("Size", "SSSS");
            // Log.d("Light", String.format("%f", light));

            if (mMax != 0)
                windowBrightness = mLightKalman.update(light) / mMax;
            else
                windowBrightness = mLightKalman.getCurData();

            if (windowBrightness < 0.1f)
                windowBrightness = 0.1f;
            else if(windowBrightness > 1f)
                windowBrightness = 1f;
            /*
            if (windowBrightness < 0.1f || windowBrightness > 1f)
                return;
            */
            // Android Control Brightness of Windows, progress variable will join the progress of control brightness
            android.provider.Settings.System.putInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS, (int) (mMax * windowBrightness));
            return;
        }
    };

    class DetectorBinder extends Binder {
        DetectService getService() {
            return DetectService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // 다른 컴포넌트가 bindService()를 호출하여 서비스와 연결을 시도할 경우 이 메서드가 호출이 된다.
        // 이 메서드에서 IBinder를 반환해서 서비스와 컴포넌트가 통신을 하는데 사용하는 인터페이스를 제공해야 한다.
        // 만약 시작 타입 서비를 구현했다면 null을 반환하게 된다.
        return mIBinder;
    }

    @Override
    public void onCreate() {
        // 서비스가 처음으로 생성될 때 호출이 된다. 이 메서드 안에서 초기의 설정 작업을 하면 되고 서비스가 이미 실행중일 경우에는
        // 해당 메서드가 호출되지 않는다. 즉, 초기에 정의해야 하는 정보들을 여기서 정의한다.
        float curBrightnessValue = 0;
        try {
            curBrightnessValue = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        mSlidingWindow = new SlidingWindow(5);
        // get current set brightness

        mLightKalman = new Kalman((float) curBrightnessValue);
        // 카메라에 접근하기 전에 접근허가가 되었는지 확인한다. 만약 접근허가가 나지 않았다면, 접근 허가를 요청한다.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        // 카메라 소스를 생성한다.
        try {
            createCameraSource();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 조도 센서를 받기 위한 센서 매니저 객체를 생성한다.
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // 조도 센서를 생성한다.
        Sensor lightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        mSensorManager.registerListener(mLightSensorListener,
                mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT),
                SensorManager.SENSOR_DELAY_NORMAL);

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 다른 컴포넌트가 startService()를 호출하여 서비스가 시작되면 이 메서드가 호출이 된다.
        // 만약 연결 서비스를 구현할 경우 이 메서드를 재정의할 필요가 없다.
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(mLightSensorListener);

        if (mCameraSource != null)
            mCameraSource.release();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    private void createCameraSource() throws IOException {
        // Log.d("Love", "Love");
        // 카메라를 만들고 시작합니다.

        Context context = getApplicationContext(); // 현재 Application 컨텍스트에 대한 정보를 불러오고, 이 내용을 context변수에 저장한다.

        // FaceDetector를 만들고 몇몇 기능을 설정한다.
        FaceDetector faceDetector = new FaceDetector.Builder(context)
                .setTrackingEnabled(false)
                .setClassificationType(FaceDetector.ALL_LANDMARKS)
                .build();

        faceDetector.setProcessor(new LargestFaceFocusingProcessor(
                faceDetector, new EyeBlinkTracker()
        ));

        // 카메라 디텍터를 생성하고 설정한다.
        mCameraSource = new CameraSource.Builder(context, faceDetector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(20.0f)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Log.d("Graphic", "Start!");
        mCameraSource.start();
    }

    private class EyeBlinkTracker extends Tracker<Face> {
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            //if(mSlidingWindow.getAverage() < CONST_BLINK_THREASHOLD && mNumOfBlink >= 5)
            //    Toast.makeText(getApplicationContext(), "Please blink little more!", Toast.LENGTH_SHORT).show();

            if(mHandler == null) {
                mHandler = new Handler(Looper.getMainLooper());
                mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if(mSlidingWindow.getAverage() < CONST_BLINK_THREASHOLD) {
                            Toast.makeText(getApplicationContext(), "Please blink more frequently!",
                                    Toast.LENGTH_LONG).show();
                            mHandler.postDelayed(mRunnable, 1000 * 30 * CONST_INTERVAL_TIME);
                        }
                    }
                };
                mHandler.postDelayed(mRunnable, 1000 * 30 * CONST_INTERVAL_TIME);
            }

            if (face == null)
                return;

            long curTimeSeconds = System.currentTimeMillis() / 1000;

            if (!isSetMinute) {
                mSetMinute = curTimeSeconds;
                // Log.d("Size", String.format("Minutes : %d", mSetMinute));
                isSetMinute = true;
            }

            if (isSetMinute && curTimeSeconds > mSetMinute + 10) {
                mSlidingWindow.setData(mNumOfBlink);
                mNumOfBlink = 0;
                isSetMinute = false;
            }

            Log.d("Size", String.format("Size of SlidingWindow : %d", mSlidingWindow.mData.size()));

            for(int i=0; i<mSlidingWindow.mData.size(); i++)
                Log.d("Size", String.format("%d th : %d", i, mSlidingWindow.mData.get(i)));

            double left_prob = face.getIsLeftEyeOpenProbability();
            double right_prob = face.getIsRightEyeOpenProbability();

            double prob = (left_prob + right_prob) / 2;
            if(prob < 0)
                prob = 0.5;

            // Log.d("Size", String.format("Num of bliink : %f", prob));
            if (left_prob != Face.UNCOMPUTED_PROBABILITY && right_prob != Face.UNCOMPUTED_PROBABILITY) {
                prevState = state;
                state = prob < 0.5 ? 0 : 1;

                if (prevState != state && state == 1)
                    mNumOfBlink++;
            }
        }
    }
}