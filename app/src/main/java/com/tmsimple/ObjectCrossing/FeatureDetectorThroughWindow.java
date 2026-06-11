package com.tmsimple.ObjectCrossing;

import java.text.DecimalFormat;
import java.util.ArrayList;

public class FeatureDetectorThroughWindow {

    private LogManager logManager;
    private FeatureDetectionListener listener;

    DecimalFormat decimalFormat = new DecimalFormat("##.###");

    // Store the received data for processing
    private String currentImuId;
    private int currentWindowNum;
    private double currentBiasValue;
    private double currentRecalculatedBias;
    private String currentTerrainType;
    private ArrayList<double[]> currentAcceleration;
    private ArrayList<double[]> currentVelocity;
    private ArrayList<double[]> currentPosition;

    // Interface for callbacks
    public interface FeatureDetectionListener {
        void onFeatureDetectionComplete(String imuId, int windowNum, String terrainType,
                                        ArrayList<Double> extractedFeatures, double biasValue, int startPacket, int endPacket, String groundTruth);
    }

    // Constructor
    public FeatureDetectorThroughWindow(FeatureDetectionListener listener, LogManager logManager) {
        this.listener = listener;
        this.logManager = logManager;
    }

    // Main processing method that receives data from BiasCalculation
    public void processFeatureDetectionInWindowData(String imuId, int windowNum, double biasValue,
                                  double recalculatedBias, String terrainType,
                                  ArrayList<double[]> a_corrected,
                                  ArrayList<double[]> v_corrected,
                                  ArrayList<double[]> p_corrected, int startPacket, int endPacket, String groundTruth) {

//        logManager.log("---------------------------------------------------");
//        logManager.log("Feature Detection Started for " + imuId + " Window #" + windowNum);
//        logManager.log("  Terrain Type: " + terrainType);
//        logManager.log("  Bias Value: " + decimalFormat.format(biasValue));
//        logManager.log("  Recalculated Bias: " + decimalFormat.format(recalculatedBias));
//        logManager.log("  Data Points: " + a_corrected.size());

        // Verify data integrity
        if (a_corrected.isEmpty() || v_corrected.isEmpty() || p_corrected.isEmpty()) {
            logManager.log("ERROR: Empty data arrays received in FeatureDetector!");
            return;
        }

        // Log sample data
        /*logManager.log("First Sample:");
        logManager.log("  Acceleration: [" + decimalFormat.format(a_corrected.get(0)[0]) + ", " +
                decimalFormat.format(a_corrected.get(0)[1]) + ", " +
                decimalFormat.format(a_corrected.get(0)[2]) + "]");
        logManager.log("  Velocity: [" + decimalFormat.format(v_corrected.get(0)[0]) + ", " +
                decimalFormat.format(v_corrected.get(0)[1]) + ", " +
                decimalFormat.format(v_corrected.get(0)[2]) + "]");
        logManager.log("  Position: [" + decimalFormat.format(p_corrected.get(0)[0]) + ", " +
                decimalFormat.format(p_corrected.get(0)[1]) + ", " +
                decimalFormat.format(p_corrected.get(0)[2]) + "]");

        logManager.log("Last Sample:");
        int lastIdx = a_corrected.size() - 1;
        logManager.log("  Acceleration: [" + decimalFormat.format(a_corrected.get(lastIdx)[0]) + ", " +
                decimalFormat.format(a_corrected.get(lastIdx)[1]) + ", " +
                decimalFormat.format(a_corrected.get(lastIdx)[2]) + "]");
        logManager.log("  Velocity: [" + decimalFormat.format(v_corrected.get(lastIdx)[0]) + ", " +
                decimalFormat.format(v_corrected.get(lastIdx)[1]) + ", " +
                decimalFormat.format(v_corrected.get(lastIdx)[2]) + "]");
        logManager.log("  Position: [" + decimalFormat.format(p_corrected.get(lastIdx)[0]) + ", " +
                decimalFormat.format(p_corrected.get(lastIdx)[1]) + ", " +
                decimalFormat.format(p_corrected.get(lastIdx)[2]) + "]");

*/



        // Extract features from the trajectory data
        ArrayList<Double> extractedFeatures = extractFeatures(p_corrected);

        // Notify listener when processing is complete
        if (listener != null) {
            listener.onFeatureDetectionComplete(imuId, windowNum, terrainType, extractedFeatures, biasValue, startPacket, endPacket, groundTruth);
        }
    }

    // Extract features: maxHeight (max Z) and maxStrideLength (max XY norm)
    private ArrayList<Double> extractFeatures(ArrayList<double[]> p_corrected) {
        ArrayList<Double> features = new ArrayList<>();

        if (p_corrected.isEmpty()) {
            logManager.log("ERROR: Empty position data for feature extraction!");
            features.add(0.0); // maxHeight
            features.add(0.0); // maxStrideLength
            return features;
        }

        double maxHeight = Double.NEGATIVE_INFINITY;
        double maxStrideLength = Double.NEGATIVE_INFINITY;

        // Iterate through all position points
        for (double[] position : p_corrected) {
            // Extract Z component (height)
            double z = position[2];
            if (z > maxHeight) {
                maxHeight = z;
            }

            // Calculate XY norm (horizontal distance)
            double x = position[0];
            double y = position[1];
            double xyNorm = Math.sqrt(x * x + y * y);
            if (xyNorm > maxStrideLength) {
                maxStrideLength = xyNorm;
            }
        }


        // Add features to list
        features.add(maxHeight);
        features.add(maxStrideLength);

        return features;
    }
}