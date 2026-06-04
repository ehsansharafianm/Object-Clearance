package com.tmsimple.ObjectCrossing;

public interface ImuManagerListener {
    void onImuConnectionChanged(String deviceName, boolean connected);
    void onImuScanned(String deviceName);
    void onImuReady(String deviceName);
    void onSyncingDone();
    void onDataUpdated(String deviceName, double[] eulerAngles);
    void onZuptDataUpdated(String deviceName, double gyroMag, double linearAccelMag);
    void onLogMessage(String message);
    void onFeatureDetectionUpdate(int windowNum, String terrainType, double biasValue,
                                  double maxHeight, double maxStride);
    void onImuRecordingStatusChanged(String deviceName, String status);
    void onRecordingImusReady();

    void onExportComplete();

}
