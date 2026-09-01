package com.beatblock.automap.camera;

import com.beatblock.BeatBlock;
import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.influence.InfluenceFrame;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.runtime.BeatBlockContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * 将 {@link CameraSubject} 解析为世界空间目标点（看向/环绕中心）。
 * <p>
 * 解析失败时返回 {@link CameraSubjectResolveResult}，绝不静默退化到世界原点。
 */
public final class CameraSubjectResolver {

	private CameraSubjectResolver() {}

	public static CameraSubjectResolveResult resolveResult(CameraSubject subject, CameraSubjectRole role) {
		return resolveResult(subject, role, currentAnimationEngine(), currentLayerManager());
	}

	public static CameraSubjectResolveResult resolveResult(
		CameraSubject subject,
		CameraSubjectRole role,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		if (subject == null) {
			return CameraSubjectResolveResult.fail(
				ruleForRole(role),
				"Camera subject is null"
			);
		}
		return switch (subject.kind()) {
			case STAGE_OBJECT -> resolveStageObject(subject.refId(), role, engine);
			case ANIMATED_TARGET -> resolveAnimatedTarget(subject.refId(), role, engine);
			case STAGE_GROUP -> resolveStageGroup(subject.refId(), role, engine, layerManager);
			case BUILD_LAYER -> resolveBuildLayer(subject.refId(), layerManager);
			case WORLD_POSITION -> CameraSubjectResolveResult.ok(new Vec3d(subject.x(), subject.y(), subject.z()));
			case ALL_STAGE_OBJECTS -> resolveAllStageObjectsCenter(role, engine);
		};
	}

	public static Vec3d resolveRequired(CameraSubject subject, CameraSubjectRole role) {
		CameraSubjectResolveResult result = resolveResult(subject, role);
		if (!result.resolved()) {
			throw new IllegalStateException(result.detail() != null ? result.detail() : "Unresolved camera subject");
		}
		return result.position();
	}

	public static Optional<Vec3d> tryResolve(CameraSubject subject, CameraSubjectRole role) {
		CameraSubjectResolveResult result = resolveResult(subject, role);
		return result.resolved() ? Optional.of(result.position()) : Optional.empty();
	}

	private static CameraSubjectResolveResult resolveStageObject(
		String objectId,
		CameraSubjectRole role,
		@Nullable BlockAnimationEngine engine
	) {
		if (objectId == null || objectId.isBlank()) {
			return resolveAllStageObjectsCenter(role, engine);
		}
		RuntimeStageObject object = lookupStageObject(objectId, engine);
		if (object == null) {
			return CameraSubjectResolveResult.fail(
				ruleForRole(role),
				"Missing stage object \"" + objectId + "\" for camera " + roleLabel(role)
			);
		}
		return CameraSubjectResolveResult.ok(object.getCenter());
	}

	private static CameraSubjectResolveResult resolveAnimatedTarget(
		String objectId,
		CameraSubjectRole role,
		@Nullable BlockAnimationEngine engine
	) {
		if (objectId == null || objectId.isBlank()) {
			return resolveAllStageObjectsCenter(role, engine);
		}
		RuntimeStageObject object = lookupStageObject(objectId, engine);
		if (object == null) {
			return CameraSubjectResolveResult.fail(
				ruleForRole(role),
				"Missing animated target \"" + objectId + "\" for camera " + roleLabel(role)
			);
		}
		Vec3d animated = animatedCenterForObject(object, engine);
		return CameraSubjectResolveResult.ok(animated != null ? animated : object.getCenter());
	}

	private static @Nullable Vec3d animatedCenterForObject(
		RuntimeStageObject object,
		@Nullable BlockAnimationEngine engine
	) {
		if (engine == null) return null;
		InfluenceFrame frame = engine.getLastInfluenceFrame();
		if (frame == null) return null;
		Vec3d sum = Vec3d.ZERO;
		int count = 0;
		for (BlockPos pos : object.getBlocks()) {
			AnimatedBlock animated = frame.getAnimatedBlocks().get(pos);
			if (animated != null) {
				sum = sum.add(animated.getPosition());
				count++;
			}
		}
		return count > 0 ? sum.multiply(1.0 / count) : null;
	}

