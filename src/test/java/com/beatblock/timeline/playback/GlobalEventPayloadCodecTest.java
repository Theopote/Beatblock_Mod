package com.beatblock.timeline.playback;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalEventPayloadCodecTest {

	@Test
	void decodesEveryKnownGlobalPayloadType() {
		assertInstanceOf(GlobalEventPayload.EnvironmentLighting.class,
			GlobalEventPayloadCodec.decode(Map.of("type", "lighting", "intensity", 0.5)));
		assertInstanceOf(GlobalEventPayload.ScreenTint.class,
			GlobalEventPayloadCodec.decode(Map.of("type", "screen_tint", "intensity", 0.5)));
		assertInstanceOf(GlobalEventPayload.Weather.class,
			GlobalEventPayloadCodec.decode(Map.of("type", "WEATHER", "weatherType", "rain")));
		assertInstanceOf(GlobalEventPayload.ParticleBurst.class,
			GlobalEventPayloadCodec.decode(Map.of("type", "particle", "count", 4)));
		assertInstanceOf(GlobalEventPayload.ScreenFlash.class,
			GlobalEventPayloadCodec.decode(Map.of("type", "screen-flash", "durationSeconds", 0.2)));
		assertInstanceOf(GlobalEventPayload.AudioMix.class,
			GlobalEventPayloadCodec.decode(Map.of("type", "audio mix", "volume", 0.8)));
		assertInstanceOf(GlobalEventPayload.Generic.class,
			GlobalEventPayloadCodec.decode(Map.of("type", "CUSTOM")));
	}

	@Test
	void compilerUsesTypedPayload() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		var clip = TimelineOperations.addClip(track, 0, 5);
		TimelineOperations.addEvent(clip, 1.0, EventType.GLOBAL, Map.of(
			"type", "LIGHTING", "name", "Key light", "intensity", 0.75,
			"r", 1.0, "g", 0.5, "b", 0.25, "durationSeconds", 2.0));

		CompiledGlobalEvent event = TimelineCompiler.compile(timeline).globalEvents().getFirst();
		GlobalEventPayload.EnvironmentLighting payload = assertInstanceOf(
			GlobalEventPayload.EnvironmentLighting.class, event.payload());
		assertEquals(0.75, payload.intensity(), 1e-9);
		assertEquals(2.0, payload.durationSeconds(), 1e-9);
		assertEquals("Key light", event.name());
	}

	@Test
	void validatorAndCompilerShareMalformedPayloadRules() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		var clip = TimelineOperations.addClip(track, 0, 5);
		TimelineOperations.addEvent(clip, 1.0, EventType.GLOBAL, Map.of(
			"type", "LIGHTING", "intensity", Double.NaN));

		TimelineValidationReport report = TimelineValidator.validate(timeline, null);
		assertTrue(report.hasFatalErrors());
		assertTrue(report.problems().stream()
			.anyMatch(d -> TimelineValidator.RULE_INVALID_GLOBAL_PAYLOAD.equals(d.ruleId())));
		assertThrows(TimelineCompilationException.class, () -> TimelineCompiler.compile(timeline));
	}

	@Test
	void compiledGlobalEventRejectsInvalidTimeInsteadOfClamping() {
		GlobalEventPayload payload = new GlobalEventPayload.Generic("SPECIAL", "", Map.of());
		assertThrows(IllegalArgumentException.class,
			() -> new CompiledGlobalEvent("negative", -2.0, payload));
		assertThrows(IllegalArgumentException.class,
			() -> new CompiledGlobalEvent("nan", Double.NaN, payload));
	}

	@Test
	void compilerReportsNegativeGlobalTimeAsFatal() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		var clip = TimelineOperations.addClip(track, 0.0, 1.0);
		clip.addEvent(new com.beatblock.timeline.TimelineEvent(
			"negative-global", 0.0, EventType.GLOBAL,
			Map.of("type", "LIGHTING", "name", "Too early", "intensity", 1.0)) {
			@Override public double getTimeSeconds() { return -2.0; }
		});

		TimelineValidationReport report = TimelineValidator.validate(timeline, null);
		assertTrue(report.hasFatalErrors());
		assertTrue(report.problems().stream()
			.anyMatch(d -> "negative_global_time".equals(d.ruleId()) && d.timeSeconds() == -2.0));
		TimelineCompilationException error = assertThrows(TimelineCompilationException.class,
			() -> TimelineCompiler.compile(timeline, null, null, CompilePolicy.SKIP_INVALID_EVENTS));
		assertTrue(error.report() != null);
		assertTrue(error.getMessage().contains("ruleId=negative_global_time"));
	}

	@Test
	void unknownGlobalTypeProducesVisibleValidationWarning() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		TimelineOperations.addEvent(clip, 1.0, EventType.GLOBAL,
			Map.of("type", "PLUGIN_NOT_INSTALLED", "name", "Extension cue"));

		TimelineValidationReport report = TimelineValidator.validate(timeline, null);
		assertTrue(report.problems().stream().anyMatch(d ->
			TimelineValidator.RULE_UNKNOWN_GLOBAL_EVENT.equals(d.ruleId())
				&& d.severity() == TimelineDiagnosticSeverity.WARNING));
		assertEquals(1, TimelineCompiler.compile(timeline).globalEvents().size());
	}
}
