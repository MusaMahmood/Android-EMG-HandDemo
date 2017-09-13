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
    private CSVWriter mKNNcsvWriter;
    private File mFile;
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
        Log.e(TAG, "Train Activity (true/false): " + String.valueOf(trainActivity));
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
                            Log.i(TAG, "flag: " + String.valueOf(flag) + "Sbprint: " + sbprint);
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
                Arrays.asList(new Plot[]{mXYPlotAdapterCh1.xyPlot, mXYPlotAdapterCh2.xyPlot, mXYPlotAdapterCh3.xyPlot}),
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
                uii = Uri.fromFile(mFile);
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
//        if (!mRunTrainingBool) {
//            connectToClassicBTHand();
//            Toast.makeText(this, "Attempting Bluetooth Connection to hand", Toast.LENGTH_SHORT).show();
//        }
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
        uii = Uri.fromFile(mFile);
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
            Log.e(TAG, "socketCreate fail" + e.getMessage());
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
        if (Build.VERSION.SDK_INT >= 10) {
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create Insecure RFComm Connection", e);
            }
        }
        return device.createRfcommSocketToServiceRecord(MY_UUID);
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
            mKNNcsvWriter.flush();
            mKNNcsvWriter.close();
            fileSaveInitialized = false;
        }
    }

    private File mFileClassificationParams;

    public void openParamDataFile() {
        File root = Environment.getExternalStorageDirectory();
        if (root.canWrite()) {
            File dir = new File(root.getAbsolutePath() + "/EMG_Training_Params");
            dir.mkdirs();
            mFileClassificationParams = new File(dir, "params.csv"); //+getTimestamp
            if (mFileClassificationParams.exists() && !mFileClassificationParams.isDirectory()) {
                try {                    //DO NOT APPEND DATA.
                    FileWriter fileWriter = new FileWriter(mFileClassificationParams, false);
                    mKNNcsvWriter = new CSVWriter(fileWriter);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    mKNNcsvWriter = new CSVWriter(new FileWriter(mFileClassificationParams));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //TODO READ using FileReader; SAVE using FileWriter
        }
    }

    /**
     * Initializes CSVWriter For Saving Data.
     *
     * @throws IOException bc
     */
    public void saveDataFile() throws IOException {
        File root = Environment.getExternalStorageDirectory();
        if (mRunTrainingBool) {
            fileTimeStamp = "EMG_TrainingData_" + getTimeStamp();
        } else {
            fileTimeStamp = "EMG_" + getTimeStamp();
        }
        if (root.canWrite()) {
            File dir;
            if (mRunTrainingBool) {
                dir = new File(root.getAbsolutePath() + "/EMGTrainingData");
            } else {
                dir = new File(root.getAbsolutePath() + "/EMGData");
            }
            dir.mkdirs();
            mFile = new File(dir, fileTimeStamp + ".csv");
            if (mRunTrainingBool) trainingDataFile = mFile;
            if (mFile.exists() && !mFile.isDirectory()) {
                Log.d(TAG, "File " + mFile.toString() + " already exists - appending data");
                FileWriter fileWriter = new FileWriter(mFile, true);
                csvWriter = new CSVWriter(fileWriter);
            } else {
                csvWriter = new CSVWriter(new FileWriter(mFile));
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
                openParamDataFile();
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
        if (btSocket != null) {
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
                if (mainThread) resetMenuBar();
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
            if (mEEGConnected) {
                if (mCh1.dataBuffer != null) {
                    //concatenate
                    mCh1.dataBuffer = Bytes.concat(mCh1.dataBuffer, mCh1.characteristicDataPacketBytes);
                } else {
                    //Init:
                    mCh1.dataBuffer = mCh1.characteristicDataPacketBytes;
                }
                mCh1.totalDataPointsReceived += mCh1.characteristicDataPacketBytes.length / 3;
                mCh1.packetCounter++;
                if (mCh1.packetCounter == 10) { //every 60 dp (~240 ms)
                    for (int i = 0; i < mCh1.dataBuffer.length / 3; i++) {
                        mGraphAdapterCh1.addDataPoint(bytesToDouble(mCh1.dataBuffer[3 * i], mCh1.dataBuffer[3 * i + 1], mCh1.dataBuffer[3 * i + 2]), mCh1.totalDataPointsReceived - mCh1.dataBuffer.length / 3 + i);
                        if (mRunTrainingBool)
                            updateTrainingRoutine(mCh1.totalDataPointsReceived - mCh1.dataBuffer.length / 3 + i);
                    }
                    mCh1.dataBuffer = null;
                    mCh1.packetCounter = 0;
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
            if (mEEGConnected) {
                if (mCh2.dataBuffer != null) { //concatenate
                    mCh2.dataBuffer = Bytes.concat(mCh2.dataBuffer, mCh2.characteristicDataPacketBytes);
                } else { //Init:
                    mCh2.dataBuffer = mCh2.characteristicDataPacketBytes;
                }
                mCh2.totalDataPointsReceived += mCh2.characteristicDataPacketBytes.length / 3;
                mCh2.packetCounter++;
                if (mCh2.packetCounter == 10) {
                    for (int i = 0; i < mCh2.dataBuffer.length / 3; i++) {
                        mGraphAdapterCh2.addDataPoint(bytesToDouble(mCh2.dataBuffer[3 * i], mCh2.dataBuffer[3 * i + 1], mCh2.dataBuffer[3 * i + 2]), mCh2.totalDataPointsReceived - mCh2.dataBuffer.length / 3 + i);
                    }
                    mCh2.dataBuffer = null;
                    mCh2.packetCounter = 0;
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
            if (mEEGConnected) {
                if (mCh3.dataBuffer != null) { //concatenate
                    mCh3.dataBuffer = Bytes.concat(mCh3.dataBuffer, mCh3.characteristicDataPacketBytes);
                } else { //Init:
                    mCh3.dataBuffer = mCh3.characteristicDataPacketBytes;
                }
                mCh3.totalDataPointsReceived += mCh3.characteristicDataPacketBytes.length / 3;
                mCh3.packetCounter++;
                if (mCh3.packetCounter == 10) {
                    for (int i = 0; i < mCh3.dataBuffer.length / 3; i++) {
                        mGraphAdapterCh3.addDataPoint(bytesToDouble(mCh3.dataBuffer[3 * i], mCh3.dataBuffer[3 * i + 1], mCh3.dataBuffer[3 * i + 2]), mCh3.totalDataPointsReceived - mCh3.dataBuffer.length / 3 + i);
                    }
                    mCh3.dataBuffer = null;
                    mCh3.packetCounter = 0;
                }
            }
        }

        // Done?
        if (mCh1.chEnabled && mCh2.chEnabled && mCh3.chEnabled) {
            mTotalPacketCount++;
            mEEGConnected = true;
            mCh1.chEnabled = false;
            mCh2.chEnabled = false;
            mCh3.chEnabled = false;
            if (mCh3.characteristicDataPacketBytes != null && mCh2.characteristicDataPacketBytes != null && mCh1.characteristicDataPacketBytes != null)
                writeToDisk24(mCh1.characteristicDataPacketBytes, mCh2.characteristicDataPacketBytes, mCh3.characteristicDataPacketBytes);

            if (mTotalPacketCount % 10 == 0 && mTotalPacketCount > 120) {
                Thread t = new Thread(mRunnableClassifyTaskThread);
//                ClassifyTask classifyTask = new ClassifyTask();
                Log.e(TAG, "[" + String.valueOf(mNumberOfClassifierCalls + 1) + "] CALLING CLASSIFIER FUNCTION!");
                mNumberOfClassifierCalls++;
//                classifyTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                t.start();
            }
        }
    }

    private void updateTrainingRoutine(int dataPoints) {
        if (dataPoints % 250 == 0) {
            int second = dataPoints / 250;
            //TODO: REMEMBER TO CHANGE mEMGClass
            int eventSecondCountdown = 0;
            if (second >= 0 && second < 10) {
                eventSecondCountdown = 10 - second;
                updateTrainingPrompt("Relax Hand - Countdown to First Event: " + String.valueOf(eventSecondCountdown) + "s\n Next up: Close Hand");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second >= 10 && second < 20) {
                eventSecondCountdown = 20 - second;
                updateTrainingPrompt("[1] Close Hand and Hold for " + String.valueOf(eventSecondCountdown) + "s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 1;
            } else if (second >= 20 && second < 30) {
                eventSecondCountdown = 30 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown) + "s\n Next up: Close Pinky");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second >= 30 && second < 40) {
                eventSecondCountdown = 40 - second;
                updateTrainingPrompt("[7] Close Pinky and Hold for " + String.valueOf(eventSecondCountdown) + "s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 7;
            } else if (second >= 40 && second < 50) {
                eventSecondCountdown = 50 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown) + "s\n Next up: Close Ring");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second >= 50 && second < 60) {
                eventSecondCountdown = 60 - second;
                updateTrainingPrompt("[6] Close Ring and Hold for " + String.valueOf(eventSecondCountdown) + "s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 6;
            } else if (second >= 60 && second < 70) {
                eventSecondCountdown = 70 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown) + "s\n Next up: Close Middle");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second >= 70 && second < 80) {
                eventSecondCountdown = 80 - second;
                updateTrainingPrompt("[5] Close Middle and Hold " + String.valueOf(eventSecondCountdown) + "s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 5;
            } else if (second >= 80 && second < 90) {
                eventSecondCountdown = 90 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown) + "s\n Next up: Close Index");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second >= 90 && second < 100) {
                eventSecondCountdown = 100 - second;
                updateTrainingPrompt("[4] Close Index and Hold " + String.valueOf(eventSecondCountdown) + "s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 4;
            } else if (second >= 100 && second < 110) {
                eventSecondCountdown = 110 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown) + "s\n Next up: Close Thumb");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second >= 110 && second < 120) {
                eventSecondCountdown = 120 - second;
                updateTrainingPrompt("[3] Close Thumb and Hold " + String.valueOf(eventSecondCountdown) + "s\n Next up: Relax Hand");
                updateTrainingPromptColor(Color.RED);
                mEMGClass = 3;
            } else if (second >= 120 && second < 130) {
                eventSecondCountdown = 130 - second;
                updateTrainingPrompt("[0] Relax Hand and Remain for " + String.valueOf(eventSecondCountdown) + "s\n Next up: Done");
                updateTrainingPromptColor(Color.GREEN);
                mEMGClass = 0;
            } else if (second >= 130) {
                mEMGClass = 0;
                updateTrainingPrompt("[Training Complete!]");
                updateTrainingPromptColor(Color.DKGRAY);
                mRunTrainingBool = false;
                updateTrainingView(View.GONE);
                //TODO: Replace with internal process and extract/save features.
//                readFromTrainingFile(trainingDataFile);
                TrainTask trainTask = new TrainTask();
                Log.e(TAG, "CALLING TRAINING FUNCTION - ASYNCTASK!");
                trainTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
            if (eventSecondCountdown == 10) {
                mMediaBeep.start();
            }
        }
    }

    private void updateTrainingPrompt(final String prompt) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRunTrainingBool) {
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
                if (mRunTrainingBool) {
                    mTrainingInstructions.setTextColor(color);
                }
            }
        });
    }

    ClassDataAnalysis TrainingData;

    public void readFromTrainingFile(File f) {
        try {
            CSVReader csvReader = new CSVReader(new FileReader(f), ',');
            List<String[]> strings = csvReader.readAll();
            Log.e(TAG, "strings.length = " + String.valueOf(strings.size()));
            TrainingData = new ClassDataAnalysis(strings, 30000);
            double[] trainingDataAll = ClassDataAnalysis.concatAll();
            Log.e(TAG, "trainingDataAll.length = " + String.valueOf(trainingDataAll.length));
            double[] trainingDataSelect = new double[120000];
            if (trainingDataAll != null) {
                System.arraycopy(trainingDataAll, 0, trainingDataSelect, 0, 120000);
//                CUSTOM_KNN_PARAMS = jTrainingRoutineKNN(trainingDataSelect);
                CUSTOM_KNN_PARAMS = jTrainingRoutineKNN2(trainingDataSelect);
            } else {
                Log.e(TAG, "Error! trainingDataAll is null!");
            }
            //Write to Disk?
            if (mKNNcsvWriter != null) {
                String[] strings1 = new String[1];
                for (double p: CUSTOM_KNN_PARAMS) {
                    strings1[0] = p+"";
                    mKNNcsvWriter.writeNext(strings1,false);
                }
            }
//            double[] numbers = new double[496];
//            System.arraycopy(CUSTOM_KNN_PARAMS, 4464, numbers, 0, 496);
//            Log.e(TAG, "CUSTOM_PARAMS2 returned val: " + Arrays.toString(numbers));
            Log.e(TAG, "CUSTOM_PARAMS2 len: " + String.valueOf(CUSTOM_KNN_PARAMS.length));
            mUseCustomParams = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double bytesToDouble(byte a1, byte a2, byte a3) {
        int a = unsignedToSigned(unsignedBytesToInt(a1, a2, a3), 24);
        return ((double) a / 8388607.0) * 2.25;
    }

    private int mNumberOfClassifierCalls = 0;
    private double mLastYValue = 0;
    private static final double[] DEFAULT_PARAMS = {0.000150000000000000, 0.0350000000000000, 0.000200000000000000,
            0.000120000000000000, 0.00200000000000000, 0.000300000000000000, 0.000250000000000000,
            0.000290000000000000, 3.40000000000000e-05, 0.000300000000000000, 0.000700000000000000};
    //[4960]
    private static final double[] DEFAULT_KNN_PARAMS = {2.52570284838834e-05, 2.46410018572031e-05, 2.17028740739075e-05, 2.15119142086479e-05, 2.08144157845188e-05, 2.04833648630074e-05, 2.55567929950814e-05, 2.66301927057671e-05, 2.68272181679023e-05, 2.67939538391554e-05, 2.26631338018881e-05, 1.99997315058376e-05, 2.18622448522584e-05, 2.28200168988706e-05, 2.25382035424696e-05, 2.57486860995084e-05, 2.56522709829797e-05, 2.35614405987431e-05, 2.26643365346237e-05, 1.98611167459978e-05, 2.05933024549955e-05, 2.12982897514635e-05, 2.46689043050031e-05, 2.82857180521956e-05, 2.87486238556977e-05, 2.78445398568975e-05, 2.89569287175870e-05, 2.63604406622930e-05, 2.60388944348302e-05, 2.82044532424924e-05, 2.71552160450931e-05, 2.39395353588667e-05, 2.38683213862594e-05, 2.28711115275799e-05, 2.17938153823035e-05, 2.39532581665458e-05, 2.49024748619228e-05, 3.04563949380730e-05, 3.73622628282631e-05, 7.43406212897591e-05, 0.000759441925141325, 0.000845143574992141, 0.000937821484901308, 0.000968566803715634, 0.000721355723854395, 0.000552553102349706, 0.000418496251681552, 0.000346603470810307, 0.000300411833683895, 0.000296450007007725, 0.000281244829544099, 0.000266347588532602, 0.000263949198386800, 0.000250037278286330, 0.000253390028080523, 0.000262646104058595, 0.000273773322219373, 0.000273918355528519, 0.000263875327839369, 0.000261247937034170, 0.000259545096415406, 0.000272826418057738, 0.000273847730436460, 0.000290233073278571, 0.000277076751622260, 0.000268087541437981, 0.000273909785367490, 0.000252560080656362, 0.000261947279421652, 0.000257871771496332, 0.000260006033917128, 0.000259771911583705, 0.000272450716431823, 0.000269516703510106, 0.000257991296631871, 0.000269314587469059, 0.000261110393569923, 0.000246294714828233, 0.000258204638276910, 0.000266434879025817, 0.000293004936424057, 0.000295088001278810, 0.000272832625379896, 0.000241976265417451, 0.000132563644043736, 6.31934449604157e-05, 3.76296351850586e-05, 2.99260732972367e-05, 3.09830967811042e-05, 2.99671685672103e-05, 3.21881350846757e-05, 3.38037372525031e-05, 2.69009737460016e-05, 2.95495663937697e-05, 3.28888350009048e-05, 3.17211059352038e-05, 3.66412840710789e-05, 3.36504491727913e-05, 2.95881651593980e-05, 2.81129134864588e-05, 2.48224462648893e-05, 2.83988540858817e-05, 2.98051539312218e-05, 5.67856831010620e-05, 5.55589343286993e-05, 5.34159125368146e-05, 5.39514267355827e-05, 2.80219440451770e-05, 2.70453670877507e-05, 2.89919821719510e-05, 2.74765250044629e-05, 2.69187491930682e-05, 2.55384504013920e-05, 2.26497096448984e-05, 2.21962069146215e-05, 2.15708312284239e-05, 1.80307297566768e-05, 1.75473429574034e-05, 1.95988812917549e-05, 2.51465278411704e-05, 2.60087585738709e-05, 2.88292583885299e-05, 2.94119458866182e-05, 7.17264000407088e-05, 0.000149803849603725, 0.000157998760766569, 0.000165874050619828, 0.000177190816263219, 0.000115985585241534, 0.000117816050540669, 0.000119819016852163, 0.000109377635450906, 0.000100105604594522, 9.92003711496560e-05, 8.43756292562766e-05, 8.01859063041307e-05, 8.07789264910596e-05, 6.98336803059956e-05, 7.59298915606718e-05, 8.87560395194675e-05, 0.000101519164205239, 0.000106222938337975, 0.000120409343662233, 0.000125499878990274, 0.000128059078381336, 0.000132377534122363, 0.000131406203717348, 0.000127285771635457, 0.000121921186102582, 0.000120481053857255, 0.000114223177819851, 0.000106386257822782, 0.000106361815534706, 0.000103086834543748, 0.000101802112481045, 0.000100325180188140, 0.000100880990674943, 9.67905562276105e-05, 9.43045543911808e-05, 9.51633651122524e-05, 9.27377891193615e-05, 8.71995011720102e-05, 8.40229079026628e-05, 8.58133518376169e-05, 7.92173827286005e-05, 6.99189258256187e-05, 6.41015440201707e-05, 5.44702541493756e-05, 4.20822213283992e-05, 4.26251785964421e-05, 4.60834243148752e-05, 4.68850686517117e-05, 4.30722201858174e-05, 3.34447515053804e-05, 2.59063726870797e-05, 2.72984526976629e-05, 2.96343073876119e-05, 3.08823591688650e-05, 2.97539270005793e-05, 2.97389526516576e-05, 3.03639411151594e-05, 4.73464035437408e-05, 4.77617453778379e-05, 4.72640196637147e-05, 4.68056554588068e-05, 2.27050493232313e-05, 2.25128998847938e-05, 2.13296814723769e-05, 1.89117767601512e-05, 2.93370288803537e-05, 3.12329127328407e-05, 3.12578823123625e-05, 3.28513081572429e-05, 2.55243336314485e-05, 2.56806513688926e-05, 2.64494930189832e-05, 2.51036305413055e-05, 2.85593684323042e-05, 2.70124888296661e-05, 2.54070667872228e-05, 2.68298707035056e-05, 2.18393716628096e-05, 3.04873032796299e-05, 3.55660643968626e-05, 3.48680274975187e-05, 4.79459745607166e-05, 0.000343195822868596, 0.000612157660013674, 0.000666304257401832, 0.000692831781659055, 0.000695979203971295, 0.000420810977940444, 0.000364828814783928, 0.000344998723616456, 0.000338593462894159, 0.000341133204827454, 0.000359952820746171, 0.000370134393417188, 0.000359892997170427, 0.000372782694246171, 0.000371434656053935, 0.000349478617215949, 0.000363636397794572, 0.000364900555355899, 0.000358227961684185, 0.000371855815731814, 0.000375167437959304, 0.000357765653420111, 0.000371143498764882, 0.000366973084227994, 0.000401499923841740, 0.000448631732730508, 0.000437715392978729, 0.000435735487175436, 0.000406327569793873, 0.000338422834205796, 0.000342631787381042, 0.000346828042087654, 0.000334806988331788, 0.000325046281493474, 0.000292301162932725, 0.000284811590257263, 0.000294128392531190, 0.000276905681720497, 0.000272148347392470, 0.000274058592397403, 0.000254635444958797, 0.000259153830842159, 0.000267317402692877, 0.000240541844824285, 0.000206719291723180, 0.000166757115549396, 7.67207550989256e-05, 4.60379896925296e-05, 3.70867892939034e-05, 2.80788886030570e-05, 3.35816740867686e-05, 3.26075527015370e-05, 3.30814545085266e-05, 3.25361456166712e-05, 2.42864833313394e-05, 2.71361122551999e-05, 2.62186402045148e-05, 2.59478801373851e-05, 2.55351826231281e-05, 2.25377744465142e-05, 2.36927857570666e-05, 3.07183126852222e-05, 3.19962785551174e-05, 3.28402218258736e-05, 4.13653987685428e-05, 3.76433303585344e-05, 3.63942664889126e-05, 3.54183426912554e-05, 2.32693069971356e-05, 2.37125977743545e-05, 2.80648149254033e-05, 2.82889458230117e-05, 2.81997574682314e-05, 2.81527762626460e-05, 2.90279874628783e-05, 2.72915513394900e-05, 2.67465030986429e-05, 2.72984952217215e-05, 2.34692280864392e-05, 2.53426017570577e-05, 3.60889006603343e-05, 0.000211552315897938, 0.000287041007977347, 0.000304943151786068, 0.000317305591044210, 0.000299775141570317, 0.000192558284169368, 0.000201884548840876, 0.000200702451290123, 0.000199577436838494, 0.000196855394384893, 0.000183200850242939, 0.000173845947442876, 0.000192935144803941, 0.000213543969359202, 0.000212943828048094, 0.000220857943058504, 0.000213685397283818, 0.000201733639609661, 0.000203278243893006, 0.000206809745657347, 0.000197454623456034, 0.000184186781126036, 0.000165888601567556, 0.000140488787801647, 0.000159453795408387, 0.000161962794166990, 0.000171971680121058, 0.000182092946218753, 0.000158149277856373, 0.000156199734839206, 0.000151266004694783, 0.000156169959583926, 0.000160439368700452, 0.000160974267553692, 0.000152612723133425, 0.000132156580245061, 0.000130027805133293, 0.000131594607365195, 0.000131518703713806, 0.000138178333579114, 0.000141904187085120, 0.000132713233725471, 0.000150368391892620, 0.000158827238324658, 0.000147667614860301, 0.000157150292376667, 0.000450212481552335, 0.000444718374309912, 0.000443366394659160, 0.000437848960705133, 8.77939468406430e-05, 0.000115119492374665, 0.000118174676232470, 0.000118744096044783, 0.000118401257291548, 8.54837417460802e-05, 5.42852305403261e-05, 4.80646405308155e-05, 4.97171742981738e-05, 5.75185710443140e-05, 5.31798203113891e-05, 5.77811505955479e-05, 5.59327072955461e-05, 4.76705592609786e-05, 4.26214959381235e-05, 3.07885669534065e-05, 2.19622835387924e-05, 2.16955920610874e-05, 2.59172866396129e-05, 2.66645249285873e-05, 2.58734176266121e-05, 2.66243088326452e-05, 6.41318366010145e-05, 6.71945046423919e-05, 7.43617166795366e-05, 7.50531595963276e-05, 4.58797176420477e-05, 5.15137851039151e-05, 4.18311476987899e-05, 3.97779915492278e-05, 3.88304903808193e-05, 2.74484564045576e-05, 2.15224045858357e-05, 2.22476752406479e-05, 2.34710405180996e-05, 2.41183236804331e-05, 4.70417388122095e-05, 0.000258565307797292, 0.000263970213302859, 0.000269567494770793, 0.000271394079982808, 0.000115010848308848, 9.71768349254165e-05, 9.45845856820276e-05, 9.72208355055290e-05, 9.56666395203949e-05, 0.000104132550544227, 0.000104151955195686, 0.000102724538217877, 0.000102641591048647, 9.69653672701839e-05, 9.47607023313810e-05, 8.65103289804685e-05, 8.95654879514849e-05, 8.98546428119762e-05, 9.09888244064879e-05, 8.92321409488346e-05, 8.63946934606922e-05, 8.63990919542961e-05, 8.33304337614375e-05, 8.44963591619176e-05, 8.06310307084575e-05, 8.06766491951721e-05, 7.78184004421484e-05, 7.77509498121241e-05, 7.61075921573834e-05, 7.34898853748626e-05, 7.95663779287285e-05, 7.71276325366281e-05, 8.32727199735690e-05, 8.75727854726520e-05, 8.90358712402046e-05, 8.74752321817773e-05, 7.90913821426194e-05, 7.59879333128508e-05, 6.96855597543514e-05, 6.72430905900793e-05, 7.05746554169975e-05, 6.39190497955084e-05, 5.68965299611463e-05, 4.95586751130481e-05, 3.98987044516130e-05, 3.20918902594998e-05, 2.67686221258556e-05, 2.78978135691887e-05, 2.66906444149153e-05, 2.69300294837997e-05, 2.46530637935465e-05, 2.46672887359737e-05, 2.62925017358114e-05, 2.70880715756347e-05, 2.99526053628040e-05, 3.12498148001511e-05, 3.45801050799573e-05, 3.24911879142612e-05, 2.92069531927871e-05, 3.00664171440352e-05, 2.51095386689902e-05, 2.59032060127558e-05, 2.54240439936272e-05, 2.23041325619478e-05, 2.08513939947558e-05, 2.34625660917369e-05, 2.38967862831855e-05, 2.68111874443655e-05, 2.79206748791168e-05, 2.53986235196970e-05, 2.86807178262368e-05, 2.76082014400787e-05, 2.58385957231280e-05, 2.55728825009462e-05, 3.10906990615056e-05, 3.13469394432187e-05, 3.03440225057527e-05, 3.01502121401514e-05, 2.65606338374727e-05, 2.17966655066686e-05, 2.26096100302432e-05, 2.49369568033006e-05, 3.16067980009202e-05, 0.000884127137380154, 0.00114667245593241, 0.00122626127616813, 0.00127033493213861, 0.00117596058757136, 0.000656957124611880, 0.000521676670518152, 0.000386222956689366, 0.000370063820261927, 0.000367195575433877, 0.000327292258924811, 0.000330949534179369, 0.000314424284692864, 0.000308621558910357, 0.000297077546994138, 0.000288490753383689, 0.000283876938057084, 0.000251625550530291, 0.000244350222137105, 0.000223022360460540, 0.000195556542123773, 0.000193081196339177, 0.000182704668766549, 0.000175978854057252, 0.000180917791782664, 0.000178316408402323, 0.000247946473843327, 0.000265043789177744, 0.000278807398036046, 0.000285823556215684, 0.000247575319641649, 0.000257887057856832, 0.000264028620768572, 0.000258890991051478, 0.000258283316823399, 0.000275425620538296, 0.000264230777493028, 0.000293143248651681, 0.000310272601491115, 7.13319647581688e-05, 7.16863075533014e-05, 6.70873814947375e-05, 8.11002396644791e-05, 9.83152528766127e-05, 0.000111873923465203, 0.000152081595519540, 0.000152535355163127, 0.000188924755712153, 0.000189413630737792, 0.000155909430185706, 0.000152489142575615, 7.90101972070393e-05, 6.54498371361330e-05, 6.09794117010038e-05, 5.85694255149851e-05, 5.90078484662176e-05, 5.67892816918918e-05, 5.82034165039530e-05, 5.64100530133001e-05, 5.98007861236590e-05, 6.59912457964316e-05, 6.42295754559106e-05, 6.57403625286316e-05, 6.92491855627721e-05, 6.08325195699698e-05, 5.86526198006097e-05, 6.28627411745507e-05, 6.19405093961927e-05, 6.17414590418972e-05, 6.33840694366812e-05, 6.03079277981692e-05, 6.03803692916418e-05, 6.15408096204078e-05, 6.16089412895051e-05, 6.34829271904953e-05, 5.77405403363393e-05, 5.77646994859346e-05, 5.47696568097765e-05, 6.22534056383437e-05, 0.000664442023652231, 0.000717912551818405, 0.000746629444307749, 0.000764796258789231, 0.000600293668700045, 0.000378322836282150, 0.000341876848559100, 0.000330475530004979, 0.000315697760793472, 0.000302853806829241, 0.000318783981944595, 0.000310413956267698, 0.000310834825002187, 0.000295534592296500, 0.000286162193439455, 0.000296079185786145, 0.000271260832153586, 0.000299122171608820, 0.000312359892344237, 0.000295825482348709, 0.000303639462176965, 0.000284432086417362, 0.000266782103198788, 0.000251398226219202, 0.000249266316817803, 0.000251697163074289, 0.000259600744121051, 0.000273575965811534, 0.000262864310096836, 0.000279248593401540, 0.000286103215662380, 0.000283459012339849, 0.000293505737729114, 0.000280168333882715, 0.000249015330290739, 0.000239318907430143, 0.000243994455916586, 0.000234353543896735, 0.000241556655910184, 0.000250498105781842, 0.000232797005057430, 0.000226372039268326, 0.000185545595958387, 0.000144337773047956, 0.000100409861760574, 7.73255031381602e-05, 6.67127384082430e-05, 5.92611456720291e-05, 6.11888805876569e-05, 6.70690848522893e-05, 6.74482206778194e-05, 7.07102904864654e-05, 7.02057964778145e-05, 6.20269405228924e-05, 6.43977459810889e-05, 6.55652521354247e-05, 6.37335537919347e-05, 6.55573870883351e-05, 6.39044768218386e-05, 5.86490378646611e-05, 5.92490202926535e-05, 5.51538181443681e-05, 5.71995775569393e-05, 6.88094869273832e-05, 7.59406616633774e-05, 7.79767091063568e-05, 8.36584770218195e-05, 7.81337631716963e-05, 6.46846083829096e-05, 6.19536540852591e-05, 5.47246334811250e-05, 5.77911299817657e-05, 5.51540622103066e-05, 6.11906901234422e-05, 5.94314515814253e-05, 6.38119003917491e-05, 7.02354454032744e-05, 7.10104968196756e-05, 7.20066679324560e-05, 7.30301281528894e-05, 7.22646800497164e-05, 7.00905147070487e-05, 6.70260545832219e-05, 6.82089222381847e-05, 9.98595939328693e-05, 0.000114739434043343, 0.000121224926916963, 0.000130691604960423, 0.000116220424754733, 0.000105381852791541, 0.000100774133018186, 0.000104643680577037, 0.000102522785103121, 0.000105877640532522, 0.000104090487779823, 9.47435159114927e-05, 8.23839803984621e-05, 8.27237941066117e-05, 8.34204730385245e-05, 8.06559739357912e-05, 8.80754484908750e-05, 8.51317147758910e-05, 8.07072172314931e-05, 8.14320556446705e-05, 7.52445866126577e-05, 6.98322724446337e-05, 7.37111442623970e-05, 7.60396402188559e-05, 7.95558380145460e-05, 8.07357725222562e-05, 8.16734444018149e-05, 7.64455885253694e-05, 7.46011252951280e-05, 7.44411463324112e-05, 6.98435897112011e-05, 7.01234945418565e-05, 7.18098748799692e-05, 6.70299844977729e-05, 6.84246380519768e-05, 6.64353399479934e-05, 6.61261576833710e-05, 6.90309691389805e-05, 7.11159473117019e-05, 7.14989283669758e-05, 8.72013185349723e-05, 9.33200938546418e-05, 9.11732519542662e-05, 8.84008679679099e-05, 7.90274025954982e-05, 7.37877209602694e-05, 7.55910170095109e-05, 8.04051539446194e-05, 7.75924470305988e-05, 7.16826153667396e-05, 6.71863948205263e-05, 6.85947800115758e-05, 6.61744074396847e-05, 6.89522911924134e-05, 6.93103324767859e-05, 7.29703913641779e-05, 6.93005411146128e-05, 7.41012922256838e-05, 7.21653631365774e-05, 6.69296104197012e-05, 6.67385789482196e-05, 6.35510393082007e-05, 6.60986387397761e-05, 7.11012285460973e-05, 8.78655293086806e-05, 0.000142230239320677, 0.000142939186425147, 0.000142798907636486, 0.000139744564304294, 7.38979819717511e-05, 7.53156756716004e-05, 7.76033885238735e-05, 7.55550770676416e-05, 7.33057810600151e-05, 6.89201209663395e-05, 6.60358250877607e-05, 6.90928937698501e-05, 7.21822995654876e-05, 7.24169936872227e-05, 6.91393466369345e-05, 7.00653296001511e-05, 6.88252274447735e-05, 0.000167171344650012, 0.000310670743648307, 0.000319270195957441, 0.000330224845218796, 0.000329142165418107, 0.000167317504099693, 0.000159253229915469, 0.000158061018708516, 0.000161300570876025, 0.000164796712769584, 0.000164793347853602, 0.000158851536817677, 0.000155655215295232, 0.000165358452813136, 0.000153698383245298, 0.000147843358800985, 0.000152446289002669, 0.000138969212299652, 0.000134503198737313, 0.000133990115537304, 0.000130233128652722, 0.000125272835245199, 0.000134793261249798, 0.000136629082461578, 0.000154529456847049, 0.000159178817908287, 0.000160641393246168, 0.000157671530553833, 0.000140138058101378, 0.000132668912104746, 0.000124050414240429, 0.000125945829476567, 0.000121434892122906, 0.000121026229147157, 0.000121171492976528, 0.000117785355389716, 0.000115242548881630, 0.000107997130984828, 0.000110492718832690, 0.000108634717318824, 0.000110123804784104, 0.000114520637395338, 0.000110332485672354, 0.000106387622928423, 0.000110491477664292, 9.43569848662735e-05, 8.60313185841268e-05, 8.18009670000526e-05, 6.45733991979291e-05, 6.03542953702256e-05, 6.61332278714479e-05, 6.54768178471130e-05, 7.32505658361483e-05, 9.17201091698961e-05, 0.000105064885804398, 0.000103569531007912, 0.000100477872432849, 0.000100031811447901, 6.94821850725204e-05, 7.13384893967940e-05, 6.85561731714528e-05, 6.44608516796019e-05, 6.84419309578196e-05, 6.37975204774658e-05, 6.51970010292189e-05, 6.99669117645647e-05, 6.69987821035287e-05, 6.60659369026854e-05, 6.82710561479267e-05, 6.45624770668503e-05, 6.81005937901770e-05, 7.19931468642131e-05, 7.10178994333971e-05, 7.06114275975150e-05, 6.53992319228926e-05, 6.72591882037934e-05, 7.03527741147714e-05, 6.92633918945969e-05, 7.07317615957108e-05, 6.92810058060490e-05, 6.88961932586411e-05, 0.000222980000895397, 0.000447604944013104, 0.000522441924214899, 0.000568116978692368, 0.000570298043216856, 0.000416985758178958, 0.000369042241625889, 0.000310037634241490, 0.000293904918969726, 0.000306069129474203, 0.000259904381526027, 0.000258220958184880, 0.000256807098509734, 0.000246354634166352, 0.000265163978581462, 0.000259332054877911, 0.000254432822369293, 0.000256912347981908, 0.000245985371476489, 0.000242628962238406, 0.000246797415118864, 0.000235783702888722, 0.000230198361308099, 0.000218701458141684, 0.000193926157098577, 0.000189547012961986, 0.000183997319765625, 0.000178369483438132, 0.000187356339168041, 0.000188007204574730, 0.000180081860773482, 0.000179863797691515, 0.000176276405734834, 0.000174487948264850, 0.000177798172922738, 0.000183525253731272, 0.000192108932957706, 0.000187612120967202, 0.000196804798954469, 0.000220380554757164, 0.000233951625921646, 0.000242818843822646, 0.000246107217647096, 0.000241660413194193, 0.000232376595280477, 0.000217559719341356, 0.000215994906560310, 0.000184173099031036, 0.000142505113491559, 0.000131273420618126, 7.47092438841105e-05, 5.79486903869998e-05, 5.54209380478684e-05, 6.62053938531598e-05, 5.97810872203715e-05, 6.00294626553245e-05, 6.20535681073178e-05, 5.07877402629610e-05, 4.93762969179601e-05, 5.76201317454750e-05, 6.45140075352548e-05, 6.47640358758404e-05, 6.29945556119421e-05, 5.81751317269700e-05, 4.86543660401018e-05, 4.93310421903010e-05, 5.04261992680605e-05, 5.51889816586759e-05, 5.28291880270964e-05, 5.02744588815902e-05, 4.80459786919496e-05, 4.32212285887960e-05, 5.22037537231906e-05, 5.30176907755022e-05, 5.71658318769955e-05, 6.11026201009327e-05, 5.45881863753577e-05, 5.31822002979281e-05, 5.15769349609024e-05, 4.51053114982778e-05, 4.45618574012378e-05, 4.30778930860045e-05, 4.35908851432182e-05, 4.48138205920762e-05, 4.63263511441555e-05, 5.11893050684958e-05, 7.84138786169601e-05, 0.000149956264582564, 0.000157772956274772, 0.000161251992025488, 0.000157243696451620, 9.67498586501027e-05, 8.91767103328873e-05, 9.02236848390877e-05, 8.71473878129427e-05, 8.87909357564073e-05, 9.01888682203976e-05, 8.91786664792204e-05, 9.46267683666631e-05, 9.13525849443644e-05, 9.21452962499803e-05, 9.12361048207972e-05, 8.60102560061976e-05, 8.60777662468346e-05, 8.78935980763685e-05, 8.50683839915018e-05, 8.31858456719789e-05, 8.18559691384716e-05, 8.37755987992971e-05, 8.87984564034672e-05, 8.88613747545547e-05, 8.44468009024057e-05, 8.16874695926001e-05, 7.20897843795688e-05, 7.24244339689963e-05, 7.44219145323357e-05, 7.53571848643544e-05, 7.44362602284906e-05, 7.42655287465256e-05, 7.03144379535366e-05, 7.01017019253928e-05, 7.41907660122342e-05, 7.29483157365189e-05, 7.51894838353094e-05, 7.50960867815096e-05, 0.000134285640835803, 0.000163018226298536, 0.000163334613572349, 0.000164582166209815, 0.000135175946704823, 8.02924991239441e-05, 7.15196587277239e-05, 6.98871252693295e-05, 6.90865777055756e-05, 6.31778311599768e-05, 6.64216465520041e-05, 6.53699220738431e-05, 6.23875806585384e-05, 6.43502296479418e-05, 6.72547932084409e-05, 6.87152690512320e-05, 7.18361465946305e-05, 6.80965222869808e-05, 6.63531823408869e-05, 6.35968502006625e-05, 6.62452634981522e-05, 7.71787022163616e-05, 7.41496426323668e-05, 7.27039315004722e-05, 6.66533968464615e-05, 5.42465918652910e-05, 5.27274440631630e-05, 5.10698916562596e-05, 5.35467915614876e-05, 5.69308070872702e-05, 5.61335796516299e-05, 6.09689127277189e-05, 6.10904278956425e-05, 6.71126406838265e-05, 6.85234848171784e-05, 6.82745457531694e-05, 6.94695206217365e-05, 6.56884032522607e-05, 6.01266283158580e-05, 6.28972165232997e-05, 6.74875932403401e-05, 7.05859295025235e-05, 7.01045812349572e-05, 6.79451431022104e-05, 6.64146176461888e-05, 0.000310026195682276, 0.000427334525018378, 0.000473602432386876, 0.000495298729650563, 0.000437622876828227, 0.000332462878790194, 0.000308833427592186, 0.000280917476667974, 0.000281183821319807, 0.000264843362994869, 0.000256141025982843, 0.000249805348290862, 0.000254026597528692, 0.000252778588481521, 0.000240139771918647, 0.000239210373394116, 0.000222751984670606, 0.000223955869106846, 0.000211299823988911, 0.000190946397804240, 0.000193922757537746, 0.000189211100660979, 0.000190828365287791, 0.000192846285390845, 0.000195546625322374, 0.000188818293831641, 0.000200926373360809, 0.000218328827676927, 0.000220110020801888, 0.000212189961948922, 0.000213242315553545, 0.000217031435918301, 0.000218706186919319, 0.000227976742480621, 0.000234410196644664, 0.000215403221785063, 0.000211664496799112, 0.000209317582218304, 0.000185537650240011, 9.50423081690403e-05, 9.65534059387927e-05, 9.98369928215255e-05, 9.53886651342145e-05, 7.82625773398108e-05, 8.09879438704837e-05, 9.27067301785313e-05, 9.90879104084586e-05, 0.000125640028938301, 0.000120658100590622, 0.000103957455612858, 9.79870539811296e-05, 5.71969896136065e-05, 5.35945157718604e-05, 6.15791545779247e-05, 6.63425044552170e-05, 6.51838168181756e-05, 7.75570042419381e-05, 8.10187110461514e-05, 8.15868054974015e-05, 8.88000865788285e-05, 8.39973265089389e-05, 7.45574297673526e-05, 6.56629965885587e-05, 5.65294609864124e-05, 5.89488154567977e-05, 5.42507888819000e-05, 8.43618400739636e-05, 8.43930645749051e-05, 8.22850924600664e-05, 9.11972077951221e-05, 7.27115691914693e-05, 7.73188571592147e-05, 7.62162380529547e-05, 6.87835450404231e-05, 6.22874928679850e-05, 6.20517037965142e-05, 7.56356655215488e-05, 8.63595873752293e-05, 9.39602687070364e-05, 0.000587458897055058, 0.000749826900923452, 0.000780139346438118, 0.000811219840916770, 0.000659096169092235, 0.000429586070083633, 0.000388308411289748, 0.000356449631322464, 0.000324449960386102, 0.000298322904770053, 0.000308470570584902, 0.000314905758716300, 0.000322324413830302, 0.000317670952357667, 0.000302434906063443, 0.000302044638262168, 0.000306772533618794, 0.000311985363983028, 0.000314189352722695, 0.000319050140490976, 0.000312058508805662, 0.000290333522912599, 0.000284411957754798, 0.000275601499581511, 0.000283288669461384, 0.000292968773956020, 0.000298263317504723, 0.000300650396908318, 0.000317165572077650, 0.000297555889161564, 0.000291304712516669, 0.000270845085399292, 0.000282972519624010, 0.000283127826462158, 0.000290826878118219, 0.000289089856673723, 0.000271465967782740, 0.000249172642279032, 0.000230162458452402, 0.000219264945986433, 0.000205849061414992, 0.000187321332983817, 0.000165817463455094, 0.000142277561947900, 0.000130293058955169, 0.000122561852737483, 0.000103892638223631, 9.24954190198094e-05, 9.18189953016033e-05, 8.08490898740135e-05, 6.92053631074978e-05, 7.30756683647740e-05, 6.75303291954165e-05, 7.41895626240589e-05, 6.65369236963845e-05, 6.52742074166395e-05, 6.37565985080534e-05, 6.26116237731230e-05, 6.31243840667840e-05, 6.72547101483088e-05, 7.07823011123467e-05, 7.16925825568076e-05, 6.36029578163007e-05, 5.63313344767126e-05, 5.42654977503529e-05, 5.08522338853723e-05, 5.02062909659195e-05, 5.46712622036421e-05, 5.36926614588039e-05, 5.20364458417527e-05, 6.45491296237498e-05, 6.36796987800841e-05, 6.15607134673989e-05, 6.27876465934347e-05, 6.09273533193684e-05, 6.27208991242717e-05, 6.41518318457885e-05, 6.61357920203120e-05, 5.66565775581251e-05, 5.00044486817339e-05, 5.15853698484173e-05, 5.79555851243832e-05, 7.12081406012693e-05, 0.000547010490335484, 0.000858128574578430, 0.00100708839642298, 0.00110342712381208, 0.00114836395846582, 0.000937273958517792, 0.000849565198001590, 0.000793919217196841, 0.000785072447043326, 0.000719723747528957, 0.000665795388457067, 0.000590855682372051, 0.000477539273150780, 0.000378414580315996, 0.000413154143696614, 0.000449952141570066, 0.000475376711470940, 0.000492536922291328, 0.000529933416042613, 0.000571585990292048, 0.000619975461383742, 0.000614803874293294, 0.000566869912646546, 0.000495059422773958, 0.000415505735869868, 0.000399110761757534, 0.000398561152783452, 0.000385896343896376, 0.000381089738953852, 0.000366537905102851, 0.000330000944687624, 0.000338392957997973, 0.000348802018612483, 0.000347549167260749, 0.000361126508017905, 0.000332360676188828, 0.000297904603183637, 0.000275203478415063, 0.000289062418278541, 0.000254550100287172, 0.000252651391492995, 0.000235566079668838, 0.000168911042815400, 0.000128942170175759, 7.76609279786035e-05, 6.62862107889997e-05, 6.32000258210685e-05, 6.88555980329816e-05, 7.13501008892270e-05, 7.35572537119089e-05, 7.35878618534615e-05, 7.45881858345706e-05, 7.64096202097780e-05, 7.08783288292940e-05, 9.63945374468153e-05, 0.000103016569902831, 0.000107465780647780, 0.000118619514291127, 0.000104483626399347, 8.26935439157128e-05, 8.48781445760905e-05, 8.40472209015807e-05, 9.05040449476217e-05, 8.96509863464702e-05, 8.19884995176728e-05, 7.44512398747874e-05, 6.11232293401713e-05, 6.92132313723809e-05, 8.31773123945007e-05, 9.58469973194078e-05, 9.71587358308742e-05, 9.17506352127077e-05, 8.13968803397858e-05, 6.57434773245861e-05, 7.35353560556924e-05, 7.53283305823134e-05, 7.53234681716951e-05, 7.89585077567733e-05, 8.06453345386879e-05, 8.09831461245160e-05, 8.23722139731590e-05, 8.50732373780069e-05, 9.25462974168243e-05, 0.000207665462743935, 0.000539138238797890, 0.000550022853626927, 0.000557173081013374, 0.000551280402096193, 0.000202454153992432, 0.000165271642468146, 0.000155136508828057, 0.000167812242280061, 0.000175537972556724, 0.000178227680880231, 0.000173620157357065, 0.000163313330595427, 0.000157280156895929, 0.000148186063218551, 0.000142633951244503, 0.000150188749052142, 0.000148995630945530, 0.000142882679910210, 0.000150497887530353, 0.000147143213933374, 0.000151986049762886, 0.000168077572886958, 0.000176917352364699, 0.000186414211976818, 0.000191074884851393, 0.000181042381997050, 0.000181502631632721, 0.000179503997839248, 0.000177980961551053, 0.000184250174092426, 0.000173756319637756, 0.000167106758584711, 0.000154255018034304, 0.000143779029442242, 0.000137913987951990, 0.000138070762978300, 0.000128445751893480, 0.000120257744837211, 0.000122047047049900, 0.000102228509722767, 9.67005034498196e-05, 0.000102892237851564, 9.56669846498000e-05, 0.000104652837709671, 0.000105728540996294, 9.44865832843344e-05, 9.12279807661327e-05, 7.98754952703090e-05, 8.25307900731857e-05, 7.52309277995108e-05, 6.95549563769275e-05, 6.76559442535976e-05, 7.22184157377825e-05, 7.81652582717096e-05, 8.01045186975971e-05, 8.32560391106673e-05, 8.42354363785217e-05, 7.59057571709400e-05, 7.36713326218590e-05, 7.16680202406789e-05, 5.96814267440071e-05, 6.11338725609985e-05, 6.03161092389327e-05, 5.63206231897756e-05, 5.58037796472186e-05, 5.05363880600637e-05, 5.42518007281477e-05, 5.68404351136648e-05, 6.64973942851221e-05, 7.54279887069833e-05, 8.63913093510581e-05, 9.81111610111789e-05, 9.27228870943773e-05, 9.51227079043044e-05, 8.37262512099868e-05, 7.03544226062365e-05, 7.56823804829813e-05, 6.86239776143671e-05, 6.32170653030876e-05, 7.52521137609274e-05, 0.000247530722938041, 0.000291659585663489, 0.000313214598372043, 0.000320844248815469, 0.000280180061500100, 0.000185717756424073, 0.000183523661241054, 0.000181404891533482, 0.000182629785102048, 0.000178417237621603, 0.000162220046965683, 0.000156700959711953, 0.000156659585404663, 0.000153333697632973, 0.000151342870735889, 0.000146840355913355, 0.000149104563667520, 0.000137168985916723, 0.000136749333670004, 0.000132414408995120, 0.000128396893168712, 0.000148205373715280, 0.000179575691651127, 0.000175102686288982, 0.000182254967386518, 0.000162898405391122, 0.000129218392471404, 0.000140388513507261, 0.000134277051870991, 0.000142180596635192, 0.000139208637783856, 0.000132110378076985, 0.000125507658043176, 0.000131698870769433, 0.000119979839032537, 0.000124208878749354, 0.000119326132974039, 0.000113743871544776, 0.000114655410974699, 0.000135317985006384, 0.000148522137971721, 0.000147628847436531, 0.000153664180694366, 0.000146320428426635, 0.000138950342556759, 0.000131258907371686, 0.000170045978014212, 0.000184435802401713, 0.000174658032795963, 0.000172598938807803, 0.000130046881073869, 9.37691780070827e-05, 9.59261756071640e-05, 9.23263378039074e-05, 8.72678039528649e-05, 9.12851641919631e-05, 8.60451677547573e-05, 8.82889203499282e-05, 8.63124930384204e-05, 8.23517677603234e-05, 8.65835110199503e-05, 8.39126097591279e-05, 8.35871352262420e-05, 7.68184704879968e-05, 6.81928128328492e-05, 7.21336607891011e-05, 7.58678697066033e-05, 8.53737286853369e-05, 9.48543453773238e-05, 9.16355581877063e-05, 8.71960854009886e-05, 8.61441357066540e-05, 7.77704805581354e-05, 7.67509072082746e-05, 7.95603867157697e-05, 8.44331102442710e-05, 8.70759761868357e-05, 9.85669691533658e-05, 0.000102026987576545, 0.000106898381322073, 9.73797722365769e-05, 9.02026925313640e-05, 8.52409106894449e-05, 8.68758642291518e-05, 8.73849533838365e-05, 8.52922533167155e-05, 8.84487088067767e-05, 0.000499859865304750, 0.000544811250698356, 0.000562174661960997, 0.000572108146739954, 0.000319436825589696, 0.000238853291345643, 0.000221196355536591, 0.000212738950978907, 0.000203178554800623, 0.000193013707438301, 0.000186068146740105, 0.000185172060207265, 0.000174175698263638, 0.000166981026234956, 0.000149766077452521, 0.000145028502123121, 0.000144219953921368, 0.000149712109467246, 0.000147426586924669, 0.000146514342073781, 0.000150127454577019, 0.000153882034974903, 0.000161316107856566, 0.000164187980372810, 0.000154555110479281, 0.000153012657844469, 0.000160074221957746, 0.000144229266181560, 0.000144245538079956, 0.000151539876725736, 0.000151421129442335, 0.000180128837297304, 0.000193033453706760, 0.000204458501252729, 0.000203992290058484, 0.000207353334648849, 0.000196999637445595, 0.000181482833962394, 0.000174947908610267, 0.000162108165028574, 0.000161128187890916, 0.000151238751773204, 0.000135981584866685, 0.000120603915622780, 0.000105979292747913, 8.57591654384382e-05, 8.49968017711036e-05, 7.97885678044787e-05, 7.01821011961782e-05, 8.22220294295602e-05, 8.08730605927424e-05, 8.05051059969853e-05, 8.84776558776754e-05, 9.37408239072866e-05, 8.75858726252335e-05, 9.31608481302143e-05, 9.35097549866500e-05, 7.45844240205044e-05, 7.60445531807488e-05, 7.45951958004472e-05, 7.84596462463012e-05, 8.20600162897421e-05, 7.58314119459907e-05, 7.00585096341885e-05, 6.92902446954234e-05, 6.49560766763399e-05, 7.22125140121264e-05, 7.62141024535849e-05, 7.47228861405830e-05, 7.08694733861230e-05, 6.58086540006799e-05, 6.97945645534056e-05, 6.96936614031239e-05, 6.83119648221060e-05, 6.76867904181090e-05, 5.36764449428224e-05, 5.78727334974794e-05, 5.80772926950430e-05, 6.11664195889055e-05, 7.01573714397851e-05, 7.40072444572864e-05, 7.50622059966956e-05, 8.15212935260427e-05, 0.000355611327443993, 0.000368305198368251, 0.000370218781897815, 0.000372475675662714, 0.000174421873829799, 0.000130367189896826, 0.000135706824563379, 0.000135101682971512, 0.000158480491787497, 0.000161782854769070, 0.000149894675820328, 0.000144576209175174, 0.000128502314343077, 0.000126651242474749, 0.000142235253610304, 0.000153456689391052, 0.000150380098793929, 0.000143678726594439, 0.000132747256736026, 0.000121428894711536, 0.000115616056643833, 0.000111128820860497, 0.000106030581191651, 0.000104746608664290, 0.000107284423795139, 0.000108930859206772, 0.000104537202308000, 0.000103166479718615, 9.85505731711383e-05, 9.38076527576074e-05, 9.02602339807197e-05, 9.44071248807787e-05, 8.97188706499214e-05, 8.88832357287676e-05, 8.76337962915415e-05, 7.81720162632569e-05, 8.62765393678752e-05, 9.36342801531615e-05, 9.31835756383089e-05, 0.00577580249173521, 0.00570267137301786, 0.00486227930554609, 0.00499282672329048, 0.00477860711641418, 0.00472283693001437, 0.00565579213460773, 0.00588465720882712, 0.00595186739027221, 0.00594133076436482, 0.00525676069940241, 0.00471553123285024, 0.00503718406684412, 0.00536582695543178, 0.00527167554068226, 0.00608178379347598, 0.00597875991643619, 0.00544230108462700, 0.00521131086855974, 0.00464519312374877, 0.00468829142668782, 0.00489529021958351, 0.00554296118897281, 0.00617279869752914, 0.00633960833920492, 0.00602798663177405, 0.00622952617624582, 0.00597606143582834, 0.00588112113420267, 0.00652754842807977, 0.00615328519983853, 0.00555597375020324, 0.00557553437899622, 0.00525308559045920, 0.00519881264712120, 0.00560587324705635, 0.00577263205536113, 0.00688183731076300, 0.00819039512084916, 0.0136722644523700, 0.0956100369940746, 0.135349057971976, 0.181705433762830, 0.205636941918786, 0.158391783316037, 0.124962965750793, 0.0955976903830349, 0.0810995634011663, 0.0716005488367605, 0.0702064069406787, 0.0665206184764764, 0.0635431674305541, 0.0623094974434017, 0.0596999322035970, 0.0602803662351092, 0.0628758793304226, 0.0652956328562645, 0.0636578738662784, 0.0634068222452556, 0.0619207218630472, 0.0620061845064074, 0.0653825877497136, 0.0656022465881146, 0.0690466273625962, 0.0662767356602436, 0.0637198598521839, 0.0655958054777451, 0.0589680807280408, 0.0618009622176142, 0.0607907328349943, 0.0608697118239839, 0.0614665728013738, 0.0643737985247530, 0.0641874585469237, 0.0610973911403868, 0.0631684504488908, 0.0614793322142794, 0.0587373277957218, 0.0613930846852836, 0.0626872805792516, 0.0647746056212875, 0.0658432864207076, 0.0549733132867665, 0.0431662025750420, 0.0239625529671140, 0.0117356547110093, 0.00750654395529486, 0.00652301796238614, 0.00648055715870925, 0.00638456360175423, 0.00695617126610207, 0.00731198247878051, 0.00618606829646316, 0.00664807019582063, 0.00721592467330613, 0.00698129750734104, 0.00806104932828458, 0.00731346692896916, 0.00621946661621829, 0.00584802960199102, 0.00537724003418755, 0.00615003654522012, 0.00668525351081265, 0.0103505412055860, 0.00975149385090052, 0.00882016543641786, 0.00902006324088883, 0.00619128265066982, 0.00615288393469198, 0.00660082570252132, 0.00634084301566115, 0.00604359988202040, 0.00584033317112527, 0.00512076819181715, 0.00505742677110535, 0.00483182621319923, 0.00413380272299142, 0.00409187949387130, 0.00452462728256049, 0.00547680187446958, 0.00577344996982954, 0.00647856544872750, 0.00666083241071974, 0.0100722927320318, 0.0238249146482019, 0.0283343678890696, 0.0331001307226239, 0.0374672849973219, 0.0276023239477138, 0.0280940710870813, 0.0283922045168972, 0.0256546562783475, 0.0227344813567304, 0.0225083376814793, 0.0187796631406765, 0.0174226823653179, 0.0178125212676762, 0.0160142135026218, 0.0176087784297036, 0.0207917465151974, 0.0237492999715610, 0.0250750575543541, 0.0282587129512597, 0.0291556236651293, 0.0294094185812590, 0.0308902712956394, 0.0306787019275909, 0.0298555867520689, 0.0288488004091953, 0.0288416012145893, 0.0272962265824889, 0.0252962669521618, 0.0249512447145440, 0.0239011761716158, 0.0237402468860694, 0.0234238061807497, 0.0232329610392730, 0.0220963333823423, 0.0211451851030820, 0.0216806635268118, 0.0213490411785164, 0.0206276663591615, 0.0196478956748460, 0.0194533152714104, 0.0179847344974726, 0.0149354518890707, 0.0131360202964881, 0.0100310778232145, 0.00855137063457669, 0.00936218145283819, 0.00992319982739531, 0.0101955978442952, 0.00900222663447648, 0.00708187065772136, 0.00604132261429669, 0.00622973423730350, 0.00673530502292660, 0.00700783512672459, 0.00652726764873312, 0.00656833284262583, 0.00669895428461293, 0.00923440139528237, 0.00947588560387091, 0.00929162908451461, 0.00891451902610300, 0.00533740253927211, 0.00516461902789700, 0.00498889712468484, 0.00452452512402370, 0.00621820589782134, 0.00674847275641062, 0.00678560776846660, 0.00725104596034459, 0.00601426993636686, 0.00593851882571871, 0.00618089403382053, 0.00587913935298864, 0.00655200145865310, 0.00624000083827588, 0.00583126982608154, 0.00608250061244961, 0.00501609808763565, 0.00641452074633446, 0.00731224561401799, 0.00716272156949283, 0.00932080953459823, 0.0383692985295968, 0.0911719765244643, 0.120107381470464, 0.140568230585610, 0.135467718721927, 0.0966197867722044, 0.0843956132296303, 0.0818845290280932, 0.0805236390863167, 0.0800018936637583, 0.0848459060736138, 0.0863858949811771, 0.0843803502062549, 0.0880836498578280, 0.0862020879633553, 0.0841834656734930, 0.0871664682701736, 0.0878306662014162, 0.0862969389183103, 0.0887998281992880, 0.0892084739538705, 0.0849114138168916, 0.0877417665777031, 0.0852385374807439, 0.0936215692257255, 0.105829809943751, 0.102978085822084, 0.103220093895618, 0.0951927944631837, 0.0806039502622718, 0.0805155888276678, 0.0809348056689987, 0.0783161345662653, 0.0770068040370729, 0.0691933455929340, 0.0679621711334288, 0.0683854735030629, 0.0648774356660004, 0.0639196021173991, 0.0633785877811614, 0.0590289971497271, 0.0606992864507571, 0.0630556751897970, 0.0530104910279283, 0.0421614546103227, 0.0300383726726898, 0.0141856759241208, 0.0101311083168639, 0.00785773338724563, 0.00659442430883826, 0.00754275772122182, 0.00726078552296262, 0.00738033007450220, 0.00717232613571536, 0.00563904071398859, 0.00615370440994190, 0.00602223243826475, 0.00588436278169125, 0.00582251072637905, 0.00532207652086092, 0.00544943297435167, 0.00678478673889323, 0.00712074973238314, 0.00741287168195695, 0.00926549701512866, 0.00816800876741727, 0.00796219306960119, 0.00758928807000518, 0.00548552972363559, 0.00555571466747072, 0.00641406464630618, 0.00643824251970375, 0.00651770653701082, 0.00650858102294930, 0.00646173682699005, 0.00632487381444827, 0.00609263994827336, 0.00618053092350435, 0.00552885923004738, 0.00596998332925765, 0.00795005004623610, 0.0283396431568260, 0.0476971494315289, 0.0584965436088036, 0.0664115981803879, 0.0608185822735035, 0.0465190076223060, 0.0481167741853297, 0.0478670248352230, 0.0476273406972113, 0.0463047238835806, 0.0433497833910290, 0.0414053041455514, 0.0453360935406012, 0.0500957024960124, 0.0492973731011099, 0.0517915874430314, 0.0503292833066278, 0.0465159148894097, 0.0488905325728246, 0.0495518147918107, 0.0467103989748931, 0.0432512082459698, 0.0380867096104660, 0.0328786391466923, 0.0359581589493043, 0.0368855412004844, 0.0396792433108704, 0.0423272529128784, 0.0375108224332395, 0.0370674868641743, 0.0356019733330019, 0.0367461049714562, 0.0378663118264155, 0.0381177557395037, 0.0349264299007977, 0.0309491964312551, 0.0305469885611234, 0.0307811955639023, 0.0308168946821460, 0.0325504010648881, 0.0333666462236040, 0.0315572470836302, 0.0360122747271540, 0.0377745518297263, 0.0344841786436378, 0.0360280599157042, 0.0715974874521254, 0.0676201757192548, 0.0656726492369631, 0.0599599283252966, 0.0177356092913147, 0.0212844176948711, 0.0222978548474339, 0.0226756010598460, 0.0223822192799898, 0.0144718155245629, 0.0115929963697813, 0.0103029025229913, 0.0108469586924703, 0.0127909740720710, 0.0119223145191432, 0.0132515565883848, 0.0124769608123807, 0.0100929398474726, 0.00863218867668654, 0.00644423522659737, 0.00504057393220576, 0.00500833083162252, 0.00588382929343196, 0.00587643649210048, 0.00591131543826651, 0.00605311639713036, 0.00999459627609757, 0.0110612573345423, 0.0133556880974274, 0.0138991860388615, 0.00931295904796510, 0.0108744941742962, 0.00904547884652592, 0.00818936580989969, 0.00784320277618076, 0.00555295026005127, 0.00493249298094057, 0.00515537752129398, 0.00549182625663067, 0.00567517730247609, 0.00853908797739984, 0.0342554995235967, 0.0392855536822836, 0.0446441148144067, 0.0462512812655779, 0.0250684015567530, 0.0231266481496490, 0.0223877143844531, 0.0230605291693447, 0.0228187585989139, 0.0249734688335783, 0.0247031628187290, 0.0242666425274430, 0.0241419402516299, 0.0226407167373544, 0.0220923209027561, 0.0205426589897024, 0.0212275653241607, 0.0211054184523864, 0.0211933242958219, 0.0210640907609298, 0.0207968804243790, 0.0207933380767499, 0.0198855700930132, 0.0200874740989809, 0.0190023650173493, 0.0191426264801894, 0.0185066761986722, 0.0186166275524789, 0.0180352498876615, 0.0173941646843078, 0.0187749804521436, 0.0179551425763704, 0.0196253593628072, 0.0208087255591124, 0.0208089165224644, 0.0205619731620083, 0.0181689287115377, 0.0176674789282066, 0.0163343006685817, 0.0157820874492084, 0.0167358079356805, 0.0148723961493764, 0.0126366874698552, 0.0103636814637576, 0.00788685024024261, 0.00629908206300147, 0.00619696551805616, 0.00632482694815467, 0.00625859811639075, 0.00619720072279854, 0.00577787428260385, 0.00574406759437636, 0.00614194754789129, 0.00633742785809896, 0.00695537519443492, 0.00725840886822271, 0.00767009739150576, 0.00697945170834412, 0.00643589647254383, 0.00641569562023502, 0.00574113999433209, 0.00601681871938609, 0.00580458154460840, 0.00531062799891484, 0.00487755838939604, 0.00528303737994100, 0.00538309986914709, 0.00603450898928422, 0.00639626850074423, 0.00594077405581714, 0.00672675445460743, 0.00630496864364133, 0.00599998591813935, 0.00596387374665486, 0.00675097372875179, 0.00682234590489105, 0.00648217535037890, 0.00647110163725031, 0.00555775836082714, 0.00514061576792148, 0.00535225838257171, 0.00581808425546442, 0.00714279539582233, 0.0851499793352312, 0.158683062307739, 0.209275940403189, 0.245205148311500, 0.213620720326333, 0.141212398295242, 0.112458770256870, 0.0889882300286188, 0.0848786275765423, 0.0837955194536218, 0.0771569921233761, 0.0754782534636963, 0.0743494099804140, 0.0729706722194741, 0.0696821910553346, 0.0679815185164364, 0.0652397346314503, 0.0590282910625890, 0.0573589493732488, 0.0510766809708650, 0.0461268833817451, 0.0458801464990589, 0.0433330527704404, 0.0418835700328801, 0.0428871883555023, 0.0410954637157269, 0.0529636133478384, 0.0583626573593159, 0.0619918530367366, 0.0642527034280186, 0.0578484379301541, 0.0610814384374813, 0.0624781570603903, 0.0608601832144714, 0.0603053019945925, 0.0629325237247239, 0.0614092094987880, 0.0686124332735019, 0.0732060294471822, 0.0163008463452007, 0.0162798479703013, 0.0154622378107612, 0.0178382308098058, 0.0223435880324937, 0.0255563529427240, 0.0339012563044996, 0.0339312262176289, 0.0407752918573472, 0.0404884291675691, 0.0317374487340371, 0.0294864884038602, 0.0171477969810304, 0.0151390472352897, 0.0143414327904965, 0.0135321871457720, 0.0139043911629017, 0.0133150735415394, 0.0135659151628930, 0.0134795387836809, 0.0142244900540968, 0.0153378593667539, 0.0152971078799923, 0.0157401946217957, 0.0160744807578994, 0.0142070286426093, 0.0134944652354751, 0.0145466649634317, 0.0144617341505511, 0.0143516582904138, 0.0148794032508961, 0.0140239671765265, 0.0139403006142212, 0.0143341593232447, 0.0140696896248571, 0.0147394200190594, 0.0137177385410244, 0.0134159203564110, 0.0129614588853853, 0.0143322769292757, 0.0801036204094167, 0.108543614558828, 0.130106775032326, 0.145885068677214, 0.108890217820220, 0.0884895012186343, 0.0798876445217647, 0.0772987198407620, 0.0753687810809802, 0.0732870552104130, 0.0775465255522605, 0.0756996363728218, 0.0749701276534355, 0.0702668086423292, 0.0686419957826826, 0.0713223505261581, 0.0648992634605111, 0.0703016169246426, 0.0733613972215673, 0.0690642435570071, 0.0714125236649920, 0.0677285698352378, 0.0626824716768229, 0.0608548188881651, 0.0592413560926396, 0.0592869635322592, 0.0614717577562122, 0.0648769294557002, 0.0631035350963801, 0.0672163450004952, 0.0677122517531306, 0.0673786737769057, 0.0697056235056867, 0.0652054093570096, 0.0583809113841729, 0.0560483720852252, 0.0562695014175414, 0.0547811761655730, 0.0568402174301196, 0.0584248632876971, 0.0540600388732454, 0.0509282421173781, 0.0399712685278390, 0.0304024820040728, 0.0217739206254892, 0.0176081894341987, 0.0156480048200333, 0.0139010911837707, 0.0143047925232896, 0.0156616210866867, 0.0156745302242947, 0.0165231187244327, 0.0160091891007070, 0.0144785795899658, 0.0147770011225251, 0.0150850414769647, 0.0148272259057887, 0.0153832106739542, 0.0150270974091793, 0.0140909624933810, 0.0140232252916700, 0.0131429436453993, 0.0135401969037634, 0.0158064685622974, 0.0173282479743207, 0.0181310802381517, 0.0195346551176204, 0.0176441515798089, 0.0150860461186139, 0.0142967564029310, 0.0130492772165540, 0.0138351225798775, 0.0131342226354891, 0.0141777149961665, 0.0138750251301228, 0.0146117790502391, 0.0164922708700224, 0.0165832956758735, 0.0167868212388538, 0.0171757804515652, 0.0168387432006903, 0.0157991179996286, 0.0151087518219902, 0.0153019718044034, 0.0206465327944749, 0.0252075977075392, 0.0276006182608514, 0.0307731275900456, 0.0279824299158501, 0.0246987127544953, 0.0237377460002094, 0.0238773579372212, 0.0234812401072193, 0.0248544153569962, 0.0238649821952647, 0.0221396767736376, 0.0194134574589221, 0.0192835262063116, 0.0198520802169463, 0.0192701795066693, 0.0211762994882133, 0.0201377054384042, 0.0192169249159929, 0.0192935789964429, 0.0176999654537485, 0.0167511650012803, 0.0177641499075146, 0.0182280156526867, 0.0191208310884937, 0.0193366320915927, 0.0194656199601723, 0.0182547420207975, 0.0176675201201358, 0.0175905812410168, 0.0166437031663256, 0.0166024949246125, 0.0171441619932358, 0.0159843410617400, 0.0163093423939826, 0.0158161093964234, 0.0157537706530607, 0.0166692894159374, 0.0169091811436743, 0.0167200807716336, 0.0198645577472902, 0.0211947907338282, 0.0205555147771928, 0.0197635717579036, 0.0177389730926438, 0.0169934111007834, 0.0175356316272669, 0.0188825911042989, 0.0183697954672032, 0.0165933850518534, 0.0156070583765532, 0.0161509761942072, 0.0158079329127745, 0.0165127916765883, 0.0165601182593063, 0.0171403289189133, 0.0163509570469802, 0.0171998553405487, 0.0168050268673486, 0.0154015140322878, 0.0156663679863420, 0.0150787844876673, 0.0157226665637345, 0.0170670686879168, 0.0198179165261463, 0.0283503554212784, 0.0284635925644248, 0.0281816196109892, 0.0262771813406847, 0.0171491364628418, 0.0177802575331354, 0.0181219439483967, 0.0175314632399053, 0.0168698966258430, 0.0159526779879262, 0.0156713079066228, 0.0164047041317990, 0.0170761618811063, 0.0170270672600379, 0.0164157237912202, 0.0164168228604591, 0.0162404296530732, 0.0274631831401028, 0.0531926441666224, 0.0587885211129162, 0.0653714493718077, 0.0615023946024469, 0.0386596522323260, 0.0379573256152182, 0.0377126730390901, 0.0389303559344138, 0.0397092985282272, 0.0391909084705579, 0.0374852815980531, 0.0372798610287567, 0.0386864709363920, 0.0353476602896115, 0.0350696538469271, 0.0358100012342120, 0.0327079227871510, 0.0317353951995667, 0.0308337356947174, 0.0296136232208824, 0.0290454893579117, 0.0313967113976419, 0.0322017918590648, 0.0363371661778733, 0.0375165857567721, 0.0377257819613026, 0.0367607940472713, 0.0330847584435895, 0.0317445086661389, 0.0293612430862348, 0.0299146726919516, 0.0286743275854319, 0.0290751376914606, 0.0291659009692455, 0.0284440065432440, 0.0274481750365904, 0.0256928539595888, 0.0261874860256214, 0.0257581908613785, 0.0263796267341124, 0.0272278534432746, 0.0263782405979872, 0.0252993511783642, 0.0259608594060314, 0.0223540365697740, 0.0200793715347222, 0.0189281110785752, 0.0150137131266205, 0.0140085310622895, 0.0150211194434993, 0.0152027440144516, 0.0169271996906177, 0.0203214064502850, 0.0225687895968393, 0.0223785014847781, 0.0217097208985149, 0.0207845553070895, 0.0166429259677593, 0.0169305122512510, 0.0160739471861462, 0.0152167356262149, 0.0161104231736275, 0.0151608068800241, 0.0155625455771193, 0.0165695584101287, 0.0160870748748066, 0.0158752899366142, 0.0163901436673573, 0.0153968354244196, 0.0161316337162997, 0.0170913742585473, 0.0165482684935293, 0.0166380648833479, 0.0153855711329461, 0.0157567295458710, 0.0165415883367361, 0.0162833804243036, 0.0164459648953396, 0.0162050085715171, 0.0157798182169120, 0.0332417756972655, 0.0749578402778551, 0.103915855313560, 0.126622942790034, 0.127192242211086, 0.0944135840739770, 0.0850962974501242, 0.0703496898337760, 0.0676049193278333, 0.0715883155516822, 0.0607568245555656, 0.0611187352010225, 0.0610711274514154, 0.0586769940475740, 0.0633005996804910, 0.0625513544201393, 0.0611814223400883, 0.0613833247540842, 0.0593870670805487, 0.0583911419253793, 0.0589680672450886, 0.0563007707247083, 0.0550102831632239, 0.0522716600657009, 0.0459243494479616, 0.0445839738660436, 0.0437886600987393, 0.0418869213399396, 0.0443573139456488, 0.0442160305309158, 0.0431060956790412, 0.0431587952354398, 0.0421803189192315, 0.0416450711991009, 0.0421073924702982, 0.0432642068280096, 0.0455106523950545, 0.0436297465919447, 0.0460620085344673, 0.0510708397685387, 0.0547068149196942, 0.0578101103737390, 0.0583708017577948, 0.0567331894497875, 0.0548256181053722, 0.0500547800867890, 0.0491099269929481, 0.0400597674489723, 0.0297083796284414, 0.0261339194868912, 0.0168743892515814, 0.0133963670161630, 0.0126512864032625, 0.0147647911837675, 0.0131406661163183, 0.0132126778483140, 0.0135066867293369, 0.0112267979919284, 0.0110766016616008, 0.0129873447391404, 0.0143769215134091, 0.0144702660426389, 0.0142090143867644, 0.0131933422490521, 0.0114995096556843, 0.0118590301582531, 0.0118847288301841, 0.0129165667547841, 0.0121306455652086, 0.0114290706513468, 0.0110827321004459, 0.0100602534892201, 0.0118432474037288, 0.0119483013862647, 0.0130657408698095, 0.0140821253296357, 0.0123851039990351, 0.0123783780516288, 0.0114086513744072, 0.0100931841195641, 0.0102238103772864, 0.00992427205102334, 0.0102149896862994, 0.0104473579988524, 0.0108711178808891, 0.0118417764914683, 0.0166668392927322, 0.0280934753867561, 0.0314939112536379, 0.0333465689819113, 0.0316962302114042, 0.0229956131644826, 0.0213547183825206, 0.0213708948410574, 0.0209736846636930, 0.0214289857673712, 0.0216961819302575, 0.0213845075378870, 0.0223779296067097, 0.0215829094716652, 0.0218682572595090, 0.0217171433158768, 0.0206973418416256, 0.0207390230757664, 0.0208667423467155, 0.0200497986153654, 0.0197163753755006, 0.0194656801247071, 0.0197876492398813, 0.0214032857230025, 0.0211932355382950, 0.0203042025417174, 0.0194805198817997, 0.0171763867856152, 0.0172460585412474, 0.0178625488358204, 0.0177611095847891, 0.0177690647901966, 0.0177386274320826, 0.0165926775714724, 0.0167782009696161, 0.0176219163013794, 0.0173210612065772, 0.0180166103235803, 0.0176041344693547, 0.0259539765661067, 0.0329917365924959, 0.0332064889547705, 0.0339091136965377, 0.0259850983315557, 0.0181511440952462, 0.0167191254743615, 0.0163119351862696, 0.0160264763883171, 0.0150623149663972, 0.0156716613029175, 0.0154782318043187, 0.0148455697055800, 0.0151429101731930, 0.0158552785592210, 0.0161413990950047, 0.0168748251047509, 0.0158695425307815, 0.0153478823747978, 0.0149094323062392, 0.0154470168988211, 0.0180645395980716, 0.0170489792418034, 0.0167228235618271, 0.0151221787091710, 0.0126741122129692, 0.0125153477204135, 0.0121197715719397, 0.0126216511067716, 0.0136114727453061, 0.0132973893976371, 0.0141443781754982, 0.0143808667056250, 0.0154923233011778, 0.0161258544687995, 0.0161854608098017, 0.0164586763988477, 0.0149479985912610, 0.0140247309814631, 0.0144743592816298, 0.0155233665983896, 0.0161972231334802, 0.0160999607007199, 0.0155605260548210, 0.0151060960616776, 0.0442872987282153, 0.0761556696335484, 0.0975340631770486, 0.110960718243187, 0.0967776613480349, 0.0778250233328702, 0.0730424758316345, 0.0671776774573449, 0.0673807275690818, 0.0630144767529511, 0.0616963656191356, 0.0601933496784094, 0.0611736960029027, 0.0606298360131005, 0.0573075631537714, 0.0575810519129983, 0.0535063845028854, 0.0538048715564090, 0.0507810404932681, 0.0454564592085889, 0.0464402952067880, 0.0447626699552832, 0.0453800610800050, 0.0461383479251575, 0.0468163498147843, 0.0449037536216739, 0.0473770784182799, 0.0515229248924780, 0.0504378186899019, 0.0498561695869924, 0.0501280417812756, 0.0509006140260992, 0.0511229760967459, 0.0533775680113951, 0.0549360455321201, 0.0514604535520326, 0.0505315421879076, 0.0499782657607086, 0.0438527402664970, 0.0210370599379978, 0.0216406206880470, 0.0220399572605292, 0.0206997836416512, 0.0163955396113424, 0.0174200120637861, 0.0194369265178179, 0.0214480895432343, 0.0276604632501541, 0.0263333472131163, 0.0220572616282223, 0.0198320305160571, 0.0129949809786146, 0.0123106729564493, 0.0139179386641384, 0.0149972027544619, 0.0147596871831683, 0.0170981849260168, 0.0178082178411169, 0.0182942829565458, 0.0201428208536862, 0.0183497716555320, 0.0166029593567954, 0.0142735683129337, 0.0125975660749549, 0.0131651087815275, 0.0122593148422893, 0.0176604971217802, 0.0175670673726525, 0.0165665031998299, 0.0190121155669582, 0.0157468634433029, 0.0173764101140864, 0.0175312930511489, 0.0154110409169058, 0.0142993288295663, 0.0139956043711681, 0.0165763395377379, 0.0187570542380137, 0.0202087088848592, 0.0727390312709242, 0.118950550336557, 0.140253211327930, 0.162278519768650, 0.133686572405493, 0.102147986193661, 0.0916943513671518, 0.0831290737702005, 0.0764287003024528, 0.0719341001715257, 0.0742215694235036, 0.0745395652100016, 0.0766594762551336, 0.0752744460597021, 0.0715913989869647, 0.0724349707534070, 0.0720811675962629, 0.0738789530184359, 0.0739682416879707, 0.0759010883665401, 0.0731764903254341, 0.0699201878325636, 0.0681294560398351, 0.0657147717819597, 0.0678672968203619, 0.0695541757953689, 0.0709200465829226, 0.0716352309283809, 0.0741447788997379, 0.0715979200087591, 0.0687067370201827, 0.0648639014815331, 0.0673009268801854, 0.0678637397930075, 0.0681930783188542, 0.0678770313311051, 0.0615265590903885, 0.0582455140081140, 0.0546282485037832, 0.0513678586625564, 0.0469740610616880, 0.0417592183784872, 0.0363928480450880, 0.0316883957068463, 0.0280990167239746, 0.0264837087439358, 0.0237427513621360, 0.0204867752919320, 0.0204146738015005, 0.0169793760745949, 0.0152284124277941, 0.0163545714807751, 0.0150410727773848, 0.0166922850089347, 0.0149166765440335, 0.0146229963030194, 0.0139462499138639, 0.0132225582795266, 0.0136628265205771, 0.0148059438885517, 0.0158582201166949, 0.0161262177608730, 0.0144612974868580, 0.0123800609792037, 0.0120984946885233, 0.0115691300856163, 0.0110700189339015, 0.0123321182121387, 0.0122795810272781, 0.0117745513546446, 0.0142305830850018, 0.0139393445307454, 0.0131349902612124, 0.0134431773351499, 0.0126461081449002, 0.0122938880772736, 0.0129513572433748, 0.0132555966035150, 0.0115334026536950, 0.0110898831978111, 0.0112324101744371, 0.0125707704025826, 0.0157693770374355, 0.0621851382514239, 0.136024052345695, 0.193572085097706, 0.242132771614919, 0.255760411524912, 0.218072208280091, 0.200469692249369, 0.185952557235583, 0.185570387602076, 0.167282676190604, 0.150029817839727, 0.129918275557228, 0.105052941441384, 0.0889364619186039, 0.0960409554774544, 0.106275013125962, 0.112224437546388, 0.116895592660090, 0.123440355314203, 0.134464710390332, 0.147797209640442, 0.146818756591794, 0.133520392510714, 0.117157917125425, 0.0990500050970392, 0.0952152433368954, 0.0939961368222338, 0.0908491139338178, 0.0887327327460558, 0.0854877355914673, 0.0764117756444354, 0.0801124787806754, 0.0819124766570781, 0.0834399370389639, 0.0855257029542992, 0.0781235988899243, 0.0690967219207243, 0.0631770236177526, 0.0661694663629644, 0.0574642136076006, 0.0569342593454784, 0.0503392897218419, 0.0344603886120335, 0.0259536863637538, 0.0170276220141887, 0.0154406550181367, 0.0147326730617664, 0.0156081312052645, 0.0165342135369215, 0.0165375739358615, 0.0168147057677870, 0.0165430782795459, 0.0171708009895352, 0.0154723738673217, 0.0196130042171893, 0.0210080128980023, 0.0225914913078719, 0.0256001093902626, 0.0220495443509450, 0.0183976015324885, 0.0182021682075156, 0.0185813583297521, 0.0202934081171891, 0.0197071655504161, 0.0181866535996435, 0.0156473971067571, 0.0142221888749993, 0.0155361424587406, 0.0185812376906826, 0.0217717506217650, 0.0220707700188002, 0.0204610155451216, 0.0178350025914810, 0.0149973765206300, 0.0163592037821066, 0.0174281229299179, 0.0175280278113557, 0.0178487705040328, 0.0180197392127895, 0.0179495448462798, 0.0184001203608171, 0.0191752562524318, 0.0197036315323164, 0.0325587240593005, 0.0818866595021346, 0.0909981291281290, 0.0975311588658347, 0.0905054046567293, 0.0450203019393973, 0.0397722383164851, 0.0376569717114556, 0.0408487402869183, 0.0428327993428419, 0.0427237440912978, 0.0410983915227736, 0.0387855353910218, 0.0368756938659300, 0.0344985534080900, 0.0346277854816314, 0.0358243579077221, 0.0352043229697138, 0.0336797659794907, 0.0346754717711277, 0.0348354937675505, 0.0359943182351441, 0.0399488562034829, 0.0418376979629977, 0.0441500013878831, 0.0454241759415995, 0.0427617263814855, 0.0431856722006964, 0.0425072826044784, 0.0420257677948037, 0.0433326213618026, 0.0406033379046754, 0.0386007947916667, 0.0361395113248529, 0.0339583374042662, 0.0322115672545654, 0.0322222336361520, 0.0298911717699421, 0.0278104784987729, 0.0276888747521691, 0.0235710912258326, 0.0218102050885738, 0.0237188684951764, 0.0219300245927206, 0.0237302176366568, 0.0240405695607397, 0.0211544128943025, 0.0207155754861912, 0.0185746178967100, 0.0189696055469126, 0.0174947754830118, 0.0158601354368620, 0.0155024847260881, 0.0160241443753209, 0.0174648986096261, 0.0180713632494220, 0.0187478365671981, 0.0188905908149389, 0.0170363156618896, 0.0169111415403254, 0.0161989703502865, 0.0137785255673472, 0.0142988763705493, 0.0140816017966847, 0.0130102214609202, 0.0125708469108201, 0.0115560598996008, 0.0124018174880132, 0.0131148122811897, 0.0149135515802929, 0.0171499648482907, 0.0188152483945943, 0.0212128411231789, 0.0197102736804506, 0.0200808475934515, 0.0179113862267532, 0.0161238874576754, 0.0173721771413029, 0.0158289781463254, 0.0148464386190822, 0.0170916229764678, 0.0355451992157208, 0.0495536730738226, 0.0608908624367084, 0.0658252009903809, 0.0536640142325278, 0.0442098225443247, 0.0439520105591239, 0.0433332043131475, 0.0439203439309130, 0.0424085009262274, 0.0390209193757004, 0.0370562882062377, 0.0369984160887101, 0.0366027226977013, 0.0353092684698708, 0.0350890740658450, 0.0350164214139351, 0.0325842605043441, 0.0322832993025654, 0.0312980353574649, 0.0303944315403202, 0.0343821622972429, 0.0390545258880999, 0.0374634904327991, 0.0392482344464909, 0.0344750186843901, 0.0296349100568842, 0.0328084900322433, 0.0308066456485639, 0.0338688922298020, 0.0332523128668971, 0.0309883708255037, 0.0300176915923648, 0.0308601667790543, 0.0285683055984473, 0.0293995721919928, 0.0280483413934374, 0.0270544934352457, 0.0270590153513952, 0.0313746317621058, 0.0344674074504253, 0.0339230400517061, 0.0360824202540797, 0.0334205149600068, 0.0330092169696093, 0.0307364791230205, 0.0368807200885576, 0.0389997442348725, 0.0356564328374300, 0.0351720586868913, 0.0273426700219462, 0.0220241511915537, 0.0227166398448960, 0.0212213239479693, 0.0201551110915266, 0.0206904009879265, 0.0192795044623033, 0.0208652104600080, 0.0201294977965590, 0.0194085730674788, 0.0202854298813674, 0.0192236113986120, 0.0185035132743873, 0.0170678972590163, 0.0153429738125245, 0.0166667272181681, 0.0174734999884604, 0.0200891087428938, 0.0220777219230086, 0.0213277273813902, 0.0200351763498359, 0.0195772700020217, 0.0178701265461702, 0.0175185608360084, 0.0184378994698688, 0.0191578836802552, 0.0200113842727088, 0.0225519499317444, 0.0234199100274709, 0.0244866561430436, 0.0218894545978302, 0.0207695922685514, 0.0195973776047921, 0.0197241397157011, 0.0203560632472185, 0.0194289864451300, 0.0202272282976674, 0.0667320548876334, 0.0855228016301622, 0.0977340945876978, 0.106713415556272, 0.0680560033149047, 0.0567137808432437, 0.0520317442450459, 0.0512615886652872, 0.0487296571508845, 0.0459779838841090, 0.0447779981238197, 0.0437105861819693, 0.0410775579983962, 0.0390627118110301, 0.0349930074509090, 0.0341402995601490, 0.0343375665219454, 0.0356096420870502, 0.0352624191765186, 0.0348284735235586, 0.0362255720539892, 0.0369902295161952, 0.0386031618569148, 0.0385815904064268, 0.0369975490372824, 0.0364183070507249, 0.0376113813000049, 0.0339890354955589, 0.0345403677718956, 0.0358265079798637, 0.0358200460025724, 0.0423680669153840, 0.0447642745270349, 0.0479839539069037, 0.0480138735699354, 0.0475028159858350, 0.0456925033247712, 0.0426535384554146, 0.0416096888565702, 0.0393084946659282, 0.0385947133233868, 0.0355879026616968, 0.0308762797792724, 0.0269973660347874, 0.0228304207287013, 0.0199015988584159, 0.0197365013796468, 0.0181268926131162, 0.0160694740830911, 0.0184562844900823, 0.0177667450601505, 0.0176762984307143, 0.0200514647123876, 0.0208610618360591, 0.0200135242473167, 0.0217756658235835, 0.0213779665701142, 0.0174994531248574, 0.0176160246101304, 0.0169707673742516, 0.0172990692748303, 0.0185717782075278, 0.0167700718018476, 0.0154844375866668, 0.0155753272213682, 0.0146456039239625, 0.0164533323073838, 0.0174309099961134, 0.0167953093414731, 0.0163257454104551, 0.0149613860712178, 0.0160262540374226, 0.0160377743135086, 0.0153360337489530, 0.0154848705465076, 0.0124222958531857, 0.0129411397410751, 0.0132993044381705, 0.0138385104982438, 0.0161320793514206, 0.0168199990352578, 0.0170647105734908, 0.0181348220650513, 0.0457737983282862, 0.0532483192368060, 0.0555649077512803, 0.0581393840870787, 0.0346958944074332, 0.0307099861707907, 0.0313560492524495, 0.0311542432265478, 0.0360652182890711, 0.0355978333758382, 0.0323592049140212, 0.0320340846257466, 0.0290098164974167, 0.0296765735015728, 0.0335015400899883, 0.0364645461211981, 0.0351802049207158, 0.0330603299678673, 0.0308278526796246, 0.0280889105024277, 0.0269914555447428, 0.0262810424769887, 0.0253934126823025, 0.0250410386890364, 0.0254867381091364, 0.0258173346467519, 0.0244352657529665, 0.0245004141038426, 0.0233712278963383, 0.0217620974470818, 0.0215896041125881, 0.0220504359892033, 0.0208276632397538, 0.0207818570753454, 0.0202905129938989, 0.0187606184524886, 0.0205610214323217, 0.0219929086996025, 0.0221311084293437, 7.30137317423605e-05, 4.90347302418747e-05, 4.90347302418747e-05, 5.28719871575432e-05, 5.28719871575432e-05, 5.28719871575432e-05, 5.28719871575432e-05, 6.14701255601060e-05, 6.14701255601060e-05, 6.14701255601060e-05, 6.14701255601060e-05, 4.42579482429172e-05, 5.12932694300959e-05, 5.12932694300959e-05, 5.97764871805046e-05, 7.29878509410624e-05, 7.29878509410624e-05, 7.29878509410624e-05, 7.29878509410624e-05, 5.05997550334005e-05, 6.52112305543943e-05, 6.52112305543943e-05, 8.50383411196469e-05, 8.50383411196469e-05, 8.50383411196469e-05, 8.50383411196469e-05, 8.50383411196469e-05, 6.55397215860988e-05, 6.55397215860988e-05, 6.55397215860988e-05, 6.55397215860988e-05, 5.24621914623393e-05, 5.24621914623393e-05, 5.24621914623393e-05, 5.24621914623393e-05, 6.09287804410532e-05, 6.09287804410532e-05, 6.52618359474072e-05, 0.000121808593317628, 0.000122748068511114, 0.00427980623328336, 0.00427980623328336, 0.00427980623328336, 0.00427980623328336, 0.00245460868799915, 0.00243280896806154, 0.00118020341985283, 0.000864545834854669, 0.000864545834854669, 0.000864545834854669, 0.000850072059631357, 0.000789443632319084, 0.000789443632319084, 0.000789443632319084, 0.000789443632319084, 0.000727138089394349, 0.000727138089394349, 0.000727138089394349, 0.000727138089394349, 0.000683502670004829, 0.00100588038922863, 0.00114920621698339, 0.00114920621698339, 0.00114920621698339, 0.00114920621698339, 0.000932518227395914, 0.000932518227395914, 0.000932518227395914, 0.000932518227395914, 0.000769123142625678, 0.000769123142625678, 0.000737937823652702, 0.000840739289806780, 0.000840739289806780, 0.000840739289806780, 0.000840739289806780, 0.000681584801856310, 0.000611537153549917, 0.000668064499555970, 0.000668064499555970, 0.000924960988354606, 0.000924960988354606, 0.000924960988354606, 0.000924960988354606, 0.000464936940036472, 0.000292670287414935, 0.000146041669460421, 0.000146041669460421, 6.20090720481660e-05, 6.20090720481660e-05, 6.20090720481660e-05, 6.91998534674392e-05, 6.91998534674392e-05, 8.53062690425246e-05, 8.53062690425246e-05, 8.53062690425246e-05, 0.000104822381906282, 0.000104822381906282, 0.000104822381906282, 0.000104822381906282, 8.44246398494703e-05, 8.44246398494703e-05, 8.44246398494703e-05, 0.000282631539535781, 0.000282631539535781, 0.000282631539535781, 0.000282631539535781, 6.98286830955516e-05, 6.98286830955516e-05, 6.98286830955516e-05, 6.98286830955516e-05, 6.98286830955516e-05, 6.73146569032099e-05, 6.16264456457073e-05, 6.16264456457073e-05, 6.02225160616497e-05, 4.48047349275177e-05, 4.07002683714483e-05, 6.01371425230415e-05, 8.15186622441395e-05, 8.15186622441395e-05, 8.15186622441395e-05, 8.15186622441395e-05, 0.000217940908465858, 0.000497553130957789, 0.000497553130957789, 0.000497553130957789, 0.000497553130957789, 0.000397576087146032, 0.000397576087146032, 0.000397576087146032, 0.000280749248139389, 0.000280749248139389, 0.000230194920275072, 0.000175928986886686, 0.000175928986886686, 0.000175928986886686, 0.000171594430486889, 0.000183626904535241, 0.000186993668789316, 0.000284854357711945, 0.000284854357711945, 0.000340929671553125, 0.000365227224846768, 0.000365227224846768, 0.000365227224846768, 0.000365227224846768, 0.000346218915717498, 0.000346218915717498, 0.000308996270562589, 0.000250210532210646, 0.000245118705087012, 0.000300553098915516, 0.000300553098915516, 0.000300553098915516, 0.000300553098915516, 0.000330687872453305, 0.000330687872453305, 0.000330687872453305, 0.000330687872453305, 0.000266196830242473, 0.000222025527492765, 0.000222025527492765, 0.000264693199756551, 0.000264693199756551, 0.000264693199756551, 0.000264693199756551, 0.000264693199756551, 0.000128745726136499, 0.000118612490162545, 0.000245410021510612, 0.000245410021510612, 0.000245410021510612, 0.000245410021510612, 7.24802742759647e-05, 0.000110850676380674, 0.000110850676380674, 0.000110850676380674, 0.000110850676380674, 0.000110850676380674, 0.000127885586601022, 0.000165223947243807, 0.000165223947243807, 0.000165223947243807, 0.000165223947243807, 6.15380216876603e-05, 6.15380216876603e-05, 6.15380216876603e-05, 4.27057394954044e-05, 0.000114424090743453, 0.000114424090743453, 0.000114424090743453, 0.000114424090743453, 5.02872533508175e-05, 6.01989631413289e-05, 6.01989631413289e-05, 6.01989631413289e-05, 7.36948465178317e-05, 7.36948465178317e-05, 7.36948465178317e-05, 7.36948465178317e-05, 4.95208994110635e-05, 9.32086556200361e-05, 9.32086556200361e-05, 9.32086556200361e-05, 0.000334186291262323, 0.00328795937090858, 0.00328795937090858, 0.00328795937090858, 0.00328795937090858, 0.00328795937090858, 0.00149070083350508, 0.00149070083350508, 0.000850800976870008, 0.000850354464928521, 0.000737199825646858, 0.00145770908341204, 0.00145770908341204, 0.00145770908341204, 0.00145770908341204, 0.00145770908341204, 0.000837681644957794, 0.00111565507468488, 0.00111565507468488, 0.00111565507468488, 0.00111565507468488, 0.000909412760122060, 0.000852052849172705, 0.000852052849172705, 0.000852052849172705, 0.00116326740609568, 0.00116326740609568, 0.00116326740609568, 0.00116326740609568, 0.00116326740609568, 0.00108106741272457, 0.00121640035647095, 0.00121640035647095, 0.00121640035647095, 0.00121640035647095, 0.000824335074898656, 0.000774069188582235, 0.00136477093541956, 0.00136477093541956, 0.00136477093541956, 0.00136477093541956, 0.000877124119903134, 0.00103835778606308, 0.00103835778606308, 0.00103835778606308, 0.00103835778606308, 0.000587458518879477, 0.000587458518879477, 0.000132079341813650, 0.000132079341813650, 7.63511675083844e-05, 0.000104466689956993, 0.000104466689956993, 0.000104466689956993, 0.000104466689956993, 7.05736316118699e-05, 8.41832726957796e-05, 8.41832726957796e-05, 8.41832726957796e-05, 8.41832726957796e-05, 6.41310892654153e-05, 8.81436025065474e-05, 0.000111392690031774, 0.000111392690031774, 0.000111392690031774, 0.000111392690031774, 7.98191197208250e-05, 7.98191197208250e-05, 7.98191197208250e-05, 6.78632685392747e-05, 6.78632685392747e-05, 8.86534353169233e-05, 8.86534353169233e-05, 8.86534353169233e-05, 8.86534353169233e-05, 6.28208221129136e-05, 6.08976804388908e-05, 6.08976804388908e-05, 6.08976804388908e-05, 6.08976804388908e-05, 6.04470797423656e-05, 0.000104689274429272, 0.00162135071257191, 0.00162135071257191, 0.00162135071257191, 0.00162135071257191, 0.00162135071257191, 0.000557453471817988, 0.000632398328065961, 0.000632398328065961, 0.000632398328065961, 0.000632398328065961, 0.000531295221057051, 0.000520482097766929, 0.000927447161976949, 0.000927447161976949, 0.000927447161976949, 0.000927447161976949, 0.000722677376120902, 0.000713422723553288, 0.000713422723553288, 0.000598209653485860, 0.000598209653485860, 0.000598209653485860, 0.000598209653485860, 0.000490287263396731, 0.000906913916577614, 0.000906913916577614, 0.000906913916577614, 0.000906913916577614, 0.000453269262448906, 0.000453269262448906, 0.000403357634708560, 0.000577174637175385, 0.000577174637175385, 0.000577174637175385, 0.000577174637175385, 0.000441995622595649, 0.000391191488433613, 0.000625112630033606, 0.000625112630033606, 0.000625112630033606, 0.000625112630033606, 0.000412556728562784, 0.000552376156669853, 0.000552376156669853, 0.000552376156669853, 0.000552376156669853, 0.00133103614772420, 0.00133103614772420, 0.00133103614772420, 0.00133103614772420, 0.000384856150550505, 0.000445198117178093, 0.000445198117178093, 0.000445198117178093, 0.000445198117178093, 0.000415179286906586, 0.000198296847932190, 0.000198296847932190, 0.000198296847932190, 0.000198296847932190, 0.000135288710130497, 0.000150573266305789, 0.000150573266305789, 0.000150573266305789, 0.000150573266305789, 0.000113533200064496, 6.65636220392927e-05, 6.65636220392927e-05, 7.96853387109673e-05, 7.96853387109673e-05, 7.96853387109673e-05, 7.96853387109673e-05, 0.000318417784252124, 0.000318417784252124, 0.000318417784252124, 0.000318417784252124, 0.000219960459438884, 0.000219960459438884, 0.000114676230990547, 0.000114676230990547, 0.000114676230990547, 8.02966520575347e-05, 6.72358761687267e-05, 6.72358761687267e-05, 6.72358761687267e-05, 6.72358761687267e-05, 0.000201107793112249, 0.000969498359976077, 0.000969498359976077, 0.000969498359976077, 0.000969498359976077, 0.000450898957657746, 0.000258518099071467, 0.000258518099071467, 0.000271578763362272, 0.000271578763362272, 0.000294411260529808, 0.000294411260529808, 0.000294411260529808, 0.000294411260529808, 0.000289927378455010, 0.000304365874782622, 0.000304365874782622, 0.000365820681436856, 0.000365820681436856, 0.000365820681436856, 0.000365820681436856, 0.000215376698875019, 0.000215376698875019, 0.000215376698875019, 0.000215376698875019, 0.000211584027473232, 0.000245662816133321, 0.000245662816133321, 0.000245662816133321, 0.000245662816133321, 0.000200150079933571, 0.000366762563101909, 0.000366762563101909, 0.000366762563101909, 0.000366762563101909, 0.000366762563101909, 0.000261027528955160, 0.000255965834410799, 0.000302980694934148, 0.000302980694934148, 0.000302980694934148, 0.000302980694934148, 0.000169443768127939, 0.000169443768127939, 0.000169443768127939, 0.000169443768127939, 0.000169443768127939, 6.66527767014877e-05, 6.66527767014877e-05, 7.24592214754165e-05, 7.24592214754165e-05, 7.24592214754165e-05, 7.24592214754165e-05, 7.24592214754165e-05, 7.16965761244077e-05, 7.53569220362238e-05, 7.53569220362238e-05, 0.000109678244636350, 0.000109678244636350, 0.000109678244636350, 0.000109678244636350, 8.91171841706560e-05, 8.91171841706560e-05, 8.91171841706560e-05, 5.71640658940027e-05, 5.71640658940027e-05, 4.94446818816834e-05, 4.94446818816834e-05, 5.83281386045832e-05, 5.83281386045832e-05, 5.83281386045832e-05, 7.08442359313821e-05, 7.08442359313821e-05, 7.08442359313821e-05, 7.08442359313821e-05, 8.16474614849418e-05, 8.16474614849418e-05, 8.16474614849418e-05, 8.16474614849418e-05, 5.74742820613721e-05, 5.74742820613721e-05, 5.74742820613721e-05, 7.79867452988374e-05, 9.67407059688181e-05, 0.00426301669312802, 0.00426301669312802, 0.00426301669312802, 0.00426301669312802, 0.00426301669312802, 0.00202586997130732, 0.00202586997130732, 0.00129201492193663, 0.00129201492193663, 0.00129201492193663, 0.00116312738200818, 0.00116312738200818, 0.000780831931866217, 0.000780831931866217, 0.000780831931866217, 0.000780831931866217, 0.00104121658984362, 0.00104121658984362, 0.00104121658984362, 0.00104121658984362, 0.000498437316025155, 0.000531536442516754, 0.000531536442516754, 0.000531536442516754, 0.000531536442516754, 0.000472279683265865, 0.000794055654710910, 0.000794055654710910, 0.000849446035673414, 0.000849446035673414, 0.000849446035673414, 0.000849446035673414, 0.000860226323811895, 0.000860226323811895, 0.000860226323811895, 0.000860226323811895, 0.000632557771459157, 0.00116759553264373, 0.00116759553264373, 0.000179950732031973, 0.000179950732031973, 0.000179950732031973, 0.000312437910760736, 0.000312437910760736, 0.000312437910760736, 0.000377962807265813, 0.000377962807265813, 0.000399298272538823, 0.000399298272538823, 0.000399298272538823, 0.000399298272538823, 0.000201341858706957, 0.000201341858706957, 0.000173663192343526, 0.000147666076951131, 0.000147666076951131, 0.000147666076951131, 0.000147666076951131, 0.000134569665538064, 0.000134569665538064, 0.000222605531071378, 0.000222605531071378, 0.000222605531071378, 0.000222605531071378, 0.000124995236862399, 0.000124995236862399, 0.000189753691882651, 0.000189753691882651, 0.000189753691882651, 0.000189753691882651, 0.000165496474974009, 0.000207114990180573, 0.000207114990180573, 0.000207114990180573, 0.000207114990180573, 0.000141873962023185, 0.000141873962023185, 0.000141873962023185, 0.000190664123265682, 0.00411492816021126, 0.00411492816021126, 0.00411492816021126, 0.00411492816021126, 0.00411492816021126, 0.000903355919681156, 0.000903355919681156, 0.000903355919681156, 0.000710901343537083, 0.000703333411825990, 0.000703333411825990, 0.000703333411825990, 0.000730698209966227, 0.000730698209966227, 0.000730698209966227, 0.000730698209966227, 0.000589846015049864, 0.000967338233888161, 0.000967338233888161, 0.000967338233888161, 0.000967338233888161, 0.000756397286517923, 0.000756397286517923, 0.000756397286517923, 0.000607029639436545, 0.000706600814329497, 0.000706600814329497, 0.000706600814329497, 0.000734376973430117, 0.000734376973430117, 0.000769646457687476, 0.000805285436967601, 0.000805285436967601, 0.000805285436967601, 0.000805285436967601, 0.000655931904700269, 0.000655931904700269, 0.000655931904700269, 0.000777528752909538, 0.000777528752909538, 0.000777528752909538, 0.000777528752909538, 0.000678824635928210, 0.000487860034322200, 0.000487860034322200, 0.000264554619593311, 0.000235029019690648, 0.000144176577690395, 0.000154566904366084, 0.000154566904366084, 0.000154566904366084, 0.000190119483142043, 0.000190119483142043, 0.000190119483142043, 0.000190637769798223, 0.000190637769798223, 0.000190637769798223, 0.000190637769798223, 0.000163703772964321, 0.000155105829536118, 0.000155105829536118, 0.000139411337718129, 0.000139411337718129, 0.000254553898991008, 0.000254553898991008, 0.000254553898991008, 0.000254553898991008, 0.000254553898991008, 0.000229412276622650, 0.000229412276622650, 0.000134779225819952, 0.000134779225819952, 0.000134779225819952, 0.000153742168647943, 0.000153742168647943, 0.000153742168647943, 0.000179194795083237, 0.000179194795083237, 0.000179194795083237, 0.000246995013432703, 0.000246995013432703, 0.000246995013432703, 0.000246995013432703, 0.000241485063006290, 0.000411844754283872, 0.000411844754283872, 0.000411844754283872, 0.000411844754283872, 0.000316265580637082, 0.000316265580637082, 0.000316265580637082, 0.000381456402444216, 0.000381456402444216, 0.000381456402444216, 0.000381456402444216, 0.000223885127303243, 0.000214605936980097, 0.000189101615188577, 0.000198915467597317, 0.000198915467597317, 0.000198915467597317, 0.000232356390070814, 0.000232356390070814, 0.000232356390070814, 0.000232356390070814, 0.000212763460506315, 0.000212763460506315, 0.000188995564778841, 0.000188995564778841, 0.000176655835828347, 0.000177450800427811, 0.000194757025629289, 0.000194757025629289, 0.000194757025629289, 0.000194757025629289, 0.000172188380582756, 0.000172188380582756, 0.000172188380582756, 0.000172188380582756, 0.000167414515900435, 0.000167414515900435, 0.000172164034272494, 0.000215124592315863, 0.000298714571846356, 0.000299889225869467, 0.000299889225869467, 0.000299889225869467, 0.000299889225869467, 0.000207924486091891, 0.000207924486091891, 0.000222866695962800, 0.000222866695962800, 0.000222866695962800, 0.000222866695962800, 0.000193378557537484, 0.000191550725785004, 0.000190906452679125, 0.000190906452679125, 0.000190906452679125, 0.000176011699564980, 0.000172311091397730, 0.000172311091397730, 0.000172311091397730, 0.000168494198395729, 0.000168494198395729, 0.000155719885837478, 0.000181407265967632, 0.000181407265967632, 0.000278438635574274, 0.000394567373637359, 0.000394567373637359, 0.000394567373637359, 0.000394567373637359, 0.000203914913550783, 0.000253493111458051, 0.000283203609131430, 0.000283203609131430, 0.000283203609131430, 0.000283203609131430, 0.000223086561496656, 0.000223086561496656, 0.000223086561496656, 0.000223086561496656, 0.000143871008233335, 0.000240639032389777, 0.000240639032389777, 0.000459385885644516, 0.00125474310272239, 0.00125474310272239, 0.00125474310272239, 0.00125474310272239, 0.000460215657866143, 0.000457330766173461, 0.000390637692801936, 0.000390637692801936, 0.000548984836585154, 0.000548984836585154, 0.000548984836585154, 0.000548984836585154, 0.000548984836585154, 0.000447006517924688, 0.000447006517924688, 0.000412216792092998, 0.000412216792092998, 0.000412216792092998, 0.000328266576595535, 0.000326419568170771, 0.000350731138422020, 0.000350731138422020, 0.000350731138422020, 0.000366543311933467, 0.000366543311933467, 0.000366543311933467, 0.000366543311933467, 0.000362496831296858, 0.000291682285498153, 0.000291682285498153, 0.000325867440128771, 0.000325867440128771, 0.000325867440128771, 0.000325867440128771, 0.000301654904221513, 0.000256872857993912, 0.000251826391255957, 0.000326386123379635, 0.000326386123379635, 0.000326386123379635, 0.000326386123379635, 0.000222030103807726, 0.000207522783459505, 0.000244551854870274, 0.000244551854870274, 0.000244551854870274, 0.000244551854870274, 0.000156047248641126, 0.000181341623537849, 0.000212242670572077, 0.000212242670572077, 0.000212242670572077, 0.000212242670572077, 0.000256947402188169, 0.000256947402188169, 0.000256947402188169, 0.000256947402188169, 0.000190869621838547, 0.000190869621838547, 0.000200208282950427, 0.000200208282950427, 0.000200208282950427, 0.000200208282950427, 0.000186112411240990, 0.000186112411240990, 0.000172378929058456, 0.000172378929058456, 0.000172378929058456, 0.000176216580116642, 0.000176216580116642, 0.000176216580116642, 0.000176216580116642, 0.000172060782326725, 0.000172060782326725, 0.000172060782326725, 0.000172060782326725, 0.000168347231690554, 0.000168347231690554, 0.000282802775812498, 0.000282802775812498, 0.00132723917638440, 0.00216946795098429, 0.00216946795098429, 0.00216946795098429, 0.00216946795098429, 0.00152152681312374, 0.00179300755925591, 0.00179300755925591, 0.00179300755925591, 0.00179300755925591, 0.000739415039872256, 0.000739415039872256, 0.000739415039872256, 0.000730999048114423, 0.000850679178365674, 0.000850679178365674, 0.000850679178365674, 0.000850679178365674, 0.000750940423884733, 0.000750940423884733, 0.000740737364445533, 0.000737064037517107, 0.000745461472025057, 0.000745461472025057, 0.000745461472025057, 0.000745461472025057, 0.000554396582655522, 0.000459978936768072, 0.000517110801371130, 0.000517110801371130, 0.000532717809766463, 0.000708227380874416, 0.000708227380874416, 0.000708227380874416, 0.000708227380874416, 0.000675817298352848, 0.000681447466985041, 0.000681447466985041, 0.000681447466985041, 0.000732550155983634, 0.000768401938950007, 0.000768401938950007, 0.000768401938950007, 0.000768401938950007, 0.000639251273007305, 0.000639251273007305, 0.000530199036549804, 0.000520327725533091, 0.000456552272106175, 0.000263856829762348, 0.000208916033435936, 0.000208916033435936, 0.000208916033435936, 0.000208916033435936, 0.000197032506316340, 0.000197032506316340, 0.000197032506316340, 0.000175970243460076, 0.000138774064471953, 0.000183957632943407, 0.000183957632943407, 0.000183957632943407, 0.000183957632943407, 0.000133200162443973, 0.000133200162443973, 0.000131019394578600, 0.000131019394578600, 0.000131019394578600, 0.000131019394578600, 0.000117151818085388, 0.000107761320350169, 0.000109414539820575, 0.000124236270331234, 0.000142290204432018, 0.000142290204432018, 0.000142290204432018, 0.000142290204432018, 0.000117677747034599, 0.000117677747034599, 0.000117677747034599, 0.000140452000171394, 0.000140452000171394, 0.000140452000171394, 0.000140452000171394, 0.000114809386883071, 0.000114809386883071, 0.000194448883680317, 0.000849122613469723, 0.000849122613469723, 0.000849122613469723, 0.000849122613469723, 0.000342067442626411, 0.000309591391411004, 0.000309591391411004, 0.000255501634015282, 0.000255501634015282, 0.000240714858297328, 0.000240714858297328, 0.000341404050383111, 0.000341404050383111, 0.000341404050383111, 0.000341404050383111, 0.000200817054661006, 0.000200817054661006, 0.000228177176665930, 0.000228177176665930, 0.000239650398835126, 0.000239650398835126, 0.000239650398835126, 0.000239650398835126, 0.000189322179363106, 0.000189322179363106, 0.000189322179363106, 0.000189322179363106, 0.000202051006569726, 0.000202051006569726, 0.000243177690351886, 0.000243177690351886, 0.000243177690351886, 0.000243177690351886, 0.000216721543298381, 0.000216721543298381, 0.000174122710678820, 0.000266620285954142, 0.000266620285954142, 0.000267590851248463, 0.000408684322536679, 0.000408684322536679, 0.000408684322536679, 0.000408684322536679, 0.000249914244210158, 0.000249914244210158, 0.000204140260706221, 0.000185177692973339, 0.000148357658868073, 0.000148357658868073, 0.000148357658868073, 0.000148357658868073, 0.000213186310326662, 0.000213186310326662, 0.000221609018242099, 0.000233601732403820, 0.000233601732403820, 0.000233601732403820, 0.000233601732403820, 0.000156195822903601, 0.000279817063489674, 0.000279817063489674, 0.000279817063489674, 0.000279817063489674, 0.000149923755914923, 0.000118486522541126, 0.000118486522541126, 0.000102370374402893, 0.000134666526899916, 0.000143522272157909, 0.000143522272157909, 0.000143522272157909, 0.000195922672824547, 0.000195922672824547, 0.000195922672824547, 0.000195922672824547, 0.000195922672824547, 0.000159603818506859, 0.000160590657775282, 0.000185705955329957, 0.000185705955329957, 0.000185705955329957, 0.000185705955329957, 0.000185705955329957, 0.00177742422160095, 0.00177742422160095, 0.00177742422160095, 0.00177742422160095, 0.00143433099105986, 0.00118486458734838, 0.00118486458734838, 0.00103463714274603, 0.00103463714274603, 0.00103463714274603, 0.000870298638827385, 0.000870298638827385, 0.000870298638827385, 0.000870298638827385, 0.000810385292478658, 0.000801444428056515, 0.000801444428056515, 0.000801444428056515, 0.000658647916358890, 0.000555307669974701, 0.000627843667387430, 0.000627843667387430, 0.000627843667387430, 0.000627843667387430, 0.000715067345238890, 0.000715067345238890, 0.000771429889650934, 0.000854850058971656, 0.000854850058971656, 0.000854850058971656, 0.000854850058971656, 0.000814297168776880, 0.000814297168776880, 0.000814297168776880, 0.000744973967424772, 0.000744973967424772, 0.000744973967424772, 0.000718953579907635, 0.000637566210858594, 0.000214950774441915, 0.000214950774441915, 0.000214950774441915, 0.000214950774441915, 0.000175792208676817, 0.000210616118835189, 0.000210616118835189, 0.000210616118835189, 0.000263057293484579, 0.000263057293484579, 0.000263057293484579, 0.000263057293484579, 0.000113499778385775, 0.000130224883045982, 0.000173443323839864, 0.000173443323839864, 0.000173443323839864, 0.000188115562482872, 0.000188115562482872, 0.000188115562482872, 0.000188115562482872, 0.000167251661453937, 0.000167251661453937, 0.000167251661453937, 0.000167251661453937, 0.000157388988487019, 0.000157388988487019, 0.000254106877219219, 0.000254106877219219, 0.000254106877219219, 0.000254106877219219, 0.000174390010064319, 0.000164502361254624, 0.000164502361254624, 0.000164502361254624, 0.000112506054656976, 0.000141602079093437, 0.000231687789465654, 0.000231687789465654, 0.000231687789465654, 0.00353000388877328, 0.00353000388877328, 0.00353000388877328, 0.00353000388877328, 0.00353000388877328, 0.000942499895214107, 0.000942499895214107, 0.000942499895214107, 0.000942499895214107, 0.000711593601463552, 0.000830594815427502, 0.000830594815427502, 0.000830594815427502, 0.000830594815427502, 0.000661868594675428, 0.000661868594675428, 0.000763356212535973, 0.000835757214156326, 0.000835757214156326, 0.000835757214156326, 0.000835757214156326, 0.000768811335890829, 0.000768811335890829, 0.000611076273828545, 0.000853494519004125, 0.000853494519004125, 0.000853494519004125, 0.000853494519004125, 0.000853494519004125, 0.000696315685390088, 0.000683634823769949, 0.000683634823769949, 0.000683634823769949, 0.000669601799281905, 0.000660772872706351, 0.000660772872706351, 0.000645957104189119, 0.000645957104189119, 0.000719140649834333, 0.000719140649834333, 0.000719140649834333, 0.000719140649834333, 0.000617837543565920, 0.000617837543565920, 0.000617837543565920, 0.000617837543565920, 0.000264260587865680, 0.000264260587865680, 0.000209136319121230, 0.000209136319121230, 0.000186690637937904, 0.000186690637937904, 0.000186690637937904, 0.000159610632961163, 0.000142329099646653, 0.000142329099646653, 0.000142329099646653, 0.000125340382490885, 0.000125340382490885, 0.000141219738315217, 0.000145288640421821, 0.000145288640421821, 0.000145288640421821, 0.000145288640421821, 0.000131992167242988, 0.000131992167242988, 0.000131992167242988, 0.000134857391341948, 0.000134857391341948, 0.000134857391341948, 0.000134857391341948, 0.000130453002442142, 0.000130453002442142, 0.000130453002442142, 0.000130453002442142, 0.000201957873498276, 0.000201957873498276, 0.000201957873498276, 0.000201957873498276, 0.000147553315675110, 0.000147553315675110, 0.000147553315675110, 0.000204818010087164, 0.00395022104311038, 0.00395022104311038, 0.00395022104311038, 0.00395022104311038, 0.00395022104311038, 0.00263586377771585, 0.00257145965580841, 0.00257145965580841, 0.00257145965580841, 0.00326964561754893, 0.00326964561754893, 0.00326964561754893, 0.00326964561754893, 0.000901986305762696, 0.00147187229663690, 0.00147187229663690, 0.00147187229663690, 0.00147187229663690, 0.00216908482947531, 0.00216908482947531, 0.00216908482947531, 0.00216908482947531, 0.00169390535884359, 0.00169390535884359, 0.00105661029041399, 0.00105661029041399, 0.00149174482188180, 0.00149174482188180, 0.00149174482188180, 0.00149174482188180, 0.00112902367693555, 0.00111665080414429, 0.00111665080414429, 0.00111665080414429, 0.00111665080414429, 0.000945798685997736, 0.000945798685997736, 0.000945798685997736, 0.000945798685997736, 0.000716028794917473, 0.000651222924359184, 0.000651222924359184, 0.000651222924359184, 0.000629930876922565, 0.000294531576833930, 0.000186462881028177, 0.000149150409077068, 0.000388290843280981, 0.000388290843280981, 0.000388290843280981, 0.000388290843280981, 0.000209061745922909, 0.000209061745922909, 0.000165869050223232, 0.000335583198352809, 0.000335583198352809, 0.000335583198352809, 0.000440305780936835, 0.000440305780936835, 0.000440305780936835, 0.000440305780936835, 0.000178292913574384, 0.000180364635141855, 0.000180364635141855, 0.000180364635141855, 0.000180364635141855, 0.000184075999061064, 0.000304051692191161, 0.000304051692191161, 0.000351070668270671, 0.000351070668270671, 0.000351070668270671, 0.000351070668270671, 0.000168736150917369, 0.000168736150917369, 0.000144150569667422, 0.000144150569667422, 0.000198129483072588, 0.000198129483072588, 0.000198129483072588, 0.000198129483072588, 0.000168146752581083, 0.000368852100892134, 0.00201377094893570, 0.00337326413877788, 0.00337326413877788, 0.00337326413877788, 0.00337326413877788, 0.000681305748306040, 0.000465477179370335, 0.000448571180874068, 0.000461677849172120, 0.000461677849172120, 0.000469402387746238, 0.000469402387746238, 0.000469402387746238, 0.000469402387746238, 0.000469402387746238, 0.000440914706304359, 0.000549378536041442, 0.000549378536041442, 0.000549378536041442, 0.000549378536041442, 0.000372744741652628, 0.000440432173445232, 0.000506791653647605, 0.000637865638200906, 0.000637865638200906, 0.000637865638200906, 0.000637865638200906, 0.000483059857469621, 0.000482690778150707, 0.000482690778150707, 0.000633381989726810, 0.000633381989726810, 0.000659883590139779, 0.000659883590139779, 0.000659883590139779, 0.000659883590139779, 0.000447575031110683, 0.000363507109872842, 0.000353191607115931, 0.000455281136641235, 0.000455281136641235, 0.000455281136641235, 0.000455281136641235, 0.000378792674898927, 0.000378792674898927, 0.000378792674898927, 0.000222142256171360, 0.000222142256171360, 0.000213451464525350, 0.000213451464525350, 0.000194436305859414, 0.000194436305859414, 0.000172587710043298, 0.000172587710043298, 0.000220347639684235, 0.000220347639684235, 0.000220347639684235, 0.000220347639684235, 0.000150755797440889, 0.000150755797440889, 0.000146668281371154, 0.000155671224963796, 0.000155671224963796, 0.000155671224963796, 0.000155671224963796, 0.000155671224963796, 0.000145407379436645, 0.000135119693698020, 0.000135119693698020, 0.000135119693698020, 0.000166117437796383, 0.000166117437796383, 0.000237858653743373, 0.000237858653743373, 0.000238641206595642, 0.000238641206595642, 0.000238641206595642, 0.000238641206595642, 0.000174266824031524, 0.000153882402738671, 0.000153882402738671, 0.000513413131604474, 0.000822535298791382, 0.000822535298791382, 0.000822535298791382, 0.000822535298791382, 0.000489785824048382, 0.000586329207502469, 0.000586329207502469, 0.000586329207502469, 0.000586329207502469, 0.000462815352462717, 0.000462815352462717, 0.000462815352462717, 0.000386773223586943, 0.000386773223586943, 0.000386773223586943, 0.000386773223586943, 0.000439986114610412, 0.000439986114610412, 0.000439986114610412, 0.000439986114610412, 0.000523702574281343, 0.000922431018392413, 0.000922431018392413, 0.000922431018392413, 0.000922431018392413, 0.000402201326571673, 0.000402201326571673, 0.000402201326571673, 0.000402201326571673, 0.000345151023261159, 0.000345151023261159, 0.000345151023261159, 0.000370064508266120, 0.000370064508266120, 0.000370064508266120, 0.000370064508266120, 0.000318826189634804, 0.000318826189634804, 0.000453017742908778, 0.000453017742908778, 0.000453017742908778, 0.000453017742908778, 0.000340354600905926, 0.000561532664226666, 0.000561532664226666, 0.000576842960966814, 0.000833079291417918, 0.000833079291417918, 0.000833079291417918, 0.000833079291417918, 0.000213521943069399, 0.000264563962480139, 0.000264563962480139, 0.000264563962480139, 0.000264563962480139, 0.000222561050686391, 0.000222561050686391, 0.000222561050686391, 0.000206321631853133, 0.000227677913241110, 0.000227677913241110, 0.000227677913241110, 0.000227677913241110, 0.000205326532381436, 0.000205326532381436, 0.000185832069867929, 0.000185832069867929, 0.000196410555450141, 0.000196410555450141, 0.000196410555450141, 0.000196410555450141, 0.000163653498627171, 0.000163653498627171, 0.000163653498627171, 0.000192886229573327, 0.000219667392634464, 0.000219667392634464, 0.000224804904975432, 0.000224804904975432, 0.000224804904975432, 0.000224804904975432, 0.000207369459267492, 0.000207369459267492, 0.000186269028570048, 0.000203152562376493, 0.000203152562376493, 0.00301707011558682, 0.00301707011558682, 0.00301707011558682, 0.00301707011558682, 0.000876923757459625, 0.000771084624298548, 0.000683241775263543, 0.000535653827128211, 0.000535653827128211, 0.000454216611459971, 0.000414262110192094, 0.000414262110192094, 0.000414262110192094, 0.000383732760672369, 0.000383732760672369, 0.000437082491958071, 0.000437082491958071, 0.000437082491958071, 0.000437082491958071, 0.000316457465397784, 0.000395112899720818, 0.000395112899720818, 0.000490364768905004, 0.000490364768905004, 0.000490364768905004, 0.000490364768905004, 0.000490364768905004, 0.000418204587998012, 0.000418204587998012, 0.000418204587998012, 0.000392176815368695, 0.000612663162884636, 0.000612663162884636, 0.000624146094125234, 0.000624146094125234, 0.000624146094125234, 0.000624146094125234, 0.000497221230864738, 0.000460018310462664, 0.000542817256385853, 0.000542817256385853, 0.000542817256385853, 0.000542817256385853, 0.000366529319815527, 0.000366529319815527, 0.000248389943063674, 0.000248389943063674, 0.000248389943063674, 0.000232472754420725, 0.000245658755789596, 0.000245658755789596, 0.000245658755789596, 0.000245658755789596, 0.000245658755789596, 0.000214486186193889, 0.000214486186193889, 0.000214486186193889, 0.000199150919976810, 0.000199150919976810, 0.000199150919976810, 0.000190293641540257, 0.000190293641540257, 0.000184832754969471, 0.000184832754969471, 0.000185488763840945, 0.000185488763840945, 0.000185488763840945, 0.000185488763840945, 0.000149587870461530, 0.000149587870461530, 0.000149587870461530, 0.000158886513900017, 0.000158886513900017, 0.000158886513900017, 0.000158886513900017, 0.000135125409473294, 0.000126919942892315, 0.000126919942892315, 0.000147351251510359, 0.000147351251510359, 0.000176177188912684, 0.000176177188912684, 0.000272553182042845, 0.00281253757926664, 0.00281253757926664, 0.00281253757926664, 0.00281253757926664, 0.000556937700242682, 0.000339293538378759, 0.000407719267057742, 0.000407719267057742, 0.000407719267057742, 0.000407719267057742, 0.000407719267057742, 0.000380010913634299, 0.000302476009407137, 0.000448336070283997, 0.000448336070283997, 0.000448336070283997, 0.000448336070283997, 0.000389520128958616, 0.000327626349686915, 0.000327626349686915, 0.000258237723027703, 0.000257281737854174, 0.000234779474960538, 0.000234779474960538, 0.000288117347442549, 0.000288117347442549, 0.000288117347442549, 0.000288117347442549, 0.000278093665484643, 0.000278093665484643, 0.000232821914233892, 0.000347307722012317, 0.000347307722012317, 0.000347307722012317, 0.000347307722012317, 0.000221283334301991, 0.000221283334301991, 0.000221283334301991, 0.000221283334301991, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};

    private double[] CUSTOM_PARAMS;

    private double[] CUSTOM_KNN_PARAMS;

    private boolean mUseCustomParams = false; //TODO: Change this!

    private class TrainTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            readFromTrainingFile(trainingDataFile);
            return null;
        }
    }

    Runnable mRunnableClassifyTaskThread = new Runnable() {
        @Override
        public void run() {
            double Y = 0.0;
            double[] concat = Doubles.concat(mGraphAdapterCh1.classificationBuffer, mGraphAdapterCh2.classificationBuffer, mGraphAdapterCh3.classificationBuffer);
            if (mUseCustomParams && CUSTOM_KNN_PARAMS!=null) {
                if(CUSTOM_KNN_PARAMS.length==4960) Y =  jClassifyUsingKNN(concat, CUSTOM_KNN_PARAMS);
                else if (CUSTOM_KNN_PARAMS.length==9920) Y = jClassifyUsingKNNv4(concat, CUSTOM_KNN_PARAMS, 1);
            } else {
                Y = jClassifyUsingKNN(concat, DEFAULT_KNN_PARAMS);
            }
            mLastYValue = Y;
            processClassifiedData(Y);
        }
    };

    private class ClassifyTask extends AsyncTask<Void, Void, Double> {
        @Override
        protected Double doInBackground(Void... voids) {
            double[] concat = Doubles.concat(mGraphAdapterCh1.classificationBuffer, mGraphAdapterCh2.classificationBuffer, mGraphAdapterCh3.classificationBuffer);
            if (mUseCustomParams && CUSTOM_KNN_PARAMS!=null) {
//                return jClassifyWithParams(concat, CUSTOM_PARAMS, mLastYValue);
                if(CUSTOM_KNN_PARAMS.length==4960) return jClassifyUsingKNN(concat, CUSTOM_KNN_PARAMS);
                else if (CUSTOM_KNN_PARAMS.length==9920) return jClassifyUsingKNNv4(concat, CUSTOM_KNN_PARAMS, 1);
                else return 0.0;
            } else {
//                return jClassifyWithParams(concat, DEFAULT_PARAMS, mLastYValue);
                return jClassifyUsingKNN(concat, DEFAULT_KNN_PARAMS);
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
            if (mConnectedThread != null) {
                if (Y != 0) {
                    int command;
                    if (Y == 2) {
                        command = 1;
                    } else if (Y == 1) {
                        command = 2;
                    } else
                        command = (int) Y;
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
                if (mConnectedThread != null)
                    mConnectedThread.write(1);
            }
        }
    }

    private void writeToDisk24(byte[] ch1, byte[] ch2, byte[] ch3) {
        for (int i = 0; i < ch1.length / 3; i++) {
            try {
                exportFileWithClass(bytesToDouble(ch1[3 * i], ch1[3 * i + 1], ch1[3 * i + 2]), bytesToDouble(ch2[3 * i], ch2[3 * i + 1], ch2[3 * i + 2]), bytesToDouble(ch3[3 * i], ch3[3 * i + 1], ch3[3 * i + 2]));
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
        if (btAdapter == null) {
            Log.e(TAG, "Fatal Error: " + "Bluetooth not support");
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

    /**
     * @param DataArray EMG Data (3 x 750)
     * @param params    Classification Parameters (11 x 1)
     * @param LastY     Previous 'Y' output
     * @return Y - new classified output
     */
    public native double jClassifyWithParams(double[] DataArray, double[] params, double LastY);

    /**
     * @param DataArray 4 x 30000(:) array in a single vector, with class tags
     * @return PARAMS
     */
    public native double[] jTrainingRoutine(double[] DataArray);

    /**
     *
     * @param DataArray of size 1x120000
     * @return double array of size 1x4960
     */
    public native double[] jTrainingRoutineKNN(double[] DataArray);
    /**
     *
     * @param DataArray of size 1x120000
     * @return double array of size 1x9920
     */
    public native double[] jTrainingRoutineKNN2(double[] DataArray);

    public native double jClassifyUsingKNN(double[] DataArray, double[] kNNParams);

    public native double jClassifyUsingKNNv4(double[] DataArray, double[] kNNParams, double knn);
}
