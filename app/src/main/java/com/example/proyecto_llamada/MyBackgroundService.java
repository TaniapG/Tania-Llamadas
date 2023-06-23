package com.example.proyecto_llamada;


import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.lang.reflect.Method;

public class MyBackgroundService extends Service {

    private LocationManager locationManager;
    private LocationListener locationListener;

    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "ForegroundServiceChannel";

    double latitude = -1.0;
    double longitude = -1.0;
    private boolean isCallRejected = false;
    String telefono;
    private boolean isCallAnswered = false;
    private int lastState = TelephonyManager.CALL_STATE_IDLE;

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MyBackgroundService.this);
        telefono = preferences.getString("telefono", "");

        Toast.makeText(this, "Servicio iniciado", Toast.LENGTH_SHORT).show();

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListener);

        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        phoneStateListener = new PhoneStateListener() {
            private Handler handler = new Handler();

            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        // Llamada entrante
                        Toast.makeText(MyBackgroundService.this, "Llamada entrante", Toast.LENGTH_SHORT).show();
                        // Envío de mensajes con las coordenadas al recibir una llamada
                        sendMessageWithCoordinates(latitude, longitude);

                        // Programar la devolución de llamada después de 15 segundos
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Devolver la llamada
                                returnCall(incomingNumber);
                                // Mostrar el Toast "Devolviendo llamada"
                                Toast.makeText(MyBackgroundService.this, "Devolviendo llamada", Toast.LENGTH_SHORT).show();
                            }
                        }, 15000); // Retraso de 15 segundos en milisegundos
                        break;

                    case TelephonyManager.CALL_STATE_IDLE:
                        if (incomingNumber != null && !incomingNumber.isEmpty()) {
                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (lastState == TelephonyManager.CALL_STATE_RINGING && !isCallAnswered) {
                                        if (isCallRejected) {
                                            // Llamada rechazada
                                            Toast.makeText(MyBackgroundService.this, "Llamada rechazada", Toast.LENGTH_SHORT).show();
                                            // Realizar acciones adicionales en caso de una llamada rechazada
                                        } else {
                                            // Llamada no contestada
                                            Toast.makeText(MyBackgroundService.this, "Llamada no contestada", Toast.LENGTH_SHORT).show();
                                            // Realizar acciones adicionales en caso de una llamada no contestada
                                        }
                                        // Regresar llamada
                                        returnCall(incomingNumber);
                                    } else {
                                        // Llamada finalizada
                                        Toast.makeText(MyBackgroundService.this, "Llamada finalizada", Toast.LENGTH_SHORT).show();
                                    }
                                    // Restablecer el estado de la llamada
                                    isCallAnswered = false;
                                    isCallRejected = false;
                                }
                            }, 3000); // Retraso de 3 segundos en milisegundos
                        }
                        break;

                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                            // Llamada contestada
                            isCallAnswered = true;
                            Toast.makeText(MyBackgroundService.this, "Llamada contestada", Toast.LENGTH_SHORT).show();

                            // Silenciar la bocina durante la llamada
                            silenceSpeaker();
                        }
                        break;
                }
                // Actualizar el estado anterior de la llamada
                lastState = state;
            }
        };

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        // Crear el canal de notificación para el servicio en primer plano
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Foreground Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        // Configurar la notificación para el servicio en primer plano
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Servicio en segundo plano")
                .setContentText("Realizando acción en segundo plano")
                .setSmallIcon(R.drawable.gps)
                .setContentIntent(pendingIntent);

        Notification notification = builder.build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void returnCall(String incomingNumber) {
        Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + telefono));
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // Manejar caso de permisos no concedidos
            return;
        }

        startActivity(callIntent);
    }

    private void sendMessageWithCoordinates(double latitude, double longitude) {
        SmsManager smsManager = SmsManager.getDefault();

        String phoneNumber = telefono;
        String mapsLink = "https://www.google.com/maps?q=" + latitude + "," + longitude;
        String message = "Las coordenadas actuales del dispositivo son: " + latitude + ", " + longitude + "\n\n" + mapsLink;

        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
    }

    private void silenceSpeaker() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            try {
                // Get the getForceUse method using reflection
                Method methodGetForceUse = audioManager.getClass().getMethod("getForceUse", int.class);

                // Invoke the method with the FOR_COMMUNICATION parameter to get the current configuration
                int currentConfig = (int) methodGetForceUse.invoke(audioManager, 0);

                // If the current configuration is not already set to FOR_COMMUNICATION, set it
                if (currentConfig != 0) {
                    // Get the setForceUse method using reflection
                    Method methodSetForceUse = audioManager.getClass().getMethod("setForceUse", int.class, int.class);

                    // Invoke the method with FOR_COMMUNICATION configuration to silence the speaker
                    methodSetForceUse.invoke(audioManager, 0, 0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        locationManager.removeUpdates(locationListener);
    }
}