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
import com.androidplot.xy.SimpleXYSeries;
import com.beele.BluetoothLe;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Doubles;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
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
    private BluetoothGattService mLedService = null;
    private int mWheelchairGattIndex;

    private boolean mEEGConnected = false;

    //Layout - TextViews and Buttons
//    private TextView mBatteryLevel;
    private TextView mDataRate;
    private TextView mSSVEPClassTextView;
    private TextView mYfitTextView;
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
    private int batteryWarning = 20;//
    private String fileTimeStamp = "";
    private double dataRate;
    private double mEMGClass = 0;
    private int mLastButtonPress = 0;

    //Classification
    private boolean mWheelchairControl = false; //Default classifier.

    //Play Sound:
    MediaPlayer mMediaBeep;

    //Bluetooth Classic - For Robotic Hand
    Handler mHandler;
    final int RECIEVE_MESSAGE = 1;        // Status  for Handler
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder sb = new StringBuilder();
    private static int flag = 0;

    private ConnectedThread mConnectedThread;

    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // MAC-address of Bluetooth module (you must edit this line)
    private static String address = "30:14:12:17:21:88";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);
        //Set orientation of device based on screen type/size:
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //Recieve Intents:
        Intent intent = getIntent();
        deviceMacAddresses = intent.getStringArrayExtra(MainActivity.INTENT_DEVICES_KEY);
        String[] deviceDisplayNames = intent.getStringArrayExtra(MainActivity.INTENT_DEVICES_NAMES);
        String[] intentDelayLength = intent.getStringArrayExtra(MainActivity.INTENT_DELAY_LENGTH);
        mSecondsBetweenStimulus = Integer.valueOf(intentDelayLength[0]);
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
//        mBatteryLevel = (TextView) findViewById(R.id.batteryText);
        mDataRate = (TextView) findViewById(R.id.dataRate);
        mDataRate.setText("...");
        //Initialize Bluetooth
        ActionBar ab = getActionBar();
        ab.setTitle(mDeviceName);
        ab.setSubtitle(mDeviceAddress);
        initializeBluetoothArray();
        //Bluetooth Classic Stuff:
        mYfitTextView = (TextView) findViewById(R.id.classifierOutput);
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
        mGraphAdapterCh1 = new GraphAdapter(1000, "EMG Data Ch 1", false, false, Color.BLUE, 1500); //Color.parseColor("#19B52C") also, RED, BLUE, etc.
        mGraphAdapterCh2 = new GraphAdapter(1000, "EMG Data Ch 2", false, false, Color.RED, 1500); //Color.parseColor("#19B52C") also, RED, BLUE, etc.
        mGraphAdapterCh3 = new GraphAdapter(1000, "EMG Data Ch 3", false, false, Color.GREEN, 1500); //Color.parseColor("#19B52C") also, RED, BLUE, etc.
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
        mSSVEPClassTextView = (TextView) findViewById(R.id.eegClassTextView);
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
        Button b1 = (Button) findViewById(R.id.b1);
        Button b2 = (Button) findViewById(R.id.b2);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mConnectedThread!=null)
                    mConnectedThread.write(1);
                mEMGClass = 1;
            }
        });
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mConnectedThread!=null)
                    mConnectedThread.write(2);
                mEMGClass = 2;
            }
        });
        mMediaBeep = MediaPlayer.create(this, R.raw.beep_01a);
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
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
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

    private boolean fileSaveInitialized = false;
    private CSVWriter csvWriter;
    private File file;
    private File root;

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
        root = Environment.getExternalStorageDirectory();
        fileTimeStamp = "EMG_" + getTimeStamp();
        if (root.canWrite()) {
            File dir = new File(root.getAbsolutePath() + "/EMGData");
            dir.mkdirs();
            file = new File(dir, fileTimeStamp + ".csv");
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
                if (AppConstant.SERVICE_WHEELCHAIR_CONTROL.equals(service.getUuid())) {
                    mLedService = service;
                    Log.i(TAG, "BLE Wheelchair Control Service found");
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


    private int batteryLevel = -1;

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        Log.i(TAG, "onCharacteristicRead");
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (AppConstant.CHAR_BATTERY_LEVEL.equals(characteristic.getUuid())) {
                batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
//                updateBatteryStatus(batteryLevel, batteryLevel + " %");
                Log.i(TAG, "Battery Level :: " + batteryLevel);
            }
        } else {
            Log.e(TAG, "onCharacteristic Read Error" + status);
        }
    }

    private boolean eeg_ch1_data_on = false;
    private boolean eeg_ch2_data_on = false;
    private boolean eeg_ch3_data_on = false;
    private int packetNumber = -1;
    //Count of How Many Alerts Given To Subject
    private int mAlertBeepCounter = 1;
    private int mAlertBeepCounterSwitch = 1;
    private int mClassifierCounter = 0;
    private int mSecondsBetweenStimulus = 0;
    //EOG:
    // Classification
    private double[] yfitarray = new double[5];
    byte[] dataBytesCh1;
    byte[] dataBytesCh2;
    byte[] dataBytesCh3;
    private short packNumCh1 = 0;
    private short packNumCh2 = 0;
    private short packNumCh3 = 0;
    private int dataNumCh1 = 0;
    private int dataNumCh2 = 0;
    private int dataNumCh3 = 0;
    private byte[] bufferCh1;
    private byte[] bufferCh2;
    private byte[] bufferCh3;

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        //TODO: ADD BATTERY MEASURE CAPABILITY IN FIRMWARE: (ble_ADC)
        if (AppConstant.CHAR_EEG_CH1_SIGNAL.equals(characteristic.getUuid())) {
            dataBytesCh1 = characteristic.getValue();
            if (!eeg_ch1_data_on) {
                eeg_ch1_data_on = true;
            }
            getDataRateBytes(dataBytesCh1.length);
//            if(mEEGConnected) mGraphAdapterCh1.addDataPoints(dataEEGBytes,3,packetNumber);
            if(mEEGConnected) {
                if(bufferCh1!=null) {
                    //concatenate
                    bufferCh1 = Bytes.concat(bufferCh1, dataBytesCh1);
                } else {
                    //Init:
                    bufferCh1 = dataBytesCh1;
                }
                dataNumCh1+=dataBytesCh1.length/3;
                packNumCh1++;
                if(packNumCh1==10) {
                    for (int i = 0; i < bufferCh1.length/3; i++) {
                        mGraphAdapterCh1.addDataPoint(bytesToDouble(bufferCh1[3*i], bufferCh1[3*i+1], bufferCh1[3*i+2]),dataNumCh1-bufferCh1.length+i);
                    }
                    bufferCh1=null;
                    packNumCh1=0;
                }
            }
        }

        if (AppConstant.CHAR_EEG_CH2_SIGNAL.equals(characteristic.getUuid())) {
            if (!eeg_ch2_data_on) {
                eeg_ch2_data_on = true;
            }
            dataBytesCh2 = characteristic.getValue();
            int byteLength = dataBytesCh2.length;
            getDataRateBytes(byteLength);
            if(mEEGConnected) {
                if(bufferCh2!=null) {
                    //concatenate
                    bufferCh2 = Bytes.concat(bufferCh2, dataBytesCh2);
                } else {
                    //Init:
                    bufferCh2 = dataBytesCh2;
                }
                dataNumCh2+=dataBytesCh2.length/3;
                packNumCh2++;
                if(packNumCh2==10) {
                    for (int i = 0; i < bufferCh2.length/3; i++) {
                        mGraphAdapterCh2.addDataPoint(bytesToDouble(bufferCh2[3*i], bufferCh2[3*i+1], bufferCh2[3*i+2]),dataNumCh2-bufferCh2.length+i);
                    }
                    bufferCh2=null;
                    packNumCh2=0;
                }
            }
        }

        if (AppConstant.CHAR_EEG_CH3_SIGNAL.equals(characteristic.getUuid())) {
            if (!eeg_ch3_data_on) {
                eeg_ch3_data_on = true;
            }
            dataBytesCh3 = characteristic.getValue();
            int byteLength = dataBytesCh3.length;
            getDataRateBytes(byteLength);
            if(mEEGConnected) {
                if(bufferCh3!=null) {
                    //concatenate
                    bufferCh3 = Bytes.concat(bufferCh3, dataBytesCh3);
                } else {
                    //Init:
                    bufferCh3 = dataBytesCh3;
                }
                dataNumCh3+=dataBytesCh3.length/3;
                packNumCh3++;
                if(packNumCh3==10) {
                    for (int i = 0; i < bufferCh3.length/3; i++) {
                        mGraphAdapterCh3.addDataPoint(bytesToDouble(bufferCh3[3*i], bufferCh3[3*i+1], bufferCh3[3*i+2]),dataNumCh3-bufferCh3.length+i);
                    }
                    bufferCh3=null;
                    packNumCh3=0;
                }
            }
        }

        // TODO: 5/15/2017 2-Channel EEG:
        if (eeg_ch1_data_on && eeg_ch2_data_on && eeg_ch3_data_on) {
            packetNumber++;
            mEEGConnected = true;
            eeg_ch1_data_on = false; eeg_ch2_data_on = false; eeg_ch3_data_on = false;
            if (dataBytesCh3 != null && dataBytesCh2 != null && dataBytesCh1 != null)
                writeToDisk24(dataBytesCh1,dataBytesCh2,dataBytesCh3);

            if(packetNumber%5==0 && packetNumber>41) {
                ClassifyTask classifyTask = new ClassifyTask();
                Log.e(TAG,"["+String.valueOf(mNumberOfClassifierCalls+1)+"] CALLING CLASSIFIER FUNCTION!");
                mNumberOfClassifierCalls++;
                classifyTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
        if (mSecondsBetweenStimulus != 0) {
            if (Math.floor(0.004*dataNumCh1) == (mSecondsBetweenStimulus * mAlertBeepCounter)) {
                mAlertBeepCounter++;
                int temp = mAlertBeepCounterSwitch;
//                switch (temp) {
//                    case 1:
//                        mEMGClass = 0;
//                        break;
//                    case 2:
//                        mEMGClass = 3;
//                        break;
//                    case 3:
//                        mEMGClass=0;
//                        break;
//                    case 4:
//                        mEMGClass=4;
//                        break;
//                    case 5:
//                        mEMGClass=0;
//                        break;
//                    case 6:
//                        mEMGClass=5;
//                        break;
//                    case 7:
//                        mEMGClass=0;
//                        break;
//                    case 8:
//                        mEMGClass=6;
//                        break;
//                    case 9:
//                        mEMGClass=0;
//                        break;
//                    case 10:
//                        mEMGClass=7;
//                        break;
//                    case 11:
//                        mEMGClass=0;
//                        mAlertBeepCounterSwitch = 1;
//                        break;
//                    default:
//                        mEMGClass = 0;
//                        break;
//                }
//                mAlertBeepCounterSwitch++;
//                mMediaBeep.start();
//                //For training open/close
//                if(temp%2==0) {
//                    mEMGClass = 2;
//                    mMediaBeep.start();
//                } else {
//                    mEMGClass = 1;
//                }
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSSVEPClassTextView.setText("C:[" + mEMGClass + "]");
            }
        });
    }

    private double bytesToDouble(byte a1, byte a2, byte a3) {
        int a = unsignedToSigned(unsignedBytesToInt(a1,a2,a3),24);
        return ((double)a/8388607.0)*2.25;
    }

    private int mNumberOfClassifierCalls = 0;
    private double mLastYValue = 0;

    private class ClassifyTask extends AsyncTask<Void, Void, Double> {
        @Override
        protected Double doInBackground(Void... voids) {
            double[] concat = Doubles.concat(mGraphAdapterCh1.classificationBuffer,mGraphAdapterCh2.classificationBuffer,mGraphAdapterCh3.classificationBuffer);
            return jClassify(concat, mLastYValue);
        }

        @Override
        protected void onPostExecute(Double predictedClass) {
            mLastYValue = predictedClass;
            processClassifiedData(predictedClass);
            super.onPostExecute(predictedClass);
        }
    }

    private double findGraphMin(SimpleXYSeries s) {
        if (s.size() > 0) {
            double min = (double) s.getY(0);
            for (int i = 1; i < s.size(); i++) {
                double a = (double) s.getY(i);
                if (a < min) {
                    min = a;
                }
            }
            return min;
        } else {
            return 0.0;
        }
    }

    private void executeCommand(int command) {
        byte[] bytes = new byte[1];
        switch (command) {
            case 0:
                bytes[0] = (byte) 0x00;
                break;
            case 1:
                bytes[0] = (byte) 0x01; //Stop
                break;
            case 2:
                bytes[0] = (byte) 0xF0; //?
                break;
            case 3:
                bytes[0] = (byte) 0x0F;
                break;
            case 4:
                bytes[0] = (byte) 0xFF;
                // TODO: 6/27/2017 Disconnect instead of reverse?
                break;
            default:
                break;
        }
        if (mLedService != null) {
            mBluetoothLe.writeCharacteristic(mBluetoothGattArray[mWheelchairGattIndex], mLedService.getCharacteristic(AppConstant.CHAR_WHEELCHAIR_CONTROL), bytes);
        }
    }

    private void processClassifiedData(final double Y) {
        //Shift backwards:
        System.arraycopy(yfitarray, 1, yfitarray, 0, 4);
        //Add to end;
        yfitarray[4] = Y;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mYfitTextView.setText(String.valueOf(Y));
            }
        });
        //Analyze:
        Log.e(TAG, " YfitArray: " + Arrays.toString(yfitarray));
        final boolean checkLastThreeMatches = lastThreeMatches(yfitarray);
        if (checkLastThreeMatches) {
            //Get value:
            Log.e(TAG, "Found fit: " + String.valueOf(yfitarray[4]));
            final String s = "[" + String.valueOf(Y) + "]";

            // TODO: 4/27/2017 CONDITION :: CONTROL WHEELCHAIR
            if(mConnectedThread!=null && Y!=0) {
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
    }

    private void writeToDisk24(final double ch1, final double ch2, final double ch3) {
        try {
            exportFileWithClass(ch1, ch2, ch3);
        } catch (IOException e) {
            Log.e("IOException", e.toString());
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
        String lastRssi = String.valueOf(rssi) + "db";
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
                //TODO: ATTEMPT TO RECONNECT:
                updateConnectionState(getString(R.string.disconnected));
                stopMonitoringRssiValue();
                invalidateOptionsMenu();
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

//    private void updateBatteryStatus(final int percent, final String status) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if (percent <= batteryWarning) {
//                    mBatteryLevel.setTextColor(Color.RED);
//                    mBatteryLevel.setTypeface(null, Typeface.BOLD);
//                    Toast.makeText(getApplicationContext(), "Charge Battery, Battery Low " + status, Toast.LENGTH_SHORT).show();
//                } else {
//                    mBatteryLevel.setTextColor(Color.GREEN);
//                    mBatteryLevel.setTypeface(null, Typeface.BOLD);
//                }
//                mBatteryLevel.setText(status);
//            }
//        });
//    }

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

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);        // Get number of bytes and message in "buffer"
                    mHandler.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();     // Send to message queue Handler
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String message) {
            Log.d(TAG, "...Data to send: " + message + "...");
            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                Log.d(TAG, "...Error data send: " + e.getMessage() + "...");
            }
        }

        public void write(int message) {
            Log.d(TAG, "...Data to send: " + String.valueOf(message) + "...");
            byte[] msgBuffer = ByteBuffer.allocate(4).putInt(message).array();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                Log.d(TAG, "...Error data send: " + e.getMessage() + "...");
            }
        }
    }

    private int unsignedToSigned(int unsigned, int size) {
        if ((unsigned & (1 << size - 1)) != 0) {
            unsigned = -1 * ((1 << size - 1) - (unsigned & ((1 << size - 1) - 1)));
        }
        return unsigned;
    }

    private int unsignedBytesToInt(byte b0, byte b1) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8));
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

    public native double jClassify(double[] Array, double LastY);

}
