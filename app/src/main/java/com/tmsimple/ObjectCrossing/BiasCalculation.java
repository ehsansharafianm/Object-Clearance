package com.tmsimple.ObjectCrossing;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;

public class BiasCalculation {

    private LogManager logManager;
    private BiasCalculationListener listener;

    DecimalFormat decimalFormat = new DecimalFormat("##.###");


    // ========== CALIBRATION PHASE VARIABLES ==========
    private boolean isCalibrated = false;
    private int calibrationWindowCount = 0;
    private static final int REQUIRED_CALIBRATION_WINDOWS = 10;
    private ArrayList<Double> calibrationBiasValues = new ArrayList<>();

    // Calculated calibration parameters
    private double mu_HS = 0.0;  // Mean/median of HS bias
    private double sigma_HS = 0.0;  // Robust standard deviation

    // Optimal k values (from your previous optimization)
    private static final double K1 = 1.2;  // Multiplier for ramp threshold
    private static final double K2 = 3.2;  // Multiplier for stair threshold

    // Calculated thresholds (will be set after calibration)
    private double T_RAMP_ASCEND = 0.0;
    private double T_STAIR_ASCEND = 0.0;
    private double T_RAMP_DESCEND = 0.0;
    private double T_STAIR_DESCEND = 0.0;

    // OLD FIXED THRESHOLDS (kept for reference, won't be used after calibration)
    private static final double THRESHOLD_VALUE1 = 0.3;
    private static final double THRESHOLD_VALUE2 = 0.11;
    private static final double THRESHOLD_VALUE3 = -0.11;
    private static final double THRESHOLD_VALUE4 = -0.40;


    // Keep only ONE interface
    public interface BiasCalculationListener {
        void onBiasCalculationComplete(String imuId, int windowNum, double biasValue, double recalculatedBias, String terrainType, ArrayList<double[]> a_corrected, ArrayList<double[]> v_corrected, ArrayList<double[]> p_corrected, int startPacket, int endPacket);
    }

    // Constructor
    public BiasCalculation(BiasCalculationListener listener, LogManager logManager) {
        this.listener = listener;
        this.logManager = logManager;
    }
    // NEW: Method to check calibration status
    public boolean isCalibrated() {
        return isCalibrated;
    }

    // NEW: Method to get calibration progress
    public int getCalibrationProgress() {
        return calibrationWindowCount;
    }

    // NEW: Method to get calibration parameters
    public double getMu() {
        return mu_HS;
    }

    public double getSigma() {
        return sigma_HS;
    }


