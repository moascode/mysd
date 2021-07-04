package com.distance.mysd;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class that start or stop a bluetooth le scan and prepare scan results.
 */
public class BluetoothLE {
    /*Static constants*/
    static final int REQUEST_ENABLE_BT = 1;
    static final int PERMISSION_ACCESS_FINE_LOCATION = 1;
    private static final int COUNTER_LIMIT = 100;
    private static final int RSSI_SIGNAL_LIMIT = -75;
    private static final int RSSI_DISTANCE_SAFE = -55;
    private static final double DISTANCE_SAFE = 0.5;
    private static final int DEFAULT_TX_POWER = -55;
    private static final byte TX_POWER = 0x0A;
    private static final String DEVICE_NAME = "DT_DEVICE";
    /*UUID string*/
    /*private static final String SERVICE_DATA_UUID_STRING = "00006B61-0000-1000-8000-00805F9B34FB"; Not used */
    /*UUID for advertisement*/
    /*private final ParcelUuid serviceId = ParcelUuid.fromString(SERVICE_DATA_UUID_STRING); Not used */

    /*Bluetooth specific variable*/
    private BluetoothAdapter blAdapter;
    private BluetoothLeScanner bleScanner;
    private ScanSettings scanSettings;
    private List<ScanFilter> scanFilters = new ArrayList<>();

    /*Activity that calls this class*/
    private MainActivity mActivity;

    /*Map to hold all the device address and list of information*/
    private Map<String, List<Object>> deviceList = new HashMap<>();
    private Map<String, List<Object>> savedState = new HashMap<>();
    private int signalCounter = 0;
    private static final int CHECK_SIGNAL = 50;
    /*List of device information-counter,total RSSI, average RSSI, distance, safe/unsafe */
    private List<Object> trainedRssi;
    /*Index constants of trainedRssi list*/
    static final int INDEX_COUNTER = 0;
    static final int INDEX_TOTAL = 1;
    static final int INDEX_AVG_RSSI = 2;
    static final int INDEX_DISTANCE = 3;
    static final int INDEX_IS_SAFE = 4;
    static final int INDEX_NAME = 5;

    /*TX power/ reference RSSI at 2 meters*/
    /*Reference: https://github.com/opentrace-community/opentrace-calibration/blob/master/Device%20Data.csv*/
    private static final Map<String, Double> TX_POWER_LOOKUP_TABLE = createTxPowerLookupTable();
    private static Map<String, Double> createTxPowerLookupTable() {
        Map<String, Double> txPowerLookupTable = new HashMap<>();
        txPowerLookupTable.put("SM-N960F", -59.64);		//SAMSUNG Galaxy Note 9
        txPowerLookupTable.put("SM-G950F", -69.13);		//SAMSUNG Galaxy S8
        txPowerLookupTable.put("SM-G975F", -57.87);		//SAMSUNG Galaxy S10+
        txPowerLookupTable.put("SM-N975F", -49.9);		//SAMSUNG Galaxy Note 10+
        txPowerLookupTable.put("LYA-L29", -70.56);		//HUAWEI Mate 20 Pro
        txPowerLookupTable.put("SM-A705MN", -61.78);	//SAMSUNG Galaxy A70
        txPowerLookupTable.put("G8342", -59.54);		//SONY Xperia XZ1
        txPowerLookupTable.put("Mi A1", -63.52);		//XIAOMI Mi A1
        txPowerLookupTable.put("Pixel 3a XL", -58.41);	//GOOGLE Pixel 3a XL
        txPowerLookupTable.put("Pocophone F1", -55.72);	//XIAOMI Pocophone F1
        txPowerLookupTable.put("Mi A2 Lite", -59.45);	//XIAOMI Mi A2 Lite
        txPowerLookupTable.put("Nexus 5X", -67.4);		//LG Nexus 5X
        txPowerLookupTable.put("SM-A107F", -66.57);		//SAMSUNG Galaxy A10S
        txPowerLookupTable.put("CPH1909", -57.51);		//OPPO AX5s
        txPowerLookupTable.put("RMX1911", -66.74);		//OPPO Realme 5
        txPowerLookupTable.put("Mi Max 3", -55.33);		//XIAOMI Mi Max 3
        txPowerLookupTable.put("YAL-L21", -56.79);		//HUAWEI Nova 5t
        txPowerLookupTable.put("vivo 1915", -59.9);		//VIVO Y19
        return Collections.unmodifiableMap(txPowerLookupTable);
    }

