package com.beatblock.audio.python;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonEnvironmentDiagnosticsTest {

	@Test
	void explainPythonErrorDetectsDemucsMissing() {
		String hint = PythonEnvironmentDiagnostics.explainPythonError("ModuleNotFoundError: No module named demucs");
		assertFalse(hint.isBlank());
		assertTrue(hint.contains("Demucs"));
	}

	@Test
	void looksLikeDemucsMissingMatchesTorchErrors() {
		assertTrue(PythonEnvironmentDiagnostics.looksLikeDemucsMissing("No module named torch"));
		assertFalse(PythonEnvironmentDiagnostics.looksLikeDemucsMissing("disk full"));
	}
}
