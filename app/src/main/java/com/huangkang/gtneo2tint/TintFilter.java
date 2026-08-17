package com.huangkang.gtneo2tint;

import android.opengl.GLES20;
import androidx.annotation.NonNull;
import com.otaliastudios.cameraview.filter.BaseFilter;
import com.otaliastudios.cameraview.filter.OneParameterFilter;

/** Real-time green/magenta tint filter. App range: -100..+100. */
public final class TintFilter extends BaseFilter implements OneParameterFilter {
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float tintAmount;\n" +
            "varying vec2 " + DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME + ";\n" +
            "void main() {\n" +
            "  vec4 color = texture2D(sTexture, " + DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME + ");\n" +
            "  float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
            "  float strength = (0.22 + 0.78 * luma) * tintAmount;\n" +
            "  vec3 shifted = color.rgb + vec3(strength, -strength, strength);\n" +
            "  gl_FragColor = vec4(clamp(shifted, 0.0, 1.0), color.a);\n" +
            "}\n";

    private float amount = 0f;
    private int tintLocation = -1;

    @Override public void setParameter1(float value) {
        amount = Math.max(-1f, Math.min(1f, value * 2f - 1f));
    }
    @Override public float getParameter1() { return (amount + 1f) * 0.5f; }
    public void setAmount(float value) { amount = Math.max(-1f, Math.min(1f, value)); }
    @NonNull @Override public String getFragmentShader() { return FRAGMENT_SHADER; }

    @Override public void onCreate(int programHandle) {
        super.onCreate(programHandle);
        tintLocation = GLES20.glGetUniformLocation(programHandle, "tintAmount");
        if (tintLocation < 0) throw new IllegalStateException("Tint uniform not found");
    }
    @Override public void onDestroy() { super.onDestroy(); tintLocation = -1; }
    @Override protected void onPreDraw(long timestampUs, @NonNull float[] transformMatrix) {
        super.onPreDraw(timestampUs, transformMatrix);
        GLES20.glUniform1f(tintLocation, amount);
    }
}
