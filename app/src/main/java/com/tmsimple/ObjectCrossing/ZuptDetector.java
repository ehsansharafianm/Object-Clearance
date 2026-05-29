package com.tmsimple.ObjectCrossing;

import java.text.DecimalFormat;

public class ZuptDetector {

    // Detection thresholds
    private double gyroThreshold = 15; // rad/s
    private double accelThreshold = 4; // m/s² (linear acceleration without gravity)

    DecimalFormat decimalFormat = new DecimalFormat("##.###");

    // Enhanced buffer parameters
    private static final int WINDOW_SIZE = 8; // Maximum window size
    private static final int MIN_CANDIDATES = 3; // Minimum candidates to process
    private static final long TIMEOUT_MS = 150; // Maximum wait time (150ms)

    // Window management parameters
    private static final double MIN_WINDOW_DURATION_S = 0.5; // Minimum 0.5 seconds between ZUPTs
    private static final long IMU_FREQUENCY = 60; // Hz

    // Window tracking for each IMU
    private WindowTracker imu1WindowTracker;
    private WindowTracker imu2WindowTracker;

    // Circular buffers for each IMU
    private CandidateBuffer imu1Buffer;
    private CandidateBuffer imu2Buffer;

    private ZuptListener listener;
    private LogManager logManager;

    // Window data structure
    private static class GaitWindow {
        int startPacketCounter;
        int endPacketCounter;
        double duration; // in seconds

        GaitWindow(int startPacket) {
            this.startPacketCounter = startPacket;
        }

        void close(int endPacket) {
            this.endPacketCounter = endPacket;
            this.duration = (double) (endPacketCounter - startPacketCounter) / IMU_FREQUENCY; // Convert to seconds
        }

        boolean isValid() {
            return endPacketCounter > 0 && duration >= ((double) MIN_WINDOW_DURATION_S); // Valid if closed and >= 0.5s
        }
    }

    // Window tracker for each IMU - enhanced to track gait cycles between ZUPTs
    private static class WindowTracker {
        private GaitWindow currentWindow = null;
        private long lastZuptTime = 0;
        private int windowCount = 0;

        // Additional fields for gait cycle tracking
        private int lastZuptPacket = -1;
        private int gaitCycleCount = 0;

        boolean canAcceptNewZupt(int currentPacketCounter) {
            if (lastZuptPacket == -1) return true;

            // Safety check for packet counter sequence
            if (currentPacketCounter <= lastZuptPacket) {
                // Handle packet counter reset or out-of-order packets
                return false; // or handle appropriately
            }

            return ((double) (currentPacketCounter - lastZuptPacket) / IMU_FREQUENCY) >= MIN_WINDOW_DURATION_S;
        }

        // Returns gait cycle information if a cycle was completed
        GaitWindow acceptNewZupt(int packetCounter) {
            GaitWindow completedGaitCycle = null;

            // If we have a previous ZUPT, create a gait cycle
            if (lastZuptPacket != -1) {  // Remove the lastZuptTime check
                gaitCycleCount++;

                // Create a "gait cycle window" using the existing GaitWindow structure
                completedGaitCycle = new GaitWindow(lastZuptPacket);
                completedGaitCycle.close(packetCounter);
            }

            // Update for next cycle
            lastZuptPacket = packetCounter;
            windowCount++;

            // Start new window for candidate tracking (if needed)
            currentWindow = new GaitWindow(packetCounter);

            return completedGaitCycle;
        }

        GaitWindow getCurrentWindow() {
            return currentWindow;
        }

        int getWindowCount() {
            return windowCount;
        }

        int getGaitCycleCount() {
            return gaitCycleCount;
        }



        double getPacketTimeSinceLastZupt(int currentPacketCounter) {
            if (lastZuptPacket == -1) return 0;
            return (double)(currentPacketCounter - lastZuptPacket) / IMU_FREQUENCY;
        }

    }

    // Enhanced Candidate data structure with timestamp
    private static class ZuptCandidate {
        int packetCounter;
        double gyroMagnitude;
        double accelMagnitude;
        double rollAngle;
        double combinedScore;
        long timestamp;
        int candidateId; // Position in the window

        ZuptCandidate(int packetCounter, double gyroMag, double accelMag, double rollAngle, int candidateId) {
            this.packetCounter = packetCounter;
            this.gyroMagnitude = gyroMag;
            this.accelMagnitude = accelMag;
            this.rollAngle = rollAngle;
            this.candidateId = candidateId;
            this.timestamp = System.currentTimeMillis();
            // Combined score for selection (lower is better)
            this.combinedScore = gyroMag * 0.2 + accelMag * 0.8;
        }
    }

