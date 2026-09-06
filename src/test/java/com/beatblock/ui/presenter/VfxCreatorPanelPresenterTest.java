package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.automap.camera.CameraSubjectKind;
import com.beatblock.automap.vfx.GlobalEffectKind;
import com.beatblock.automap.vfx.VfxEffectCategory;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.playback.GlobalEventPayloadCodec;
import com.beatblock.timeline.util.MusicalDuration;
import com.beatblock.timeline.util.MusicalDurationUnit;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class VfxCreatorPanelPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private StageObjectSystem stageObjects;
	private BuildLayerManager layers;
	private final AtomicReference<Optional<Vec3d>> crosshair = new AtomicReference<>(Optional.empty());
	private VfxCreatorPanelPresenter presenter;

	@BeforeEach
	void setUp() {
		var context = BeatBlock.getContext();
		timeline = context.timeline();
		editor = context.timelineEditor();
		editor.getCommandManager().clear();
		editor.getSelectionState().clearAll();
		timeline.setDurationSeconds(60.0);
		timeline.setMetadata("bpm", 120.0);
		editor.getClock().setDurationSeconds(60.0);
		editor.getClock().setCurrentTimeSeconds(4.0);
		var global = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		if (global != null) {
			List.copyOf(global.getClips()).forEach(c -> global.removeClip(c.getId()));
		}
		layers = context.buildLayerManager();
		layers.purgeAllLayers();
		stageObjects = context.blockAnimationEngine().getStageObjectSystem();
		stageObjects.clear();
		var layer = layers.createFromSelection(
			"Main Building",
			List.of(new BlockPos(2, 64, 4), new BlockPos(3, 64, 4)));
		assertTrue(layer != null);

		crosshair.set(Optional.empty());
		presenter = new VfxCreatorPanelPresenter(
			() -> timeline,
			() -> editor,
			() -> stageObjects,
			() -> layers,
			crosshair::get
		);
	}

	@Test
	void setCategorySelectsDefaultKindForTab() {
		presenter.setCategory(VfxEffectCategory.ENVIRONMENT_LIGHTING);
		assertEquals(GlobalEffectKind.ENVIRONMENT_LIGHTING, presenter.viewState().kind());
		assertEquals(VfxEffectCategory.ENVIRONMENT_LIGHTING, presenter.viewState().category());

		presenter.setCategory(VfxEffectCategory.PARTICLES);
		assertEquals(GlobalEffectKind.PARTICLE_BURST, presenter.viewState().kind());
	}

	@Test
	void insertUsesMusicalDurationSecondsOnScreenTint() {
		presenter.setCategory(VfxEffectCategory.SCREEN_TINT);
		presenter.setKind(GlobalEffectKind.SCREEN_TINT);
		double expectedSeconds = MusicalDuration.beats(2).toSeconds(120.0);
		presenter.setDurationSeconds(expectedSeconds);
		presenter.setDurationUnit(MusicalDurationUnit.BEATS);

		assertTrue(presenter.insertAtPlayhead().success());

		var event = timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().getFirst().getEvents().stream()
			.filter(e -> e.getType() == EventType.GLOBAL)
			.findFirst()
			.orElseThrow();
		GlobalEventPayload.ScreenTint tint = assertInstanceOf(
			GlobalEventPayload.ScreenTint.class,
			GlobalEventPayloadCodec.decode(event.getParameters()));
		assertEquals(expectedSeconds, tint.durationSeconds(), 1e-6);
	}

	@Test
	void particlePositionUsesSelectedStageObjectCenterLabel() {
		layers.setSelectionTo(layers.getLayerOrderIds().getFirst());
		presenter.setCategory(VfxEffectCategory.PARTICLES);

		var state = presenter.viewState();
		assertEquals(
			BBTexts.get("beatblock.vfx_creator.position.center", "Main Building"),
			state.particlePositionLabel());
		assertFalse(state.particlePositionManual());

		assertTrue(presenter.insertAtPlayhead().success());
		var event = timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().getFirst().getEvents().stream()
			.filter(e -> e.getType() == EventType.GLOBAL)
			.findFirst()
			.orElseThrow();
		GlobalEventPayload.ParticleBurst burst = assertInstanceOf(
			GlobalEventPayload.ParticleBurst.class,
			GlobalEventPayloadCodec.decode(event.getParameters()));
		assertEquals(CameraSubjectKind.STAGE_OBJECT, burst.followSubjectKind());
		assertEquals(layers.getSelectedStageObjectIds().getFirst(), burst.followSubjectRef());
	}

	@Test
	void applyStormPresetIsOneUndo() {
		assertTrue(presenter.applyPreset("storm").success());
		assertEquals(1, editor.getCommandManager().undoCount());
		assertEquals(3, timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().size());
		assertEquals(3, editor.getSelectionState().getSelectedEvents().size());

		editor.getCommandManager().undo();
		assertEquals(0, timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().size());
	}

	@Test
	void particlePositionFallsBackToCrosshairThenManual() {
		layers.clearSelection();
		crosshair.set(Optional.of(new Vec3d(10, 70, -5)));
		presenter.setCategory(VfxEffectCategory.PARTICLES);

		var state = presenter.viewState();
		assertEquals(BBTexts.get("beatblock.vfx_creator.position.crosshair"), state.particlePositionLabel());
		assertEquals(10.0, state.particleX(), 1e-6);

		crosshair.set(Optional.empty());
		presenter.refreshParticlePositionFromSelection();
		assertTrue(presenter.insertAtPlayhead().success());

		presenter.enableManualParticlePosition();
		presenter.setParticlePosition(9, 70, -1);
		assertTrue(presenter.insertAtPlayhead().success());

		var events = timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().stream()
			.flatMap(c -> c.getEvents().stream())
			.filter(e -> e.getType() == EventType.GLOBAL)
			.toList();
		GlobalEventPayload.ParticleBurst manual = assertInstanceOf(
			GlobalEventPayload.ParticleBurst.class,
			GlobalEventPayloadCodec.decode(events.getLast().getParameters()));
		assertNull(manual.followSubjectKind());
		assertEquals(9.0, manual.x(), 1e-6);
		assertEquals(70.0, manual.y(), 1e-6);
		assertEquals(-1.0, manual.z(), 1e-6);
	}
}
