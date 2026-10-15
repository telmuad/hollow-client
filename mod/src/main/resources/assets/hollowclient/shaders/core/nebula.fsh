#version 330

// Hollow Client: nebula background for the title screen (original shader).
// Colors come from a tiny palette texture: gradient start, gradient end, accent, background.

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec2 params;
in vec4 extra;

out vec4 fragColor;

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
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 7; i++) {
        if (i >= octaves) break;
        v += amp * noise(p);
        p = rot * p * 2.03 + vec2(1.7, 9.2);
        amp *= 0.5;
    }
    return v;
}

// A layer of stars: one candidate star per grid cell, twinkling.
float stars(vec2 uv, float scale, float t, float density) {
    vec2 g = uv * scale;
    vec2 cell = floor(g);
    vec2 f = fract(g);
    float h = hash(cell);
    if (h > density) return 0.0;
    vec2 pos = vec2(hash(cell + 7.1), hash(cell + 3.7)) * 0.8 + 0.1;
    float d = length(f - pos);
    float size = 0.02 + 0.05 * hash(cell + 1.3);
    float twinkle = 0.55 + 0.45 * sin(t * (1.0 + 2.0 * hash(cell + 5.5)) + h * 40.0);
    return smoothstep(size, 0.0, d) * twinkle;
}

void main() {
    vec2 res = max(ScreenSize, vec2(1.0));
    vec2 frag = gl_FragCoord.xy;
    vec2 uv = (frag - 0.5 * res) / res.y;          // centered, aspect-correct, y up

    float t = params.x;
    float parallax = params.y;
    vec2 mouse = vec2(extra.r - 0.5, 0.5 - extra.g); // GUI y is down, GL y is up
    int octaves = 3 + int(extra.b * 3.0 + 0.5);
    float brightness = extra.a;

    vec3 c0 = texture(Sampler0, vec2(0.125, 0.5)).rgb;
    vec3 c1 = texture(Sampler0, vec2(0.375, 0.5)).rgb;
    vec3 accent = texture(Sampler0, vec2(0.625, 0.5)).rgb;
    vec3 bg = texture(Sampler0, vec2(0.875, 0.5)).rgb;

    // Deep background: dark, slightly tinted toward the gradient at the top.
    vec3 col = mix(bg, mix(bg, c0, 0.18), smoothstep(-0.6, 0.8, uv.y));

    // Far stars barely move with the mouse; near ones move more (parallax).
    vec2 farUv = uv + mouse * 0.02 * parallax + vec2(t * 0.002, 0.0);
    vec2 nearUv = uv + mouse * 0.07 * parallax + vec2(t * 0.006, 0.0);
    col += vec3(0.75, 0.8, 1.0) * stars(farUv, 70.0, t, 0.45) * 0.55;

    // Nebula clouds: domain-warped fbm, colored along the theme gradient.
    vec2 cloudUv = uv * 1.6 + mouse * 0.04 * parallax;
    vec2 q = vec2(fbm(cloudUv + vec2(0.0, t * 0.02), octaves),
                  fbm(cloudUv + vec2(5.2, 1.3) - vec2(t * 0.015, 0.0), octaves));
    vec2 r = vec2(fbm(cloudUv + 3.0 * q + vec2(1.7, 9.2) + t * 0.01, octaves),
                  fbm(cloudUv + 3.0 * q + vec2(8.3, 2.8) - t * 0.012, octaves));
    float n = fbm(cloudUv + 2.5 * r, octaves);
    float density = smoothstep(0.28, 0.9, n);
    vec3 cloud = mix(c0, c1, clamp(r.x * 1.3, 0.0, 1.0));
    cloud = mix(cloud, accent, smoothstep(0.75, 1.0, n) * 0.5);
    // a soft band across the screen keeps the clouds away from the corners
    float band = exp(-pow((uv.y - uv.x * 0.25) * 1.6, 2.0));
    col += cloud * density * (0.45 + 0.75 * band) * 1.25;
    // bright filaments
    col += mix(c1, vec3(1.0), 0.4) * pow(density, 5.0) * 0.5 * band;

    col += vec3(1.0) * stars(nearUv, 28.0, t * 1.3, 0.22) * 0.9;

    // Gentle light near the mouse.
    float m = length(uv - mouse * vec2(res.x / res.y, 1.0));
    col += accent * 0.10 * exp(-m * m * 9.0) * parallax;

    // Vignette and brightness.
    float vig = smoothstep(1.25, 0.25, length(uv * vec2(0.85, 1.0)));
    col *= mix(0.55, 1.0, vig) * brightness;

    // Tiny dither against banding in the dark gradients.
    col += (hash(frag + fract(t)) - 0.5) / 255.0;
    fragColor = vec4(col, 1.0);
}
