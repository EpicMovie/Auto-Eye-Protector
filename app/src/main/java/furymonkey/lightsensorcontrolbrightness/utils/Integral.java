package furymonkey.lightsensorcontrolbrightness.utils;

/**
 * Created by furymonkey on 2017-04-23.
 */

public class Integral {
    private float mResult = 0;
    private float mPrevData = 0;
    private long mPrevTime = 0;

    public Integral(long t){
        mPrevTime = t;
    }

    public float getResult(long curTime, float curData){
        // Log.d("show", Double.toString(mPrevData) + " " + Double.toString(curData) + " " + Long.toString(curTime - mPrevTime) + " " + Long.toString(curTime) + " " + Long.toString(mPrevTime));

        mResult += (mPrevData + curData) * (curTime - mPrevTime) / (2 * 1000); // Becase this is millisec

        mPrevTime = curTime;
        mPrevData = curData;

        return mResult;
    }
}
