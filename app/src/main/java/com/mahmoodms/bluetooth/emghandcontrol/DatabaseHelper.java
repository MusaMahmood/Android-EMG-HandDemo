package com.mahmoodms.bluetooth.emghandcontrol;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

/**
 * Created by hemanthc98 on 9/23/17.
 */

public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "TrainingData.db";
    public static final int DATABASE_VERSION = 1;
    private static final String CREATE_TABLE = "CREATE TABLE " + TrainingDataContract.TrainingData.TABLE_NAME +
            " (" + TrainingDataContract.TrainingData._ID + " INTEGER PRIMARY KEY," + TrainingDataContract.TrainingData.COLUMN_NAME
            + " TEXT," + TrainingDataContract.TrainingData.COLUMN_DATA + " TEXT)";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TrainingDataContract.TrainingData.TABLE_NAME);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public boolean insertData(String name, String arr) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(TrainingDataContract.TrainingData.COLUMN_NAME, name);
            values.put(TrainingDataContract.TrainingData.COLUMN_DATA, arr);
            db.insert(TrainingDataContract.TrainingData.TABLE_NAME, null, values);
            return true;
        } catch(SQLiteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public ArrayList<String> getAllData() {
        ArrayList<String> list = new ArrayList<String>();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery("select " + TrainingDataContract.TrainingData.COLUMN_NAME + " from " + TrainingDataContract.TrainingData.TABLE_NAME, null);
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                list.add(cursor.getString(cursor.getColumnIndex(TrainingDataContract.TrainingData.COLUMN_NAME)));
                cursor.moveToNext();
            }
        } catch(SQLiteException e) {
            e.printStackTrace();
        }
        return list;
    }

}