    // Enhanced circular buffer implementation with detailed logging
    private static class CandidateBuffer {
        private ZuptCandidate[] buffer;
        private int head = 0;
        private int size = 0;
        private final int capacity;
        private long firstCandidateTime = 0;
        private int candidateCounter = 0; // Track total candidates added
        private String imuId; // For logging purposes
        private LogManager logManager;

        CandidateBuffer(int capacity, String imuId, LogManager logManager) {
            this.capacity = capacity;
            this.buffer = new ZuptCandidate[capacity];
            this.imuId = imuId;
            this.logManager = logManager;
        }

        void add(ZuptCandidate candidate) {
            // Record time of first candidate
            if (size == 0) {
                firstCandidateTime = System.currentTimeMillis();
                candidateCounter = 0; // Reset counter for new window
                /*logManager.log("--- NEW CANDIDATE WINDOW STARTED - " + imuId + " ---");*/
            }

            candidateCounter++;
            candidate.candidateId = candidateCounter;

            buffer[head] = candidate;
            head = (head + 1) % capacity;
            if (size < capacity) size++;

            // Calculate time to wait
            long currentWaitTime = System.currentTimeMillis() - firstCandidateTime;
            long remainingWaitTime = Math.max(0, TIMEOUT_MS - currentWaitTime);

            // Log candidate details
            /*logManager.log("Candidate #" + candidateCounter + " - " + imuId +
                    " | Packet: " + candidate.packetCounter +
                    " | Position: " + size + "/" + capacity +
                    " | Gyro: " + new DecimalFormat("##.##").format(candidate.gyroMagnitude) +
                    " | Accel: " + new DecimalFormat("##.##").format(candidate.accelMagnitude) +
                    " | Score: " + new DecimalFormat("##.##").format(candidate.combinedScore) +
                    " | Wait time: " + currentWaitTime + "ms" +
                    " | Remaining: " + remainingWaitTime + "ms");*/

            // Log buffer status
            if (isFull()) {
//                logManager.log("BUFFER FULL - " + imuId + " | Processing window with " + size + " candidates");
            } else if (hasMinimumCandidates() && hasTimedOut()) {
//                logManager.log("TIMEOUT REACHED - " + imuId + " | Processing window with " + size + " candidates");
            }
        }

        ZuptCandidate findBestCandidate() {
            if (size == 0) return null;

            ZuptCandidate best = null;
            int bestIndex = -1;

            // Find the best candidate and log all candidates in the window
//            logManager.log("--- WINDOW ANALYSIS - " + imuId + " ---");
            for (int i = 0; i < size; i++) {
                if (buffer[i] != null) {
                    ZuptCandidate candidate = buffer[i];
                    if (candidate == null) continue;
                    boolean isBest = (best == null || candidate.combinedScore < best.combinedScore);

                    if (isBest) {
                        best = candidate;
                        bestIndex = i;
                    }
                }
            }

            if (best != null) {
                /*logManager.log("SELECTED CANDIDATE #" + best.candidateId + " - " + imuId +
                        " | Position: " + (bestIndex + 1) + "/" + size +
                        " | Packet: " + best.packetCounter +
                        " | Gyro: " + new DecimalFormat("##.##").format(best.gyroMagnitude) +
                        " | Accel: " + new DecimalFormat("##.##").format(best.accelMagnitude) +
                        " | Final Score: " + new DecimalFormat("##.##").format(best.combinedScore));*/
            }

            return best;
        }

        boolean isFull() {
            return size == capacity;
        }

        boolean hasMinimumCandidates() {
            return size >= MIN_CANDIDATES;
        }

        boolean hasTimedOut() {
            if (size == 0) return false;
            return (System.currentTimeMillis() - firstCandidateTime) > TIMEOUT_MS;
        }

        boolean shouldProcess() {
            return isFull() || (hasMinimumCandidates() && hasTimedOut());
        }

        void clear() {
            if (size > 0) {
               /* logManager.log("--- CANDIDATE WINDOW CLOSED - " + imuId + " ---");
                logManager.log("----------------");*/
            }
            size = 0;
            head = 0;
            firstCandidateTime = 0;
            candidateCounter = 0;
        }

        int getSize() {
            return size;
        }

        long getWaitTime() {
            if (size == 0) return 0;
            return System.currentTimeMillis() - firstCandidateTime;
        }
    }

    // Interface for callbacks
    public interface ZuptListener {
        void onZuptDataUpdated(String imuId, double gyroMag, double linearAccelMag);
        void onGaitWindowCreated(String imuId, int windowNum, int startPacket, int endPacket, double duration);
    }

