package com.mahmoodms.bluetooth.emghandcontrol;

import android.provider.BaseColumns;

/**
 * Created by hemanthc98 on 9/23/17.
 */

public class TrainingDataContract {

    private TrainingDataContract() {}

    public static class TrainingData implements BaseColumns {
        public static final String TABLE_NAME = "entry";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_DATA = "data";
    }
}