    /**
     * Constructor
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    BluetoothLE(MainActivity activity) {
        //Get main activity
        mActivity = activity;
        //Ensure Bluetooth is available on the device and it is enabled
        grantBluetoothPermission();
        //Ensure Location is available on the device and it is enabled
        grantLocationPermission();
    }

    /**
     * Start BLE scan
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void start(){
        Log.d(mActivity.getString(R.string.tag_ble), "startScan");
        if (blAdapter.isEnabled()) {
            //Reset Device list
            deviceList.clear();
            //Advertise device as BLE peripheral
            advertise();
            //Get Scanner from adapter
            bleScanner = blAdapter.getBluetoothLeScanner();
            //Set settings for scanner
            scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            //Filter out scanning
            ScanFilter scanName = new ScanFilter.Builder()
                    //.setServiceUuid(serviceId)
                    //.setDeviceName(DEVICE_NAME)
                    .build();
            scanFilters.add(scanName);
            //Start asynchronous scanning
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    bleScanner.startScan(scanFilters, scanSettings, leScanCallback);
                }
            });
        }
    }

    /**
     * Stop BLE scan
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void stop(){
        Log.d(mActivity.getString(R.string.tag_ble), "stopScan");
        //Stop asynchronous scanning
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                bleScanner.stopScan(leScanCallback);
            }
        });
    }

    /**
     * Request Bluetooth permission
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void grantBluetoothPermission() {
        Log.d(mActivity.getString(R.string.tag_ble), "grantBluetoothPermission");
        //Get Bluetooth manager from System service
        final BluetoothManager bluetoothManager = (BluetoothManager) mActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        //Initialize Bluetooth adapter
        if (bluetoothManager != null)
            blAdapter = bluetoothManager.getAdapter();
        if (blAdapter == null || !blAdapter.isEnabled()) {
            //Prompt user to enable bluetooth
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    /**
     * Request Location permission
     */
    void grantLocationPermission() {
        Log.d(mActivity.getString(R.string.tag_ble), "grantLocationPermission");
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(mActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                //Show explanation to the user
                new AlertDialog.Builder(mActivity)
                        .setTitle("This app need location access")
                        .setMessage("Please grant location access so this app can detect peripherals.")
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt user for location access
                                ActivityCompat.requestPermissions(mActivity,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ACCESS_FINE_LOCATION);
                            }
                        }).create().show();
            } else {
                //No explanation needed, prompt for location access
                ActivityCompat.requestPermissions(mActivity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ACCESS_FINE_LOCATION);
            }
        }
    }

    /**
     * Bluetooth advertisement
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void advertise() {
        Log.d(mActivity.getString(R.string.tag_ble), "advertise");
        //Get advertiser from adapter
        BluetoothLeAdvertiser advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        //Set device name
        BluetoothAdapter.getDefaultAdapter().setName(DEVICE_NAME);
        //Set Advertise settings
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode( AdvertiseSettings.ADVERTISE_MODE_LOW_POWER )
                .setTxPowerLevel( AdvertiseSettings.ADVERTISE_TX_POWER_HIGH )
                .setConnectable(false)
                .build();

        //TODO: include txPower from TX_POWER_LOOKUP_TABLE in the advertisement
        /*double setTxPower = -55;
        if (TX_POWER_LOOKUP_TABLE.containsKey(android.os.Build.MODEL))
            setTxPower = TX_POWER_LOOKUP_TABLE.get(android.os.Build.MODEL);
        byte[] txPowerInByte = new byte[8];
        long txPowerInLong = Double.doubleToLongBits(setTxPower);
        for(int i = 0; i < 8; i++) txPowerInByte[i] = (byte)((txPowerInLong >> ((7 - i) * 8)) & 0xff);*/

        //Set advertise data
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                //.addManufacturerData(ManufacturerID, DEVICE_NAME.getBytes())
                //.addManufacturerData(TXPOWER, txPowerInByte)
                //.addServiceUuid(serviceId)
                .build();

        //Advertise callback
        AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
            }
            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
            }
        };

        //Start advertising
        advertiser.startAdvertising( settings, data, advertisingCallback );
    }

    /**
     * Device scan callback result
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private ScanCallback leScanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(mActivity.getString(R.string.tag_ble), "onScanResult");

            trainedRssi = new ArrayList<>();
            int txPower = 0;
            String macAddress = result.getDevice().getAddress();

            //Train RSSI values for better result
            double avgRssi = trainRssi(macAddress, result.getRssi());
            //Remove device that no longer receiving signal
            signalCounter++;
            if (signalCounter == CHECK_SIGNAL) {
                if (!savedState.isEmpty()) {
                    for(Map.Entry<String, List<Object>> entry : deviceList.entrySet()) {
                        String key = entry.getKey();
                        if (savedState.containsKey(key)) {
                            List<Object> currentList = entry.getValue();
                            List<Object> savedList = savedState.get(key);
                            if (currentList != null && savedList != null && ((int)currentList.get(INDEX_COUNTER) != 0)) {
                                if (currentList.get(INDEX_COUNTER) == savedList.get(INDEX_COUNTER)) {
                                    currentList.set(INDEX_COUNTER, 0);
                                    deviceList.put(key, currentList);
                                }
                            }
                        }
                    }
                }
                savedState.putAll(deviceList);
                signalCounter = 0;
            }
            //Skip of the signal is weak
            if (avgRssi < RSSI_SIGNAL_LIMIT) {
                //Remove the device from the list due to weak signal
                if (deviceList.containsKey(macAddress))
                    deviceList.remove(result.getDevice().getAddress());
            }
            else {
                //Get txPower from the advertised manufacturer data
                if (result.getScanRecord() != null) {
                    SparseArray<byte []> manufacturerData = result.getScanRecord().getManufacturerSpecificData();
                    if (manufacturerData.size() > 0) {
                        txPower = manufacturerData.keyAt(TX_POWER);
                    }
                }
                if (txPower == 0) {
                    txPower = DEFAULT_TX_POWER;
                }
                //Calculate distance
                double distance = calculateDistance(txPower, avgRssi);
                //Deciding user compliance
                boolean is_safe = isSafe(distance, avgRssi);
                //Add to the list
                trainedRssi.add(avgRssi);
                trainedRssi.add(roundDoubleWithTwoPrecision(distance));
                if (is_safe) {
                    trainedRssi.add(mActivity.getString(R.string.safe));
                }
                else {
                    trainedRssi.add(mActivity.getString(R.string.unsafe));
                }
                trainedRssi.add(result.getDevice().getName());
                //Map list information to the device
                deviceList.put(macAddress, trainedRssi);
                //Pass results to view
                mActivity.bleScanActivity(deviceList);
            }
        }
    };

    /**
     * Deciding safe/unsafe based on Social distancing compliance
     */
    private boolean isSafe(double distance, double avgRssi){
        // Distance and avgRssi is considered for deciding SAFE/UNSAFE
        return (distance > DISTANCE_SAFE || avgRssi < RSSI_DISTANCE_SAFE);
    }

    /**
     * Distance calculation based on measured power and rssi
     */
    private double calculateDistance(int measuredPower, double rssi) {
        if (rssi == 0) {
            return -1.0; // if we cannot determine distance, return -1.
        }
        double ratio = rssi*1.0/measuredPower;
        if (ratio < 1.0) {
            return Math.pow(ratio,10);
        }
        else {
            return (0.89976)*Math.pow(ratio,7.7095) + 0.111;
        }
    }

    /**
     * Calculate average RSSI values upto 100 continuous signals
     * This is to stabilize the RSSI which keeps on changing (approx. 800times/second)
     */
    private double trainRssi(String key, double rssi){
        int counter = 0;
        double total = 0.0, avgRssi = 0.0;
        if (deviceList.containsKey(key)) {
            List<Object> list = deviceList.get(key);
            if (list != null) {
                counter = ((int)list.get(INDEX_COUNTER));
                total = ((double) list.get(INDEX_TOTAL));
                total += rssi;
                avgRssi = total / ++counter;
                if (counter > COUNTER_LIMIT) {
                    counter = 1;
                    total = avgRssi;
                }
            }
        }
        else {
            avgRssi = total = rssi;
            ++counter;
        }
        trainedRssi.add(counter);
        trainedRssi.add(roundDoubleWithTwoPrecision(total));
        return roundDoubleWithTwoPrecision(avgRssi);
    }

    /**
     * Round up double values with two decimal precision
     */
    private double roundDoubleWithTwoPrecision(double value) {
        double rValue = value;
        rValue = rValue * 100;
        rValue = Math.round(rValue);
        rValue = rValue / 100;
        return rValue;
    }

    /**
     * Get txPower from manufacturer data
     */
    private byte getTXPower( byte data[]) {
        int pos=0;
        int dlen = data.length;
        byte txpower = 0;
        while((pos+1) < dlen) {
            int bpos = pos;
            int blen = ((int)data[pos]) & 0xFF;
            if( blen == 0 )
                break;
            if( bpos+blen > dlen )
                break;
            ++pos;
            int type = ((int)data[pos]) & 0xFF;
            ++pos;
            //int len = blen - 1;
            if (type == TX_POWER)
                txpower = data[pos];
        }
        return txpower;
    }
}