    public ZuptDetector(ZuptListener listener, LogManager logManager) {
        this.listener = listener;
        this.logManager = logManager;
        this.imu1Buffer = new CandidateBuffer(WINDOW_SIZE, "IMU1", logManager);
        this.imu2Buffer = new CandidateBuffer(WINDOW_SIZE, "IMU2", logManager);
        this.imu1WindowTracker = new WindowTracker();
        this.imu2WindowTracker = new WindowTracker();
    }

    // Main processing method
    public void processNewImuData(String imuId, double gyroMagnitude, double accelMagnitude, double rollAngle, int packetCounter) {

        // Always update UI with current values
        if (listener != null) {
            listener.onZuptDataUpdated(imuId, gyroMagnitude, accelMagnitude);
        }
        // Check if current sample meets threshold criteria
        boolean meetsCriteria = (gyroMagnitude < gyroThreshold) && (accelMagnitude < accelThreshold);

        // Route to correct IMU buffer
        if ("IMU1".equals(imuId)) {
            processCandidateDetection(imuId, imu1Buffer, imu1WindowTracker,
                    gyroMagnitude, accelMagnitude, rollAngle, packetCounter, meetsCriteria);
        } else if ("IMU2".equals(imuId)) {
            processCandidateDetection(imuId, imu2Buffer, imu2WindowTracker,
                    gyroMagnitude, accelMagnitude, rollAngle, packetCounter, meetsCriteria);
        }
    }

    // Core candidate processing logic with proper gait cycle detection
    private void processCandidateDetection(String imuId, CandidateBuffer buffer, WindowTracker windowTracker,
                                           double gyroMag, double accelMag, double rollAngle,
                                           int packetCounter, boolean meetsCriteria) {

        if (meetsCriteria) {
            // Add qualifying sample to buffer with detailed logging
            ZuptCandidate candidate = new ZuptCandidate(packetCounter, gyroMag, accelMag, rollAngle, 0);
            buffer.add(candidate);
        }

        // Process buffer when conditions are met
        if (buffer.shouldProcess()) {
            ZuptCandidate bestCandidate = buffer.findBestCandidate();

            if (bestCandidate != null) {

                // Check if we can accept this ZUPT (minimum time constraint)
                if (windowTracker.canAcceptNewZupt(bestCandidate.packetCounter)) {

                    double timeSinceLastZupt = windowTracker.getPacketTimeSinceLastZupt(bestCandidate.packetCounter);
                    logManager.log("=================   START    ========================");
                    // Log that ZUPT is detected and accepted
                    logManager.log("OPTIMAL ZUPT ACCEPTED - " + imuId +
                            "| at packet: " + bestCandidate.packetCounter +
                            "| At time: " + decimalFormat.format(timeSinceLastZupt) + "s" +
                            "| to previous ZUPT " +
                            "| Accel Mag: " + decimalFormat.format(bestCandidate.accelMagnitude) +
                            "| Velocity Mag: " + decimalFormat.format(bestCandidate.gyroMagnitude) +
                            "| Score Mag: " + decimalFormat.format(bestCandidate.combinedScore));

                    // Accept the new ZUPT and get the completed gait cycle
                    GaitWindow completedGaitCycle = windowTracker.acceptNewZupt(bestCandidate.packetCounter);

                    // Log gait cycle completion if we have one
                    if (completedGaitCycle != null) {
                        /*logManager.log("GAIT CYCLE COMPLETED - " + imuId +
                                " | Cycle #" + windowTracker.getGaitCycleCount() +
                                " | Start ZUPT: " + completedGaitCycle.startPacketCounter +
                                " | End ZUPT: " + completedGaitCycle.endPacketCounter +
                                " | Duration: " + decimalFormat.format(completedGaitCycle.duration) + "s");*/

                        // Notify listener about completed gait cycle
                        if (listener != null) {
                            listener.onGaitWindowCreated(imuId, windowTracker.getGaitCycleCount(),
                                    completedGaitCycle.startPacketCounter, completedGaitCycle.endPacketCounter, completedGaitCycle.duration);
                        }
                    }



                } else {
                    double timeSinceLastZupt = windowTracker.getPacketTimeSinceLastZupt(bestCandidate.packetCounter);

                    /*logManager.log("OPTIMAL ZUPT REJECTED - " + imuId +
                            " at packet: " + bestCandidate.packetCounter +
                            " At time: " + decimalFormat.format(timeSinceLastZupt) + "s < 0.5s)" +
                            " to previous ZUPT");*/
                }
            }

            // Clear buffer for next window
            buffer.clear();
        }
    }
}