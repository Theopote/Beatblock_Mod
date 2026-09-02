package com.beatblock.timeline.playback;

import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.automap.choreography.ChoreographyVfx;
import com.beatblock.automap.choreography.ChoreographyVfxPayloadMapper;
import com.beatblock.automap.choreography.ChoreographyVfxPersistence;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.project.TimelineAnimationPersistence;
import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ParticleBurstPayloadRoundTripTest {

	@Test
	void mapperPreservesSpreadAndSpeed() {
		var vfx = new ChoreographyVfx.ParticleBurst(
			2.0, "burst", "minecraft:crit", CameraSubject.worldPosition(1, 2, 3),
			16, 4.0, 1.5, 0);

		GlobalEventPayload.ParticleBurst payload = assertInstanceOf(
			GlobalEventPayload.ParticleBurst.class,
			ChoreographyVfxPayloadMapper.toPayload(vfx));

		assertEquals(4.0, payload.spread(), 1e-9);
		assertEquals(1.5, payload.speed(), 1e-9);
		assertEquals(1.0, payload.x(), 1e-9);
		assertEquals(2.0, payload.y(), 1e-9);
		assertEquals(3.0, payload.z(), 1e-9);
	}

	@Test
	void codecRoundTripsSpreadAndSpeed() {
		GlobalEventPayload.ParticleBurst original = new GlobalEventPayload.ParticleBurst(
			"Hit", "minecraft:firework", 8, 64, -3, 20, 2.5, 0.8);

		Map<String, Object> encoded = GlobalEventPayloadCodec.encode(original);
		GlobalEventPayload decoded = GlobalEventPayloadCodec.decode(encoded);
		GlobalEventPayload.ParticleBurst payload = assertInstanceOf(
			GlobalEventPayload.ParticleBurst.class, decoded);

		assertEquals(original, payload);
		assertEquals(2.5, encoded.get("spread"));
		assertEquals(0.8, encoded.get("speed"));
	}

	@Test
	void codecDefaultsSpreadAndSpeedForLegacyMaps() {
		GlobalEventPayload.ParticleBurst payload = assertInstanceOf(
			GlobalEventPayload.ParticleBurst.class,
			GlobalEventPayloadCodec.decode(Map.of("type", "PARTICLE_BURST", "count", 3)));

		assertEquals(GlobalEventPayload.ParticleBurst.DEFAULT_SPREAD, payload.spread(), 1e-9);
		assertEquals(GlobalEventPayload.ParticleBurst.DEFAULT_SPEED, payload.speed(), 1e-9);
	}

	@Test
	void choreographyPersistenceRoundTripsSpreadAndSpeed() {
		var original = new ChoreographyVfx.ParticleBurst(
			1.0, "spark", "minecraft:crit", CameraSubject.allStageObjects(),
			12, 3.5, 1.25, 2);
		JsonArray json = ChoreographyVfxPersistence.toJson(List.of(original));

		ChoreographyVfx restored = ChoreographyVfxPersistence.fromJson(json).getFirst();
		ChoreographyVfx.ParticleBurst burst = assertInstanceOf(ChoreographyVfx.ParticleBurst.class, restored);

		assertEquals(3.5, burst.spread(), 1e-9);
		assertEquals(1.25, burst.speed(), 1e-9);
		assertEquals(2, burst.sectionIndex());
	}

	@Test
	void timelineGlobalTrackPersistenceRoundTripsSpreadAndSpeed() {
		Timeline timeline = Timeline.createDefault();
		timeline.addGlobalPayloadEvent(
			4.0,
			new GlobalEventPayload.ParticleBurst("Poof", "minecraft:poof", 0, 64, 0, 8, 1.0, 0.2),
			TimelineEventOrigin.MANUAL);

		JsonArray tracksJson = TimelineAnimationPersistence.toJson(timeline);
		Timeline restored = Timeline.createDefault();
		TimelineAnimationPersistence.loadInto(restored, tracksJson);

		var event = restored.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().getFirst().getEvents().getFirst();
		assertEquals(EventType.GLOBAL, event.getType());
		GlobalEventPayload.ParticleBurst payload = assertInstanceOf(
			GlobalEventPayload.ParticleBurst.class,
			GlobalEventPayloadCodec.decode(event.getParameters()));
		assertEquals(1.0, payload.spread(), 1e-9);
		assertEquals(0.2, payload.speed(), 1e-9);
	}

	@Test
	void fingerprintReflectsSpreadAndSpeed() {
		CompiledTimelineSnapshot base = snapshotWithParticle(0.5, 0.04);
		CompiledTimelineSnapshot wide = snapshotWithParticle(4.0, 1.5);

		assertNotEquals(
			CompiledProgramFingerprint.compute(base),
			CompiledProgramFingerprint.compute(wide));
	}

	private static CompiledTimelineSnapshot snapshotWithParticle(double spread, double speed) {
		return new CompiledTimelineSnapshot(
			List.of(),
			List.of(),
			new CompiledCameraTrack(List.of()),
			List.of(),
			List.of(),
			List.of(new CompiledGlobalEvent(
				"particle-1",
				2.0,
				new GlobalEventPayload.ParticleBurst("Burst", "minecraft:crit", 0, 64, 0, 10, spread, speed))),
			CompiledAudioReference.empty(),
			new double[0],
			120.0,
			60.0,
			true,
			1,
			null);
	}
}
