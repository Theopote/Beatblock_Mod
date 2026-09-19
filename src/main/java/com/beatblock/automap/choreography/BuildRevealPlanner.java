package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;
import com.beatblock.engine.BuildSequenceMode;
import com.beatblock.timeline.generation.PacingMode;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * BUILD_REVEAL：从段落结构推导 {@link BuildSequencePlan}，不生成 Pulse/Wave Phrase。
 */
public final class BuildRevealPlanner {

	private static final double FALLBACK_DURATION_SECONDS = 8.0;
	private static final double MIN_DURATION_SECONDS = 1.0;
	private static final double DROP_LEAD_IN_SECONDS = 8.0;

	private BuildRevealPlanner() {}

	/**
	 * @param plan           已含 sections 的编舞计划
	 * @param layerId        BuildLayer id（必填）
	 * @param targetObjectId 图层关联的 StageObject id
	 * @return 无效输入时返回 null
	 */
	public static @Nullable BuildSequencePlan plan(
		@Nullable ChoreographyPlan plan,
		@Nullable String layerId,
		@Nullable String targetObjectId
	) {
		if (plan == null || layerId == null || layerId.isBlank()) {
			return null;
		}
		String objectId = targetObjectId != null ? targetObjectId.trim() : "";
		Window window = resolveWindow(plan.sections());
		return new BuildSequencePlan(
			layerId.trim(),
			objectId,
			window.startSeconds,
			window.endSeconds,
			BuildSequenceMode.WALL,
			PacingMode.FIXED_INTERVAL,
			false,
			null,
			window.sectionIndex
		);
	}

	static Window resolveWindow(List<ChoreographyPlan.SectionPlan> sections) {
		List<ChoreographyPlan.SectionPlan> safe = sections != null ? sections : List.of();
		int buildIndex = indexOfType(safe, SectionType.BUILD);
		if (buildIndex >= 0) {
			ChoreographyPlan.SectionPlan build = safe.get(buildIndex);
			double end = build.endSeconds();
			int dropIndex = indexOfType(safe, SectionType.DROP);
			if (dropIndex > buildIndex) {
				end = Math.max(end, safe.get(dropIndex).startSeconds());
			}
			end = Math.max(build.startSeconds() + MIN_DURATION_SECONDS, end);
			return new Window(build.startSeconds(), end, buildIndex);
		}

		int dropIndex = indexOfType(safe, SectionType.DROP);
		if (dropIndex >= 0) {
			ChoreographyPlan.SectionPlan drop = safe.get(dropIndex);
			double start = Math.max(0.0, drop.startSeconds() - DROP_LEAD_IN_SECONDS);
			double end = Math.max(start + MIN_DURATION_SECONDS, drop.startSeconds());
			if (end - start < MIN_DURATION_SECONDS) {
				end = start + FALLBACK_DURATION_SECONDS;
			}
			return new Window(start, end, dropIndex);
		}

		int chorusIndex = indexOfType(safe, SectionType.CHORUS);
		if (chorusIndex >= 0) {
			ChoreographyPlan.SectionPlan chorus = safe.get(chorusIndex);
			double end = Math.max(chorus.startSeconds() + MIN_DURATION_SECONDS, chorus.endSeconds());
			return new Window(chorus.startSeconds(), end, chorusIndex);
		}

		double timelineEnd = maxEnd(safe);
		if (timelineEnd <= 0.0) {
			return new Window(0.0, FALLBACK_DURATION_SECONDS, -1);
		}
		double duration = Math.min(FALLBACK_DURATION_SECONDS, timelineEnd);
		return new Window(0.0, Math.max(MIN_DURATION_SECONDS, duration), -1);
	}

	private static int indexOfType(List<ChoreographyPlan.SectionPlan> sections, SectionType type) {
		for (int i = 0; i < sections.size(); i++) {
			ChoreographyPlan.SectionPlan section = sections.get(i);
			if (section != null && section.sectionType() == type) {
				return i;
			}
		}
		return -1;
	}

	private static double maxEnd(List<ChoreographyPlan.SectionPlan> sections) {
		double max = 0.0;
		for (ChoreographyPlan.SectionPlan section : sections) {
			if (section != null) {
				max = Math.max(max, section.endSeconds());
			}
		}
		return max;
	}

	record Window(double startSeconds, double endSeconds, int sectionIndex) {}
}
