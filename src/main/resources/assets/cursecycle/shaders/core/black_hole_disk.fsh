#version 150

in vec3 localPosition;
in vec4 vertexColor;
uniform vec4 ColorModulator;
uniform float DiskTime;
out vec4 fragColor;

float grain(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    float radius = length(localPosition.xz);
    float angle = atan(localPosition.z, localPosition.x);
    float orbit = angle + DiskTime * 0.13;
    float bands = sin(radius * 180.0 + sin(orbit * 7.0) * 3.0);
    float wisps = sin(orbit * 37.0 + radius * 95.0 + sin(orbit * 11.0) * 2.0);
    float dust = grain(floor(vec2(orbit * 170.0, radius * 480.0)));
    float energy = clamp(0.55 + bands * 0.17 + wisps * 0.23 + dust * 0.22, 0.1, 1.0);
    vec3 heat = mix(vec3(1.0, 0.27, 0.035), vec3(1.0, 0.94, 0.72), clamp((0.9 - radius) * 1.7, 0.0, 1.0));
    vec3 color = mix(vertexColor.rgb, heat, 0.8) * (0.45 + energy * 0.75);
    fragColor = vec4(color, smoothstep(0.10, 0.48, energy) * vertexColor.a) * ColorModulator;
}
