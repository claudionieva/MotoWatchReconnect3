package com.motowatchreconnect;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

public class WatchReconnectService extends Service {

    private static final String TAG = "WatchReconnectService";
    private static final String CHANNEL_ID = "watch_reconnect";
    private static final int NOTIFICATION_ID = 1;

    public static final String EXTRA_MAC = "watch_mac";
    public static final String ACTION_STOP = "com.motowatchreconnect.STOP";

    // CLAVE: este flag evita cerrar el GATT nunca
    private static boolean running = false;
    private static boolean watchConnected = false;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;   // ← NUNCA se cierra, vive para siempre
    private Handler handler;
    private String watchMac;

    // ──────────────────────────────────────────
    // Callback GATT: el corazón de la reconexión
    // ──────────────────────────────────────────
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                watchConnected = true;
                Log.d(TAG, "✅ Watch CONECTADO");
                updateNotification("✅ Watch conectado");
                // Descubrir servicios es opcional pero confirma la conexión BLE real
                if (ActivityCompat.checkSelfPermission(WatchReconnectService.this,
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices();
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                watchConnected = false;
                Log.d(TAG, "❌ Watch desconectado (status=" + status + "). NO cerramos GATT — autoConnect reconecta solo.");
                updateNotification("🔄 Watch desconectado — reconectando...");
                // ⚠️ La clave: NO llamar gatt.close() ni gatt.connect()
                // Con autoConnect=true, Android reconecta solo cuando el watch vuelva
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Servicios descubiertos, status=" + status);
        }
    };

    // Escucha si el Bluetooth del teléfono se apaga/enciende
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            if (state == BluetoothAdapter.STATE_ON) {
                Log.d(TAG, "Bluetooth volvió a encenderse — iniciando GATT persistente");
                handler.postDelayed(() -> connectGattPersistent(), 2000);
            } else if (state == BluetoothAdapter.STATE_OFF) {
                Log.d(TAG, "Bluetooth apagado — limpiando GATT");
                cleanupGatt();
                updateNotification("⚠️ Bluetooth apagado");
            }
        }
    };

    // ──────────────────────────────────────────
    // Ciclo de vida del servicio
    // ──────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bm.getAdapter();

        createNotificationChannel();

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.hasExtra(EXTRA_MAC)) {
            watchMac = intent.getStringExtra(EXTRA_MAC);
        }

        startForeground(NOTIFICATION_ID, buildNotification("Iniciando..."));
        running = true;

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            connectGattPersistent();
        } else {
            updateNotification("⚠️ Activá el Bluetooth");
        }

        return START_STICKY; // Se reinicia automáticamente si Android lo mata
    }

    @Override
    public void onDestroy() {
        running = false;
        cleanupGatt();
        unregisterReceiver(bluetoothStateReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ──────────────────────────────────────────
    // Conexión GATT persistente
    // ──────────────────────────────────────────

    private void connectGattPersistent() {
        if (watchMac == null) {
            Log.e(TAG, "MAC no configurada");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Sin permiso BLUETOOTH_CONNECT");
            return;
        }

        // Si ya hay un GATT activo con autoConnect, no hacer nada más
        if (bluetoothGatt != null) {
            Log.d(TAG, "GATT ya activo, autoConnect manejará la reconexión");
            return;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(watchMac);
        Log.d(TAG, "Conectando GATT persistente a: " + watchMac);
        updateNotification("🔄 Conectando al watch...");

        // autoConnect=true es lo FUNDAMENTAL:
        // Android mantendrá esta conexión viva y reconectará SOLO cuando el watch esté disponible
        bluetoothGatt = device.connectGatt(
            this,
            true,                          // ← autoConnect: reconexión automática indefinida
            gattCallback,
            BluetoothDevice.TRANSPORT_LE   // Forzar BLE
        );

        if (bluetoothGatt == null) {
            Log.e(TAG, "connectGatt devolvió null — reintentando en 10s");
            handler.postDelayed(this::connectGattPersistent, 10_000);
        }
    }

    private void cleanupGatt() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
        watchConnected = false;
    }

    // ──────────────────────────────────────────
    // Notificación persistente
    // ──────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "Watch Reconectar",
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Monitoreo de conexión Bluetooth del watch");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, WatchReconnectService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Watch Reconectar")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_delete, "Detener", stopPi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    public static boolean isRunning() { return running; }
    public static boolean isWatchConnected() { return watchConnected; }
}
