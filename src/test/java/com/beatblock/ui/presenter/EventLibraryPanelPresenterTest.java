package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.eventlibrary.EventTemplate;
import com.beatblock.ui.eventlibrary.EventTemplateStore;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Event Library Creator lifecycle: insertManualEvents, one Undo, auto-selection.
 */
@WithBeatBlockContext
class EventLibraryPanelPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private CommandManager commandManager;
	private StageObjectSystem stageObjects;
	private EventLibraryPanelPresenter presenter;
	private String templateId;

	@BeforeEach
	void setUp() {
		EventTemplateStore.resetForTests();

		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(60.0);
		MusicPlayer musicPlayer = new MusicPlayer();
		editor = new TimelineEditor(timeline, musicPlayer);
		commandManager = editor.getCommandManager();
		stageObjects = new StageObjectSystem();
		BeatBlock.installContext(BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build());

		EventPropertiesPresenter eventPropertiesPresenter = new EventPropertiesPresenter(
			id -> true,
			blockId -> blockId != null && blockId.startsWith("minecraft:"),
			() -> List.of(new EventPropertiesOption("", "未绑定")),
			() -> List.of(new EventPropertiesOption("", "未绑定")),
			() -> new EventPropertiesPresenter.CameraViewSample(1, 2, 3, 90f, 0f)
		);
		presenter = new EventLibraryPanelPresenter(
			eventPropertiesPresenter,
			() -> timeline,
			() -> editor,
			() -> stageObjects
		);

		EventTemplate template = new EventTemplate(
			"tpl-lifecycle-test",
			"Pulse Snapshot",
			"Pulse",
			0.35,
			0.8f,
			Map.of("actionMode", "ANIMATE", "animationType", "Pulse", "energy", 0.8f, "durationSeconds", 0.35)
		);
		templateId = template.id();
		EventTemplateStore.add(template);
	}

	@AfterEach
	void tearDown() {
		EventTemplateStore.resetForTests();
	}

	@Test
	void multiTargetWriteIsOneUndoAndSelectsAllCreatedEvents() {
		SelectionState selection = editor.getSelectionState();
		selection.selectEvent("stale");
		selection.selectClip("stale-clip");

		EventTemplate template = EventTemplateStore.find(templateId).orElseThrow();
		int written = EventLibraryPanelPresenter.writeTemplateEvents(
			timeline,
			editor,
			template,
			12.5,
			List.of("a", "b", "c")
		);

		assertEquals(3, written);
		assertEquals(3, timeline.getBlockAnimationEvents().size());
		assertEquals(1, commandManager.undoCount());
		assertEquals(3, selection.getSelectedEvents().size());
		assertTrue(selection.getSelectedClips().isEmpty());
		assertFalse(selection.isEventSelected("stale"));

		commandManager.undo();
		assertEquals(0, timeline.getBlockAnimationEvents().size());
		assertEquals(0, commandManager.undoCount());
	}

	@Test
	void applyTemplateWithSoleStageObjectBindsAndSelects() {
		stageObjects.register(StageObjectSystem.fromBlocks(
			"solo-stage", "Solo", List.of(new BlockPos(0, 64, 0))));

		var outcome = presenter.applyTemplate(templateId);

		assertTrue(outcome.success());
		assertEquals(1, timeline.getBlockAnimationEvents().size());
		assertEquals("solo-stage", timeline.getBlockAnimationEvents().getFirst().getTargetObjectId());
		assertEquals(1, editor.getSelectionState().getSelectedEvents().size());
		assertEquals(1, commandManager.undoCount());
		assertEquals(BBTexts.get("beatblock.event_library.applied", "Pulse Snapshot"), outcome.message());
	}

	@Test
	void applyTemplateUnboundWhenNoStageObjects() {
		var outcome = presenter.applyTemplate(templateId);

		assertTrue(outcome.success());
		assertEquals(1, timeline.getBlockAnimationEvents().size());
		assertEquals("", timeline.getBlockAnimationEvents().getFirst().getTargetObjectId());
		assertEquals(
			BBTexts.get("beatblock.event_library.applied_unbound", "Pulse Snapshot"),
			outcome.message()
		);
	}

	@Test
	void applyMissingTemplateFails() {
		var outcome = presenter.applyTemplate("missing-template-id");
		assertFalse(outcome.success());
		assertEquals(BBTexts.get("beatblock.event_library.template_missing"), outcome.message());
		assertEquals(0, commandManager.undoCount());
	}

	@Test
	void saveFromSelectionRequiresAnimationEvent() {
		var outcome = presenter.saveFromSelection("Nope");
		assertFalse(outcome.success());
		assertEquals(BBTexts.get("beatblock.event_library.no_selection"), outcome.message());

		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 10.0);
		var event = TimelineOperations.addEvent(
			clip, 1.0, EventType.ANIMATION,
			Map.of("animationType", "Pulse", "targetObject", "stage", "durationSeconds", 0.35)
		);
		editor.getSelectionState().selectEvent(event.getId());

		var saved = presenter.saveFromSelection("Saved Pulse");
		assertTrue(saved.success());
		assertTrue(EventTemplateStore.all().stream().anyMatch(t -> "Saved Pulse".equals(t.name())));
	}
}
