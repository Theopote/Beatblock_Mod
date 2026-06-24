package com.beatblock.ui.presenter;

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
}