    // Method to process bias calculation
    public void processBiasCalculation(ImuManager.GaitWindowData windowData, int windowNum, int startPacket, int endPacket, String groundTruthTerrain) {

        if (windowData.eulerAnglesInWindow.isEmpty()) {
            logManager.log("ERROR: Empty window data received!");
            return;
        }

        int sampleCount = windowData.packetCountersInWindow.size();
        /*logManager.log("------------------------------------------");
        logManager.log("Processing " + sampleCount + " samples from packet " +
                windowData.startPacket + " to " + windowData.endPacket);*/

        // Extract the arrays from windowData
        ArrayList<Integer> packetCounters = windowData.packetCountersInWindow;
        ArrayList<double[]> eulerAngles = windowData.eulerAnglesInWindow;
        ArrayList<double[]> accelData = windowData.accelDataInWindow;
        ArrayList<float[]> quaternions = windowData.quaternionsInWindow;

        // Constants
        double gravity = 9.80665; // m/s²
        double sampleRate = 60.0; // Hz
        double delta_t = 1.0 / sampleRate;
        double gain_hs = 1.00;

        // Initialize arrays for integration
        ArrayList<double[]> v = new ArrayList<>();
        ArrayList<double[]> p = new ArrayList<>();
        ArrayList<double[]> a = new ArrayList<>();

        // Initial conditions
        v.add(new double[]{0, 0, 0});
        p.add(new double[]{0, 0, 0});

        // Bias Jacobian matrices
        double[][] Bv = new double[3][3];
        double[][] Bp = new double[3][3];

        // First integration: no bias
        for (int k = 0; k < sampleCount; k++) {
            double[] currentEuler = eulerAngles.get(k);
            double[] currentAccel = accelData.get(k);
            float[] currentQuat = quaternions.get(k);


            // Calculate Euler angles from quaternion using ZYX convention (matching MATLAB)
            double[] eulerZYX = quaternionToEulerZYX(currentQuat);
            double roll  = eulerZYX[0];
            double pitch = eulerZYX[1];
            double yaw   = eulerZYX[2];

            double[][] rotationMatrix = calculateRotationMatrix(roll, pitch, yaw);


            // double[][] rotationMatrixTranspose = transposeMatrix(rotationMatrix);

            double[] acc_world = multiplyMatrixVector(rotationMatrix, currentAccel);
            //double[] acc_world = multiplyMatrixVector(rotationMatrixTranspose, currentAccel);

            acc_world[0] = acc_world[0] - 0;
            acc_world[1] = acc_world[1] - 0;
            acc_world[2] = acc_world[2] - gravity;

            a.add(acc_world);

            double[] v_prev = v.get(k);
            double[] p_prev = p.get(k);

            double[] v_new = new double[3];
            v_new[0] = v_prev[0] + acc_world[0] * delta_t;
            v_new[1] = v_prev[1] + acc_world[1] * delta_t;
            v_new[2] = v_prev[2] + acc_world[2] * delta_t;
            v.add(v_new);

            double[] p_new = new double[3];
            p_new[0] = p_prev[0] + v_prev[0] * delta_t + 0.5 * acc_world[0] * delta_t * delta_t;
            p_new[1] = p_prev[1] + v_prev[1] * delta_t + 0.5 * acc_world[1] * delta_t * delta_t;
            p_new[2] = p_prev[2] + v_prev[2] * delta_t + 0.5 * acc_world[2] * delta_t * delta_t;
            p.add(p_new);


            // Log the calculated values for this sample
            /*if (k == 0 || k == sampleCount - 1) {
                logManager.log("---------------------------");
                logManager.log("packetCounter: " + packetCounters.get(k));
                logManager.log("Sample " + k + ":");
                logManager.log("  measuredQaternion: [" + decimalFormat.format(currentQuat[0]) + ", " + decimalFormat.format(currentQuat[1]) + ", " +
                        decimalFormat.format(currentQuat[2]) + ", " + decimalFormat.format(currentQuat[3]) + "]");
                logManager.log("  eulerXsens: [" + decimalFormat.format(currentEuler[0]) + ", " + decimalFormat.format(currentEuler[1]) + ", " + decimalFormat.format(currentEuler[2]) + "]");
                logManager.log("  rotationMatrix: [" + decimalFormat.format(rotationMatrix[0][0]) + " " + decimalFormat.format(rotationMatrix[0][1]) + " " + decimalFormat.format(rotationMatrix[0][2]) + "]");
                logManager.log("                  [" + decimalFormat.format(rotationMatrix[1][0]) + " " + decimalFormat.format(rotationMatrix[1][1]) + " " + decimalFormat.format(rotationMatrix[1][2]) + "]");
                logManager.log("                  [" + decimalFormat.format(rotationMatrix[2][0]) + " " + decimalFormat.format(rotationMatrix[2][1]) + " " + decimalFormat.format(rotationMatrix[2][2]) + "]");
                logManager.log("  calculatedEuler: [" + decimalFormat.format(roll) + ", " + decimalFormat.format(pitch) + ", " + decimalFormat.format(yaw) + "]");
                logManager.log("  localAccel: [" + decimalFormat.format(currentAccel[0]) + ", " +
                        decimalFormat.format(currentAccel[1]) + ", " +
                        decimalFormat.format(currentAccel[2]) + "]");
                logManager.log("  localAccelMag: [" + decimalFormat.format(Math.sqrt(currentAccel[0] * currentAccel[0] + currentAccel[1] * currentAccel[1] + currentAccel[2] * currentAccel[2])) + "]");
                logManager.log("  localAccelMagCalibrated: [" + decimalFormat.format(Math.sqrt(currentAccel[0] * currentAccel[0] + currentAccel[1] * currentAccel[1] + currentAccel[2] * currentAccel[2])
                        - windowData.segmentWindow.initAccelValue) + "]");
                logManager.log("  accWorld: [" + decimalFormat.format(acc_world[0]) + ", " +
                        decimalFormat.format(acc_world[1]) + ", " +
                        decimalFormat.format(acc_world[2]) + "]");
                logManager.log("  worldAcceldMag: [" + decimalFormat.format(Math.sqrt(acc_world[0] * acc_world[0] + acc_world[1] * acc_world[1] + acc_world[2] * acc_world[2])) + "]");
                logManager.log("  velocity: [" + decimalFormat.format(v_new[0]) + ", " +
                        decimalFormat.format(v_new[1]) + ", " +
                        decimalFormat.format(v_new[2]) + "]");
                logManager.log("  position: [" + decimalFormat.format(p_new[0]) + ", " +
                        decimalFormat.format(p_new[1]) + ", " +
                        decimalFormat.format(p_new[2]) + "]");
            }*/

            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    Bv[i][j] += rotationMatrix[i][j] * delta_t;
                }
            }

            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    Bp[i][j] += Bv[i][j] * delta_t - 0.5 * rotationMatrix[i][j] * delta_t * delta_t;
                }
            }
        }

        // Calculate bias after integration loop
        // T_post_hs = time(endIdx) - time(idx_hs_global)
        // Assuming the entire stride is post heel-strike for now
        double T_post_hs = sampleCount * delta_t;

        // Build the Desired Jacobian (4x4 matrix)
        double[][] DesiredJacobian = new double[4][4];

        // Row 1: Bv(1,:), 0
        DesiredJacobian[0][0] = Bv[0][0];
        DesiredJacobian[0][1] = Bv[0][1];
        DesiredJacobian[0][2] = Bv[0][2];
        DesiredJacobian[0][3] = 0;

        // Row 2: Bv(2,:), 0
        DesiredJacobian[1][0] = Bv[1][0];
        DesiredJacobian[1][1] = Bv[1][1];
        DesiredJacobian[1][2] = Bv[1][2];
        DesiredJacobian[1][3] = 0;

        // Row 3: Bv(3,:), 1
        DesiredJacobian[2][0] = Bv[2][0];
        DesiredJacobian[2][1] = Bv[2][1];
        DesiredJacobian[2][2] = Bv[2][2];
        DesiredJacobian[2][3] = 1;

        // Row 4: Bp(3,:), T_post_hs
        DesiredJacobian[3][0] = Bp[2][0];
        DesiredJacobian[3][1] = Bp[2][1];
        DesiredJacobian[3][2] = Bp[2][2];
        DesiredJacobian[3][3] = T_post_hs;

        // Get last velocity and relative z position
        double[] v_last = v.get(v.size() - 1); // v(end,:)
        double pz_end_rel = p.get(p.size() - 1)[2] - p.get(0)[2]; // p(end,3) - p(1,3)

        // Build measurement vector [v_last, pz_end_rel]'
        double[] measurements = new double[4];
        measurements[0] = v_last[0];
        measurements[1] = v_last[1];
        measurements[2] = v_last[2];
        measurements[3] = pz_end_rel;

        // Solve: biasVector = inv(DesiredJacobian) * measurements
        double[] biasVector = solveLinearSystem(DesiredJacobian, measurements);

        // Extract bias components
        double biasAccX = biasVector[0];
        double biasAccY = biasVector[1];
        double biasAccZ = biasVector[2];
        double biasV_hs = biasVector[3];

        // Create biasAcc array for terrain determination
        double[] biasAcc = new double[]{biasAccX, biasAccY, biasAccZ};

        // Log the calculated bias
        /*logManager.log("----------------------------------");
        logManager.log("Bias calculated for " + windowData.imuId + " Window #" + windowNum);
        logManager.log("  Bias Acc: [" + decimalFormat.format(Math.sqrt(biasAccX*biasAccX + biasAccY*biasAccY + biasAccZ*biasAccZ)) + "]");
        logManager.log("  Bias V_hs: " + decimalFormat.format(biasV_hs));*/

        // For now, return the magnitude of acceleration bias
        // double calculatedBias = Math.sqrt(biasAccX*biasAccX + biasAccY*biasAccY + biasAccZ*biasAccZ);
        double calculatedBias = biasV_hs;

        // ========== CALIBRATION LOGIC ==========
        if (!isCalibrated) {
            // Only accept Level_Walk data for calibration
            if (groundTruthTerrain.equals("Level_Walk")) {
                calibrationBiasValues.add(biasV_hs);
                calibrationWindowCount++;

                /*logManager.log("------------------------------------------");
                logManager.log("CALIBRATION Window #" + calibrationWindowCount + "/" + REQUIRED_CALIBRATION_WINDOWS);
                logManager.log("  Bias V_hs: " + decimalFormat.format(biasV_hs));
                logManager.log("------------------------------------------");*/

                logManager.log("CAL " + windowData.imuId + " [" + calibrationWindowCount + "/" + REQUIRED_CALIBRATION_WINDOWS + "] bias:" + decimalFormat.format(biasV_hs));

                // Check if calibration is complete
                if (calibrationWindowCount >= REQUIRED_CALIBRATION_WINDOWS) {
                    performCalibration();
                }
            } else {
                logManager.log("CAL " + windowData.imuId + " Win#" + windowNum + " skipped (terrain: " + groundTruthTerrain + ")");
            }

            // During calibration, we don't do terrain classification or second integration
            // Just return here
            return;
        }

        // ========== AFTER CALIBRATION: NORMAL PROCESSING ==========

        // **** Terrain Determination using CALIBRATED thresholds
