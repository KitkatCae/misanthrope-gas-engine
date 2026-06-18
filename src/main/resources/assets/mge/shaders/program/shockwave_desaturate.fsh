#version 150
/*
 * MGE shockwave — per-wave desaturation/whitening pass.
 *
 * Run once per active wave, AFTER all per-wave additive refraction+rim-glow
 * passes (shockwave_distort.fsh) have completed for every wave. Reads the
 * already-accumulated color buffer (which may include other waves' additive
 * contributions and, for waves after the first, this same desaturation
 * effect from earlier waves in the sequence) and desaturates/whitens it near
 * THIS wave's shell.
 *
 * This is intentionally NOT a single combined multi-wave pass — Mojang's
 * Uniform wrapper (com.mojang.blaze3d.shaders.Uniform) hard-caps uniform
 * component count at 4 (vec4/mat4), so true GLSL uniform arrays aren't
 * representable through the JSON-driven EffectInstance path Minecraft post
 * shaders use. Running this sequentially, once per wave, with the same
 * simple scalar/vector uniforms as the distortion pass, sidesteps that
 * limitation entirely at the cost of imperfect compounding where multiple
 * waves' shells overlap at the same pixel (each pass only sees the
 * "max so far" via blending, not a true combined maximum across all waves
 * at once) — accepted as a minor cosmetic tradeoff for simplicity.
 *
 * Blend mode is alpha-blend (not additive) since this pass needs to mix
 * toward gray/white, which additive blending cannot do.
 */

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

uniform mat4 InvViewProjMat;

uniform vec3  WaveOriginRel;
uniform float WaveRadius;
uniform vec4  WaveBuckets; // [fine, heavy, ash, exotic] mg/m3

in vec2 texCoord;
out vec4 fragColor;

const float SHELL_THICKNESS      = 1.6;
const float BUCKET_NORMALIZE_MAX = 400.0;

vec3 reconstructWorldPos(vec2 uv, float depth) {
    vec4 ndc = vec4(uv.x * 2.0 - 1.0, uv.y * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 worldPos = InvViewProjMat * ndc;
    return worldPos.xyz / worldPos.w;
}

void main() {
    vec3 original = texture(DiffuseSampler, texCoord).rgb;
    float depth = texture(DiffuseDepthSampler, texCoord).r;

    if (depth >= 0.9999 || WaveRadius < 0.0) {
        fragColor = vec4(original, 1.0);
        return;
    }

    vec3 worldPosRel = reconstructWorldPos(texCoord, depth);
    float dist = length(worldPosRel - WaveOriginRel);
    float absDelta = abs(dist - WaveRadius);

    if (absDelta > SHELL_THICKNESS) {
        fragColor = vec4(original, 1.0);
        return;
    }

    float t = 1.0 - smoothstep(0.0, SHELL_THICKNESS, absDelta);

    float ashAmt  = clamp(WaveBuckets.z / BUCKET_NORMALIZE_MAX, 0.0, 1.0);
    float fineAmt = clamp(WaveBuckets.x / BUCKET_NORMALIZE_MAX, 0.0, 1.0);

    // Ash drives both desaturation and whitening strongly; fine dust
    // contributes a softer haze-whitening without much desaturation —
    // matches the ParticulateBucket design (ash = dark/opaque/combustion,
    // fine = light low-opacity haze).
    float desatAmt  = clamp(t * ashAmt, 0.0, 0.85);
    float whitenAmt = clamp(t * (ashAmt * 0.7 + fineAmt * 0.4), 0.0, 0.6);

    float luminance = dot(original, vec3(0.299, 0.587, 0.114));
    vec3 desaturated = mix(original, vec3(luminance), desatAmt);
    vec3 whitened = mix(desaturated, vec3(1.0), whitenAmt);

    fragColor = vec4(whitened, 1.0);
}
