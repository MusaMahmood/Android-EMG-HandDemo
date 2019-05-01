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
import java.io.FileFilter;
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
    boolean mNRF52 = false;

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
        mGraphAdapterCh1 = new GraphAdapter(1000, "EMG Data Ch 1", false, Color.BLUE, 750); //Color.parseColor("#19B52C") also, RED, BLUE, etc.
        mGraphAdapterCh2 = new GraphAdapter(1000, "EMG Data Ch 2", false, Color.RED, 750); //Color.parseColor("#19B52C") also, RED, BLUE, etc.
        mGraphAdapterCh3 = new GraphAdapter(1000, "EMG Data Ch 3", false, Color.GREEN, 750); //Color.parseColor("#19B52C") also, RED, BLUE, etc.
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
//            mXYPlotAdapterCh1.filterData();
//            mXYPlotAdapterCh2.filterData();
//            mXYPlotAdapterCh3.filterData();
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
        if(!mRunTrainingBool) {
            File root = Environment.getExternalStorageDirectory();
            File dir = new File(root.getAbsolutePath() + "/EMGTrainingData");
            File[] files = dir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isFile();
                }
            });
            long lastMod = Long.MIN_VALUE;
            File choice = null;
            for ( File fe: files) {
                if (fe.lastModified()>lastMod) {
                    choice=fe;
                    lastMod = fe.lastModified();
                }
            }
            if(choice!=null) {
                Log.e(TAG,"MOST RECENT FILE (getName): "+choice.getName());
                trainingDataFile = new File(dir, choice.getName());
                TrainTask trainTask = new TrainTask();
                trainTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
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
            if("EMG 250Hz".equals(mBluetoothDeviceArray[i].getName())) {
                mNRF52 = false;
            } else if ("EMG 3CH 250Hz".equals(mBluetoothDeviceArray[i].getName())) {
                mNRF52 = true;
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
                    mBluetoothLe.readCharacteristic(gatt, service.getCharacteristic(AppConstant.CHAR_BATTERY_LEVEL));
                    mBluetoothLe.setCharacteristicNotification(gatt, service.getCharacteristic(AppConstant.CHAR_BATTERY_LEVEL), true);
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
            if(!TrainingData.ERROR) {
                double[] trainingDataAll = ClassDataAnalysis.concatAll();
                if(trainingDataAll!=null) {
                    Log.e(TAG, "trainingDataAll.length = " + String.valueOf(trainingDataAll.length));
                    double[] trainingDataSelect = new double[120000];
                    System.arraycopy(trainingDataAll, 0, trainingDataSelect, 0, 120000);
                    CUSTOM_KNN_PARAMS = jTrainingRoutineKNN2(trainingDataSelect);
                }
                //Write to Disk? // dont...
                if (mKNNcsvWriter != null) {
                    String[] strings1 = new String[1];
                    for (double p: CUSTOM_KNN_PARAMS) {
                        strings1[0] = p+"";
                        mKNNcsvWriter.writeNext(strings1,false);
                    }
                }
                Log.e(TAG, "CUSTOM_PARAMS2 len: " + String.valueOf(CUSTOM_KNN_PARAMS.length));
                mUseCustomParams = true;
                Log.e(TAG, "NOW USING CUSTOM PARAMS!");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(DeviceControlActivity.this, "Custom Training Data Loaded", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(DeviceControlActivity.this, "Unable to Load Training Data! \n Please Run Training Session Again", Toast.LENGTH_LONG).show();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double bytesToDoubleLSBFirst(byte a1, byte a2, byte a3) {
        int a = unsignedToSigned(unsignedBytesToInt(a1, a2, a3), 24);
        return ((double) a / 8388607.0) * 2.25;
    }

    private double bytesToDoubleMSBFirst(byte a3, byte a2, byte a1) {
        int a = unsignedToSigned(unsignedBytesToInt(a1, a2, a3), 24);
        return ((double) a / 8388607.0) * 2.25;
    }

    private double bytesToDouble(byte a1, byte a2, byte a3 ) {
        int a;
        if (mNRF52) {
            a = unsignedToSigned(unsignedBytesToInt(a3, a2, a1), 24);
        } else {
            a = unsignedToSigned(unsignedBytesToInt(a1, a2, a3), 24);
        }
        return ((double) a / 8388607.0) * 2.25;
    }

    private int mNumberOfClassifierCalls = 0;

    private double[] CUSTOM_KNN_PARAMS;

    private double[] DEFAULT_KNN_PARAMS = {7.27040818933107e-05,5.19567291732290e-05,5.36481403436296e-05,5.80189859469515e-05,9.81033348779181e-05,0.000188264719235785,0.000192720869901950,0.000193353099993064,0.000186859226682157,7.66204579665955e-05,6.26043747703055e-05,8.28235805240152e-05,8.76944016989399e-05,8.40086468606769e-05,8.57783317158430e-05,6.90489373159073e-05,5.37407380246296e-05,5.46565928497528e-05,5.24375188785073e-05,5.22366509937287e-05,6.49879983207079e-05,6.64385111508759e-05,7.59435775708983e-05,7.94268700884637e-05,7.61010954067423e-05,7.35528634098546e-05,6.51692903273631e-05,5.63484957655438e-05,5.15070528781735e-05,6.76318538051740e-05,7.36116309346200e-05,7.52481596172481e-05,7.69418550195026e-05,6.59300479757605e-05,5.91806894386804e-05,6.04170172111816e-05,5.79964803445327e-05,5.07685599021167e-05,5.65237844184811e-05,0.000215117031376043,0.000260146035067674,0.000280291086875967,0.000322711331002823,0.000329624860081297,0.000248858301014673,0.000254693850104630,0.000237498974972971,0.000219713031614232,0.000212160518297354,0.000192423821514790,0.000166893183879694,0.000164062023028895,0.000156015774282209,0.000164798634626782,0.000189896575324239,0.000208760336789957,0.000213269814687642,0.000218436208327837,0.000204326115934300,0.000195272940546813,0.000190780139672976,0.000172455978587155,0.000166381966611128,0.000146432105806023,0.000139129432290297,0.000145096573428846,0.000149704767344570,0.000152188677655435,0.000151689908314139,0.000147935021383437,0.000137580021197661,0.000133701395138295,0.000141115521653472,0.000142565374420060,0.000148709832710837,0.000154041810763418,0.000153578486285831,0.000147716287211534,0.000143386564699971,0.000146220706014001,0.000145721735671754,0.000172909108279594,0.000190717281955533,0.000185054560695123,0.000171122331089424,0.000146710708179080,8.51412554208260e-05,6.73907856681722e-05,6.90053659419879e-05,6.80584862820248e-05,6.80484319524903e-05,6.31127173712296e-05,5.95541215361174e-05,6.28931304369340e-05,6.27163979388053e-05,6.75322711800100e-05,6.88077286128929e-05,6.54918137084553e-05,6.96183502399741e-05,7.14813974654384e-05,7.14178715690824e-05,7.05692890320417e-05,6.28154650819763e-05,6.25048815971591e-05,6.89059276538249e-05,6.64779503292110e-05,8.74422725090108e-05,8.88775608788674e-05,8.60637855728830e-05,8.57801731974913e-05,8.06160788815052e-05,6.26573934022453e-05,5.95892115687485e-05,5.72561445632417e-05,5.30540819102543e-05,5.85497243469600e-05,5.91540379828939e-05,5.90500569470267e-05,5.67043223169973e-05,5.56910338898981e-05,7.40016005343285e-05,8.46162944216534e-05,9.76383480360237e-05,0.000114514470890430,0.000113935676975726,0.000112331905799637,0.000117483006497526,0.000114681575902584,0.000110145069515716,0.000114282341850544,0.000114487572187172,0.000107671433892501,0.000107717235965783,0.000111064933343116,0.000101954599611565,0.000105945404772865,0.000108496052825695,0.000104931742019174,0.000112036937748507,0.000109827701081423,0.000111732688940809,0.000110168462387968,0.000100876539375177,9.81240351076597e-05,9.51551234443627e-05,9.43512615299931e-05,0.000102305228799578,0.000114607047790710,0.000122646586380793,0.000122497890085485,0.000122396839006835,0.000112359108485379,0.000109683865376039,0.000107759227769070,0.000104527195614890,0.000101464303707683,0.000101018193420429,0.000105351459454874,9.92891583113141e-05,0.000106371374718194,0.000105523677820497,0.000101348219999713,0.000101342946089948,9.96238286002571e-05,0.000103472150139910,0.000102688540443954,0.000106218638184655,0.000103584298810899,9.93828987963515e-05,8.71611789073610e-05,7.24871391650928e-05,8.74038783970770e-05,8.90079154954610e-05,0.000116065046547535,0.000146273939715537,0.000137980650466840,0.000137701512066228,0.000119490544388746,6.00887971679630e-05,5.20725022595468e-05,4.75472059871702e-05,5.28131833807094e-05,5.76641068176751e-05,6.10327714398141e-05,6.51694715108755e-05,6.22525625516205e-05,6.08065529491842e-05,6.00725843879163e-05,5.92610340859006e-05,6.60544537142476e-05,7.34595664181363e-05,7.09203901122401e-05,7.16007887236271e-05,6.52241720118189e-05,5.78443056277127e-05,5.44559464111340e-05,5.46312663451591e-05,5.68497287724810e-05,6.79494695525024e-05,6.97828708868742e-05,7.49556900530136e-05,7.21205879840224e-05,7.56226912959231e-05,7.26124443612223e-05,6.83807548356751e-05,9.12939711323379e-05,0.000305396743267999,0.000544297056521953,0.000557924808497418,0.000567162861777191,0.000567731411333048,0.000234957175076271,0.000211833388828761,0.000185292515635425,0.000184630577501355,0.000176948277728080,0.000167145275408429,0.000154644338536203,0.000136813498241743,0.000137897436749800,0.000133270086483766,0.000123726522479111,0.000128594442586079,0.000126816627811337,0.000123700262903327,0.000131736566785627,0.000134576471338771,0.000125772024338803,0.000136269546054950,0.000132722118534922,0.000138693295295430,0.000143807892484863,0.000133172721025378,0.000144361302727197,0.000141187279239030,0.000137017514647921,0.000140525465408689,0.000138083597349502,0.000136797287941964,0.000134227922456150,0.000138518318692533,0.000131193431886052,0.000128797185241857,0.000139954273191847,0.000135495275809711,0.000137765319968596,0.000147995901191865,0.000154523962354350,0.000159537227335798,0.000145243365003175,0.000134669324421369,0.000113355192281331,8.51441862941272e-05,8.06937324724587e-05,6.92444195553636e-05,6.15937568862893e-05,6.45999248429021e-05,5.85440077014618e-05,6.18137692844371e-05,5.99664031560327e-05,6.39945270112988e-05,6.38709724392480e-05,5.96086481181075e-05,5.76240948123152e-05,5.13979583994236e-05,6.00403624155063e-05,6.29671457791212e-05,6.64096210588480e-05,7.13598358856472e-05,6.47614860647139e-05,6.70776942333523e-05,6.46805894879427e-05,6.10890363505073e-05,6.08397577111060e-05,5.05760113914415e-05,4.67573504593380e-05,5.19525335467540e-05,5.51082190639078e-05,6.13137251453479e-05,6.39827012794031e-05,6.59679947662414e-05,6.36523846153034e-05,5.80992205032354e-05,6.07163123310131e-05,5.65132387140539e-05,6.10010639660368e-05,6.26199418405397e-05,6.33345604216144e-05,6.60646965316087e-05,0.000101307054321928,0.000442315482971066,0.000472567105946952,0.000478631394888505,0.000477281290863390,0.000225350855652701,0.000142098303113200,0.000139515305478776,0.000140818229229967,0.000140927658241259,0.000143116817242236,0.000131199112147446,0.000128563005696885,0.000120980269568958,0.000108461663502383,0.000106636463531087,9.91926034155466e-05,0.000115234481856518,0.000128478223014249,0.000132661322710778,0.000136531315600794,0.000130988600367371,0.000139602192048133,0.000136376958836379,0.000136310789193271,0.000132204723610457,0.000124140878905257,0.000126829493832690,0.000125318448357069,0.000127817954645268,0.000131615496052508,0.000126884642112907,0.000127625277431261,0.000130340864836080,0.000120633188552966,0.000114827797886441,0.000115837545446707,0.000110193248964276,0.000113490327028286,0.000119312438301149,0.000114209254631036,0.000117598356546294,0.000131263088236491,0.000132041941638602,0.000120860306127106,0.000106171352308134,8.18338069530369e-05,5.18765859809932e-05,6.25815983339982e-05,6.34672620711704e-05,6.42624939358995e-05,9.04100072220014e-05,0.000159261088052401,0.000249310820429290,0.000252967881469423,0.000250335056695615,0.000212501367696521,7.99049243868049e-05,6.31508593909059e-05,6.12148533928720e-05,5.97274518704920e-05,6.98575621739559e-05,6.51371705253615e-05,5.81904599522219e-05,5.52754155661844e-05,4.60077743055180e-05,5.58083315591746e-05,6.05199549997571e-05,6.14889434933662e-05,6.15840345244888e-05,6.02746997569588e-05,6.56089024886077e-05,6.57650447165648e-05,6.49993046832802e-05,6.67770496874014e-05,5.12382812858767e-05,4.64861288266808e-05,5.04791215450501e-05,4.66667483259199e-05,4.77795640476395e-05,5.34830737421211e-05,5.03185535718311e-05,5.55813370740897e-05,6.12451538672066e-05,8.54527586826610e-05,0.000110582795376201,0.000122187849543980,0.000123518953256203,0.000127637287590105,9.59450639012762e-05,8.81175470694409e-05,8.41462672843733e-05,8.35416231565797e-05,8.41857497899378e-05,7.20956576098004e-05,7.21377756719454e-05,7.17128420530624e-05,7.37664039060855e-05,8.30162702805565e-05,8.56275520388887e-05,9.44455334247769e-05,9.76739081277244e-05,8.97215160526023e-05,9.00735752052925e-05,8.84078859906129e-05,7.44083941247504e-05,7.79736194571066e-05,7.54994192132178e-05,7.36274487038406e-05,7.71701550903644e-05,7.77137715191766e-05,8.26440321349229e-05,8.21717836495027e-05,7.75893061255081e-05,7.56501523438443e-05,7.47824802192437e-05,7.56904902871071e-05,8.11917661163645e-05,8.78623269316223e-05,9.11600270612231e-05,8.94345549668490e-05,8.40743983171328e-05,8.04455923530426e-05,7.53989222152005e-05,7.85419326897149e-05,8.43314062210691e-05,9.05193466561188e-05,9.65242638542486e-05,8.85699095377605e-05,8.67596553971566e-05,7.65652779487735e-05,5.65281881315600e-05,6.38214253374298e-05,8.10663274273416e-05,8.88347709548200e-05,9.23547589931822e-05,8.69497688263018e-05,7.80217439828921e-05,5.97154810933731e-05,5.83420641421184e-05,6.24074705259319e-05,5.95366744828526e-05,5.91034298432020e-05,6.06456436415676e-05,5.21536613358457e-05,4.89047528493358e-05,5.54840742123549e-05,6.48625352752287e-05,6.87801965805769e-05,7.05189879148260e-05,6.46007456470660e-05,5.43084430681844e-05,7.99825503215950e-05,8.72361572864125e-05,9.46186674440355e-05,9.71383877578433e-05,7.17922956062930e-05,6.63303636472216e-05,6.07978476527386e-05,6.13265047284479e-05,6.00174371107398e-05,5.90105720063426e-05,6.33837102854305e-05,6.49942172329657e-05,6.55139610351273e-05,6.99523194499484e-05,6.45683713523748e-05,7.77055541021877e-05,7.61822043292036e-05,7.96632644514757e-05,8.07652176836417e-05,7.70769539284503e-05,8.39530516222039e-05,8.59373476526743e-05,8.78835508949200e-05,8.43189405634685e-05,9.40068493530239e-05,9.75594449730779e-05,9.53635239169261e-05,9.79554977846328e-05,9.30240179942981e-05,8.51147774358669e-05,7.88260356012673e-05,7.60431020926177e-05,7.31275036994292e-05,6.75189289863123e-05,6.27999963494965e-05,5.42428939140489e-05,4.87501084482927e-05,5.42032348759006e-05,5.69483304800498e-05,6.07555827927367e-05,6.15802037115318e-05,5.85268737808735e-05,7.42774903027997e-05,8.70630702826536e-05,0.000107387212187002,0.000119104127797943,0.000112201162004006,0.000106831652333208,9.29993848310856e-05,7.30946060590519e-05,7.05342936209114e-05,8.24686063558041e-05,9.91061388392408e-05,9.79481388841060e-05,9.80780383242131e-05,6.45558564534728e-05,6.73053643524790e-05,6.78532775744557e-05,7.13116793236659e-05,9.02996446494145e-05,0.000123071871816854,0.000125410940240875,0.000124021346223611,0.000125380350313651,8.92504907510180e-05,9.33318473055688e-05,8.89437603829120e-05,7.80249024875104e-05,7.38447602856629e-05,6.34243317609230e-05,6.05079048376493e-05,6.24151843520805e-05,6.92798860768209e-05,9.65150701981459e-05,0.000103414232781568,0.000108248488845354,0.000117383081530647,8.92076686666357e-05,8.44654640618488e-05,7.68792351488117e-05,7.03779281360648e-05,5.85761961890688e-05,5.66633066917435e-05,6.09263333549144e-05,6.72946454343342e-05,7.28076660215166e-05,7.49486139923131e-05,7.49169356169141e-05,7.30073964729059e-05,6.89010290170213e-05,6.31037943006591e-05,5.68198819595331e-05,7.06900435223168e-05,8.69590764072259e-05,0.000231197843794403,0.000362958868960814,0.000466738303968770,0.000624275435683567,0.000709757543816135,0.000739118558479264,0.000727448053454822,0.000672999876714451,0.000574244946343347,0.000502644468030082,0.000482134112541081,0.000476488778741862,0.000489836778613444,0.000472622604460475,0.000449903042504329,0.000436955720650820,0.000440594589750917,0.000439531150208212,0.000474616719564722,0.000472573935235848,0.000476789561327055,0.000525199505724341,0.000535185928920133,0.000512661536887438,0.000500395582375807,0.000442829838961531,0.000411038006916635,0.000418470899920994,0.000423453301229962,0.000415166935314932,0.000396478283159504,0.000387983200539544,0.000331325077522303,0.000328919558765586,0.000326686596150162,0.000339494979829790,0.000354538296132528,0.000377092428512249,0.000382895534134230,0.000396990982740994,0.000400870170064692,0.000417264152220187,0.000398039491482310,0.000361928099253679,0.000326609265952831,0.000223933564462648,0.000157880522098953,0.000109301418438111,8.21847324145936e-05,7.54582825896990e-05,6.24742098662341e-05,6.49408007624783e-05,7.08261819062022e-05,7.80051986469722e-05,7.68027888528765e-05,7.45277742068539e-05,7.46991940339922e-05,6.25016468260053e-05,7.35571418375846e-05,7.87790382580649e-05,8.83191334407084e-05,9.17634156047804e-05,8.43147446067841e-05,8.58558934538899e-05,7.76792327598089e-05,8.60070878861648e-05,8.97435695781005e-05,8.35545348340367e-05,8.23547212703102e-05,8.11483120395685e-05,6.62686168120275e-05,7.75448236298263e-05,7.85844296282569e-05,8.48108564397671e-05,8.17258468754972e-05,7.52881471549754e-05,8.82751814016860e-05,7.55578795775010e-05,7.68196731730202e-05,8.39908570069862e-05,7.24510608803493e-05,7.44052615104449e-05,8.95497049518399e-05,9.22529170005826e-05,0.000100285959198753,0.000117237930224148,0.000107612909105250,0.000110765689308044,0.000106549057305185,0.000100610591598299,0.000100054837818524,0.000104323188959337,0.000106383211652400,0.000100164915490909,0.000102197208006527,0.000102581493251656,9.77221270348961e-05,0.000102347340143596,0.000122729736865324,0.000129729255802526,0.000130110681412523,0.000129193780905651,0.000104133706621337,8.75759127783448e-05,9.20990145918459e-05,0.000100424709760571,0.000113202241382347,0.000112597399567546,0.000107684931993096,0.000108057535492707,9.98773123102205e-05,0.000104220112821823,9.98908157673297e-05,9.40007170214189e-05,8.48328145730516e-05,7.80958835171452e-05,7.60209720362941e-05,8.19306453599292e-05,8.02014831778033e-05,7.52365579024816e-05,8.02520260129304e-05,9.52654740850517e-05,9.76430832925139e-05,0.000101886492520751,0.000100335287287982,8.27073235186577e-05,7.66715524398718e-05,8.51109945369276e-05,8.93015636833004e-05,0.000100798493376459,0.000100513119624620,9.95162915733904e-05,9.43156834435751e-05,7.94047330276938e-05,0.000101481129297386,0.000107779608468323,0.000104173116335157,0.000106344764929647,0.000103564433703455,9.99210208592980e-05,0.000105292111048925,0.000102182720996605,9.42522634299478e-05,8.75412906609089e-05,7.38274145304477e-05,7.37860473359331e-05,7.83783796653735e-05,6.64364266216611e-05,6.56283575195050e-05,5.92200723733700e-05,5.36396556687234e-05,5.39505250129870e-05,5.41180885865124e-05,5.18630077968993e-05,5.07882167092847e-05,5.46925562202568e-05,5.72720626936556e-05,6.20835161566749e-05,6.63058333904020e-05,7.38890053399405e-05,7.69672397987471e-05,9.16501787717498e-05,9.55873076373770e-05,9.02964411236608e-05,8.58679678768064e-05,7.66032705459351e-05,6.66669960215848e-05,9.12527655525394e-05,0.000153678238626059,0.000161702197959644,0.000178733864860763,0.000186779654800676,0.000149306447442091,0.000140557264593878,0.000128066225144801,0.000118832172997982,0.000107218230412091,0.000102915354877104,9.61271027815378e-05,8.62878462674183e-05,0.000104625482060794,0.000107967964975823,0.000118195785195978,0.000116562501481971,0.000103630301351247,9.07250760423520e-05,7.68636989831117e-05,9.05469572576349e-05,9.97615797136134e-05,0.000110958950106620,0.000111464117407160,0.000101123728705117,9.67040290554954e-05,8.17715162341311e-05,8.67568724231386e-05,8.09281330918388e-05,8.06948589325259e-05,7.97223935874642e-05,8.18430777168967e-05,8.39128662618993e-05,8.14443768582692e-05,8.48277045607668e-05,9.13846315610388e-05,9.08315146814165e-05,9.91546466245575e-05,9.78307830629233e-05,0.000100981915037085,0.000107020263651758,0.000116410586785339,0.000122568805721341,0.000135907078308274,0.000125793014852926,0.000114861285666860,0.000105131990162372,8.81330018105206e-05,8.37941060206709e-05,8.28259850977166e-05,9.59427292304414e-05,9.83099071163551e-05,0.000109276111752370,0.000106016658867332,9.81572184736335e-05,7.86706934202518e-05,6.04735070251359e-05,5.83651275544266e-05,6.93911259619381e-05,7.63227359114334e-05,8.92967952039560e-05,9.25661360278143e-05,8.45590471208078e-05,9.30750057819060e-05,8.10262375529234e-05,8.69859163776299e-05,8.54397436708079e-05,8.78940773607389e-05,8.94678761250299e-05,8.16031980902787e-05,8.96720386360483e-05,8.02708144895122e-05,8.98430016290455e-05,0.000103448769667518,9.22852625564716e-05,9.34054259953564e-05,8.71397310238457e-05,7.07979609634608e-05,7.96080297450572e-05,8.93620058485616e-05,8.49189458761222e-05,8.42250696524895e-05,9.06626716643442e-05,7.19128508089147e-05,0.000161655901125114,0.000197430617245377,0.000208313938832646,0.000213405403329283,0.000168611517411778,0.000122871792543237,0.000114907696484923,0.000113307911598531,0.000117058140892509,0.000110831552657469,0.000103225313900580,9.42131628347911e-05,9.34903712157161e-05,8.65815524632346e-05,8.16243630645356e-05,9.22919884772283e-05,0.000104312079953811,0.000110393804772513,0.000117233691794725,0.000117759389858538,0.000118417416574841,0.000120877333519311,0.000125257651039521,0.000118530473473353,0.000110405821169679,0.000110393792919765,0.000106991902981122,0.000107023581593819,0.000104589491159466,0.000105643594229726,0.000106629844922998,0.000116944616969319,0.000114754355097355,0.000117689064740759,0.000116528925215671,0.000102426743264824,0.000121327721569782,0.000128509289652016,0.000127074945328138,0.000131716915354700,0.000124947756574300,0.000113060228546668,0.000120276726737742,0.000117979695338525,0.000113006019377095,0.000103870497950429,7.84094004315863e-05,7.37255671706996e-05,7.45343245314470e-05,7.17353077927421e-05,8.70570093472610e-05,0.000132419791854285,0.000160268291048376,0.000165249818652976,0.000165214209968241,0.000121414353808987,7.04368301479705e-05,5.71059988554831e-05,6.03488349739586e-05,7.81688040144548e-05,8.70691504131342e-05,8.60097277561955e-05,0.000105564543921999,0.000124879517729238,0.000113072514989697,0.000113966379957058,0.000102690088134706,8.82803437772892e-05,8.39787233915554e-05,8.63350192238589e-05,8.27883106412575e-05,6.50520630124897e-05,7.10663696499789e-05,6.66542777659870e-05,7.49958134029278e-05,6.70612323826813e-05,5.56432415321620e-05,7.04832173523638e-05,7.23383863355953e-05,7.22334273138656e-05,8.59907971003965e-05,8.17910418181463e-05,6.96432714246136e-05,6.73023057136042e-05,7.32996775706936e-05,9.05209257336986e-05,9.88508595520609e-05,0.000120447036887762,0.000114968525638904,0.000112130020129562,0.000107650898547306,0.000103493815897035,9.47829336501684e-05,8.82429891729714e-05,0.000101434091694742,9.22029739764272e-05,0.000105969324683578,0.000107705906974152,0.000101121880535467,0.000112517675147010,9.80828885685122e-05,9.32737752400166e-05,8.78230037375558e-05,6.92982503647625e-05,6.53321742900440e-05,7.24731468594750e-05,8.57634618261445e-05,8.77964509763783e-05,9.64150144169138e-05,9.89952916443445e-05,0.000102151123147843,9.77708342225445e-05,9.05365548300654e-05,8.46743100964837e-05,7.64081232674308e-05,7.78867397475820e-05,8.73954930074226e-05,9.27923228072410e-05,9.22069476821284e-05,9.27308210625815e-05,9.44653448678643e-05,8.38884708096099e-05,8.12157347973120e-05,8.18980402186424e-05,8.94861181732355e-05,9.08864509317460e-05,9.42270365076738e-05,9.73575938088841e-05,8.78254407271866e-05,8.21345013693869e-05,9.36715138895945e-05,8.49313630944892e-05,9.29990704641213e-05,9.66056490151621e-05,8.62388028156619e-05,9.56883113895744e-05,0.000102809275676209,9.70658228996855e-05,9.47393382316166e-05,8.44794073526636e-05,8.09201938483368e-05,0.000102925164467612,0.000101552972119159,0.000119286571050641,0.000119269318276555,0.000102535851134344,0.000102310778052529,8.69168168196488e-05,8.34157988984238e-05,7.21030830625354e-05,8.05466454994270e-05,8.49276513406363e-05,8.71311309121474e-05,9.25952932814744e-05,8.20381223745635e-05,7.34709904406185e-05,6.92769397448384e-05,6.68369809889548e-05,7.64040991369584e-05,8.68034281413069e-05,9.02389706422322e-05,9.35442822451867e-05,8.17315483543539e-05,7.20339216298477e-05,6.71530361890217e-05,8.01504841611775e-05,8.26351284970206e-05,7.90424690909443e-05,7.87011182554315e-05,6.43770813033973e-05,0.000107741391866629,0.000158217749564100,0.000207850285889560,0.000288249583739507,0.000313336607003215,0.000329797991493717,0.000306089948566623,0.000248835016208928,0.000207461218689615,0.000168753794956720,0.000161013135544521,0.000156365295997885,0.000148717305239391,0.000145972810942124,0.000143439492335578,0.000140385642508701,0.000140726651316766,0.000140138393201580,0.000131037566707026,0.000131176705384852,0.000134402468740501,0.000135076933806680,0.000144986373027262,0.000141538556112864,0.000143306642751754,0.000151864115363719,0.000145005754119177,0.000147186278735572,0.000153734628280722,0.000156071603951671,0.000155253974963393,0.000149997341470505,0.000156854401027446,0.000146715486142508,0.000153760721830283,0.000156899458319053,3.22068652504619e-05,3.35048383468101e-05,3.27810541616070e-05,3.28061089311116e-05,4.57327064031314e-05,7.03223565850644e-05,7.53628855101071e-05,7.57841315543698e-05,7.01409925993826e-05,4.27636258185712e-05,3.47470288669718e-05,3.20972988740026e-05,3.23089661298123e-05,3.29507120383318e-05,3.26183220773092e-05,4.42130212062603e-05,4.54154547658034e-05,4.49029435595420e-05,4.57709683531310e-05,3.40283870996417e-05,3.61195448857347e-05,3.89171307146016e-05,3.91477311857167e-05,4.11094204351759e-05,3.81986566530876e-05,3.67600194635060e-05,3.56226081530301e-05,3.24031033623993e-05,3.40063111634519e-05,3.45389080107560e-05,3.53167573235095e-05,3.64786659077804e-05,3.39506385142558e-05,3.23952816619398e-05,2.95021373145477e-05,3.10039124589712e-05,3.00337354131930e-05,3.24447402606189e-05,4.43410131775777e-05,0.000373419010825402,0.000562488904898638,0.000612687224685998,0.000715551685805632,0.000748021709029132,0.000612962540984950,0.000601083966247800,0.000550126240579344,0.000465028669932042,0.000419568871909450,0.000393431234693721,0.000431989994125566,0.000454955368917650,0.000432037940716270,0.000414960016363419,0.000360291780824630,0.000319211830974819,0.000318368311412371,0.000346398590697502,0.000347087029929763,0.000360148410773008,0.000366035289276877,0.000366745437947601,0.000369601434654953,0.000353037268393793,0.000356729441988726,0.000331125958813426,0.000318533009972192,0.000329643411667676,0.000339973298433055,0.000362203800555613,0.000363456511671405,0.000343110383450864,0.000316066739954987,0.000322306259641913,0.000313313108555233,0.000312608020557813,0.000338177219990245,0.000344768815087573,0.000352459864416905,0.000377485119931064,0.000383900562983992,0.000392700916676877,0.000365224159582869,0.000328999737698155,0.000272600537669918,0.000186049134129573,7.51402437984830e-05,6.80091375986972e-05,6.12397011843200e-05,5.56037816102052e-05,5.36975312585965e-05,4.13256146226405e-05,4.13017318035104e-05,5.12551205283543e-05,5.15844049867724e-05,5.38852998353455e-05,5.86889233778038e-05,4.66051192649098e-05,4.48906521546264e-05,4.59351819186660e-05,4.07880641637143e-05,4.28547705555939e-05,4.69342100999373e-05,4.53740264498425e-05,4.84454837059683e-05,5.13099405074334e-05,4.45412804181675e-05,5.06996839665360e-05,5.66946009170808e-05,5.38301764172598e-05,6.36350651705524e-05,5.94317767337553e-05,5.37768234023497e-05,5.02993249613411e-05,4.34316575313782e-05,4.63433672248051e-05,5.20265065998104e-05,5.41083571749180e-05,5.36561181813815e-05,5.45297991042563e-05,5.20151959523586e-05,7.04472966220827e-05,0.000201057058684035,0.000297579719692169,0.000372491928202778,0.000390855794150541,0.000382005692429379,0.000337775982020812,0.000287358640320952,0.000312414752707460,0.000313714193744889,0.000333832289119635,0.000339506703673195,0.000330375050146334,0.000322272717259515,0.000337503375331974,0.000341420855667133,0.000335854379354043,0.000341696844196546,0.000308563692484698,0.000290385409037236,0.000280003741789886,0.000276612181154546,0.000290524933567303,0.000283805108067760,0.000299740868105727,0.000286175394546938,0.000322307106725518,0.000334715922386776,0.000331653802187330,0.000328867902146843,0.000301945974994106,0.000278347221606020,0.000282650504502835,0.000295168342318111,0.000306200092418808,0.000309157158917409,0.000302419773128278,0.000293553840109743,0.000262410288465671,0.000270778055950068,0.000298791590611488,0.000333732693031155,0.000330122468781495,0.000322583291088908,0.000291426673118240,0.000229821046398467,0.000217192830128600,0.000177354798808128,0.000135122824624147,9.22144069144987e-05,5.47190958910069e-05,4.55309902731679e-05,4.86898333732034e-05,5.07237760825215e-05,5.92314988897230e-05,6.18647583576857e-05,6.15960214695016e-05,5.33371842191268e-05,4.67900665256112e-05,4.44042548063463e-05,4.05240992464038e-05,4.15356507569663e-05,4.23180776365605e-05,4.13962377728403e-05,4.11904459445711e-05,3.98043746311619e-05,3.46018617810422e-05,4.01359774414423e-05,4.16171178959457e-05,4.26684851821670e-05,4.61776849952641e-05,4.30973187727541e-05,4.02266443423872e-05,4.32221651317875e-05,3.85737962434840e-05,4.03026083150624e-05,4.38285812367087e-05,4.72098705714690e-05,4.87206641706973e-05,5.00297231509314e-05,4.82593671838829e-05,4.77736191573145e-05,4.37431968570171e-05,4.44502924692246e-05,4.60765827050894e-05,0.000266533384050204,0.000406356492521718,0.000448702673539011,0.000477649206642137,0.000483044917671922,0.000314971527109357,0.000271069055242305,0.000233911140250057,0.000215564005998906,0.000200176869316894,0.000198362325475333,0.000179679917509494,0.000177994384117479,0.000194576574029800,0.000200542814899545,0.000193338821702523,0.000190184155361588,0.000167876007781896,0.000146107082262088,0.000147527014330145,0.000161115610645424,0.000164558084057167,0.000169053930218916,0.000171130195261299,0.000166771736814312,0.000167747030597366,0.000171505717339126,0.000169749187544445,0.000174938713128760,0.000198218855177241,0.000203785105618644,0.000206517809707542,0.000208982756525452,0.000191244730724865,0.000184326679850590,0.000177732475785104,0.000178515398546207,0.000165603419144788,0.000162964609037278,0.000162834399486575,0.000166365580715059,0.000176083699650771,0.000175866359248417,0.000174455013229992,0.000156800987652301,0.000130173202663278,0.000102243464793401,8.34878169681010e-05,7.01455503942236e-05,6.40250000882588e-05,9.60663912540479e-05,9.40448317347191e-05,9.19323953485055e-05,8.73748998294911e-05,5.23459247142090e-05,4.66847998037263e-05,5.09902954036447e-05,5.77464179141092e-05,5.80907568594271e-05,5.44213062631826e-05,4.68555417638818e-05,4.12935875623182e-05,4.01400723597240e-05,4.04626684089954e-05,3.98178685306608e-05,4.18955374652009e-05,5.06694123363569e-05,9.33261731304759e-05,9.42282624230455e-05,0.000114096506456948,0.000113991813757807,8.54960330623424e-05,8.10756076875330e-05,5.88087595347405e-05,3.72470418192468e-05,4.01164024693115e-05,4.08489044570768e-05,3.96954178215198e-05,6.35681897654932e-05,7.94446228729421e-05,8.40585878102628e-05,8.65632144673194e-05,7.69858850020806e-05,9.25867081749820e-05,0.000476264766212134,0.000516685692093986,0.000522934302467203,0.000522997589702698,0.000258487179752109,0.000142937646627512,0.000149814357843127,0.000147363919607510,0.000143376998494182,0.000141289392035953,0.000109132714191541,0.000105980516525566,0.000100159099441590,9.32885523658436e-05,8.94754271802586e-05,8.94013369914779e-05,9.64304237553556e-05,0.000101835202066981,0.000107813804650162,0.000110321233366068,0.000106634228314863,0.000113832310530390,0.000113528825465697,0.000120172777987696,0.000119901209426599,0.000115338752789957,0.000115367450141433,0.000134358919555930,0.000145740588563008,0.000144855355554937,0.000140999296385398,0.000126491658970477,0.000106417729926860,9.96358520626396e-05,0.000103383466893135,0.000107405405430324,0.000103530956225315,0.000107934699656529,0.000106828857509925,0.000104414686379340,0.000102210130664654,0.000108140434114604,0.000110597837197272,0.000102473807464543,9.51178660213704e-05,9.00265739478257e-05,5.67406374327429e-05,5.28422717482546e-05,5.06242706402261e-05,4.68750572298727e-05,4.63663853176385e-05,4.76398297802219e-05,4.71345702743258e-05,4.61917804790654e-05,4.58581193772404e-05,4.40626528882159e-05,3.99971357610799e-05,3.67789564058529e-05,3.51499662659422e-05,3.01420479546264e-05,2.99666835666765e-05,3.36283899153576e-05,3.58084562168930e-05,3.75521526666419e-05,3.64863262648328e-05,3.43510006815182e-05,3.24107174708070e-05,3.84386237817373e-05,3.85982994459839e-05,3.97208496177357e-05,4.31301135974170e-05,3.90864213189258e-05,3.82356893343142e-05,3.78845236228072e-05,3.45985505154033e-05,3.64757936200705e-05,4.02913167730899e-05,4.25735191327688e-05,4.40540818708227e-05,4.47250744564246e-05,4.11142528473890e-05,4.21095720357208e-05,4.34531970587763e-05,0.000137696170080659,0.000238124511518044,0.000264696689423203,0.000274858560162767,0.000279105476481993,0.000174861534064531,0.000134048210822357,0.000121508877041413,0.000117129699162048,0.000123446881696886,0.000127199380333088,0.000120884686736301,0.000116672130165485,0.000123740444585966,0.000120064214423546,0.000123206686200963,0.000113224448763274,0.000108627129497046,0.000110176141849240,0.000111281170493462,0.000117463847856945,0.000115155960195250,0.000112488523360861,0.000122714919130404,0.000123117274691574,0.000125600937682225,0.000130469064621446,0.000124197578121895,0.000122845169411743,0.000124659098518896,0.000127450481193950,0.000122471372114700,0.000126368501767613,0.000125497737766560,0.000122695475506711,0.000129267433372361,0.000125198517924127,0.000120727205034997,0.000126020015015634,0.000117952429583368,0.000120503095681792,0.000129333862871235,0.000144450100719090,0.000142036076762065,0.000136630461578565,0.000126610900077847,0.000105691990596150,7.59244584706120e-05,6.70947231863608e-05,6.29644541245440e-05,5.44222790950550e-05,4.67765217475427e-05,4.25701605809657e-05,3.65308986609958e-05,3.85260583758173e-05,3.96771423681874e-05,3.94413493908044e-05,3.92502451849082e-05,3.80863771736372e-05,3.60121477059511e-05,3.92460896195848e-05,3.84553994538215e-05,3.69555069338247e-05,3.48235357055131e-05,3.24644584458616e-05,4.18923900727151e-05,4.52021153368362e-05,4.59622293310564e-05,4.81018074194410e-05,4.69514099529697e-05,3.97578734564896e-05,3.99575973095593e-05,4.28540304609595e-05,4.05235453568584e-05,4.10874980284120e-05,4.17344212996207e-05,3.62839266675998e-05,3.55084048468290e-05,3.81983669359926e-05,4.04067813828471e-05,4.42992078869103e-05,4.50946188145317e-05,4.83954615042291e-05,4.98820758267070e-05,4.72929200750951e-05,4.89235646438290e-05,4.68560115669612e-05,5.07221015294729e-05,5.78166683761404e-05,6.67889504592860e-05,7.55365185088940e-05,8.23407307248074e-05,8.71659488777848e-05,8.46909594126682e-05,8.30340721241730e-05,7.92964798758834e-05,7.25481927457870e-05,7.39589215705726e-05,7.81789263814409e-05,7.82897256676110e-05,7.09805335091294e-05,6.35292904838851e-05,5.17710175133564e-05,4.92692983462931e-05,4.81312079485995e-05,4.88871818966169e-05,4.61707870521403e-05,4.57228123030821e-05,4.40405895372476e-05,4.74918378865216e-05,4.73423171601452e-05,5.03682534396503e-05,5.40208805647684e-05,5.67345947469731e-05,5.45600980437454e-05,5.22222715542282e-05,4.75582872384248e-05,4.13456332283035e-05,4.42310203413847e-05,4.80647158509878e-05,5.13352243784828e-05,5.16487225755124e-05,5.00251655483324e-05,0.0138695227849108,0.0115230533673200,0.0123657650087542,0.0131812021642741,0.0182987922991799,0.0283139271309328,0.0305730558103247,0.0307918369704142,0.0275444765194610,0.0170871987287164,0.0139326192628485,0.0182679191464605,0.0188284685751034,0.0176306372799185,0.0184931722698655,0.0140021896941565,0.0121276546098459,0.0123115586040246,0.0116850485473684,0.0117799643867767,0.0143050808158432,0.0147131252543707,0.0168620305783559,0.0177502101334451,0.0171245552130373,0.0167206736798412,0.0146663680071288,0.0128868653408254,0.0119580644208821,0.0144398549267300,0.0162050753199439,0.0167882022151744,0.0171394818584242,0.0150852321303022,0.0133945024148068,0.0135895548856456,0.0127674707612991,0.0114407282013696,0.0125558273371503,0.0290484239407349,0.0417007431729881,0.0519159101885619,0.0675914568623180,0.0670497768472601,0.0588988061761940,0.0602927444864616,0.0548433261993096,0.0525248083258904,0.0503022103723031,0.0452785157510609,0.0394031621152218,0.0389218944066433,0.0363803195671767,0.0392002469280238,0.0435844459526970,0.0468403544899500,0.0480214186847160,0.0492131038854276,0.0453256630156719,0.0457685232801237,0.0445877719176202,0.0398881511489265,0.0394237278789930,0.0348402962610067,0.0331063932539368,0.0338920523699094,0.0353178224290686,0.0360384932123955,0.0365042552903620,0.0354829612080641,0.0327774007851247,0.0321900430801769,0.0333791254928285,0.0341070346922371,0.0358752351474121,0.0368766960252331,0.0363776699515355,0.0354007879602599,0.0338503544770695,0.0343395951189301,0.0340579775466178,0.0394039895664808,0.0432247086726631,0.0399223904926226,0.0355175943359398,0.0265898308621069,0.0186907456823309,0.0155971288607119,0.0158076280296949,0.0155183858375472,0.0153343723254416,0.0142064391426420,0.0131190357877339,0.0144583095342716,0.0142470559073906,0.0158473352756662,0.0162143291165264,0.0151702842224998,0.0162152816205857,0.0166200325083092,0.0166199759356893,0.0159980219227826,0.0146430041687382,0.0146005563984835,0.0158693139657742,0.0150871748967591,0.0189380725999997,0.0188443594041154,0.0181037686357357,0.0185765652637000,0.0160970305074697,0.0138735648849702,0.0128276234650153,0.0119194069087859,0.0116301686455239,0.0129582009233608,0.0134277674931591,0.0135171218524130,0.0131548558140864,0.0129391346394015,0.0153277670048719,0.0180640815195127,0.0212609091469082,0.0257195273956938,0.0257627885224477,0.0258658345757881,0.0267041292180995,0.0263198219161569,0.0260523775182058,0.0267336111100719,0.0270880468214405,0.0252694623770406,0.0250774044301381,0.0256428916919681,0.0237516927041939,0.0245142513319252,0.0253937933960939,0.0243642733500748,0.0264056479047607,0.0259505922654187,0.0260680376720493,0.0264526214136094,0.0237386097498515,0.0230355205351097,0.0221584140434125,0.0217009669060647,0.0238985342384230,0.0266683703152223,0.0290535657040767,0.0290213157422582,0.0286084293500655,0.0265335825558098,0.0254391246969768,0.0250034338761694,0.0248303043031090,0.0240575696475583,0.0239836705075749,0.0246743799025563,0.0235829436768231,0.0249017497708969,0.0246942065931151,0.0234787989041945,0.0233726385963151,0.0234034705098935,0.0242813311585262,0.0241523737835162,0.0252481028270862,0.0240739286766092,0.0225154425325440,0.0198099886087179,0.0168909579160135,0.0191651552517804,0.0193234404417839,0.0245636307990766,0.0308847141671848,0.0287993724379242,0.0281803072806108,0.0221107691498059,0.0134797184019045,0.0116082805445988,0.0111127719006813,0.0121638594920869,0.0133459232696743,0.0138787599738108,0.0147348702597993,0.0141093603994661,0.0133894801753216,0.0136481748491781,0.0136081645131865,0.0152017036142044,0.0166582298669421,0.0159167967622333,0.0161808480575771,0.0145259014079087,0.0134958169951399,0.0129065905342333,0.0124360313074357,0.0131129023581014,0.0152758825105121,0.0157911509492126,0.0171093896901296,0.0164980459605752,0.0172879281235707,0.0164071056229535,0.0155227000158499,0.0199485059592825,0.0397770403062294,0.0846954363198730,0.0963973764350848,0.102269253630582,0.0988071535540228,0.0531586799426722,0.0476733171138258,0.0436547619626439,0.0432785352495713,0.0415994309469901,0.0391600973196938,0.0358214536928028,0.0327201299952033,0.0326033902911702,0.0310361177364588,0.0291131480058794,0.0292696055396429,0.0293782587688429,0.0292734819487538,0.0314831024440413,0.0314110049555214,0.0304476420065012,0.0325599731949388,0.0314132832716701,0.0324897317979172,0.0337986903940975,0.0316842015030283,0.0343751519104593,0.0332878414789328,0.0327409914116415,0.0333404684647230,0.0324529351127699,0.0325227518292579,0.0319658293307463,0.0326919835609694,0.0310704222955664,0.0302451470874202,0.0329978391557844,0.0318879048987230,0.0329929541411575,0.0357499690437074,0.0366341719512017,0.0367964893936774,0.0327352905139430,0.0299978286241452,0.0249843657381273,0.0193475672764824,0.0181916209578144,0.0156383143165259,0.0141286971413707,0.0147655843446551,0.0136654114614900,0.0142076814482186,0.0137973875672654,0.0147547425021802,0.0145853975326891,0.0134855689348714,0.0129081707311563,0.0114183079199753,0.0131291544071229,0.0137396144598742,0.0146292667731571,0.0160057456977831,0.0143807316325575,0.0153984899660787,0.0145097096645324,0.0131734443196510,0.0137857758386707,0.0114167154947788,0.0109510341192965,0.0121385412747197,0.0127701137394559,0.0138351039435513,0.0145661766950538,0.0145139496562543,0.0138754500985144,0.0128934325042886,0.0135894244507817,0.0129291371826251,0.0140516087974832,0.0146719988526537,0.0148314126265621,0.0154593294515957,0.0205705795062818,0.0651019086461563,0.0792131031297965,0.0854122776903018,0.0844977014506442,0.0464443487394889,0.0336450700770261,0.0330926448097418,0.0328454207111691,0.0329690136427734,0.0342936774620177,0.0310519695737401,0.0303924297453430,0.0284555475133760,0.0251195968423239,0.0252098224829631,0.0235483173782617,0.0266439619976023,0.0295423980658099,0.0310631093788228,0.0320881824582320,0.0305304641117067,0.0316657771194813,0.0318088064989774,0.0315791874403329,0.0305366108149856,0.0289157556822789,0.0286999322805798,0.0295133358627609,0.0300335119904117,0.0309267559268020,0.0302342373225426,0.0302774662825024,0.0307956140540920,0.0279817168478610,0.0268542732734193,0.0262773813924270,0.0252483875450586,0.0266152414664067,0.0278281610859057,0.0267674240052835,0.0274381572650648,0.0306800869483463,0.0300486428244772,0.0270892377548281,0.0219979271084304,0.0160705663744939,0.0121591056813942,0.0137700995558092,0.0140023699668455,0.0143665433165575,0.0181866996683946,0.0295349700854741,0.0466274161220332,0.0494854196241509,0.0483945839037535,0.0345866105051718,0.0165094198578927,0.0144395321758822,0.0139107718859691,0.0139530680457057,0.0163495685255677,0.0147558812292862,0.0126086727898420,0.0122058834476769,0.0104620698106254,0.0126410744101510,0.0137345823317950,0.0135603670155398,0.0136194757070446,0.0129290914409229,0.0142979204752513,0.0142916764557079,0.0141718009082538,0.0144399731943773,0.0116967023142414,0.0108016163618364,0.0108010294579453,0.00991569114325460,0.0100842402014552,0.0113677098446613,0.0112036714044150,0.0122609623926330,0.0135649893545551,0.0167199153584259,0.0228022243003106,0.0264436565197149,0.0271762443908224,0.0274331619481094,0.0212242828951644,0.0203695301251933,0.0193007753017702,0.0191198264461429,0.0194120542431242,0.0169898697372040,0.0168598941410062,0.0168389764536283,0.0172647717809473,0.0190447857229308,0.0199458035841708,0.0217535423846827,0.0227297942543299,0.0207433614581187,0.0208999769331995,0.0198003407694052,0.0174633007155814,0.0181304612339553,0.0175968141342415,0.0176038214729679,0.0183010382401187,0.0183166980462926,0.0194317209691394,0.0193766748227903,0.0180182318811167,0.0177697729007665,0.0175356692814566,0.0174377131650353,0.0189853462786558,0.0206330339337730,0.0208416439866114,0.0211625089508720,0.0195955320930568,0.0185749556294814,0.0179032312614278,0.0180632357430394,0.0197471126308868,0.0212408626794458,0.0223451924973308,0.0198502978551842,0.0191739069100923,0.0164982565288064,0.0128664605695494,0.0145005778794845,0.0176290504752507,0.0193340259868980,0.0208814676014593,0.0189557122787578,0.0164779116080750,0.0135700884031174,0.0131462437276980,0.0143680850038113,0.0134471145453392,0.0136519621118209,0.0136520488677210,0.0115178282734278,0.0110593887259203,0.0120643301980779,0.0138134905454630,0.0151620123030818,0.0155721795445275,0.0142554773625937,0.0124107754181095,0.0163496166823484,0.0183756912348736,0.0205427128756208,0.0215041479521015,0.0158036495356032,0.0147315218184513,0.0137515256325912,0.0135819360346027,0.0136687649879290,0.0131897075025323,0.0138642680129775,0.0142853092584261,0.0144784743486586,0.0154115990342468,0.0149774009916110,0.0179062700699316,0.0171354761400835,0.0180793678873713,0.0181671066768515,0.0171526554823396,0.0199357016463281,0.0199553927824419,0.0207115384312696,0.0200548185105590,0.0218581063964560,0.0226496555859861,0.0221681536033656,0.0223510039715400,0.0211910101347518,0.0193526198343329,0.0178677672300122,0.0178943679559678,0.0169979021265489,0.0155002464427336,0.0140284020005794,0.0121949746420487,0.0113844540525039,0.0124593397946342,0.0132185319078941,0.0141590747330564,0.0143473311821859,0.0135976874947519,0.0170189389618268,0.0192528355698552,0.0238675787395489,0.0270753744455165,0.0243067074122737,0.0235140693209682,0.0199523061260440,0.0164508380996043,0.0160398339339700,0.0178568562286192,0.0216014457029580,0.0211271432497475,0.0212315956301854,0.0148847866198532,0.0151383030249624,0.0148328294086357,0.0158690387826508,0.0204875323454332,0.0259275494402375,0.0264849182418416,0.0264145306873850,0.0261035023656197,0.0201111028401100,0.0209518415336093,0.0190386608536095,0.0171652020334521,0.0158968418505440,0.0140630628215617,0.0137606552969376,0.0136967010391308,0.0151077946834172,0.0195682006367200,0.0218787228386096,0.0233743876320129,0.0257795602690454,0.0204056730866706,0.0192721610341674,0.0174385265825489,0.0149873780813231,0.0136620360296955,0.0129088548446696,0.0139021427520915,0.0153901480845493,0.0166882509736375,0.0175624637487306,0.0170326343868484,0.0167892866556519,0.0151513697020078,0.0141470381577131,0.0131891205718867,0.0155213857235075,0.0194113415758324,0.0352969945855667,0.0639661495197618,0.0949950603011760,0.138700135157137,0.163582743666333,0.173742044833623,0.169714000177288,0.150141217056151,0.132746853097641,0.116188634754509,0.114448231849499,0.114180933085733,0.117157510936199,0.112149134071034,0.106022568477792,0.102899786269285,0.104647302465622,0.104821382317710,0.112639715039691,0.111591384178478,0.112235931783566,0.122607761874022,0.124553943422470,0.119897387700039,0.115971684192048,0.103962902941266,0.0975855694229630,0.100123584794575,0.100259887885016,0.0967622079402087,0.0929794519135418,0.0891446335707179,0.0793640600416044,0.0786208857041283,0.0781333441598138,0.0808883082426886,0.0843873585113870,0.0911568457368172,0.0925628923473821,0.0955789544118877,0.0954119009767619,0.0964931944685676,0.0908967742214713,0.0798182912775760,0.0644856968532933,0.0462905320061161,0.0331416869737039,0.0232475578582770,0.0183466973274728,0.0170916572212968,0.0145420451172292,0.0150298217359093,0.0163782956520526,0.0177614245254630,0.0171587928238872,0.0165000052186061,0.0165606484929804,0.0131309630824667,0.0160003383677025,0.0170838672016000,0.0182395575286985,0.0197929519447427,0.0179125224398709,0.0185676625762469,0.0176455253816480,0.0192463954583902,0.0200368763356447,0.0179967492426799,0.0179612694110104,0.0170295825726365,0.0151197597563570,0.0173491343108597,0.0175648608930287,0.0186025950628006,0.0173044019019662,0.0158933325909397,0.0185446352139803,0.0164118463379284,0.0172165063208729,0.0193246665681083,0.0165941919464616,0.0168122044940035,0.0194213181351200,0.0195187163479126,0.0221574186756595,0.0265759770401219,0.0245349565612277,0.0251860199509376,0.0242512334411993,0.0234811468243924,0.0236042577616330,0.0249164701427729,0.0251331619153566,0.0235874085045317,0.0242931015006727,0.0241748460059468,0.0232081436808575,0.0243001396524753,0.0278121106869994,0.0292889980984717,0.0293021987325247,0.0287398719292184,0.0233629154163385,0.0207115657855332,0.0218402810786642,0.0235701307286560,0.0264852234341997,0.0262723711336468,0.0247241128574896,0.0247217294597943,0.0233607444357387,0.0242661335941825,0.0231495920339111,0.0207097607299995,0.0194923991025480,0.0181576979199921,0.0178078540717744,0.0192990302905740,0.0185895558846904,0.0176336092616822,0.0186586446475133,0.0218352554420293,0.0223247366763199,0.0240908511732088,0.0233834891673052,0.0191112985862533,0.0181015325087900,0.0189964388839318,0.0202780425019602,0.0231955315899802,0.0229490746722740,0.0223348924664818,0.0216191276610436,0.0180467901956283,0.0223897098944654,0.0238767609155058,0.0224973801083842,0.0233986178965400,0.0221608030311064,0.0214860026140304,0.0228387627986585,0.0216755073163320,0.0201504744102849,0.0184184072501871,0.0160442551417801,0.0168916728353236,0.0173867780423883,0.0151771443043635,0.0150132862554046,0.0137541524550785,0.0124464075143102,0.0127762090890299,0.0127386007038121,0.0118980066784702,0.0117872446942764,0.0121435120369497,0.0125442772717297,0.0139849530829797,0.0150117974895606,0.0164117481644448,0.0174304466239540,0.0204642308274113,0.0213638406776574,0.0201082587438286,0.0184293595761968,0.0164260283992225,0.0148841654016650,0.0186154122656696,0.0304713411564750,0.0334001985235956,0.0395020571124619,0.0423090588124854,0.0338152839226033,0.0330647661856154,0.0305540638118566,0.0278427965398360,0.0252336192060056,0.0239717893437739,0.0222034163512101,0.0206496297266126,0.0240246157514133,0.0246881213516469,0.0270807356505042,0.0264289301416532,0.0228554148047491,0.0204365646151216,0.0179095312052364,0.0206040508095508,0.0229608120024107,0.0261218349626798,0.0260879483766505,0.0237180254422578,0.0219420976382266,0.0188752211449598,0.0199300558365856,0.0187624273457214,0.0191990964202745,0.0191707480581296,0.0191781200178245,0.0199485518411500,0.0189506047836304,0.0196918071933471,0.0206126886687190,0.0204031546833739,0.0225614623476188,0.0225228029859181,0.0229198906322457,0.0253538120149374,0.0272314759342820,0.0289437495864138,0.0322259417374295,0.0290900342309477,0.0258082226627866,0.0235235138088059,0.0202907246831801,0.0195181708335596,0.0195653556594751,0.0216979071862843,0.0219581117708242,0.0244271214296570,0.0230193426794075,0.0204973967066864,0.0167181927013863,0.0132138023720721,0.0132140134281973,0.0150884378669830,0.0170181261039947,0.0197939833169924,0.0208821509651064,0.0186238392915353,0.0199439370591137,0.0173505138474508,0.0183840500376735,0.0186531082013400,0.0187474165651162,0.0195732959523591,0.0182578332690797,0.0201496706497980,0.0180002137823369,0.0201649663389279,0.0227306045521690,0.0194395307923886,0.0196973968443219,0.0185881763976334,0.0154360782945687,0.0176217926686077,0.0194724226690122,0.0182173271488009,0.0177688402359497,0.0194634452212347,0.0160972703336164,0.0305303573515137,0.0397495567539567,0.0438940653236834,0.0472859234576235,0.0362956033184563,0.0285514515894937,0.0268863565198123,0.0267807601603854,0.0275566769368772,0.0260473120536067,0.0237033788402760,0.0218118919419591,0.0216934686335643,0.0201594001056896,0.0195811263517033,0.0218328770135354,0.0241634736130274,0.0260543554511462,0.0278541356720406,0.0279163992985994,0.0282948860878901,0.0287792408251290,0.0295967713425096,0.0274388720354136,0.0257547242682516,0.0256991527334944,0.0247488835578568,0.0255194685951010,0.0248279084265355,0.0247801497726310,0.0252986640102451,0.0268377764534517,0.0262302899732163,0.0271960495159618,0.0268358441717279,0.0243341491064998,0.0286011370493578,0.0299224248521678,0.0294914657136733,0.0310440598728762,0.0284213724670311,0.0269456098666410,0.0276230922936489,0.0265891118925982,0.0255748037833423,0.0224055878066808,0.0183328205182934,0.0171241694124816,0.0172541243554989,0.0167238690031817,0.0189609165027596,0.0267721352018231,0.0316014719839093,0.0340800054299850,0.0332170552669678,0.0221396504031167,0.0151416201745294,0.0129352365292313,0.0132312640677154,0.0169841041597304,0.0186671405834378,0.0185830333939671,0.0228978708972297,0.0269618938848081,0.0239953317346525,0.0242871544053481,0.0207383856023399,0.0187044029045465,0.0173822894361270,0.0181102236048527,0.0170892926402898,0.0136813726907932,0.0158403668597635,0.0147406090595701,0.0162397031660311,0.0143884565998159,0.0113946911985292,0.0145982967305965,0.0149550347136710,0.0155073944783401,0.0188122025556008,0.0172878191278912,0.0145231314680911,0.0140619018385698,0.0157603079219056,0.0197428850512014,0.0225542421765590,0.0284496192011599,0.0264507013260659,0.0256081973094285,0.0247212095430844,0.0235106000039906,0.0218342465562920,0.0204531953968393,0.0229734944167017,0.0205226256119647,0.0236558489299348,0.0237538992884914,0.0223112909564813,0.0249405681781364,0.0220116259454588,0.0211070172061862,0.0191853232320992,0.0164835965826940,0.0157020197763997,0.0168155870707869,0.0197128891777717,0.0198411618074513,0.0221172414178777,0.0228549531405835,0.0238855266411585,0.0224239054234115,0.0204673211387102,0.0192008684318671,0.0174123609118809,0.0177300317861814,0.0196221827278593,0.0203180179076067,0.0199682065834256,0.0208178639680539,0.0206208869844385,0.0197633706986644,0.0194990007935651,0.0195036720625034,0.0211072554036650,0.0214024145881467,0.0219589523334942,0.0225065081202121,0.0201595950417050,0.0183409936217579,0.0207279351737669,0.0187585668030187,0.0202775402596753,0.0215385590520576,0.0186945461282371,0.0215987899902069,0.0229150293196597,0.0215457386633500,0.0212854956411434,0.0182983507536398,0.0176377663881250,0.0213001769075462,0.0208252596112344,0.0259263530570117,0.0252411513782949,0.0232561104354143,0.0228386458257123,0.0193177386121193,0.0179685078341237,0.0154426720422202,0.0178795793460519,0.0185318957980801,0.0190887616604949,0.0208806682355504,0.0181050283205301,0.0169598512750546,0.0159076297917890,0.0151001312357550,0.0171017811478327,0.0190212229514323,0.0199455988132022,0.0207142299444110,0.0175527454716512,0.0160976013088205,0.0156435447442550,0.0174176847129298,0.0183615592930224,0.0174149900212268,0.0170096544433340,0.0149502234402732,0.0208201324256464,0.0311142213409465,0.0437437672920320,0.0633199388183763,0.0725185282974171,0.0765358294131058,0.0709383557391707,0.0579810952715407,0.0478529959109896,0.0398704540685116,0.0385331172047980,0.0371196109492970,0.0358301772452148,0.0349771789995038,0.0340448847765459,0.0326785174317782,0.0333228896345930,0.0326215952190891,0.0309479644134053,0.0313376581085573,0.0317668547247866,0.0323539906760743,0.0345472545850463,0.0340209309543086,0.0339552794339970,0.0362478939006907,0.0338141731449974,0.0347363090112165,0.0363499239082857,0.0365439859631402,0.0364507225113051,0.0348391195078006,0.0362026224898955,0.0342413687792867,0.0363848546122040,0.0374288535203564,0.00753543147151645,0.00771044312084097,0.00743928158949379,0.00741636070178464,0.00964534408049154,0.0139595375344625,0.0160426000981234,0.0163082550838088,0.0140614180114042,0.00943272708462578,0.00804145290774572,0.00749454434421279,0.00760499181157876,0.00780352513455485,0.00745927297024294,0.00982106926884708,0.0100600730824069,0.00971705510010307,0.00994032284164381,0.00772154098062521,0.00813264222089936,0.00892045603526691,0.00910588123986625,0.00959713737428480,0.00911549404322852,0.00859014271420977,0.00822542301036842,0.00755193988253400,0.00777811215098249,0.00806728334984870,0.00825247485476741,0.00841884182529906,0.00780123910780445,0.00747078079698568,0.00680525517048267,0.00719538931689875,0.00699502805077291,0.00743529204155899,0.00979030661389632,0.0417103242802294,0.0862599733480083,0.113877466516690,0.154175500383893,0.163572933192736,0.144909616520716,0.141353055469005,0.125145702677652,0.109116939160199,0.0993469733438085,0.0949201427255346,0.102102258846118,0.108933626915491,0.102583632003968,0.0963226616492875,0.0850568073600269,0.0766907672189937,0.0758438762707974,0.0831286788763133,0.0829455665710239,0.0861667709063392,0.0869378014037850,0.0850322298886790,0.0876617313474466,0.0841017513792900,0.0850610159195596,0.0797080255374540,0.0761002880210355,0.0793776479586313,0.0804035624448474,0.0863103200940119,0.0871603506872844,0.0825676167489586,0.0752978989152289,0.0748160430602257,0.0740281965388699,0.0738978587114369,0.0805734744857724,0.0815539378620941,0.0839148739383758,0.0901660135790199,0.0909284319522931,0.0918150353046279,0.0814969700442409,0.0648742867952496,0.0484449180483928,0.0266160654274325,0.0169193896986156,0.0155559133073846,0.0137743679807220,0.0126381407223681,0.0116083074398100,0.00953441667205347,0.00932592984642624,0.0109924860304897,0.0111699392957144,0.0119396787206390,0.0130084187859814,0.0107553705239602,0.0103908089045395,0.0104473066202232,0.00955980306008026,0.0101175135059348,0.0108316233937718,0.0103259373258478,0.0109464789674556,0.0115808458728248,0.0100521753742806,0.0112159495616346,0.0119510450063940,0.0115945494215797,0.0139810853367726,0.0127296108879472,0.0109342810839852,0.0106736571391245,0.00954812149292702,0.0101320860878410,0.0115974076021626,0.0121319254183776,0.0118683041547633,0.0120741649315458,0.0116496621591492,0.0136760405452527,0.0328628086154991,0.0563853591724228,0.0782346493965118,0.0892215089540453,0.0870482528214055,0.0755242819627778,0.0670374128090768,0.0736877954401634,0.0737921348584880,0.0795554946510636,0.0782523444893849,0.0779390865312844,0.0756970712734121,0.0804223919095459,0.0809262370913295,0.0801572914911480,0.0815178475712891,0.0718861890335403,0.0690414094671385,0.0650755865631475,0.0639346491934524,0.0674277047026985,0.0659619618380590,0.0705000756973537,0.0679884582294546,0.0757311371336790,0.0793563193677138,0.0770999041007220,0.0780754640435880,0.0702050203300517,0.0655560549615483,0.0674201239690357,0.0697413506354846,0.0726900218464286,0.0729521543353254,0.0717336491892520,0.0696143737810722,0.0627738370930270,0.0637782387847118,0.0691653471929076,0.0763001860390148,0.0755963425143321,0.0731188653679730,0.0672026331207679,0.0532943605341293,0.0482137069901955,0.0366403960694960,0.0254329068411226,0.0183420046421631,0.0115500084111174,0.0100925253816959,0.0106894903625115,0.0110880461137319,0.0133584770878643,0.0136540791907984,0.0134496974135131,0.0116876054780306,0.0103691012932173,0.0103615486253469,0.00930624274247112,0.00962326728674034,0.00974782979325059,0.00929756552010816,0.00955315165691098,0.00915665962058890,0.00825203556956287,0.00928299310978960,0.00964771387781980,0.00987338919700967,0.0104865233315874,0.00969624666717548,0.00922847723244918,0.00966083198827962,0.00874982025724983,0.00903639530226552,0.00979621470852634,0.0102070323111901,0.0110231205595209,0.0112582858134654,0.0110009644609969,0.0108520990895406,0.0100766332849437,0.0100897121483766,0.0103771357430379,0.0302038758379077,0.0639309798532091,0.0840093067958873,0.101091699187531,0.101955610794567,0.0731246011606327,0.0617591619822932,0.0556927580320046,0.0514589562862750,0.0476594176926348,0.0471305796097803,0.0430466734286598,0.0423395025255672,0.0460188398049848,0.0469333239687135,0.0454394421380040,0.0444425032600789,0.0394935372423753,0.0346590491500354,0.0356922094141423,0.0383684618584307,0.0393918583364428,0.0408558254825344,0.0410792135820668,0.0394956716935278,0.0401504302836783,0.0410788499473367,0.0403244836699029,0.0418791150466507,0.0471313351009760,0.0486426389613873,0.0489093398312762,0.0490695485628659,0.0451706200746848,0.0440548540951670,0.0427567382283818,0.0417308859959375,0.0395729888990373,0.0392946348762667,0.0388513863622789,0.0396738041898920,0.0411739645703798,0.0405855724253456,0.0390103270010427,0.0351141115063317,0.0290440323832038,0.0223462126351187,0.0189546316198191,0.0157606638286777,0.0148410993392278,0.0204518407167563,0.0193912589624238,0.0188102296467205,0.0168825155297315,0.0103523911988674,0.0107194897157555,0.0112832376686182,0.0127648969971315,0.0127932719232489,0.0118372575091706,0.0100836963070842,0.00800240776899990,0.00901229516837740,0.00902075763198030,0.00879472348058779,0.00944371875976605,0.0103061800677255,0.0181228970767005,0.0186997636997593,0.0239341199759718,0.0235133602220541,0.0158213320561167,0.0154259139495328,0.0107600953021328,0.00869917327674089,0.00915761105403622,0.00943810361355693,0.00923184715691778,0.0127306844087129,0.0150884740112006,0.0165235349433547,0.0177030648517441,0.0151663981760732,0.0184150187769319,0.0668392841182964,0.0851675626563539,0.0926611032411485,0.0924995236772061,0.0506765491456512,0.0342479950736980,0.0349309198276991,0.0343177227008188,0.0328024835878674,0.0326280901303257,0.0258381392987202,0.0251474839628762,0.0238160724095250,0.0222646258462678,0.0212039050569718,0.0208885254820098,0.0226223748137838,0.0238243510871534,0.0254287201378298,0.0261261066615915,0.0254101500222690,0.0268717428557120,0.0267392199634053,0.0280743955199696,0.0276256441575890,0.0267201364214652,0.0265186906178384,0.0308458799566586,0.0333650574425992,0.0328959043671614,0.0317390147057875,0.0280051136873046,0.0250534704389240,0.0233614725233070,0.0241143299541720,0.0248704754918005,0.0241007006584839,0.0256589590647378,0.0258554916586655,0.0250774930647703,0.0243604571756987,0.0250944820433715,0.0249916499979833,0.0224046961152613,0.0203086352811903,0.0179969498863796,0.0133742043052476,0.0124114666352740,0.0117175276320964,0.0109338646405204,0.0107210275899729,0.0110652460857104,0.0109608579915519,0.0105413538921928,0.0103649255557562,0.00971953218868924,0.00911153115565600,0.00855674719075470,0.00802435317615364,0.00700434464112874,0.00702006723427220,0.00753105223231697,0.00819775765236092,0.00872623331243323,0.00831805718543698,0.00778204992718875,0.00742597218606124,0.00876406435997553,0.00887438219485571,0.00916013156842513,0.0100823537191515,0.00861931649448468,0.00897959189747354,0.00866701269926464,0.00773889106260978,0.00844145952058950,0.00897365873511298,0.00961538058145404,0.0100957362192009,0.0102426776665538,0.00963064304734707,0.00953985315348410,0.00991810743694761,0.0190976017479212,0.0392578650821620,0.0504754441739994,0.0566328434731542,0.0576187071837029,0.0381740950563019,0.0304087831526631,0.0284784200289321,0.0274533490906139,0.0295207662424583,0.0302318252784410,0.0283587114985487,0.0275995570486338,0.0291276925905917,0.0283883126930825,0.0291579554424169,0.0267354714709012,0.0258251022756496,0.0263239864844408,0.0267768563066355,0.0283973988470770,0.0276718054865411,0.0268866623192431,0.0288025233817394,0.0291575232237209,0.0297396527869108,0.0308737093043701,0.0298659446659934,0.0295454446473768,0.0297403376591612,0.0302905612651937,0.0285953478839291,0.0292274434293825,0.0300134890085395,0.0292845845709510,0.0306614516627460,0.0298205730684662,0.0285497770861443,0.0295506599925352,0.0281844287363795,0.0288543939397926,0.0310690131358284,0.0336507826948901,0.0329088562573208,0.0310024251898564,0.0273721687278607,0.0206883728546138,0.0174358214900456,0.0149352834435205,0.0132411506724365,0.0118188793776957,0.00980390991902892,0.00914900381663653,0.00859002201004584,0.00898869924424342,0.00920940209101823,0.00912369983130121,0.00902406622579126,0.00875707773162630,0.00855513284998416,0.00923870878011453,0.00897833495581823,0.00872380867234617,0.00810155016552062,0.00752299269454631,0.00897212264472318,0.00979108015330753,0.00999757579529674,0.0104990432547374,0.0100902443517125,0.00926759149352525,0.00929747652599521,0.00998328977647368,0.00940260153204779,0.00952419555024077,0.00980159910303441,0.00845981547141407,0.00821326473886309,0.00867825993620469,0.00896396531871810,0.0100995462850226,0.0104550581474136,0.0110646586603491,0.0114032808517004,0.0108283557064355,0.0111292627749644,0.0101425926604272,0.0106925026925663,0.0125325441250533,0.0147376491750584,0.0175564160649104,0.0189464573776579,0.0207675765051789,0.0198883676412193,0.0193477982684944,0.0178955874549272,0.0164411142380335,0.0168473059031049,0.0177420756783334,0.0177197163092977,0.0157918456943791,0.0139674326709561,0.0120219929702783,0.0115445686511790,0.0113442814596039,0.0115560331564922,0.0107155902506396,0.0108527467152260,0.0104671996047156,0.0111189228192775,0.0111856218905603,0.0119559411159808,0.0129793658661259,0.0131807885390841,0.0129949471005593,0.0122244700223835,0.0108697807992772,0.00990243230551103,0.0104234536894686,0.0111241555407106,0.0120819189596273,0.0121897393882314,0.0116062604952639,0.000132204434372150,0.000132204434372150,0.000132204434372150,0.000156929646818751,0.000250539441560939,0.000772898465631119,0.000772898465631119,0.000772898465631119,0.000772898465631119,0.000162004456503685,0.000147236875530534,0.000173912164377051,0.000195494269645661,0.000195494269645661,0.000195494269645661,0.000195494269645661,0.000132059631472223,0.000195562718372254,0.000195562718372254,0.000195562718372254,0.000195562718372254,0.000182844508291310,0.000182844508291310,0.000182844508291310,0.000180342262669696,0.000161408843264671,0.000142494292498577,0.000142494292498577,0.000142494292498577,0.000187222779182256,0.000187222779182256,0.000187222779182256,0.000187222779182256,0.000161344552666893,0.000161344552666893,0.000161344552666893,0.000161344552666893,0.000116483357208081,0.000131665159740860,0.00177401870244730,0.00177401870244730,0.00177401870244730,0.00177401870244730,0.00177401870244730,0.000823415077773923,0.000823415077773923,0.000823415077773923,0.000624590558649560,0.000624590558649560,0.000524580311607577,0.000513975244973988,0.000513975244973988,0.000579480629381069,0.000579480629381069,0.000808285924454843,0.000808285924454843,0.000808285924454843,0.000808285924454843,0.000808285924454843,0.000618688285711665,0.000538460901082080,0.000538460901082080,0.000538460901082080,0.000325616454798276,0.000557544050420743,0.000557544050420743,0.000557544050420743,0.000557544050420743,0.000459393621120039,0.000459393621120039,0.000459393621120039,0.000459393621120039,0.000459393621120039,0.000441968762556705,0.000441968762556705,0.000435330235674112,0.000435330235674112,0.000408041422335725,0.000408041422335725,0.000561699022003173,0.000561699022003173,0.000819832828042621,0.000819832828042621,0.000819832828042621,0.000819832828042621,0.000819832828042621,0.000274588883383643,0.000136938188369550,0.000167486344492878,0.000185833819306258,0.000185833819306258,0.000185833819306258,0.000185833819306258,0.000162400614776263,0.000140501532579573,0.000140501532579573,0.000146641925157360,0.000146641925157360,0.000146641925157360,0.000146641925157360,0.000146641925157360,0.000130740748829453,0.000130343598834835,0.000130343598834835,0.000188936225295011,0.000188936225295011,0.000188936225295011,0.000188936225295011,0.000180373914135461,0.000180373914135461,0.000131182794361552,0.000131182794361552,0.000185701603068927,0.000185701603068927,0.000185701603068927,0.000185701603068927,0.000156945433260711,0.000156945433260711,0.000156945433260711,0.000156945433260711,0.000137230513203767,0.000196288676681102,0.000372359202965862,0.000372359202965862,0.000372359202965862,0.000372359202965862,0.000372359202965862,0.000343866292984265,0.000343866292984265,0.000343866292984265,0.000316211749439250,0.000316211749439250,0.000282880609853410,0.000282880609853410,0.000263878708050246,0.000269051124639564,0.000269051124639564,0.000269051124639564,0.000352804862022374,0.000352804862022374,0.000352804862022374,0.000352804862022374,0.000295277289040087,0.000295277289040087,0.000295277289040087,0.000295277289040087,0.000313881603069394,0.000313881603069394,0.000313881603069394,0.000313881603069394,0.000339742493689195,0.000339742493689195,0.000339742493689195,0.000339742493689195,0.000305410324720637,0.000305410324720637,0.000305410324720637,0.000305410324720637,0.000293995160336967,0.000293995160336967,0.000293995160336967,0.000293995160336967,0.000220190127927924,0.000220190127927924,0.000243603102769626,0.000243603102769626,0.000243603102769626,0.000243603102769626,0.000243603102769626,0.000205101725252758,0.000205101725252758,0.000294062013459438,0.000294062013459438,0.000312908046415872,0.000312908046415872,0.000312908046415872,0.000312908046415872,0.000284669233757549,0.000171148162779232,0.000116355363073441,0.000116355363073441,0.000126439345044986,0.000144127416837497,0.000156928379611286,0.000156928379611286,0.000156928379611286,0.000156928379611286,0.000144402812400129,0.000144402812400129,0.000144402812400129,0.000207113683954387,0.000207113683954387,0.000207113683954387,0.000207113683954387,0.000122286681221858,0.000142435390768985,0.000142435390768985,0.000142435390768985,0.000172271083749369,0.000172271083749369,0.000172271083749369,0.000172271083749369,0.000167526786144422,0.000167526786144422,0.000167526786144422,0.000240721849216953,0.00255498301828808,0.00255498301828808,0.00255498301828808,0.00255498301828808,0.00255498301828808,0.000900831566643133,0.000677368009852099,0.000547905086448024,0.000771239264433819,0.000771239264433819,0.000771239264433819,0.000771239264433819,0.000395183315635943,0.000395183315635943,0.000426972212122686,0.000426972212122686,0.000496691589729962,0.000496691589729962,0.000496691589729962,0.000496691589729962,0.000496691589729962,0.000363574012022553,0.000410425116284989,0.000410425116284989,0.000428827077412025,0.000428827077412025,0.000428827077412025,0.000436836647436049,0.000436836647436049,0.000436836647436049,0.000436836647436049,0.000384509434007044,0.000481424431988987,0.000481424431988987,0.000481424431988987,0.000506661895101422,0.000506661895101422,0.000506661895101422,0.000506661895101422,0.000383404456398250,0.000399427149995078,0.000604621778479372,0.000604621778479372,0.000604621778479372,0.000604621778479372,0.000440645560374124,0.000219280111998024,0.000219280111998024,0.000181465465288277,0.000167852893085166,0.000167852893085166,0.000148707670222534,0.000166000789214711,0.000166000789214711,0.000166000789214711,0.000166000789214711,0.000148778215351706,0.000148778215351706,0.000167556467909660,0.000167556467909660,0.000167556467909660,0.000167556467909660,0.000166204041928219,0.000166204041928219,0.000160595978428978,0.000160595978428978,0.000158951940507755,0.000141571281549749,0.000141571281549749,0.000127542310991365,0.000113146970228246,0.000147715631209208,0.000147715631209208,0.000147715631209208,0.000149558746292101,0.000149558746292101,0.000149558746292101,0.000149558746292101,0.000134329788221902,0.000134329788221902,0.000133090394969050,0.000133090394969050,0.000196642324490691,0.000196642324490691,0.00244956309456404,0.00244956309456404,0.00244956309456404,0.00244956309456404,0.00103764554807829,0.000447089758895124,0.000453403896801626,0.000453403896801626,0.000453403896801626,0.000453403896801626,0.000395604456554315,0.000395604456554315,0.000395604456554315,0.000338665289612753,0.000330851083413235,0.000330851083413235,0.000330851083413235,0.000551579328335799,0.000551579328335799,0.000551579328335799,0.000551579328335799,0.000629248607843111,0.000629248607843111,0.000629248607843111,0.000629248607843111,0.000503253549423572,0.000503253549423572,0.000441074870075926,0.000441074870075926,0.000384633507431867,0.000370993574809855,0.000370993574809855,0.000376699576691158,0.000376699576691158,0.000376699576691158,0.000487455253697446,0.000487455253697446,0.000487455253697446,0.000487455253697446,0.000351457025184423,0.000390455357774374,0.000486516945578278,0.000486516945578278,0.000486516945578278,0.000486516945578278,0.000486516945578278,0.000150097861982446,0.000121507686328654,0.000121507686328654,0.000121507686328654,0.000121507686328654,0.000379277998226421,0.000379277998226421,0.000379277998226421,0.000379277998226421,0.000343726613736783,0.000233556583978766,0.000144610249419966,0.000166230186710199,0.000166230186710199,0.000196873472747675,0.000196873472747675,0.000196873472747675,0.000196873472747675,0.000109183680579155,0.000167100064399842,0.000167100064399842,0.000167100064399842,0.000167100064399842,0.000167100064399842,0.000159991472922591,0.000159991472922591,0.000159991472922591,0.000159991472922591,0.000118118010068440,0.000112042748122071,0.000138828384991522,0.000138828384991522,0.000138828384991522,0.000138828384991522,0.000119400052969258,0.000119400052969258,0.000122041444175614,0.000166241515801457,0.000322259278827526,0.000322259278827526,0.000322259278827526,0.000322259278827526,0.000315726662078306,0.000280111312573032,0.000280111312573032,0.000280111312573032,0.000280111312573032,0.000203693041945812,0.000203693041945812,0.000203693041945812,0.000203693041945812,0.000189070876001945,0.000189070876001945,0.000320616449100937,0.000320616449100937,0.000320616449100937,0.000320616449100937,0.000320616449100937,0.000215189465402838,0.000215189465402838,0.000215189465402838,0.000206727563678676,0.000206727563678676,0.000206727563678676,0.000221395390577457,0.000221395390577457,0.000221395390577457,0.000221395390577457,0.000205071904091702,0.000206905521715182,0.000206905521715182,0.000217973645210624,0.000277592744986523,0.000277592744986523,0.000277592744986523,0.000277592744986523,0.000182401650629823,0.000182401650629823,0.000182401650629823,0.000245106188022987,0.000357428213206320,0.000357428213206320,0.000357428213206320,0.000357428213206320,0.000156332598645412,0.000156332598645412,0.000221202843080220,0.000221202843080220,0.000221202843080220,0.000221202843080220,0.000176526862777372,0.000143956052610875,0.000143956052610875,0.000143956052610875,0.000133941978249791,0.000137920486199721,0.000137920486199721,0.000137920486199721,0.000137920486199721,0.000137920486199721,0.000187351329915654,0.000187351329915654,0.000187351329915654,0.000187351329915654,0.000131730281972699,0.000258876654282903,0.000258876654282903,0.000258876654282903,0.000258876654282903,0.000172135549278173,0.000172135549278173,0.000153349947749644,0.000153349947749644,0.000153349947749644,0.000153349947749644,0.000128181108907272,0.000128181108907272,0.000128181108907272,0.000146992439337026,0.000146992439337026,0.000168233050666840,0.000168233050666840,0.000168233050666840,0.000168233050666840,0.000175106371706167,0.000199385421667037,0.000277539889257259,0.000277539889257259,0.000277539889257259,0.000277539889257259,0.000277539889257259,0.000215691801281080,0.000239610193850124,0.000239610193850124,0.000239610193850124,0.000239610193850124,0.000160485934329580,0.000160485934329580,0.000159843119861231,0.000159843119861231,0.000159843119861231,0.000130173044257970,0.000130173044257970,0.000130173044257970,0.000161145956531070,0.000161145956531070,0.000161145956531070,0.000177692127173590,0.000177692127173590,0.000345135715733914,0.000345135715733914,0.000345135715733914,0.000345135715733914,0.000247593754358668,0.000179946003031068,0.000179946003031068,0.000274558927009686,0.000274558927009686,0.000274558927009686,0.000274558927009686,0.000153244796535278,0.000169997250161541,0.000169997250161541,0.000191416636244081,0.000191416636244081,0.000356669630866684,0.000356669630866684,0.000356669630866684,0.000356669630866684,0.000228678505953545,0.000228678505953545,0.000228678505953545,0.000215317647613760,0.000215317647613760,0.000163970358295857,0.000163970358295857,0.000163970358295857,0.000237586645497767,0.000256705924809941,0.000256705924809941,0.000256705924809941,0.000264787884489296,0.000264787884489296,0.000264787884489296,0.000264787884489296,0.000264787884489296,0.000137045571952675,0.000161756591660568,0.000163911457043616,0.000188002390466915,0.000188002390466915,0.000188002390466915,0.000188002390466915,0.000145703275545084,0.000131078309985075,0.000131078309985075,0.000131078309985075,0.000222950392527948,0.000222950392527948,0.000424967360102604,0.00182047724217753,0.00182047724217753,0.00188146508990658,0.00188146508990658,0.00188146508990658,0.00188146508990658,0.00188146508990658,0.00184551199975635,0.00150854644611955,0.00150854644611955,0.00133763516182443,0.00133763516182443,0.00133763516182443,0.00133763516182443,0.00133034988540430,0.00129841648397379,0.00129841648397379,0.00161186853687519,0.00161186853687519,0.00161186853687519,0.00173791883590608,0.00176441927197377,0.00176441927197377,0.00176441927197377,0.00176441927197377,0.00122784763600954,0.00122784763600954,0.00113031955403712,0.00113031955403712,0.00109796249282733,0.00109796249282733,0.00102135312987327,0.00102135312987327,0.00102135312987327,0.00102135312987327,0.00107440463409472,0.00107440463409472,0.00107440463409472,0.00107440463409472,0.00124705711146335,0.00135933505843825,0.00135933505843825,0.00135933505843825,0.00135933505843825,0.000666318726321670,0.000577344575826282,0.000391305316860652,0.000196040922109932,0.000196040922109932,0.000195725811371966,0.000195725811371966,0.000195725811371966,0.000193993034672879,0.000193993034672879,0.000193993034672879,0.000202015527313909,0.000202015527313909,0.000209891747507265,0.000209891747507265,0.000247602581800945,0.000247602581800945,0.000247602581800945,0.000247602581800945,0.000160362267236138,0.000215130563021661,0.000215130563021661,0.000215130563021661,0.000215130563021661,0.000215130563021661,0.000189891289715862,0.000189891289715862,0.000189891289715862,0.000223985309825166,0.000223985309825166,0.000223985309825166,0.000223985309825166,0.000170860893888717,0.000170860893888717,0.000197052769977960,0.000197052769977960,0.000197052769977960,0.000197052769977960,0.000171732887120143,0.000204000160115141,0.000370400738252401,0.000370400738252401,0.000370400738252401,0.000370400738252401,0.000296108440123796,0.000296108440123796,0.000276478832190071,0.000336629840135726,0.000336629840135726,0.000336629840135726,0.000341811476927887,0.000341811476927887,0.000341811476927887,0.000363633721941423,0.000363633721941423,0.000363633721941423,0.000363633721941423,0.000286769173886238,0.000286769173886238,0.000258767168474476,0.000266115692275457,0.000266115692275457,0.000266115692275457,0.000266115692275457,0.000266115692275457,0.000223089989364718,0.000267466185170823,0.000267466185170823,0.000267466185170823,0.000267466185170823,0.000235061718177208,0.000235061718177208,0.000200944386942538,0.000242172359446123,0.000242172359446123,0.000242172359446123,0.000242172359446123,0.000235046240180617,0.000235046240180617,0.000235046240180617,0.000211869783022871,0.000208379340001315,0.000266186545539925,0.000266186545539925,0.000266186545539925,0.000266186545539925,0.000266186545539925,0.000216723538467937,0.000216723538467937,0.000268755754963121,0.000268755754963121,0.000268755754963121,0.000268755754963121,0.000236733794555572,0.000236733794555572,0.000236733794555572,0.000236733794555572,0.000217218694661047,0.000217218694661047,0.000217218694661047,0.000191401934512220,0.000191401934512220,0.000177823854621706,0.000177823854621706,0.000168822793045282,0.000117184089341657,0.000137192940905687,0.000137192940905687,0.000137192940905687,0.000137192940905687,0.000168151289497301,0.000168151289497301,0.000168151289497301,0.000168151289497301,0.000166847895240418,0.000202964801154451,0.000214274792386299,0.000214274792386299,0.000214274792386299,0.000214274792386299,0.000197377114727512,0.000197377114727512,0.000262678785010967,0.000634744398316751,0.000634744398316751,0.000634744398316751,0.000634744398316751,0.000345427361764640,0.000345427361764640,0.000345427361764640,0.000345427361764640,0.000263991550848528,0.000263991550848528,0.000263991550848528,0.000239823451105340,0.000239823451105340,0.000257929463786382,0.000257929463786382,0.000257929463786382,0.000257929463786382,0.000249691181638405,0.000185197279375595,0.000293722548143750,0.000293722548143750,0.000293722548143750,0.000293722548143750,0.000243517952682167,0.000243517952682167,0.000201107706543073,0.000201107706543073,0.000195102373256018,0.000201850155078268,0.000201850155078268,0.000201850155078268,0.000208967509923854,0.000208967509923854,0.000208967509923854,0.000281518523181559,0.000281518523181559,0.000290317260513588,0.000290317260513588,0.000290317260513588,0.000290317260513588,0.000344276341841385,0.000344276341841385,0.000344276341841385,0.000344276341841385,0.000294475902556511,0.000255136278699906,0.000255136278699906,0.000167180165027689,0.000197477131101684,0.000261433429551142,0.000261433429551142,0.000261433429551142,0.000261433429551142,0.000167081502927295,0.000167081502927295,0.000159715595067510,0.000160341225481146,0.000160341225481146,0.000177791973601361,0.000177791973601361,0.000197086868304849,0.000197086868304849,0.000224552014089572,0.000224552014089572,0.000224552014089572,0.000224552014089572,0.000221074805040595,0.000221074805040595,0.000200721710919180,0.000200721710919180,0.000200264185915634,0.000200264185915634,0.000273235215566103,0.000273235215566103,0.000273235215566103,0.000273235215566103,0.000158242519540980,0.000240079881443304,0.000240079881443304,0.000240079881443304,0.000240079881443304,0.000240079881443304,0.000193742591753829,0.000697264414944152,0.000697264414944152,0.000697264414944152,0.000697264414944152,0.000693005670227389,0.000378736973929409,0.000396290056243370,0.000396290056243370,0.000396290056243370,0.000396290056243370,0.000327484248197933,0.000327484248197933,0.000327484248197933,0.000327484248197933,0.000267899454547370,0.000267899454547370,0.000328285040256915,0.000328285040256915,0.000328285040256915,0.000328285040256915,0.000353184107942715,0.000353184107942715,0.000353184107942715,0.000353184107942715,0.000356391939340411,0.000356391939340411,0.000356391939340411,0.000356391939340411,0.000249178797407402,0.000249178797407402,0.000420005206166445,0.000420005206166445,0.000420005206166445,0.000420005206166445,0.000267872034606952,0.000302822494198213,0.000413781354511475,0.000413781354511475,0.000413781354511475,0.000413781354511475,0.000413781354511475,0.000314657902767633,0.000376957321523903,0.000376957321523903,0.000376957321523903,0.000376957321523903,0.000218624121785668,0.000178134846022093,0.000178134846022093,0.000166617143006778,0.000166617143006778,0.000326484435322084,0.000326484435322084,0.000326484435322084,0.000326484435322084,0.000205922015739195,0.000205922015739195,0.000124898045472649,0.000124898045472649,0.000256900331444018,0.000256900331444018,0.000256900331444018,0.000256900331444018,0.000256900331444018,0.000224468563009232,0.000224468563009232,0.000224468563009232,0.000268274883708868,0.000268274883708868,0.000268274883708868,0.000268274883708868,0.000195724371343504,0.000162807697810495,0.000162807697810495,0.000191533740182616,0.000191533740182616,0.000191533740182616,0.000191533740182616,0.000171169193450755,0.000171169193450755,0.000230337397511533,0.000230337397511533,0.000230337397511533,0.000230337397511533,0.000179065491285212,0.000256883661032065,0.000256883661032065,0.000319194556673944,0.000319194556673944,0.000319194556673944,0.000319194556673944,0.000319194556673944,0.000237137592664090,0.000237137592664090,0.000269402795634012,0.000269402795634012,0.000269402795634012,0.000269402795634012,0.000266401144438902,0.000266401144438902,0.000266401144438902,0.000266401144438902,0.000266401144438902,0.000191165776360582,0.000191165776360582,0.000191165776360582,0.000282226631393975,0.000282226631393975,0.000282226631393975,0.000282226631393975,0.000280288255617808,0.000280288255617808,0.000280288255617808,0.000280288255617808,0.000204624168018365,0.000204624168018365,0.000262414564372744,0.000262414564372744,0.000262414564372744,0.000262414564372744,0.000262414564372744,0.000210128685842346,0.000210128685842346,0.000192701404998586,0.000205471479518181,0.000205471479518181,0.000205471479518181,0.000309698613652060,0.000309698613652060,0.000309698613652060,0.000309698613652060,0.000220090138672851,0.000220090138672851,0.000220090138672851,0.000206204204610946,0.000206204204610946,0.000206204204610946,0.000206204204610946,0.000196335998544564,0.000196335998544564,0.000162910104984588,0.000256130993000621,0.000256130993000621,0.000256130993000621,0.000256130993000621,0.000245876404921035,0.000245876404921035,0.000245876404921035,0.000245876404921035,0.000131922515593567,0.000225498724837092,0.000225498724837092,0.000225498724837092,0.000225498724837092,0.000183991485630486,0.000177935480467988,0.000154171285479921,0.000135589333948666,0.000135589333948666,0.000208733102392881,0.000208733102392881,0.000208733102392881,0.000208733102392881,0.000170604030263317,0.000170604030263317,0.000214936360962971,0.000214936360962971,0.000214936360962971,0.000214936360962971,0.000172005406223003,0.000641976685742742,0.000711945547742023,0.000726714291508910,0.00121837028878646,0.00121837028878646,0.00121837028878646,0.00121837028878646,0.000885597317334139,0.000629530609735211,0.000519155435991457,0.000438258765240159,0.000438258765240159,0.000438258765240159,0.000345207681161394,0.000345207681161394,0.000382719461534589,0.000382719461534589,0.000382719461534589,0.000382719461534589,0.000382655426049328,0.000392075618201277,0.000392075618201277,0.000409933919437630,0.000409933919437630,0.000419588789000855,0.000467977971531927,0.000467977971531927,0.000467977971531927,0.000467977971531927,0.000410931929335069,0.000410931929335069,0.000410931929335069,0.000410931929335069,0.000430159328335065,0.000430159328335065,0.000430159328335065,0.000106091816190671,0.000106091816190671,0.000106091816190671,0.000106091816190671,0.000156313132324468,0.000252996366007153,0.000252996366007153,0.000252996366007153,0.000252996366007153,0.000109814519463472,7.98323474205813e-05,7.98323474205813e-05,7.98323474205813e-05,7.98323474205813e-05,6.95912880776046e-05,0.000109041404944915,0.000109041404944915,0.000109041404944915,0.000109041404944915,0.000100927454913183,0.000112240433667406,0.000112240433667406,0.000112240433667406,0.000112240433667406,8.57987077384127e-05,8.57987077384127e-05,8.57987077384127e-05,6.90590831669400e-05,6.90590831669400e-05,7.43968055798795e-05,7.43968055798795e-05,8.24774964013258e-05,8.24774964013258e-05,8.24774964013258e-05,8.24774964013258e-05,7.32573314965188e-05,6.54611136817046e-05,8.79033845011730e-05,8.79033845011730e-05,0.00114525079266516,0.00187889612808482,0.00187889612808482,0.00187889612808482,0.00187889612808482,0.00187431830853258,0.00187431830853258,0.00169301073362883,0.00159509067580655,0.000918052257688874,0.000949794739639054,0.00148609513892468,0.00148609513892468,0.00148609513892468,0.00148609513892468,0.000964697097237723,0.00100751517679238,0.00100751517679238,0.00100751517679238,0.00100751517679238,0.000965207697829336,0.000965207697829336,0.000889174328578704,0.000883204030453660,0.000883204030453660,0.000883204030453660,0.000694036132547989,0.000694036132547989,0.000694036132547989,0.000688848205569265,0.000837517589954760,0.000837517589954760,0.000837517589954760,0.000837517589954760,0.00113945540539006,0.00113945540539006,0.00113945540539006,0.00113945540539006,0.000802257379945219,0.000802257379945219,0.000802257379945219,0.00100027317263697,0.00100027317263697,0.00100027317263697,0.00100027317263697,0.00100027317263697,0.000681023104509551,0.000180943179416262,0.000180943179416262,0.000153947031896130,0.000153947031896130,0.000153947031896130,9.21904986550641e-05,0.000131798510138954,0.000131798510138954,0.000131798510138954,0.000131798510138954,0.000131798510138954,0.000109216139780994,0.000109216139780994,0.000109216139780994,0.000105314206671072,0.000107951754820692,0.000107951754820692,0.000112132116650897,0.000172957647769906,0.000172957647769906,0.000172957647769906,0.000195119624192212,0.000195119624192212,0.000195119624192212,0.000195119624192212,0.000171016016466599,0.000171016016466599,0.000176519896705602,0.000176519896705602,0.000207425662842690,0.000207425662842690,0.000207425662842690,0.000207425662842690,0.000193720891105584,0.000133540090469971,0.000654522438565462,0.00136856562793423,0.00136856562793423,0.00136856562793423,0.00136856562793423,0.00104892053447019,0.00104892053447019,0.000857869729285886,0.000981032247967673,0.000981032247967673,0.00104008769188652,0.00104008769188652,0.00104008769188652,0.00104008769188652,0.00104008769188652,0.000876646294937240,0.000876646294937240,0.000876646294937240,0.000876561616815253,0.000876561616815253,0.000876561616815253,0.000915660728975255,0.000915660728975255,0.000915660728975255,0.000915660728975255,0.000793245896952454,0.00104790137655183,0.00104790137655183,0.00104790137655183,0.00104790137655183,0.000894143511618747,0.000752321348721462,0.000752321348721462,0.000955807808761150,0.000955807808761150,0.000955807808761150,0.000955807808761150,0.000804004427486810,0.000742241182644680,0.000957185085516132,0.000957185085516132,0.000957185085516132,0.000957185085516132,0.000957185085516132,0.000666946343917773,0.000659789644053433,0.000659789644053433,0.000652561411861910,0.000652561411861910,0.000417007299485213,0.000167100463159989,0.000167100463159989,0.000167100463159989,0.000225513362432687,0.000225513362432687,0.000225513362432687,0.000225513362432687,0.000186983095296728,0.000186983095296728,0.000100220274995081,9.02467956395160e-05,9.02467956395160e-05,0.000105687552468558,0.000105687552468558,0.000105687552468558,0.000105687552468558,7.44482337962918e-05,9.19327825598315e-05,9.85840722751851e-05,9.85840722751851e-05,9.85840722751851e-05,9.85840722751851e-05,8.24383619249634e-05,0.000120885015288063,0.000120885015288063,0.000120885015288063,0.000120885015288063,0.000120885015288063,0.000121017539504900,0.000121017539504900,0.000121017539504900,0.000121017539504900,0.000106888218038058,0.000106888218038058,9.72681469756782e-05,0.000931143858970395,0.00116902306623300,0.00116902306623300,0.00116902306623300,0.00116902306623300,0.000845518246376398,0.000845518246376398,0.000797399345947048,0.000536624727687096,0.000536624727687096,0.000531546089365903,0.000531546089365903,0.000580988330563396,0.000628336262270153,0.000628336262270153,0.000628336262270153,0.000628336262270153,0.000553197112307612,0.000479153179610513,0.000441297296812282,0.000441297296812282,0.000560460715647798,0.000560460715647798,0.000560460715647798,0.000560460715647798,0.000537298961166559,0.000537298961166559,0.000537298961166559,0.000500525047941632,0.000579984484607522,0.000579984484607522,0.000579984484607522,0.000579984484607522,0.000532377011082101,0.000499864535497406,0.000484032176248856,0.000474300441859400,0.000474300441859400,0.000428643427330212,0.000578101914441931,0.000578101914441931,0.000578101914441931,0.000578101914441931,0.000578101914441931,0.000569533472953817,0.000336970924431566,0.000230963892564957,0.000230963892564957,0.000181696976161663,0.000206916617483700,0.000268299878611172,0.000268299878611172,0.000268299878611172,0.000268299878611172,0.000213296172226017,0.000113393036255803,0.000159671544305074,0.000159671544305074,0.000159671544305074,0.000159671544305074,0.000119869270495146,0.000119869270495146,0.000125225079230415,0.000125225079230415,0.000125225079230415,0.000125225079230415,0.000187961819113122,0.000227357520204043,0.000227357520204043,0.000227357520204043,0.000231829460704622,0.000231829460704622,0.000231829460704622,0.000231829460704622,9.85456677568383e-05,0.000120106360110843,0.000120106360110843,0.000120106360110843,0.000135558888247641,0.000261795714220186,0.000261795714220186,0.000261795714220186,0.000261795714220186,0.000342356104470318,0.00150084681660296,0.00150084681660296,0.00150084681660296,0.00150084681660296,0.00105562772421621,0.000327760667399059,0.000388894277477953,0.000388894277477953,0.000388894277477953,0.000388894277477953,0.000272829895126042,0.000313787912651071,0.000313787912651071,0.000313787912651071,0.000313787912651071,0.000237986738035465,0.000301950613464310,0.000301950613464310,0.000301950613464310,0.000301950613464310,0.000305333548491874,0.000305333548491874,0.000373846873621025,0.000373846873621025,0.000373846873621025,0.000373846873621025,0.000338666617270228,0.000390649034809392,0.000466684770414343,0.000466684770414343,0.000466684770414343,0.000466684770414343,0.000230558093391714,0.000230558093391714,0.000243368412837114,0.000273518857805114,0.000273518857805114,0.000273518857805114,0.000273518857805114,0.000253087131811644,0.000246567689230380,0.000246567689230380,0.000302730424850784,0.000302730424850784,0.000302730424850784,0.000302730424850784,0.000109950941859575,0.000109950941859575,0.000109719630952497,0.000109719630952497,0.000105597521827659,0.000105597521827659,0.000124532435794885,0.000124532435794885,0.000124532435794885,0.000124532435794885,9.86997140713897e-05,9.86997140713897e-05,9.86997140713897e-05,9.86997140713897e-05,6.45956102683530e-05,0.000107343575250868,0.000107343575250868,0.000107343575250868,0.000107343575250868,0.000107343575250868,7.30235954348101e-05,9.08658352370448e-05,9.08658352370448e-05,9.08658352370448e-05,9.08658352370448e-05,7.84128871494561e-05,8.71995039221033e-05,8.71995039221033e-05,8.71995039221033e-05,8.71995039221033e-05,0.000113731484390817,0.000113731484390817,0.000113731484390817,0.000113731484390817,9.32826948227536e-05,9.32826948227536e-05,0.000117747680311239,0.00169872988286316,0.00169872988286316,0.00169872988286316,0.00169872988286316,0.00169872988286316,0.000663403535200735,0.000590147521845433,0.000367258385805312,0.000512258113147410,0.000512258113147410,0.000512258113147410,0.000512258113147410,0.000468024272074138,0.000503653475027462,0.000503653475027462,0.000503653475027462,0.000503653475027462,0.000321108057192067,0.000321108057192067,0.000418876282112208,0.000418876282112208,0.000418876282112208,0.000418876282112208,0.000471276338666546,0.000471276338666546,0.000471276338666546,0.000471276338666546,0.000406711599563434,0.000402056469022521,0.000402056469022521,0.000402056469022521,0.000402056469022521,0.000402056469022521,0.000388649438950464,0.000383996774839488,0.000588635976859481,0.000588635976859481,0.000588635976859481,0.000588635976859481,0.000349947923538113,0.000365874561557395,0.000418366309385575,0.000760867785921209,0.000760867785921209,0.000760867785921209,0.000760867785921209,0.000760867785921209,0.000221218817703128,0.000221218817703128,0.000174351236991837,0.000148186655684676,0.000148186655684676,0.000145163099689034,8.36105795222680e-05,0.000120967775067493,0.000120967775067493,0.000120967775067493,0.000120967775067493,9.42893240142434e-05,0.000106411842581023,0.000106411842581023,0.000106411842581023,0.000106411842581023,0.000106411842581023,7.50004878237827e-05,0.000140119333378652,0.000140119333378652,0.000140119333378652,0.000140119333378652,0.000140119333378652,9.31939314713540e-05,8.59428116843036e-05,0.000122176431838171,0.000122176431838171,0.000122176431838171,0.000122176431838171,8.80554494438066e-05,8.80554494438066e-05,9.79415610733125e-05,0.000110855658609077,0.000110855658609077,0.000110855658609077,0.000110855658609077,0.000113136527042421,0.000113136527042421,0.000113136527042421,0.000113136527042421,0.000254122738215003,0.000254122738215003,0.000254122738215003,0.000254122738215003,0.000254122738215003,0.000290272535419584,0.000290272535419584,0.000290272535419584,0.000290272535419584,0.000211927912348367,0.000211927912348367,0.000260334488769031,0.000260334488769031,0.000260334488769031,0.000260334488769031,0.000117377142278029,0.000131570257047786,0.000131570257047786,0.000131570257047786,0.000131570257047786,0.000129201171237385,0.000129201171237385,0.000129201171237385,0.000129201171237385,0.000131013315428222,0.000131013315428222,0.000131013315428222,0.000131013315428222,0.000101074444471279,0.000101074444471279,0.000102423176654043,0.000102423176654043,0.000102423176654043,0.000121035108551898,0.000121035108551898,0.000121035108551898,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3};

    private boolean mUseCustomParams = false;

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
                if(DEFAULT_KNN_PARAMS.length==4960) Y =  jClassifyUsingKNN(concat, DEFAULT_KNN_PARAMS);
                else if (DEFAULT_KNN_PARAMS.length==9920) Y = jClassifyUsingKNNv4(concat, DEFAULT_KNN_PARAMS, 1);
            }
            processClassifiedData(Y);
        }
    };

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
                exportFileWithClass(bytesToDouble(ch1[3 * i], ch1[3 * i + 1], ch1[3 * i + 2]),
                        bytesToDouble(ch2[3 * i], ch2[3 * i + 1], ch2[3 * i + 2]),
                        bytesToDouble(ch3[3 * i], ch3[3 * i + 1], ch3[3 * i + 2]));
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
        System.loadLibrary("emg-lib");
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
