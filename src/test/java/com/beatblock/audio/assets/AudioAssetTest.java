package com.beatblock.audio.assets;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioAssetTest {

	@Test
	void rootPathHasSafeEmptyFileName() {
		AudioAsset asset = new AudioAsset(Path.of("/"));
		assertEquals("", asset.getFileName());
	}

	@Test
	void finishedStepsSnapshotCannotMutateAsset() {
		AudioAsset asset = new AudioAsset(Path.of("song.mp3"));
		asset.markStepFinished(AudioAnalysisStep.BPM_DETECTION);

		var snapshot = asset.getFinishedSteps();
		snapshot.clear();

		assertTrue(asset.getFinishedSteps().contains(AudioAnalysisStep.BPM_DETECTION));
		assertFalse(asset.getFinishedSteps().isEmpty());
	}
}
