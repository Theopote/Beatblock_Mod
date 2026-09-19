package com.beatblock.automap.cast;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 舞台卡司：所有 StageObject 的角色表，供 Animation / Camera / VFX 共享。
 */
public record StageCast(List<StageCastAssignment> assignments) {

	public StageCast {
		assignments = assignments != null ? List.copyOf(assignments) : List.of();
	}

	public static StageCast empty() {
		return new StageCast(List.of());
	}

	/**
	 * 从目标对象列表自动分配角色：
	 * <pre>
	 * 0 → HERO (low)
	 * 1 → LEAD (mid)
	 * 2 → SUPPORT (high)
	 * 3 → ACCENT
	 * 4+ → BACKGROUND
	 * </pre>
	 */
	public static StageCast fromTargetIds(@Nullable List<String> targetObjectIds) {
		if (targetObjectIds == null || targetObjectIds.isEmpty()) {
			return empty();
		}
		List<StageCastAssignment> out = new ArrayList<>();
		int index = 0;
		for (String raw : targetObjectIds) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String id = raw.trim();
			StageRole role = switch (index) {
				case 0 -> StageRole.HERO;
				case 1 -> StageRole.LEAD;
				case 2 -> StageRole.SUPPORT;
				case 3 -> StageRole.ACCENT;
				default -> StageRole.BACKGROUND;
			};
			String[] features = switch (index) {
				case 0 -> new String[] {"low"};
				case 1 -> new String[] {"mid"};
				case 2 -> new String[] {"high"};
				default -> new String[0];
			};
			out.add(StageCastAssignment.of(id, role, index / 3, features));
			index++;
		}
		return new StageCast(out);
	}

	public boolean isEmpty() {
		return assignments.isEmpty();
	}

	public List<String> allObjectIds() {
		List<String> ids = new ArrayList<>(assignments.size());
		for (StageCastAssignment assignment : assignments) {
			if (assignment.isValid()) {
				ids.add(assignment.objectId());
			}
		}
		return List.copyOf(ids);
	}

	public @Nullable String primaryHeroId() {
		return firstIdWithRole(StageRole.HERO);
	}

	public @Nullable String firstIdWithRole(StageRole role) {
		for (StageCastAssignment assignment : assignments) {
			if (assignment.isValid() && assignment.role() == role) {
				return assignment.objectId();
			}
		}
		return null;
	}

	public List<String> idsWithRole(StageRole role) {
		List<String> ids = new ArrayList<>();
		for (StageCastAssignment assignment : assignments) {
			if (assignment.isValid() && assignment.role() == role) {
				ids.add(assignment.objectId());
			}
		}
		return List.copyOf(ids);
	}

	/** Phrase/Hero 参与者：HERO + LEAD + SUPPORT + ACCENT（排除 BACKGROUND）。 */
	public List<String> choreographyParticipantIds() {
		List<String> ids = new ArrayList<>();
		for (StageCastAssignment assignment : assignments) {
			if (assignment.isValid() && assignment.role().isFocusable()) {
				ids.add(assignment.objectId());
			}
		}
		if (ids.isEmpty()) {
			return allObjectIds();
		}
		return List.copyOf(ids);
	}

	/** Accent 频段兼容映射：feature → objectId（每特征取最高 prominence）。 */
	public Map<String, String> featureTargetMap() {
		Map<String, String> out = new LinkedHashMap<>();
		Map<String, Float> best = new LinkedHashMap<>();
		for (StageCastAssignment assignment : assignments) {
			if (!assignment.isValid()) {
				continue;
			}
			for (String feature : assignment.preferredFeatures()) {
				float prev = best.getOrDefault(feature, -1f);
				if (assignment.prominence() > prev) {
					best.put(feature, assignment.prominence());
					out.put(feature, assignment.objectId());
				}
			}
		}
		return Map.copyOf(out);
	}

	public @Nullable StageRole roleOf(String objectId) {
		if (objectId == null || objectId.isBlank()) {
			return null;
		}
		for (StageCastAssignment assignment : assignments) {
			if (objectId.equals(assignment.objectId())) {
				return assignment.role();
			}
		}
		return null;
	}
}
