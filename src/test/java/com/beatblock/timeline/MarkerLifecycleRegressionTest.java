package com.beatblock.timeline;

import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CreateMarkerCommand;
import com.beatblock.timeline.command.DeleteMarkerCommand;
import com.beatblock.timeline.command.MoveMarkerCommand;
import com.beatblock.timeline.command.UpdateMarkerCommand;
import com.beatblock.timeline.editor.InteractionMode;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.interaction.TimelineGestureLifecycle;
import com.beatblock.timeline.marker.MarkerAnalysisMerger;
import com.beatblock.timeline.project.OscProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mandatory Marker lifecycle regressions from Creator Surface audit.
 */
class MarkerLifecycleRegressionTest {

	@TempDir
	Path tempDir;

	@Test
	void createMarkerIsOneUndo() {
		Timeline timeline = Timeline.createDefault();
		CommandManager cm = new CommandManager();
		TimelineMarker marker = TimelineMarker.manual(1.0, "Cue", MarkerType.GENERIC);

		cm.execute(new CreateMarkerCommand(timeline, marker));
		assertEquals(1, timeline.getMarkers().size());
		assertEquals(1, cm.undoCount());

		cm.undo();
		assertTrue(timeline.getMarkers().isEmpty());
		assertEquals(0, cm.undoCount());
	}

	@Test
	void updateMarkerIsOneUndo() {
		Timeline timeline = Timeline.createDefault();
		CommandManager cm = new CommandManager();
		TimelineMarker before = TimelineMarker.manual(1.0, "A", MarkerType.GENERIC);
		timeline.addMarker(before);
		TimelineMarker after = before.withFields(2.0, "B", MarkerType.DROP, true);

		cm.execute(new UpdateMarkerCommand(timeline, before, after));
		assertEquals("B", timeline.getMarkers().getFirst().getName());
		assertEquals(1, cm.undoCount());

		cm.undo();
		assertEquals("A", timeline.getMarkers().getFirst().getName());
		assertEquals(1.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);
	}

	@Test
	void deleteMarkerUndoRestoresIdAndType() {
		Timeline timeline = Timeline.createDefault();
		CommandManager cm = new CommandManager();
		TimelineMarker marker = new TimelineMarker(
			"keep-id", 3.0, "Cam", MarkerType.CAMERA, MarkerOrigin.MANUAL, MarkerEditState.USER_EDITED);
		timeline.addMarker(marker);

		cm.execute(new DeleteMarkerCommand(timeline, marker));
		assertTrue(timeline.getMarkers().isEmpty());

		cm.undo();
		TimelineMarker restored = timeline.getMarkers().getFirst();
		assertEquals("keep-id", restored.getId());
		assertEquals(MarkerType.CAMERA, restored.getType());
		assertEquals(3.0, restored.getTimeSeconds(), 1e-9);
	}

	@Test
	void dragMarkerCommitIsOneUndo() {
		Timeline timeline = Timeline.createDefault();
		CommandManager cm = new CommandManager();
		TimelineMarker before = TimelineMarker.manual(1.0, "Cue", MarkerType.GENERIC);
		timeline.addMarker(before);

		cm.execute(new MoveMarkerCommand(timeline, before, 4.5));
		assertEquals(4.5, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);
		assertEquals(1, cm.undoCount());

		cm.undo();
		assertEquals(1.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);
	}

	@Test
	void dragCancelRestoresOriginalTime() {
		Timeline timeline = Timeline.createDefault();
		TimelineMarker marker = TimelineMarker.manual(2.0, "Cue", MarkerType.GENERIC);
		timeline.addMarker(marker);

		InteractionState interaction = new InteractionState();
		interaction.setMode(InteractionMode.MARKER_DRAG);
		interaction.setActiveMarkerId(marker.getId());
		interaction.setMarkerDragBefore(marker);
		interaction.setMarkerDragStartTimeSeconds(2.0);
		timeline.updateMarkerTimeLive(marker.getId(), 9.0);

		TimelineGestureLifecycle.cancelLiveDocumentPreview(
			timeline, interaction, null, null, null, null, null, null);

		assertEquals(2.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);
		assertEquals(InteractionMode.NONE, interaction.getMode());
		assertNull(interaction.getActiveMarkerId());
	}

	@Test
	void sectionMarkerTypeChangeIsProtected() {
		TimelineMarker section = TimelineMarker.audioAnalysisSection(0, "SECTION A");
		assertTrue(MarkerSemanticService.requiresStructuralConfirm(
			section, MarkerEditPolicy.StructuralAction.CHANGE_TYPE, MarkerType.FX));
		assertFalse(MarkerSemanticService.allowsMutation(
			section, MarkerEditPolicy.StructuralAction.CHANGE_TYPE, MarkerType.FX, false));
		assertTrue(MarkerSemanticService.allowsMutation(
			section, MarkerEditPolicy.StructuralAction.CHANGE_TYPE, MarkerType.FX, true));
	}

