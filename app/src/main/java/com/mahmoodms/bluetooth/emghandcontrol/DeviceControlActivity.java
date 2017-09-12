package com.mahmoodms.bluetooth.emghandcontrol;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.androidplot.Plot;
import com.androidplot.util.Redrawer;
import com.beele.BluetoothLe;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Doubles;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Created by mahmoodms on 5/31/2016.
 */

public class DeviceControlActivity extends Activity implements BluetoothLe.BluetoothLeListener {
    // Graphing Variables:
    private GraphAdapter mGraphAdapterCh1;
    private GraphAdapter mGraphAdapterCh2;
    private GraphAdapter mGraphAdapterCh3;
    public XYPlotAdapter mXYPlotAdapterCh1;
    public XYPlotAdapter mXYPlotAdapterCh2;
    public XYPlotAdapter mXYPlotAdapterCh3;
    public static Redrawer redrawer;
    private boolean plotImplicitXVals = false;
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    //LocalVars
    private String mDeviceName;
    private String mDeviceAddress;
    private boolean mConnected;
    //Class instance variable
    private BluetoothLe mBluetoothLe;
    private BluetoothManager mBluetoothManager = null;
    //Connecting to Multiple Devices
    private String[] deviceMacAddresses = null;
    private BluetoothDevice[] mBluetoothDeviceArray = null;
    private BluetoothGatt[] mBluetoothGattArray = null;
    private int mWheelchairGattIndex;

    private boolean mEEGConnected = false;

    //Layout - TextViews and Buttons
//    private TextView mBatteryLevel;
    private TextView mDataRate;
    private TextView mYfitTextView;
    private TextView mTrainingInstructions;
    private Button mExportButton;
    private long mLastTime;
    private long mCurrentTime;
    private long mClassTime; //DON'T DELETE!!!

    private boolean filterData = false;
    private int points = 0;
    private Menu menu;

    //RSSI:
    private static final int RSSI_UPDATE_TIME_INTERVAL = 2000;
    private Handler mTimerHandler = new Handler();
    private boolean mTimerEnabled = false;

    //Data Variables:
    private String fileTimeStamp = "";
    private double dataRate;
    private double mEMGClass = 0;

    //Classification
    boolean mRunTrainingBool = false;

    //Play Sound:
    MediaPlayer mMediaBeep;

    //Bluetooth Classic - For Robotic Hand
    public static Handler mHandler;
    public static final int RECIEVE_MESSAGE = 1;        // Status  for Handler
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder sb = new StringBuilder();
    private static int flag = 0;

    private ConnectedThread mConnectedThread;

    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // MAC-address of Bluetooth module (you must edit this line to change)
    private static String address = "30:14:12:17:21:88";

