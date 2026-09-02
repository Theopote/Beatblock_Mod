package com.beatblock.automap.choreography;

import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编译 motif 时使用的参与者空间布局（中心坐标）。
 * <p>
 * 无运行时坐标时可用 {@link #synthetic(List, MotifAxis)} 按列表顺序生成测试/回退布局。
 */
public final class SpatialMotifLayout {

	private final Map<String, Vec3d> centers;

	public SpatialMotifLayout(Map<String, Vec3d> centers) {
		this.centers = centers != null ? Map.copyOf(centers) : Map.of();
	}

	public Map<String, Vec3d> centers() {
		return centers;
	}

	public @Nullable Vec3d centerOf(String participantId) {
		if (participantId == null || participantId.isBlank()) return null;
		return centers.get(participantId);
	}

	public static SpatialMotifLayout fromStageRoles(List<ChoreographyPlan.StageRoleAssignment> roles) {
		return SpatialMotifLayoutResolver.resolveFromStageRoles(roles, MotifAxis.X, null);
	}

	public static SpatialMotifLayout synthetic(List<String> participantIds, MotifAxis axis) {
		if (participantIds == null || participantIds.isEmpty()) {
			return new SpatialMotifLayout(Map.of());
		}
		Map<String, Vec3d> out = new LinkedHashMap<>();
		MotifAxis resolved = axis != null ? axis : MotifAxis.X;
		for (int i = 0; i < participantIds.size(); i++) {
			String id = participantIds.get(i);
			if (id == null || id.isBlank()) continue;
			double offset = i * 4.0;
			Vec3d center = switch (resolved) {
				case X -> new Vec3d(offset, 0.0, 0.0);
				case Z -> new Vec3d(0.0, 0.0, offset);
				case RADIAL -> {
					double angle = (Math.PI * 2.0 * i) / Math.max(1, participantIds.size());
					yield new Vec3d(Math.cos(angle) * 6.0, 0.0, Math.sin(angle) * 6.0);
				}
			};
			out.put(id, center);
		}
		return new SpatialMotifLayout(out);
	}
}
