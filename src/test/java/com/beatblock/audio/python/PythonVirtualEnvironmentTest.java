package com.beatblock.audio.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PythonVirtualEnvironmentTest {

	@TempDir
	Path tempDir;

	@Test
	void venvDirectoryIsUnderAnalyzerFolder() {
		Path configDir = tempDir.resolve("beatblock");
		Path venvDir = PythonVirtualEnvironment.venvDirectory(configDir);
		assertEquals(configDir.resolve("analyzer").resolve(".venv"), venvDir);
	}

	@Test
	void venvPythonExecutableMissingWhenNotCreated() {
		Path configDir = tempDir.resolve("beatblock");
		assertNull(PythonVirtualEnvironment.venvPythonExecutable(configDir));
	}
}
