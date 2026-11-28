#version 330

// Le moteur Zephyr du kit Berskar : les formes de l'interface (coins arrondis, anneaux,
// ombres, halos) dessinees au fragment, sans texture. Les blocs d'uniformes sont ceux
// des shaders d'interface du jeu (dynamictransforms.glsl et projection.glsl).
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

// La coordonnee de la forme portee par l'UV du sommet (voir zephyr_rounded.fsh, zephyr_shadow.fsh).
out vec2 shape;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    shape = UV0;
    vertexColor = Color;
}
