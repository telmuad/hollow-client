#version 330

// Hollow Client post-processing, final step: bloom, chromatic aberration, color grading, vignette, film grain and
// motion blur (blend with the previous frame), written back into the frame.

#moj_import <hollowclient:hollow_post.glsl>

uniform sampler2D MainSampler;
uniform sampler2D BloomSampler;
uniform sampler2D PrevSampler;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

void main() {
    vec2 uv = texCoord;
    vec2 fromCenter = uv - 0.5;
    float dist = length(fromCenter);

    vec3 col;
    float chroma = Effects.y;
    if (chroma > 0.0) {
        // red and blue are pulled apart more toward the edges
        vec2 off = fromCenter * dist * chroma * 0.03;
        col.r = texture(MainSampler, uv + off).r;
        col.g = texture(MainSampler, uv).g;
        col.b = texture(MainSampler, uv - off).b;
    } else {
        col = texture(MainSampler, uv).rgb;
    }

    // bloom: add the blurred bright parts, gently compressed so whites don't clip hard
    vec3 glow = texture(BloomSampler, uv).rgb * Bloom.y;
    col = col + glow * (1.0 - col * 0.5);

    // color grading
    col *= Grade.z;
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(luma), col, Grade.x);
    col = (col - 0.5) * Grade.y + 0.5;
    col = col * Tint.rgb + Lift.rgb * (1.0 - col);

    // vignette
    col *= 1.0 - Effects.x * smoothstep(0.3, 0.85, dist * 1.15);

    // film grain, a little stronger in the mid-tones
    if (Effects.z > 0.0) {
        float n = hash(uv * OutSize + vec2(fract(Effects.w * 7.13) * 431.0, fract(Effects.w * 3.71) * 917.0)) - 0.5;
        col += n * Effects.z * 0.14 * (1.0 - abs(luma - 0.5));
    }

    col = clamp(col, 0.0, 1.0);

    // motion blur: blend with the previous finished frame
    if (Lift.w > 0.0) col = mix(col, texture(PrevSampler, uv).rgb, Lift.w);

    fragColor = vec4(col, 1.0);
}
