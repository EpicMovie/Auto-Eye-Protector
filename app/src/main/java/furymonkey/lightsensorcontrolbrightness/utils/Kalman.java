package furymonkey.lightsensorcontrolbrightness.utils;

/**
 * Created by furymonkey on 2017-04-18.
 */

public class Kalman {
    private float Q = 0.00001f;
    private float R = 0.001f;
    private float P = 1;
    private float X = 0;
    private float K;

    public Kalman(float init){
        X = init;
    }

    private void meausreUpdate(){
        K = (P + Q) / (P + Q + R);
        P = R * (P + Q) / (P + Q + R);
    }

    public float getCurData(){
        return X;
    }

    public float update(float measurement){
        meausreUpdate();
        X = X + (measurement - X) * K;

        return X;
    }
}
