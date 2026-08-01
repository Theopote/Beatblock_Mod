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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
