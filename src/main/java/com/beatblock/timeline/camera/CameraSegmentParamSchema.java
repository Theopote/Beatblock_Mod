package com.beatblock.timeline.camera;

import com.beatblock.automap.camera.CameraSegmentSemantics;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Parameter schema for {@link CameraSegmentKind} remints (kind combo in Properties).
 * <p>
 * Policy mirrors Animation Library type replace:
 * keep shared semantic / provenance fields, drop old-kind-only geometry, fill new-kind defaults.
 */
public final class CameraSegmentParamSchema {

	private CameraSegmentParamSchema() {
	}

	/** Geometry keys owned by a segment kind (editable in Properties). */
	public static List<String> geometryKeys(CameraSegmentKind kind) {
		if (kind == null) {
			return List.of();
		}
		return switch (kind) {
			case PATH -> List.of();
			case DOLLY -> List.of(
				"startX", "startY", "startZ", "endX", "endY", "endZ", "baseYawDeg", "basePitchDeg");
			case ORBIT -> List.of(
				"targetX", "targetY", "targetZ", "radius", "height", "yawStartDeg", "yawEndDeg");
			case CRANE -> List.of(
				"startX", "startY", "startZ", "endX", "endY", "endZ", "yawDeg", "pitchDeg");
			case SHAKE -> List.of(
				"anchorX", "anchorY", "anchorZ", "yawDeg", "pitchDeg",
				"distance", "amplitude", "frequencyHz", "beatSync", "beatsPerPulse");
		};
	}

	/**
	 * Keys that survive kind remint (and segment Apply), independent of geometry kind.
	 */
	public static Set<String> sharedKeys() {
		Set<String> keys = new HashSet<>();
		keys.add("kind");
		keys.add(CameraSegmentSemantics.KEY_EASE);
		keys.add(CameraSegmentSemantics.KEY_TRANSITION);
		keys.add(CameraSegmentSemantics.KEY_COLLISION_POLICY);
		keys.add(CameraSegmentSemantics.KEY_BAKED_TARGET_X);
		keys.add(CameraSegmentSemantics.KEY_BAKED_TARGET_Y);
		keys.add(CameraSegmentSemantics.KEY_BAKED_TARGET_Z);
		keys.add(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_KIND);
		keys.add(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_REF);
		keys.add(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_X);
		keys.add(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_Y);
		keys.add(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_Z);
		keys.add(TimelineGenerationMetadataSupport.PARAM_ORIGIN);
		keys.addAll(TimelineGenerationMetadataSupport.GENERATION_IDENTITY_KEYS);
		return Set.copyOf(keys);
	}

	public static boolean isAllowed(@Nullable CameraSegmentKind kind, @Nullable String key) {
		if (key == null || key.isBlank()) {
			return false;
		}
		if (sharedKeys().contains(key)) {
			return true;
		}
		return kind != null && geometryKeys(kind).contains(key);
	}

	/**
	 * Remint parameters for a new segment kind:
	 * keep shared + new-kind geometry already present; drop old-kind-only fields;
	 * write missing new-kind defaults ({@code putIfAbsent}).
	 */
	public static Map<String, Object> remintForKind(
		@Nullable Map<String, Object> existing,
		@Nullable CameraSegmentKind newKind,
		@Nullable Map<String, Object> defaults
	) {
		CameraSegmentKind kind = newKind != null ? newKind : CameraSegmentKind.PATH;
		Map<String, Object> out = new HashMap<>();
		if (existing != null) {
			for (Map.Entry<String, Object> entry : existing.entrySet()) {
				String key = entry.getKey();
				if (isAllowed(kind, key)) {
					out.put(key, entry.getValue());
				}
			}
		}
		out.put("kind", kind.name());
		if (defaults != null) {
			for (Map.Entry<String, Object> entry : defaults.entrySet()) {
				String key = entry.getKey();
				if (key == null || key.isBlank() || "kind".equals(key)) {
					continue;
				}
				if (isAllowed(kind, key)) {
					out.putIfAbsent(key, entry.getValue());
				}
			}
		}
		return out;
	}

	/** Drop keys that are neither shared nor valid geometry for {@code kind}. */
	public static Map<String, Object> sanitizeForKind(
		@Nullable Map<String, Object> parameters,
		@Nullable CameraSegmentKind kind
	) {
		CameraSegmentKind resolved = kind != null
			? kind
			: CameraSegmentKind.fromParam(parameters != null ? parameters.get("kind") : null);
		Map<String, Object> out = new HashMap<>();
		if (parameters != null) {
			for (Map.Entry<String, Object> entry : parameters.entrySet()) {
				if (isAllowed(resolved, entry.getKey())) {
					out.put(entry.getKey(), entry.getValue());
				}
			}
		}
		out.put("kind", Objects.requireNonNullElse(resolved, CameraSegmentKind.PATH).name());
		return out;
	}
}
