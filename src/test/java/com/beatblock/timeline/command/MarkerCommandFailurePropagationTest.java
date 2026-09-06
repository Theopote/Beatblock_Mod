package com.beatblock.timeline.command;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.SectionPlanSource;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.MarkerEditState;
import com.beatblock.timeline.MarkerOrigin;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.ui.presenter.MarkerPanelPresenter;
import com.beatblock.ui.presenter.TimelineEditorPresenter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1.1 — failed marker mutations must not project structure, notify, or enter Undo.
 */
class MarkerCommandFailurePropagationTest {

	@Test
	void updateMarkerRejectedByLockedSectionCollisionDoesNotNotify() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timeline);
		savePlan(timeline, List.of(
			section(0.0, 10.0, SectionType.INTRO, "intro"),
			section(10.0, 20.0, SectionType.VERSE, "verse"),
			section(20.0, 30.0, SectionType.CHORUS, "chorus")
		));
		TimelineMarker locked = new TimelineMarker(
			"lock", 10.0, "SECTION verse", MarkerType.SECTION,
			MarkerOrigin.MANUAL, MarkerEditState.LOCKED);
		TimelineMarker moving = TimelineMarker.manual(20.0, "SECTION chorus", MarkerType.SECTION);
		timeline.addMarker(locked);
		timeline.addMarker(moving);

		MarkerPanelPresenter presenter = new MarkerPanelPresenter(
			new TimelineEditorPresenter(() -> editor, time -> {}),
			() -> timeline
		);
		int undoBefore = editor.getCommandManager().undoCount();
		ChoreographyPlan planBefore = ChoreographyPlanStore.loadPlan(timeline);

		var outcome = presenter.applyMarkerEdit(
			timeline, moving.getId(), "SECTION chorus", 10.0, MarkerType.SECTION.ordinal(), false);

		assertFalse(outcome.result().ok());
		assertEquals(20.0, find(timeline, moving.getId()).getTimeSeconds(), 1e-9);
		assertEquals(10.0, find(timeline, "lock").getTimeSeconds(), 1e-9);
		assertEquals(MarkerEditState.LOCKED, find(timeline, "lock").getEditState());
		assertEquals(undoBefore, editor.getCommandManager().undoCount());
		assertEquals(
			planBefore.sections().get(1).startSeconds(),
			ChoreographyPlanStore.loadPlan(timeline).sections().get(1).startSeconds(),
			1e-9
		);
		assertEquals(
			planBefore.sections().get(2).startSeconds(),
			ChoreographyPlanStore.loadPlan(timeline).sections().get(2).startSeconds(),
			1e-9
		);
	}

	@Test
	void moveMarkerRejectedCollisionDoesNotProjectPlan() {
		Timeline timeline = Timeline.createDefault();
		savePlan(timeline, List.of(
			section(0.0, 10.0, SectionType.INTRO, "intro"),
			section(10.0, 20.0, SectionType.VERSE, "verse"),
			section(20.0, 30.0, SectionType.CHORUS, "chorus")
		));
		timeline.addMarker(new TimelineMarker(
			"lock", 10.0, "SECTION verse", MarkerType.SECTION,
			MarkerOrigin.MANUAL, MarkerEditState.LOCKED));
		TimelineMarker moving = TimelineMarker.manual(20.0, "SECTION chorus", MarkerType.SECTION);
		timeline.addMarker(moving);
		double planChorusStart = ChoreographyPlanStore.loadPlan(timeline).sections().get(2).startSeconds();

		MoveMarkerCommand command = new MoveMarkerCommand(timeline, moving, 10.0);
		command.execute();

		assertFalse(command.wasApplied());
		assertEquals(20.0, find(timeline, moving.getId()).getTimeSeconds(), 1e-9);
		assertEquals(
			planChorusStart,
			ChoreographyPlanStore.loadPlan(timeline).sections().get(2).startSeconds(),
			1e-9
		);
	}

	@Test
	void failedMarkerCommandDoesNotCreateUndoEntry() {
		Timeline timeline = Timeline.createDefault();
		CommandManager cm = new CommandManager();
		timeline.addMarker(new TimelineMarker(
			"lock", 10.0, "SECTION A", MarkerType.SECTION,
			MarkerOrigin.MANUAL, MarkerEditState.LOCKED));
		TimelineMarker moving = TimelineMarker.manual(20.0, "SECTION B", MarkerType.SECTION);
		timeline.addMarker(moving);

		MoveMarkerCommand command = new MoveMarkerCommand(timeline, moving, 10.0);
		cm.execute(command);

		assertFalse(command.wasApplied());
		assertEquals(0, cm.undoCount());
		assertFalse(cm.canUndo());
	}

	@Test
	void successfulSectionMoveProjectsPlanExactlyOnce() {
		Timeline timeline = Timeline.createDefault();
		CommandManager cm = new CommandManager();
		savePlan(timeline, List.of(
			section(0.0, 8.0, SectionType.INTRO, "intro"),
			section(8.0, 16.0, SectionType.VERSE, "verse")
		));
		TimelineMarker before = TimelineMarker.manual(8.0, "SECTION verse", MarkerType.SECTION);
		timeline.addMarker(before);

		MoveMarkerCommand command = new MoveMarkerCommand(timeline, before, 9.5);
		cm.execute(command);

		assertTrue(command.wasApplied());
		assertEquals(1, cm.undoCount());
		assertEquals(9.5, find(timeline, before.getId()).getTimeSeconds(), 1e-9);
		assertEquals(9.5, ChoreographyPlanStore.loadPlan(timeline).sections().get(1).startSeconds(), 1e-9);
		assertEquals(9.5, ChoreographyPlanStore.loadPlan(timeline).sections().get(0).endSeconds(), 1e-9);
		assertEquals(SectionPlanSource.USER_EDITED,
			ChoreographyPlanStore.loadPlan(timeline).sections().get(1).source());

		cm.undo();
		assertEquals(8.0, find(timeline, before.getId()).getTimeSeconds(), 1e-9);
		assertEquals(8.0, ChoreographyPlanStore.loadPlan(timeline).sections().get(1).startSeconds(), 1e-9);
	}

	private static TimelineMarker find(Timeline timeline, String id) {
		return timeline.getMarkers().stream()
			.filter(m -> id.equals(m.getId()))
			.findFirst()
			.orElseThrow();
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
