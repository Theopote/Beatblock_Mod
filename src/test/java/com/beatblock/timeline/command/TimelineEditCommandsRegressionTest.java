package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEditCommandsRegressionTest {

	@Test
	void pasteRedoRestoresCreatedClipWithSameIdAndTimes() {
		Timeline timeline = Timeline.createDefault();
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		assertNotNull(track);
		assertTrue(track.getClips().isEmpty());

		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();
		clipboard.add(new TimelineInteractionClipboard.ClipboardEvent(
			Timeline.TRACK_ID_ANIMATION_AUTO, "missing", 1.0, EventType.ANIMATION, Map.of("k", 1)));
		clipboard.add(new TimelineInteractionClipboard.ClipboardEvent(
			Timeline.TRACK_ID_ANIMATION_AUTO, "missing", 2.0, EventType.ANIMATION, Map.of("k", 2)));

		SelectionState selection = new SelectionState();
		PasteTimelineEventsCommand command = new PasteTimelineEventsCommand(
			new TimelineInteractionClipboard.PasteRequest(
				timeline, selection, clipboard, 5.0, null, null, new TimelineTrackListState()));

		command.execute();
		assertEquals(1, track.getClips().size());
		Clip created = track.getClips().getFirst();
		String clipId = created.getId();
		double start = created.getStartTimeSeconds();
		double end = created.getEndTimeSeconds();
		assertEquals(2, created.getEvents().size());

		command.undo();
		assertTrue(track.getClips().isEmpty());

		command.execute();
		assertEquals(1, track.getClips().size());
		Clip restored = track.getClips().getFirst();
		assertEquals(clipId, restored.getId());
		assertEquals(start, restored.getStartTimeSeconds(), 1e-9);
		assertEquals(end, restored.getEndTimeSeconds(), 1e-9);
		assertEquals(2, restored.getEvents().size());
	}

	@Test
	void copyIncludesEventsFromSelectedClips() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 4);
		TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());
		TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of());

		SelectionState selection = new SelectionState();
		selection.selectClip(clip.getId());
		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();
		TimelineInteractionClipboard.copy(clipboard, timeline, selection);

		assertEquals(2, clipboard.size());
		assertEquals(1.0, clipboard.get(0).timeSeconds(), 1e-9);
		assertEquals(2.0, clipboard.get(1).timeSeconds(), 1e-9);
	}

	@Test
	void splitClipMovesLaterEventsAndSupportsUndo() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0.0, 10.0);
		TimelineEvent early = TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of());
		TimelineEvent late = TimelineOperations.addEvent(clip, 7.0, EventType.ANIMATION, Map.of());
		SelectionState selection = new SelectionState();
		selection.selectClip(clip.getId());

		SplitClipCommand command = new SplitClipCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), 5.0, selection);
		command.execute();

		assertTrue(command.wasApplied());
		assertEquals(5.0, clip.getEndTimeSeconds(), 1e-9);
		assertNotNull(clip.getEvent(early.getId()));
		assertNull(clip.getEvent(late.getId()));

		String rightId = command.rightClipId();
		assertNotNull(rightId);
		Clip right = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClip(rightId);
		assertNotNull(right);
		assertEquals(5.0, right.getStartTimeSeconds(), 1e-9);
		assertEquals(10.0, right.getEndTimeSeconds(), 1e-9);
		assertNotNull(right.getEvent(late.getId()));

		command.undo();
		assertEquals(10.0, clip.getEndTimeSeconds(), 1e-9);
		assertNotNull(clip.getEvent(late.getId()));
		assertNull(timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClip(rightId));
	}

	@Test
	void duplicateSelectionPastesAfterSpanAndUndoes() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0.0, 20.0);
		TimelineEvent event = TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of("a", 1));
		SelectionState selection = new SelectionState();
		selection.selectEvent(event.getId());

		DuplicateTimelineEventsCommand command = new DuplicateTimelineEventsCommand(
			timeline, selection, new TimelineTrackListState());
		command.execute();

		assertEquals(2, clip.getEvents().size());
		double[] times = clip.getEvents().stream().mapToDouble(TimelineEvent::getTimeSeconds).sorted().toArray();
		assertEquals(2.0, times[0], 1e-9);
		assertEquals(2.25, times[1], 1e-9);

		command.undo();
		assertEquals(1, clip.getEvents().size());
		assertEquals(2.0, clip.getEvents().getFirst().getTimeSeconds(), 1e-9);
	}

	@Test
	void cutSelectedClipRemovesClipInOneUndoStep() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0.0, 4.0);
		TimelineEvent event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of("k", 1));
		SelectionState selection = new SelectionState();
		selection.selectClip(clip.getId());
		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();

		CutTimelineEventsCommand command = new CutTimelineEventsCommand(
			timeline, selection, new TimelineTrackListState(), clipboard);
		command.execute();

		assertTrue(command.wasApplied());
		assertEquals(1, clipboard.size());
		assertNull(timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClip(clip.getId()));
		assertTrue(selection.getSelectedClips().isEmpty());
		assertTrue(selection.getSelectedEvents().isEmpty());

		command.undo();
		Clip restored = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClip(clip.getId());
		assertNotNull(restored);
		assertNotNull(restored.getEvent(event.getId()));
		assertTrue(clipboard.isEmpty());
	}

	@Test
	void deleteClipClearsStaleEventSelectionAndRedoesWithoutSelection() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0.0, 4.0);
		TimelineEvent event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());
		SelectionState selection = new SelectionState();
		selection.selectClip(clip.getId());
		selection.selectEvent(event.getId());

		DeleteSelectedTimelineEntriesCommand command = new DeleteSelectedTimelineEntriesCommand(
			timeline, selection, new TimelineTrackListState());
		command.execute();
		assertTrue(command.wasApplied());
		assertTrue(selection.getSelectedEvents().isEmpty());
		assertNull(timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClip(clip.getId()));

		command.undo();
		assertNotNull(timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClip(clip.getId()));

		// Selection intentionally not restored; redo must still work from snapshot.
		selection.clearAll();
		command.execute();
		assertNull(timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClip(clip.getId()));
	}

	@Test
	void splitRedoKeepsSameRightClipId() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0.0, 10.0);
		TimelineOperations.addEvent(clip, 7.0, EventType.ANIMATION, Map.of());
		SelectionState selection = new SelectionState();
		selection.selectClip(clip.getId());

		SplitClipCommand command = new SplitClipCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), 5.0, selection);
		command.execute();
		String rightId = command.rightClipId();
		assertNotNull(rightId);

		command.undo();
		command.execute();
		assertEquals(rightId, command.rightClipId());
		assertNotNull(timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClip(rightId));
	}

	@Test
	void duplicateDoesNotMutateUserClipboard() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0.0, 10.0);
		TimelineEvent event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());
		SelectionState selection = new SelectionState();
		selection.selectEvent(event.getId());

		var userClipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();
		userClipboard.add(new TimelineInteractionClipboard.ClipboardEvent(
			Timeline.TRACK_ID_ANIMATION_AUTO, "x", 9.0, EventType.ANIMATION, Map.of("keep", true)));

		DuplicateTimelineEventsCommand command = new DuplicateTimelineEventsCommand(
			timeline, selection, new TimelineTrackListState());
		command.execute();

		assertEquals(1, userClipboard.size());
		assertEquals(9.0, userClipboard.getFirst().timeSeconds(), 1e-9);
		assertEquals(2, clip.getEvents().size());
	}

	@Test
	void pasteDoesNotForceIncompatibleEventsOntoContextTrack() {
		Timeline timeline = Timeline.createDefault();
		Clip cameraClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 0.0, 10.0);
		TimelineOperations.addEvent(cameraClip, 1.0, EventType.CAMERA_KEYFRAME, Map.of());

		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();
		clipboard.add(new TimelineInteractionClipboard.ClipboardEvent(
			Timeline.TRACK_ID_CAMERA, cameraClip.getId(), 1.0, EventType.CAMERA_KEYFRAME, Map.of()));

		SelectionState selection = new SelectionState();
		PasteTimelineEventsCommand command = new PasteTimelineEventsCommand(
			new TimelineInteractionClipboard.PasteRequest(
				timeline,
				selection,
				clipboard,
				3.0,
				Timeline.TRACK_ID_ANIMATION_AUTO, // incompatible context
				null,
				new TimelineTrackListState()));
		command.execute();

		assertTrue(command.wasApplied());
		assertEquals(0, timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClips().stream()
			.mapToInt(c -> c.getEvents().size()).sum());
		assertTrue(timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().stream()
			.anyMatch(c -> c.getEvents().stream().anyMatch(e -> Math.abs(e.getTimeSeconds() - 3.0) < 1e-9)));
	}

	@Test
	void pasteIntoContextClipExpandsBoundsAndUndoRestores() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0.0, 2.0);
		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();
		clipboard.add(new TimelineInteractionClipboard.ClipboardEvent(
			Timeline.TRACK_ID_ANIMATION_AUTO, "src", 0.0, EventType.ANIMATION, Map.of()));

		SelectionState selection = new SelectionState();
		PasteTimelineEventsCommand command = new PasteTimelineEventsCommand(
			new TimelineInteractionClipboard.PasteRequest(
				timeline, selection, clipboard, 5.0,
				Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(),
				new TimelineTrackListState()));
		command.execute();

		assertEquals(5.0, clip.getEndTimeSeconds(), 1e-9);
		assertEquals(1, clip.getEvents().size());

		command.undo();
		assertEquals(2.0, clip.getEndTimeSeconds(), 1e-9);
		assertTrue(clip.getEvents().isEmpty());
	}

	@Test
	void cutSkipsLockedTrackContentInClipboard() {
		Timeline timeline = Timeline.createDefault();
		Clip unlocked = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0.0, 4.0);
		TimelineEvent unlockedEvent = TimelineOperations.addEvent(unlocked, 1.0, EventType.ANIMATION, Map.of("u", 1));
		Clip locked = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, 0.0, 4.0);
		TimelineEvent lockedEvent = TimelineOperations.addEvent(locked, 1.5, EventType.ANIMATION, Map.of("l", 1));

		TimelineTrackListState trackList = new TimelineTrackListState();
		trackList.setLocked(com.beatblock.timeline.rendering.TimelineTrackMeta.ROW_ANIM_BLOCK, true);

		SelectionState selection = new SelectionState();
		selection.selectEvent(unlockedEvent.getId());
		selection.selectEvent(lockedEvent.getId());
		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();

		CutTimelineEventsCommand command = new CutTimelineEventsCommand(
			timeline, selection, trackList, clipboard);
		command.execute();

		assertTrue(command.wasApplied());
		assertEquals(1, clipboard.size());
		assertTrue(clipboard.getFirst().parameters().containsKey("u"));
		assertTrue(unlocked.getEvents().isEmpty());
		assertEquals(1, locked.getEvents().size());
	}

	@Test
	void deletingLastAudioClipRestoresRootMetadataOnUndo() {
		Timeline timeline = Timeline.createDefault();
		Clip audioClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_AUDIO, 0.0, 4.0);
		timeline.setMetadata("audioRootClipId", audioClip.getId());
		timeline.setMetadata("audioPath", "C:/music/song.wav");
		timeline.setMetadata("audioAssetId", "asset-1");
		var audioData = timeline.getTrack(Timeline.TRACK_ID_AUDIO).getAudioData();
		assertNotNull(audioData);
		audioData.setWaveform(new com.beatblock.timeline.WaveformData(new float[]{0.1f, 0.2f}, 1.0, 44100));
		audioData.addFeatureEvent("kick", new com.beatblock.timeline.FeatureEvent(0.5, 0.8f));

		SelectionState selection = new SelectionState();
		selection.selectClip(audioClip.getId());
		DeleteSelectedTimelineEntriesCommand command = new DeleteSelectedTimelineEntriesCommand(
			timeline, selection, new TimelineTrackListState());
		command.execute();

		assertNull(timeline.getTrack(Timeline.TRACK_ID_AUDIO).getClip(audioClip.getId()));
		assertNull(timeline.getMetadata("audioRootClipId"));
		assertNull(timeline.getMetadata("audioPath"));
		assertNull(audioData.getWaveform());
		assertTrue(audioData.getFeatureTracks().isEmpty());

		command.undo();
		assertNotNull(timeline.getTrack(Timeline.TRACK_ID_AUDIO).getClip(audioClip.getId()));
		assertEquals(audioClip.getId(), timeline.getMetadata("audioRootClipId"));
		assertEquals("C:/music/song.wav", timeline.getMetadata("audioPath"));
		assertEquals("asset-1", timeline.getMetadata("audioAssetId"));
		assertNotNull(audioData.getWaveform());
		assertEquals(2, audioData.getWaveform().getSampleCount());
		assertNotNull(audioData.getFeatureTrack("kick"));
		assertEquals(1, audioData.getFeatureTrack("kick").size());
	}

	@Test
	void deletingRootAudioClipWhileOthersRemainPromotesNewRoot() {
		Timeline timeline = Timeline.createDefault();
		Clip root = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_AUDIO, 0.0, 2.0);
		Clip other = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_AUDIO, 2.0, 4.0);
		timeline.setMetadata("audioRootClipId", root.getId());
		timeline.setMetadata("clipLabel_" + root.getId(), "root");
		timeline.setMetadata("clipAudioPath_" + root.getId(), "C:/a.wav");
		timeline.setMetadata("clipLabel_" + other.getId(), "other");

		SelectionState selection = new SelectionState();
		selection.selectClip(root.getId());
		DeleteSelectedTimelineEntriesCommand command = new DeleteSelectedTimelineEntriesCommand(
			timeline, selection, new TimelineTrackListState());
		command.execute();

		assertNull(timeline.getTrack(Timeline.TRACK_ID_AUDIO).getClip(root.getId()));
		assertNotNull(timeline.getTrack(Timeline.TRACK_ID_AUDIO).getClip(other.getId()));
		assertEquals(other.getId(), timeline.getMetadata("audioRootClipId"));
		assertNull(timeline.getMetadata("clipLabel_" + root.getId()));
		assertNull(timeline.getMetadata("clipAudioPath_" + root.getId()));
		assertEquals("other", timeline.getMetadata("clipLabel_" + other.getId()));

		command.undo();
		assertEquals(root.getId(), timeline.getMetadata("audioRootClipId"));
		assertEquals("root", timeline.getMetadata("clipLabel_" + root.getId()));
		assertEquals("C:/a.wav", timeline.getMetadata("clipAudioPath_" + root.getId()));
	}

	@Test
	void pasteStripsBuildLayerBindingClaimParams() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0.0, 10.0);
		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();
		clipboard.add(new TimelineInteractionClipboard.ClipboardEvent(
			Timeline.TRACK_ID_ANIMATION_AUTO,
			clip.getId(),
			1.0,
			EventType.ANIMATION,
			Map.of(
				"layerId", "layer-1",
				"layerBound", "true",
				"stageObjectId", "stage-1",
				"animationType", "build"
			)));

		SelectionState selection = new SelectionState();
		PasteTimelineEventsCommand command = new PasteTimelineEventsCommand(
			new TimelineInteractionClipboard.PasteRequest(
				timeline, selection, clipboard, 3.0,
				Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(),
				new TimelineTrackListState()));
		command.execute();

		assertEquals(1, clip.getEvents().size());
		TimelineEvent pasted = clip.getEvents().getFirst();
		assertNull(pasted.getParameter("layerId"));
		assertNull(pasted.getParameter("layerBound"));
		assertEquals("stage-1", pasted.getParameter("stageObjectId"));
	}

	@Test
	void pasteMultiTrackCreatesSeparateClipsPerTrack() {
		Timeline timeline = Timeline.createDefault();
		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();
		clipboard.add(new TimelineInteractionClipboard.ClipboardEvent(
			Timeline.TRACK_ID_ANIMATION_AUTO, "a", 1.0, EventType.ANIMATION, Map.of("t", "auto")));
		clipboard.add(new TimelineInteractionClipboard.ClipboardEvent(
			Timeline.TRACK_ID_CAMERA, "c", 1.0, EventType.CAMERA_KEYFRAME, Map.of("t", "cam")));

		SelectionState selection = new SelectionState();
		PasteTimelineEventsCommand command = new PasteTimelineEventsCommand(
			new TimelineInteractionClipboard.PasteRequest(
				timeline, selection, clipboard, 5.0, null, null, new TimelineTrackListState()));
		command.execute();

		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClips().size());
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClips().getFirst().getEvents().size());
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst().getEvents().size());
	}
}
