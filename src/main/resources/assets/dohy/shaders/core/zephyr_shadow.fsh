#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 shape;
in vec4 vertexColor;

out vec4 fragColor;

// Une ombre douce ou un halo : l'opacite tombe de la couleur pleine (UV nul) a rien (a
// une distance de 1), en courbe douce (smoothstep). Une ombre est coupee en neuf : les
// coins mesurent depuis leur coin interieur, les bords depuis leur bord interieur.
void main() {
    float t = clamp(1.0 - length(shape), 0.0, 1.0);
    vec4 color = vertexColor;
    color.a *= t * t * (3.0 - 2.0 * t);
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
