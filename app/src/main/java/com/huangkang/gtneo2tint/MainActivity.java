package com.huangkang.gtneo2tint;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private CameraView camera;
    private TintFilter tintFilter;
    private SeekBar tintBar;
    private SeekBar exposureBar;
    private TextView tintValue;
    private TextView exposureValue;
    private TextView status;
    private TextView zoomValue;
    private TextView flashValue;
    private Button recordButton;
    private Button flashButton;
    private Button flipButton;
    private Button gridButton;
    private LinearLayout advancedPanel;
    private boolean recording = false;
    private boolean gridOn = false;
    private boolean advancedOpen = true;
    private int flashMode = 0;
    private float zoom = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        configureCamera();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        camera = new CameraView(this);
        camera.setKeepScreenOn(true);
        root.addView(camera, new FrameLayout.LayoutParams(-1, -1));

        camera.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && !recording) {
                try {
                    camera.startAutoFocus(event.getX(), event.getY());
                    status.setText("对焦中");
                    status.postDelayed(() -> { if (!recording) status.setText("自动对焦"); }, 900);
                } catch (Exception e) {
                    status.setText("自动对焦不可用");
                }
            }
            return false;
        });

        // Subtle top gradient panel.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(10));
        top.setBackground(gradient(0xCC000000, 0x00000000, true));

        TextView appTitle = label("GT Neo2  •  VIDEO", 17, Color.WHITE);
        appTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(appTitle, new LinearLayout.LayoutParams(0, dp(42), 1f));
        status = label("自动对焦", 12, 0xFFE0E0E0);
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        top.addView(status, new LinearLayout.LayoutParams(dp(90), dp(42)));
        root.addView(top, frameTop());

        // Compact control chips over the preview.
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER);
        chips.setPadding(dp(10), dp(4), dp(10), dp(4));
        flipButton = chip("↺  镜头");
        flashButton = chip("⚡ 关闭");
        gridButton = chip("▦  网格");
        Button advancedButton = chip("☷  参数");
        chips.addView(flipButton, weightChip());
        chips.addView(flashButton, weightChip());
        chips.addView(gridButton, weightChip());
        chips.addView(advancedButton, weightChip());
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(-1, dp(52));
        chipLp.gravity = Gravity.TOP;
        chipLp.topMargin = dp(62);
        root.addView(chips, chipLp);

        advancedPanel = new LinearLayout(this);
        advancedPanel.setOrientation(LinearLayout.VERTICAL);
        advancedPanel.setPadding(dp(16), dp(10), dp(16), dp(8));
        advancedPanel.setBackground(round(0xDD101114, dp(18)));
        advancedPanel.addView(parameterHeader("TINT", "绿色  ←  -100                 +100  →  洋红"));

        tintValue = label("0", 14, Color.WHITE);
        tintValue.setGravity(Gravity.CENTER);
        advancedPanel.addView(tintValue, new LinearLayout.LayoutParams(-1, dp(24)));
        tintBar = seek(200, 100);
        tintBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int value = p - 100;
                tintValue.setText("Tint  " + (value > 0 ? "+" : "") + value);
                if (tintFilter != null) tintFilter.setAmount(value / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        advancedPanel.addView(tintBar);

        advancedPanel.addView(parameterHeader("EXPOSURE", "暗  ←  EV  →  亮"));
        exposureValue = label("0.0 EV", 14, Color.WHITE);
        exposureValue.setGravity(Gravity.CENTER);
        advancedPanel.addView(exposureValue, new LinearLayout.LayoutParams(-1, dp(24)));
        exposureBar = seek(40, 20);
        exposureBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float ev = (p - 20) / 10f;
                try {
                    camera.setExposureCorrection(ev);
                    exposureValue.setText(String.format(Locale.US, "%+.1f EV", ev));
                } catch (Exception ignored) {}
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        advancedPanel.addView(exposureBar);

        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setGravity(Gravity.CENTER_VERTICAL);
        Button zoomOut = chip("−");
        zoomValue = label("1.0×", 15, Color.WHITE);
        zoomValue.setGravity(Gravity.CENTER);
        Button zoomIn = chip("+");
        zoomRow.addView(zoomOut, new LinearLayout.LayoutParams(0, dp(42), 1f));
        zoomRow.addView(zoomValue, new LinearLayout.LayoutParams(0, dp(42), 1f));
        zoomRow.addView(zoomIn, new LinearLayout.LayoutParams(0, dp(42), 1f));
        advancedPanel.addView(zoomRow);
        zoomOut.setOnClickListener(v -> changeZoom(-0.1f));
        zoomIn.setOnClickListener(v -> changeZoom(0.1f));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, dp(250));
        panelLp.gravity = Gravity.BOTTOM;
        panelLp.leftMargin = dp(10);
        panelLp.rightMargin = dp(10);
        panelLp.bottomMargin = dp(108);
        root.addView(advancedPanel, panelLp);

        // Bottom camera deck.
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(12));
        bottom.setBackground(gradient(0x00000000, 0xF5000000, false));

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        TextView mode = label("录像", 13, 0xFFBDBDBD);
        mode.setTypeface(null, android.graphics.Typeface.BOLD);
        info.addView(mode, new LinearLayout.LayoutParams(0, dp(30), 1f));
        flashValue = label("H.264  •  8 Mbps", 11, 0xFFBDBDBD);
        flashValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        info.addView(flashValue, new LinearLayout.LayoutParams(0, dp(30), 1f));
        bottom.addView(info);

        recordButton = new Button(this);
        recordButton.setText("●  开始录像");
        recordButton.setTextSize(16);
        recordButton.setTextColor(Color.WHITE);
        recordButton.setAllCaps(false);
        recordButton.setBackground(round(0xFFE53935, dp(28)));
        recordButton.setOnClickListener(v -> toggleRecording());
        bottom.addView(recordButton, new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(bottom, frameBottom());

        flipButton.setOnClickListener(v -> {
            if (!recording) {
                camera.setFacing(camera.getFacing() == Facing.BACK ? Facing.FRONT : Facing.BACK);
                status.setText(camera.getFacing() == Facing.BACK ? "后置摄像头" : "前置摄像头");
            }
        });
        flashButton.setOnClickListener(v -> cycleFlash());
        gridButton.setOnClickListener(v -> {
            gridOn = !gridOn;
            camera.setGrid(gridOn ? Grid.DRAW_3X3 : Grid.OFF);
            gridButton.setText(gridOn ? "▦  网格开" : "▦  网格");
        });
        advancedButton.setOnClickListener(v -> {
            advancedOpen = !advancedOpen;
            advancedPanel.setVisibility(advancedOpen ? View.VISIBLE : View.GONE);
        });

        setContentView(root);
    }

    private LinearLayout.LayoutParams weightChip() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private Button chip(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackground(round(0xB51A1C20, dp(22)));
        return b;
    }

    private TextView parameterHeader(String left, String right) {
        TextView t = label(left + "   " + right, 10, 0xFF9E9E9E);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private SeekBar seek(int max, int progress) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(progress);
        s.setPadding(dp(4), 0, dp(4), 0);
        return s;
    }

    private TextView label(String text, float size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(color);
        v.setTextSize(size);
        return v;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private android.graphics.drawable.GradientDrawable gradient(int start, int end, boolean top) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{start, end});
        return d;
    }

    private FrameLayout.LayoutParams frameTop() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, dp(62));
        p.gravity = Gravity.TOP;
        return p;
    }

    private FrameLayout.LayoutParams frameBottom() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, dp(108));
        p.gravity = Gravity.BOTTOM;
        return p;
    }

    private void configureCamera() {
        tintFilter = new TintFilter();
        tintFilter.setAmount(0f);
        camera.setEngine(Engine.CAMERA2);
        camera.setPreview(Preview.GL_SURFACE);
        camera.setFacing(Facing.BACK);
        camera.setMode(Mode.VIDEO);
        camera.setAudio(Audio.ON);
        camera.setVideoCodec(VideoCodec.H_264);
        camera.setVideoBitRate(8_000_000);
        camera.setAudioBitRate(128_000);
        camera.setVideoMaxDuration(10 * 60 * 1000);
        camera.setSnapshotMaxWidth(1920);
        camera.setSnapshotMaxHeight(1080);
        camera.setFilter(tintFilter);
        camera.addCameraListener(new CameraListener() {
            @Override public void onVideoRecordingStart() {
                recording = true;
                runOnUiThread(() -> {
                    recordButton.setText("■  停止录像");
                    recordButton.setBackground(round(0xFFB71C1C, dp(28)));
                    status.setText("● 录像中");
                    advancedPanel.setVisibility(View.GONE);
                });
            }
            @Override public void onVideoRecordingEnd() {
                recording = false;
                runOnUiThread(() -> {
                    recordButton.setText("●  开始录像");
                    recordButton.setBackground(round(0xFFE53935, dp(28)));
                    status.setText("正在保存");
                });
            }
            @Override public void onVideoTaken(@NonNull VideoResult result) {
                File source = result.getFile();
                if (source != null && source.exists()) saveToMovies(source);
            }
        });
        camera.setLifecycleOwner(this);
    }

    private void cycleFlash() {
        if (recording) return;
        flashMode = (flashMode + 1) % 3;
        try {
            if (flashMode == 0) { camera.setFlash(Flash.OFF); flashButton.setText("⚡ 关闭"); }
            else if (flashMode == 1) { camera.setFlash(Flash.ON); flashButton.setText("⚡ 开启"); }
            else { camera.setFlash(Flash.AUTO); flashButton.setText("⚡ 自动"); }
        } catch (Exception e) {
            flashMode = 0;
            flashButton.setText("⚡ 不可用");
        }
    }

    private void changeZoom(float delta) {
        try {
            zoom = Math.max(0f, Math.min(1f, zoom + delta));
            camera.setZoom(zoom);
            float display = 1f + zoom * 7f;
            zoomValue.setText(String.format(Locale.US, "%.1f×", display));
        } catch (Exception e) { status.setText("变焦不可用"); }
    }

    private void toggleRecording() {
        if (!hasPermissions()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }
        if (recording) { camera.stopVideo(); return; }
        File pendingVideo = new File(getCacheDir(), "gtneo2_tint_" + System.currentTimeMillis() + ".mp4");
        try { camera.takeVideoSnapshot(pendingVideo, 10 * 60 * 1000); }
        catch (Exception e) {
            status.setText("录像失败");
            Toast.makeText(this, "录像启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasPermissions() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void saveToMovies(File source) {
        new Thread(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, source.getName());
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GTNeo2Tint");
                    values.put(MediaStore.Video.Media.IS_PENDING, 1);
                }
                Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("无法创建媒体文件");
                try (FileInputStream in = new FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(uri)) {
                    byte[] buffer = new byte[1024 * 64];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Video.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, done, null, null);
                }
                source.delete();
                runOnUiThread(() -> status.setText("已保存到 Movies/GTNeo2Tint"));
            } catch (Exception e) { runOnUiThread(() -> status.setText("保存失败：" + e.getMessage())); }
        }).start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && hasPermissions()) status.setText("权限已允许 · 可以录像");
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
