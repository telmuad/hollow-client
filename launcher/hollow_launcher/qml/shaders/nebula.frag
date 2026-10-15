#version 440
// Hollow launcher nebula. One shader for the window background and for the "glass" panels: a panel draws the
// same nebula at its own position in the window, softer and darker, inside a rounded rectangle, which reads as
// frosted glass over the background without any blur passes.

layout(location = 0) in vec2 qt_TexCoord0;
layout(location = 0) out vec4 fragColor;

layout(std140, binding = 0) uniform buf {
    mat4 qt_Matrix;
    float qt_Opacity;
    float time;
    float frost;       // 0 = background, 1 = frosted glass
    float radius;      // corner radius in pixels (0 = square)
    vec2 mouse;        // mouse position in window pixels
    vec2 itemPos;      // this item's top-left in window pixels
    vec2 itemSize;     // this item's size in pixels
    vec2 windowSize;
    vec4 colorA;       // theme gradient start
    vec4 colorB;       // theme gradient end
    vec4 base;         // background color
    float stars;       // 0..1 star amount
};

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p, int octaves) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        v += amp * noise(p);
        p = p * 2.03 + vec2(13.1, 7.7);
        amp *= 0.5;
    }
    return v;
}

float roundedBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 px = itemPos + qt_TexCoord0 * itemSize;            // window pixels
    float aspect = windowSize.x / max(1.0, windowSize.y);
    vec2 uv = px / max(vec2(1.0), windowSize);
    vec2 m = mouse / max(vec2(1.0), windowSize);
    // parallax: the nebula leans a little toward the mouse
    vec2 par = (m - 0.5) * 0.06;
    vec2 p = vec2((uv.x - 0.5) * aspect, uv.y - 0.5) * 2.2 + par;
    float t = time * 0.035;
    int oct = frost > 0.5 ? 3 : 5;

    vec2 q = vec2(fbm(p + vec2(0.0, t), oct), fbm(p + vec2(5.2, -t * 0.8), oct));
    vec2 r = vec2(fbm(p + 3.0 * q + vec2(1.7, 9.2) + t * 0.6, oct), fbm(p + 3.0 * q + vec2(8.3, 2.8) - t * 0.4, oct));
    float n = fbm(p + 2.6 * r, oct);

    vec3 col = base.rgb;
    float cloud = smoothstep(0.28, 0.85, n);
    vec3 tint = mix(colorA.rgb, colorB.rgb, clamp(r.x * 1.25 - 0.1, 0.0, 1.0));
    col += tint * cloud * 0.5;
    col += mix(colorB.rgb, colorA.rgb, q.y) * pow(cloud, 2.5) * 0.55;
    // a faint wide haze so the dark areas aren't flat
    col += tint * smoothstep(0.2, 0.7, q.x) * 0.06;
    // light following the mouse
    vec2 dm = (uv - m) * vec2(aspect, 1.0);
    float glow = exp(-dot(dm, dm) * 9.0);
    col += tint * glow * 0.22;
    // stars, twinkling
    if (stars > 0.0 && frost < 0.5) {
        vec2 g = px / 3.0;
        vec2 cell = floor(g);
        float h = hash(cell);
        if (h > 0.9965) {
            vec2 c = cell + vec2(hash(cell + 1.3), hash(cell + 7.1));
            float d = length(g - c);
            float tw = 0.55 + 0.45 * sin(time * (1.0 + 3.0 * hash(cell + 2.0)) + h * 50.0);
            col += vec3(1.0) * smoothstep(0.9, 0.0, d) * tw * stars;
        }
    }
    // gentle vignette on the background
    vec2 v = uv - 0.5;
    col *= 1.0 - dot(v, v) * 0.55 * (1.0 - frost);

    float alpha = 1.0;
    if (frost > 0.0) {
        // frosted glass: darker, flatter, a soft sheen at the top edge
        col = mix(col, base.rgb, 0.45) * 0.78;
        float topSheen = smoothstep(0.0, 0.35, 1.0 - qt_TexCoord0.y) * 0.05;
        col += vec3(topSheen);
        alpha = 0.86;
    }
    if (radius > 0.0) {
        vec2 local = qt_TexCoord0 * itemSize - itemSize * 0.5;
        float d = roundedBox(local, itemSize * 0.5, radius);
        alpha *= clamp(0.5 - d, 0.0, 1.0);
    }
    fragColor = vec4(col * alpha, alpha) * qt_Opacity;
}
