#version 330

// Hollow Client bloom, step 1: keep the bright parts of the frame (soft threshold) while shrinking it.

#moj_import <hollowclient:hollow_post.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 px = 0.35 / OutSize;
    vec3 c = texture(InSampler, texCoord + vec2(-px.x, -px.y)).rgb
           + texture(InSampler, texCoord + vec2(px.x, -px.y)).rgb
           + texture(InSampler, texCoord + vec2(-px.x, px.y)).rgb
           + texture(InSampler, texCoord + vec2(px.x, px.y)).rgb;
    c *= 0.25;
    float brightness = max(c.r, max(c.g, c.b));
    float threshold = Bloom.x;
    float knee = 0.25;
    float soft = clamp(brightness - threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1e-4);
    float amount = max(soft, brightness - threshold) / max(brightness, 1e-4);
    fragColor = vec4(c * amount, 1.0);
}
