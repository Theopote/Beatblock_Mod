package com.beatblock.automap.camera;

import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@link CameraShot} 语义字段 ↔ Timeline 摄像机片段参数。
 * <p>
 * 写入后由 {@link com.beatblock.client.camera.TimelineCameraEvaluator} 在播放时消费。
 */
public final class CameraSegmentSemantics {

	public static final String KEY_EASE = "ease";
	public static final String KEY_TRANSITION = "transition";
	public static final String KEY_COLLISION_POLICY = "collisionPolicy";
	public static final String KEY_BAKED_TARGET_X = "bakedTargetX";
	public static final String KEY_BAKED_TARGET_Y = "bakedTargetY";
	public static final String KEY_BAKED_TARGET_Z = "bakedTargetZ";
	public static final String KEY_FOLLOW_SUBJECT_KIND = "followSubjectKind";
	public static final String KEY_FOLLOW_SUBJECT_REF = "followSubjectRef";
	public static final String KEY_FOLLOW_SUBJECT_X = "followSubjectX";
	public static final String KEY_FOLLOW_SUBJECT_Y = "followSubjectY";
	public static final String KEY_FOLLOW_SUBJECT_Z = "followSubjectZ";

	private CameraSegmentSemantics() {}

	public static Map<String, Object> fromShot(CameraShot shot) {
		Map<String, Object> params = new HashMap<>();
		if (shot == null) return params;
		params.put(KEY_EASE, shot.easing().name());
		params.put(KEY_TRANSITION, shot.transition().name());
		params.put(KEY_COLLISION_POLICY, shot.collisionPolicy().name());
		writeFollowSubject(params, shot.effectiveLookAt());
		return params;
	}

	public static void mergeInto(Map<String, Object> segmentParams, CameraShot shot) {
		if (segmentParams == null || shot == null) return;
		segmentParams.putAll(fromShot(shot));
	}

	public static void mergeInto(Map<String, Object> segmentParams, @Nullable Map<String, Object> semantics) {
		if (segmentParams == null || semantics == null || semantics.isEmpty()) return;
		segmentParams.putAll(semantics);
	}

	public static CameraShotEasing easingFrom(@Nullable Map<String, Object> params) {
		return parseEnum(stringParam(params, KEY_EASE, "SMOOTH"), CameraShotEasing.class, CameraShotEasing.SMOOTH);
	}

	public static CameraShotTransition transitionFrom(@Nullable Map<String, Object> params) {
		return parseEnum(stringParam(params, KEY_TRANSITION, "CUT"), CameraShotTransition.class, CameraShotTransition.CUT);
	}

	public static CameraCollisionPolicy collisionPolicyFrom(@Nullable Map<String, Object> params) {
		return parseEnum(
			stringParam(params, KEY_COLLISION_POLICY, "AVOID_BLOCKS"),
			CameraCollisionPolicy.class,
			CameraCollisionPolicy.AVOID_BLOCKS
		);
	}

