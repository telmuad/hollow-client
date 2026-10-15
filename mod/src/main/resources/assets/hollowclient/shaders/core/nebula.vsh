#version 330

// Hollow Client: nebula background for the title screen (original shader).
// Inputs are packed into an ordinary GUI blit: UV0 = (time in seconds, parallax strength),
// vertex color = (mouse x, mouse y, quality, brightness). Screen size comes from the Globals block.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 params;
out vec4 extra;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    params = UV0;
    extra = Color;
}
