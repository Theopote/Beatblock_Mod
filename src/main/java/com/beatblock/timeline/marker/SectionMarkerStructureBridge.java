package com.beatblock.timeline.marker;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanEditor;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.SectionPlanSource;
import com.beatblock.timeline.MarkerEditPolicy;
import com.beatblock.timeline.MarkerSemanticService;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import org.jspecify.annotations.Nullable;

/**
 * SECTION Marker ↔ {@link ChoreographyPlan} 防漂移桥接（非重构，非双向 SoT）。
 * <p>
 * <b>长期目标：</b> Music Structure ({@link ChoreographyPlan#sections()}) 为本体，
 * SECTION Marker 仅为 binding / 导航投影。
 * <p>
 * <b>当前约定：</b>
 * <ul>
 *   <li>不把 Marker 编辑当成结构的唯一真相</li>
 *   <li>SECTION Marker 的时间/名称提交后，尽量投影回 Plan 对应段落（标 USER_EDITED）</li>
 *   <li>Plan 段落边界拖动后，尽量投影到邻近 SECTION Marker</li>
 *   <li>删除 SECTION Marker <b>不</b>删除 Plan 段落（结构 SoT 仍在 Plan）</li>
 * </ul>
 */
public final class SectionMarkerStructureBridge {

	/** 与 {@link MarkerAnalysisMerger#PROXIMITY_SECONDS} 对齐，便于匹配同一段落。 */
	public static final double MATCH_PROXIMITY_SECONDS = MarkerAnalysisMerger.PROXIMITY_SECONDS;

	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private SectionMarkerStructureBridge() {
	}

	/**
	 * Marker 提交后：用 {@code matchTimeSeconds} 找到 Plan 段落，投影 {@code current} 的时间/标签。
	 *
	 * @param matchTimeSeconds 用于定位段落的时间（通常为编辑前时间；新建可用当前时间）
	 */
	public static boolean projectMarkerOntoPlan(
		@Nullable Timeline timeline,
		double matchTimeSeconds,
		@Nullable TimelineMarker current
	) {
		if (timeline == null || current == null || !current.getType().isStructural()) {
			return false;
		}
		if (Boolean.TRUE.equals(REENTRANT.get())) {
			return false;
		}
		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		if (plan == null || plan.sections().isEmpty()) {
			return false;
		}
		int index = findNearestSectionIndex(plan, matchTimeSeconds);
		if (index < 0) {
			return false;
		}
		ChoreographyPlan.SectionPlan section = plan.sections().get(index);
		if (section.source() == SectionPlanSource.LOCKED) {
			return false;
		}

		REENTRANT.set(true);
		try {
			ChoreographyPlan updated = applySectionStartAndLabel(
				plan, index, current.getTimeSeconds(), extractSectionLabel(current.getName()));
			if (updated == plan) {
				return false;
			}
			ChoreographyPlanStore.save(timeline, updated, ChoreographyPlanStore.loadConfig(timeline));
			return true;
		} finally {
			REENTRANT.set(false);
		}
	}

	/**
	 * Plan 边界拖动提交后：把邻近 SECTION Marker 投影到新起点。
	 */
	public static boolean projectPlanBoundaryOntoMarkers(
		@Nullable Timeline timeline,
		double previousStartSeconds,
		double newStartSeconds
	) {
		if (timeline == null || Math.abs(previousStartSeconds - newStartSeconds) <= 1e-9) {
			return false;
		}
		if (Boolean.TRUE.equals(REENTRANT.get())) {
			return false;
		}
		TimelineMarker match = findNearestSectionMarker(timeline, previousStartSeconds);
		if (match == null || MarkerEditPolicy.isLocked(match)) {
			return false;
		}
		REENTRANT.set(true);
		try {
			TimelineMarker updated = match.withTimeSeconds(newStartSeconds, true);
			return timeline.replaceMarker(updated);
		} finally {
			REENTRANT.set(false);
		}
	}

	static int findNearestSectionIndex(ChoreographyPlan plan, double timeSeconds) {
		int best = -1;
		double bestDist = MATCH_PROXIMITY_SECONDS;
		for (int i = 0; i < plan.sections().size(); i++) {
			double dist = Math.abs(plan.sections().get(i).startSeconds() - timeSeconds);
			if (dist <= bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	static @Nullable TimelineMarker findNearestSectionMarker(Timeline timeline, double timeSeconds) {
		TimelineMarker best = null;
		double bestDist = MATCH_PROXIMITY_SECONDS;
		for (TimelineMarker marker : timeline.getMarkers()) {
			if (marker == null || !marker.getType().isStructural()) {
				continue;
			}
			double dist = Math.abs(marker.getTimeSeconds() - timeSeconds);
			if (dist <= bestDist) {
				bestDist = dist;
				best = marker;
			}
		}
		return best;
	}

	private static ChoreographyPlan applySectionStartAndLabel(
		ChoreographyPlan plan,
		int sectionIndex,
		double newStartSeconds,
		String label
	) {
		ChoreographyPlan.SectionPlan section = plan.sections().get(sectionIndex);
		boolean timeChanged = Math.abs(section.startSeconds() - newStartSeconds) > 1e-6;
		boolean labelChanged = label != null && !label.isBlank()
			&& !label.equalsIgnoreCase(section.label());

		ChoreographyPlan updated = plan;
		if (timeChanged) {
			updated = ChoreographyPlanEditor.setSectionStartSeconds(updated, sectionIndex, newStartSeconds);
		}
		if (labelChanged && sectionIndex < updated.sections().size()) {
			ChoreographyPlan.SectionPlan current = updated.sections().get(sectionIndex);
			updated = ChoreographyPlanEditor.updateSection(
				updated,
				sectionIndex,
				current.sectionType(),
				label,
				current.source() == SectionPlanSource.LOCKED
			);
		}
		return updated;
	}

	static String extractSectionLabel(@Nullable String markerName) {
		return MarkerSemanticService.extractSectionLabel(markerName);
	}
}