	@Test
	void generatedSectionEditBecomesUserEdited() {
		Timeline timeline = Timeline.createDefault();
		TimelineMarker generated = TimelineMarker.audioAnalysisSection(1.0, "SECTION A");
		timeline.addMarker(generated);

		new MoveMarkerCommand(timeline, generated, 2.0).execute();
		assertEquals(MarkerEditState.USER_EDITED, timeline.getMarkers().getFirst().getEditState());
		assertEquals(MarkerOrigin.AUDIO_ANALYSIS, timeline.getMarkers().getFirst().getOrigin());
	}

	@Test
	void deleteGeneratedSectionRequiresPolicy() {
		TimelineMarker generated = TimelineMarker.audioAnalysisSection(0, "SECTION A");
		assertTrue(MarkerSemanticService.requiresStructuralConfirm(
			generated, MarkerEditPolicy.StructuralAction.DELETE, null));
		assertFalse(MarkerSemanticService.allowsMutation(
			generated, MarkerEditPolicy.StructuralAction.DELETE, null, false));
	}

	@Test
	void duplicateSectionTimestampRejectedOrResolved() {
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(TimelineMarker.manual(10.0, "SECTION A", MarkerType.SECTION));
		timeline.addMarker(TimelineMarker.manual(10.0, "SECTION B", MarkerType.SECTION));
		assertEquals(1, timeline.getMarkers().size());
		assertEquals("SECTION B", timeline.getMarkers().getFirst().getName());

		timeline.addMarker(new TimelineMarker(
			"lock", 20.0, "SECTION L", MarkerType.SECTION,
			MarkerOrigin.MANUAL, MarkerEditState.LOCKED));
		assertFalse(timeline.addMarker(TimelineMarker.manual(20.0, "SECTION X", MarkerType.SECTION)));
		assertEquals(2, timeline.getMarkers().size());
	}

	@Test
	void bindingSectionLookupUpdatesAfterMarkerCommand() {
		Timeline timeline = Timeline.createDefault();
		TimelineMarker intro = TimelineMarker.manual(0.0, "SECTION intro", MarkerType.SECTION);
		timeline.addMarker(intro);
		assertEquals("intro", MarkerSemanticService.sectionLabelAtTime(timeline, 1.0));

		TimelineMarker renamed = intro.withFields(0.0, "SECTION chorus", MarkerType.SECTION, true);
		new UpdateMarkerCommand(timeline, intro, renamed).execute();
		assertEquals("chorus", MarkerSemanticService.sectionLabelAtTime(timeline, 1.0));
	}

	@Test
	void oscRoundTripPreservesMarkerOrigin() throws Exception {
		Path file = tempDir.resolve("markers.osc");
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(new TimelineMarker(
			"sec1", 4.0, "SECTION INTRO", MarkerType.SECTION,
			MarkerOrigin.AUDIO_ANALYSIS, MarkerEditState.GENERATED));
		OscProjectStore.save(file, timeline);

		OscProjectStore.LoadedProject loaded = OscProjectStore.load(file);
		assertEquals(MarkerOrigin.AUDIO_ANALYSIS, loaded.getMarkers().getFirst().getOrigin());
		assertEquals(MarkerEditState.GENERATED, loaded.getMarkers().getFirst().getEditState());
	}

	@Test
	void userEditedSectionSurvivesReAnalysis() {
		TimelineMarker edited = new TimelineMarker(
			"keep", 10.0, "SECTION chorus", MarkerType.SECTION,
			MarkerOrigin.AUDIO_ANALYSIS, MarkerEditState.USER_EDITED);
		List<TimelineMarker> merged = MarkerAnalysisMerger.merge(
			List.of(edited),
			List.of(
				new MarkerAnalysisMerger.AnalyzedSection(10.0, "SECTION CHORUS"),
				new MarkerAnalysisMerger.AnalyzedSection(20.0, "SECTION OUTRO")
			)
		);
		assertTrue(merged.stream().anyMatch(m ->
			"keep".equals(m.getId())
				&& m.getEditState() == MarkerEditState.USER_EDITED
				&& Math.abs(m.getTimeSeconds() - 10.0) < 1e-9));
		assertFalse(MarkerSemanticService.isReplaceableByAudioAnalysis(edited));
	}
}
