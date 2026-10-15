#version 330

// Hollow Client post-processing: the uniform block shared by every pass (written each frame by PostFx.java).
layout(std140) uniform HollowPost {
    vec4 Bloom;     // x threshold, y intensity
    vec4 Effects;   // x vignette, y chromatic aberration, z grain, w time (s)
    vec4 Grade;     // x saturation, y contrast, z exposure
    vec4 Tint;      // rgb highlight tint
    vec4 Lift;      // rgb shadow lift, w motion blur blend
    vec2 Direction; // blur step in texture coordinates
    vec2 OutSize;   // size of the pass output in pixels
};
