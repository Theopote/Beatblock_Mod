package com.beatblock.timeline.marker;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.SectionPlanSource;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.MarkerEditState;
import com.beatblock.timeline.MarkerOrigin;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.command.MoveMarkerCommand;
import com.beatblock.timeline.command.UpdateMarkerCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionMarkerStructureBridgeTest {

	@Test
	void markerMoveProjectsOntoMatchingPlanSection() {
		Timeline timeline = Timeline.createDefault();
		savePlan(timeline, List.of(
			section(0.0, 8.0, SectionType.INTRO, "intro"),
			section(8.0, 16.0, SectionType.VERSE, "verse")
		));
		TimelineMarker marker = TimelineMarker.manual(8.0, "SECTION verse", MarkerType.SECTION);
		timeline.addMarker(marker);

		assertTrue(SectionMarkerStructureBridge.projectMarkerOntoPlan(
			timeline, 8.0, marker.withTimeSeconds(9.5, true)));

		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		assertEquals(9.5, plan.sections().get(1).startSeconds(), 1e-6);
		assertEquals(SectionPlanSource.USER_EDITED, plan.sections().get(1).source());
		assertEquals(9.5, plan.sections().get(0).endSeconds(), 1e-6);
	}

	@Test
	void markerRenameProjectsLabelOntoPlan() {
		Timeline timeline = Timeline.createDefault();
		savePlan(timeline, List.of(
			section(0.0, 10.0, SectionType.CHORUS, "chorus")
		));
		TimelineMarker before = TimelineMarker.manual(0.0, "SECTION chorus", MarkerType.SECTION);
		timeline.addMarker(before);
		TimelineMarker after = before.withFields(0.0, "SECTION Drop Hit", MarkerType.SECTION, true);

		assertTrue(SectionMarkerStructureBridge.projectMarkerOntoPlan(timeline, 0.0, after));

		assertEquals("Drop Hit", ChoreographyPlanStore.loadPlan(timeline).sections().getFirst().label());
		assertEquals(SectionPlanSource.USER_EDITED,
			ChoreographyPlanStore.loadPlan(timeline).sections().getFirst().source());
	}

	@Test
	void planBoundaryProjectsOntoNearbySectionMarker() {
		Timeline timeline = Timeline.createDefault();
		TimelineMarker marker = TimelineMarker.manual(8.0, "SECTION verse", MarkerType.SECTION);
		timeline.addMarker(marker);

		assertTrue(SectionMarkerStructureBridge.projectPlanBoundaryOntoMarkers(timeline, 8.0, 9.0));
		assertEquals(9.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-6);
	}

	@Test
	void lockedPlanSectionRejectsMarkerProjection() {
		Timeline timeline = Timeline.createDefault();
		savePlan(timeline, List.of(
			new ChoreographyPlan.SectionPlan(
				8.0, 16.0, SectionType.VERSE, "verse", 1.0, SectionPlanSource.LOCKED)
		));
		TimelineMarker marker = TimelineMarker.manual(8.0, "SECTION verse", MarkerType.SECTION);
		timeline.addMarker(marker);

		assertFalse(SectionMarkerStructureBridge.projectMarkerOntoPlan(
			timeline, 8.0, marker.withTimeSeconds(9.0, true)));
		assertEquals(8.0, ChoreographyPlanStore.loadPlan(timeline).sections().getFirst().startSeconds(), 1e-6);
	}

	@Test
	void lockedMarkerRejectsPlanBoundaryProjection() {
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(new TimelineMarker(
			"lock", 8.0, "SECTION verse", MarkerType.SECTION,
			MarkerOrigin.MANUAL, MarkerEditState.LOCKED));

		assertFalse(SectionMarkerStructureBridge.projectPlanBoundaryOntoMarkers(timeline, 8.0, 9.0));
		assertEquals(8.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-6);
	}

	@Test
	void moveMarkerCommandKeepsPlanInSyncOnExecuteAndUndo() {
		Timeline timeline = Timeline.createDefault();
		savePlan(timeline, List.of(
			section(0.0, 8.0, SectionType.INTRO, "intro"),
			section(8.0, 16.0, SectionType.VERSE, "verse")
		));
		TimelineMarker before = TimelineMarker.manual(8.0, "SECTION verse", MarkerType.SECTION);
		timeline.addMarker(before);

		MoveMarkerCommand command = new MoveMarkerCommand(timeline, before, 9.0);
		command.execute();
		assertEquals(9.0, ChoreographyPlanStore.loadPlan(timeline).sections().get(1).startSeconds(), 1e-6);

		command.undo();
		assertEquals(8.0, ChoreographyPlanStore.loadPlan(timeline).sections().get(1).startSeconds(), 1e-6);
		assertEquals(8.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-6);
	}

	@Test
	void updateMarkerCommandProjectsRename() {
		Timeline timeline = Timeline.createDefault();
		savePlan(timeline, List.of(section(0.0, 10.0, SectionType.DROP, "drop")));
		TimelineMarker before = TimelineMarker.manual(0.0, "SECTION drop", MarkerType.SECTION);
		timeline.addMarker(before);
		TimelineMarker after = before.withFields(0.0, "SECTION Big Drop", MarkerType.SECTION, true);

		UpdateMarkerCommand command = new UpdateMarkerCommand(timeline, before, after);
		command.execute();
		assertEquals("Big Drop", ChoreographyPlanStore.loadPlan(timeline).sections().getFirst().label());

		command.undo();
		assertEquals("drop", ChoreographyPlanStore.loadPlan(timeline).sections().getFirst().label());
	}

	@Test
	void extractSectionLabelStripsPrefix() {
		assertEquals("verse", SectionMarkerStructureBridge.extractSectionLabel("SECTION verse"));
		assertEquals("Drop", SectionMarkerStructureBridge.extractSectionLabel("SECTION Drop"));
		assertEquals("custom", SectionMarkerStructureBridge.extractSectionLabel("custom"));
	}

	private static void savePlan(Timeline timeline, List<ChoreographyPlan.SectionPlan> sections) {
		ChoreographyPlanStore.save(
			timeline,
			new ChoreographyPlan(sections, List.of(), List.of(), List.of(), List.of(), DensityCurve.uniform(1.0)),
			null
		);
	}

	private static ChoreographyPlan.SectionPlan section(
		double start,
		double end,
		SectionType type,
		String label
	) {
		return new ChoreographyPlan.SectionPlan(
			start, end, type, label, 1.0, SectionPlanSource.ANALYZED);
	}
}
