package com.example.proyecto_llamada;


import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    EditText ettelefono;
    Button btnguardar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ettelefono = findViewById(R.id.ettelefono);
        btnguardar = findViewById(R.id.btnguardar);

        btnguardar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phoneNumber = ettelefono.getText().toString();
                savePhoneNumber(phoneNumber);
                Toast.makeText(MainActivity.this, "Número guardado con éxito", Toast.LENGTH_SHORT).show();
                startBackgroundService(phoneNumber);
                restartActivity();
            }
        });

        String phoneNumber = getSavedPhoneNumber();
        if (!phoneNumber.isEmpty()) {
            ettelefono.setText(phoneNumber);
            startBackgroundService(phoneNumber);
        }
    }

    private void savePhoneNumber(String phoneNumber) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("telefono", phoneNumber);
        editor.apply();
    }

    private String getSavedPhoneNumber() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return sharedPreferences.getString("telefono", "");
    }

    private void startBackgroundService(String phoneNumber) {
        Intent serviceIntent = new Intent(this, MyBackgroundService.class);
        serviceIntent.putExtra("phoneNumber", phoneNumber);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void restartActivity() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    public boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);

        for (ActivityManager.RunningServiceInfo service : runningServices) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }
}
