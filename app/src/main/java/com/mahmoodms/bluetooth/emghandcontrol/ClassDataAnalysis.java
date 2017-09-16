package com.mahmoodms.bluetooth.emghandcontrol;

import android.util.Log;
import android.widget.Toast;

import com.google.common.primitives.Doubles;

import java.util.List;

/**
 * Created by mmahmood31 on 9/12/2017.
 */

public class ClassDataAnalysis {
    private static final String TAG = ClassDataAnalysis.class.getSimpleName();
    private static double data[][];
    public boolean ERROR = false;

    public ClassDataAnalysis(List<String[]> strings, int length) {
        int stringSize = strings.size();
        data = new double[4][length];
        //Check data integrity first:
        if(strings.size() >= length) {
            for (int i = 0; i < length; i++) {
                if(strings.get(i).length!=4) {
                    Log.e(TAG, "ERROR! - INCORRECT LENGTH("+String.valueOf(strings.get(i).length)+")!; BREAK @ [ " + String.valueOf(i) + "/" + String.valueOf(stringSize) + "]");
                    return;
                }
            }
            for (int i = 0; i < length; i++) {
                for (int j = 0; j < 4; j++) { //add to array
                    if (strings.get(i).length==4)
                        data[j][i] = Double.parseDouble(strings.get(i)[j]);
                }
            }
            Log.e(TAG,"Final Len: ["+String.valueOf(data[0].length)+"]");
            this.ERROR = false;
        } else {
            Log.e(TAG, "ERROR! - Not enough data. ");
            this.ERROR = true;
        }
    }

    static double[] concatAll() {
        if(data[0]!=null) return Doubles.concat(data[0], data[1], data[2], data[3]);
        else return null;
    }

}
