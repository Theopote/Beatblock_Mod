package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.audio.IAudioAnalyzer;
import com.beatblock.audio.assets.AudioAnalysisMode;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetStatus;
import com.beatblock.audio.beatmap.AnchorType;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapMeta;
import com.beatblock.audio.beatmap.MusicSection;
import com.beatblock.audio.beatmap.SectionLabel;
import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.rendering.TimelineAudioFeatureFillSupport;
import com.beatblock.ui.i18n.BBTexts;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class AudioAnalysisPanelPresenterTest {

	@Test
	void demucsToggleUsesInjectedAnalyzer() {
		AudioAnalysisService service = AudioAnalysisService.createForTesting();
		service.setUseDemucs(true);
		BeatBlockContext context = BeatBlockContext.builder()
			.externalAudioAnalyzer(service)
			.build();
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(() -> context);

		assertTrue(presenter.isUseDemucs());
		presenter.setUseDemucs(false);
		assertFalse(presenter.isUseDemucs());
		assertFalse(service.isUseDemucs());
	}

	@Test
	void analyzerUnavailableWhenContextHasNoService() {
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(
			() -> BeatBlockContext.builder().build()
		);
		assertFalse(presenter.isAnalyzerAvailable());
		assertNull(presenter.externalAnalyzer());
		assertEquals(0, presenter.activeAnalysisCount());
		assertNull(presenter.pythonRuntimeSummary());
		assertNull(presenter.runtimeHealthSnapshot());
		assertNull(presenter.backendAnalyzer());
	}

	@Test
	void setUseDemucsNoOpsWhenAnalyzerMissing() {
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(
			() -> BeatBlockContext.builder().build()
		);
		presenter.setUseDemucs(true);
		assertFalse(presenter.isUseDemucs());
	}

	@Test
	void exposesRuntimeDiagnosticsFromInjectedService() {
		AudioAnalysisService service = AudioAnalysisService.createForTesting();
		BeatBlockContext context = BeatBlockContext.builder()
			.externalAudioAnalyzer(service)
			.build();
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(() -> context);

		assertTrue(presenter.isAnalyzerAvailable());
		assertNotNull(presenter.pythonRuntimeSummary());
		PythonEnvironmentDiagnostics.RuntimeHealthSnapshot health = presenter.runtimeHealthSnapshot();
		assertNotNull(health);
		IAudioAnalyzer backend = presenter.backendAnalyzer();
		assertNotNull(backend);
		assertEquals(0, presenter.activeAnalysisCount());
	}

	@Test
	void applyToTimelineRequiresCompletedBeatmap() {
		BeatBlockContext context = BeatBlock.getContext();
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(() -> context);
		AudioAsset pending = new AudioAsset(null);
		pending.setStatus(AudioAssetStatus.PENDING);

		PresenterResult result = presenter.applyToTimeline(pending);
		assertFalse(result.ok());
		assertFalse(result.messageOrEmpty().isBlank());
	}

	@Test
	void applyToTimelineFailsWithoutEditor() {
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(
			() -> BeatBlockContext.builder().build()
		);
		AudioAsset asset = completedAsset();

		PresenterResult result = presenter.applyToTimeline(asset);
		assertFalse(result.ok());
		assertFalse(result.messageOrEmpty().isBlank());
	}

	@Test
	void applyToTimelineConnectsAssetAndSeedsChoreographyPlan() {
		BeatBlockContext context = BeatBlock.getContext();
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(() -> context);
		AudioAsset asset = completedAsset();

		PresenterResult result = presenter.applyToTimeline(asset);
		assertTrue(result.ok(), result.messageOrEmpty());
		assertFalse(result.messageOrEmpty().isBlank());

		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(context.timeline());
		assertNotNull(plan);
		assertEquals(2, plan.sections().size());
		assertFalse(plan.musicalStructure().phrases().isEmpty());
	}

	@Test
	void clearCacheAndReanalyzeMarksTimelineAwaitingWhenAssetIsActive() {
		BeatBlockContext context = BeatBlock.getContext();
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(() -> context);
		AudioAsset asset = completedAsset();

		assertTrue(presenter.applyToTimeline(asset).ok());
		assertNull(context.timeline().getMetadata("awaitingAnalyzedBeatmap"));

		String result = presenter.clearCacheAndReanalyze(asset, AudioAnalysisMode.BASIC);
		assertTrue(
			result.contains(BBTexts.get("beatblock.audio.reanalyze_will_apply_timeline")),
			result
		);
		Object awaiting = context.timeline().getMetadata("awaitingAnalyzedBeatmap");
		assertNotNull(awaiting);
		assertEquals(
			TimelineAudioFeatureFillSupport.buildAudioAssetKey(asset),
			TimelineAudioFeatureFillSupport.normalizeAudioPath(awaiting.toString())
		);
	}

	@Test
	void clearCacheAndReanalyzeDoesNotMarkAwaitingForUnrelatedAsset() {
		BeatBlockContext context = BeatBlock.getContext();
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(() -> context);
		AudioAsset onTimeline = completedAsset();
		AudioAsset other = completedAsset(Path.of("other.wav"));

		assertTrue(presenter.applyToTimeline(onTimeline).ok());
		presenter.clearCacheAndReanalyze(other, AudioAnalysisMode.BASIC);

		assertNull(context.timeline().getMetadata("awaitingAnalyzedBeatmap"));
	}

	@Test
	void reviewStructureAppliesWhenNotOnTimeline() {
		BeatBlockContext context = BeatBlock.getContext();
		AudioAnalysisPanelPresenter presenter = new AudioAnalysisPanelPresenter(() -> context);
		AudioAsset asset = completedAsset();

		PresenterResult result = presenter.reviewStructureInTimeline(asset);
		assertTrue(result.ok(), result.messageOrEmpty());
		assertTrue(context.timelineEditor().isActiveTimelineAudio(asset));
	}

	private static AudioAsset completedAsset() {
		return completedAsset(Path.of("song.wav"));
	}

	private static AudioAsset completedAsset(Path path) {
		AudioAsset asset = new AudioAsset(path);
		asset.setStatus(AudioAssetStatus.COMPLETED);
		asset.setBpm(120f);
		asset.setSectionCount(2);
		asset.setBeatmap(new Beatmap(
			1,
			new BeatmapMeta(path.getFileName().toString(), 16000, 120, 1.0, "4/4", 44100, "", "", null, null, null),
			List.of(
				new BeatEvent(0, "kick", 0.8f, AnchorType.ARRIVE, 0, 0, 0),
				new BeatEvent(500, "snare", 0.7f, AnchorType.ARRIVE, 1, 0, 1)
			),
			List.of(
				new MusicSection(0, 8000, SectionLabel.INTRO, 0.3f),
				new MusicSection(8000, 16000, SectionLabel.CHORUS, 0.8f)
			),
			null,
			null
		));
		return asset;
	}
}
