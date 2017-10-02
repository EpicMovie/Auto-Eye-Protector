package furymonkey.lightsensorcontrolbrightness.utils;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by furymonkey on 2017-06-20.
 */

public class SlidingWindow {
    private int mWindowSize = 0;    // Set the number of windows
    private int mIteration = 0;     // Set the current window indicator
    public List<Integer> mData = new ArrayList<Integer>();

    public SlidingWindow(int windowSize){
        mWindowSize = windowSize;
    }

    public void setData(int i){
        if(mData.size() <= mWindowSize){
            Log.d("Inserted", String.format("%d", mData.size()));
            mData.add(new Integer(i));
            mIteration++;
        } else {
            if(mIteration < mWindowSize){
                mData.set(mIteration, new Integer(i));
                mIteration++;
            } else {
                mIteration = 0;
                mData.set(mIteration, new Integer(i));
            }
        }
    }
    
    public int getAverage() {
        Integer sum = 0;
        if(!mData.isEmpty()) {
            for (Integer temp : mData)
                sum += temp;
            return (int) sum.intValue() / mData.size();
        }
        return -1;
    }
}
