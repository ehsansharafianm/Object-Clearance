package com.tmsimple.ObjectCrossing;

import com.xsens.dot.android.sdk.models.DotDevice;
import com.xsens.dot.android.sdk.utils.DotLogger;

import java.util.ArrayList;

public class Segment {
    public DotDevice xsDevice;
    public String[] dataOutput = {"No Data", "NA", "NA", "NA", "NA"};

    public int initializationCounter = 1;
    public int sampleCounter = 1;
    public String MAC;
    public String Name;
    public boolean isScanned = false;
    public boolean isConnected = false;
    public boolean isReady = false;

    public DotLogger normalDataLogger;

    // Calibration fields
    public double sumOfInitialRoll = 0.0;
    public double sumOfInitialGyro = 0.0;
    public double sumOfInitialAccel = 0.0;

    public double initRollValue = 0.0;
    public double initGyroValue = 0.0;
    public double initAccelValue = 0.0;

    // Data storage for processing
    public ArrayList<double[]> storedEulerAngles = new ArrayList<>();
    public ArrayList<float[]> storedQuaternions = new ArrayList<>();
    public ArrayList<double[]> storedAccelData = new ArrayList<>();
    public ArrayList<Integer> storedPacketCounters = new ArrayList<>();
    // Data storage for processing with maximum size
    private static final int MAX_STORED_SAMPLES = 500;

    // Method to store data with size limit
    public void storeData(double[] eulerAngles, float[] quats, double[] accelData, int packetCounter) {

        // Remove label offset from packet counter - HANDLE ALL LABELS
        if (packetCounter >= 1000000 && packetCounter < 2000000) {
            packetCounter = packetCounter - 1000000;
        } else if (packetCounter >= 2000000 && packetCounter < 3000000) {
            packetCounter = packetCounter - 2000000;
        } else if (packetCounter >= 3000000 && packetCounter < 4000000) {
            packetCounter = packetCounter - 3000000;
        } else if (packetCounter >= 4000000 && packetCounter < 5000000) {
            packetCounter = packetCounter - 4000000;
        } else if (packetCounter >= 5000000 && packetCounter < 6000000) {
            packetCounter = packetCounter - 5000000;
        } else if (packetCounter >= 6000000 && packetCounter < 7000000) {
            packetCounter = packetCounter - 6000000;
        }


        // Create copies to avoid reference issues
        double[] eulerCopy = new double[eulerAngles.length];
        System.arraycopy(eulerAngles, 0, eulerCopy, 0, eulerAngles.length);

        double[] accelCopy = new double[accelData.length];
        System.arraycopy(accelData, 0, accelCopy, 0, accelData.length);

        float[] quatsCopy = new float[quats.length];
        System.arraycopy(quats, 0, quatsCopy, 0, quats.length);

        // If we've reached the maximum size, remove the oldest element (index 0)
        if (storedEulerAngles.size() >= MAX_STORED_SAMPLES) {
            storedEulerAngles.remove(0);
            storedAccelData.remove(0);
            storedPacketCounters.remove(0);
            storedQuaternions.remove(0);
        }

        // Add new data at the end
        storedEulerAngles.add(eulerCopy);
        storedAccelData.add(accelCopy);
        storedPacketCounters.add(packetCounter);
        storedQuaternions.add(quatsCopy);
    }


    public Segment(String Name, String MAC){
        this.MAC = MAC;
        this.Name = Name;
    }

}