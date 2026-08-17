package com.huangkang.gtneo2tint;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
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
    private TextView tintValue;
    private TextView status;
    private Button recordButton;
    private boolean recording = false;
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

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(18), dp(8), dp(18), dp(12));
        controls.setBackgroundColor(0xFF101010);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("GT Neo2 Camera", 18);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        status = label("Camera2 · OpenGL", 13);
        status.setTextColor(0xFFB0B0B0);
        titleRow.addView(status);
        controls.addView(titleRow);

        tintValue = label("Tint  0", 18);
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
        scale.setGravity(Gravity.CENTER_VERTICAL);
        TextView green = label("-100  绿", 12);
        TextView magenta = label("洋红  +100", 12);
        magenta.setGravity(Gravity.RIGHT);
        scale.addView(green, new LinearLayout.LayoutParams(0, -2, 1f));
        scale.addView(magenta, new LinearLayout.LayoutParams(0, -2, 1f));
        controls.addView(scale);

        recordButton = new Button(this);
        recordButton.setText("开始录像");
        recordButton.setTextSize(17);
        recordButton.setOnClickListener(v -> toggleRecording());
        controls.addView(recordButton, new LinearLayout.LayoutParams(-1, dp(54)));

        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
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
            // Video snapshots use the same GL preview pipeline as the displayed frame,
            // so the custom TintFilter is rendered into the recorded MP4.
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && hasPermissions()) status.setText("权限已允许 · 可以录像");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
