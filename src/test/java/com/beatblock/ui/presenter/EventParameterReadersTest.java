package com.beatblock.ui.presenter;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventParameterReadersTest {

	@Test
	void readsStringAndNumericParameters() {
		Map<String, Object> params = Map.of(
			"name", "pulse",
			"energy", 0.75,
			"missing", "fallback"
		);
		assertEquals("pulse", EventParameterReaders.stringParam(params, "name"));
		assertEquals("", EventParameterReaders.stringParam(params, "absent"));
		assertEquals("fallback", EventParameterReaders.stringParam(params, "absent", "fallback"));
		assertEquals(0.75, EventParameterReaders.numericParam(params, "energy", 0.0), 1e-9);
	}

	@Test
	void readsBooleanParametersFromMultipleRepresentations() {
		assertTrue(EventParameterReaders.booleanParam(Map.of("flag", true), "flag", false));
		assertTrue(EventParameterReaders.booleanParam(Map.of("flag", 1), "flag", false));
		assertTrue(EventParameterReaders.booleanParam(Map.of("flag", "yes"), "flag", false));
		assertFalse(EventParameterReaders.booleanParam(Map.of("flag", "no"), "flag", true));
		assertTrue(EventParameterReaders.booleanParam(Map.of(), "flag", true));
	}

	@Test
	void animationParamsReadsCoreFieldsFromMapOrEvent() {
		Map<String, Object> params = Map.of(
			"mode", "BUILD",
			"animationType", "build",
			"targetObject", "stage",
			"energy", 0.5,
			"durationSeconds", 2.0,
			"eventOrigin", TimelineEventOrigin.AUTO_GENERATED.name()
		);
		var fromMap = EventParameterReaders.animationParams(params);
		assertEquals(TimelineAnimationActionMode.BUILD, fromMap.actionMode());
		assertEquals("build", fromMap.animationType());

		var event = new TimelineAnimationEvent("ev", 1.0, 2.0, "pulse", "stage", 1f, Map.of("mode", "ANIMATE"));
		var fromEvent = EventParameterReaders.animationParams(event);
		assertEquals(TimelineAnimationActionMode.ANIMATE, fromEvent.actionMode());
		assertEquals("pulse", fromEvent.animationType());
	}
}
