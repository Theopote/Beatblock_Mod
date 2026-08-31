package com.beatblock.timeline.interaction;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChoreographySectionHitTestTest {

	@Test
	void findsSectionIndexInRulerBand() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(32.0);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 12, SectionType.INTRO, "intro"),
				new ChoreographyPlan.SectionPlan(12, 32, SectionType.DROP, "drop")
			),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		ChoreographyPlanStore.save(timeline, plan, null);

		TimelineViewState view = new TimelineViewState();
		view.setZoom(20f);
		view.setViewStartTimeSeconds(0);
		view.setViewEndTimeSeconds(32);

		TimelineLayout layout = new TimelineLayout();
		layout.rulerTop = 100f;
		layout.rulerHeight = 40f;
		layout.rulerLeft = 200f;
		layout.rulerWidth = 800f;
		layout.contentLeft = 200f;
		layout.contentWidth = 800f;

		float bandY = layout.rulerTop + layout.rulerHeight - 8f;
		float introX = layout.contentLeft + view.timeToScreen(6.0);
		float dropX = layout.contentLeft + view.timeToScreen(20.0);

		assertEquals(0, ChoreographySectionHitTest.findSectionIndexAtMouse(timeline, view, layout, introX, bandY));
		assertEquals(1, ChoreographySectionHitTest.findSectionIndexAtMouse(timeline, view, layout, dropX, bandY));
		assertEquals(-1, ChoreographySectionHitTest.findSectionIndexAtMouse(timeline, view, layout, introX, layout.rulerTop + 2f));
	}

	@Test
	void detectsBoundaryNearSectionEdge() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(32.0);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 12, SectionType.INTRO, "intro"),
				new ChoreographyPlan.SectionPlan(12, 32, SectionType.DROP, "drop")
			),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		ChoreographyPlanStore.save(timeline, plan, null);

		TimelineViewState view = new TimelineViewState();
		view.setZoom(20f);
		view.setViewStartTimeSeconds(0);
		view.setViewEndTimeSeconds(32);

		TimelineLayout layout = new TimelineLayout();
		layout.rulerTop = 100f;
		layout.rulerHeight = 40f;
		layout.rulerLeft = 200f;
		layout.rulerWidth = 800f;
		layout.contentLeft = 200f;
		layout.contentWidth = 800f;

		float bandY = layout.rulerTop + layout.rulerHeight - 8f;
		float boundaryX = layout.contentLeft + view.timeToScreen(12.0);

		ChoreographySectionHitTest.Hit hit = ChoreographySectionHitTest.hit(timeline, view, layout, boundaryX, bandY);
		assertEquals(ChoreographySectionHitTest.HitKind.SECTION_BOUNDARY, hit.kind());
		assertEquals(1, hit.boundaryIndex());

		ChoreographySectionHitTest.Hit bodyHit = ChoreographySectionHitTest.hit(
			timeline, view, layout, boundaryX + 20f, bandY);
		assertEquals(ChoreographySectionHitTest.HitKind.SECTION_BODY, bodyHit.kind());
		assertEquals(1, bodyHit.sectionIndex());
	}
}
