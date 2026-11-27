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

// Un quart de disque, ou d'anneau, par coin d'une forme arrondie (le reste de la forme,
// ce sont des quads pleins, a l'UV nul).
//
// L'UV va de 0 au coin interieur a 1 sur le bord : la distance au coin interieur, en
// rayons. Le bord est lisse sur un pixel, quelle que soit l'echelle (fwidth).
//
// Pour un anneau, le rayon interieur (sur 512) est porte par la partie entiere de l'UV,
// multipliee par deux : u = 2 N + x, avec x entre 0 et 1.
void main() {
    float ring = floor(shape.x * 0.5);
    vec2 uv = shape - vec2(2.0 * ring);
    float d = length(uv);
    float edge = max(fwidth(d), 0.0001);
    float coverage = clamp((1.0 - d) / edge + 0.5, 0.0, 1.0);
    if (ring > 0.5) {
        coverage *= clamp((d - ring / 512.0) / edge + 0.5, 0.0, 1.0);
    }
    vec4 color = vertexColor;
    color.a *= coverage;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
