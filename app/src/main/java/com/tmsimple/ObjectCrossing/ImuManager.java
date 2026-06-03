package com.tmsimple.ObjectCrossing;

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

    private Segment IMU1, IMU2, IMU3, IMU4;
    private boolean isLoggingData = false;
    private int packetCounterOffset = 0;

    private LogManager logManager;
    int measurementMode;
    private BiasCalculation biasCalculation;
    private FeatureDetectorThroughWindow featureDetectorThroughWindow;
    private String groundTruthTerrain;
    // ========== NEW: CALIBRATION STATE TRACKING ==========
    private boolean isCalibrationComplete = false;
    private int calibrationWindowsProcessed = 0;


    private int SelectionMesurementMode = DotPayload.PAYLOAD_TYPE_CUSTOM_MODE_5;
    private boolean isDiscoveryMode = false;
    private java.util.HashSet<String> discoveredDevices = new java.util.HashSet<>();
    private HashMap<String, String> macToTagMap;
    // This list MUST use the androidx type, as this is what getDiscoveredDevices() will return.
    private final ArrayList<BluetoothDevice> discoveredDevicesList = new ArrayList<>();
    // You need the BluetoothAdapter to perform the translation.

    private UiManager uiManager;

    // Confusion Matrix Variables
    private HashMap<String, HashMap<String, Integer>> confusionMatrix;
    private List<String> terrainTypes;
    private HashMap<Integer, String> packetToLabelMap; // Track labels for each packet
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
    public void onGaitWindowCreated(String imuId, int windowNum, int startPacket, int endPacket, double duration){ //This is called when a gait cycle is detected, is coming from ZuptDetector

        logManager.log("GAIT CYCLE is Received in  ImuManager - " + imuId +
                " | Start Packet: " + startPacket +
                " | End Packet: " + endPacket +
                " | Duration: " + decimalFormat.format(duration) + "s");

        if (isLoggingData) {
            // Extract the gait window data
            GaitWindowData windowData = extractGaitWindowData(imuId, startPacket, endPacket);
            biasCalculation.processBiasCalculation(windowData, windowNum, startPacket, endPacket, groundTruthTerrain);

            // ⚠️ ADD THIS CHECK HERE:
            if (!isCalibrationComplete && biasCalculation.isCalibrated()) {
                onCalibrationJustCompleted();
            }
        }
    }

    // ========== NEW: HANDLE CALIBRATION COMPLETION ==========
    private void onCalibrationJustCompleted() {
        isCalibrationComplete = true;

        logManager.log("==========================================");
        logManager.log(">>> CALIBRATION COMPLETE IN IMU MANAGER <<<");
        logManager.log("  System is now ready for terrain classification");
        logManager.log("  Confusion matrix tracking enabled");
        logManager.log("==========================================");

        // Initialize confusion matrix now that calibration is done
        initializeConfusionMatrix();
    }
    public void onBiasCalculationComplete(String imuId, int windowNum, double biasValue, double recalculatedBias, String terrainType, ArrayList<double[]> a_corrected, ArrayList<double[]> v_corrected, ArrayList<double[]> p_corrected, int startPacket, int endPacket) {


        // ========== ONLY UPDATE CONFUSION MATRIX AFTER CALIBRATION ==========
        if (isCalibrationComplete) {
            // Only update confusion matrix if we have valid terrain data
            if (!groundTruthTerrain.equals("Unknown") && !groundTruthTerrain.equals("Standing")) {
                updateConfusionMatrix(groundTruthTerrain, terrainType);

                logManager.log("Window #" + windowNum + " - Ground Truth: " + groundTruthTerrain +
                        ", Predicted: " + terrainType +
                        (groundTruthTerrain.equals(terrainType) ? " ✓ CORRECT" : " ✗ INCORRECT"));
            } else {
                logManager.log("Window #" + windowNum + " - Skipped (Ground Truth: " + groundTruthTerrain + ")");
            }
        } else {
            // During calibration, just acknowledge window processing
            logManager.log("Window #" + windowNum + " - Calibration in progress (no classification yet)");
        }


        // Pass the data to the feature detector
        featureDetectorThroughWindow.processFeatureDetectionInWindowData(imuId, windowNum, biasValue, recalculatedBias,
                terrainType, a_corrected, v_corrected, p_corrected, startPacket, endPacket);


    }
    @Override
    public void onFeatureDetectionComplete(String imuId, int windowNum, String terrainType,
                                           ArrayList<Double> extractedFeatures, double biasValue, int startPacket, int endPacket) {

        logManager.log("Feature Detection Complete for " + imuId + " Window #" + windowNum);

        // Extract the two features
        if (extractedFeatures.size() >= 2) {
            double maxHeight = extractedFeatures.get(0);
            double maxStrideLength = extractedFeatures.get(1);


//            logManager.log("  Max Stride Length (XY): " + decimalFormat.format(maxStrideLength) + " m");
//            logManager.log("  Max Height (Z): " + decimalFormat.format(maxHeight) + " m");

            // Log to feature CSV
            if (isLoggingData) {
                logManager.logFeatureData(imuId, windowNum, terrainType, maxHeight, maxStrideLength, biasValue, startPacket, endPacket);
                // NEW: Update UI display for IMU1 only
                if (imuId.equals("IMU1") && listener != null) {
                    listener.onFeatureDetectionUpdate(windowNum, terrainType, biasValue,
                            maxHeight, maxStrideLength);
                }
            }
        } else {
            logManager.log("  ERROR: Insufficient features extracted! Expected 2, got " + extractedFeatures.size());
        }
        logManager.log("  Terrain Type: " + terrainType);
        logManager.log("=================   END    ========================");

    }



    /*===========================================================================*/
    public void setSegments(Segment IMU1, Segment IMU2, Segment IMU3, Segment IMU4) {
        this.IMU1 = IMU1;
        this.IMU2 = IMU2;
        this.IMU3 = IMU3;
        this.IMU4 = IMU4;
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

        if (IMU1 == null || IMU2 == null || IMU3 == null || IMU4 == null) {
            logManager.log("Error: Segments not initialized before scanning!");
            return;
        }


        String address = bluetoothDevice.getAddress();

        if (isDiscoveryMode) {
            if (!discoveredDevices.contains(address)) {
                discoveredDevices.add(address);

                String tagFromMap = macToTagMap.getOrDefault(address, "Unknown Tag");
                String deviceInfo = "Address= " + address + ", Tag= " + tagFromMap;
                logManager.log("IMU Found: " + deviceInfo);

                // ADD THIS LINE: Update spinners with new discovered devices
                if (uiManager != null) {
                    uiManager.updateSpinnersWithDiscoveredDevices(context, discoveredDevices);
                }
            }
            return;
        }
        // ----------


        if (address.equals(IMU1.MAC) && !IMU1.isScanned) {
            IMU1.isScanned = true;
            IMU1.xsDevice = new DotDevice(context, bluetoothDevice, this);
            IMU1.xsDevice.connect();
            IMU1.isConnected = true;
            deviceList.add(IMU1.xsDevice);

            listener.onImuScanned(IMU1.Name);
            logManager.log(IMU1.Name + " is scanned and logger is created");
        }
        else if (address.equals(IMU2.MAC) && !IMU2.isScanned) {
            IMU2.isScanned = true;
            IMU2.xsDevice = new DotDevice(context, bluetoothDevice, this);
            IMU2.xsDevice.connect();
            deviceList.add(IMU2.xsDevice);
            IMU2.isConnected = true;

            listener.onImuScanned(IMU2.Name);
            logManager.log(IMU2.Name + " is scanned and logger is created");
        }
        else if (address.equals(IMU3.MAC) && !IMU3.isScanned) {
            IMU3.isScanned = true;
            IMU3.xsDevice = new DotDevice(context, bluetoothDevice, this);
            IMU3.xsDevice.connect();
            deviceList.add(IMU3.xsDevice);
            IMU3.isConnected = true;

            listener.onImuScanned(IMU3.Name);
            logManager.log(IMU3.Name + " is scanned and logger is created");
        }
        else if (address.equals(IMU4.MAC) && !IMU4.isScanned) {
            IMU4.isScanned = true;
            IMU4.xsDevice = new DotDevice(context, bluetoothDevice, this);
            IMU4.xsDevice.connect();
            deviceList.add(IMU4.xsDevice);
            IMU4.isConnected = true;

            listener.onImuScanned(IMU4.Name);
            logManager.log(IMU4.Name + " is scanned and logger is created");
        }

        if (IMU1.isScanned && IMU2.isScanned && IMU3.isScanned && IMU4.isScanned) {
            mScanner.stopScan();
            logManager.log("All Devices are Scanned");
        }
    }
    @Override
    public void onDotInitDone(String address) {
        if (address.equals(IMU1.MAC)) {
            IMU1.isReady = true;
            IMU1.xsDevice.setOutputRate(60);
            listener.onImuReady(IMU1.Name);
            logManager.log("IMU1 IMU sample rate is : " + String.valueOf(IMU1.xsDevice.getCurrentOutputRate()));
        }
        else if (address.equals(IMU2.MAC)) {
            IMU2.isReady = true;
            IMU2.xsDevice.setOutputRate(60);
            listener.onImuReady(IMU2.Name);
            logManager.log("IMU2 IMU sample rate is : " + String.valueOf(IMU2.xsDevice.getCurrentOutputRate()));
        }
        else if (address.equals(IMU3.MAC)) {
            IMU3.isReady = true;
            IMU3.xsDevice.setOutputRate(60);
            listener.onImuReady(IMU3.Name);
            logManager.log("IMU3 IMU sample rate is : " + String.valueOf(IMU3.xsDevice.getCurrentOutputRate()));
        }
        else if (address.equals(IMU4.MAC)) {
            IMU4.isReady = true;
            IMU4.xsDevice.setOutputRate(60);
            listener.onImuReady(IMU4.Name);
            logManager.log("IMU4 IMU sample rate is : " + String.valueOf(IMU4.xsDevice.getCurrentOutputRate()));
        }
    }

    public void startSync() {

        DotSyncManager.getInstance(this).stopSyncing();

        logManager.log("Start Sync clicked");
        logManager.log("Device List size: " + deviceList.size());

        if (!IMU1.isReady || !IMU2.isReady || !IMU3.isReady || !IMU4.isReady) {
            logManager.log("Error: Devices not ready for syncing. IMU1: " + IMU1.isReady + ", IMU2: " + IMU2.isReady + ", IMU3: " + IMU3.isReady + ", IMU4: " + IMU4.isReady);
            return;
        }

        deviceList.get(0).setRootDevice(true);
        logManager.log("Root device set: " + deviceList.get(0).getTag());
        DotSyncManager.getInstance(this).startSyncing(deviceList, 100);
        logManager.log("Sync requested.");
    }
    @Override
    public void onSyncingDone(HashMap<String, Boolean> results, boolean allSuccess, int errorCode) {
        // 1ï¸âƒ£ Set measurement mode
        measurementMode = SelectionMesurementMode;
        IMU1.xsDevice.setMeasurementMode(measurementMode);
        IMU2.xsDevice.setMeasurementMode(measurementMode);
        IMU3.xsDevice.setMeasurementMode(measurementMode);
        IMU4.xsDevice.setMeasurementMode(measurementMode);

        // Optional: store if you need
        // this.measurementMode = measurementMode;

        // 2ï¸âƒ£ Log detailed mode info
        logManager.log("Measurement Mode set: "
                + IMU1.xsDevice.getMeasurementMode()
                + " / " + IMU2.xsDevice.getMeasurementMode()
                + " / " + IMU3.xsDevice.getMeasurementMode()
                + " / " + IMU4.xsDevice.getMeasurementMode());

        // 3ï¸âƒ£ Log syncing result
        logManager.log("---------- Syncing is done! ----------");

        // 4ï¸âƒ£ Notify UI through listener
        listener.onSyncingDone();
    }
    public void startMeasurement() {

        if (IMU1.xsDevice.startMeasuring()) {logManager.log("IMU1 is measuring");}
        if (IMU2.xsDevice.startMeasuring()) {logManager.log("IMU2 is measuring");}
        if (IMU3.xsDevice.startMeasuring()) {logManager.log("IMU3 is measuring");}
        if (IMU4.xsDevice.startMeasuring()) {logManager.log("IMU4 is measuring");}
    }
    public void stopMeasurement() {
        IMU1.xsDevice.stopMeasuring();
        IMU1.normalDataLogger.stop();
        IMU2.xsDevice.stopMeasuring();
        IMU2.normalDataLogger.stop();
        IMU3.xsDevice.stopMeasuring();
        IMU3.normalDataLogger.stop();
        IMU4.xsDevice.stopMeasuring();
        IMU4.normalDataLogger.stop();

        logConfusionMatrix();
    }
    public void disconnectAll() {
        IMU1.xsDevice.disconnect();
        IMU2.xsDevice.disconnect();
        IMU3.xsDevice.disconnect();
        IMU4.xsDevice.disconnect();
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
            logManager.log("IMU3 IMU is " + (isConnected ? "connected!" : "disconnected!"));
            listener.onImuConnectionChanged(IMU3.Name, isConnected);
        } else if (address.equals(IMU4.MAC)) {
            IMU4.isConnected = isConnected;
            logManager.log("IMU4 IMU is " + (isConnected ? "connected!" : "disconnected!"));
            listener.onImuConnectionChanged(IMU4.Name, isConnected);
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
        }
        else if (address.equals(IMU4.MAC)) {
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
                // IMU2.storeData(eulerAngles, quats, accelData, dotData.getPacketCounter());

            }
            else if (address.equals(IMU3.MAC)) {
                IMU3.normalDataLogger.update(dotData);
                IMU3.sampleCounter++;
                IMU3.dataOutput[3] = decimalFormat.format(dotData.getPacketCounter());
            }
            else if (address.equals(IMU4.MAC)) {
                IMU4.normalDataLogger.update(dotData);
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
            // Use calibrated data for ZUPT
            double[] calibrated = applyCalibratedData(IMU2, eulerAngles, gyroData, accelData);
            double calibratedGyro = calibrated[1];
            double calibratedAccel = calibrated[2];
            //zuptDetector.processNewImuData("IMU2", calibratedGyro, calibratedAccel, eulerAngles[0], dotData.getPacketCounter());
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
        if (labeledPacketCounter >= 1000000 && labeledPacketCounter < 10000000){
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
        } else if (packetCounter >= 1000000 && packetCounter < 2000000) {
            return "Standing";  // Based on your existing standing mode
        } else {
            return "Unknown";  // For packet counters outside defined ranges
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

    private void initializeConfusionMatrix() {
        terrainTypes = new ArrayList<>();
        terrainTypes.add("Level_Walk");
        terrainTypes.add("Ramp_Ascend");
        terrainTypes.add("Stair_Ascend");
        terrainTypes.add("Ramp_Descend");
        terrainTypes.add("Stair_Descend");

        confusionMatrix = new HashMap<>();
        for (String actual : terrainTypes) {
            HashMap<String, Integer> row = new HashMap<>();
            for (String predicted : terrainTypes) {
                row.put(predicted, 0);
            }
            confusionMatrix.put(actual, row);
        }

        packetToLabelMap = new HashMap<>();
        logManager.log("Confusion Matrix initialized");
    }

    private void updateConfusionMatrix(String groundTruth, String predicted) {
        if (confusionMatrix == null) {
            initializeConfusionMatrix();
        }

        // Make sure both terrain types are valid
        if (!terrainTypes.contains(groundTruth)) {
            logManager.log("WARNING: Unknown ground truth terrain: " + groundTruth);
            return;
        }
        if (!terrainTypes.contains(predicted)) {
            logManager.log("WARNING: Unknown predicted terrain: " + predicted);
            return;
        }

        // Update the confusion matrix
        HashMap<String, Integer> row = confusionMatrix.get(groundTruth);
        int currentCount = row.get(predicted);
        row.put(predicted, currentCount + 1);

        logManager.log("Confusion Matrix Updated: Actual=" + groundTruth + ", Predicted=" + predicted);

    }

    // Add this method to display the confusion matrix
    private void logConfusionMatrix() {
        if (confusionMatrix == null || confusionMatrix.isEmpty()) {
            logManager.log("Confusion Matrix is empty");
            return;
        }

        logManager.log("============ CONFUSION MATRIX ==============");

        // Header row
        StringBuilder header = new StringBuilder("Actual\\Predicted".format("%-20s", "Actual\\Predicted"));
        for (String predicted : terrainTypes) {
            header.append(String.format("%-15s", predicted));
        }
        logManager.log(header.toString());
        logManager.log("----------------------------------------------------------");

        // Data rows
        int totalSamples = 0;
        int correctPredictions = 0;

        for (String actual : terrainTypes) {
            StringBuilder row = new StringBuilder(String.format("%-20s", actual));
            HashMap<String, Integer> rowData = confusionMatrix.get(actual);

            for (String predicted : terrainTypes) {
                int count = rowData.get(predicted);
                row.append(String.format("%-15d", count));
                totalSamples += count;

                if (actual.equals(predicted)) {
                    correctPredictions += count;
                }
            }
            logManager.log(row.toString());
        }

        logManager.log("----------------------------------------------------------");

        // Calculate and display accuracy
        if (totalSamples > 0) {
            double accuracy = (correctPredictions * 100.0) / totalSamples;
            logManager.log("Total Samples: " + totalSamples);
            logManager.log("Correct Predictions: " + correctPredictions);
            logManager.log("Overall Accuracy: " + decimalFormat.format(accuracy) + "%");
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
    public void onDotRecordingNotification(String address, boolean isEnabled) {}

    @Override
    public void onDotEraseDone(String s, boolean b) {}

    @Override
    public void onDotRequestFlashInfoDone(String s, int i, int i1) {}

    @Override
    public void onDotRecordingAck(String s, int i, boolean b, com.xsens.dot.android.sdk.models.DotRecordingState dotRecordingState) {}

    @Override
    public void onDotGetRecordingTime(String s, int i, int i1, int i2) {}

    @Override
    public void onDotRequestFileInfoDone(String s, java.util.ArrayList<com.xsens.dot.android.sdk.models.DotRecordingFileInfo> arrayList, boolean b) {}

    @Override
    public void onDotDataExported(String s, com.xsens.dot.android.sdk.models.DotRecordingFileInfo dotRecordingFileInfo, com.xsens.dot.android.sdk.events.DotData dotData) {}

    @Override
    public void onDotDataExported(String s, com.xsens.dot.android.sdk.models.DotRecordingFileInfo dotRecordingFileInfo) {}

    @Override
    public void onDotAllDataExported(String s) {}

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