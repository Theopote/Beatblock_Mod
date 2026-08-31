package com.beatblock.automap.camera;

import com.beatblock.BeatBlock;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;

/**
 * 将 {@link CameraSubject} 解析为世界空间目标点（看向/环绕中心）。
 */
public final class CameraSubjectResolver {

	private CameraSubjectResolver() {}

	public static Vec3d resolve(CameraSubject subject) {
		if (subject == null) return Vec3d.ZERO;
		return switch (subject.kind()) {
			case STAGE_OBJECT, ANIMATED_TARGET -> resolveStageObject(subject.refId());
			case STAGE_GROUP -> resolveStageGroup(subject.refId());
			case BUILD_LAYER -> resolveBuildLayer(subject.refId());
			case WORLD_POSITION -> new Vec3d(subject.x(), subject.y(), subject.z());
			case ALL_STAGE_OBJECTS -> resolveAllStageObjectsCenter();
		};
	}

	private static Vec3d resolveStageObject(String objectId) {
		if (objectId == null || objectId.isBlank()) {
			return resolveAllStageObjectsCenter();
		}
		RuntimeStageObject object = lookupStageObject(objectId);
		return object != null ? object.getCenter() : Vec3d.ZERO;
	}

	private static Vec3d resolveStageGroup(String groupId) {
		BuildLayerManager layers = currentLayerManager();
		if (layers != null && groupId != null && !groupId.isBlank()) {
			Vec3d sum = Vec3d.ZERO;
			int count = 0;
			for (BuildLayer layer : layers.getAll()) {
				if (groupId.equals(layer.getGroupId())) {
					sum = sum.add(layer.getStageObject().getCenter());
					count++;
				}
			}
			if (count > 0) {
				return sum.multiply(1.0 / count);
			}
		}
		return resolveAllStageObjectsCenter();
	}

	private static Vec3d resolveBuildLayer(String layerId) {
		BuildLayerManager layers = currentLayerManager();
		if (layers != null && layerId != null && !layerId.isBlank()) {
			BuildLayer layer = layers.get(layerId);
			if (layer != null) {
				return layer.getStageObject().getCenter();
			}
		}
		return Vec3d.ZERO;
	}

	private static Vec3d resolveAllStageObjectsCenter() {
		StageObjectSystem system = currentStageObjectSystem();
		if (system == null) return Vec3d.ZERO;
		Collection<RuntimeStageObject> all = system.getAll();
		if (all == null || all.isEmpty()) return Vec3d.ZERO;
		Vec3d sum = Vec3d.ZERO;
		int count = 0;
		for (RuntimeStageObject object : all) {
			if (object == null) continue;
			sum = sum.add(object.getCenter());
			count++;
		}
		return count > 0 ? sum.multiply(1.0 / count) : Vec3d.ZERO;
	}

	private static RuntimeStageObject lookupStageObject(String objectId) {
		StageObjectSystem system = currentStageObjectSystem();
		if (system == null) return null;
		for (RuntimeStageObject object : system.getAll()) {
			if (object != null && objectId.equals(object.getId())) return object;
		}
		return null;
	}

	private static StageObjectSystem currentStageObjectSystem() {
		var engine = BeatBlock.getContext().blockAnimationEngine();
		return engine != null ? engine.getStageObjectSystem() : null;
	}

	private static BuildLayerManager currentLayerManager() {
		return BeatBlock.getContext().buildLayerManager();
	}
}
