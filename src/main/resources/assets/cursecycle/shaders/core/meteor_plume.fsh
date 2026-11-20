#version 150
in vec2 uv;
in vec4 vertexColor;
uniform vec4 ColorModulator;
uniform float EffectTime;
out vec4 fragColor;
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    float a = fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453);
    float b = fract(sin(dot(i + vec2(1, 0), vec2(127.1, 311.7))) * 43758.5453);
    float c = fract(sin(dot(i + vec2(0, 1), vec2(127.1, 311.7))) * 43758.5453);
    float d = fract(sin(dot(i + vec2(1, 1), vec2(127.1, 311.7))) * 43758.5453);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
void main() {
    float t = EffectTime * 3.0 + vertexColor.g * 19.0;
    float a = uv.x * 6.283185;
    vec2 flow = vec2(sin(a) * 3.0 + cos(a * 3.0 + uv.y * 6.0 - t) * .5, uv.y * 9.0 - t * 2.0);
    float n = noise(flow) * .65 + noise(flow * 2.7) * .35;
    float heat = clamp(n * 1.5 + (1.0 - uv.y) * .4, 0.0, 1.0);
    float edge = 1.0 - smoothstep(.75, 1.0, uv.y + (n - .5) * .2);
    float alpha = smoothstep(.15 + uv.y * .18, .62, n) * edge * vertexColor.a;
    if (alpha < .015) discard;
    vec3 fire = mix(vec3(.9, .075, .008), vec3(1.0, .63, .07), smoothstep(.25, .75, heat));
    fire = mix(fire, vec3(1.0, .97, .65), smoothstep(.78, 1.0, heat));
    fragColor = vec4(fire, alpha) * ColorModulator;
}
