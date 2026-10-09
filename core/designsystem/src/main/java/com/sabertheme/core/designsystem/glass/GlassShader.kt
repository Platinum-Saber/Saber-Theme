package com.sabertheme.core.designsystem.glass

/**
 * One AGSL pass per glass surface, bounded to the surface's own rect:
 * rounded-rect SDF -> edge refraction into the cached blurred wallpaper,
 * tint, specular rim lit from [light], border, and a press bloom.
 *
 * Coordinates are local surface pixels. `origin` maps them into wallpaper
 * pixels; `backdropScale` maps wallpaper pixels into the blurred bitmap.
 */
internal const val GLASS_AGSL = """
uniform shader backdrop;
uniform float2 size;
uniform float radius;
uniform float2 origin;
uniform float backdropScale;
uniform float4 tint;
uniform float refraction;
uniform float2 light;
uniform float highlight;
uniform float4 rimColor;
uniform float4 borderColor;
uniform float borderWidth;
uniform float3 press;
uniform float4 bloomColor;

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float d = sdRoundBox(p, halfSize, radius);
    float coverage = clamp(0.5 - d, 0.0, 1.0);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    float2 n = float2(
        sdRoundBox(p + float2(0.5, 0.0), halfSize, radius) - sdRoundBox(p - float2(0.5, 0.0), halfSize, radius),
        sdRoundBox(p + float2(0.0, 0.5), halfSize, radius) - sdRoundBox(p - float2(0.0, 0.5), halfSize, radius));
    n = n / max(length(n), 0.0001);

    // Refraction: content near the rim is pulled inward, like a thick lens edge.
    float band = min(min(size.x, size.y) * 0.3, 28.0);
    float edge = smoothstep(-band, 0.0, d);
    float2 offset = -n * edge * edge * refraction * band;
    float3 c = backdrop.eval((origin + coord + offset) * backdropScale).rgb;
    c = mix(c, tint.rgb, tint.a);

    // Specular rim: the edge facing the light is bright, the opposite edge glints.
    float facing = max(dot(n, light), 0.0);
    float away = max(dot(n, -light), 0.0);
    float rim = smoothstep(-5.0, 0.0, d);
    float spec = highlight * (rim * (pow(facing, 2.0) + 0.35 * pow(away, 3.0)) + 0.18 * edge * facing);
    c = mix(c, rimColor.rgb, clamp(spec * rimColor.a, 0.0, 1.0));

    float border = smoothstep(-borderWidth - 0.5, -borderWidth + 0.5, d);
    c = mix(c, borderColor.rgb, border * borderColor.a);

    // Press bloom: light spreads from the finger as the press progresses.
    float reach = max(size.x, size.y) * (0.35 + 0.45 * press.z);
    float bloom = press.z * (1.0 - smoothstep(0.0, reach, length(coord - press.xy)));
    c += bloomColor.rgb * bloomColor.a * bloom;

    return half4(half3(c * coverage), half(coverage));
}
"""
