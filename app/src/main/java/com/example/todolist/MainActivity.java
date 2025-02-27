package com.example.todolist;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private EditText taskInput, etPhone, etOtp;
    private Button addButton, btnSendOtp, btnVerifyOtp;
    private RecyclerView taskList;
    private List<String> tasks;
    private TaskAdapter taskAdapter;
    private static final int NOTIFICATION_PERMISSION_CODE = 101;
    private static final int LOCATION_PERMISSION_CODE = 102;
    private static final int SMS_PERMISSION_CODE = 103;
    private static final int PHONE_STATE_PERMISSION_CODE = 104;
    private FusedLocationProviderClient fusedLocationClient;
    private String generatedOtp;
    private BroadcastReceiver smsReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);

        // Initialize notification channel for Android 13+
        NotificationHelper.createNotificationChannel(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestPermissionsIfNeeded();

        // Initialize UI Elements
        taskInput = findViewById(R.id.taskInput);
        addButton = findViewById(R.id.addButton);
        taskList = findViewById(R.id.taskList);
        etPhone = findViewById(R.id.etphone);
        etOtp = findViewById(R.id.etotp);
        btnSendOtp = findViewById(R.id.sms);
        btnVerifyOtp = findViewById(R.id.btn_verify_otp);

        tasks = new ArrayList<>();
        taskAdapter = new TaskAdapter(tasks, this);
        taskList.setLayoutManager(new LinearLayoutManager(this));
        taskList.setAdapter(taskAdapter);

        // Trigger SIM phone number detection on phone EditText click
        etPhone.setOnClickListener(v -> retrieveSimPhoneNumber());

        addButton.setOnClickListener(v -> {
            String task = taskInput.getText().toString().trim();
            if (!task.isEmpty()) {
                tasks.add(task);
                taskAdapter.notifyItemInserted(tasks.size() - 1);
                taskInput.setText("");
                fetchLocationAndNotify("Task Added", "You added: " + task);
            }
        });

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                return;
            }
            String token = task.getResult();
            Log.d("FCM", "Token: " + token);
        });

        // Send OTP
        btnSendOtp.setOnClickListener(v -> {
            String phoneNumber = etPhone.getText().toString().trim();
            if (phoneNumber.isEmpty() || phoneNumber.length() < 10) {
                Toast.makeText(MainActivity.this, "Enter a valid phone number", Toast.LENGTH_SHORT).show();
            } else {
                sendOtp(phoneNumber);
            }
        });

        // Verify OTP
        btnVerifyOtp.setOnClickListener(v -> {
            String otp = etOtp.getText().toString().trim();
            if (otp.isEmpty() || otp.length() < 6) {
                Toast.makeText(MainActivity.this, "Enter a valid OTP", Toast.LENGTH_SHORT).show();
            } else {
                verifyOtp(otp);
            }
        });

        registerSmsReceiver(); // Register SMS receiver for auto OTP detection
    }

    /**
     * Retrieves SIM phone numbers if permissions are granted.
     */
    private void retrieveSimPhoneNumber() {
        // Check for necessary permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_PHONE_NUMBERS
            }, PHONE_STATE_PERMISSION_CODE);
            return;
        }

        SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subscriptionManager != null) {
            List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
            if (subscriptionInfoList != null && !subscriptionInfoList.isEmpty()) {
                List<String> phoneNumbers = new ArrayList<>();
                for (SubscriptionInfo info : subscriptionInfoList) {
                    String phoneNumber = info.getNumber();
                    if (phoneNumber != null && !phoneNumber.isEmpty()) {
                        phoneNumbers.add(phoneNumber);
                    }
                }
                if (!phoneNumbers.isEmpty()) {
                    showPhoneNumberSelectionDialog(phoneNumbers);
                } else {
                    Toast.makeText(this, "No phone numbers available from SIM cards.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "No active SIM cards detected.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Unable to access SIM information.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Displays an AlertDialog with available SIM phone numbers.
     * Includes an option for the user to manually enter another number.
     */
    private void showPhoneNumberSelectionDialog(List<String> phoneNumbers) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Phone Number");

        // Add option for manual entry
        phoneNumbers.add("Use another number");
        String[] phoneArray = phoneNumbers.toArray(new String[0]);

        builder.setItems(phoneArray, (dialog, which) -> {
            if (which == phoneNumbers.size() - 1) {
                // "Use another number" selected; enable manual input
                etPhone.setText("");
                etPhone.setEnabled(true);
                etPhone.requestFocus();
            } else {
                // SIM number selected; set value and disable manual editing
                etPhone.setText(phoneArray[which]);
                etPhone.setEnabled(false);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    /**
     * (Optional) Checks SIM state and sends OTP.
     * Here, we simply simulate OTP sending.
     */
    private void checkSimAndSendOtp() {
        SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subscriptionManager != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PHONE_STATE_PERMISSION_CODE);
                return;
            }
        }
        sendOtp(null);
    }

    /**
     * Simulates sending an OTP to the specified phone number.
     *
     * @param phoneNumber The phone number to send the OTP to.
     */
    private void sendOtp(String phoneNumber) {
        generatedOtp = String.format("%06d", new Random().nextInt(999999));
        Toast.makeText(MainActivity.this, "OTP Sent: " + generatedOtp, Toast.LENGTH_LONG).show();
        Log.d("OTP", "Sending OTP " + generatedOtp + " to " + phoneNumber);
    }

    /**
     * Verifies the entered OTP against the generated one.
     *
     * @param otp The OTP entered by the user.
     */
    private void verifyOtp(String otp) {
        if (otp.equals(generatedOtp)) {
            Toast.makeText(MainActivity.this, "✅ OTP Verified Successfully!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(MainActivity.this, "❌ Incorrect OTP", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Fetches the last known location and sends a notification.
     *
     * @param title   Notification title.
     * @param message Notification message.
     */
    private void fetchLocationAndNotify(String title, String message) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    String locationMessage = message + "\n📍 Location: " +
                            location.getLatitude() + ", " + location.getLongitude();
                    NotificationHelper.showNotification(MainActivity.this, title, locationMessage);
                } else {
                    NotificationHelper.showNotification(MainActivity.this, title, message + "\n📍 Location not available");
                }
            });
        } else {
            NotificationHelper.showNotification(MainActivity.this, title, message + "\n⚠️ Location permission denied");
        }
    }

    /**
     * Requests necessary permissions at runtime.
     */
    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECEIVE_SMS},
                    SMS_PERMISSION_CODE);
        }
    }

    /**
     * Registers a BroadcastReceiver to listen for incoming SMS messages for auto OTP detection.
     */
    private void registerSmsReceiver() {
        smsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
                    Bundle bundle = intent.getExtras();
                    if (bundle != null) {
                        Object[] pdus = (Object[]) bundle.get("pdus");
                        if (pdus != null) {
                            for (Object pdu : pdus) {
                                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                                String messageBody = smsMessage.getMessageBody();

                                // Use regex to extract a 6-digit OTP from the message body
                                if (messageBody.matches(".*\\b\\d{6}\\b.*")) {
                                    String otp = messageBody.replaceAll("\\D+", "");
                                    etOtp.setText(otp);
                                    Toast.makeText(context, "OTP Auto-Filled", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(smsReceiver, filter);
    }

    /**
     * Handles permission request results.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PHONE_STATE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                retrieveSimPhoneNumber();
            } else {
                Toast.makeText(this, "Permission denied. Cannot fetch SIM phone number.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Unregisters the SMS receiver when the activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (smsReceiver != null) {
            unregisterReceiver(smsReceiver);
        }
    }
}