	private static CameraSubjectResolveResult resolveStageGroup(
		String groupId,
		CameraSubjectRole role,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		if (layerManager != null && groupId != null && !groupId.isBlank()) {
			Vec3d sum = Vec3d.ZERO;
			int count = 0;
			for (BuildLayer layer : layerManager.getAll()) {
				if (layer != null && groupId.equals(layer.getGroupId())) {
					sum = sum.add(layer.getStageObject().getCenter());
					count++;
				}
			}
			if (count > 0) {
				return CameraSubjectResolveResult.ok(sum.multiply(1.0 / count));
			}
		}
		return resolveAllStageObjectsCenter(role, engine);
	}

	private static CameraSubjectResolveResult resolveBuildLayer(
		String layerId,
		@Nullable BuildLayerManager layerManager
	) {
		if (layerId == null || layerId.isBlank()) {
			return CameraSubjectResolveResult.fail(
				CameraValidationRules.MISSING_CAMERA_BUILD_LAYER,
				"Camera build layer reference is blank"
			);
		}
		if (layerManager == null) {
			return CameraSubjectResolveResult.fail(
				CameraValidationRules.MISSING_CAMERA_BUILD_LAYER,
				"Build layer manager unavailable for camera build layer \"" + layerId + "\""
			);
		}
		BuildLayer layer = layerManager.get(layerId);
		if (layer == null) {
			return CameraSubjectResolveResult.fail(
				CameraValidationRules.MISSING_CAMERA_BUILD_LAYER,
				"Missing build layer \"" + layerId + "\" for camera subject"
			);
		}
		return CameraSubjectResolveResult.ok(layer.getStageObject().getCenter());
	}

	private static CameraSubjectResolveResult resolveAllStageObjectsCenter(
		CameraSubjectRole role,
		@Nullable BlockAnimationEngine engine
	) {
		StageObjectSystem system = stageSystem(engine);
		if (system == null) {
			return CameraSubjectResolveResult.fail(
				ruleForRole(role),
				"Stage object system unavailable for camera " + roleLabel(role)
			);
		}
		Collection<RuntimeStageObject> all = system.getAll();
		if (all == null || all.isEmpty()) {
			return CameraSubjectResolveResult.fail(
				ruleForRole(role),
				"No stage objects available for camera " + roleLabel(role)
			);
		}
		Vec3d sum = Vec3d.ZERO;
		int count = 0;
		for (RuntimeStageObject object : all) {
			if (object == null) continue;
			sum = sum.add(object.getCenter());
			count++;
		}
		if (count == 0) {
			return CameraSubjectResolveResult.fail(
				ruleForRole(role),
				"No stage objects available for camera " + roleLabel(role)
			);
		}
		return CameraSubjectResolveResult.ok(sum.multiply(1.0 / count));
	}

	private static @Nullable RuntimeStageObject lookupStageObject(
		String objectId,
		@Nullable BlockAnimationEngine engine
	) {
		StageObjectSystem system = stageSystem(engine);
		return system != null ? system.get(objectId) : null;
	}

	private static @Nullable StageObjectSystem stageSystem(@Nullable BlockAnimationEngine engine) {
		if (engine != null) {
			return engine.getStageObjectSystem();
		}
		BeatBlockContext ctx = tryContext();
		if (ctx == null) return null;
		BlockAnimationEngine ctxEngine = ctx.blockAnimationEngine();
		return ctxEngine != null ? ctxEngine.getStageObjectSystem() : null;
	}

	private static @Nullable BeatBlockContext tryContext() {
		try {
			return BeatBlock.getContext();
		} catch (IllegalStateException ex) {
			return null;
		}
	}

	private static @Nullable BuildLayerManager currentLayerManager() {
		BeatBlockContext ctx = tryContext();
		return ctx != null ? ctx.buildLayerManager() : null;
	}

	private static @Nullable BlockAnimationEngine currentAnimationEngine() {
		BeatBlockContext ctx = tryContext();
		return ctx != null ? ctx.blockAnimationEngine() : null;
	}

	private static String ruleForRole(CameraSubjectRole role) {
		return role == CameraSubjectRole.LOOK_AT
			? CameraValidationRules.MISSING_CAMERA_LOOK_AT
			: CameraValidationRules.MISSING_CAMERA_SUBJECT;
	}

	private static String roleLabel(CameraSubjectRole role) {
		return role == CameraSubjectRole.LOOK_AT ? "look-at" : "subject";
	}
}
