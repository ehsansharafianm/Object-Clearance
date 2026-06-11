package com.tmsimple.ObjectCrossing;
import com.xsens.dot.android.sdk.recording.DotRecordingManager;
import com.xsens.dot.android.sdk.models.DotRecordingState;
import com.xsens.dot.android.sdk.models.DotRecordingFileInfo;

import androidx.bluetooth.BluetoothDevice;
import android.content.Context;
import com.xsens.dot.android.sdk.DotSdk;
import com.xsens.dot.android.sdk.interfaces.DotDeviceCallback;
import com.xsens.dot.android.sdk.interfaces.DotMeasurementCallback;
import com.xsens.dot.android.sdk.interfaces.DotRecordingCallback;
import com.xsens.dot.android.sdk.interfaces.DotScannerCallback;
import com.xsens.dot.android.sdk.interfaces.DotSyncCallback;
import com.xsens.dot.android.sdk.models.DotDevice;
import com.xsens.dot.android.sdk.models.DotPayload;
import com.xsens.dot.android.sdk.models.DotSyncManager;
import com.xsens.dot.android.sdk.utils.DotParser;
import com.xsens.dot.android.sdk.utils.DotScanner;
import android.bluetooth.le.ScanSettings;
import android.content.res.Resources;

import com.xsens.dot.android.sdk.events.DotData;
import com.xsens.dot.android.sdk.utils.DotLogger;

import java.util.Date;
import java.util.List;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

