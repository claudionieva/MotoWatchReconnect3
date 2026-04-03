package com.motowatchreconnect;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationRequest;
import android.companion.BluetoothLeDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WatchReconnect";
    public static final String WATCH_MAC = "C4:49:3E:F4:F8:0E";

    private CompanionDeviceManager companionDeviceManager;
    private TextView statusText;
    private Button btnAsociar;
    private Button btnIniciar;
    private Button btnDetener;

    // Lanzador del diálogo de asociación CDM
    private final ActivityResultLauncher<IntentSenderRequest> associationLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    BluetoothDevice device = result.getData()
                        .getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
                    if (device != null) {
                        Log.d(TAG, "Watch asociado: " + device.getAddress());
                        setStatus("✅ Watch asociado: " + device.getAddress() + "\nTocá INICIAR MONITOREO");
                        btnIniciar.setEnabled(true);
                    }
                } else {
                    setStatus("❌ Asociación cancelada. Intentá de nuevo.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        btnAsociar = findViewById(R.id.btnAsociar);
        btnIniciar = findViewById(R.id.btnIniciar);
        btnDetener = findViewById(R.id.btnDetener);

        companionDeviceManager = getSystemService(CompanionDeviceManager.class);

        btnAsociar.setOnClickListener(v -> requestPermissionsAndAssociate());
        btnIniciar.setOnClickListener(v -> startMonitoring());
        btnDetener.setOnClickListener(v -> stopMonitoring());

        checkCurrentState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkCurrentState();
    }

    private void checkCurrentState() {
        if (WatchReconnectService.isRunning()) {
            setStatus("🟢 Monitoreo activo en segundo plano");
            btnIniciar.setEnabled(false);
            btnDetener.setEnabled(true);
            return;
        }

        boolean associated = isWatchAssociated();
        if (associated) {
            setStatus("✅ Watch asociado — tocá INICIAR MONITOREO");
            btnIniciar.setEnabled(true);
            btnDetener.setEnabled(false);
        } else {
            setStatus("Primero asociá el watch con ASOCIAR WATCH");
            btnIniciar.setEnabled(false);
            btnDetener.setEnabled(false);
        }
    }

    private boolean isWatchAssociated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return companionDeviceManager.getMyAssociations().stream()
                .anyMatch(a -> {
                    if (a.getDeviceMacAddress() == null) return false;
                    return WATCH_MAC.equalsIgnoreCase(a.getDeviceMacAddress().toString());
                });
        }
        // En Android 12 no hay getMyAssociations(), asumimos que si el servicio nunca corrió
        // es porque no está asociado aún
        return false;
    }

    private void requestPermissionsAndAssociate() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (needed.isEmpty()) {
            associateDevice();
        } else {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) {
                associateDevice();
            } else {
                setStatus("❌ Se necesitan los permisos de Bluetooth para continuar");
            }
        }
    }

    private void associateDevice() {
        setStatus("🔍 Buscando watch... Seleccioná 'moto watch F80E' en el diálogo");

        BluetoothLeDeviceFilter filter = new BluetoothLeDeviceFilter.Builder().build();

        AssociationRequest request = new AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(false)
            .build();

        companionDeviceManager.associate(request, new CompanionDeviceManager.Callback() {
            @Override
            public void onDeviceFound(IntentSender chooserLauncher) {
                try {
                    IntentSenderRequest req = new IntentSenderRequest.Builder(chooserLauncher).build();
                    associationLauncher.launch(req);
                } catch (Exception e) {
                    Log.e(TAG, "Error lanzando diálogo CDM", e);
                    setStatus("❌ Error al abrir el diálogo de asociación");
                }
            }

            @Override
            public void onFailure(CharSequence error) {
                runOnUiThread(() -> setStatus("❌ Error en asociación: " + error));
            }
        }, null);
    }

    private void startMonitoring() {
        Intent intent = new Intent(this, WatchReconnectService.class);
        intent.putExtra(WatchReconnectService.EXTRA_MAC, WATCH_MAC);
        ContextCompat.startForegroundService(this, intent);
        setStatus("🟢 Monitoreo activo en segundo plano");
        btnIniciar.setEnabled(false);
        btnDetener.setEnabled(true);
    }

    private void stopMonitoring() {
        Intent intent = new Intent(this, WatchReconnectService.class);
        intent.setAction(WatchReconnectService.ACTION_STOP);
        startService(intent);
        setStatus("⛔ Monitoreo detenido");
        btnIniciar.setEnabled(true);
        btnDetener.setEnabled(false);
    }

    private void setStatus(String msg) {
        runOnUiThread(() -> statusText.setText(msg));
    }
}
