package com.beatblock.automap.camera;

import com.beatblock.BeatBlock;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.runtime.BeatBlockContext;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 将 {@link CameraSubject} 解析为 {@link StageBounds}。 */
public final class CameraSubjectBoundsResolver {

	private CameraSubjectBoundsResolver() {}

	public static Optional<StageBounds> tryResolve(CameraSubject subject) {
		return tryResolve(subject, currentAnimationEngine(), currentLayerManager());
	}

	public static Optional<StageBounds> tryResolve(
		CameraSubject subject,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		if (subject == null) return Optional.empty();
		return switch (subject.kind()) {
			case STAGE_OBJECT, ANIMATED_TARGET -> boundsForStageObject(subject.refId(), engine);
			case STAGE_GROUP -> boundsForStageGroup(subject.refId(), engine, layerManager);
			case BUILD_LAYER -> boundsForBuildLayer(subject.refId(), layerManager);
			case WORLD_POSITION -> Optional.of(StageBounds.unitAt(
				new Vec3d(subject.x(), subject.y(), subject.z())));
			case ALL_STAGE_OBJECTS -> boundsForAllStageObjects(engine);
		};
	}

	public static StageBounds resolveRequired(CameraSubject subject) {
		return tryResolve(subject).orElseThrow(() ->
			new IllegalStateException("Unresolved camera subject bounds for " + subject.displayLabel()));
	}

	private static Optional<StageBounds> boundsForStageObject(
		@Nullable String objectId,
		@Nullable BlockAnimationEngine engine
	) {
		if (objectId == null || objectId.isBlank()) {
			return boundsForAllStageObjects(engine);
		}
		RuntimeStageObject object = lookupStageObject(objectId, engine);
		if (object == null) return Optional.empty();
		return Optional.of(StageBounds.fromStageObject(object));
	}

	private static Optional<StageBounds> boundsForStageGroup(
		@Nullable String groupId,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		if (layerManager != null && groupId != null && !groupId.isBlank()) {
			List<StageBounds> bounds = new ArrayList<>();
			for (BuildLayer layer : layerManager.getAll()) {
				if (layer != null && groupId.equals(layer.getGroupId())) {
					bounds.add(StageBounds.fromStageObject(layer.getStageObject()));
				}
			}
			if (!bounds.isEmpty()) {
				return Optional.of(StageBounds.union(bounds));
			}
		}
		return boundsForAllStageObjects(engine);
	}

	private static Optional<StageBounds> boundsForBuildLayer(
		@Nullable String layerId,
		@Nullable BuildLayerManager layerManager
	) {
		if (layerId == null || layerId.isBlank() || layerManager == null) {
			return Optional.empty();
		}
		BuildLayer layer = layerManager.get(layerId);
		if (layer == null) return Optional.empty();
		return Optional.of(StageBounds.fromStageObject(layer.getStageObject()));
	}

	private static Optional<StageBounds> boundsForAllStageObjects(@Nullable BlockAnimationEngine engine) {
		StageObjectSystem system = stageSystem(engine);
		if (system == null) return Optional.empty();
		Collection<RuntimeStageObject> all = system.getAll();
		if (all == null || all.isEmpty()) return Optional.empty();
		List<StageBounds> bounds = new ArrayList<>();
		for (RuntimeStageObject object : all) {
			if (object != null) bounds.add(StageBounds.fromStageObject(object));
		}
		return bounds.isEmpty() ? Optional.empty() : Optional.of(StageBounds.union(bounds));
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
}
