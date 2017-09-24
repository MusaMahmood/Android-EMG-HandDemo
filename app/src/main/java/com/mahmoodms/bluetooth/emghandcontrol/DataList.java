package com.mahmoodms.bluetooth.emghandcontrol;

import android.app.Activity;;
import android.os.Bundle;;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class DataList extends Activity {

    private DatabaseHelper helper = new DatabaseHelper(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_list);
        ListView list = (ListView) findViewById(R.id.list);
        ArrayList<String> array = helper.getAllData();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, array);
        list.setAdapter(adapter);
    }
}
