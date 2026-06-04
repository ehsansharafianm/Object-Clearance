package com.tmsimple.ObjectCrossing;

import android.view.View;
import android.widget.TextView;
import android.content.Context;

import com.xsens.dot.android.sdk.models.DotDevice;
import com.xsens.dot.android.sdk.utils.DotLogger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class LogManager {

    private TextView logContents;
    private Context context;
    private File logFile;
    public String Version = "v1.2";
    public int SAMPLE_RATE = 60; //Hz
    public ArrayList<File> loggerFilePaths = new ArrayList<>();
    public ArrayList<String> loggerFileNames = new ArrayList<>();
    private ImuManager imuManager;

    private HashMap<String, File> featureLogFiles = new HashMap<>();
    private HashMap<String, BufferedWriter> featureLogWriters = new HashMap<>();
    private boolean isFeatureLoggingActive = false;
    private String currentLogDirectory;

    public LogManager(Context context, TextView logContents, File logFile) {
        this.context = context;
        this.logContents = logContents;
        this.logFile = logFile;
    }
    public void setLogContents(TextView logContents) {
        this.logContents = logContents;
    }
    public void setImuManager(ImuManager imuManager) {
        this.imuManager = imuManager;
    }

    // You can update the log file later if needed
    public void setLogFile(File logFile, int subjectNumber) {
        this.logFile = logFile;
        log("Subject number set: " + subjectNumber);
        log("Log File Created");
    }
    public void setLogVisible(boolean visible) {
        if (logContents != null) {
            logContents.post(() -> {
                logContents.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            });
        }
    }

    public void log(String message) {
        String logMessage = java.text.DateFormat.getDateTimeInstance().format(new Date()) + ": " + message + "\n";

        // Append to UI on UI thread
        if (logContents != null) {
            logContents.post(() -> {
                try {
                    logContents.append("\n" + message);
                    int scrollAmount = logContents.getLayout().getLineTop(logContents.getLineCount()) - logContents.getHeight();
                    logContents.scrollTo(0, Math.max(scrollAmount, 0));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Write to file
        if (logFile != null) {
            try (FileOutputStream stream = new FileOutputStream(logFile, true)) {
                stream.write(logMessage.getBytes());
            } catch (IOException | NullPointerException e) {
                if (logContents != null) {
                    logContents.post(() -> logContents.append("\nError: Log file not found"));
                }
            }
        }
    }
    public DotLogger createDataLog(String ImuID, DotDevice device, String subjectTitle, int subjectNumber,  ImuManager imuManager) {

        try {
            File loggerFileFolder;
            String loggerFileName;
            loggerFileFolder = context.getApplicationContext().getExternalFilesDir(subjectTitle + "/" + device.getTag());
            currentLogDirectory = loggerFileFolder.getPath();
            loggerFileName = ImuID + "_" + device.getTag() + "_" + java.text.DateFormat.getDateTimeInstance().format(new Date()) + ", Subject " + subjectNumber + ".csv";
            String path = loggerFileFolder.getPath() + "/" + loggerFileName;
            File loggerFile = new File(path);

            loggerFilePaths.add(loggerFile);
            loggerFileNames.add(loggerFileName);
            log(loggerFileName + " created");

            DotLogger logger = new DotLogger(context.getApplicationContext(), 1, imuManager.getMeasurementMode(), path, device.getTag(),
                    device.getFirmwareVersion(), true, SAMPLE_RATE, null, Version, 0);
            return logger;


        } catch (NullPointerException e) {
            log("Error with creation of logger with" + device.getName());
            DotLogger logger = new DotLogger(context.getApplicationContext(), 1, imuManager.getMeasurementMode(), "", device.getTag(),
                    device.getFirmwareVersion(), true, SAMPLE_RATE, null,
                    Version, 0);
            return logger;
        }
    }

    public void createFeatureLog(String imuId, String subjectTitle, int subjectNumber) {
        try {
            File featureLogFolder = context.getApplicationContext().getExternalFilesDir(subjectTitle);
            String featureLogFileName = "FeatureLog_" + imuId + "_Subject" + subjectNumber + "_" +
                    java.text.DateFormat.getDateTimeInstance().format(new Date()) + ".csv";
            String path = featureLogFolder.getPath() + "/" + featureLogFileName;
            File featureLogFile = new File(path);

            // Create the file and write header
            BufferedWriter featureLogWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(featureLogFile, false)));

            // Write CSV header
            featureLogWriter.write("Window_Number,Terrain_Type,Start_Packet,End_Packet,Max_Height_m,Max_Stride_Length_m,Bias_Value\n");
            featureLogWriter.flush();

            // Store in HashMaps
            featureLogFiles.put(imuId, featureLogFile);
            featureLogWriters.put(imuId, featureLogWriter);

            // Add to the list of files to upload
            loggerFilePaths.add(featureLogFile);
            loggerFileNames.add(featureLogFileName);

            log("Feature log created for " + imuId + ": " + featureLogFileName);

        } catch (IOException e) {
            log("Error creating feature log for " + imuId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void initializeFeatureLogs(String subjectTitle, int subjectNumber) {
        createFeatureLog("IMU1", subjectTitle, subjectNumber);
        createFeatureLog("IMU2", subjectTitle, subjectNumber);
        isFeatureLoggingActive = true;
        log("Feature logging initialized for all IMUs");
    }

    public void logFeatureData(String imuId, int windowNum, String terrainType,
                               double maxHeight, double maxStrideLength, double biasValue, int startPacket, int endPacket) {
        if (!isFeatureLoggingActive) {
            log("WARNING: Feature logging not active!");
            return;
        }

        BufferedWriter writer = featureLogWriters.get(imuId);
        if (writer == null) {
            log("WARNING: No feature log writer found for " + imuId);
            return;
        }

        try {
            // Format: Window_Number,Terrain_Type,Start_Packet,End_Packet,Max_Height_m,Max_Stride_Length_m,Bias_Value
            String row = String.format("%d,%s,%d,%d,%.4f,%.4f,%.4f\n",
                    windowNum, terrainType, startPacket, endPacket,
                    maxHeight, maxStrideLength, biasValue);

            writer.write(row);
            writer.flush(); // Flush immediately to ensure data is written

            log("Feature data logged for " + imuId + ": Window #" + windowNum + " | " + terrainType);

        } catch (IOException e) {
            log("Error writing feature data for " + imuId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    public void closeFeatureLogs() {
        for (String imuId : featureLogWriters.keySet()) {
            BufferedWriter writer = featureLogWriters.get(imuId);
            if (writer != null) {
                try {
                    writer.close();
                    log("Feature log closed for " + imuId);
                } catch (IOException e) {
                    log("Error closing feature log for " + imuId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        featureLogWriters.clear();
        featureLogFiles.clear();
        isFeatureLoggingActive = false;
        log("All feature logs closed successfully");
    }
    public String getLogDirectory() {
        return context.getApplicationContext().getExternalFilesDir("logs").getPath();
    }
    public void addExportFileToUploadList(File file) {
        loggerFilePaths.add(file);
        loggerFileNames.add(file.getName());
        log("Export file added to upload list: " + file.getName());
    }



}
