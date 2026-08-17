package com.huangkang.gtneo2tint;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
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
    private TintFilter tintFilter;
    private SeekBar tintBar;
    private SeekBar exposureBar;
    private TextView tintValue;
    private TextView exposureValue;
    private TextView status;
    private TextView zoomValue;
    private Button recordButton;
    private Button flashButton;
    private Button flipButton;
    private Button gridButton;
    private boolean recording = false;
    private boolean gridOn = false;
    private int flashMode = 0;
    private float zoom = 0f;
    private File pendingVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureCamera();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        camera = new CameraView(this);
        camera.setKeepScreenOn(true);
        root.addView(camera, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Tap the preview to perform real Camera2 touch metering / autofocus.
        camera.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && !recording) {
                try {
                    camera.startAutoFocus(event.getX(), event.getY());
                    status.setText("对焦中");
                } catch (Exception e) {
                    status.setText("自动对焦不可用");
                }
            }
            return false;
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(6), dp(12), dp(10));
        controls.setBackgroundColor(0xFF101010);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        flipButton = smallButton("↺ 镜头");
        flashButton = smallButton("闪光关");
        gridButton = smallButton("网格关");
        top.addView(flipButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        top.addView(flashButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        top.addView(gridButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        controls.addView(top);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("GT Neo2 Camera", 18);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        status = label("Camera2 · OpenGL", 12);
        status.setTextColor(0xFFB0B0B0);
        titleRow.addView(status);
        controls.addView(titleRow);

        tintValue = label("Tint  0", 17);
        tintValue.setGravity(Gravity.CENTER);
        controls.addView(tintValue);
        tintBar = new SeekBar(this);
        tintBar.setMax(200);
        tintBar.setProgress(100);
        tintBar.setContentDescription("Tint -100 to +100");
        tintBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress - 100;
                tintValue.setText("Tint  " + value);
                if (tintFilter != null) tintFilter.setAmount(value / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        controls.addView(tintBar);

        LinearLayout scale = new LinearLayout(this);
        TextView green = label("-100 绿", 11);
        TextView magenta = label("洋红 +100", 11);
        magenta.setGravity(Gravity.RIGHT);
        scale.addView(green, new LinearLayout.LayoutParams(0, -2, 1f));
        scale.addView(magenta, new LinearLayout.LayoutParams(0, -2, 1f));
        controls.addView(scale);

        exposureValue = label("曝光  0.0 EV", 15);
        exposureValue.setGravity(Gravity.CENTER);
        controls.addView(exposureValue);
        exposureBar = new SeekBar(this);
        exposureBar.setMax(40);
        exposureBar.setProgress(20);
        exposureBar.setContentDescription("Exposure correction");
        exposureBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float ev = (progress - 20) / 10f;
                try {
                    camera.setExposureCorrection(ev);
                    exposureValue.setText(String.format("曝光  %.1f EV", ev));
                } catch (Exception ignored) { }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        controls.addView(exposureBar);

        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setGravity(Gravity.CENTER_VERTICAL);
        Button zoomOut = smallButton("−");
        zoomValue = label("1.0×", 15);
        zoomValue.setGravity(Gravity.CENTER);
        Button zoomIn = smallButton("+");
        zoomRow.addView(zoomOut, new LinearLayout.LayoutParams(0, dp(42), 1f));
        zoomRow.addView(zoomValue, new LinearLayout.LayoutParams(0, dp(42), 1f));
        zoomRow.addView(zoomIn, new LinearLayout.LayoutParams(0, dp(42), 1f));
        controls.addView(zoomRow);
        zoomOut.setOnClickListener(v -> changeZoom(-0.1f));
        zoomIn.setOnClickListener(v -> changeZoom(0.1f));

        recordButton = new Button(this);
        recordButton.setText("开始录像");
        recordButton.setTextSize(17);
        recordButton.setOnClickListener(v -> toggleRecording());
        controls.addView(recordButton, new LinearLayout.LayoutParams(-1, dp(54)));

        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

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
            gridButton.setText(gridOn ? "网格开" : "网格关");
        });
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        return b;
    }

    private TextView label(String text, float size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(0xFFFFFFFF);
        v.setTextSize(size);
        return v;
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
                    recordButton.setText("停止录像");
                    status.setText("录像中 · Tint " + (tintBar.getProgress() - 100));
                });
            }
            @Override public void onVideoRecordingEnd() {
                recording = false;
                runOnUiThread(() -> recordButton.setText("开始录像"));
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
            if (flashMode == 0) {
                camera.setFlash(Flash.OFF);
                flashButton.setText("闪光关");
            } else if (flashMode == 1) {
                camera.setFlash(Flash.ON);
                flashButton.setText("闪光开");
            } else {
                camera.setFlash(Flash.AUTO);
                flashButton.setText("闪光自动");
            }
        } catch (Exception e) {
            flashMode = 0;
            flashButton.setText("闪光不可用");
        }
    }

    private void changeZoom(float delta) {
        try {
            zoom = Math.max(0f, Math.min(1f, zoom + delta));
            camera.setZoom(zoom);
            float display = 1f + zoom * 7f;
            zoomValue.setText(String.format("%.1f×", display));
        } catch (Exception e) {
            status.setText("变焦不可用");
        }
    }

    private void toggleRecording() {
        if (!hasPermissions()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }
        if (recording) {
            camera.stopVideo();
            return;
        }
        pendingVideo = new File(getCacheDir(), "gtneo2_tint_" + System.currentTimeMillis() + ".mp4");
        try {
            camera.takeVideoSnapshot(pendingVideo, 10 * 60 * 1000);
        } catch (Exception e) {
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
                try (FileInputStream in = new FileInputStream(source);
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
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
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("保存失败：" + e.getMessage()));
            }
        }).start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && hasPermissions()) status.setText("权限已允许 · 可以录像");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