//         String terrainType = terrainDeterminationAdaptive(biasV_hs);

        // **** Terrain Determination using hardcoded thresholds
        String terrainType = terrainDeterminationHardcoded(biasV_hs, biasAcc);

        logManager.log("WIN " + windowData.imuId + " #" + windowNum + " bias:" + decimalFormat.format(biasV_hs) + " -> " + terrainType);



        /*logManager.log("-----------------");
        logManager.log("Terrain Type: " + terrainType);
        logManager.log("-----------------");*/

        // Check if terrain requires recalculation
        if (terrainType.equals("Stair_Descend") || terrainType.equals("Stair_Ascend") ||
                terrainType.equals("Ramp_Descend") || terrainType.equals("Ramp_Ascend")) {

            /*logManager.log(" Detected Activity: " + terrainType);
*/
            // Recalculate with modified Jacobian (3x3 using only Bv)
            double[][] modifiedJacobian = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    modifiedJacobian[i][j] = Bv[i][j];
                }
            }

            // New measurements vector (only velocity)
            double[] modifiedMeasurements = new double[3];
            modifiedMeasurements[0] = v_last[0];
            modifiedMeasurements[1] = v_last[1];
            modifiedMeasurements[2] = v_last[2];

            // Recalculate bias
            double[] modifiedBiasVector = solveLinearSystem(modifiedJacobian, modifiedMeasurements);

            biasAccX = modifiedBiasVector[0];
            biasAccY = modifiedBiasVector[1];
            biasAccZ = modifiedBiasVector[2];
            biasV_hs = 0;

            biasAcc = new double[]{biasAccX, biasAccY, biasAccZ};

        }

        // Log the calculated bias
        /*logManager.log("----------------------------------");
        logManager.log("Bias calculated for " + windowData.imuId + " Window #" + windowNum);
        logManager.log("  Bias Acc: [" + decimalFormat.format(Math.sqrt(biasAccX*biasAccX + biasAccY*biasAccY + biasAccZ*biasAccZ)) + "]");
        logManager.log("  Bias V_hs: " + decimalFormat.format(biasV_hs));*/

        // For now, return the magnitude of acceleration bias
        double recalculatedBias = biasV_hs;


        //===================================================================================
        // SECOND INTEGRATION: WITH BIAS CORRECTION
        //===================================================================================
        /*logManager.log("----------------------------------");
        logManager.log("Starting Second Integration with Bias Correction");*/

        // Clear arrays for second integration
        ArrayList<double[]> v_corrected = new ArrayList<>();
        ArrayList<double[]> p_corrected = new ArrayList<>();
        ArrayList<double[]> a_corrected = new ArrayList<>();

        // Initial conditions for second integration
        double[] v_initial = new double[3];
        v_initial[0] = 0;
        v_initial[1] = 0;
        v_initial[2] = 0 - gain_hs * biasV_hs;
        v_corrected.add(v_initial);

        double[] p_initial = new double[]{0, 0, 0};
        p_corrected.add(p_initial);

        // Second integration loop with bias correction
        for (int k = 0; k < sampleCount; k++) {
            double[] currentAccel = accelData.get(k);
            float[] currentQuat = quaternions.get(k);

            // Calculate Euler angles from quaternion
            double[] eulerZYX = quaternionToEulerZYX(currentQuat);
            double roll  = eulerZYX[0];
            double pitch = eulerZYX[1];
            double yaw   = eulerZYX[2];

            double[][] rotationMatrix = calculateRotationMatrix(roll, pitch, yaw);

            // Subtract bias from acceleration: (acceleration_IMU - biasAcc)
            double[] accel_unbiased = new double[3];
            accel_unbiased[0] = currentAccel[0] - biasAcc[0];
            accel_unbiased[1] = currentAccel[1] - biasAcc[1];
            accel_unbiased[2] = currentAccel[2] - biasAcc[2];

            // Transform to world frame and subtract gravity
            double[] acc_world_corrected = multiplyMatrixVector(rotationMatrix, accel_unbiased);
            acc_world_corrected[0] = acc_world_corrected[0] - 0;
            acc_world_corrected[1] = acc_world_corrected[1] - 0;
            acc_world_corrected[2] = acc_world_corrected[2] - gravity;

            a_corrected.add(acc_world_corrected);

            // Get previous velocity and position
            double[] v_prev_corrected = v_corrected.get(k);
            double[] p_prev_corrected = p_corrected.get(k);

            // Update velocity: v(k+1,:) = v(k,:) + acc_world * delta_t
            double[] v_new_corrected = new double[3];
            v_new_corrected[0] = v_prev_corrected[0] + acc_world_corrected[0] * delta_t;
            v_new_corrected[1] = v_prev_corrected[1] + acc_world_corrected[1] * delta_t;
            v_new_corrected[2] = v_prev_corrected[2] + acc_world_corrected[2] * delta_t;
            v_corrected.add(v_new_corrected);

            // Update position: p(k+1,:) = p(k,:) + v(k,:) * delta_t + 0.5 * acc_world * delta_t^2
            double[] p_new_corrected = new double[3];
            p_new_corrected[0] = p_prev_corrected[0] + v_prev_corrected[0] * delta_t + 0.5 * acc_world_corrected[0] * delta_t * delta_t;
            p_new_corrected[1] = p_prev_corrected[1] + v_prev_corrected[1] * delta_t + 0.5 * acc_world_corrected[1] * delta_t * delta_t;
            p_new_corrected[2] = p_prev_corrected[2] + v_prev_corrected[2] * delta_t + 0.5 * acc_world_corrected[2] * delta_t * delta_t;
            p_corrected.add(p_new_corrected);

            // Optional: Log some samples for verification
            /*if (k == 0 || k == sampleCount - 1) {
                logManager.log("Sample " + k + " (Corrected):");
                logManager.log("  Packet: " + packetCounters.get(k));
                logManager.log("  Accel (unbiased): [" + decimalFormat.format(accel_unbiased[0]) + ", " +
                        decimalFormat.format(accel_unbiased[1]) + ", " +
                        decimalFormat.format(accel_unbiased[2]) + "]");
                logManager.log("  Accel (world): [" + decimalFormat.format(acc_world_corrected[0]) + ", " +
                        decimalFormat.format(acc_world_corrected[1]) + ", " +
                        decimalFormat.format(acc_world_corrected[2]) + "]");
                logManager.log("  Velocity: [" + decimalFormat.format(v_new_corrected[0]) + ", " +
                        decimalFormat.format(v_new_corrected[1]) + ", " +
                        decimalFormat.format(v_new_corrected[2]) + "]");
                logManager.log("  Position: [" + decimalFormat.format(p_new_corrected[0]) + ", " +
                        decimalFormat.format(p_new_corrected[1]) + ", " +
                        decimalFormat.format(p_new_corrected[2]) + "]");
            }*/
        }

        // Log final results after bias correction
        double[] v_final_corrected = v_corrected.get(v_corrected.size() - 1);
        double[] p_final_corrected = p_corrected.get(p_corrected.size() - 1);

        /*logManager.log("----------------------------------");
        logManager.log("Second Integration Complete");
        logManager.log("  Final Velocity: [" + decimalFormat.format(v_final_corrected[0]) + ", " +
                decimalFormat.format(v_final_corrected[1]) + ", " +
                decimalFormat.format(v_final_corrected[2]) + "]");
        logManager.log("  Final Position: [" + decimalFormat.format(p_final_corrected[0]) + ", " +
                decimalFormat.format(p_final_corrected[1]) + ", " +
                decimalFormat.format(p_final_corrected[2]) + "]");

        double v_mag_final = Math.sqrt(v_final_corrected[0]*v_final_corrected[0] +
                v_final_corrected[1]*v_final_corrected[1] +
                v_final_corrected[2]*v_final_corrected[2]);
        logManager.log("  Final Velocity Magnitude: " + decimalFormat.format(v_mag_final) + " m/s");

        double p_stride = Math.sqrt(p_final_corrected[0]*p_final_corrected[0] +
                p_final_corrected[1]*p_final_corrected[1]);
        logManager.log("  Stride Length: " + decimalFormat.format(p_stride) + " m");

        double p_displacement = Math.sqrt(p_final_corrected[0]*p_final_corrected[0] +
                p_final_corrected[1]*p_final_corrected[1] +
                p_final_corrected[2]*p_final_corrected[2]);
        logManager.log("  Total Displacement: " + decimalFormat.format(p_displacement) + " m");
        logManager.log("----------------------------------");

        logManager.log(" Detected Activity: " + terrainType);
        logManager.log("====================================");*/


        // Call the callback with terrain type and corrected trajectory data
        if (listener != null) {
            listener.onBiasCalculationComplete(windowData.imuId, windowNum, calculatedBias, recalculatedBias, terrainType,
                    a_corrected, v_corrected, p_corrected, startPacket, endPacket);
        }
    }
    // ============================================      End of the Bias Calculation ===========================================
    // ============================================                                  ===========================================
    // ============================================                                  ===========================================

    // Calculate rotation matrix from Euler angles (ZYX convention)
    private double[][] calculateRotationMatrix(double roll, double pitch, double yaw) {
        // Convert degrees to radians
        double rollRad = Math.toRadians(roll);
        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(yaw);

        // Rz (yaw rotation around z-axis)
        double[][] Rz = {
                {Math.cos(yawRad), -Math.sin(yawRad), 0},
                {Math.sin(yawRad),  Math.cos(yawRad), 0},
                {0,                 0,                1}
        };

        // Ry (pitch rotation around y-axis)
        double[][] Ry = {
                { Math.cos(pitchRad), 0, Math.sin(pitchRad)},
                { 0,                  1, 0},
                {-Math.sin(pitchRad), 0, Math.cos(pitchRad)}
        };

        // Rx (roll rotation around x-axis)
        double[][] Rx = {
                {1, 0,                  0},
                {0, Math.cos(rollRad), -Math.sin(rollRad)},
                {0, Math.sin(rollRad),  Math.cos(rollRad)}
        };

        // Calculate combined rotation matrix: R = Rz * Ry * Rx
        return multiplyMatrices(multiplyMatrices(Rz, Ry), Rx);
    }
    // Transpose a 3x3 matrix
    private double[][] transposeMatrix(double[][] matrix) {
        double[][] result = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                result[i][j] = matrix[j][i];
            }
        }
        return result;
    }

    // Helper method to multiply two 3x3 matrices
    private double[][] multiplyMatrices(double[][] A, double[][] B) {
        double[][] result = new double[3][3];

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                result[i][j] = 0;
                for (int k = 0; k < 3; k++) {
                    result[i][j] += A[i][k] * B[k][j];
                }
            }
        }

        return result;
    }
    // Helper method to multiply 3x3 matrix by 3x1 vector
    private double[] multiplyMatrixVector(double[][] matrix, double[] vector) {
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            result[i] = 0;
            for (int j = 0; j < 3; j++) {
                result[i] += matrix[i][j] * vector[j];
            }
        }
        return result;
    }
    private double[] quaternionToEulerZYX(float[] q) {
        double w = q[0];
        double x = q[1];
        double y = q[2];
        double z = q[3];

        // ZYX Euler angles (Yaw-Pitch-Roll sequence)
        double roll = Math.atan2(2.0 * (w*x + y*z), 1.0 - 2.0 * (x*x + y*y));
        double pitch = Math.asin(2.0 * (w*y - z*x));
        double yaw = Math.atan2(2.0 * (w*z + x*y), 1.0 - 2.0 * (y*y + z*z));

        // Convert to degrees
        yaw = Math.toDegrees(yaw);
        pitch = Math.toDegrees(pitch);
        roll = Math.toDegrees(roll);

        return new double[]{roll, pitch, yaw};
    }


    // Solve linear system Ax = b using Gaussian elimination with partial pivoting
    private double[] solveLinearSystem(double[][] A, double[] b) {
        int n = b.length;
        double[][] augmented = new double[n][n + 1];

        // Create augmented matrix [A|b]
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                augmented[i][j] = A[i][j];
            }
            augmented[i][n] = b[i];
        }

        // Forward elimination with partial pivoting
        for (int k = 0; k < n; k++) {
            // Find pivot
            int maxRow = k;
            for (int i = k + 1; i < n; i++) {
                if (Math.abs(augmented[i][k]) > Math.abs(augmented[maxRow][k])) {
                    maxRow = i;
                }
            }

            // Swap rows
            double[] temp = augmented[k];
            augmented[k] = augmented[maxRow];
            augmented[maxRow] = temp;

            // Eliminate column
            for (int i = k + 1; i < n; i++) {
                double factor = augmented[i][k] / augmented[k][k];
                for (int j = k; j <= n; j++) {
                    augmented[i][j] -= factor * augmented[k][j];
                }
            }
        }

        // Back substitution
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0;
            for (int j = i + 1; j < n; j++) {
                sum += augmented[i][j] * x[j];
            }
            x[i] = (augmented[i][n] - sum) / augmented[i][i];
        }

        return x;
    }

    private String terrainDeterminationHardcoded(double biasV_hs, double[] biasAcc) {
        String terrainType;

        if (biasV_hs < THRESHOLD_VALUE4) {
            terrainType = "Stair_Descend";
        } else if (biasV_hs >= THRESHOLD_VALUE4 && biasV_hs < THRESHOLD_VALUE3) {
            terrainType = "Ramp_Descend";
        } else if (biasV_hs >= THRESHOLD_VALUE3 && biasV_hs <= THRESHOLD_VALUE2) {
            terrainType = "Level_Walk";
        } else if (biasV_hs > THRESHOLD_VALUE2 && biasV_hs <= THRESHOLD_VALUE1) {
            terrainType = "Ramp_Ascend";
        } else if (biasV_hs > THRESHOLD_VALUE1) {
            terrainType = "Stair_Ascend";
        } else {
            logManager.log("ERROR: Terrain could not be detected");
            terrainType = "Unknown";
        }

        return terrainType;
    }

    // ========== NEW: CALIBRATED TERRAIN DETERMINATION ==========
    private String terrainDeterminationAdaptive(double biasV_hs) {
        String terrainType;

        // ========== DEBUG ==========
        logManager.log("Bias=" + decimalFormat.format(biasV_hs) +
                " | T_stair-=" + decimalFormat.format(T_STAIR_DESCEND) +
                " | T_ramp-=" + decimalFormat.format(T_RAMP_DESCEND) +
                " | T_ramp+=" + decimalFormat.format(T_RAMP_ASCEND) +
                " | T_stair+=" + decimalFormat.format(T_STAIR_ASCEND));

        if (biasV_hs < T_STAIR_DESCEND) {
            terrainType = "Stair_Descend";
        } else if (biasV_hs >= T_STAIR_DESCEND && biasV_hs < T_RAMP_DESCEND) {
            terrainType = "Ramp_Descend";
        } else if (biasV_hs >= T_RAMP_DESCEND && biasV_hs <= T_RAMP_ASCEND) {
            terrainType = "Level_Walk";
        } else if (biasV_hs > T_RAMP_ASCEND && biasV_hs <= T_STAIR_ASCEND) {
            terrainType = "Ramp_Ascend";
        } else if (biasV_hs > T_STAIR_ASCEND) {
            terrainType = "Stair_Ascend";
        } else {
            logManager.log("ERROR: Terrain could not be detected");
            terrainType = "Unknown";
        }

        return terrainType;
    }

    // ========== NEW: CALIBRATION CALCULATION METHOD ==========
    private void performCalibration() {
        logManager.log("==========================================");
        logManager.log("PERFORMING CALIBRATION WITH IQR FILTERING");
        logManager.log("==========================================");

        if (calibrationBiasValues.size() < REQUIRED_CALIBRATION_WINDOWS) {
            logManager.log("ERROR: Not enough calibration data!");
            return;
        }

        // Step 1: Compute magnitudes for IQR identification
        ArrayList<Double> magnitudes = new ArrayList<>();
        for (double bias : calibrationBiasValues) {
            magnitudes.add(Math.abs(bias));
        }
        Collections.sort(magnitudes);

        int n = magnitudes.size();

        // Step 2: Find Q25 and Q75 (25th and 75th percentiles)
        int q25_idx = n / 4;
        int q75_idx = (3 * n) / 4;
        double Q25 = magnitudes.get(q25_idx);
        double Q75 = magnitudes.get(q75_idx);

        logManager.log("Magnitude Q25: " + decimalFormat.format(Q25));
        logManager.log("Magnitude Q75: " + decimalFormat.format(Q75));

        // Step 3: Filter to IQR subset using SIGNED values
        ArrayList<Double> IQR_subset = new ArrayList<>();
        for (double bias : calibrationBiasValues) {
            double magnitude = Math.abs(bias);
            if (magnitude >= Q25 && magnitude <= Q75) {
                IQR_subset.add(bias);  // Keep SIGNED value
            }
        }

        int n_IQR = IQR_subset.size();
        logManager.log("IQR subset size: " + n_IQR + " out of " + n + " windows");
        logManager.log("Removed " + (n - n_IQR) + " outliers");

        // Step 4: Calculate μ_HS from IQR subset (SIGNED values)
        Collections.sort(IQR_subset);
        if (n_IQR % 2 == 0) {
            mu_HS = (IQR_subset.get(n_IQR/2 - 1) + IQR_subset.get(n_IQR/2)) / 2.0;
        } else {
            mu_HS = IQR_subset.get(n_IQR/2);
        }

        logManager.log("Step 1: Baseline (μ_HS) from IQR = " + decimalFormat.format(mu_HS));

        // Step 5: Normalize ALL data (not just IQR)
        ArrayList<Double> normalizedBias = new ArrayList<>();
        for (double bias : calibrationBiasValues) {
            normalizedBias.add(bias - mu_HS);
        }

        // Step 6: Calculate MAD from ALL normalized data
        ArrayList<Double> absoluteDeviations = new ArrayList<>();
        for (double normalizedValue : normalizedBias) {
            absoluteDeviations.add(Math.abs(normalizedValue));
        }
        Collections.sort(absoluteDeviations);

        double MAD;
        int m = absoluteDeviations.size();
        if (m % 2 == 0) {
            MAD = (absoluteDeviations.get(m/2 - 1) + absoluteDeviations.get(m/2)) / 2.0;
        } else {
            MAD = absoluteDeviations.get(m/2);
        }

        logManager.log("Step 2: MAD from ALL data = " + decimalFormat.format(MAD));

        // Step 7: Convert MAD to standard deviation equivalent
        //sigma_HS = 1.4826 * MAD;
        sigma_HS = 0.1156;

        logManager.log("Step 3: Robust Spread (σ_HS) = " + decimalFormat.format(sigma_HS));

        // Step 8: Calculate thresholds using optimal k values
        T_RAMP_ASCEND = mu_HS + K1 * sigma_HS;
        T_STAIR_ASCEND = mu_HS + K2 * sigma_HS;
        T_RAMP_DESCEND = mu_HS - K1 * sigma_HS;
        T_STAIR_DESCEND = mu_HS - K2 * sigma_HS;

        logManager.log("==========================================");
        logManager.log("CALIBRATION COMPLETE!");
        logManager.log("==========================================");
        logManager.log("Parameters:");
        logManager.log("  μ_HS (baseline) = " + decimalFormat.format(mu_HS));
        logManager.log("  σ_HS (spread), constant = " + decimalFormat.format(sigma_HS));
        logManager.log("  k₁ (ramp) = " + K1);
        logManager.log("  k₂ (stair) = " + K2);
        logManager.log("------------------------------------------");
        logManager.log("Calculated Thresholds:");
        logManager.log("  Stair Ascend:  > " + decimalFormat.format(T_STAIR_ASCEND));
        logManager.log("  Ramp Ascend:   > " + decimalFormat.format(T_RAMP_ASCEND));
        logManager.log("  Level Walk:    " + decimalFormat.format(T_RAMP_DESCEND) + " to " + decimalFormat.format(T_RAMP_ASCEND));
        logManager.log("  Ramp Descend:  < " + decimalFormat.format(T_RAMP_DESCEND));
        logManager.log("  Stair Descend: < " + decimalFormat.format(T_STAIR_DESCEND));
        logManager.log("==========================================");

        isCalibrated = true;
    }



}