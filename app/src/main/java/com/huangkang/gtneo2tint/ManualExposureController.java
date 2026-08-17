package com.huangkang.gtneo2tint;

import android.hardware.camera2.Camera2Engine;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.Looper;

import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.engine.CameraEngine;
import com.otaliastudios.cameraview.engine.Camera2Engine;
import com.otaliastudios.cameraview.engine.orchestrator.CameraState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Applies real Camera2 sensor exposure controls to CameraView's Camera2 engine.
 * CameraView 2.7.2 does not expose shutter/ISO publicly, so this bridges to the
 * engine's existing repeating CaptureRequest without replacing the camera stack.
 */
public final class ManualExposureController {
    private final CameraView camera;
    private long exposureTimeNs = 16_666_666L; // 1/60 s
    private int iso = 100;
    private final Handler main = new Handler(Looper.getMainLooper());

    public ManualExposureController(CameraView camera) {
        this.camera = camera;
    }

    public void setExposureTimeNs(long value) {
        exposureTimeNs = Math.max(1_000_000L, value);
        scheduleApply();
    }

    public void setIso(int value) {
        iso = Math.max(50, value);
        scheduleApply();
    }

    public void applyAfterCameraChanges() {
        scheduleApply();
    }

    private void scheduleApply() {
        main.post(() -> {
            try {
                Field engineField = CameraView.class.getDeclaredField("mCameraEngine");
                engineField.setAccessible(true);
                Object engine = engineField.get(camera);
                if (!(engine instanceof Camera2Engine)) return;

                Method getOrchestrator = CameraEngine.class.getDeclaredMethod("getOrchestrator");
                getOrchestrator.setAccessible(true);
                Object orchestrator = getOrchestrator.invoke(engine);
                Method schedule = orchestrator.getClass().getMethod(
                        "scheduleStateful", String.class, CameraState.class, Runnable.class);
                schedule.invoke(orchestrator, "manual exposure", CameraState.PREVIEW,
                        (Runnable) () -> applyOnEngineThread((Camera2Engine) engine));
            } catch (Throwable ignored) {
                // CameraView can be between engine states. The next UI change retries.
            }
        });
    }

    private void applyOnEngineThread(Camera2Engine engine) {
        try {
            Field builderField = Camera2Engine.class.getDeclaredField("mRepeatingRequestBuilder");
            builderField.setAccessible(true);
            CaptureRequest.Builder builder = (CaptureRequest.Builder) builderField.get(engine);
            if (builder == null) return;

            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTimeNs);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
            // Keep the frame period valid for 30fps video while allowing the selected shutter.
            builder.set(CaptureRequest.SENSOR_FRAME_DURATION, Math.max(exposureTimeNs, 33_333_333L));

            Method apply = Camera2Engine.class.getDeclaredMethod("applyRepeatingRequestBuilder");
            apply.setAccessible(true);
            apply.invoke(engine);
        } catch (Throwable ignored) {
            // Unsupported manual-sensor capability or a transient camera state.
        }
    }
}
