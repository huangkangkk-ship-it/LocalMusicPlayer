package com.huangkang.gtneo2tint;

import android.opengl.GLES20;
import androidx.annotation.NonNull;
import com.otaliastudios.cameraview.filter.BaseFilter;
import com.otaliastudios.cameraview.filter.OneParameterFilter;

/** Real-time green/magenta Tint plus color-temperature correction. */
public final class TintFilter extends BaseFilter implements OneParameterFilter {
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float tintAmount;\n" +
            "uniform float temperature;\n" +
            "varying vec2 " + DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME + ";\n" +
            "void main() {\n" +
            "  vec4 color = texture2D(sTexture, " + DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME + ");\n" +
            "  float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
            "  float strength = (0.22 + 0.78 * luma) * tintAmount;\n" +
            "  vec3 shifted = color.rgb + vec3(strength, -strength, strength);\n" +
            "  float t = temperature;\n" +
            "  shifted.r += t * 0.16;\n" +
            "  shifted.g += t * 0.035;\n" +
            "  shifted.b -= t * 0.16;\n" +
            "  gl_FragColor = vec4(clamp(shifted, 0.0, 1.0), color.a);\n" +
            "}\n";

    private float amount = 0f;
    private float temperature = 0f;
    private int tintLocation = -1;
    private int temperatureLocation = -1;

    @Override public void setParameter1(float value) {
        amount = Math.max(-1f, Math.min(1f, value * 2f - 1f));
    }
    @Override public float getParameter1() { return (amount + 1f) * 0.5f; }
    public void setAmount(float value) { amount = Math.max(-1f, Math.min(1f, value)); }

    /** 4000K is neutral; lower values cool the image, higher values warm it. */
    public void setTemperatureKelvin(int kelvin) {
        temperature = Math.max(-1f, Math.min(1f, (kelvin - 4000f) / 2500f));
    }

    @NonNull @Override public String getFragmentShader() { return FRAGMENT_SHADER; }

    @Override public void onCreate(int programHandle) {
        super.onCreate(programHandle);
        tintLocation = GLES20.glGetUniformLocation(programHandle, "tintAmount");
        temperatureLocation = GLES20.glGetUniformLocation(programHandle, "temperature");
        if (tintLocation < 0 || temperatureLocation < 0) throw new IllegalStateException("Tint uniforms not found");
    }
    @Override public void onDestroy() {
        super.onDestroy();
        tintLocation = -1;
        temperatureLocation = -1;
    }
    @Override protected void onPreDraw(long timestampUs, @NonNull float[] transformMatrix) {
        super.onPreDraw(timestampUs, transformMatrix);
        GLES20.glUniform1f(tintLocation, amount);
        GLES20.glUniform1f(temperatureLocation, temperature);
    }
}
