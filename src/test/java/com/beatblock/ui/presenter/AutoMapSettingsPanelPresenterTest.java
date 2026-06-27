package com.beatblock.ui.presenter;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.EnergyFrame;
import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class AutoMapSettingsPanelPresenterTest {

	@Test
	void generateBlockedWithoutFeatureTimeline() {
		Timeline timeline = Timeline.createDefault();
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.audioAnalysisEngine(new AudioAnalysisEngine())
			.build();
		AutoMapSettingsPanelPresenter presenter = new AutoMapSettingsPanelPresenter(() -> context);

		assertFalse(presenter.canGenerate());
		var outcome = presenter.generate(new AutoMapSettings());
		assertFalse(outcome.result().ok());
		assertNotNull(outcome.result().messageOrEmpty());
	}

	@Test
	void generateRunsWhenFeatureTimelinePresent() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timeline);
		AudioAnalysisEngine engine = new AudioAnalysisEngine();
		engine.bindLastFeatureTimeline(minimalFeatureTimeline());

		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.audioAnalysisEngine(engine)
			.build();
		AutoMapSettingsPanelPresenter presenter = new AutoMapSettingsPanelPresenter(() -> context);

		assertTrue(presenter.canGenerate());
		var outcome = presenter.generate(new AutoMapSettings());
		assertTrue(outcome.result().ok());
		assertNotNull(outcome.autoMapResult());
	}

	private static AudioFeatureTimeline minimalFeatureTimeline() {
		return new AudioFeatureTimeline(
			16.0,
			List.of(new DetectedBeat(1.0, 0.8f), new DetectedBeat(2.0, 0.7f)),
			List.of(new EnergyFrame(0.0, 0.2f), new EnergyFrame(8.0, 0.9f)),
			List.of(new FrequencyBands(1.0, 0.8f, 0.1f, 0.1f)),
			new com.beatblock.audio.analysis.WaveformExtractor.WaveformFrame[0],
			120f,
			null
		);
	}
}