public class ImuManager implements
        DotDeviceCallback,
        DotScannerCallback,
        DotRecordingCallback,
        DotSyncCallback,
        DotMeasurementCallback,
        ZuptDetector.ZuptListener,
        BiasCalculation.BiasCalculationListener,
        FeatureDetectorThroughWindow.FeatureDetectionListener {

    private Context context;
    private ImuManagerListener listener;

    private DotScanner mScanner;
    private ArrayList<DotDevice> deviceList;
    private ZuptDetector zuptDetector;

    private Segment IMU1, IMU2, IMU3, IMU4, IMU5, IMU6;

    // Recording managers
    private com.xsens.dot.android.sdk.recording.DotRecordingManager IMU5RecordingManager;
    private com.xsens.dot.android.sdk.recording.DotRecordingManager IMU6RecordingManager;

    // Recording state flags
    private boolean imu5EraseCompleted = false;
    private boolean imu6EraseCompleted = false;
    private boolean isExporting = false;

    // Export data IDs
    private byte[] recordingExportDataIds;

    // Export loggers
    private java.io.BufferedWriter imu5ExportWriter;
    private java.io.BufferedWriter imu6ExportWriter;
    // Export file tracking

    private boolean imu5ExportDone = false;
    private boolean imu6ExportDone = false;
    // ADD these four:
    private int imu5TotalPackets = 0;
    private int imu6TotalPackets = 0;
    private int imu5ExportedPackets = 0;
    private int imu6ExportedPackets = 0;


    private boolean isLoggingData = false;
    private int packetCounterOffset = 0;

    private LogManager logManager;
    int measurementMode;
    private BiasCalculation biasCalculation;
    private BiasCalculation biasCalculation2;
    private FeatureDetectorThroughWindow featureDetectorThroughWindow;
    private String groundTruthTerrain;
    // ========== NEW: CALIBRATION STATE TRACKING ==========
    private boolean isCalibrationComplete = false;
    private boolean isCalibrationComplete2 = false;


    private int SelectionMesurementMode = DotPayload.PAYLOAD_TYPE_CUSTOM_MODE_5;
    private boolean isDiscoveryMode = false;
    private java.util.HashSet<String> discoveredDevices = new java.util.HashSet<>();
    private HashMap<String, String> macToTagMap;
    // This list MUST use the androidx type, as this is what getDiscoveredDevices() will return.
    private final ArrayList<BluetoothDevice> discoveredDevicesList = new ArrayList<>();
    // You need the BluetoothAdapter to perform the translation.

    private UiManager uiManager;

    // Confusion Matrix Variables

    private HashMap<String, HashMap<String, Integer>> confusionMatrixImu1;
    private HashMap<String, HashMap<String, Integer>> confusionMatrixImu2;
    private List<String> terrainTypes;
    private HashMap<Integer, String> packetToLabelMap; // Track labels for each packet

    private String imu5ExportFilePath;
    private String imu6ExportFilePath;
    private DotLogger imu5ExportLogger;
    private DotLogger imu6ExportLogger;

    public void setUiManager(UiManager uiManager) {
        this.uiManager = uiManager;
    }

    DecimalFormat decimalFormat = new DecimalFormat("##.###");

    public ImuManager(Context context, ImuManagerListener listener, LogManager logManager) {
        this.context = context;
        this.listener = listener;
        this.logManager = logManager;

        // Initialize zuptDetector with UiManager reference (will be set later)
        this.zuptDetector = new ZuptDetector(this, logManager);

        DotSdk.setDebugEnabled(true);
        DotSdk.setReconnectEnabled(true);

        initializeSensorMap();

        mScanner = new DotScanner(context, this);
        mScanner.setScanMode(ScanSettings.SCAN_MODE_BALANCED);

        deviceList = new ArrayList<>();
        this.biasCalculation = new BiasCalculation(this , logManager);
        this.biasCalculation2 = new BiasCalculation(this, logManager);
        // In the constructor, after biasCalculation initialization
        this.featureDetectorThroughWindow = new FeatureDetectorThroughWindow(this, logManager);
    }



    public void onZuptDetected(String imuId, int packetCounter) {
        // This gets called when ZUPT starts - you can leave it empty for now
        // or add any additional logic you want when ZUPT is detected
    }

    public void onZuptEnded(String imuId, int packetCounter) {
        // This gets called when ZUPT ends - you can leave it empty for now
        // or add any additional logic you want when ZUPT ends
    }

    @Override
    public void onZuptDataUpdated(String imuId, double gyroMag, double linearAccelMag) {

        // This forwards the magnitude data to MainActivity for UI updates
        if (listener != null) {
            listener.onZuptDataUpdated(imuId, gyroMag, linearAccelMag);
        } else {
            logManager.log("ERROR: MainActivity listener is null!");
        }
    }
    public void onGaitWindowCreated(String imuId, int windowNum, int startPacket, int endPacket, double duration) {
        if (isLoggingData) {
            GaitWindowData windowData = extractGaitWindowData(imuId, startPacket, endPacket);
            if (imuId.equals("IMU1")) {
                biasCalculation.processBiasCalculation(windowData, windowNum, startPacket, endPacket, groundTruthTerrain);
                if (!isCalibrationComplete && biasCalculation.isCalibrated()) {
                    onCalibrationJustCompleted("IMU1");
                }
            } else if (imuId.equals("IMU2")) {
                biasCalculation2.processBiasCalculation(windowData, windowNum, startPacket, endPacket, groundTruthTerrain);
                if (!isCalibrationComplete2 && biasCalculation2.isCalibrated()) {
                    onCalibrationJustCompleted("IMU2");
                }
            }
        }
    }

    // ========== NEW: HANDLE CALIBRATION COMPLETION ==========
    private void onCalibrationJustCompleted(String imuId) {
        if (imuId.equals("IMU1")) isCalibrationComplete = true;
        else isCalibrationComplete2 = true;
        logManager.log(">>> CALIBRATION COMPLETE: " + imuId + " - terrain classification active");
        initializeConfusionMatrix();
    }
    public void onBiasCalculationComplete(String imuId, int windowNum, double biasValue, double recalculatedBias, String terrainType, ArrayList<double[]> a_corrected, ArrayList<double[]> v_corrected, ArrayList<double[]> p_corrected, int startPacket, int endPacket) {
        boolean calComplete = imuId.equals("IMU1") ? isCalibrationComplete : isCalibrationComplete2;

        if (calComplete && !groundTruthTerrain.equals("Unknown") && !groundTruthTerrain.equals("Standing")) {
            updateConfusionMatrix(imuId, groundTruthTerrain, terrainType);
            logManager.log(imuId + " Win#" + windowNum + " GT:" + groundTruthTerrain + " Pred:" + terrainType +
                    (groundTruthTerrain.equals(terrainType) ? " ✓" : " ✗"));
        }

        featureDetectorThroughWindow.processFeatureDetectionInWindowData(imuId, windowNum, biasValue, recalculatedBias,
                terrainType, a_corrected, v_corrected, p_corrected, startPacket, endPacket, groundTruthTerrain);
    }
    @Override
    public void onFeatureDetectionComplete(String imuId, int windowNum, String terrainType,
                                           ArrayList<Double> extractedFeatures, double biasValue, int startPacket, int endPacket, String groundTruth) {
        if (extractedFeatures.size() >= 2) {
            double maxHeight = extractedFeatures.get(0);
            double maxStrideLength = extractedFeatures.get(1);
            if (isLoggingData) {
                logManager.logFeatureData(imuId, windowNum, terrainType, groundTruth, maxHeight, maxStrideLength, biasValue, startPacket, endPacket);
                if (listener != null) {
                    listener.onFeatureDetectionUpdate(imuId, windowNum, terrainType, biasValue, maxHeight, maxStrideLength);
                }
            }
        } else {
            logManager.log(imuId + " Win#" + windowNum + " ERROR: expected 2 features, got " + extractedFeatures.size());
        }
    }



    /*===========================================================================*/
    public void setSegments(Segment IMU1, Segment IMU2, Segment IMU3, Segment IMU4, Segment IMU5, Segment IMU6) {
        this.IMU1 = IMU1;
        this.IMU2 = IMU2;
        this.IMU3 = IMU3;
        this.IMU4 = IMU4;
        this.IMU5 = IMU5;
        this.IMU6 = IMU6;
    }
    public int getMeasurementMode() {
        return measurementMode;
    }
    public boolean startScan() {
        return mScanner.startScan();
    }
    public void setPacketCounterOffset(int packetCounterOffset) {
        this.packetCounterOffset = packetCounterOffset;
    }


    @Override
    public void onDotScanned(android.bluetooth.BluetoothDevice bluetoothDevice, int rssi) {
        if (IMU1 == null || IMU2 == null || IMU3 == null || IMU4 == null || IMU5 == null || IMU6 == null) {
            logManager.log("Error: Segments not initialized before scanning!");
            return;
        }

        String address = bluetoothDevice.getAddress();

        if (isDiscoveryMode) {
            if (!discoveredDevices.contains(address)) {
                discoveredDevices.add(address);
                String tagFromMap = macToTagMap.getOrDefault(address, "Unknown Tag");
                logManager.log("IMU Found: Address= " + address + ", Tag= " + tagFromMap);
                if (uiManager != null) uiManager.updateSpinnersWithDiscoveredDevices(context, discoveredDevices);
            }
            return;
        }

        if (address.equals(IMU1.MAC) && !IMU1.isScanned) {
            IMU1.isScanned = true;
            IMU1.xsDevice = new DotDevice(context, bluetoothDevice, this);
            IMU1.xsDevice.connect();
            IMU1.isConnected = true;
            deviceList.add(IMU1.xsDevice);
            listener.onImuScanned(IMU1.Name);
            logManager.log(IMU1.Name + " scanned");
        } else if (address.equals(IMU2.MAC) && !IMU2.isScanned) {
            IMU2.isScanned = true;
            IMU2.xsDevice = new DotDevice(context, bluetoothDevice, this);
            IMU2.xsDevice.connect();
            IMU2.isConnected = true;
            deviceList.add(IMU2.xsDevice);
            listener.onImuScanned(IMU2.Name);
            logManager.log(IMU2.Name + " scanned");
        } else if (address.equals(IMU3.MAC) && !IMU3.isScanned) {
            IMU3.isScanned = true;
            IMU3.xsDevice = new DotDevice(context, bluetoothDevice, this);
            IMU3.xsDevice.connect();
            IMU3.isConnected = true;
            deviceList.add(IMU3.xsDevice);
            listener.onImuScanned(IMU3.Name);
            logManager.log(IMU3.Name + " scanned");
        } else if (address.equals(IMU4.MAC) && !IMU4.isScanned) {
            IMU4.isScanned = true;
            IMU4.xsDevice = new DotDevice(context, bluetoothDevice, this);
            IMU4.xsDevice.connect();
            IMU4.isConnected = true;
            deviceList.add(IMU4.xsDevice);
            listener.onImuScanned(IMU4.Name);
            logManager.log(IMU4.Name + " scanned");
        } else if (address.equals(IMU5.MAC) && !IMU5.isScanned) {
            IMU5.isScanned = true;
            IMU5.xsDevice = new DotDevice(context, bluetoothDevice, this);
            IMU5.xsDevice.connect();
            IMU5.isConnected = true;
            deviceList.add(IMU5.xsDevice);
            listener.onImuScanned(IMU5.Name);
            logManager.log(IMU5.Name + " scanned");
        } else if (address.equals(IMU6.MAC) && !IMU6.isScanned) {
            IMU6.isScanned = true;
            IMU6.xsDevice = new DotDevice(context, bluetoothDevice, this);
            IMU6.xsDevice.connect();
            IMU6.isConnected = true;
            deviceList.add(IMU6.xsDevice);
            listener.onImuScanned(IMU6.Name);
            logManager.log(IMU6.Name + " scanned");
        }

        if (IMU1.isScanned && IMU2.isScanned && IMU3.isScanned && IMU4.isScanned && IMU5.isScanned && IMU6.isScanned) {
            mScanner.stopScan();
            logManager.log("All 6 devices scanned");
        }
    }
    @Override
    public void onDotInitDone(String address) {
        if (address.equals(IMU1.MAC)) {
            IMU1.isReady = true;
            IMU1.xsDevice.setOutputRate(60);
            listener.onImuReady(IMU1.Name);
        } else if (address.equals(IMU2.MAC)) {
            IMU2.isReady = true;
            IMU2.xsDevice.setOutputRate(60);
            listener.onImuReady(IMU2.Name);
        } else if (address.equals(IMU3.MAC)) {
            IMU3.isReady = true;
            IMU3.xsDevice.setOutputRate(60);
            listener.onImuReady(IMU3.Name);
        } else if (address.equals(IMU4.MAC)) {
            IMU4.isReady = true;
            IMU4.xsDevice.setOutputRate(60);
            listener.onImuReady(IMU4.Name);
        } else if (address.equals(IMU5.MAC)) {
            IMU5.isReady = true;
            listener.onImuReady(IMU5.Name);
            logManager.log("IMU5 init done");
            if (IMU5RecordingManager == null) {
                IMU5RecordingManager = new com.xsens.dot.android.sdk.recording.DotRecordingManager(context, IMU5.xsDevice, this);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    IMU5RecordingManager.enableDataRecordingNotification();
                    logManager.log("IMU5 enableDataRecordingNotification called");
                }, 2000);
            } else {
                // After sync reconnect — re-enable without erasing
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    IMU5RecordingManager.enableDataRecordingNotification();
                    logManager.log("IMU5 re-enabled notifications after sync reconnect");
                }, 2000);
            }
        } else if (address.equals(IMU6.MAC)) {
            IMU6.isReady = true;
            listener.onImuReady(IMU6.Name);
            logManager.log("IMU6 init done");
            if (IMU6RecordingManager == null) {
                IMU6RecordingManager = new com.xsens.dot.android.sdk.recording.DotRecordingManager(context, IMU6.xsDevice, this);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    IMU6RecordingManager.enableDataRecordingNotification();
                    logManager.log("IMU6 enableDataRecordingNotification called");
                }, 2000);
            } else {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    IMU6RecordingManager.enableDataRecordingNotification();
                    logManager.log("IMU6 re-enabled notifications after sync reconnect");
                }, 2000);
            }
        }
    }

    public void startSync() {
        DotSyncManager.getInstance(this).stopSyncing();
        logManager.log("Start Sync clicked. Device list size: " + deviceList.size());

        if (!IMU1.isReady || !IMU2.isReady || !IMU3.isReady || !IMU4.isReady) {
            logManager.log("Error: Streaming IMUs not ready for sync");
            return;
        }

        deviceList.get(0).setRootDevice(true);
        DotSyncManager.getInstance(this).startSyncing(deviceList, 100);
        logManager.log("Sync requested.");
    }


    @Override
    public void onSyncingDone(HashMap<String, Boolean> results, boolean allSuccess, int errorCode) {
        measurementMode = SelectionMesurementMode;
        IMU1.xsDevice.setMeasurementMode(measurementMode);
        IMU2.xsDevice.setMeasurementMode(measurementMode);
        IMU3.xsDevice.setMeasurementMode(measurementMode);
        IMU4.xsDevice.setMeasurementMode(measurementMode);
        // IMU5 and IMU6 intentionally excluded — they use recording mode
        logManager.log("---------- Syncing done ----------");
        listener.onSyncingDone();
    }
    public void startMeasurement() {
        if (IMU1.xsDevice.startMeasuring()) logManager.log("IMU1 measuring");
        else logManager.log("IMU1 startMeasuring FAILED");

        if (IMU2.xsDevice.startMeasuring()) logManager.log("IMU2 measuring");
        else logManager.log("IMU2 startMeasuring FAILED");

        if (IMU3.xsDevice.startMeasuring()) logManager.log("IMU3 measuring");
        else logManager.log("IMU3 startMeasuring FAILED");

        if (IMU4.xsDevice.startMeasuring()) logManager.log("IMU4 measuring");
        else logManager.log("IMU4 startMeasuring FAILED");

        if (IMU5RecordingManager != null) { IMU5RecordingManager.startRecording(); logManager.log("IMU5 recording started"); }
        if (IMU6RecordingManager != null) { IMU6RecordingManager.startRecording(); logManager.log("IMU6 recording started"); }
    }
    public void stopMeasurement() {
        IMU1.xsDevice.stopMeasuring(); if (IMU1.normalDataLogger != null) IMU1.normalDataLogger.stop();
        IMU2.xsDevice.stopMeasuring(); if (IMU2.normalDataLogger != null) IMU2.normalDataLogger.stop();
        IMU3.xsDevice.stopMeasuring(); if (IMU3.normalDataLogger != null) IMU3.normalDataLogger.stop();
        IMU4.xsDevice.stopMeasuring(); if (IMU4.normalDataLogger != null) IMU4.normalDataLogger.stop();
        if (IMU5RecordingManager != null) IMU5RecordingManager.stopRecording();
        if (IMU6RecordingManager != null) IMU6RecordingManager.stopRecording();
        logConfusionMatrix();
    }
    public void exportRecordingData(int subjectNumber) {
        logManager.log("exportRecordingData called");

        recordingExportDataIds = new byte[]{
                DotRecordingManager.RECORDING_DATA_ID_TIMESTAMP,
                DotRecordingManager.RECORDING_DATA_ID_ORIENTATION,
                DotRecordingManager.RECORDING_DATA_ID_CALIBRATED_ACC,
                DotRecordingManager.RECORDING_DATA_ID_CALIBRATED_GYR
        };

        imu5ExportDone = false;
        imu6ExportDone = false;
        isExporting = true;

        imu5TotalPackets = 0;
        imu6TotalPackets = 0;
        imu5ExportedPackets = 0;
        imu6ExportedPackets = 0;

        String timestamp = java.text.DateFormat.getDateTimeInstance().format(new Date());

        java.io.File imu5Folder = context.getApplicationContext().getExternalFilesDir("Subject " + subjectNumber + "/" + IMU5.xsDevice.getTag());
        java.io.File imu6Folder = context.getApplicationContext().getExternalFilesDir("Subject " + subjectNumber + "/" + IMU6.xsDevice.getTag());
        imu5Folder.mkdirs();
        imu6Folder.mkdirs();

        imu5ExportFilePath = imu5Folder.getPath() + "/IMU5_" + IMU5.xsDevice.getTag() + "_" + timestamp + ", Subject " + subjectNumber + ".csv";
        imu6ExportFilePath = imu6Folder.getPath() + "/IMU6_" + IMU6.xsDevice.getTag() + "_" + timestamp + ", Subject " + subjectNumber + ".csv";

        imu5ExportLogger = DotLogger.createRecordingsLogger(
                context.getApplicationContext(), recordingExportDataIds,
                imu5ExportFilePath, IMU5.xsDevice.getTag(),
                IMU5.xsDevice.getFirmwareVersion(), "60", 0);

        imu6ExportLogger = DotLogger.createRecordingsLogger(
                context.getApplicationContext(), recordingExportDataIds,
                imu6ExportFilePath, IMU6.xsDevice.getTag(),
                IMU6.xsDevice.getFirmwareVersion(), "60", 0);

        logManager.log("IMU5 export logger created: " + imu5ExportFilePath);
        logManager.log("IMU6 export logger created: " + imu6ExportFilePath);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (IMU5RecordingManager != null) IMU5RecordingManager.enableDataRecordingNotification();
            if (IMU6RecordingManager != null) IMU6RecordingManager.enableDataRecordingNotification();
            logManager.log("Export: enableDataRecordingNotification called for IMU5 and IMU6");
        }, 2000);
    }
    public void disconnectAll() {
        if (IMU1.xsDevice != null) IMU1.xsDevice.disconnect();
        if (IMU2.xsDevice != null) IMU2.xsDevice.disconnect();
        if (IMU3.xsDevice != null) IMU3.xsDevice.disconnect();
        if (IMU4.xsDevice != null) IMU4.xsDevice.disconnect();
        if (IMU5.xsDevice != null) IMU5.xsDevice.disconnect();
        if (IMU6.xsDevice != null) IMU6.xsDevice.disconnect();
    }
    // Callbacks will go here in the next step

    @Override
    public void onDotConnectionChanged(String address, int state) {


        boolean isConnected = (state == DotDevice.CONN_STATE_CONNECTED);

        if (address.equals(IMU1.MAC)) {
            IMU1.isConnected = isConnected;
            logManager.log("IMU1 IMU is " + (isConnected ? "connected!" : "disconnected!"));
            listener.onImuConnectionChanged(IMU1.Name, isConnected);

        } else if (address.equals(IMU2.MAC)) {
            IMU2.isConnected = isConnected;
            logManager.log("IMU2 IMU is " + (isConnected ? "connected!" : "disconnected!"));
            listener.onImuConnectionChanged(IMU2.Name, isConnected);
        } else if (address.equals(IMU3.MAC)) {
            IMU3.isConnected = isConnected;
            logManager.log("IMU3 " + (isConnected ? "connected" : "disconnected"));
            listener.onImuConnectionChanged(IMU3.Name, isConnected);
        } else if (address.equals(IMU4.MAC)) {
            IMU4.isConnected = isConnected;
            logManager.log("IMU4 " + (isConnected ? "connected" : "disconnected"));
            listener.onImuConnectionChanged(IMU4.Name, isConnected);
        } else if (address.equals(IMU5.MAC)) {
            IMU5.isConnected = isConnected;
            logManager.log("IMU5 " + (isConnected ? "connected" : "disconnected"));
            listener.onImuConnectionChanged(IMU5.Name, isConnected);
        } else if (address.equals(IMU6.MAC)) {
            IMU6.isConnected = isConnected;
            logManager.log("IMU6 " + (isConnected ? "connected" : "disconnected"));
            listener.onImuConnectionChanged(IMU6.Name, isConnected);
        }

    }

    @Override
    public void onDotDataChanged(String address, com.xsens.dot.android.sdk.events.DotData dotData) {

        final float[] quats = dotData.getQuat();
        final double[] eulerAngles = DotParser.quaternion2Euler(quats);
        final double[] gyroData = dotData.getGyr();
        final double[] accelData = dotData.getAcc();


        if (address.equals(IMU1.MAC)) {
            dotData.setPacketCounter(dotData.getPacketCounter() + packetCounterOffset);
            // Calculate initial values during standing


            calculateInitialValues(IMU1, dotData, eulerAngles, gyroData, accelData);

            // Apply calibration
            double[] calibrated = applyCalibratedData(IMU1, eulerAngles, gyroData, accelData);
            eulerAngles[0] = calibrated[0]; // Use calibrated roll

        } else if (address.equals(IMU2.MAC)) {
            dotData.setPacketCounter(dotData.getPacketCounter() + packetCounterOffset);
            // Same for IMU2
            calculateInitialValues(IMU2, dotData, eulerAngles, gyroData, accelData);

            double[] calibrated = applyCalibratedData(IMU2, eulerAngles, gyroData, accelData);
            eulerAngles[0] = calibrated[0];
        } else if (address.equals(IMU3.MAC)) {
            dotData.setPacketCounter(dotData.getPacketCounter() + packetCounterOffset);
            calculateInitialValues(IMU3, dotData, eulerAngles, gyroData, accelData);
            double[] calibrated = applyCalibratedData(IMU3, eulerAngles, gyroData, accelData);
            eulerAngles[0] = calibrated[0];
        } else if (address.equals(IMU4.MAC)) {
            dotData.setPacketCounter(dotData.getPacketCounter() + packetCounterOffset);
            calculateInitialValues(IMU4, dotData, eulerAngles, gyroData, accelData);
            double[] calibrated = applyCalibratedData(IMU4, eulerAngles, gyroData, accelData);
            eulerAngles[0] = calibrated[0];
        }

        if (listener != null) {
            listener.onDataUpdated(address, eulerAngles);
        }

        // PRESERVE THE ORIGINAL LOGGING SECTION
        if (isLoggingData) {
            if (address.equals(IMU1.MAC)) {

                IMU1.normalDataLogger.update(dotData);
                IMU1.sampleCounter++;
                IMU1.dataOutput[3] = decimalFormat.format(dotData.getPacketCounter());

                // Store data in the segment object
                IMU1.storeData(eulerAngles, quats, accelData, dotData.getPacketCounter());


            } else if (address.equals(IMU2.MAC)) {

                IMU2.normalDataLogger.update(dotData);
                IMU2.sampleCounter++;
                IMU2.dataOutput[3] = decimalFormat.format(dotData.getPacketCounter());

                // Store data in the segment object
                IMU2.storeData(eulerAngles, quats, accelData, dotData.getPacketCounter());

            } else if (address.equals(IMU3.MAC)) {
                if (IMU3.normalDataLogger != null) IMU3.normalDataLogger.update(dotData);
                IMU3.sampleCounter++;
                IMU3.dataOutput[3] = decimalFormat.format(dotData.getPacketCounter());
            } else if (address.equals(IMU4.MAC)) {
                if (IMU4.normalDataLogger != null) IMU4.normalDataLogger.update(dotData);
                IMU4.sampleCounter++;
                IMU4.dataOutput[3] = decimalFormat.format(dotData.getPacketCounter());
            }
        }

        // ZUPT PROCESSING - happens always (outside logging condition)
        if (address.equals(IMU1.MAC)) {
            // Use calibrated data for ZUPT
            double[] calibrated = applyCalibratedData(IMU1, eulerAngles, gyroData, accelData);
            double calibratedGyro = calibrated[1];
            double calibratedAccel = calibrated[2];


            // Get ground truth terrain for this packet
            groundTruthTerrain = getGroundTruthTerrain(dotData.getPacketCounter());


            zuptDetector.processNewImuData("IMU1", calibratedGyro, calibratedAccel, eulerAngles[0], removeLabelOffset(dotData.getPacketCounter()));

        } else if (address.equals(IMU2.MAC)) {
            double[] calibrated = applyCalibratedData(IMU2, eulerAngles, gyroData, accelData);
            double calibratedGyro = calibrated[1];
            double calibratedAccel = calibrated[2];
            groundTruthTerrain = getGroundTruthTerrain(dotData.getPacketCounter());
            zuptDetector.processNewImuData("IMU2", calibratedGyro, calibratedAccel, eulerAngles[0], removeLabelOffset(dotData.getPacketCounter()));
        }

    }

    public void setLoggingData(boolean logging) {
        this.isLoggingData = logging;
    }

    public void calculateInitialValues(Segment segment, com.xsens.dot.android.sdk.events.DotData dotData, double[] eulerAngles, double[] gyroData, double[] accelData) {

        if (dotData.getPacketCounter() > 1000000 && dotData.getPacketCounter() < 2000000) { // Standing mode

            // Accumulate all sensor values
            segment.sumOfInitialRoll += eulerAngles[0];
            segment.sumOfInitialGyro += Math.sqrt(
                    gyroData[0] * gyroData[0] +
                            gyroData[1] * gyroData[1] +
                            gyroData[2] * gyroData[2]);
            segment.sumOfInitialAccel += Math.sqrt(
                    accelData[0] * accelData[0] +
                            accelData[1] * accelData[1] +
                            accelData[2] * accelData[2]);

            // Calculate running averages
            segment.initRollValue = segment.sumOfInitialRoll / segment.initializationCounter;
            segment.initGyroValue = segment.sumOfInitialGyro / segment.initializationCounter;
            segment.initAccelValue = segment.sumOfInitialAccel / segment.initializationCounter;

            // Log every 2 seconds (120 samples at 60Hz)
            if (segment.initializationCounter % 120 == 119) {
                logManager.log("Initial values for " + segment.Name + ":");
                logManager.log("  Roll: " + decimalFormat.format(segment.initRollValue));

                logManager.log("  Gyro: " +
                        decimalFormat.format(segment.initGyroValue));

                logManager.log("  Accel: " +
                        decimalFormat.format(segment.initAccelValue));
            }

            segment.initializationCounter++;
        }
    }

    // Apply calibration offsets
    private double[] applyCalibratedData(Segment segment, double[] eulerAngles, double[] gyroData, double[] accelData) {
        double[] calibrated = new double[7];

        // Subtract initial values from current measurements
        /*calibrated[0] = eulerAngles[0] - segment.initRollValue;*/   // Roll
        calibrated[0] = eulerAngles[0];

        calibrated[1] = Math.sqrt(gyroData[0]*gyroData[0] + gyroData[1]*gyroData[1] + gyroData[2]*gyroData[2]) -
                segment.initGyroValue;

        calibrated[2] = Math.sqrt(accelData[0]*accelData[0] + accelData[1]*accelData[1] + accelData[2]*accelData[2]) -
                segment.initAccelValue;

        return calibrated;
    }

    // Data structure to hold extracted window data
    public static class GaitWindowData {
        public ArrayList<double[]> eulerAnglesInWindow;
        public ArrayList<double[]> accelDataInWindow;
        public ArrayList<Integer> packetCountersInWindow;
        public ArrayList<float[]> quaternionsInWindow;
        public int startPacket;
        public int endPacket;
        public String imuId;
        public Segment segmentWindow;

        public GaitWindowData(String imuId) {
            this.imuId = imuId;
            eulerAnglesInWindow = new ArrayList<>();
            accelDataInWindow = new ArrayList<>();
            packetCountersInWindow = new ArrayList<>();
            quaternionsInWindow = new ArrayList<>();
        }
    }

    // Extract gait window data from stored arrays
    private GaitWindowData extractGaitWindowData(String imuId, int startPacket, int endPacket) {
        GaitWindowData windowData = new GaitWindowData(imuId);
        windowData.startPacket = startPacket;
        windowData.endPacket = endPacket;

        Segment segment = null;
        if (imuId.equals("IMU1")) {
            segment = IMU1;
            windowData.segmentWindow = IMU1;
        } else if (imuId.equals("IMU2")) {
            segment = IMU2;
            windowData.segmentWindow = IMU2;
        }

        if (segment == null) return windowData;

        // Iterate through stored data and extract matching packets
        for (int i = 0; i < segment.storedPacketCounters.size(); i++) {
            int packet = segment.storedPacketCounters.get(i);

            // Check if this packet is within the gait window
            if (packet >= startPacket && packet <= endPacket) {
                // Create copies to avoid reference issues
                double[] eulerCopy = new double[segment.storedEulerAngles.get(i).length];
                System.arraycopy(segment.storedEulerAngles.get(i), 0, eulerCopy, 0, eulerCopy.length);

                double[] accelCopy = new double[segment.storedAccelData.get(i).length];
                System.arraycopy(segment.storedAccelData.get(i), 0, accelCopy, 0, accelCopy.length);

                float[] quatsCopy = new float[segment.storedQuaternions.get(i).length];
                System.arraycopy(segment.storedQuaternions.get(i), 0, quatsCopy, 0, quatsCopy.length);

                windowData.eulerAnglesInWindow.add(eulerCopy);
                windowData.accelDataInWindow.add(accelCopy);
                windowData.packetCountersInWindow.add(packet);
                windowData.quaternionsInWindow.add(quatsCopy);
            }
        }

        return windowData;
    }

    // Remove label offset from packet counter
    private int removeLabelOffset(int labeledPacketCounter) {
        if (labeledPacketCounter >= 1000000) {
            int offsetMultiplier = labeledPacketCounter / 1000000;
            return labeledPacketCounter - (offsetMultiplier * 1000000);
        } else {
            return labeledPacketCounter;
        }
    }

    // Start discovery mode to list all available IMUs
    public boolean startDiscoveryScan() {
        isDiscoveryMode = true;
        discoveredDevices.clear();
        logManager.log("=== IMU Discovery Mode Started ===");
        logManager.log("Scanning for all available Xsens DOT devices...");

        // Start scanning for 10 seconds
        boolean started = mScanner.startScan();

        if (started) {
            // Schedule automatic stop after 10 seconds
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopDiscoveryScan();
                }
            }, 5000); // 5 seconds
        }

        return started;
    }


    private void initializeSensorMap() {
        // Create a new HashMap to store the mappings
        macToTagMap = new HashMap<>();
        try {
            // Get the application's resources
            Resources res = context.getResources();

            // Read the single combined array from strings.xml
            String[] sensorMappings = res.getStringArray(R.array.sensor_mac_map);

            // Loop through each "MAC,Tag" string
            for (String mapping : sensorMappings) {
                // Split the string by the comma
                String[] parts = mapping.split(",");

                // Ensure the format is correct (exactly one comma)
                if (parts.length == 2) {
                    String macAddress = parts[0].trim(); // The MAC address
                    String tag = parts[1].trim();        // The Tag
                    macToTagMap.put(macAddress, tag);
                }
            }
            logManager.log("Sensor map initialized from strings.xml with " + macToTagMap.size() + " entries.");
        } catch (Exception e) {
            // Log an error if the string array is missing or something goes wrong
            logManager.log("Error initializing sensor map from strings.xml: " + e.getMessage());
        }
    }

    private String getGroundTruthTerrain(int packetCounter) {
        if (packetCounter >= 2000000 && packetCounter < 3000000) {
            return "Level_Walk";
        } else if (packetCounter >= 3000000 && packetCounter < 4000000) {
            return "Ramp_Ascend";
        } else if (packetCounter >= 4000000 && packetCounter < 5000000) {
            return "Ramp_Descend";
        } else if (packetCounter >= 5000000 && packetCounter < 6000000) {
            return "Stair_Ascend";
        } else if (packetCounter >= 6000000 && packetCounter < 7000000) {
            return "Stair_Descend";
        } else if (packetCounter >= 7000000 && packetCounter < 8000000) {
            return "Height1_Depth1";
        } else if (packetCounter >= 8000000 && packetCounter < 9000000) {
            return "Height1_Depth2";
        } else if (packetCounter >= 9000000 && packetCounter < 10000000) {
            return "Height2_Depth1";
        } else if (packetCounter >= 10000000 && packetCounter < 11000000) {
            return "Height2_Depth2";
        } else if (packetCounter >= 11000000 && packetCounter < 12000000) {
            return "Height3_Depth1";
        } else if (packetCounter >= 12000000 && packetCounter < 13000000) {
            return "Height3_Depth2";
        } else {
            return "NA";  // For packet counters outside defined ranges
        }
    }


    public void stopDiscoveryScan() {
        isDiscoveryMode = false;
        mScanner.stopScan();
        logManager.log("===== Discovery scan stopped." + " Found " + discoveredDevices.size() + " devices =====");
    }

    // --- MODIFICATION 4: Add a getter for the discovered devices list ---
    public ArrayList<BluetoothDevice> getDiscoveredDevices() {
        return discoveredDevicesList;
    }

    private HashMap<String, HashMap<String, Integer>> createEmptyConfusionMatrix() {
        HashMap<String, HashMap<String, Integer>> matrix = new HashMap<>();
        for (String actual : terrainTypes) {
            HashMap<String, Integer> row = new HashMap<>();
            for (String predicted : terrainTypes) row.put(predicted, 0);
            matrix.put(actual, row);
        }
        return matrix;
    }

    private void initializeConfusionMatrix() {
        if (terrainTypes == null) {
            terrainTypes = new ArrayList<>();
            terrainTypes.add("Level_Walk");
            terrainTypes.add("Ramp_Ascend");
            terrainTypes.add("Stair_Ascend");
            terrainTypes.add("Ramp_Descend");
            terrainTypes.add("Stair_Descend");
            packetToLabelMap = new HashMap<>();
        }
        if (confusionMatrixImu1 == null) confusionMatrixImu1 = createEmptyConfusionMatrix();
        if (confusionMatrixImu2 == null) confusionMatrixImu2 = createEmptyConfusionMatrix();
    }

    private void updateConfusionMatrix(String imuId, String groundTruth, String predicted) {
        initializeConfusionMatrix();
        if (!terrainTypes.contains(groundTruth) || !terrainTypes.contains(predicted)) return;
        HashMap<String, HashMap<String, Integer>> matrix = imuId.equals("IMU1") ? confusionMatrixImu1 : confusionMatrixImu2;
        HashMap<String, Integer> row = matrix.get(groundTruth);
        row.put(predicted, row.get(predicted) + 1);
    }

    private void logConfusionMatrix() {
        if (terrainTypes == null) return;
        logSingleConfusionMatrix("IMU1", confusionMatrixImu1);
        logSingleConfusionMatrix("IMU2", confusionMatrixImu2);
    }

    private void logSingleConfusionMatrix(String imuId, HashMap<String, HashMap<String, Integer>> matrix) {
        if (matrix == null || matrix.isEmpty()) {
            logManager.log(imuId + " Confusion Matrix: no data");
            return;
        }
        logManager.log("===== CONFUSION MATRIX: " + imuId + " =====");
        StringBuilder header = new StringBuilder(String.format("%-18s", "Actual\\Predicted"));
        for (String predicted : terrainTypes) header.append(String.format("%-15s", predicted));
        logManager.log(header.toString());

        int totalSamples = 0, correctPredictions = 0;
        for (String actual : terrainTypes) {
            StringBuilder row = new StringBuilder(String.format("%-18s", actual));
            HashMap<String, Integer> rowData = matrix.get(actual);
            for (String predicted : terrainTypes) {
                int count = rowData.get(predicted);
                row.append(String.format("%-15d", count));
                totalSamples += count;
                if (actual.equals(predicted)) correctPredictions += count;
            }
            logManager.log(row.toString());
        }
        if (totalSamples > 0) {
            double accuracy = (correctPredictions * 100.0) / totalSamples;
            logManager.log(imuId + " Accuracy: " + correctPredictions + "/" + totalSamples + " = " + decimalFormat.format(accuracy) + "%");
        }
    }
    @Override
    public void onDotRecordingNotification(String address, boolean isEnabled) {
        if (!isEnabled) return;
        logManager.log("onDotRecordingNotification: " + address + " isEnabled=" + isEnabled + " isExporting=" + isExporting);

        if (isExporting) {
            String imuName = address.equals(IMU5.MAC) ? "IMU5" : "IMU6";
            listener.onImuRecordingStatusChanged(imuName, "Exporting...");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (address.equals(IMU5.MAC) && IMU5RecordingManager != null) {
                    IMU5RecordingManager.requestFlashInfo();
                    logManager.log("IMU5 requestFlashInfo called (export)");
                } else if (address.equals(IMU6.MAC) && IMU6RecordingManager != null) {
                    IMU6RecordingManager.requestFlashInfo();
                    logManager.log("IMU6 requestFlashInfo called (export)");
                }
            }, 2000);
        } else {
            if (address.equals(IMU5.MAC) && !imu5EraseCompleted && IMU5RecordingManager != null) {
                IMU5RecordingManager.eraseRecordingData();
                logManager.log("IMU5 eraseRecordingData called");
                listener.onImuRecordingStatusChanged("IMU5", "Erasing...");
            } else if (address.equals(IMU6.MAC) && !imu6EraseCompleted && IMU6RecordingManager != null) {
                IMU6RecordingManager.eraseRecordingData();
                logManager.log("IMU6 eraseRecordingData called");
                listener.onImuRecordingStatusChanged("IMU6", "Erasing...");
            }
        }
    }
    public void onDotRequestFlashInfoDone(String address, int usedFlashSize, int remainingFlashSize) {
        logManager.log("onDotRequestFlashInfoDone: " + address
                + " used=" + usedFlashSize + " remaining=" + remainingFlashSize);
        if (isExporting) {
            int totalFlash = usedFlashSize + remainingFlashSize;
            double percent = (totalFlash > 0) ? (usedFlashSize * 100.0 / totalFlash) : 0;
            int totalPackets = (int)(percent * 365 * 60.0 * 60.0);
            logManager.log("percent=" + String.format("%.4f", percent) + "% → totalPackets=" + totalPackets);

            if (address.equals(IMU5.MAC)) {
                imu5TotalPackets = totalPackets;
                imu5ExportedPackets = 0;
                IMU5RecordingManager.requestFileInfo();
                logManager.log("IMU5 requestFileInfo called");
            } else if (address.equals(IMU6.MAC)) {
                imu6TotalPackets = totalPackets;
                imu6ExportedPackets = 0;
                IMU6RecordingManager.requestFileInfo();
                logManager.log("IMU6 requestFileInfo called");
            }
        }
    }
    @Override
    public void onDotRequestFileInfoDone(String address, java.util.ArrayList<com.xsens.dot.android.sdk.models.DotRecordingFileInfo> fileList, boolean success) {
        logManager.log("onDotRequestFileInfoDone: " + address + " fileCount=" + (fileList != null ? fileList.size() : 0) + " success=" + success);
        if (!isExporting) return;

        if (fileList == null || fileList.isEmpty()) {
            logManager.log("No recording files found on " + address);
            return;
        }

        com.xsens.dot.android.sdk.recording.DotRecordingManager manager = null;
        if (address.equals(IMU5.MAC)) manager = IMU5RecordingManager;
        else if (address.equals(IMU6.MAC)) manager = IMU6RecordingManager;
        if (manager == null) return;

        final com.xsens.dot.android.sdk.recording.DotRecordingManager finalManager = manager;
        final java.util.ArrayList<com.xsens.dot.android.sdk.models.DotRecordingFileInfo> finalFileList = fileList;

        // Step 1: select data fields
        recordingExportDataIds = new byte[]{
                DotRecordingManager.RECORDING_DATA_ID_TIMESTAMP,
                DotRecordingManager.RECORDING_DATA_ID_ORIENTATION,
                DotRecordingManager.RECORDING_DATA_ID_CALIBRATED_ACC,
                DotRecordingManager.RECORDING_DATA_ID_CALIBRATED_GYR
        };

        if (finalManager.selectExportedData(recordingExportDataIds)) {
            logManager.log("selectExportedData OK for " + address);
        } else {
            logManager.log("selectExportedData FAILED for " + address);
            return;
        }

        // Step 2: start exporting after 1 second
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (finalManager.startExporting(finalFileList)) {
                logManager.log("startExporting called for " + address + " with " + finalFileList.size() + " files");
            } else {
                logManager.log("startExporting FAILED for " + address);
            }
        }, 1000);
    }
    public void onDotDataExported(String address, com.xsens.dot.android.sdk.models.DotRecordingFileInfo fileInfo, com.xsens.dot.android.sdk.events.DotData dotData) {
        if (address.equals(IMU5.MAC) && imu5ExportLogger != null) {
            imu5ExportLogger.update(dotData);
            imu5ExportedPackets++;
            if (imu5ExportedPackets % 50 == 0) {
                listener.onImuRecordingStatusChanged("IMU5", "Exporting " + imu5ExportedPackets + " samples");
            }
        } else if (address.equals(IMU6.MAC) && imu6ExportLogger != null) {
            imu6ExportLogger.update(dotData);
            imu6ExportedPackets++;
            if (imu6ExportedPackets % 50 == 0) {
                listener.onImuRecordingStatusChanged("IMU6", "Exporting " + imu6ExportedPackets + " samples");
            }
        }
    }

    @Override
    public void onDotDataExported(String address, com.xsens.dot.android.sdk.models.DotRecordingFileInfo fileInfo) {
        logManager.log("File exported from " + address + ": " + fileInfo.getFileName());
    }

    @Override
    public void onDotAllDataExported(String address) {
        logManager.log("All data exported from " + address);
        if (address.equals(IMU5.MAC)) {
            imu5ExportDone = true;
            listener.onImuRecordingStatusChanged("IMU5", "Exported");
            if (imu5ExportLogger != null) imu5ExportLogger.stop();
            if (imu5ExportFilePath != null) {
                logManager.log("IMU5 file exists: " + new java.io.File(imu5ExportFilePath).exists());
                logManager.addExportFileToUploadList(new java.io.File(imu5ExportFilePath));
            }
        } else if (address.equals(IMU6.MAC)) {
            imu6ExportDone = true;
            listener.onImuRecordingStatusChanged("IMU6", "Exported");
            if (imu6ExportLogger != null) imu6ExportLogger.stop();
            if (imu6ExportFilePath != null) {
                logManager.log("IMU6 file exists: " + new java.io.File(imu6ExportFilePath).exists());
                logManager.addExportFileToUploadList(new java.io.File(imu6ExportFilePath));
            }
        }
        if (imu5ExportDone && imu6ExportDone && isExporting) {
            isExporting = false;
            listener.onExportComplete();
            logManager.log("Export complete for all recording IMUs");
        }
    }

    @Override
    public void onDotRecordingAck(String address, int recordingId, boolean isSuccess, DotRecordingState recordingState) {
        String imuName = (IMU5 != null && address.equals(IMU5.MAC)) ? "IMU5" : "IMU6";
        if (recordingId == DotRecordingManager.RECORDING_ID_START_RECORDING) {
            logManager.log(imuName + " start recording ack: " + (isSuccess ? "OK" : "FAILED") + " state=" + recordingState);
        } else if (recordingId == DotRecordingManager.RECORDING_ID_STOP_RECORDING) {
            logManager.log(imuName + " stop recording ack: " + (isSuccess ? "OK" : "FAILED") + " state=" + recordingState);
        }
    }
    @Override
    public void onDotEraseDone(String address, boolean success) {
        logManager.log("onDotEraseDone: " + address + " success=" + success);
        if (address.equals(IMU5.MAC)) {
            imu5EraseCompleted = true;
            logManager.log("IMU5 erase done");
            listener.onImuRecordingStatusChanged("IMU5", "Erased");
        } else if (address.equals(IMU6.MAC)) {
            imu6EraseCompleted = true;
            logManager.log("IMU6 erase done");
            listener.onImuRecordingStatusChanged("IMU6", "Erased");
        }
        if (imu5EraseCompleted && imu6EraseCompleted) {
            listener.onRecordingImusReady();
            logManager.log("Both recording IMUs erased and ready");
        }
    }

    @Override
    public void onDotButtonClicked(String s, long l) {}

    @Override
    public void onDotPowerSavingTriggered(String s) {}

    @Override
    public void onReadRemoteRssi(String s, int i) {}

    @Override
    public void onDotOutputRateUpdate(String s, int i) {}

    @Override
    public void onDotFilterProfileUpdate(String s, int i) {}

    @Override
    public void onDotGetFilterProfileInfo(String s, java.util.ArrayList<com.xsens.dot.android.sdk.models.FilterProfileInfo> arrayList) {}

    @Override
    public void onSyncStatusUpdate(String s, boolean b) {}



    @Override
    public void onDotGetRecordingTime(String s, int i, int i1, int i2) {}



    @Override
    public void onDotStopExportingData(String s) {}

    @Override
    public void onSyncingStarted(String s, boolean b, int i) {}

    @Override
    public void onSyncingProgress(int i, int i1) {}

    @Override
    public void onSyncingResult(String s, boolean b, int i) {}

    @Override
    public void onSyncingStopped(String s, boolean b, int i) {}

    @Override
    public void onDotServicesDiscovered(String s, int i) {}

    @Override
    public void onDotFirmwareVersionRead(String s, String s1) {}

    @Override
    public void onDotTagChanged(String s, String s1) {}

    @Override
    public void onDotBatteryChanged(String s, int i, int i1) {}

    @Override
    public void onDotHeadingChanged(String s, int i, int i1) {}

    @Override
    public void onDotRotLocalRead(String s, float[] floats) {}


}