package com.huangkang.gtneo2tint;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.VideoResult;
import com.otaliastudios.cameraview.controls.Audio;
import com.otaliastudios.cameraview.controls.Engine;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.controls.Flash;
import com.otaliastudios.cameraview.controls.Grid;
import com.otaliastudios.cameraview.controls.Mode;
import com.otaliastudios.cameraview.controls.Preview;
import com.otaliastudios.cameraview.controls.VideoCodec;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private CameraView camera;
    private ManualExposureController manualExposure;
    private TintFilter tintFilter;
    private SeekBar tintBar, exposureBar, wbBar;
    private TextView tintValue, exposureValue, wbValue, status, zoomValue, recordTime;
    private TextView modeAuto, modePro;
    private LinearLayout proPanel;
    private Button recordButton, flashButton, flipButton, gridButton, shutterButton, isoButton;
    private boolean recording = false, gridOn = false, proMode = true;
    private int flashMode = 0;
    private float zoom = 0f;
    private File pendingVideo;
    private int shutterIndex = 1;
    private final String[] shutters = {"1/30", "1/60", "1/120", "1/240"};
    private final long[] shutterNs = {33_333_333L, 16_666_666L, 8_333_333L, 4_166_666L};
    private int isoIndex = 0;
    private final int[] isos = {100, 200, 400, 800, 1600};
    private int whiteBalanceKelvin = 4000;

    private final int ORANGE = Color.rgb(255, 95, 35);
    private final int WHITE = Color.WHITE;
    private final int MUTED = Color.rgb(185, 185, 190);
    private final int PANEL = Color.argb(215, 12, 12, 15);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        configureCamera();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        camera = new CameraView(this);
        camera.setKeepScreenOn(true);
        manualExposure = new ManualExposureController(camera);
        root.addView(camera, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = panel(12, 10, 12, 8);
        TextView logo = text("GT NEO2", 16, WHITE);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(logo, weight(0, 42, 1.2f));
        top.addView(text("VIDEO", 11, ORANGE), weight(0, 42, .7f));
        top.addView(text("1080P", 13, WHITE), weight(0, 42, .7f));
        top.addView(text("● MIC", 11, MUTED), weight(0, 42, .7f));
        flipButton = iconButton("↻");
        top.addView(flipButton, new LinearLayout.LayoutParams(dp(44), dp(42)));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, dp(62), Gravity.TOP);
        tp.setMargins(dp(8), dp(8), dp(8), 0);
        root.addView(top, tp);

        camera.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP && !recording) {
                try {
                    camera.startAutoFocus(e.getX(), e.getY());
                    showFocus(root, e.getX(), e.getY());
                    status.setText("AF · 对焦中");
                } catch (Exception ex) { status.setText("AF · 自动对焦不可用"); }
            }
            return true;
        });

        LinearLayout deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.VERTICAL);
        deck.setPadding(dp(14), dp(8), dp(14), dp(12));
        deck.setBackgroundColor(PANEL);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        status = text("AF", 12, ORANGE);
        recordTime = text("待机", 12, MUTED);
        statusRow.addView(status, weight(0, 28, 1));
        statusRow.addView(recordTime, weight(0, 28, 1));
        statusRow.addView(text("MANUAL VIDEO", 11, MUTED), weight(0, 28, 1));
        deck.addView(statusRow);

        LinearLayout modes = new LinearLayout(this);
        modes.setGravity(Gravity.CENTER_VERTICAL);
        modeAuto = modeButton("自动", false);
        TextView modeP = modeButton("P", false);
        modePro = modeButton("M", true);
        modes.addView(modeAuto, weight(0, 40, 1));
        modes.addView(modeP, weight(0, 40, .55f));
        modes.addView(modePro, weight(0, 40, .55f));
        deck.addView(modes);
        modeAuto.setOnClickListener(v -> setMode(false));
        modeP.setOnClickListener(v -> { try { camera.setExposureCorrection(0f); } catch (Exception ignored) {} exposureBar.setProgress(20); setMode(false); });
        modePro.setOnClickListener(v -> setMode(true));

        LinearLayout manualRow = new LinearLayout(this);
        manualRow.setGravity(Gravity.CENTER_VERTICAL);
        shutterButton = pill("快门  " + shutters[shutterIndex]);
        isoButton = pill("ISO  手动 " + isos[isoIndex]);
        manualRow.addView(shutterButton, weight(0, 42, 1));
        manualRow.addView(isoButton, weight(0, 42, 1));
        deck.addView(manualRow);
        shutterButton.setOnClickListener(v -> cycleShutter());
        isoButton.setOnClickListener(v -> cycleIso());

        LinearLayout wbHead = new LinearLayout(this);
        wbHead.addView(text("色温", 13, WHITE), weight(0, 30, .7f));
        wbValue = text("4000K", 14, ORANGE);
        wbValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        wbHead.addView(wbValue, weight(0, 30, .6f));
        deck.addView(wbHead);
        wbBar = slider();
        wbBar.setMax(5000); wbBar.setProgress(1500);
        wbBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean u) {
                whiteBalanceKelvin = 2500 + p;
                wbValue.setText(whiteBalanceKelvin + "K");
                if (tintFilter != null) tintFilter.setTemperatureKelvin(whiteBalanceKelvin);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        deck.addView(wbBar);

        LinearLayout tintHead = new LinearLayout(this);
        tintHead.addView(text("Tint", 13, WHITE), weight(0, 30, .7f));
        tintValue = text("0", 15, ORANGE); tintValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        tintHead.addView(tintValue, weight(0, 30, .5f));
        deck.addView(tintHead);
        tintBar = slider(); tintBar.setMax(200); tintBar.setProgress(100);
        tintBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean u) {
                int v = p - 100; tintValue.setText(String.valueOf(v));
                if (tintFilter != null) tintFilter.setAmount(v / 100f);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        deck.addView(tintBar);
        LinearLayout labels = new LinearLayout(this);
        labels.addView(text("-100 绿", 10, MUTED), weight(0, 20, 1));
        TextView center = text("色调", 10, MUTED); center.setGravity(Gravity.CENTER); labels.addView(center, weight(0, 20, 1));
        TextView mag = text("洋红 +100", 10, MUTED); mag.setGravity(Gravity.RIGHT); labels.addView(mag, weight(0, 20, 1));
        deck.addView(labels);

        proPanel = new LinearLayout(this);
        proPanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout expHead = new LinearLayout(this);
        expHead.addView(text("曝光补偿", 13, WHITE), weight(0, 30, .7f));
        exposureValue = text("手动传感器", 14, ORANGE); exposureValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        expHead.addView(exposureValue, weight(0, 30, .5f));
        proPanel.addView(expHead);
        exposureBar = slider(); exposureBar.setMax(40); exposureBar.setProgress(20);
        exposureBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean u) {
                float ev = (p - 20) / 10f;
                exposureValue.setText(String.format("手动 · %.1f EV", ev));
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        proPanel.addView(exposureBar);
        deck.addView(proPanel);

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = pill("−"); zoomValue = text("1.0×", 15, WHITE); zoomValue.setGravity(Gravity.CENTER);
        Button plus = pill("+"); flashButton = pill("闪光关"); gridButton = pill("网格");
        tools.addView(minus, weight(0, 38, .7f)); tools.addView(zoomValue, weight(0, 38, .8f)); tools.addView(plus, weight(0, 38, .7f));
        tools.addView(flashButton, weight(0, 38, 1f)); tools.addView(gridButton, weight(0, 38, 1f));
        deck.addView(tools);
        minus.setOnClickListener(v -> changeZoom(-.1f)); plus.setOnClickListener(v -> changeZoom(.1f));
        flashButton.setOnClickListener(v -> cycleFlash());
        gridButton.setOnClickListener(v -> { gridOn = !gridOn; camera.setGrid(gridOn ? Grid.DRAW_3X3 : Grid.OFF); gridButton.setText(gridOn ? "网格✓" : "网格"); });

        recordButton = new Button(this);
        recordButton.setText("●  开始录像"); recordButton.setTextColor(WHITE); recordButton.setTextSize(17); recordButton.setAllCaps(false);
        recordButton.setBackground(round(Color.rgb(190, 35, 30), 24)); recordButton.setOnClickListener(v -> toggleRecording());
        deck.addView(recordButton, new LinearLayout.LayoutParams(-1, dp(54)));

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bp.setMargins(dp(8), 0, dp(8), dp(8)); root.addView(deck, bp);
        setContentView(root);

        flipButton.setOnClickListener(v -> { if (!recording) { camera.setFacing(camera.getFacing() == Facing.BACK ? Facing.FRONT : Facing.BACK); status.setText(camera.getFacing() == Facing.BACK ? "AF · 后置" : "AF · 前置"); manualExposure.applyAfterCameraChanges(); } });
        applyManualExposure();
    }

    private void setMode(boolean pro) {
        proMode = pro;
        modePro.setTextColor(pro ? ORANGE : WHITE); modeAuto.setTextColor(!pro ? ORANGE : WHITE);
        proPanel.setVisibility(pro ? View.VISIBLE : View.GONE);
        if (pro) applyManualExposure();
    }

    private void cycleShutter() {
        if (recording) return;
        shutterIndex = (shutterIndex + 1) % shutters.length;
        shutterButton.setText("快门  " + shutters[shutterIndex]);
        applyManualExposure();
    }

    private void cycleIso() {
        if (recording) return;
        isoIndex = (isoIndex + 1) % isos.length;
        isoButton.setText("ISO  手动 " + isos[isoIndex]);
        applyManualExposure();
    }

    /** Uses the real Camera2 SENSOR_EXPOSURE_TIME and SENSOR_SENSITIVITY fields. */
    private void applyManualExposure() {
        if (manualExposure == null) return;
        manualExposure.setExposureTimeNs(shutterNs[shutterIndex]);
        manualExposure.setIso(isos[isoIndex]);
        exposureValue.setText("手动 · " + shutters[shutterIndex] + " · ISO " + isos[isoIndex]);
    }

    private void configureCamera() {
        tintFilter = new TintFilter();
        tintFilter.setTemperatureKelvin(4000);
        camera.setEngine(Engine.CAMERA2); camera.setPreview(Preview.GL_SURFACE); camera.setFacing(Facing.BACK);
        camera.setMode(Mode.VIDEO); camera.setAudio(Audio.ON); camera.setVideoCodec(VideoCodec.H_264);
        camera.setVideoBitRate(8_000_000); camera.setAudioBitRate(128_000); camera.setVideoMaxDuration(10 * 60 * 1000);
        camera.setFilter(tintFilter);
        camera.addCameraListener(new CameraListener() {
            @Override public void onVideoRecordingStart() { recording = true; manualExposure.applyAfterCameraChanges(); runOnUiThread(() -> { recordButton.setText("■  停止录像"); recordButton.setBackground(round(Color.rgb(150,25,25),24)); recordTime.setText("● 录像中"); status.setText("REC · MANUAL"); }); }
            @Override public void onVideoRecordingEnd() { recording = false; runOnUiThread(() -> { recordButton.setText("●  开始录像"); recordButton.setBackground(round(Color.rgb(190,35,30),24)); recordTime.setText("已停止"); status.setText("AF · 待机"); }); }
            @Override public void onVideoTaken(@NonNull VideoResult result) { File source = result.getFile(); if (source != null && source.exists()) saveToMovies(source); }
        });
        camera.setLifecycleOwner(this);
    }

    private void toggleRecording() {
        if (!hasPermissions()) { requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 100); return; }
        if (recording) { camera.stopVideo(); return; }
        applyManualExposure();
        pendingVideo = new File(getCacheDir(), "gtneo2_tint_" + System.currentTimeMillis() + ".mp4");
        try { camera.takeVideoSnapshot(pendingVideo, 10 * 60 * 1000); } catch (Exception e) { Toast.makeText(this, "录像启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void cycleFlash() {
        if (recording) return;
        flashMode = (flashMode + 1) % 3;
        try {
            if (flashMode == 0) { camera.setFlash(Flash.OFF); flashButton.setText("闪光关"); }
            else if (flashMode == 1) { camera.setFlash(Flash.ON); flashButton.setText("闪光开"); }
            else { camera.setFlash(Flash.AUTO); flashButton.setText("闪光自动"); }
        } catch (Exception e) { flashMode = 0; flashButton.setText("闪光关"); }
        manualExposure.applyAfterCameraChanges();
    }

    private void changeZoom(float d) {
        try { zoom = Math.max(0f, Math.min(1f, zoom + d)); camera.setZoom(zoom); zoomValue.setText(String.format("%.1f×", 1f + zoom * 7f)); manualExposure.applyAfterCameraChanges(); }
        catch (Exception e) { status.setText("变焦不可用"); }
    }

    private void showFocus(FrameLayout root, float x, float y) {
        TextView ring = text("＋", 34, ORANGE); ring.setGravity(Gravity.CENTER); ring.setBackground(round(Color.TRANSPARENT,30));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(62),dp(62)); p.leftMargin=(int)x-dp(31); p.topMargin=(int)y-dp(31); root.addView(ring,p); ring.postDelayed(() -> root.removeView(ring),1000);
    }

    private boolean hasPermissions() { return checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED; }

    private void saveToMovies(File source) {
        new Thread(() -> { try {
            ContentValues values = new ContentValues(); values.put(MediaStore.Video.Media.DISPLAY_NAME,source.getName()); values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
            if (android.os.Build.VERSION.SDK_INT>=29) { values.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/GTNeo2Tint"); values.put(MediaStore.Video.Media.IS_PENDING,1); }
            Uri uri=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values); if(uri==null) throw new IllegalStateException("无法创建媒体文件");
            try(FileInputStream in=new FileInputStream(source); OutputStream out=getContentResolver().openOutputStream(uri)){ byte[] buf=new byte[65536]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n); }
            if(android.os.Build.VERSION.SDK_INT>=29){ ContentValues done=new ContentValues(); done.put(MediaStore.Video.Media.IS_PENDING,0); getContentResolver().update(uri,done,null,null); }
            source.delete(); runOnUiThread(() -> status.setText("已保存 · Movies/GTNeo2Tint"));
        } catch(Exception e){ runOnUiThread(() -> status.setText("保存失败")); } }).start();
    }

    @Override public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] results){ super.onRequestPermissionsResult(requestCode,permissions,results); if(requestCode==100&&hasPermissions()) status.setText("AF · 权限已允许"); }
    private LinearLayout panel(int l,int t,int r,int b){ LinearLayout x=new LinearLayout(this); x.setGravity(Gravity.CENTER_VERTICAL); x.setPadding(dp(l),dp(t),dp(r),dp(b)); x.setBackgroundColor(Color.argb(165,8,8,10)); return x; }
    private TextView text(String s,float size,int color){ TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView modeButton(String s,boolean active){ TextView v=text(s,15,active?ORANGE:WHITE); v.setGravity(Gravity.CENTER); v.setBackground(round(Color.argb(80,80,80,85),18)); v.setPadding(dp(8),0,dp(8),0); return v; }
    private Button iconButton(String s){ return pill(s); }
    private Button pill(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(12); b.setTextColor(WHITE); b.setAllCaps(false); b.setBackground(round(Color.argb(100,80,80,85),20)); return b; }
    private SeekBar slider(){ SeekBar s=new SeekBar(this); s.setPadding(dp(2),0,dp(2),0); return s; }
    private LinearLayout.LayoutParams weight(int w,int h,float weight){ return new LinearLayout.LayoutParams(dp(w),dp(h),weight); }
    private GradientDrawable round(int color,int radius){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
}
