package com.motowatchreconnect;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String WATCH_MAC = "C4:49:3E:F4:F8:0E";

    private TextView statusText;
    private Button btnIniciar;
    private Button btnDetener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        btnIniciar = findViewById(R.id.btnIniciar);
        btnDetener = findViewById(R.id.btnDetener);

        btnIniciar.setOnClickListener(v -> requestPermissionsAndStart());
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
            setStatus("🟢 Monitoreo activo\nReconectando automáticamente cuando el watch se desconecte");
            btnIniciar.setEnabled(false);
            btnDetener.setEnabled(true);
        } else {
            setStatus("Tocá INICIAR MONITOREO para empezar");
            btnIniciar.setEnabled(true);
            btnDetener.setEnabled(false);
        }
    }

    private void requestPermissionsAndStart() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (needed.isEmpty()) {
            startMonitoring();
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
                startMonitoring();
            } else {
                setStatus("❌ Se necesitan permisos de Bluetooth para continuar");
            }
        }
    }

    private void startMonitoring() {
        Intent intent = new Intent(this, WatchReconnectService.class);
        intent.putExtra(WatchReconnectService.EXTRA_MAC, WATCH_MAC);
        ContextCompat.startForegroundService(this, intent);
        setStatus("🟢 Monitoreo activo\nReconectando automáticamente cuando el watch se desconecte");
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
