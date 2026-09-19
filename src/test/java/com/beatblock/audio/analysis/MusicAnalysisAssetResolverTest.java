package com.beatblock.audio.analysis;

import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetStatus;
import com.beatblock.audio.beatmap.Beatmap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicAnalysisAssetResolverTest {

	@Test
	void resolvesFromBeatmapWhenEngineHasNoLastTimeline() {
		Path path = Path.of("F:/tmp/music-analysis-test.wav").toAbsolutePath().normalize();
		AudioAsset asset = new AudioAsset(path);
		Beatmap beatmap = BeatmapFeatureTimelineAdapterTest.sampleBeatmap();
		asset.setBeatmap(beatmap);
		asset.setStatus(AudioAssetStatus.COMPLETED);

		AudioAnalysisEngine engine = new AudioAnalysisEngine();
		MusicAnalysisAsset resolved = MusicAnalysisAssetResolver.resolve(asset, engine);

		assertNotNull(resolved);
		assertTrue(resolved.isUsable());
		assertEquals(MusicAnalysisAsset.Source.PYTHON_BEATMAP, resolved.source());
		assertNotNull(asset.getFeatureTimeline());
		assertNotNull(engine.getLastFeatureTimeline());
		assertEquals(16.0, resolved.durationSeconds(), 1e-6);
	}

	@Test
	void prefersEngineTimelineWhenNoAssetBound() {
		AudioAnalysisEngine engine = new AudioAnalysisEngine();
		engine.bindLastFeatureTimeline(new AudioFeatureTimeline(
			8.0,
			List.of(new DetectedBeat(1.0, 0.8f)),
			List.of(new EnergyFrame(0.0, 0.2f)),
			List.of(new FrequencyBands(1.0, 0.8f, 0.1f, 0.1f)),
			new WaveformExtractor.WaveformFrame[0],
			128f,
			null
		));

		MusicAnalysisAsset resolved = MusicAnalysisAssetResolver.resolve((AudioAsset) null, engine);

		assertNotNull(resolved);
		assertEquals(MusicAnalysisAsset.Source.JAVA_FEATURE, resolved.source());
		assertEquals(8.0, resolved.durationSeconds(), 1e-6);
	}
}
