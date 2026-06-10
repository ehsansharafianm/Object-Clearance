package com.tmsimple.ObjectCrossing;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity implements ImuManagerListener {


    private Segment IMU1, IMU2, IMU3, IMU4, IMU5, IMU6;
    public String IMU1MAC = "D4:22:CD:00:63:D6"; //V2-16
    public String IMU2MAC = "D4:22:CD:00:63:8B"; //V2-17
    public String IMU3MAC = "D4:22:CD:00:9F:96"; //V2-18
    public String IMU4MAC = "D4:22:CD:00:9F:94"; //V2-19
    public String IMU5MAC = "D4:22:CD:00:9F:95"; //V2-20 (recording)
    public String IMU6MAC = "D4:22:CD:00:63:7D"; //V2-15 (recording)


    public File logFile;
    //public ArrayList<File> loggerFilePaths = new ArrayList<>();
    //public ArrayList<String> loggerFileNames = new ArrayList<>();
    public int subjectNumber = 0;
    public String subjectTitle;
    public String logFileName;
    public File logFilePath;
    public String subjectDateAndTime;
    private ImuManager imuManager;
    private LogManager logManager;
    private UiManager uiManager;
    private PermissionManager permissionManager;
    private boolean isScanning = false;

    StorageReference storageReference;

    private boolean isSyncing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_page);

        storageReference = FirebaseStorage.getInstance().getReference();
        logManager = new LogManager(this, null, null);
        permissionManager = new PermissionManager(this, logManager);
        permissionManager.requestAllPermissions();

        // Initialize main page directly
        initializeMainPage();

    }

    private  void initializeMainPage(){

        logFilePath = this.getApplicationContext().getExternalFilesDir("logs");


        imuManager = new ImuManager(this, this, logManager);
        logManager.setImuManager(imuManager);

        // Set the root
        View root = findViewById(R.id.labeling_data_root);
        uiManager = new UiManager(root, imuManager);
        uiManager.bindLabelingDataViews(getWindow().getDecorView().getRootView());
        uiManager.setupImuSpinners(this);

        imuManager.setUiManager(uiManager);

        // Before scanning all should be deactive; after each step they will be enabled

        uiManager.setButton(uiManager.scanButton,null, null, null, false);
        uiManager.setButton(uiManager.syncButton, null, null, null, false);
        uiManager.setButton(uiManager.measureButton, null, null, null, false);
        uiManager.setButton(uiManager.stopButton, null, null, null, false);
        uiManager.setButton(uiManager.disconnectButton, null, null, null, false);
        uiManager.setButton(uiManager.uploadButton, null, null, null, false);
        uiManager.setButton(uiManager.dataLogButton, null, null, null, false);
        uiManager.setButton(uiManager.exportButton, null, null, null, false);


        uiManager.setEnterSubjectNumberHandler(uiManager.enterSubjectNumber, new UiManager.OnSubjectNumberEnteredListener() {
            @Override
            public void onSubjectNumberEntered(int subjcetNu) {

                subjectTitle = "Subject " + subjcetNu;
                subjectDateAndTime = java.text.DateFormat.getDateTimeInstance().format(new Date());
                logFileName = "Logger " + subjectTitle + " " + subjectDateAndTime + ".txt";
                logFile = new File(logFilePath, logFileName);
                logManager.setLogFile(logFile, subjcetNu);
                subjectNumber = subjcetNu;
            }
        });

        // DataLogger Button
        uiManager.setDataLogButtonHandler(uiManager.dataLogButton, logManager, imuManager);


        //uiManager.bindLabelButtons();
        uiManager.setupLabelDialog(this);
        uiManager.setupLogDialog(this, logManager);
        uiManager.setupFeatureDialog(this);
        uiManager.setupImuDataDialog(this);


    }

