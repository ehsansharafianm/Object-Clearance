package com.tmsimple.ObjectCrossing;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import java.util.HashSet;
import java.util.Locale;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class UiManager {

    // All UI elements
    private final View root;
    private final ImuManager imuManager;

    public Button scanButton, syncButton, measureButton, disconnectButton,
            stopButton, uploadButton, dataLogButton, listImusButton, openLabelDialogButton,
            showFeaturesButton, listButton;

    private android.app.Dialog labelDialog, logDialog, featureDialog;

    public Button showImuDataButton;
    private android.app.Dialog imuDataDialog;

    // Dialog TextViews for IMU data
    public TextView dialogImu1Status, dialogImu2Status;
    public TextView dialogImu1Roll, dialogImu2Roll;

    public TextView dialogImu1Index, dialogImu2Index;
    public TextView dialogImu1Battery, dialogImu2Battery;

    public TextView dialogImu3Status, dialogImu4Status;
    public TextView dialogImu3Roll, dialogImu4Roll;

    public TextView dialogImu3Index, dialogImu4Index;
    public TextView dialogImu3Battery, dialogImu4Battery;


    private View labelDialogView;
    public Button logToggleButton;
    public TextView imu1Status, imu2Status, logContents;
    public TextView imu1Roll, imu2Roll;           // Roll angles

    public TextView imu1Index, imu2Index;         // Packet indices
    public TextView imu1Battery, imu2Battery, logContentsDialog;

    public TextView imu3Status, imu4Status;
    public TextView imu3Roll, imu4Roll;

    public TextView imu3Index, imu4Index;
    public TextView imu3Battery, imu4Battery;
    public TextView imu5Status, imu6Status;
    public TextView imu5RecStatus, imu6RecStatus;
    public Button exportButton;
    public Spinner spinnerIMU5, spinnerIMU6;
    private String selectedIMU5Mac, selectedIMU6Mac;
    public EditText enterSubjectNumber;
    // Feature detection display fields
    public TextView imu1WindowNumber, imu1TerrainType, imu1BiasValue, imu1MaxHeight, imu1MaxStride;
    // Per-page views for the ViewPager (index 0 = IMU1, index 1 = IMU2)
    private android.view.View[] featurePages = new android.view.View[2];

    public CardView imuListDialog;
    public Spinner spinnerIMU1, spinnerIMU2, spinnerIMU3, spinnerIMU4;
    private Map<String, String> macToNameMap; // Maps MAC address to name
    private Map<String, String> nameToMacMap; // Maps name to MAC address
    private String selectedIMU1Mac, selectedIMU2Mac, selectedIMU3Mac, selectedIMU4Mac;

    private LogManager logManager;
    private LinearLayout appBorderContainer;
    private Context context;

    private LinearLayout embeddedLogContainer;
    private ScrollView embeddedLogScrollView;
    private TextView embeddedLogContents;
    private Button toggleLogButton;
    private boolean isLogExpanded = true;



    public void setLogManager(LogManager logManager) {
        this.logManager = logManager;
    }
    public UiManager(View rootView, ImuManager imuManager) {
        this.root = rootView;
        this.imuManager = imuManager;
        this.context = rootView.getContext();
    }

    // Bind all Views from layout
    public void bindLabelingDataViews(View root) {

        enterSubjectNumber = root.findViewById(R.id.enterSubjectNumber);
        scanButton = root.findViewById(R.id.scanButton);
        syncButton = root.findViewById(R.id.syncButton);
        measureButton = root.findViewById(R.id.measureButton);
        disconnectButton = root.findViewById(R.id.disconnectButton);
        stopButton = root.findViewById(R.id.stopButton);
        listImusButton = root.findViewById(R.id.listImusButton);
        uploadButton = root.findViewById(R.id.uploadButton);
        dataLogButton = root.findViewById(R.id.dataLogButton);

        // Status fields
        imu1Status = root.findViewById(R.id.imu1Status);
        imu2Status = root.findViewById(R.id.imu2Status);

        // IMU1 data fields
        imu1Roll = root.findViewById(R.id.imu1Roll);
        imu1Index = root.findViewById(R.id.imu1Index);
        imu1Battery = root.findViewById(R.id.imu1Battery);

        // IMU2 data fields
        imu2Roll = root.findViewById(R.id.imu2Roll);
        imu2Index = root.findViewById(R.id.imu2Index);
        imu2Battery = root.findViewById(R.id.imu2Battery);
        // IMU3 data fields
        imu3Status = root.findViewById(R.id.imu3Status);
        imu3Index = root.findViewById(R.id.imu3Index);

        // IMU4 data fields
        imu4Status = root.findViewById(R.id.imu4Status);
        imu4Index = root.findViewById(R.id.imu4Index);
        // IMU5 data fields
        imu5Status = root.findViewById(R.id.imu5Status);
        imu5RecStatus = root.findViewById(R.id.imu5RecStatus);

        // IMU6 data fields
        imu6Status = root.findViewById(R.id.imu6Status);
        imu6RecStatus = root.findViewById(R.id.imu6RecStatus);

        // Export button
        exportButton = root.findViewById(R.id.exportButton);

        // Feature detection display fields
//        imu1WindowNumber = root.findViewById(R.id.imu1WindowNumber);
//        imu1TerrainType = root.findViewById(R.id.imu1TerrainType);
//        imu1BiasValue = root.findViewById(R.id.imu1BiasValue);
//        imu1MaxHeight = root.findViewById(R.id.imu1MaxHeight);
//        imu1MaxStride = root.findViewById(R.id.imu1MaxStride);

        // FOR SPINNERS
        spinnerIMU1 = root.findViewById(R.id.spinnerIMU1);
        spinnerIMU2 = root.findViewById(R.id.spinnerIMU2);
        spinnerIMU3 = root.findViewById(R.id.spinnerIMU3);
        spinnerIMU4 = root.findViewById(R.id.spinnerIMU4);
        spinnerIMU5 = root.findViewById(R.id.spinnerIMU5);
        spinnerIMU6 = root.findViewById(R.id.spinnerIMU6);

        openLabelDialogButton = root.findViewById(R.id.openLabelDialogButton);
        showFeaturesButton = root.findViewById(R.id.showFeaturesButton);
        showImuDataButton = root.findViewById(R.id.showImuDataButton);

        // Status fields
        imu1Status = root.findViewById(R.id.imu1Status);
        imu2Status = root.findViewById(R.id.imu2Status);

        // Index fields
        imu1Index = root.findViewById(R.id.imu1Index);
        imu2Index = root.findViewById(R.id.imu2Index);

        appBorderContainer = root.findViewById(R.id.labeling_data_root);

        Button listButton = root.findViewById(R.id.listImusButton);

        // Bind embedded log views
        embeddedLogScrollView = root.findViewById(R.id.logScrollView);
        embeddedLogContents = root.findViewById(R.id.logContentsDialog);
        toggleLogButton = root.findViewById(R.id.toggleLogVisibility);

        // Setup toggle functionality
        setupEmbeddedLogToggle();

    }


    //
    // ---------- CONFIGURATION METHODS ----------
    //

    // Configure Button (text, color, enabled)
    public void setButton(Button button, String text, String colorHex, String textColorHex, Boolean enabled) {
        if (button == null) return;
        if (text != null) button.setText(text);
        if (colorHex != null) button.setBackgroundColor(Color.parseColor(colorHex));
        if (textColorHex != null) button.setTextColor(Color.parseColor(textColorHex));
        if (enabled != null) button.setEnabled(enabled);
    }

    // Configure TextView (text only)
    public void setTextView(TextView textView, String text, String colorHex, Boolean enabled)  {
        if (textView == null) return;
        if (text != null) textView.setText(text);
        if (colorHex != null) textView.setBackgroundColor(Color.parseColor(colorHex));
        if (enabled != null) textView.setEnabled(enabled);
    }

    public void setDataLogButtonHandler(Button button, LogManager logManager, ImuManager imuManager) {
        button.setOnClickListener(new View.OnClickListener() {
            int index = 0;

            @Override
            public void onClick(View v) {
                index++;
                if (index % 2 == 1) {
                    imuManager.setLoggingData(true);
                    button.setBackgroundColor(Color.parseColor("#008080"));
                    button.setText("Logging ...");
                    logManager.log(" ---- Data is Logging -----");
                } else if (index % 2 == 0 && index > 1) {
                    imuManager.setLoggingData(false);
                    button.setBackgroundColor(Color.parseColor("#2196F3"));
                    button.setText("No Log");
                    logManager.log("---- Data Logging Stopped -----");
                }
            }
        });
        vibratePhone(100);
    }

    private boolean isLogVisible = false;

    public interface OnSubjectNumberEnteredListener {
        void onSubjectNumberEntered(int subjectNumber);
    }

    public void setEnterSubjectNumberHandler(EditText editText, OnSubjectNumberEnteredListener listener) {
        if (editText == null || listener == null) return;

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable string) {
                if (!TextUtils.isEmpty(string)) {
                    try {
                        int number = Integer.parseInt(string.toString());
                        listener.onSubjectNumberEntered(number);
                        setButton(scanButton, null, null, null, true);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }

                }
            }
        });
    }



    // single listener that reads its offset (and colors) out of the view’s tag
    private final View.OnTouchListener labelTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // each button has a PacketOffsetItem in its tag
            PacketOffsetItem info = (PacketOffsetItem) v.getTag();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    imuManager.setPacketCounterOffset(info.offset);
                    v.setBackgroundColor(info.activeColor);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                    imuManager.setPacketCounterOffset(0);
                    v.setBackgroundColor(info.defaultColor);
                    break;
            }
            return true;
        }
    };
    // Modified bindLabelButtons to work with dialog view
    private void bindLabelButtonsInDialog(View dialogView) {
        PacketOffsetItem[] configs = new PacketOffsetItem[] {
                new PacketOffsetItem(R.id.labelButton1, 1000000,
                        Color.parseColor("#05fff8"),
                        Color.parseColor("#903F51B5")),
                new PacketOffsetItem(R.id.labelButton2, 2000000,
                        Color.parseColor("#05fff8"),
                        Color.parseColor("#903F51B5")),
                new PacketOffsetItem(R.id.labelButton3, 3000000,
                        Color.parseColor("#05fff8"),
                        Color.parseColor("#903F51B5")),
                new PacketOffsetItem(R.id.labelButton4, 4000000,
                        Color.parseColor("#05fff8"),
                        Color.parseColor("#903F51B5")),
                new PacketOffsetItem(R.id.labelButton5, 5000000,
                        Color.parseColor("#05fff8"),
                        Color.parseColor("#903F51B5")),
                new PacketOffsetItem(R.id.labelButton6, 6000000,
                        Color.parseColor("#05fff8"),
                        Color.parseColor("#903F51B5")),
        };

        for (PacketOffsetItem cfg : configs) {
            Button btn = dialogView.findViewById(cfg.buttonId);
            if (btn != null) {
                btn.setTag(cfg);
                btn.setOnTouchListener(labelTouchListener);
            }
        }
    }
    private static class PacketOffsetItem {
        final int buttonId;
        final int offset;
        final int activeColor;
        final int defaultColor;

        PacketOffsetItem(int buttonId, int offset, int activeColor, int defaultColor) {
            this.buttonId    = buttonId;
            this.offset      = offset;
            this.activeColor = activeColor;
            this.defaultColor= defaultColor;
        }
    }


    // Optional utility: clear all text fields
    public void clearAllValues() {
        imu1Roll.setText("");
        imu1Index.setText("");
        imu1Battery.setText("");

        imu2Roll.setText("");
        imu2Index.setText("");
        imu2Battery.setText("");

        if (imu3Roll != null) imu3Roll.setText("");
        if (imu3Index != null) imu3Index.setText("");
        if (imu3Battery != null) imu3Battery.setText("");

        if (imu4Roll != null) imu4Roll.setText("");
        if (imu4Index != null) imu4Index.setText("");
        if (imu4Battery != null) imu4Battery.setText("");

        if (imu5Status != null) imu5Status.setText("-");
        if (imu5RecStatus != null) imu5RecStatus.setText("-");

        if (imu6Status != null) imu6Status.setText("-");
        if (imu6RecStatus != null) imu6RecStatus.setText("-");
    }

    // Method to update feature detection display with dynamic colors
    // Method to update feature detection display with dynamic colors
    public void updateFeatureDisplay(String imuId, int windowNum, String terrainType, double biasValue, double maxHeight, double maxStride) {
        int backgroundColor;
        switch (terrainType) {
            case "Level_Walk":    backgroundColor = Color.parseColor("#4CAF50"); break;
            case "Stair_Ascend":  backgroundColor = Color.parseColor("#2196F3"); break;
            case "Stair_Descend": backgroundColor = Color.parseColor("#673AB7"); break;
            case "Ramp_Ascend":   backgroundColor = Color.parseColor("#FF9800"); break;
            case "Ramp_Descend":  backgroundColor = Color.parseColor("#FFC107"); break;
            default:              backgroundColor = Color.parseColor("#9E9E9E"); break;
        }

        // Update main-page TextViews (IMU1 only)
        if (imuId.equals("IMU1")) {
            if (imu1WindowNumber != null) imu1WindowNumber.setText(String.valueOf(windowNum));
            if (imu1TerrainType != null) {
                imu1TerrainType.setText(terrainType);
                android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
                d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                d.setColor(backgroundColor);
                d.setCornerRadius(8);
                imu1TerrainType.setBackground(d);
            }
            if (imu1BiasValue != null) imu1BiasValue.setText(String.format(Locale.US, "%.3f", biasValue));
            if (imu1MaxHeight != null) imu1MaxHeight.setText(String.format(Locale.US, "%.3f m", maxHeight));
            if (imu1MaxStride != null) imu1MaxStride.setText(String.format(Locale.US, "%.3f m", maxStride));
        }

        // Update correct ViewPager page
        int pageIndex = imuId.equals("IMU1") ? 0 : 1;
        if (featurePages[pageIndex] != null) {
            android.view.View page = featurePages[pageIndex];
            android.widget.TextView tvTerrain = page.findViewById(R.id.pageTerrainType);
            android.widget.TextView tvWindow  = page.findViewById(R.id.pageWindowNumber);
            android.widget.TextView tvBias    = page.findViewById(R.id.pageBiasValue);
            android.widget.TextView tvHeight  = page.findViewById(R.id.pageMaxHeight);
            android.widget.TextView tvStride  = page.findViewById(R.id.pageMaxStride);
            if (tvWindow  != null) tvWindow.setText(String.valueOf(windowNum));
            if (tvBias    != null) tvBias.setText(String.format(Locale.US, "%.3f", biasValue));
            if (tvHeight  != null) tvHeight.setText(String.format(Locale.US, "%.3f m", maxHeight));
            if (tvStride  != null) tvStride.setText(String.format(Locale.US, "%.3f m", maxStride));
            if (tvTerrain != null) {
                tvTerrain.setText(terrainType);
                android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
                d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                d.setColor(backgroundColor);
                d.setCornerRadius(8);
                tvTerrain.setBackground(d);
            }
        }
    }


    // Add a method to show the dialog
    public void showImuListDialog() {
        if (imuListDialog != null) {
            imuListDialog.setVisibility(View.VISIBLE);
        }
    }

    // Parse and setup the spinners
    public void setupImuSpinners(android.content.Context context) {
        // Load the sensor list from strings.xml
        String[] sensorArray = context.getResources().getStringArray(R.array.sensor_mac_map);

        // Initialize maps
        macToNameMap = new HashMap<>();
        nameToMacMap = new HashMap<>();
        List<String> namesList = new ArrayList<>();

        // Parse each item: "MAC,Name"
        for (String item : sensorArray) {
            String[] parts = item.split(",");
            if (parts.length == 2) {
                String mac = parts[0].trim();
                String name = parts[1].trim();
                macToNameMap.put(mac, name);
                nameToMacMap.put(name, mac);
                namesList.add(name); // Only names will be displayed
            }
        }

        // Create adapter with just the names
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                namesList
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Setup IMU1 spinner
        if (spinnerIMU1 != null) {
            spinnerIMU1.setAdapter(adapter);
            spinnerIMU1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedName = (String) parent.getItemAtPosition(position);
                    selectedIMU1Mac = nameToMacMap.get(selectedName);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedIMU1Mac = null;
                }
            });
        }

        // Setup IMU2 spinner
        if (spinnerIMU2 != null) {
            spinnerIMU2.setAdapter(adapter);
            spinnerIMU2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedName = (String) parent.getItemAtPosition(position);
                    selectedIMU2Mac = nameToMacMap.get(selectedName);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedIMU2Mac = null;
                }
            });
        }
        // Setup IMU3 spinner
        if (spinnerIMU3 != null) {
            spinnerIMU3.setAdapter(adapter);
            spinnerIMU3.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedName = (String) parent.getItemAtPosition(position);
                    selectedIMU3Mac = nameToMacMap.get(selectedName);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedIMU3Mac = null;
                }
            });
        }

        // Setup IMU4 spinner
        if (spinnerIMU4 != null) {
            spinnerIMU4.setAdapter(adapter);
            spinnerIMU4.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedName = (String) parent.getItemAtPosition(position);
                    selectedIMU4Mac = nameToMacMap.get(selectedName);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedIMU4Mac = null;
                }
            });
        }
        // Setup IMU5 spinner
        if (spinnerIMU5 != null) {
            spinnerIMU5.setAdapter(adapter);
            spinnerIMU5.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedName = (String) parent.getItemAtPosition(position);
                    selectedIMU5Mac = nameToMacMap.get(selectedName);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedIMU5Mac = null;
                }
            });
        }

        // Setup IMU6 spinner
        if (spinnerIMU6 != null) {
            spinnerIMU6.setAdapter(adapter);
            spinnerIMU6.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedName = (String) parent.getItemAtPosition(position);
                    selectedIMU6Mac = nameToMacMap.get(selectedName);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedIMU6Mac = null;
                }
            });
        }
    }

    // Getter methods for selected MAC addresses
    public String getSelectedIMU1Mac() {
        return selectedIMU1Mac;
    }

    public String getSelectedIMU2Mac() {
        return selectedIMU2Mac;
    }

    // Getter methods for selected names
    public String getSelectedIMU1Name() {
        if (spinnerIMU1 != null && spinnerIMU1.getSelectedItem() != null) {
            return (String) spinnerIMU1.getSelectedItem();
        }
        return null;
    }

    public String getSelectedIMU2Name() {
        if (spinnerIMU2 != null && spinnerIMU2.getSelectedItem() != null) {
            return (String) spinnerIMU2.getSelectedItem();
        }
        return null;
    }
    public String getSelectedIMU3Mac() {
        return selectedIMU3Mac;
    }

    public String getSelectedIMU4Mac() {
        return selectedIMU4Mac;
    }

    public String getSelectedIMU3Name() {
        if (spinnerIMU3 != null && spinnerIMU3.getSelectedItem() != null) {
            return (String) spinnerIMU3.getSelectedItem();
        }
        return null;
    }

    public String getSelectedIMU4Name() {
        if (spinnerIMU4 != null && spinnerIMU4.getSelectedItem() != null) {
            return (String) spinnerIMU4.getSelectedItem();
        }
        return null;
    }
    public String getSelectedIMU5Mac() {
        return selectedIMU5Mac;
    }

    public String getSelectedIMU6Mac() {
        return selectedIMU6Mac;
    }

    public String getSelectedIMU5Name() {
        if (spinnerIMU5 != null && spinnerIMU5.getSelectedItem() != null) {
            return (String) spinnerIMU5.getSelectedItem();
        }
        return null;
    }

    public String getSelectedIMU6Name() {
        if (spinnerIMU6 != null && spinnerIMU6.getSelectedItem() != null) {
            return (String) spinnerIMU6.getSelectedItem();
        }
        return null;
    }

    // Get MAC address from name
    public String getMacFromName(String name) {
        return nameToMacMap != null ? nameToMacMap.get(name) : null;
    }

    // Get name from MAC address
    public String getNameFromMac(String mac) {
        return macToNameMap != null ? macToNameMap.get(mac) : null;
    }

    // Method to update spinners with only discoverable devices
    public void updateSpinnersWithDiscoveredDevices(Context context, HashSet<String> discoveredMacAddresses) {
        if (discoveredMacAddresses == null || discoveredMacAddresses.isEmpty()) {
            // If no devices discovered yet, show all IMUs
            setupImuSpinners(context);
            return;
        }

        // Filter the list to only include discovered devices
        List<String> discoveredNamesList = new ArrayList<>();

        for (String mac : discoveredMacAddresses) {
            String name = macToNameMap.get(mac);
            if (name != null) {
                discoveredNamesList.add(name);
            }
        }

        // If no matching devices found, keep the spinners empty or show a message
        if (discoveredNamesList.isEmpty()) {
            logManager.log("No matching IMUs found in discovery.");
            return;
        }

        // Create adapter with only discovered devices
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                discoveredNamesList
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Update IMU1 spinner
        if (spinnerIMU1 != null) {
            String previousSelection = getSelectedIMU1Name();
            spinnerIMU1.setAdapter(adapter);

            // Try to restore previous selection if it's still in the list
            if (previousSelection != null && discoveredNamesList.contains(previousSelection)) {
                int position = discoveredNamesList.indexOf(previousSelection);
                spinnerIMU1.setSelection(position);
            }
        }

        // Update IMU2 spinner
        if (spinnerIMU2 != null) {
            String previousSelection = getSelectedIMU2Name();
            spinnerIMU2.setAdapter(adapter);

            // Try to restore previous selection if it's still in the list
            if (previousSelection != null && discoveredNamesList.contains(previousSelection)) {
                int position = discoveredNamesList.indexOf(previousSelection);
                spinnerIMU2.setSelection(position);
            }
        }
        // Update IMU3 spinner
        if (spinnerIMU3 != null) {
            String previousSelection = getSelectedIMU3Name();
            spinnerIMU3.setAdapter(adapter);
            if (previousSelection != null && discoveredNamesList.contains(previousSelection)) {
                int position = discoveredNamesList.indexOf(previousSelection);
                spinnerIMU3.setSelection(position);
            }
        }

        // Update IMU4 spinner
        if (spinnerIMU4 != null) {
            String previousSelection = getSelectedIMU4Name();
            spinnerIMU4.setAdapter(adapter);
            if (previousSelection != null && discoveredNamesList.contains(previousSelection)) {
                int position = discoveredNamesList.indexOf(previousSelection);
                spinnerIMU4.setSelection(position);
            }
        }
        // Update IMU5 spinner
        if (spinnerIMU5 != null) {
            String previousSelection = getSelectedIMU5Name();
            spinnerIMU5.setAdapter(adapter);
            if (previousSelection != null && discoveredNamesList.contains(previousSelection)) {
                int position = discoveredNamesList.indexOf(previousSelection);
                spinnerIMU5.setSelection(position);
            }
        }

        // Update IMU6 spinner
        if (spinnerIMU6 != null) {
            String previousSelection = getSelectedIMU6Name();
            spinnerIMU6.setAdapter(adapter);
            if (previousSelection != null && discoveredNamesList.contains(previousSelection)) {
                int position = discoveredNamesList.indexOf(previousSelection);
                spinnerIMU6.setSelection(position);
            }
        }
    }

    // Add method to setup and show the label dialog
    public void setupLabelDialog(android.content.Context context) {
        labelDialog = new android.app.Dialog(context);

        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
        android.view.View dialogView = inflater.inflate(R.layout.dialog_label_buttons, null);
        labelDialog.setContentView(dialogView);

        if (labelDialog.getWindow() != null) {
            int dialogWidth = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            int dialogHeight = (int)(context.getResources().getDisplayMetrics().heightPixels * 0.7);
            labelDialog.getWindow().setLayout(dialogWidth, dialogHeight);

            android.view.WindowManager.LayoutParams params = labelDialog.getWindow().getAttributes();
            params.gravity = android.view.Gravity.BOTTOM;
            params.y = 0;
            params.x = 0;
            params.dimAmount = 0.1f;
            labelDialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            labelDialog.getWindow().setAttributes(params);
            labelDialog.getWindow().setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame);
        }

        // Inflate pages
        android.view.View page1 = inflater.inflate(R.layout.item_label_page1, null);
        android.view.View page2 = inflater.inflate(R.layout.item_label_page2, null);

        // Bind label buttons for page 1 (offsets 1M-6M)
        bindLabelButtonsInDialog(page1);

        // Bind label buttons for page 2 (offsets 7M-10M)
        PacketOffsetItem[] page2Configs = new PacketOffsetItem[] {
                new PacketOffsetItem(R.id.labelButton7,  7000000, Color.parseColor("#05fff8"), Color.parseColor("#E65100")),
                new PacketOffsetItem(R.id.labelButton8,  8000000, Color.parseColor("#05fff8"), Color.parseColor("#F57C00")),
                new PacketOffsetItem(R.id.labelButton9,  9000000, Color.parseColor("#05fff8"), Color.parseColor("#FB8C00")),
                new PacketOffsetItem(R.id.labelButton10, 10000000, Color.parseColor("#05fff8"), Color.parseColor("#FFA726")),
        };
        for (PacketOffsetItem cfg : page2Configs) {
            Button btn = page2.findViewById(cfg.buttonId);
            if (btn != null) {
                btn.setTag(cfg);
                btn.setOnTouchListener(labelTouchListener);
            }
        }

        // Setup ViewPager
        androidx.viewpager2.widget.ViewPager2 viewPager = dialogView.findViewById(R.id.labelViewPager);
        android.widget.LinearLayout dotsContainer = dialogView.findViewById(R.id.labelDotsContainer);

        final android.view.View[] labelPages = new android.view.View[]{ page1, page2 };

        viewPager.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
                android.view.View page = labelPages[viewType];
                if (page.getParent() != null) ((android.view.ViewGroup) page.getParent()).removeView(page);
                page.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(page) {};
            }
            @Override public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder h, int pos) {}
            @Override public int getItemCount() { return 2; }
            @Override public int getItemViewType(int position) { return position; }
        });

        // Dot indicators
        android.widget.TextView[] dots = new android.widget.TextView[2];
        for (int i = 0; i < 2; i++) {
            dots[i] = new android.widget.TextView(context);
            dots[i].setText("●");
            dots[i].setTextSize(22);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(6, 0, 6, 0);
            dots[i].setLayoutParams(lp);
            dotsContainer.addView(dots[i]);
        }
        dots[0].setTextColor(android.graphics.Color.parseColor("#052A63"));
        dots[1].setTextColor(android.graphics.Color.parseColor("#CCCCCC"));

        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                dots[0].setTextColor(position == 0 ? android.graphics.Color.parseColor("#052A63") : android.graphics.Color.parseColor("#CCCCCC"));
                dots[1].setTextColor(position == 1 ? android.graphics.Color.parseColor("#052A63") : android.graphics.Color.parseColor("#CCCCCC"));
            }
        });

        // Close button
        Button closeButton = dialogView.findViewById(R.id.closeLabelDialog);
        if (closeButton != null) closeButton.setOnClickListener(v -> labelDialog.dismiss());

        labelDialogView = dialogView;

        if (openLabelDialogButton != null) {
            openLabelDialogButton.setOnClickListener(v -> { if (labelDialog != null) labelDialog.show(); });
        }
    }

    public void bindLabelButtons() {
        bindLabelButtonsInDialog(root);
    }

    // Setup the log dialog
    public void setupLogDialog(android.content.Context context, LogManager logManager) {
        // Auto-scroll whenever text changes
        if (embeddedLogContents != null && embeddedLogScrollView != null) {
            embeddedLogContents.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    // Scroll to bottom whenever text is added
                    embeddedLogScrollView.post(() -> embeddedLogScrollView.fullScroll(android.view.View.FOCUS_DOWN));
                }
            });
        }

        // Update LogManager to use the embedded TextView
        if (logManager != null) {
            logManager.setLogContents(embeddedLogContents);
        }

    }
    public void setupFeatureDialog(android.content.Context context) {
        featureDialog = new android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        featureDialog.setContentView(R.layout.dialog_feature_results);

        if (featureDialog.getWindow() != null) {
            featureDialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    (int)(context.getResources().getDisplayMetrics().heightPixels * 0.72)
            );
            android.view.WindowManager.LayoutParams params = featureDialog.getWindow().getAttributes();
            params.gravity = android.view.Gravity.BOTTOM;
            featureDialog.getWindow().setAttributes(params);
        }

        androidx.viewpager2.widget.ViewPager2 viewPager = featureDialog.findViewById(R.id.featureViewPager);
        android.widget.LinearLayout dotsContainer = featureDialog.findViewById(R.id.dotsContainer);

        // Inflate both pages
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
        featurePages[0] = inflater.inflate(R.layout.item_imu_feature_page, null);
        featurePages[1] = inflater.inflate(R.layout.item_imu_feature_page, null);

        // Set IMU labels
        ((android.widget.TextView) featurePages[0].findViewById(R.id.pageImuLabel)).setText("Left Side");
        ((android.widget.TextView) featurePages[1].findViewById(R.id.pageImuLabel)).setText("Right Side");

        // Setup ViewPager adapter
        viewPager.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
                android.view.View page = featurePages[viewType];
                if (page.getParent() != null) {
                    ((android.view.ViewGroup) page.getParent()).removeView(page);
                }
                page.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(page) {};
            }

            @Override
            public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {}

            @Override
            public int getItemCount() { return 2; }

            @Override
            public int getItemViewType(int position) { return position; }
        });

        // Setup dot indicators
        android.widget.TextView[] dots = new android.widget.TextView[2];
        for (int i = 0; i < 2; i++) {
            dots[i] = new android.widget.TextView(context);
            dots[i].setText("●");
            dots[i].setTextSize(22);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(6, 0, 6, 0);
            dots[i].setLayoutParams(lp);
            dotsContainer.addView(dots[i]);
        }
        dots[0].setTextColor(android.graphics.Color.parseColor("#052A63"));
        dots[1].setTextColor(android.graphics.Color.parseColor("#CCCCCC"));

        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                dots[0].setTextColor(position == 0
                        ? android.graphics.Color.parseColor("#052A63")
                        : android.graphics.Color.parseColor("#CCCCCC"));
                dots[1].setTextColor(position == 1
                        ? android.graphics.Color.parseColor("#052A63")
                        : android.graphics.Color.parseColor("#CCCCCC"));
            }
        });

        // Close button
        Button closeButton = featureDialog.findViewById(R.id.closeFeatureDialog);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> featureDialog.dismiss());
        }

        // Show button
        if (showFeaturesButton != null) {
            showFeaturesButton.setOnClickListener(v -> {
                if (featureDialog != null) featureDialog.show();
            });
        }
    }


    public void setupImuDataDialog(android.content.Context context) {
        // Create the dialog
        imuDataDialog = new android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        imuDataDialog.setContentView(R.layout.dialog_imu_data);

        // Set size and position
        if (imuDataDialog.getWindow() != null) {
            imuDataDialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    (int)(context.getResources().getDisplayMetrics().heightPixels * 0.7)
            );

            android.view.WindowManager.LayoutParams params = imuDataDialog.getWindow().getAttributes();
            params.gravity = android.view.Gravity.BOTTOM;
            imuDataDialog.getWindow().setAttributes(params);
        }

        // Bind the TextViews from dialog
        dialogImu1Status = imuDataDialog.findViewById(R.id.imu1Status);
        dialogImu1Roll = imuDataDialog.findViewById(R.id.imu1Roll);
        dialogImu1Index = imuDataDialog.findViewById(R.id.imu1Index);
        dialogImu1Battery = imuDataDialog.findViewById(R.id.imu1Battery);

        dialogImu2Status = imuDataDialog.findViewById(R.id.imu2Status);
        dialogImu2Roll = imuDataDialog.findViewById(R.id.imu2Roll);
        dialogImu2Index = imuDataDialog.findViewById(R.id.imu2Index);
        dialogImu2Battery = imuDataDialog.findViewById(R.id.imu2Battery);

        dialogImu3Status = imuDataDialog.findViewById(R.id.imu3Status);
        dialogImu3Roll = imuDataDialog.findViewById(R.id.imu3Roll);
        dialogImu3Index = imuDataDialog.findViewById(R.id.imu3Index);
        dialogImu3Battery = imuDataDialog.findViewById(R.id.imu3Battery);

        dialogImu4Status = imuDataDialog.findViewById(R.id.imu4Status);
        dialogImu4Roll = imuDataDialog.findViewById(R.id.imu4Roll);
        dialogImu4Index = imuDataDialog.findViewById(R.id.imu4Index);
        dialogImu4Battery = imuDataDialog.findViewById(R.id.imu4Battery);

        // Setup close button
        Button closeButton = imuDataDialog.findViewById(R.id.closeImuDataDialog);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> imuDataDialog.dismiss());
        }

        // Setup the show button
        if (showImuDataButton != null) {
            showImuDataButton.setOnClickListener(v -> {
                if (imuDataDialog != null) {
                    imuDataDialog.show();
                }
            });
        }
    }
    // METHOD to change the app border color dynamically
    public void setAppBorderColor(String borderColorHex) {
        if (appBorderContainer == null) return;

        try {
            // Create a new GradientDrawable programmatically
            android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
            border.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            border.setStroke(12, Color.parseColor(borderColorHex)); // 4dp border width (12px)
            border.setCornerRadius(36); // 12dp corner radius (36px)
            border.setColor(Color.parseColor("#F5F5F5")); // Background color

            appBorderContainer.setBackground(border);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // OVERLOADED METHOD for more control
    public void setAppBorder(String borderColor, String backgroundColor, int borderWidthDp) {
        if (appBorderContainer == null) return;

        try {
            android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
            border.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            border.setStroke(borderWidthDp * 3, Color.parseColor(borderColor)); // Convert dp to pixels
            border.setCornerRadius(36); // 12dp corner radius
            border.setColor(Color.parseColor(backgroundColor));

            appBorderContainer.setBackground(border);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupEmbeddedLogToggle() {
        if (toggleLogButton != null && embeddedLogScrollView != null) {

            // Set initial state - ScrollView is hidden
            embeddedLogScrollView.setVisibility(View.GONE);
            toggleLogButton.setText("Show");  // Already set to "Show"

            toggleLogButton.setOnClickListener(v -> {
                if (embeddedLogScrollView.getVisibility() == View.VISIBLE) {
                    embeddedLogScrollView.setVisibility(View.GONE);
                    toggleLogButton.setText("Show");
                } else {
                    embeddedLogScrollView.setVisibility(View.VISIBLE);
                    toggleLogButton.setText("Hide");

                    // Auto-scroll to bottom when opened
                    if (embeddedLogScrollView != null) {
                        embeddedLogScrollView.post(() ->
                                embeddedLogScrollView.fullScroll(View.FOCUS_DOWN));
                    }
                }
            });
        }
    }

    // Phone vibration method
    public void vibratePhone(int durationMs) {
        android.os.Vibrator vibrator = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(durationMs);
        }
    }





}