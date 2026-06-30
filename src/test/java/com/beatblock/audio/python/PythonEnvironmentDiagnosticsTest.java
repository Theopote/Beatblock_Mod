package com.beatblock.audio.python;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
	void explainPythonErrorDetectsWinError32() {
		String hint = PythonEnvironmentDiagnostics.explainPythonError("OSError: [WinError 32] file in use");
		assertFalse(hint.isBlank());
		assertTrue(hint.contains("WinError 32") || hint.contains("占用"));
	}

	@Test
	void readRequirementPackagesSkipsComments() throws Exception {
		Path file = java.nio.file.Files.createTempFile("req", ".txt");
		java.nio.file.Files.writeString(file, """
			librosa>=0.10.1
			# comment
			numpy>=1.26
			""");
		var pkgs = PythonEnvironmentDiagnostics.readRequirementPackages(file);
		assertEquals(2, pkgs.size());
		assertTrue(pkgs.contains("librosa>=0.10.1"));
	}
}
