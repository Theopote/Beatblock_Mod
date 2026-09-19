package com.beatblock.automap.cast;

import java.util.List;
import java.util.Set;

/**
 * 单个舞台对象的角色分配。
 */
public record StageCastAssignment(
	String objectId,
	StageRole role,
	Set<String> preferredFeatures,
	float prominence,
	int groupIndex
) {
	public StageCastAssignment {
		objectId = objectId != null ? objectId.trim() : "";
		role = role != null ? role : StageRole.BACKGROUND;
		preferredFeatures = preferredFeatures != null ? Set.copyOf(preferredFeatures) : Set.of();
		prominence = Math.max(0f, Math.min(1f, prominence));
		groupIndex = Math.max(0, groupIndex);
	}

	public boolean isValid() {
		return !objectId.isBlank();
	}

	public static StageCastAssignment of(String objectId, StageRole role, int groupIndex, String... features) {
		return new StageCastAssignment(
			objectId,
			role,
			features != null && features.length > 0 ? Set.of(features) : Set.of(),
			(float) (role != null ? role.defaultProminence() : StageRole.BACKGROUND.defaultProminence()),
			groupIndex
		);
	}
}