	public static @Nullable CameraSubject followSubjectFrom(@Nullable Map<String, Object> params) {
		if (params == null || !params.containsKey(KEY_FOLLOW_SUBJECT_KIND)) return null;
		String kindName = stringParam(params, KEY_FOLLOW_SUBJECT_KIND, "");
		if (kindName.isBlank()) return null;
		try {
			CameraSubjectKind kind = CameraSubjectKind.valueOf(kindName);
			return switch (kind) {
				case STAGE_OBJECT -> CameraSubject.stageObject(stringParam(params, KEY_FOLLOW_SUBJECT_REF, ""));
				case STAGE_GROUP -> CameraSubject.stageGroup(stringParam(params, KEY_FOLLOW_SUBJECT_REF, ""));
				case BUILD_LAYER -> CameraSubject.buildLayer(stringParam(params, KEY_FOLLOW_SUBJECT_REF, ""));
				case ANIMATED_TARGET -> CameraSubject.animatedTarget(stringParam(params, KEY_FOLLOW_SUBJECT_REF, ""));
				case WORLD_POSITION -> CameraSubject.worldPosition(
					number(params, KEY_FOLLOW_SUBJECT_X, 0.0),
					number(params, KEY_FOLLOW_SUBJECT_Y, 64.0),
					number(params, KEY_FOLLOW_SUBJECT_Z, 0.0)
				);
				case ALL_STAGE_OBJECTS -> CameraSubject.allStageObjects();
			};
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	public static Vec3d bakedTarget(@Nullable Map<String, Object> params) {
		if (params == null) return Vec3d.ZERO;
		double x = number(params, KEY_BAKED_TARGET_X, number(params, "targetX", 0.0));
		double y = number(params, KEY_BAKED_TARGET_Y, number(params, "targetY", 0.0));
		double z = number(params, KEY_BAKED_TARGET_Z, number(params, "targetZ", 0.0));
		return new Vec3d(x, y, z);
	}

	public static Optional<Vec3d> followDelta(@Nullable Map<String, Object> params) {
		CameraSubject follow = followSubjectFrom(params);
		if (follow == null) return Optional.empty();
		CameraSubjectResolveResult live = CameraSubjectResolver.resolveResult(follow, CameraSubjectRole.LOOK_AT);
		if (!live.resolved() || live.position() == null) return Optional.empty();
		Vec3d baked = bakedTarget(params);
		return Optional.of(live.position().subtract(baked));
	}

	public static Map<String, Object> withFollowDeltaApplied(@Nullable Map<String, Object> params) {
		if (params == null) return Map.of();
		return applySpatialDelta(params, followDelta(params).orElse(Vec3d.ZERO));
	}

	private static Map<String, Object> applySpatialDelta(Map<String, Object> params, Vec3d delta) {
		if (delta.lengthSquared() < 1e-12) return params;
		Map<String, Object> out = new HashMap<>(params);
		applyTripleDelta(out, delta, "targetX", "targetY", "targetZ");
		applyTripleDelta(out, delta, "startX", "startY", "startZ");
		applyTripleDelta(out, delta, "endX", "endY", "endZ");
		applyTripleDelta(out, delta, "anchorX", "anchorY", "anchorZ");
		return out;
	}

	private static void applyTripleDelta(
		Map<String, Object> params,
		Vec3d delta,
		String xKey,
		String yKey,
		String zKey
	) {
		if (!params.containsKey(xKey)) return;
		params.put(xKey, number(params, xKey, 0.0) + delta.x);
		params.put(yKey, number(params, yKey, 0.0) + delta.y);
		params.put(zKey, number(params, zKey, 0.0) + delta.z);
	}

	private static void writeFollowSubject(Map<String, Object> params, CameraSubject subject) {
		if (params == null || subject == null) return;
		CameraSubjectResolveResult baked = CameraSubjectResolver.resolveResult(subject, CameraSubjectRole.LOOK_AT);
		if (!baked.resolved() || baked.position() == null) return;
		Vec3d position = baked.position();
		params.put(KEY_BAKED_TARGET_X, position.x);
		params.put(KEY_BAKED_TARGET_Y, position.y);
		params.put(KEY_BAKED_TARGET_Z, position.z);
		params.put(KEY_FOLLOW_SUBJECT_KIND, subject.kind().name());
		if (!subject.refId().isBlank()) {
			params.put(KEY_FOLLOW_SUBJECT_REF, subject.refId());
		}
		if (subject.kind() == CameraSubjectKind.WORLD_POSITION) {
			params.put(KEY_FOLLOW_SUBJECT_X, subject.x());
			params.put(KEY_FOLLOW_SUBJECT_Y, subject.y());
			params.put(KEY_FOLLOW_SUBJECT_Z, subject.z());
		}
	}

	private static String stringParam(@Nullable Map<String, Object> params, String key, String fallback) {
		if (params == null) return fallback;
		Object raw = params.get(key);
		if (raw == null) return fallback;
		String value = String.valueOf(raw).trim();
		return value.isEmpty() ? fallback : value;
	}

	private static double number(@Nullable Map<String, Object> params, String key, double fallback) {
		if (params == null) return fallback;
		Object raw = params.get(key);
		if (raw instanceof Number number) return number.doubleValue();
		if (raw != null) {
			try {
				return Double.parseDouble(String.valueOf(raw).trim());
			} catch (NumberFormatException ignored) {
				return fallback;
			}
		}
		return fallback;
	}

	private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E fallback) {
		if (raw == null || raw.isBlank()) return fallback;
		try {
			return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return fallback;
		}
	}
}
