package com.beatblock.timeline.interaction;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.ChoreographySectionBandLayout;
import com.beatblock.timeline.rendering.TimelineLayout;
import org.jspecify.annotations.Nullable;

/**
 * 标尺区编舞段落色带命中测试（段落主体与内部边界）。
 */
public final class ChoreographySectionHitTest {

	public enum HitKind {
		NONE,
		SECTION_BODY,
		SECTION_BOUNDARY
	}

	public record Hit(HitKind kind, int sectionIndex, int boundaryIndex) {
		public static final Hit NONE = new Hit(HitKind.NONE, -1, -1);

		public boolean isBoundary() {
			return kind == HitKind.SECTION_BOUNDARY;
		}

		public boolean isBody() {
			return kind == HitKind.SECTION_BODY;
		}
	}

	private ChoreographySectionHitTest() {}

	public static Hit hit(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mouseX,
		float mouseY
	) {
		if (timeline == null || viewState == null || layout == null || !layout.rulerContains(mouseX, mouseY)) {
			return Hit.NONE;
		}
		if (!ChoreographySectionBandLayout.isInBand(mouseY, layout)) {
			return Hit.NONE;
		}
		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		if (plan == null || plan.sections().isEmpty()) return Hit.NONE;

		float rLeft = layout.rulerLeft;
		float clipRight = rLeft + layout.rulerWidth;
		for (int boundaryIndex = 1; boundaryIndex < plan.sections().size(); boundaryIndex++) {
			double boundaryTime = plan.sections().get(boundaryIndex).startSeconds();
			float x = rLeft + viewState.timeToScreen(boundaryTime);
			if (x >= rLeft - 2 && x <= clipRight + 2
				&& Math.abs(mouseX - x) <= ChoreographySectionBandLayout.BOUNDARY_HIT_PX) {
				return new Hit(HitKind.SECTION_BOUNDARY, -1, boundaryIndex);
			}
		}

		int sectionIndex = findSectionIndexAtTime(plan, viewState, layout, mouseX);
		if (sectionIndex >= 0) {
			return new Hit(HitKind.SECTION_BODY, sectionIndex, -1);
		}
		return Hit.NONE;
	}

	public static int findSectionIndexAtMouse(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mouseX,
		float mouseY
	) {
		Hit result = hit(timeline, viewState, layout, mouseX, mouseY);
		return result.isBody() ? result.sectionIndex() : -1;
	}

	public static ChoreographyPlan.@Nullable SectionPlan sectionAt(
		Timeline timeline,
		int sectionIndex
	) {
		ChoreographyPlan plan = timeline != null ? ChoreographyPlanStore.loadPlan(timeline) : null;
		if (plan == null || sectionIndex < 0 || sectionIndex >= plan.sections().size()) return null;
		return plan.sections().get(sectionIndex);
	}

	private static int findSectionIndexAtTime(
		ChoreographyPlan plan,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mouseX
	) {
		double time = viewState.screenToTime(mouseX - layout.contentLeft);
		for (int i = 0; i < plan.sections().size(); i++) {
			ChoreographyPlan.SectionPlan section = plan.sections().get(i);
			boolean withinEnd = i == plan.sections().size() - 1
				? time <= section.endSeconds()
				: time < section.endSeconds();
			if (time >= section.startSeconds() && withinEnd) {
				return i;
			}
		}
		return -1;
	}
}