//*//*////*//*////*//*////*//*////*//*////*//*// //*//*// //*//*// //*//*// //*//*// //*//*// //*//*// //*//*// //*//*// //*//*// //*//*//


    /// ///////////////////////////////////  Sequence of syncing  ///////////////////////////////////////////////////////////////////

    public void scanButton_onClick(View view) {
        uiManager.setButton(uiManager.listImusButton, "IMUs are selected", "#008080", null, false);

        IMU1MAC = uiManager.getSelectedIMU1Mac();
        IMU2MAC = uiManager.getSelectedIMU2Mac();
        IMU3MAC = uiManager.getSelectedIMU3Mac();
        IMU4MAC = uiManager.getSelectedIMU4Mac();
        IMU5MAC = uiManager.getSelectedIMU5Mac();
        IMU6MAC = uiManager.getSelectedIMU6Mac();

        String imu1Name = uiManager.getSelectedIMU1Name();
        String imu2Name = uiManager.getSelectedIMU2Name();
        String imu3Name = uiManager.getSelectedIMU3Name();
        String imu4Name = uiManager.getSelectedIMU4Name();
        String imu5Name = uiManager.getSelectedIMU5Name();
        String imu6Name = uiManager.getSelectedIMU6Name();

        logManager.log("IMU1: " + imu1Name + ", MAC: " + IMU1MAC);
        logManager.log("IMU2: " + imu2Name + ", MAC: " + IMU2MAC);
        logManager.log("IMU3: " + imu3Name + ", MAC: " + IMU3MAC);
        logManager.log("IMU4: " + imu4Name + ", MAC: " + IMU4MAC);
        logManager.log("IMU5: " + imu5Name + ", MAC: " + IMU5MAC);
        logManager.log("IMU6: " + imu6Name + ", MAC: " + IMU6MAC);

        String[] macs = {IMU1MAC, IMU2MAC, IMU3MAC, IMU4MAC, IMU5MAC, IMU6MAC};
        String[] names = {"IMU1", "IMU2", "IMU3", "IMU4", "IMU5", "IMU6"};
        for (int i = 0; i < macs.length; i++) {
            for (int j = i + 1; j < macs.length; j++) {
                if (macs[i].equals(macs[j])) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Selection Error")
                            .setMessage("Please choose different IMUs! " + names[i] + " and " + names[j] + " are the same.")
                            .setPositiveButton("OK", null)
                            .show();
                    uiManager.vibratePhone(500);
                    logManager.log("Error: " + names[i] + " and " + names[j] + " cannot be the same!");
                    return;
                }
            }
        }

        IMU1 = new Segment("IMU1 IMU", IMU1MAC);
        IMU2 = new Segment("IMU2 IMU", IMU2MAC);
        IMU3 = new Segment("IMU3 IMU", IMU3MAC);
        IMU4 = new Segment("IMU4 IMU", IMU4MAC);
        IMU5 = new Segment("IMU5 IMU", IMU5MAC);
        IMU6 = new Segment("IMU6 IMU", IMU6MAC);
        imuManager.setSegments(IMU1, IMU2, IMU3, IMU4, IMU5, IMU6);

        if (imuManager.startScan()) {
            isScanning = true;
            logManager.log("Scan started!");
            uiManager.setButton(uiManager.scanButton, "Scanning ...", "#AB2727", null, null);
        } else {
            logManager.log("Failed to start scan.");
        }
    }

    // Added newly for discovery


    public void listImusButton_onClick(View view) {
        // Initialize segments to prevent null pointer exception

        if (IMU1 == null || IMU2 == null || IMU3 == null || IMU4 == null || IMU5 == null || IMU6 == null) {
            IMU1 = new Segment("IMU1 IMU", IMU1MAC);
            IMU2 = new Segment("IMU2 IMU", IMU2MAC);
            IMU3 = new Segment("IMU3 IMU", IMU3MAC);
            IMU4 = new Segment("IMU4 IMU", IMU4MAC);
            IMU5 = new Segment("IMU5 IMU", IMU5MAC);
            IMU6 = new Segment("IMU6 IMU", IMU6MAC);
            imuManager.setSegments(IMU1, IMU2, IMU3, IMU4, IMU5, IMU6);
        }

        // Disable scan button during IMU discovery
        uiManager.setButton(uiManager.scanButton, null, null, null, false);

        // Start discovery scan
        if (imuManager.startDiscoveryScan()) {
            logManager.log("Discovery scan started - searching for 5 seconds...");

            // Update button to show scanning status
            runOnUiThread(() -> {
                // Update Available IMUs button to scanning state
                uiManager.setButton(uiManager.listImusButton, "Searching...", "#AB2727", "#FFFFFF", false);

                // Disable scan button during IMU discovery
                uiManager.setButton(uiManager.scanButton, null, null, null, false);
            });

            // Reset button after 10 seconds
            new android.os.Handler().postDelayed(() -> {
                runOnUiThread(() -> {
                    uiManager.setButton(uiManager.listImusButton, "Choose The IMUs", "#2196F3", "#FFFFFF",true);

                    // Re-enable scan button after discovery completes
                    uiManager.setButton(uiManager.scanButton, null, null, null, true);
                });
            }, 5000);
        } else {
            logManager.log("Failed to start discovery scan.");
        }
    }

    // ---------------
    @Override
    public void onImuConnectionChanged(String deviceName, boolean connected) {
        runOnUiThread(() -> {
            String statusText = connected ? "Connected" : "Disconnected";

            if (deviceName.equals("IMU1 IMU")) {
                // Update main page
                uiManager.setTextView(uiManager.imu1Status, statusText, null, null);

                // Update dialog
                if (uiManager.dialogImu1Status != null) {
                    uiManager.dialogImu1Status.setText(statusText);
                }

            } else if (deviceName.equals("IMU2 IMU")) {
                // Update main page
                uiManager.setTextView(uiManager.imu2Status, statusText, null, null);

                // Update dialog
                if (uiManager.dialogImu2Status != null) {
                    uiManager.dialogImu2Status.setText(statusText);
                }
            } else if (deviceName.equals("IMU3 IMU")) {
            uiManager.setTextView(uiManager.imu3Status, statusText, null, null);
            if (uiManager.dialogImu3Status != null) {
                uiManager.dialogImu3Status.setText(statusText);
            }
            } else if (deviceName.equals("IMU4 IMU")) {
                uiManager.setTextView(uiManager.imu4Status, statusText, null, null);
                if (uiManager.dialogImu4Status != null) {
                    uiManager.dialogImu4Status.setText(statusText);
                }
            } else if (deviceName.equals("IMU5 IMU")) {
                uiManager.setTextView(uiManager.imu5Status, statusText, null, null);
            } else if (deviceName.equals("IMU6 IMU")) {
                uiManager.setTextView(uiManager.imu6Status, statusText, null, null);
            }


            if (!connected && !isSyncing) {
                // Only reset buttons on DISCONNECT
                uiManager.setButton(uiManager.scanButton, "Scan", null, null, null);
                uiManager.setButton(uiManager.measureButton, "Measure", null, null, null);
                uiManager.setButton(uiManager.syncButton, "Start Sync", null, null, null);
                uiManager.setButton(uiManager.disconnectButton, "Disconnect", null, null, null);

                uiManager.setButton(uiManager.scanButton, null, "#2196F3", null, null);
                uiManager.setButton(uiManager.syncButton, null, "#2196F3", null, null);
                uiManager.setButton(uiManager.disconnectButton, null, "#AB2727", null, null);
            }
            updateAppBorderColor();
        });
    }

    @Override
    public void onImuScanned(String deviceName) {
        runOnUiThread(() -> {
            if (deviceName.equals("IMU1 IMU")) {
                uiManager.setTextView(uiManager.imu1Status, "Scanned", null, null);
                // ADD THIS:
                if (uiManager.dialogImu1Status != null) {
                    uiManager.dialogImu1Status.setText("Scanned");
                }
            } else if (deviceName.equals("IMU2 IMU")) {
                uiManager.setTextView(uiManager.imu2Status, "Scanned", null, null);
                // ADD THIS:
                if (uiManager.dialogImu2Status != null) {
                    uiManager.dialogImu2Status.setText("Scanned");
                }
            } else if (deviceName.equals("IMU3 IMU")) {
                uiManager.setTextView(uiManager.imu3Status, "Scanned", null, null);
                if (uiManager.dialogImu3Status != null) {
                    uiManager.dialogImu3Status.setText("Scanned");
                }
            } else if (deviceName.equals("IMU4 IMU")) {
                uiManager.setTextView(uiManager.imu4Status, "Scanned", null, null);
                if (uiManager.dialogImu4Status != null) {
                    uiManager.dialogImu4Status.setText("Scanned");
                }
            } else if (deviceName.equals("IMU5 IMU")) {
                uiManager.setTextView(uiManager.imu5Status, "Scanned", null, null);
            } else if (deviceName.equals("IMU6 IMU")) {
                uiManager.setTextView(uiManager.imu6Status, "Scanned", null, null);
            }
            if (isScanning) {
                uiManager.setButton(uiManager.scanButton, "Scanning...", "#AB2727", null, null);
            }
            // UPDATE APP BORDER COLOR
            updateAppBorderColor();

        });
    }

    @Override
    public void onImuReady(String deviceName) {
        runOnUiThread(() -> {
            if (deviceName.equals("IMU1 IMU")) {
                uiManager.setTextView(uiManager.imu1Status, "Ready", null, null);
                // ADD THIS:
                if (uiManager.dialogImu1Status != null) {
                    uiManager.dialogImu1Status.setText("Ready");
                }
            } else if (deviceName.equals("IMU2 IMU")) {
                uiManager.setTextView(uiManager.imu2Status, "Ready", null, null);
                if (uiManager.dialogImu2Status != null) {
                    uiManager.dialogImu2Status.setText("Ready");
                }
            } else if (deviceName.equals("IMU3 IMU")) {
                uiManager.setTextView(uiManager.imu3Status, "Ready", null, null);
                if (uiManager.dialogImu3Status != null) {
                    uiManager.dialogImu3Status.setText("Ready");
                }
            } else if (deviceName.equals("IMU4 IMU")) {
                uiManager.setTextView(uiManager.imu4Status, "Ready", null, null);
                if (uiManager.dialogImu4Status != null) {
                    uiManager.dialogImu4Status.setText("Ready");
                }
            } else if (deviceName.equals("IMU5 IMU")) {
                uiManager.setTextView(uiManager.imu5Status, "Ready", null, null);
            } else if (deviceName.equals("IMU6 IMU")) {
                uiManager.setTextView(uiManager.imu6Status, "Ready", null, null);
            }
            if (IMU1.isReady && IMU2.isReady && IMU3.isReady && IMU4.isReady) {
                uiManager.setButton(uiManager.scanButton, "Scanned", "#008080", null, true);
            }
            // UPDATE APP BORDER COLOR
            updateAppBorderColor();
        });
    }


    public void syncButton_onClick(View view) {
        isSyncing = true;
        runOnUiThread(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                uiManager.setButton(uiManager.syncButton, "Syncing...", "#AB2727", null, null);
                uiManager.setTextView(uiManager.imu1Status, "Syncing", null, null);
                uiManager.setTextView(uiManager.imu2Status, "Syncing", null, null);
                uiManager.setTextView(uiManager.imu3Status, "Syncing", null, null);
                uiManager.setTextView(uiManager.imu4Status, "Syncing", null, null);
                uiManager.setTextView(uiManager.imu5Status, "Syncing", null, null);
                uiManager.setTextView(uiManager.imu6Status, "Syncing", null, null);

                // ADD THIS:
                if (uiManager.dialogImu1Status != null) {
                    uiManager.dialogImu1Status.setText("Syncing");
                }
                if (uiManager.dialogImu2Status != null) {
                    uiManager.dialogImu2Status.setText("Syncing");
                }
                if (uiManager.dialogImu3Status != null) {
                    uiManager.dialogImu3Status.setText("Syncing");
                }
                if (uiManager.dialogImu4Status != null) {
                    uiManager.dialogImu4Status.setText("Syncing");
                }
                // UPDATE APP BORDER COLOR
                updateAppBorderColor();
            }
        });

        imuManager.startSync();
    }

    @Override
    public void onSyncingDone() {
        isSyncing = false;

        uiManager.setButton(uiManager.measureButton, null, null, null, true);
        uiManager.setButton(uiManager.disconnectButton, null, null, null, true);
        runOnUiThread(() -> {
            uiManager.setButton(uiManager.syncButton, "Synced", "#008080", null, null);
            uiManager.setButton(uiManager.measureButton, null, null, null, true);
            uiManager.setButton(uiManager.disconnectButton, null, null, null, true);

            // ADD THIS:
            uiManager.setTextView(uiManager.imu1Status, "Synced", null, null);
            uiManager.setTextView(uiManager.imu2Status, "Synced", null, null);
            uiManager.setTextView(uiManager.imu3Status, "Synced", null, null);
            uiManager.setTextView(uiManager.imu4Status, "Synced", null, null);
            uiManager.setTextView(uiManager.imu5Status, "Synced", null, null);
            uiManager.setTextView(uiManager.imu6Status, "Synced", null, null);

            if (uiManager.dialogImu1Status != null) {
                uiManager.dialogImu1Status.setText("Synced");
            }
            if (uiManager.dialogImu2Status != null) {
                uiManager.dialogImu2Status.setText("Synced");
            }

            if (uiManager.dialogImu3Status != null) {
                uiManager.dialogImu3Status.setText("Synced");
            }
            if (uiManager.dialogImu4Status != null) {
                uiManager.dialogImu4Status.setText("Synced");
            }

            logManager.log("(Main): --- Syncing is done! ---- ");
            // UPDATE APP BORDER COLOR
            updateAppBorderColor();
        });
    }
    @Override
    public void onImuRecordingStatusChanged(String deviceName, String status) {
        runOnUiThread(() -> {
            if (deviceName.equals("IMU5 IMU") || deviceName.equals("IMU5")) {
                uiManager.setTextView(uiManager.imu5RecStatus, status, null, null);
            } else if (deviceName.equals("IMU6 IMU") || deviceName.equals("IMU6")) {
                uiManager.setTextView(uiManager.imu6RecStatus, status, null, null);
            }

            // Enable upload only after both are exported
            String imu5Rec = uiManager.imu5RecStatus != null ? uiManager.imu5RecStatus.getText().toString() : "";
            String imu6Rec = uiManager.imu6RecStatus != null ? uiManager.imu6RecStatus.getText().toString() : "";
            if (imu5Rec.equals("Exported") && imu6Rec.equals("Exported")) {
                uiManager.setButton(uiManager.exportButton, "Exported", "#008080", null, false);
                uiManager.setButton(uiManager.uploadButton, null, null, null, true);
            }
        });
    }

    public void measureButton_onClick(View view) {

        // Play sound
        MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.measuring_audio);
        mediaPlayer.setOnCompletionListener(mp -> mp.release());
        mediaPlayer.start();

        // Vibrate phone
        uiManager.vibratePhone(100);  // Vibrate for 500ms

        uiManager.setButton(uiManager.stopButton, null, null, null, true);
        uiManager.setButton(uiManager.dataLogButton, null, null, null, true);
        runOnUiThread(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                uiManager.setButton(uiManager.measureButton, "Measuring", "#008080", null, null);
                // UPDATE APP BORDER COLOR to indicate measuring
                uiManager.setAppBorderColor("#2196F3"); // Light Blue for measuring
            }
        });

        if (IMU1.xsDevice != null)
            IMU1.normalDataLogger = logManager.createDataLog("IMU1", IMU1.xsDevice, subjectTitle, subjectNumber, imuManager);
        if (IMU2.xsDevice != null)
            IMU2.normalDataLogger = logManager.createDataLog("IMU2", IMU2.xsDevice, subjectTitle, subjectNumber, imuManager);
        if (IMU3.xsDevice != null)
            IMU3.normalDataLogger = logManager.createDataLog("IMU3", IMU3.xsDevice, subjectTitle, subjectNumber, imuManager);
        if (IMU4.xsDevice != null)
            IMU4.normalDataLogger = logManager.createDataLog("IMU4", IMU4.xsDevice, subjectTitle, subjectNumber, imuManager);

        logManager.initializeFeatureLogs(subjectTitle, subjectNumber);

        imuManager.startMeasurement();
    }
