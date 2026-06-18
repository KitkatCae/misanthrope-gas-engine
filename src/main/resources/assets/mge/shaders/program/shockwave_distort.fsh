#version 150
/*
 * MGE shockwave — per-wave refraction + rim-glow pass.
 *
 * Rendered once per active wave (see ShockwavePostProcessor.java), additively
 * blended onto the main color target (blend func: add, srcrgb: one, dstrgb: one
 * — see shockwave_distort.json). Outside the wave's shell band this shader
 * outputs vec4(0) so it contributes nothing, letting any number of waves
 * stack correctly without a fixed-size array in THIS pass.
 *
 * Desaturation/whitening is intentionally NOT done here — additive blending
 * can only add light, never subtract saturation from what's already on
 * screen. That's handled by a separate combined pass afterward
 * (shockwave_desaturate.fsh) that reads the accumulated color and desaturates
 * near any active shell, across all waves at once.
 */

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

// Real camera matrices, NOT the fullscreen-quad ortho ProjMat the vertex
// shader uses — uploaded fresh every frame by ShockwavePostProcessor from
// RenderSystem.getProjectionMatrix() / getModelViewMatrix().
uniform mat4 InvViewProjMat;   // unprojects NDC -> camera-relative world space
uniform mat4 RealViewProjMat;  // projects camera-relative world space -> NDC

uniform vec2 OutSize;
uniform float Time; // 0..1, loops every real-world second (engine-provided)

// Wave state — all positions are CAMERA-RELATIVE (worldPos - cameraPos),
// uploaded that way specifically to avoid float precision loss at large
// world coordinates. See ShockwavePostProcessor.uploadWaveUniforms.
uniform vec3  WaveOriginRel;
uniform float WaveRadius;
uniform float WaveStrength;
uniform float WaveTempC;
uniform vec4  WaveBuckets; // [fine, heavy, ash, exotic] mg/m3, raw (not normalized)

in vec2 texCoord;
out vec4 fragColor;

// ── Tunables ────────────────────────────────────────────────────────────
const float SHELL_THICKNESS      = 1.6;   // blocks; half-width of the visible band
const float MAX_REFRACTION_UV    = 0.02;  // max screen-space UV offset at full strength
const float BUCKET_NORMALIZE_MAX = 400.0; // mg/m3 treated as "fully saturated" per bucket
const float RIM_TEMP_THRESHOLD_C = 150.0; // ambient°C above which thermal rim starts to show
const float RIM_TEMP_FULL_C      = 900.0; // °C at which thermal rim is fully bright
const vec3  RIM_HOT_COLOR        = vec3(1.0, 0.55, 0.18);
const vec3  RIM_EXOTIC_COLOR     = vec3(0.85, 0.65, 1.0); // warm violet-white, energetic/glowing

// Reconstructs the camera-relative world-space position of this fragment
// from its depth-buffer value.
vec3 reconstructWorldPos(vec2 uv, float depth) {
    vec4 ndc = vec4(uv.x * 2.0 - 1.0, uv.y * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 worldPos = InvViewProjMat * ndc;
    return worldPos.xyz / worldPos.w;
}

// Projects a camera-relative world-space point to screen-space UV (0..1).
vec2 projectToUV(vec3 worldPosRel) {
    vec4 clip = RealViewProjMat * vec4(worldPosRel, 1.0);
    vec2 ndc = clip.xy / clip.w;
    return ndc * 0.5 + 0.5;
}

void main() {
    float depth = texture(DiffuseDepthSampler, texCoord).r;

    // Skip the far-plane / sky — nothing to distort against.
    if (depth >= 0.9999) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 worldPosRel = reconstructWorldPos(texCoord, depth);
    float dist = length(worldPosRel - WaveOriginRel);
    float delta = dist - WaveRadius;
    float absDelta = abs(delta);

    if (absDelta > SHELL_THICKNESS) {
        fragColor = vec4(0.0);
        return;
    }

    // Bump profile: 1.0 exactly at the shell, falling to 0 at the band edges.
    float t = 1.0 - smoothstep(0.0, SHELL_THICKNESS, absDelta);

    // Radial direction is the refraction "normal" — push the sample outward
    // for the leading (outer) half of the shell, inward for the trailing
    // half, mimicking how a real density-discontinuity bends light passing
    // through it in both directions.
    vec3 radialDir = normalize(worldPosRel - WaveOriginRel);
    float pushSign = sign(delta == 0.0 ? 1.0 : delta);

    // A tiny shimmer: offset the sample point slightly over time so the
    // distortion isn't perfectly static-looking on a slow-moving wave.
    float shimmer = 1.0 + 0.08 * sin(Time * 62.83 + dist * 4.0);

    vec3 sampleWorldPos = worldPosRel + radialDir * (0.35 * pushSign * shimmer);
    vec2 sampleUV = projectToUV(sampleWorldPos);
    vec2 uvOffset = (sampleUV - texCoord) * t * WaveStrength;
    uvOffset = clamp(uvOffset, -MAX_REFRACTION_UV, MAX_REFRACTION_UV);

    vec2 finalUV = clamp(texCoord + uvOffset, vec2(0.0), vec2(1.0));
    vec3 refracted = texture(DiffuseSampler, finalUV).rgb;
    vec3 original  = texture(DiffuseSampler, texCoord).rgb;

    // The additive contribution is the DIFFERENCE the refraction makes, not
    // the full refracted color — otherwise we'd double the screen's
    // brightness everywhere the shell passes, since blending is additive.
    vec3 refractionDelta = (refracted - original) * t;

    // ── Rim glow ────────────────────────────────────────────────────────
    float thermalT = clamp((WaveTempC - RIM_TEMP_THRESHOLD_C)
                          / (RIM_TEMP_FULL_C - RIM_TEMP_THRESHOLD_C), 0.0, 1.0);
    float exoticAmt = clamp(WaveBuckets.w / BUCKET_NORMALIZE_MAX, 0.0, 1.0);

    vec3 rim = vec3(0.0);
    rim += RIM_HOT_COLOR    * thermalT * 0.6;
    rim += RIM_EXOTIC_COLOR * exoticAmt * 0.5;
    rim *= t * t; // concentrate glow tightly at the shell, fall off faster than the distortion itself

    vec3 result = refractionDelta + rim;
    fragColor = vec4(result, 1.0);
}
