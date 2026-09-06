package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineOperations;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackEngineTest {

	@Test
	void advancesOnlyDueEventsAndRewindResets() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(20);
		timeline.setMetadata("audioPath", "x.wav");
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String anim = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"s", "S", List.of(new BlockPos(0, 64, 0))));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"a", 1.0, 1.0, anim, "s", 1f,
			Map.of("animationType", anim, "targetObject", "s", "durationSeconds", 1.0)));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"b", 5.0, 1.0, anim, "s", 1f,
			Map.of("animationType", anim, "targetObject", "s", "durationSeconds", 1.0)));

		CompiledTimelineSnapshot program = TimelineCompiler.compile(timeline, engine, null);
		PlaybackEngine pe = new PlaybackEngine();
		pe.load(program);

		AtomicInteger stageHits = new AtomicInteger();
		pe.advance(0.5, (c, e) -> stageHits.incrementAndGet(), null);
		assertEquals(0, stageHits.get());

		pe.advance(1.5, (c, e) -> stageHits.incrementAndGet(), null);
		assertEquals(1, stageHits.get());

		pe.advance(6.0, (c, e) -> stageHits.incrementAndGet(), null);
		assertEquals(2, stageHits.get());

		// Rewind clears and re-fires
		pe.advance(0.0, (c, e) -> stageHits.incrementAndGet(), null);
		assertEquals(2, stageHits.get()); // no events at 0
		pe.advance(6.0, (c, e) -> stageHits.incrementAndGet(), null);
		assertEquals(4, stageHits.get());
	}

	@Test
	void formalPlayDoesNotObserveLiveDocumentEdits() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		timeline.setMetadata("audioPath", "x.wav");
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String anim = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"s", "S", List.of(new BlockPos(1, 64, 0))));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"only", 1.0, 1.0, anim, "s", 1f,
			Map.of("animationType", anim, "targetObject", "s", "durationSeconds", 1.0)));

		CompiledTimelineSnapshot program = TimelineCompiler.compile(timeline, engine, null);
		assertEquals(1, program.compiledStageEvents().size());

		// Mutate live document after compile
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"after", 2.0, 1.0, anim, "s", 1f,
			Map.of("animationType", anim, "targetObject", "s", "durationSeconds", 1.0)));
		assertTrue(timeline.getStageEvents().size() >= 2);

		PlaybackEngine pe = new PlaybackEngine();
		pe.load(program);
		List<Double> firedTimes = new ArrayList<>();
		pe.advance(5.0, (c, e) -> firedTimes.add(e.getTimeSeconds()), null);
		// Only the compiled event at t=1 fires — not the live-added t=2 event
		assertEquals(1, firedTimes.size());
		assertEquals(1.0, firedTimes.getFirst(), 1e-6);
	}

	@Test
	void globalEventsFireFromCompiledProgram() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		var track = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		var clip = TimelineOperations.addClip(track, 0, 10);
		TimelineOperations.addEvent(clip, 2.0, EventType.GLOBAL, Map.of("type", "LIGHTING", "name", "Flash"));

		CompiledTimelineSnapshot program = TimelineCompiler.compile(timeline, null, null);
		assertEquals(1, program.globalEvents().size());

		PlaybackEngine pe = new PlaybackEngine();
		pe.load(program);
		AtomicInteger globals = new AtomicInteger();
		pe.advance(3.0, null, g -> globals.incrementAndGet());
		assertEquals(1, globals.get());
		assertNotNull(program.globalEvents().getFirst().id());
		assertTrue(program.globalEvents().getFirst().typeName().contains("LIGHT"));
	}

	@Test
	void findCompiledStageById() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("audioPath", "x.wav");
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String anim = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"s", "S", List.of(new BlockPos(2, 64, 0))));
		// force stable id via clip event
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0, 5);
		var te = new com.beatblock.timeline.TimelineEvent(
			"stable-id", 1.0, EventType.ANIMATION,
			Map.of("animationType", anim, "targetObject", "s", "durationSeconds", 1.0));
		clip.addEvent(te);

		CompiledTimelineSnapshot program = TimelineCompiler.compile(timeline, engine, null);
		PlaybackEngine pe = new PlaybackEngine();
		pe.load(program);
		assertNotNull(pe.findCompiledStage("stable-id"));
	}
	@Test
	void identicalIdlessEventsUseCompiledSequenceAsSchedulingIdentity() {
		TimelineAnimationEvent first = new TimelineAnimationEvent(
			"", 1.0, 1.0, "Pulse", "tower", 1f, Map.of());
		TimelineAnimationEvent second = new TimelineAnimationEvent(
			"", 1.0, 1.0, "Pulse", "tower", 1f, Map.of());
		CompiledTimelineSnapshot program = new CompiledTimelineSnapshot(
			List.of(first, second),
			List.of(
				new CompiledStageEvent(first, null, null, 0L),
				new CompiledStageEvent(second, null, null, 1L)
			),
			new CompiledCameraTrack(List.of()),
			List.of(),
			List.of(),
			List.of(),
			CompiledAudioReference.empty(),
			new double[0],
			120.0,
			2.0,
			true,
			0,
			null
		);

		PlaybackEngine engine = new PlaybackEngine();
		engine.load(program);
		AtomicInteger hits = new AtomicInteger();
		engine.advance(1.0, (compiled, event) -> hits.incrementAndGet(), null);

		assertEquals(2, hits.get());
		assertEquals(2, engine.scheduledStageCount());
	}
	@Test
	void reconstructSeekReplaysStateButSkipsTransientEventsAndGlobals() {
		TimelineAnimationEvent transientEvent = new TimelineAnimationEvent(
			"transient", 1.0, 1.0, "Pulse", "tower", 1f,
			Map.of("actionMode", "ANIMATE"));
		TimelineAnimationEvent idempotentEvent = new TimelineAnimationEvent(
			"place", 1.0, 1.0, "Pulse", "tower", 1f,
			Map.of("actionMode", "PLACE"));
		TimelineAnimationEvent statefulEvent = new TimelineAnimationEvent(
			"build", 1.0, 1.0, "Pulse", "tower", 1f,
			Map.of("actionMode", "BUILD"));
		CompiledGlobalEvent global = new CompiledGlobalEvent(
			"flash", 1.0, new GlobalEventPayload.Generic("SCREEN_FLASH", "Flash", Map.of()));
		CompiledTimelineSnapshot program = new CompiledTimelineSnapshot(
			List.of(transientEvent, idempotentEvent, statefulEvent),
			List.of(
				new CompiledStageEvent(transientEvent, null, null, 0L),
				new CompiledStageEvent(idempotentEvent, null, null, 1L),
				new CompiledStageEvent(statefulEvent, null, null, 2L)
			),
			new CompiledCameraTrack(List.of()), List.of(), List.of(), List.of(global),
			CompiledAudioReference.empty(), new double[0], 120.0, 2.0, true, 0, null
		);

		PlaybackEngine engine = new PlaybackEngine();
		engine.load(program);
		List<String> replayed = new ArrayList<>();
		AtomicInteger globalHits = new AtomicInteger();
		engine.seek(2.0, SeekMode.RECONSTRUCT_STATE,
			(compiled, event) -> replayed.add(event.getEventId()),
			event -> globalHits.incrementAndGet());

		assertEquals(List.of("place", "build"), replayed);
		assertEquals(0, globalHits.get());
	}

	@Test
	void replayAllSeekExplicitlyReplaysTransientAndGlobalEvents() {
		TimelineAnimationEvent transientEvent = new TimelineAnimationEvent(
			"transient", 1.0, 1.0, "Pulse", "tower", 1f,
			Map.of("actionMode", "ANIMATE"));
		CompiledGlobalEvent global = new CompiledGlobalEvent(
			"flash", 1.0, new GlobalEventPayload.Generic("SCREEN_FLASH", "Flash", Map.of()));
		CompiledTimelineSnapshot program = new CompiledTimelineSnapshot(
			List.of(transientEvent), List.of(new CompiledStageEvent(transientEvent, null, null, 0L)),
			new CompiledCameraTrack(List.of()), List.of(), List.of(), List.of(global),
			CompiledAudioReference.empty(), new double[0], 120.0, 2.0, true, 0, null
		);

		PlaybackEngine engine = new PlaybackEngine();
		engine.load(program);
		AtomicInteger stageHits = new AtomicInteger();
		AtomicInteger globalHits = new AtomicInteger();
		engine.seek(2.0, SeekMode.REPLAY_ALL,
			(compiled, event) -> stageHits.incrementAndGet(),
			event -> globalHits.incrementAndGet());

		assertEquals(1, stageHits.get());
		assertEquals(1, globalHits.get());
	}
	@Test
	void semanticsPriorityIsEventThenPresetThenActionMode() {
		var preset = com.beatblock.engine.influence.BlockInfluencePreset
			.builder("stateful-animation", "Stateful Animation")
			.playbackSemantics(PlaybackSemantics.STATEFUL)
			.build();
		var definition = new com.beatblock.engine.AnimationDefinition(preset);
		TimelineAnimationEvent presetDriven = new TimelineAnimationEvent(
			"preset", 1.0, 1.0, "stateful-animation", "tower", 1f,
			Map.of("actionMode", "ANIMATE"));
		TimelineAnimationEvent explicitlyTransient = new TimelineAnimationEvent(
			"explicit", 1.0, 1.0, "stateful-animation", "tower", 1f,
			Map.of("actionMode", "ANIMATE", "playbackSemantics", "TRANSIENT"));
		TimelineAnimationEvent fallbackBuild = new TimelineAnimationEvent(
			"fallback", 1.0, 1.0, "unknown", "tower", 1f,
			Map.of("actionMode", "BUILD"));

		assertEquals(PlaybackSemantics.STATEFUL,
			new CompiledStageEvent(presetDriven, definition, null, 0L).semantics());
		assertEquals(PlaybackSemantics.TRANSIENT,
			new CompiledStageEvent(explicitlyTransient, definition, null, 1L).semantics());
		assertEquals(PlaybackSemantics.STATEFUL,
			new CompiledStageEvent(fallbackBuild, null, null, 2L).semantics());
	}

	@Test
	void reconstructSeekHonorsExplicitSemanticsForAnimateEvents() {
		TimelineAnimationEvent event = new TimelineAnimationEvent(
			"persistent-animation", 1.0, 1.0, "Rise", "tower", 1f,
			Map.of("actionMode", "ANIMATE", "playbackSemantics", "STATEFUL"));
		CompiledTimelineSnapshot program = new CompiledTimelineSnapshot(
			List.of(event), List.of(new CompiledStageEvent(event, null, null, 0L)),
			new CompiledCameraTrack(List.of()), List.of(), List.of(), List.of(),
			CompiledAudioReference.empty(), new double[0], 120.0, 2.0, true, 0, null
		);
		PlaybackEngine engine = new PlaybackEngine();
		engine.load(program);
		AtomicInteger hits = new AtomicInteger();

		engine.seek(2.0, SeekMode.RECONSTRUCT_STATE,
			(compiled, source) -> hits.incrementAndGet(), null);

		assertEquals(1, hits.get());
	}

	@Test
	void seekFromZeroToSixtyThenBackToThirtyReconstructsWithoutTransientReplay() {
		List<TimelineAnimationEvent> events = List.of(
			new TimelineAnimationEvent("state-10", 10.0, 1.0, "Place", "stage", 1f, Map.of("actionMode", "PLACE")),
			new TimelineAnimationEvent("fx-20", 20.0, 1.0, "Pulse", "stage", 1f, Map.of("actionMode", "ANIMATE")),
			new TimelineAnimationEvent("state-40", 40.0, 1.0, "Build", "stage", 1f, Map.of("actionMode", "BUILD")),
			new TimelineAnimationEvent("fx-50", 50.0, 1.0, "Pulse", "stage", 1f, Map.of("actionMode", "ANIMATE"))
		);
		List<CompiledStageEvent> compiled = new ArrayList<>();
		for (int i = 0; i < events.size(); i++) compiled.add(new CompiledStageEvent(events.get(i), null, null, i));
		CompiledTimelineSnapshot program = new CompiledTimelineSnapshot(
			events, compiled, new CompiledCameraTrack(List.of()), List.of(), List.of(), List.of(),
			CompiledAudioReference.empty(), new double[0], 120.0, 60.0, true, 0, null);
		PlaybackEngine engine = new PlaybackEngine();
		engine.load(program);
		List<String> dispatched = new ArrayList<>();

		engine.advance(60.0, (c, e) -> dispatched.add(e.getEventId()), null);
		assertEquals(List.of("state-10", "fx-20", "state-40", "fx-50"), dispatched);

		dispatched.clear();
		engine.seek(30.0, SeekMode.RECONSTRUCT_STATE,
			(c, e) -> dispatched.add(e.getEventId()), null);
		assertEquals(List.of("state-10"), dispatched);

		dispatched.clear();
		engine.advance(60.0, (c, e) -> dispatched.add(e.getEventId()), null);
		assertEquals(List.of("state-40", "fx-50"), dispatched);
	}

	@Test
	void reconstructSeekReplaysActiveTintAndFlashButNotParticlesOrExpiredEnvelopes() {
		List<CompiledGlobalEvent> globals = List.of(
			new CompiledGlobalEvent("tint-expired", 1.0,
				new GlobalEventPayload.ScreenTint("Old", 0.5, 1, 0, 0, 2.0)),
			new CompiledGlobalEvent("tint-10", 10.0,
				new GlobalEventPayload.ScreenTint("Warm", 0.7, 1, 1, 1, 10.0)),
			new CompiledGlobalEvent("burst-12", 12.0,
				new GlobalEventPayload.ParticleBurst("Poof", "poof", 0, 64, 0, 8, 0.5, 0.04)),
			new CompiledGlobalEvent("flash-14", 14.0,
				new GlobalEventPayload.ScreenFlash("Flash", 1, 1, 1, 2.0))
		);
		CompiledTimelineSnapshot program = new CompiledTimelineSnapshot(
			List.of(), List.of(), new CompiledCameraTrack(List.of()), List.of(), List.of(), globals,
			CompiledAudioReference.empty(), new double[0], 120.0, 60.0, true, 0, null);
		PlaybackEngine engine = new PlaybackEngine();
		engine.load(program);
		List<String> dispatched = new ArrayList<>();

		engine.seek(15.0, SeekMode.RECONSTRUCT_STATE, null, e -> dispatched.add(e.id()));

		assertEquals(List.of("tint-10", "flash-14"), dispatched);
		assertEquals(4, engine.scheduledGlobalCount());
	}

	@Test
	void reconstructSeekReplaysStickyWeatherAndSkipsPastParticleImpulse() {
		List<CompiledGlobalEvent> globals = List.of(
			new CompiledGlobalEvent("clear-1", 1.0,
				new GlobalEventPayload.LocalVisualWeather("Clear", "clear", 0.5)),
			new CompiledGlobalEvent("rain-8", 8.0,
				new GlobalEventPayload.LocalVisualWeather("Rain", "rain", 1.0)),
			new CompiledGlobalEvent("burst-10", 10.0,
				new GlobalEventPayload.ParticleBurst("Poof", "poof", 0, 64, 0, 4, 0.5, 0.04))
		);
		CompiledTimelineSnapshot program = new CompiledTimelineSnapshot(
			List.of(), List.of(), new CompiledCameraTrack(List.of()), List.of(), List.of(), globals,
			CompiledAudioReference.empty(), new double[0], 120.0, 60.0, true, 0, null);
		PlaybackEngine engine = new PlaybackEngine();
		engine.load(program);
		List<String> dispatched = new ArrayList<>();

		engine.seek(12.0, SeekMode.RECONSTRUCT_STATE, null, e -> dispatched.add(e.id()));

		// Continuous weather: both past cues are still "active" for reconstruct (LWW applied by handlers).
		assertEquals(List.of("clear-1", "rain-8"), dispatched);
		assertFalse(dispatched.contains("burst-10"));
		assertEquals(3, engine.scheduledGlobalCount());
	}

	@Test
	void reconstructSeekHandlesTenThousandEventsWithinBudget() {
		List<TimelineAnimationEvent> events = new ArrayList<>(10_000);
		List<CompiledStageEvent> compiled = new ArrayList<>(10_000);
		for (int i = 0; i < 10_000; i++) {
			TimelineAnimationEvent event = new TimelineAnimationEvent(
				"event-" + i, i / 100.0, 1.0, "Place", "stage", 1f, Map.of("actionMode", "PLACE"));
			events.add(event);
			compiled.add(new CompiledStageEvent(event, null, null, i));
		}
		CompiledTimelineSnapshot program = new CompiledTimelineSnapshot(
			events, compiled, new CompiledCameraTrack(List.of()), List.of(), List.of(), List.of(),
			CompiledAudioReference.empty(), new double[0], 120.0, 100.0, true, 0, null);
		PlaybackEngine engine = new PlaybackEngine();
		engine.load(program);
		AtomicInteger dispatched = new AtomicInteger();

		assertTimeout(Duration.ofSeconds(2), () -> engine.seek(
			100.0, SeekMode.RECONSTRUCT_STATE,
			(c, e) -> dispatched.incrementAndGet(), null));
		assertEquals(10_000, dispatched.get());
	}
}