/////////////////// Callbacks ////////////////////////


    @Override
    public void onDataUpdated(String deviceAddress, double[] eulerAngles) {

        runOnUiThread(() -> {
            if (deviceAddress.equals(IMU1.MAC)) {
                // Update main page
                uiManager.setTextView(uiManager.imu1Roll, String.format(Locale.US, "%.1f deg", eulerAngles[0]), null, null);
                uiManager.setTextView(uiManager.imu1Index, String.valueOf(IMU1.dataOutput[3]), null, null);
                uiManager.setTextView(uiManager.imu1Battery, IMU1.xsDevice.getBatteryPercentage() + "%", null, null);

                // Update dialog
                if (uiManager.dialogImu1Roll != null) {
                    uiManager.dialogImu1Roll.setText(String.format(Locale.US, "%.1f deg", eulerAngles[0]));
                }
                if (uiManager.dialogImu1Index != null) {
                    uiManager.dialogImu1Index.setText(String.valueOf(IMU1.dataOutput[3]));
                }
                if (uiManager.dialogImu1Battery != null) {
                    uiManager.dialogImu1Battery.setText(IMU1.xsDevice.getBatteryPercentage() + "%");
                }

            } else if (deviceAddress.equals(IMU2.MAC)) {
                // Update main page
                uiManager.setTextView(uiManager.imu2Roll, String.format(Locale.US, "%.1f deg", eulerAngles[0]), null, null);
                uiManager.setTextView(uiManager.imu2Index, String.valueOf(IMU2.dataOutput[3]), null, null);
                uiManager.setTextView(uiManager.imu2Battery, IMU2.xsDevice.getBatteryPercentage() + "%", null, null);

                // Update dialog
                if (uiManager.dialogImu2Roll != null) {
                    uiManager.dialogImu2Roll.setText(String.format(Locale.US, "%.1f deg", eulerAngles[0]));
                }
                if (uiManager.dialogImu2Index != null) {
                    uiManager.dialogImu2Index.setText(String.valueOf(IMU2.dataOutput[3]));
                }
                if (uiManager.dialogImu2Battery != null) {
                    uiManager.dialogImu2Battery.setText(IMU2.xsDevice.getBatteryPercentage() + "%");
                }
            } else if (deviceAddress.equals(IMU3.MAC)) {
                uiManager.setTextView(uiManager.imu3Index, String.valueOf(IMU3.dataOutput[3]), null, null);
                if (uiManager.dialogImu3Roll != null) {
                    uiManager.dialogImu3Roll.setText(String.format(Locale.US, "%.1f deg", eulerAngles[0]));
                }
                if (uiManager.dialogImu3Index != null) {
                    uiManager.dialogImu3Index.setText(String.valueOf(IMU3.dataOutput[3]));
                }
                if (uiManager.dialogImu3Battery != null) {
                    uiManager.dialogImu3Battery.setText(IMU3.xsDevice.getBatteryPercentage() + "%");
                }
            } else if (deviceAddress.equals(IMU4.MAC)) {
                uiManager.setTextView(uiManager.imu4Index, String.valueOf(IMU4.dataOutput[3]), null, null);
                if (uiManager.dialogImu4Roll != null) {
                    uiManager.dialogImu4Roll.setText(String.format(Locale.US, "%.1f deg", eulerAngles[0]));
                }
                if (uiManager.dialogImu4Index != null) {
                    uiManager.dialogImu4Index.setText(String.valueOf(IMU4.dataOutput[3]));
                }
                if (uiManager.dialogImu4Battery != null) {
                    uiManager.dialogImu4Battery.setText(IMU4.xsDevice.getBatteryPercentage() + "%");
                }
            }
        });
    }
    @Override
    public void onZuptDataUpdated(String deviceAddress, double gyroMag, double linearAccelMag) {
    }
    @Override
    public void onFeatureDetectionUpdate(String imuId, int windowNum, String terrainType, double biasValue, double maxHeight, double maxStride) {
        runOnUiThread(() -> uiManager.updateFeatureDisplay(imuId, windowNum, terrainType, biasValue, maxHeight, maxStride));
    }

    @Override
    public void onLogMessage(String message) {
        logManager.log(message);
    }

    /*
    /////////////////////////////////////////////////////////      Functions     //////////////////////////////
     */


    /*
    ///////////////////////////////////////////////////////         Buttons      //////////////////////
     */
    public void disconnectButton_onClick(View view) {
        // measureButton.setEnabled(false);
        uiManager.setButton(uiManager.measureButton, null, null, null, false);
        // dataLogButton.setEnabled(false);
        uiManager.setButton(uiManager.dataLogButton, null, null, null, false);
        runOnUiThread(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                // disconnectButton.setText("Disconnecting...");
                // disconnectButton.setBackgroundColor(Color.parseColor("#F60000"));
                uiManager.setButton(uiManager.disconnectButton, "Disconnecting...", "#F60000", null, false);
            }
        });
        imuManager.disconnectAll();
    }
    public void stopButton_onClick(View view) { // After measuring, the dots should be stopped to for data logging

        uiManager.setButton(uiManager.stopButton, null, null, null, false);
        uiManager.setButton(uiManager.dataLogButton, null, null, null, false);
        uiManager.setButton(uiManager.measureButton, "Stopped", null, null, false);
        uiManager.setButton(uiManager.uploadButton, null, null, null, true);
        uiManager.setButton(uiManager.exportButton, null, null, null, true);

        logManager.log("Stopping");
        imuManager.stopMeasurement();

        logManager.closeFeatureLogs();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                uiManager.setButton(uiManager.measureButton, "Stopped", null, null, null);
                // UPDATE APP BORDER COLOR to indicate stopped
                uiManager.setAppBorderColor("#AB2727"); // Red for stopped
            }
        });

    }
    public void exportButton_onClick(View view) {
        uiManager.setButton(uiManager.exportButton, "Exporting...", "#AB2727", null, false);
        logManager.log("Export started for recording IMUs");
        imuManager.exportRecordingData(subjectNumber);
    }

    public void uploadButton_onClick(View view) {
        for (int i = 0; i < logManager.loggerFileNames.size(); i++) {
            logManager.log("Uploading data to cloud : " + logManager.loggerFileNames.get(i));
            uploadLogFileToCloud(Uri.fromFile(logManager.loggerFilePaths.get(i)), logManager.loggerFileNames.get(i));
        }

        uploadLogFileToCloud(Uri.fromFile(logFile), logFileName);
    }

    private void uploadLogFileToCloud(Uri file, String fileName) {

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("File is loading...");
        progressDialog.show();

        //This is where you can change the upload location and name
        StorageReference reference = storageReference.child("logs/Subject " + Integer.toString(subjectNumber) + "/" + fileName);

        reference.putFile(file).
                addOnFailureListener(new OnFailureListener() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        //uploadButton.setText("Uploading Failed");
                        //uploadButton.setBackgroundColor(Color.parseColor("#f63e00"));
                        uiManager.setButton(uiManager.uploadButton, "Uploading Failed", "#f63e00", null, null);
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        runOnUiThread(new Runnable() {
                            @SuppressLint("SetTextI18n")
                            @Override
                            public void run() {
                                // uploadButton.setText("Uploading Done");
                                // uploadButton.setBackgroundColor(Color.parseColor("#0af056"));
                                uiManager.setButton(uiManager.uploadButton, "Uploaded", "#008080", null, null);
                            }
                        });
                        progressDialog.dismiss();
                    }
                }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onProgress(@NonNull UploadTask.TaskSnapshot snapshot) {
                        double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                        progressDialog.setMessage("File Uploaded.." + (int) progress + "%");
                    }
                });

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionManager != null) {
            permissionManager.handlePermissionResult(requestCode, grantResults);
        }
    }

    private void updateAppBorderColor() {
        String imu1StatusText = uiManager.imu1Status.getText().toString();
        String imu2StatusText = uiManager.imu2Status.getText().toString();
        String imu5StatusText = uiManager.imu5Status != null ? uiManager.imu5Status.getText().toString() : "-";
        String imu6StatusText = uiManager.imu6Status != null ? uiManager.imu6Status.getText().toString() : "-";

        String borderColor;

        if (imu1StatusText.equals("Synced") && imu2StatusText.equals("Synced")) {
            borderColor = "#052A64";
        } else if (imu1StatusText.equals("Ready") && imu2StatusText.equals("Ready")) {
            borderColor = "#052A64";
        } else if (imu1StatusText.equals("Syncing") || imu2StatusText.equals("Syncing")) {
            borderColor = "#FF9933";
        } else if ((imu1StatusText.equals("Connected") || imu1StatusText.equals("Scanned")) &&
                (imu2StatusText.equals("Connected") || imu2StatusText.equals("Scanned"))) {
            borderColor = "#052A64";
        } else if (imu1StatusText.equals("Disconnected") || imu2StatusText.equals("Disconnected") ||
                imu5StatusText.equals("Disconnected") || imu6StatusText.equals("Disconnected") ||
                imu1StatusText.equals("-") || imu2StatusText.equals("-")) {
            borderColor = "#AB2727";
        } else {
            borderColor = "#9E9E9E";
        }

        uiManager.setAppBorderColor(borderColor);
    }

    @Override
    public void onRecordingImusReady() {
        runOnUiThread(() -> {
            uiManager.setButton(uiManager.syncButton, null, null, null, true);
            logManager.log("IMU5 and IMU6 erase complete — sync button enabled");
        });
    }
    @Override
    public void onExportComplete() {
        runOnUiThread(() -> {
            uiManager.setButton(uiManager.exportButton, "Exported", "#008080", null, false);
            uiManager.setButton(uiManager.uploadButton, null, null, null, true);
            logManager.log("Export complete — files ready for upload");
        });
    }


}