package com.huangkang.gtneo2tint;

import android.opengl.GLES20;
import androidx.annotation.NonNull;
import com.otaliastudios.cameraview.filter.BaseFilter;
import com.otaliastudios.cameraview.filter.OneParameterFilter;

/** Real-time color pipeline: Tint, white balance, saturation, contrast, highlights, shadows and sharpness. */
public final class TintFilter extends BaseFilter implements OneParameterFilter {
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float tintAmount;\n" +
            "uniform float temperature;\n" +
            "uniform float saturation;\n" +
            "uniform float contrast;\n" +
            "uniform float highlights;\n" +
            "uniform float shadows;\n" +
            "uniform float sharpness;\n" +
            "varying vec2 " + DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME + ";\n" +
            "void main() {\n" +
            "  vec2 uv = " + DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME + ";\n" +
            "  vec4 color = texture2D(sTexture, uv);\n" +
            "  float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
            "  float tintStrength = (0.22 + 0.78 * luma) * tintAmount;\n" +
            "  vec3 c = color.rgb + vec3(tintStrength, -tintStrength, tintStrength);\n" +
            "  float t = temperature;\n" +
            "  c.r += t * 0.16; c.g += t * 0.035; c.b -= t * 0.16;\n" +
            "  float lum = dot(c, vec3(0.2126,0.7152,0.0722));\n" +
            "  float shadowMask = 1.0 - smoothstep(0.05, 0.55, lum);\n" +
            "  float highlightMask = smoothstep(0.45, 0.95, lum);\n" +
            "  c += shadows * shadowMask * 0.18;\n" +
            "  c += highlights * highlightMask * -0.18;\n" +
            "  c = (c - 0.5) * contrast + 0.5;\n" +
            "  float y = dot(c, vec3(0.2126,0.7152,0.0722));\n" +
            "  c = mix(vec3(y), c, saturation);\n" +
            "  if (sharpness > 0.001) {\n" +
            "    float px = 1.0 / 1080.0;\n" +
            "    vec3 n = texture2D(sTexture, uv + vec2(0.0, px)).rgb;\n" +
            "    vec3 s = texture2D(sTexture, uv - vec2(0.0, px)).rgb;\n" +
            "    vec3 e = texture2D(sTexture, uv + vec2(px, 0.0)).rgb;\n" +
            "    vec3 w = texture2D(sTexture, uv - vec2(px, 0.0)).rgb;\n" +
            "    vec3 blur = (n+s+e+w) * 0.25;\n" +
            "    c += (c - blur) * sharpness;\n" +
            "  }\n" +
            "  gl_FragColor = vec4(clamp(c, 0.0, 1.0), color.a);\n" +
            "}\n";

    private float amount = 0f;
    private float temperature = 0f;
    private float saturation = 1f;
    private float contrast = 1f;
    private float highlights = 0f;
    private float shadows = 0f;
    private float sharpness = 0f;
    private int tintLocation = -1, temperatureLocation = -1, saturationLocation = -1;
    private int contrastLocation = -1, highlightsLocation = -1, shadowsLocation = -1, sharpnessLocation = -1;

    @Override public void setParameter1(float value) { amount = Math.max(-1f, Math.min(1f, value * 2f - 1f)); }
    @Override public float getParameter1() { return (amount + 1f) * 0.5f; }
    public void setAmount(float value) { amount = Math.max(-1f, Math.min(1f, value)); }
    public void setTemperatureKelvin(int kelvin) { temperature = Math.max(-1f, Math.min(1f, (kelvin - 4000f) / 2500f)); }
    public void setSaturation(int value) { saturation = Math.max(0f, Math.min(2f, value / 100f)); }
    public void setContrast(int value) { contrast = Math.max(0f, Math.min(2f, value / 100f)); }
    public void setHighlights(int value) { highlights = Math.max(-1f, Math.min(1f, value / 100f)); }
    public void setShadows(int value) { shadows = Math.max(-1f, Math.min(1f, value / 100f)); }
    public void setSharpness(int value) { sharpness = Math.max(0f, Math.min(1f, value / 100f)); }

    @NonNull @Override public String getFragmentShader() { return FRAGMENT_SHADER; }
    @Override public void onCreate(int programHandle) {
        super.onCreate(programHandle);
        tintLocation=GLES20.glGetUniformLocation(programHandle,"tintAmount"); temperatureLocation=GLES20.glGetUniformLocation(programHandle,"temperature");
        saturationLocation=GLES20.glGetUniformLocation(programHandle,"saturation"); contrastLocation=GLES20.glGetUniformLocation(programHandle,"contrast");
        highlightsLocation=GLES20.glGetUniformLocation(programHandle,"highlights"); shadowsLocation=GLES20.glGetUniformLocation(programHandle,"shadows"); sharpnessLocation=GLES20.glGetUniformLocation(programHandle,"sharpness");
        if(tintLocation<0||temperatureLocation<0||saturationLocation<0||contrastLocation<0||highlightsLocation<0||shadowsLocation<0||sharpnessLocation<0) throw new IllegalStateException("Color uniforms not found");
    }
    @Override public void onDestroy(){ super.onDestroy(); tintLocation=temperatureLocation=saturationLocation=contrastLocation=highlightsLocation=shadowsLocation=sharpnessLocation=-1; }
    @Override protected void onPreDraw(long timestampUs,@NonNull float[] transformMatrix){
        super.onPreDraw(timestampUs,transformMatrix);
        GLES20.glUniform1f(tintLocation,amount); GLES20.glUniform1f(temperatureLocation,temperature); GLES20.glUniform1f(saturationLocation,saturation); GLES20.glUniform1f(contrastLocation,contrast); GLES20.glUniform1f(highlightsLocation,highlights); GLES20.glUniform1f(shadowsLocation,shadows); GLES20.glUniform1f(sharpnessLocation,sharpness);
    }
}
