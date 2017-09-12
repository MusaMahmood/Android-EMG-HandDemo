package com.mahmoodms.bluetooth.emghandcontrol;

import android.util.Log;

import com.google.common.primitives.Doubles;

import java.util.List;

/**
 * Created by mmahmood31 on 9/12/2017.
 */

public class ClassDataAnalysis {
    private static final String TAG = ClassDataAnalysis.class.getSimpleName();
    private static double data[][];

    public ClassDataAnalysis(List<String[]> strings) {
        int stringSize = strings.size();
        data = new double[4][stringSize];
        //Check data integrity first:
        int END = stringSize;
        for (int i = 0; i < stringSize; i++) {
            if(strings.get(i).length!=4) {
                END = i;
                Log.e(TAG, "INCORRECT LENGTH("+String.valueOf(strings.get(i).length)+")!; BREAK @ [ " + String.valueOf(END) + "/" + String.valueOf(stringSize) + "]");
//                return;
                break;
            }
        }

        for (int i = 0; i < END; i++) {
            for (int j = 0; j < 4; j++) {
                //add to array
                if (strings.get(i).length==4)
                    data[j][i] = Double.parseDouble(strings.get(i)[j]);
            }    
        }
        Log.e(TAG,"Final Len: ["+String.valueOf(data[0].length)+"]");
    }

    public static double[] concatAll() {
        if(data[0]!=null) return Doubles.concat(data[0], data[1], data[2], data[3]);
        else return null;
    }

}
