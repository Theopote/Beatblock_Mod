package com.beatblock.automap.choreography;

import com.beatblock.BeatBlock;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.runtime.BeatBlockContext;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 {@link BlockAnimationEngine} 解析空间 motif 参与者中心；缺失对象回退到 {@link SpatialMotifLayout#synthetic}。
 */
public final class SpatialMotifLayoutResolver {

	private SpatialMotifLayoutResolver() {}

	public static SpatialMotifLayout resolve(
		List<String> participantIds,
		MotifAxis axis,
		@Nullable BlockAnimationEngine engine
	) {
		if (participantIds == null || participantIds.isEmpty()) {
			return new SpatialMotifLayout(Map.of());
		}
		StageObjectSystem system = stageSystem(engine);
		Map<String, Vec3d> centers = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (String participantId : participantIds) {
			if (participantId == null || participantId.isBlank()) continue;
			RuntimeStageObject object = system != null ? system.get(participantId) : null;
			if (object != null) {
				centers.put(participantId, object.getCenter());
			} else {
				missing.add(participantId);
			}
		}
		if (!missing.isEmpty()) {
			centers.putAll(SpatialMotifLayout.synthetic(missing, axis).centers());
		}
		return new SpatialMotifLayout(centers);
	}

	public static SpatialMotifLayout resolveFromStageRoles(
		List<ChoreographyPlan.StageRoleAssignment> roles,
		MotifAxis axis,
		@Nullable BlockAnimationEngine engine
	) {
		return resolve(uniqueParticipantIds(roles), axis, engine);
	}

	public static SpatialMotifLayout resolveFromContext(
		List<String> participantIds,
		MotifAxis axis
	) {
		return resolve(participantIds, axis, currentAnimationEngine());
	}

	public static @Nullable BlockAnimationEngine currentAnimationEngine() {
		BeatBlockContext context = tryContext();
		return context != null ? context.blockAnimationEngine() : null;
	}

	private static List<String> uniqueParticipantIds(List<ChoreographyPlan.StageRoleAssignment> roles) {
		if (roles == null || roles.isEmpty()) return List.of();
		Set<String> ids = new LinkedHashSet<>();
		for (ChoreographyPlan.StageRoleAssignment role : roles) {
			if (role == null || role.targetObjectId() == null || role.targetObjectId().isBlank()) continue;
			ids.add(role.targetObjectId());
		}
		return new ArrayList<>(ids);
	}

	private static @Nullable StageObjectSystem stageSystem(@Nullable BlockAnimationEngine engine) {
		if (engine != null) {
			return engine.getStageObjectSystem();
		}
		BlockAnimationEngine contextEngine = currentAnimationEngine();
		return contextEngine != null ? contextEngine.getStageObjectSystem() : null;
	}

	private static @Nullable BeatBlockContext tryContext() {
		try {
			return BeatBlock.getContext();
		} catch (IllegalStateException ex) {
			return null;
		}
	}
}
