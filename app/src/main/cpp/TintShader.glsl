#version 300 es
precision mediump float;
uniform sampler2D uTexture;
uniform float uTint;
in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    vec4 c = texture(uTexture, vTexCoord);
    float t = clamp(uTint, -1.0, 1.0);
    // Tint: negative -> green, positive -> magenta.
    float amount = abs(t) * 0.35;
    vec3 magenta = vec3(c.r + amount * c.r, c.g - amount * c.g, c.b + amount * c.b);
    vec3 green   = vec3(c.r - amount * c.r, c.g + amount * c.g, c.b - amount * c.b);
    vec3 outRgb = mix(green, magenta, step(0.0, t));
    fragColor = vec4(clamp(outRgb, 0.0, 1.0), c.a);
}
