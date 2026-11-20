package com.absmobile.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements BaseballPitchAnalyzer.Listener {
    private static final int CAMERA_PERMISSION = 7;
    private PreviewView previewView;
    private ZoneOverlay zoneOverlay;
    private TextView decision, status, counts, confidence;
    private Button startButton;
    private boolean tracking = false;
    private int balls=0, strikes=0, outs=0;
    private ExecutorService cameraExecutor;
    private BaseballPitchAnalyzer pitchAnalyzer;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        cameraExecutor = Executors.newSingleThreadExecutor();
        buildUi();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(4,10,17));
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(-1,-1));
        zoneOverlay = new ZoneOverlay(this);
        root.addView(zoneOverlay, new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18),dp(8),dp(18),dp(8)); top.setBackgroundColor(0xD907121E);
        TextView brand=text("ABS / MOBILE 0.3",20,0xFF42F5A7,true); top.addView(brand,new LinearLayout.LayoutParams(0,dp(42),1));
        top.addView(text("EXPERIMENTAL AUTO CALL",12,0xFFFFD166,true));
        root.addView(top,new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP));

        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(8),dp(14),dp(8)); panel.setBackgroundColor(0xE807121E);
        decision=text("READY",27,0xFF42F5A7,true); decision.setGravity(Gravity.CENTER);
        panel.addView(decision,new LinearLayout.LayoutParams(-1,dp(38)));
        status=text("Camera starting · Auto FPS · Balanced AI",12,0xFFB7C8D8,false); status.setGravity(Gravity.CENTER);
        panel.addView(status,new LinearLayout.LayoutParams(-1,dp(24)));
        counts=text("BALL  0       STRIKE  0       OUT  0",16,Color.WHITE,true); counts.setGravity(Gravity.CENTER);
        panel.addView(counts,new LinearLayout.LayoutParams(-1,dp(30)));
        confidence=text("AI: waiting for moving ball candidate",11,0xFF9EB2C5,false); confidence.setGravity(Gravity.CENTER);
        panel.addView(confidence,new LinearLayout.LayoutParams(-1,dp(22)));

        LinearLayout buttons=new LinearLayout(this); buttons.setGravity(Gravity.CENTER);
        startButton=button("START AUTO",0xFF0D5B3A); Button calibrate=button("ZONE CAL",0xFF16405D);
        Button reset=button("RESET",0xFF474F56); Button manualBall=button("BALL +",0xFF7E2732); Button manualStrike=button("STRIKE +",0xFF176447);
        startButton.setOnClickListener(v->{ tracking=!tracking; if(pitchAnalyzer!=null)pitchAnalyzer.setEnabled(tracking);
            startButton.setText(tracking?"STOP AUTO":"START AUTO"); status.setText(tracking?"Auto tracking active · experimental 2-D detector":"Auto tracking paused"); });
        calibrate.setOnClickListener(v->{ zoneOverlay.setCalibration(!zoneOverlay.calibration);
            status.setText(zoneOverlay.calibration?"Calibration ON · drag strike zone":"Calibration OFF"); });
        reset.setOnClickListener(v->{balls=0;strikes=0;outs=0;updateCounts();decision.setText("READY");decision.setTextColor(0xFF42F5A7);});
        manualBall.setOnClickListener(v->applyDecision(false,1f,"Manual operator input"));
        manualStrike.setOnClickListener(v->applyDecision(true,1f,"Manual operator input"));
        for(Button b:new Button[]{startButton,calibrate,reset,manualBall,manualStrike}) buttons.addView(b);
        panel.addView(buttons);
        root.addView(panel,new FrameLayout.LayoutParams(-1,dp(166),Gravity.BOTTOM));
        setContentView(root);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            try {
                ProcessCameraProvider provider=future.get();
                Preview preview=new Preview.Builder().build(); preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(640,360)).build();
                pitchAnalyzer=new BaseballPitchAnalyzer(this,true);
                pitchAnalyzer.setEnabled(tracking);
                analysis.setAnalyzer(cameraExecutor,pitchAnalyzer);
                provider.unbindAll();
                provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis);
                status.setText("Camera live · ImageAnalysis 640×360 · Auto FPS · Balanced");
            } catch(Exception e) { status.setText("Camera error: "+e.getMessage()); }
        },ContextCompat.getMainExecutor(this));
    }

    @Override public RectF zone() {
        RectF r = zoneOverlay.getZoneNormalized();
        // PreviewView uses FILL_CENTER. Map the screen-space overlay rectangle
        // back into the 16:9 ImageAnalysis frame coordinates.
        int vw = Math.max(1, zoneOverlay.getWidth());
        int vh = Math.max(1, zoneOverlay.getHeight());
        float viewAspect = vw / (float) vh;
        float imageAspect = 16f / 9f;
        if (viewAspect > imageAspect) {
            float crop = (1f - imageAspect / viewAspect) * 0.5f;
            r.top = crop + r.top * (1f - 2f * crop);
            r.bottom = crop + r.bottom * (1f - 2f * crop);
        } else if (viewAspect < imageAspect) {
            float crop = (1f - viewAspect / imageAspect) * 0.5f;
            r.left = crop + r.left * (1f - 2f * crop);
            r.right = crop + r.right * (1f - 2f * crop);
        }
        return r;
    }
    @Override public void onTracking(String message,float x,float y,float conf) {
        runOnUiThread(()->confidence.setText(String.format(java.util.Locale.US,"%s · x %.3f y %.3f · score %.0f%%",message,x,y,conf*100f)));
    }
    @Override public void onDecision(boolean strike,float conf,String reason) {
        runOnUiThread(()->applyDecision(strike,conf,reason));
    }
    private void applyDecision(boolean strike,float conf,String reason) {
        if(strike) {
            strikes++;
            decision.setText("STRIKE"); decision.setTextColor(0xFF42F5A7);
            if(strikes>=3){strikes=0;balls=0;outs=Math.min(3,outs+1);}
        } else {
            balls++;
            decision.setText("BALL"); decision.setTextColor(0xFFFF6874);
            if(balls>=4){balls=0;strikes=0;}
        }
        updateCounts(); status.setText(reason);
        confidence.setText(String.format(java.util.Locale.US,"Decision confidence: %.0f%% · verify against calibrated footage",conf*100f));
    }
    private void updateCounts(){counts.setText("BALL  "+balls+"       STRIKE  "+strikes+"       OUT  "+outs);}
    private Button button(String label,int color){Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTextSize(10);b.setAllCaps(false);b.setPadding(dp(2),0,dp(2),0);b.setBackgroundColor(color);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(dp(3),0,dp(3),0);b.setLayoutParams(p);return b;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);return t;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==CAMERA_PERMISSION&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)startCamera();
        else status.setText("Camera permission required");}
    @Override protected void onDestroy(){super.onDestroy();if(cameraExecutor!=null)cameraExecutor.shutdown();}
}
