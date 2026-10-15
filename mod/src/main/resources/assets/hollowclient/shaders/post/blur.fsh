#version 330

// Hollow Client bloom, steps 2 and 3: a 9-tap gaussian blur in one direction, using 5 bilinear samples.

#moj_import <hollowclient:hollow_post.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 d = Direction;
    vec3 s = texture(InSampler, texCoord).rgb * 0.2270270;
    s += (texture(InSampler, texCoord + d * 1.3846154).rgb + texture(InSampler, texCoord - d * 1.3846154).rgb) * 0.3162162;
    s += (texture(InSampler, texCoord + d * 3.2307692).rgb + texture(InSampler, texCoord - d * 3.2307692).rgb) * 0.0702703;
    fragColor = vec4(s, 1.0);
}