    //File Save Variables:
    private boolean fileSaveInitialized = false;
    private CSVWriter csvWriter;
    private File file;
    private File trainingDataFile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);
        //Set orientation of device based on screen type/size:
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //Recieve Intents & Parse:
        Intent intent = getIntent();
        deviceMacAddresses = intent.getStringArrayExtra(MainActivity.INTENT_DEVICES_KEY);
        String[] deviceDisplayNames = intent.getStringArrayExtra(MainActivity.INTENT_DEVICES_NAMES);
        Boolean trainActivity = intent.getExtras().getBoolean(MainActivity.INTENT_TRAIN_BOOLEAN);
        mRunTrainingBool = trainActivity;
        Log.e(TAG,"Train Activity (true/false): "+String.valueOf(trainActivity));
        mDeviceName = deviceDisplayNames[0];
        mDeviceAddress = deviceMacAddresses[0];
        Log.d(TAG, "Device Names: " + Arrays.toString(deviceDisplayNames));
        Log.d(TAG, "Device MAC Addresses: " + Arrays.toString(deviceMacAddresses));
        Log.d(TAG, Arrays.toString(deviceMacAddresses));
        //Set up action bar:
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        ActionBar actionBar = getActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#6078ef")));
        //Flag to keep screen on (stay-awake):
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //Set up TextViews
        mExportButton = (Button) findViewById(R.id.button_export);
        mDataRate = (TextView) findViewById(R.id.dataRate);
        mDataRate.setText("...");
        mTrainingInstructions = (TextView) findViewById(R.id.trainingInstructions);
        mYfitTextView = (TextView) findViewById(R.id.classifierOutput);
        if (mRunTrainingBool) {
            updateTrainingView(View.VISIBLE);
            updateTrainingPrompt("BEGINNING TRAINING...");
        } else {
            updateTrainingView(View.GONE);
        }
        //Initialize Bluetooth
        ActionBar ab = getActionBar();
        ab.setTitle(mDeviceName);
        ab.setSubtitle(mDeviceAddress);
        initializeBluetoothArray();
        //Bluetooth Classic Stuff:
        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECIEVE_MESSAGE:
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);
                        sb.append(strIncom);
                        int endOfLineIndex = sb.indexOf("\r\n");
                        if (endOfLineIndex > 0) {
                            String sbprint = sb.substring(0, endOfLineIndex);
                            sb.delete(0, sb.length());
                            flag++;
                            Log.i(TAG,"flag: "+String.valueOf(flag)+"Sbprint: "+sbprint);
                        }
                        break;
                }
            }
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter
        checkBTState();
        // Initialize our XYPlot reference:
        mGraphAdapterCh1 = new GraphAdapter(1000, "EMG Data Ch 1", false, false, Color.BLUE, 750); //Color.parseColor("#19B52C") also, RED, BLUE, etc.
        mGraphAdapterCh2 = new GraphAdapter(1000, "EMG Data Ch 2", false, false, Color.RED, 750); //Color.parseColor("#19B52C") also, RED, BLUE, etc.
        mGraphAdapterCh3 = new GraphAdapter(1000, "EMG Data Ch 3", false, false, Color.GREEN, 750); //Color.parseColor("#19B52C") also, RED, BLUE, etc.
        //PLOT CH1 By default
        mGraphAdapterCh1.plotData = true;
        mGraphAdapterCh2.plotData = true;
        mGraphAdapterCh3.plotData = true;
        mGraphAdapterCh1.setPointWidth((float) 2);
        mGraphAdapterCh2.setPointWidth((float) 2);
        mGraphAdapterCh3.setPointWidth((float) 3);
        if (plotImplicitXVals) mGraphAdapterCh1.series.useImplicitXVals();
        if (plotImplicitXVals) mGraphAdapterCh2.series.useImplicitXVals();
        if (plotImplicitXVals) mGraphAdapterCh3.series.useImplicitXVals();
        if (filterData) {
            mXYPlotAdapterCh1.filterData();
            mXYPlotAdapterCh2.filterData();
            mXYPlotAdapterCh3.filterData();
        }
        mXYPlotAdapterCh1 = new XYPlotAdapter(findViewById(R.id.emgCh1), plotImplicitXVals, 1000);
        mXYPlotAdapterCh2 = new XYPlotAdapter(findViewById(R.id.emgCh2), plotImplicitXVals, 1000);
        mXYPlotAdapterCh3 = new XYPlotAdapter(findViewById(R.id.emgCh3), plotImplicitXVals, 1000);
        mXYPlotAdapterCh1.xyPlot.addSeries(mGraphAdapterCh1.series, mGraphAdapterCh1.lineAndPointFormatter);
        mXYPlotAdapterCh2.xyPlot.addSeries(mGraphAdapterCh2.series, mGraphAdapterCh2.lineAndPointFormatter);
        mXYPlotAdapterCh3.xyPlot.addSeries(mGraphAdapterCh3.series, mGraphAdapterCh3.lineAndPointFormatter);

        redrawer = new Redrawer(
                Arrays.asList(new Plot[]{mXYPlotAdapterCh1.xyPlot,mXYPlotAdapterCh2.xyPlot,mXYPlotAdapterCh3.xyPlot}),
                100, false);
        mExportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    saveDataFile(true);
                } catch (IOException e) {
                    Log.e(TAG, "IOException in saveDataFile");
                    e.printStackTrace();
                }
                Uri uii;
                uii = Uri.fromFile(file);
                Intent exportData = new Intent(Intent.ACTION_SEND);
                exportData.putExtra(Intent.EXTRA_SUBJECT, "data.csv");
                exportData.putExtra(Intent.EXTRA_STREAM, uii);
                exportData.setType("text/html");
                startActivity(exportData);
            }
        });
        mLastTime = System.currentTimeMillis();
        mClassTime = System.currentTimeMillis();
        ToggleButton ch1 = (ToggleButton) findViewById(R.id.toggleButtonCh1);
        ch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mGraphAdapterCh1.setPlotData(b);
            }
        });
        ToggleButton ch2 = (ToggleButton) findViewById(R.id.toggleButtonCh2);
        ch2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mGraphAdapterCh2.setPlotData(b);
            }
        });
        ToggleButton ch3 = (ToggleButton) findViewById(R.id.toggleButtonCh3);
        ch3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mGraphAdapterCh3.setPlotData(b);
            }
        });
        Button handConnectButton = (Button) findViewById(R.id.buttonHandControl);
        handConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectToClassicBTHand();
            }
        });
        if(!mRunTrainingBool) {
            connectToClassicBTHand();
            Toast.makeText(this, "Attempting Bluetooth Connection to hand", Toast.LENGTH_SHORT).show();
        }
        mMediaBeep = MediaPlayer.create(this, R.raw.beep_01a);
    }

    private void exportDataExternal() {
        try {
            saveDataFile(true);
        } catch (IOException e) {
            Log.e(TAG, "IOException in saveDataFile");
            e.printStackTrace();
        }
        Uri uii;
        uii = Uri.fromFile(file);
        Intent exportData = new Intent(Intent.ACTION_SEND);
        exportData.putExtra(Intent.EXTRA_SUBJECT, "data.csv");
        exportData.putExtra(Intent.EXTRA_STREAM, uii);
        exportData.setType("text/html");
        startActivity(exportData);
    }

    public void connectToClassicBTHand() {
        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            Log.e(TAG, "socketCreate fail"+e.getMessage());
        }
        btAdapter.cancelDiscovery();
        Log.d(TAG, "...Connecting...");
        try {
            btSocket.connect();
            Log.d(TAG, "....Connection ok...");
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                Log.e(TAG, "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
            }
        }

        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Create Socket...");

        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create Insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    public String getTimeStamp() {
        return new SimpleDateFormat("yyyyMMdd_HH.mm.ss").format(new Date());
    }

    /**
     * @param terminate - if True, terminates CSVWriter Instance
     * @throws IOException
     */
    public void saveDataFile(boolean terminate) throws IOException {
        if (terminate && fileSaveInitialized) {
            csvWriter.flush();
            csvWriter.close();
            fileSaveInitialized = false;
        }
    }

    /**
     * Initializes CSVWriter For Saving Data.
     *
     * @throws IOException bc
     */
    public void saveDataFile() throws IOException {
        File root = Environment.getExternalStorageDirectory();
        if(mRunTrainingBool) {
            fileTimeStamp = "EMG_TrainingData_" + getTimeStamp();
        } else {
            fileTimeStamp = "EMG_" + getTimeStamp();
        }
        if (root.canWrite()) {
            File dir;
            if(mRunTrainingBool) {
                dir = new File(root.getAbsolutePath()+"/EMGTrainingData");
            } else {
                dir = new File(root.getAbsolutePath() + "/EMGData");
            }
            dir.mkdirs();
            file = new File(dir, fileTimeStamp + ".csv");
            if(mRunTrainingBool) trainingDataFile = file;
            if (file.exists() && !file.isDirectory()) {
                Log.d(TAG, "File " + file.toString() + " already exists - appending data");
                FileWriter fileWriter = new FileWriter(file, true);
                csvWriter = new CSVWriter(fileWriter);
            } else {
                csvWriter = new CSVWriter(new FileWriter(file));
            }
            fileSaveInitialized = true;
        }
    }

    public void exportFileWithClass(double eegData1, double eegData2, double eegData3) throws IOException {
        if (fileSaveInitialized) {
            String[] valueCsvWrite = new String[4];
            valueCsvWrite[0] = eegData1 + "";
            valueCsvWrite[1] = eegData2 + "";
            valueCsvWrite[2] = eegData3 + "";
            valueCsvWrite[3] = mEMGClass + "";
            csvWriter.writeNext(valueCsvWrite, false);
        }
    }

    @Override
    public void onResume() {
        jmainInitialization(false);
        String fileTimeStampConcat = "EEGSensorData_" + getTimeStamp();
        Log.d("onResume-timeStamp", fileTimeStampConcat);
        if (!fileSaveInitialized) {
            try {
                saveDataFile();
            } catch (IOException ex) {
                Log.e("IOEXCEPTION:", ex.toString());
            }
        }
        redrawer.start();
        super.onResume();
    }

    @Override
    protected void onPause() {
        redrawer.pause();
        super.onPause();
    }

    private void initializeBluetoothArray() {
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothDeviceArray = new BluetoothDevice[deviceMacAddresses.length];
        mBluetoothGattArray = new BluetoothGatt[deviceMacAddresses.length];
        Log.d(TAG, "Device Addresses: " + Arrays.toString(deviceMacAddresses));
        if (deviceMacAddresses != null) {
            for (int i = 0; i < deviceMacAddresses.length; i++) {
                mBluetoothDeviceArray[i] = mBluetoothManager.getAdapter().getRemoteDevice(deviceMacAddresses[i]);
            }
        } else {
            Log.e(TAG, "No Devices Queued, Restart!");
            Toast.makeText(this, "No Devices Queued, Restart!", Toast.LENGTH_SHORT).show();
        }
        mBluetoothLe = new BluetoothLe(this, mBluetoothManager, this);
        for (int i = 0; i < mBluetoothDeviceArray.length; i++) {
            mBluetoothGattArray[i] = mBluetoothLe.connect(mBluetoothDeviceArray[i], false);
            Log.e(TAG, "Connecting to Device: " + String.valueOf(mBluetoothDeviceArray[i].getName() + " " + mBluetoothDeviceArray[i].getAddress()));
            if ("WheelchairControl".equals(mBluetoothDeviceArray[i].getName())) {
                mWheelchairGattIndex = i;
                Log.e(TAG, "mWheelchairGattIndex: " + mWheelchairGattIndex);
            }
        }
    }

    private void setNameAddress(String name_action, String address_action) {
        MenuItem name = menu.findItem(R.id.action_title);
        MenuItem address = menu.findItem(R.id.action_address);
        name.setTitle(name_action);
        address.setTitle(address_action);
        invalidateOptionsMenu();
    }

    @Override
    protected void onDestroy() {
        redrawer.finish();
        disconnectAllBLE();
        try {
            saveDataFile(true);
        } catch (IOException e) {
            Log.e(TAG, "IOException in saveDataFile");
            e.printStackTrace();
        }
        stopMonitoringRssiValue();
        super.onDestroy();
        if(btSocket!=null) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                Log.e(TAG, "In onPause() and failed to close socket." + e2.getMessage() + ".");
            }
        }
    }

    private void disconnectAllBLE(boolean mainThread) {
        if (mBluetoothLe != null) {
            for (BluetoothGatt bluetoothGatt : mBluetoothGattArray) {
                mBluetoothLe.disconnect(bluetoothGatt);
                mConnected = false;
                if(mainThread) resetMenuBar();
            }
        }
    }

    private void disconnectAllBLE() {
        if (mBluetoothLe != null) {
            for (BluetoothGatt bluetoothGatt : mBluetoothGattArray) {
                mBluetoothLe.disconnect(bluetoothGatt);
                mConnected = false;
                resetMenuBar();
            }
        }
    }

    private void resetMenuBar() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            if (menu != null) {
                menu.findItem(R.id.menu_connect).setVisible(true);
                menu.findItem(R.id.menu_disconnect).setVisible(false);
            }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_device_control, menu);
        getMenuInflater().inflate(R.menu.actionbar_item, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        this.menu = menu;
        setNameAddress(mDeviceName, mDeviceAddress);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                if (mBluetoothLe != null) {
                    initializeBluetoothArray();
                }
                connect();
                return true;
            case R.id.menu_disconnect:
                if (mBluetoothLe != null) {
                    disconnectAllBLE();
                }
                return true;
            case android.R.id.home:
                if (mBluetoothLe != null) {
                    disconnectAllBLE();
                }
                NavUtils.navigateUpFromSameTask(this);
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void connect() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MenuItem menuItem = menu.findItem(R.id.action_status);
                menuItem.setTitle("Connecting...");
            }
        });
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.i(TAG, "onServicesDiscovered");
        if (status == BluetoothGatt.GATT_SUCCESS) {
            for (BluetoothGattService service : gatt.getServices()) {
                if ((service == null) || (service.getUuid() == null)) {
                    continue;
                }
                if (AppConstant.SERVICE_DEVICE_INFO.equals(service.getUuid())) {
                    //Read the device serial number
                    mBluetoothLe.readCharacteristic(gatt, service.getCharacteristic(AppConstant.CHAR_SERIAL_NUMBER));
                    //Read the device software version
                    mBluetoothLe.readCharacteristic(gatt, service.getCharacteristic(AppConstant.CHAR_SOFTWARE_REV));
                }

                if (AppConstant.SERVICE_EEG_SIGNAL.equals(service.getUuid())) {
                    mBluetoothLe.setCharacteristicNotification(gatt, service.getCharacteristic(AppConstant.CHAR_EEG_CH1_SIGNAL), true);
                    mBluetoothLe.setCharacteristicNotification(gatt, service.getCharacteristic(AppConstant.CHAR_EEG_CH2_SIGNAL), true);
                    if (service.getCharacteristic(AppConstant.CHAR_EEG_CH3_SIGNAL) != null) {
                        mBluetoothLe.setCharacteristicNotification(gatt, service.getCharacteristic(AppConstant.CHAR_EEG_CH3_SIGNAL), true);
                    }
                    if (service.getCharacteristic(AppConstant.CHAR_EEG_CH4_SIGNAL) != null) {
                        mBluetoothLe.setCharacteristicNotification(gatt, service.getCharacteristic(AppConstant.CHAR_EEG_CH4_SIGNAL), true);
                    }
                }

                if (AppConstant.SERVICE_BATTERY_LEVEL.equals(service.getUuid())) { //Read the device battery percentage
//                    mBluetoothLe.readCharacteristic(gatt, service.getCharacteristic(AppConstant.CHAR_BATTERY_LEVEL));
//                    mBluetoothLe.setCharacteristicNotification(gatt, service.getCharacteristic(AppConstant.CHAR_BATTERY_LEVEL), true);
                }

                if (AppConstant.SERVICE_MPU.equals(service.getUuid())) {
                    mBluetoothLe.setCharacteristicNotification(gatt, service.getCharacteristic(AppConstant.CHAR_MPU_COMBINED), true);
                }
            }
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        Log.i(TAG, "onCharacteristicRead");
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (AppConstant.CHAR_BATTERY_LEVEL.equals(characteristic.getUuid())) {
                int batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                Log.i(TAG, "Battery Level :: " + batteryLevel);
            }
        } else {
            Log.e(TAG, "onCharacteristic Read Error" + status);
        }
    }

    private class DataChannel {

        boolean chEnabled;
        byte[] characteristicDataPacketBytes;
        short packetCounter;
        int totalDataPointsReceived;
        byte[] dataBuffer;
    }

    //Refactored Data Channel Classes
    DataChannel mCh1 = new DataChannel();
    DataChannel mCh2 = new DataChannel();
    DataChannel mCh3 = new DataChannel();
    //Counter for total number of packets recieved (for each ch)
    private int mTotalPacketCount = -1;

    // Classification
    private double[] yfitarray = new double[5];

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        //TODO: ADD BATTERY MEASURE CAPABILITY IN FIRMWARE: FOR NEW NRF52 with BATT. MANAGEMENT
        if (AppConstant.CHAR_EEG_CH1_SIGNAL.equals(characteristic.getUuid())) {
            mCh1.characteristicDataPacketBytes = characteristic.getValue();
            if (!mCh1.chEnabled) {
                mCh1.chEnabled = true;
            }
            getDataRateBytes(mCh1.characteristicDataPacketBytes.length);
            if(mEEGConnected) {
                if(mCh1.dataBuffer !=null) {
                    //concatenate
                    mCh1.dataBuffer = Bytes.concat(mCh1.dataBuffer, mCh1.characteristicDataPacketBytes);
                } else {
                    //Init:
                    mCh1.dataBuffer = mCh1.characteristicDataPacketBytes;
                }
                mCh1.totalDataPointsReceived+=mCh1.characteristicDataPacketBytes.length/3;
                mCh1.packetCounter++;
                if(mCh1.packetCounter==10) { //every 60 dp (~240 ms)
                    for (int i = 0; i < mCh1.dataBuffer.length/3; i++) {
                        mGraphAdapterCh1.addDataPoint(bytesToDouble(mCh1.dataBuffer[3*i], mCh1.dataBuffer[3*i+1], mCh1.dataBuffer[3*i+2]),mCh1.totalDataPointsReceived-mCh1.dataBuffer.length/3+i);
                        if(mRunTrainingBool) updateTrainingRoutine(mCh1.totalDataPointsReceived-mCh1.dataBuffer.length/3+i);
                    }
                    mCh1.dataBuffer = null;
                    mCh1.packetCounter=0;
                }
            }
        }

        if (AppConstant.CHAR_EEG_CH2_SIGNAL.equals(characteristic.getUuid())) {
            if (!mCh2.chEnabled) {
                mCh2.chEnabled = true;
            }
            mCh2.characteristicDataPacketBytes = characteristic.getValue();
            int byteLength = mCh2.characteristicDataPacketBytes.length;
            getDataRateBytes(byteLength);
            if(mEEGConnected) {
                if(mCh2.dataBuffer !=null) { //concatenate
                    mCh2.dataBuffer = Bytes.concat(mCh2.dataBuffer, mCh2.characteristicDataPacketBytes);
                } else { //Init:
                    mCh2.dataBuffer = mCh2.characteristicDataPacketBytes;
                }
                mCh2.totalDataPointsReceived+=mCh2.characteristicDataPacketBytes.length/3;
                mCh2.packetCounter++;
                if(mCh2.packetCounter==10) {
                    for (int i = 0; i < mCh2.dataBuffer.length/3; i++) {
                        mGraphAdapterCh2.addDataPoint(bytesToDouble(mCh2.dataBuffer[3*i], mCh2.dataBuffer[3*i+1], mCh2.dataBuffer[3*i+2]),mCh2.totalDataPointsReceived-mCh2.dataBuffer.length/3+i);
                    }
                    mCh2.dataBuffer = null;
                    mCh2.packetCounter=0;
                }
            }
        }

        if (AppConstant.CHAR_EEG_CH3_SIGNAL.equals(characteristic.getUuid())) {
            if (!mCh3.chEnabled) {
                mCh3.chEnabled = true;
            }
            mCh3.characteristicDataPacketBytes = characteristic.getValue();
            int byteLength = mCh3.characteristicDataPacketBytes.length;
            getDataRateBytes(byteLength);
            if(mEEGConnected) {
                if(mCh3.dataBuffer!=null) { //concatenate
                    mCh3.dataBuffer = Bytes.concat(mCh3.dataBuffer, mCh3.characteristicDataPacketBytes);
                } else { //Init:
                    mCh3.dataBuffer = mCh3.characteristicDataPacketBytes;
                }
                mCh3.totalDataPointsReceived+=mCh3.characteristicDataPacketBytes.length/3;
                mCh3.packetCounter++;
                if(mCh3.packetCounter==10) {
                    for (int i = 0; i < mCh3.dataBuffer.length/3; i++) {
                        mGraphAdapterCh3.addDataPoint(bytesToDouble(mCh3.dataBuffer[3*i], mCh3.dataBuffer[3*i+1], mCh3.dataBuffer[3*i+2]),mCh3.totalDataPointsReceived-mCh3.dataBuffer.length/3+i);
                    }
                    mCh3.dataBuffer = null;
                    mCh3.packetCounter=0;
                }
            }
        }

        // Done?
        if (mCh1.chEnabled && mCh2.chEnabled && mCh3.chEnabled) {
            mTotalPacketCount++;
            mEEGConnected = true;
            mCh1.chEnabled = false; mCh2.chEnabled = false; mCh3.chEnabled = false;
            if (mCh3.characteristicDataPacketBytes != null && mCh2.characteristicDataPacketBytes != null && mCh1.characteristicDataPacketBytes != null)
                writeToDisk24(mCh1.characteristicDataPacketBytes,mCh2.characteristicDataPacketBytes,mCh3.characteristicDataPacketBytes);

            if(mTotalPacketCount %10==0 && mTotalPacketCount > 120) {
                ClassifyTask classifyTask = new ClassifyTask();
                Log.e(TAG,"["+String.valueOf(mNumberOfClassifierCalls+1)+"] CALLING CLASSIFIER FUNCTION!");
                mNumberOfClassifierCalls++;
                classifyTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
    }

    private void updateTrainingRoutine(int dataPoints) {
        if(dataPoints%250==0) {
            int second = dataPoints/250;
            //TODO: REMEMBER TO CHANGE mEMGClass
            int eventSecondCountdown = 0;
            if(second>=0 && second < 10) {
                eventSecondCountdown = 10 - second;
                updateTrainingPrompt("Relax Hand - Countdown to First Event: "+String.valueOf(eventSecondCountdown)+"s\n Next up: Close Hand");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second>=10 && second < 20) {
                eventSecondCountdown = 20 - second;
                updateTrainingPrompt("[1] Close Hand and Hold for " + String.valueOf(eventSecondCountdown)+"s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 1;
            } else if (second>=20 && second < 30) {
                eventSecondCountdown = 30 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown)+"s\n Next up: Close Pinky");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second>=30 && second < 40) {
                eventSecondCountdown = 40 - second;
                updateTrainingPrompt("[7] Close Pinky and Hold for " + String.valueOf(eventSecondCountdown)+"s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 7;
            } else if (second>=40 && second < 50) {
                eventSecondCountdown = 50 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown)+"s\n Next up: Close Ring");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second>=50 && second < 60) {
                eventSecondCountdown = 60 - second;
                updateTrainingPrompt("[6] Close Ring and Hold for " + String.valueOf(eventSecondCountdown)+"s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 6;
            } else if (second>=60 && second < 70) {
                eventSecondCountdown = 70 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown)+"s\n Next up: Close Middle");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second>=70 && second < 80) {
                eventSecondCountdown = 80 - second;
                updateTrainingPrompt("[5] Close Middle and Hold " + String.valueOf(eventSecondCountdown)+"s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 5;
            } else if (second>=80 && second < 90) {
                eventSecondCountdown = 90 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown)+"s\n Next up: Close Index");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second>=90 && second < 100) {
                eventSecondCountdown = 100 - second;
                updateTrainingPrompt("[4] Close Index and Hold " + String.valueOf(eventSecondCountdown)+"s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 4;
            } else if (second>=100 && second < 110) {
                eventSecondCountdown = 110 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown)+"s\n Next up: Close Thumb");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second>=110 && second < 120) {
                eventSecondCountdown = 120 - second;
                updateTrainingPrompt("[3] Close Thumb and Hold " + String.valueOf(eventSecondCountdown)+"s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 3;
            } else if (second>=120 && second < 130) {
                eventSecondCountdown = 130 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown)+"s\n Next up: Done");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second>=130) {
                mEMGClass = 0;
                updateTrainingPrompt("[Training Complete!]");
                updateTrainingPromptColor(Color.DKGRAY);
                mRunTrainingBool = false;
                updateTrainingView(View.GONE);
                disconnectAllBLE(false); // Launch filesave?
                //TODO: Replace with internal process and extract/save features.
                exportDataExternal();
                readFromTrainingFile(trainingDataFile);
            }
            if(eventSecondCountdown==10) {
                mMediaBeep.start();
            }
        }
    }

    private void updateTrainingPrompt(final String prompt) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mRunTrainingBool) {
                    mTrainingInstructions.setText(prompt);
                }
            }
        });
    }

    private void updateTrainingView(final int visibility) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTrainingInstructions.setVisibility(visibility);
            }
        });
    }

    private void updateTrainingPromptColor(final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mRunTrainingBool) {
                    mTrainingInstructions.setTextColor(color);
                }
            }
        });
    }

    ClassDataAnalysis TrainingData;

    public void readFromTrainingFile(File f) {
        try {
            CSVReader csvReader = new CSVReader(new FileReader(f),',');
            List<String[]> strings = csvReader.readAll();
            Log.e(TAG, "strings.length = "+String.valueOf(strings.size()));
            TrainingData = new ClassDataAnalysis(strings);
//            double[] trainingDataAll = ClassDataAnalysis.concatAll();
//            if(trainingDataAll!=null) Log.e(TAG,"trainingDataAll - Size: "+String.valueOf(trainingDataAll.length));
//            for (int i = 0; i < ; i++) {
//
//            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double bytesToDouble(byte a1, byte a2, byte a3) {
        int a = unsignedToSigned(unsignedBytesToInt(a1,a2,a3),24);
        return ((double)a/8388607.0)*2.25;
    }

    private int mNumberOfClassifierCalls = 0;
    private double mLastYValue = 0;
    private static final double[] DEFAULT_PARAMS = {0.000150000000000000,0.0350000000000000,0.000200000000000000,
            0.000120000000000000,0.00200000000000000,0.000300000000000000,0.000250000000000000,
            0.000290000000000000,3.40000000000000e-05,0.000300000000000000,0.000700000000000000};
    private double[] CUSTOM_PARAMS;
    private boolean mUseCustomParams = false;

    private class ClassifyTask extends AsyncTask<Void, Void, Double> {
        @Override
        protected Double doInBackground(Void... voids) {
            double[] concat = Doubles.concat(mGraphAdapterCh1.classificationBuffer,mGraphAdapterCh2.classificationBuffer,mGraphAdapterCh3.classificationBuffer);
            if(mUseCustomParams && CUSTOM_PARAMS!=null) {
                return jClassifyWithParams(concat, CUSTOM_PARAMS, mLastYValue);
            } else {
                return jClassifyWithParams(concat, DEFAULT_PARAMS, mLastYValue);
            }
        }

        @Override
        protected void onPostExecute(Double predictedClass) {
            mLastYValue = predictedClass;
            processClassifiedData(predictedClass);
            super.onPostExecute(predictedClass);
        }
    }

    private void processClassifiedData(final double Y) {
        //Shift backwards:
        System.arraycopy(yfitarray, 1, yfitarray, 0, 4);
        //Add to end;
        yfitarray[4] = Y;
        //Analyze:
        Log.e(TAG, " YfitArray: " + Arrays.toString(yfitarray));
        final boolean checkLastThreeMatches = lastThreeMatches(yfitarray);
        if (checkLastThreeMatches) {
            //Get value:
            Log.e(TAG, "Found fit: " + String.valueOf(yfitarray[4]));
            final String s = "[" + String.valueOf(Y) + "]";
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mYfitTextView.setText(s);
                }
            });
            // For Controlling Hand. Some commands have to be altered because the classes don't match the commands.
            if(mConnectedThread!=null) {
                if(Y!=0) {
                    int command;
                    if(Y==2) {
                        command = 1;
                    } else if (Y==1) {
                        command = 2;
                    } else
                        command = (int)Y;
                    mConnectedThread.write(command);
                }
            }
        } else {
            boolean b = yfitarray[0] == 0.0 && yfitarray[1] == 0.0 && yfitarray[2] == 0.0
                    && yfitarray[3] == 0.0 && yfitarray[4] == 0.0;
            if (b) {
                final String s = "[" + String.valueOf(Y) + "]";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mYfitTextView.setText(s);
                    }
                });
                if(mConnectedThread!=null)
                    mConnectedThread.write(1);
            }
        }
    }

    private void writeToDisk24(byte[] ch1, byte[] ch2, byte[] ch3) {
        for (int i = 0; i < ch1.length/3; i++) {
            try {
                exportFileWithClass(bytesToDouble(ch1[3*i], ch1[3*i+1], ch1[3*i+2]), bytesToDouble(ch2[3*i], ch2[3*i+1], ch2[3*i+2]), bytesToDouble(ch3[3*i], ch3[3*i+1], ch3[3*i+2]));
            } catch (IOException e) {
                Log.e("IOException", e.toString());
            }
        }

    }

    private boolean lastThreeMatches(double[] yfitarray) {
        boolean b0 = false;
        boolean b1 = false;
        if (yfitarray[4] != 0) {
            b0 = (yfitarray[4] == yfitarray[3]);
            b1 = (yfitarray[3] == yfitarray[2]);
        }
        return b0 && b1;
    }

    private void getDataRateBytes(int bytes) {
        mCurrentTime = System.currentTimeMillis();
        points += bytes;
        if (mCurrentTime > (mLastTime + 5000)) {
            dataRate = (points / 5);
            points = 0;
            mLastTime = mCurrentTime;
            Log.e(" DataRate:", String.valueOf(dataRate) + " Bytes/s");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDataRate.setText(String.valueOf(dataRate) + " Bytes/s");
                }
            });
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        uiRssiUpdate(rssi);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        switch (newState) {
            case BluetoothProfile.STATE_CONNECTED:
                mConnected = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (menu != null) {
                            menu.findItem(R.id.menu_connect).setVisible(false);
                            menu.findItem(R.id.menu_disconnect).setVisible(true);
                        }
                    }
                });
                Log.i(TAG, "Connected");
                updateConnectionState(getString(R.string.connected));
                invalidateOptionsMenu();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mDataRate.setTextColor(Color.BLACK);
                        mDataRate.setTypeface(null, Typeface.NORMAL);
                    }
                });
                //Start the service discovery:
                gatt.discoverServices();
                startMonitoringRssiValue();
                redrawer.start();
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                mConnected = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (menu != null) {
                            menu.findItem(R.id.menu_connect).setVisible(true);
                            menu.findItem(R.id.menu_disconnect).setVisible(false);
                        }
                    }
                });
                Log.i(TAG, "Disconnected");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mDataRate.setTextColor(Color.RED);
                        mDataRate.setTypeface(null, Typeface.BOLD);
                        mDataRate.setText("0 Hz");
                    }
                });
                updateConnectionState(getString(R.string.disconnected));
                stopMonitoringRssiValue();
                invalidateOptionsMenu();
                redrawer.pause();
                break;
            default:
                break;
        }
    }

    public void startMonitoringRssiValue() {
        readPeriodicallyRssiValue(true);
    }

    public void stopMonitoringRssiValue() {
        readPeriodicallyRssiValue(false);
    }

    public void readPeriodicallyRssiValue(final boolean repeat) {
        mTimerEnabled = repeat;
        // check if we should stop checking RSSI value
        if (!mConnected || mBluetoothGattArray == null || !mTimerEnabled) {
            mTimerEnabled = false;
            return;
        }

        mTimerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mBluetoothGattArray == null || !mConnected) {
                    mTimerEnabled = false;
                    return;
                }
                // request RSSI value
                mBluetoothGattArray[0].readRemoteRssi();
                // add call it once more in the future
                readPeriodicallyRssiValue(mTimerEnabled);
            }
        }, RSSI_UPDATE_TIME_INTERVAL);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic
            characteristic, int status) {
        Log.i(TAG, "onCharacteristicWrite :: Status:: " + status);
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        Log.i(TAG, "onDescriptorRead :: Status:: " + status);
    }

    @Override
    public void onError(String errorMessage) {
        Log.e(TAG, "Error:: " + errorMessage);
    }

    private void updateConnectionState(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (status.equals(getString(R.string.connected))) {
                    Toast.makeText(getApplicationContext(), "Device Connected!", Toast.LENGTH_SHORT).show();
                } else if (status.equals(getString(R.string.disconnected))) {
                    Toast.makeText(getApplicationContext(), "Device Disconnected!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void uiRssiUpdate(final int rssi) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MenuItem menuItem = menu.findItem(R.id.action_rssi);
                MenuItem status_action_item = menu.findItem(R.id.action_status);
                final String valueOfRSSI = String.valueOf(rssi) + " dB";
                menuItem.setTitle(valueOfRSSI);
                if (mConnected) {
                    String newStatus = "Status: " + getString(R.string.connected);
                    status_action_item.setTitle(newStatus);
                } else {
                    String newStatus = "Status: " + getString(R.string.disconnected);
                    status_action_item.setTitle(newStatus);
                }
            }
        });
    }

    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if(btAdapter==null) {
            Log.e(TAG,"Fatal Error: "+"Bluetooth not support");
        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth ON...");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private int unsignedToSigned(int unsigned, int size) {
        if ((unsigned & (1 << size - 1)) != 0) {
            unsigned = -1 * ((1 << size - 1) - (unsigned & ((1 << size - 1) - 1)));
        }
        return unsigned;
    }

    private int unsignedBytesToInt(byte b0, byte b1, byte b2) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8) + (unsignedByteToInt(b2) << 16));
    }

    /**
     * Convert a signed byte to an unsigned int.
     */
    private int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }

    /*
    * Application of JNI code:
    */
    static {
        System.loadLibrary("android-jni");
    }

    public native int jmainInitialization(boolean b);

    public native double jClassify(double[] DataArray, double LastY);
    public native double jClassifyWithParams(double[] DataArray, double[] params, double LastY);

}
