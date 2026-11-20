package com.absmobile.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION = 7;
    private PreviewView previewView;
    private ZoneOverlay zoneOverlay;
    private TextView decision, status;
    private boolean tracking = false;
    private int ballCount = 0, strikeCount = 0, outs = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildUi();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(4, 10, 17));

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(-1, -1));

        zoneOverlay = new ZoneOverlay(this);
        root.addView(zoneOverlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(20), dp(12), dp(20), dp(12));
        top.setBackgroundColor(0xCC07121E);
        top.addView(text("ABS  /  MOBILE", 20, 0xFF42F5A7, true));
        top.addView(text("  ● CAMERA LIVE", 13, 0xFFFF4E61, true));
        root.addView(top, new FrameLayout.LayoutParams(-1, dp(54), Gravity.TOP));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        panel.setBackgroundColor(0xDD07121E);

        decision = text("READY", 28, 0xFF42F5A7, true);
        decision.setGravity(Gravity.CENTER);
        panel.addView(decision);

        status = text("Zone setup  ·  drag the green zone to position",
                12, 0xFFB7C8D8, false);
        status.setGravity(Gravity.CENTER);
        panel.addView(status);

        LinearLayout counts = new LinearLayout(this);
        counts.setGravity(Gravity.CENTER);
        counts.setPadding(0, dp(8), 0, dp(8));
        counts.addView(text("BALL  " + ballCount
                + "     STRIKE  " + strikeCount
                + "     OUT  " + outs, 16, Color.WHITE, true));
        panel.addView(counts);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button start = button("START TRACK", 0xFF0D5B3A);
        Button calibrate = button("ZONE CAL", 0xFF16405D);
        Button ball = button("BALL", 0xFF7E2732);
        Button strike = button("STRIKE", 0xFF176447);
        Button reset = button("RESET", 0xFF474F56);

        start.setOnClickListener(v -> {
            tracking = !tracking;
            start.setText(tracking ? "STOP TRACK" : "START TRACK");
            status.setText(tracking
                    ? "Tracking mode ON · AI detector not installed in prototype"
                    : "Tracking mode OFF");
        });

        calibrate.setOnClickListener(v -> {
            zoneOverlay.setCalibration(!zoneOverlay.calibration);
            status.setText(zoneOverlay.calibration
                    ? "Calibration ON · drag zone to adjust"
                    : "Calibration OFF");
        });

        ball.setOnClickListener(v -> {
            ballCount++;
            if (ballCount >= 4) {
                ballCount = 0;
                strikeCount = 0;
            }
            updateCount();
            showDecision("BALL", 0xFFFF6874);
        });

        strike.setOnClickListener(v -> {
            strikeCount++;
            if (strikeCount >= 3) {
                strikeCount = 0;
                ballCount = 0;
                outs = Math.min(3, outs + 1);
            }
            updateCount();
            showDecision("STRIKE", 0xFF42F5A7);
        });

        reset.setOnClickListener(v -> {
            ballCount = 0;
            strikeCount = 0;
            outs = 0;
            updateCount();
            decision.setText("READY");
            decision.setTextColor(0xFF42F5A7);
        });

        for (Button b : new Button[]{start, calibrate, ball, strike, reset}) {
            buttons.addView(b);
        }
        panel.addView(buttons);

        root.addView(panel, new FrameLayout.LayoutParams(-1, dp(170), Gravity.BOTTOM));
        setContentView(root);
    }

    private void updateCount() {
        status.setText("B " + ballCount + "  /  S " + strikeCount + "  /  O " + outs);
    }

    private void showDecision(String s, int c) {
        decision.setText(s);
        decision.setTextColor(c);
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setBackgroundColor(color);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(74), dp(44));
        p.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(p);
        return b;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return t;
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(this);

        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                provider.unbindAll();
                provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview);
            } catch (Exception e) {
                status.setText("Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}
