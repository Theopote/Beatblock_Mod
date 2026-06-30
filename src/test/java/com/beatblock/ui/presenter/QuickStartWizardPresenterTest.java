package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.EnergyFrame;
import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.test.BeatBlockTestSupport;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickStartWizardPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private QuickStartWizardPresenter presenter;
	private final AudioAssetManager manager = AudioAssetManager.getInstance();

	@BeforeEach
	void setUp() {
		BeatBlock.installContext(BeatBlockTestSupport.minimalContext());
		var context = BeatBlock.getContext();
		timeline = context.timeline();
		editor = context.timelineEditor();
		manager.bindContext(BeatBlock::getContext);
		presenter = new QuickStartWizardPresenter(
			new AutoMapSettingsPanelPresenter(BeatBlock::getContext),
			PresenterFactories.toolPanelPresenter(context),
			PresenterFactories.rhythmDropPanelPresenter(context),
			() -> timeline,
			() -> editor
		);
	}

	@AfterEach
	void tearDown() {
		for (AudioAsset asset : new ArrayList<>(manager.getAssets())) {
			manager.remove(asset.getId());
		}
		BeatBlock.resetContext();
	}

	@Test
	void importMusicRejectsEmptyPath() {
		var result = presenter.importMusic("  ");
		assertFalse(result.ok());
		assertEquals(QuickStartWizardPresenter.Step.IMPORT, presenter.step());
	}

	@Test
	void importMusicRejectsUnsupportedExtension() {
		var result = presenter.importMusic("C:/music/track.txt");
		assertFalse(result.ok());
		assertEquals(QuickStartWizardPresenter.Step.IMPORT, presenter.step());
	}

	@Test
	void importMusicAcceptsMp3AndStartsAnalysis(@TempDir Path tempDir) throws Exception {
		Path mp3 = tempDir.resolve("demo.mp3");
		Files.write(mp3, new byte[] {0x49, 0x44, 0x33, 0x03});

		var result = presenter.importMusic(mp3.toString());
		assertTrue(result.ok());
		assertEquals(QuickStartWizardPresenter.Step.CHOOSE_TYPE, presenter.step());
		assertEquals(mp3.toAbsolutePath().normalize().toString(), timeline.getMetadata("audioPath"));
		assertEquals(1, manager.getAssets().size());
	}

	@Test
	void prepareOpenSkipsImportWhenMusicAlreadyLoaded(@TempDir Path tempDir) throws Exception {
		Path mp3 = tempDir.resolve("existing.mp3");
		Files.write(mp3, new byte[] {0x49, 0x44, 0x33, 0x03});
		timeline.setMetadata("audioPath", mp3.toAbsolutePath().normalize().toString());

		var session = presenter.prepareOpen();

		assertTrue(session.skippedImport());
		assertEquals(mp3.toAbsolutePath().normalize().toString(), session.audioPath());
		assertEquals(QuickStartWizardPresenter.Step.CHOOSE_TYPE, presenter.step());
	}

	@Test
	void advanceFromSelectStepRequiresSelection() {
		presenter.goToStep(QuickStartWizardPresenter.Step.SELECT_BLOCKS);
		presenter.advanceFromSelectStep();
		assertEquals(QuickStartWizardPresenter.Step.SELECT_BLOCKS, presenter.step());
	}

	@Test
	void canGenerateRequiresAnalysisReady() {
		presenter.setCreationType(QuickStartWizardPresenter.CreationType.BUILD_APPEARANCE);
		assertFalse(presenter.canGenerate());

		AudioAnalysisEngine engine = BeatBlock.getContext().audioAnalysisEngine();
		engine.bindLastFeatureTimeline(minimalFeatureTimeline());
		assertFalse(presenter.canGenerate());
	}

	@Test
	void canGenerateWhenAnalysisReadyAndSelectionPresent() {
		AudioAnalysisEngine engine = BeatBlock.getContext().audioAnalysisEngine();
		engine.bindLastFeatureTimeline(minimalFeatureTimeline());
		engine.fillTimelineFromFeature(timeline, minimalFeatureTimeline(), 44100);

		presenter.goToStep(QuickStartWizardPresenter.Step.GENERATE);
		assertTrue(presenter.isAnalysisReady());
	}

	@Test
	void blockFallAnalysisRequiresBeatGrid() {
		presenter.setCreationType(QuickStartWizardPresenter.CreationType.BLOCK_FALL);
		assertFalse(presenter.isAnalysisReady());

		AudioAnalysisEngine engine = BeatBlock.getContext().audioAnalysisEngine();
		engine.fillTimelineFromFeature(timeline, minimalFeatureTimeline(), 44100);
		assertTrue(presenter.isAnalysisReady());
	}

	@Test
	void generateFailsWhenAnalysisNotReady() {
		var outcome = presenter.generate();
		assertFalse(outcome.result().ok());
	}

	@Test
	void indexForCreationTypeMatchesComboOrder() {
		assertEquals(0, presenter.indexForCreationType(QuickStartWizardPresenter.CreationType.BUILD_APPEARANCE));
		assertEquals(1, presenter.indexForCreationType(QuickStartWizardPresenter.CreationType.RHYTHM_JUMP));
		assertEquals(2, presenter.indexForCreationType(QuickStartWizardPresenter.CreationType.BLOCK_FALL));
	}

	private static AudioFeatureTimeline minimalFeatureTimeline() {
		return new AudioFeatureTimeline(
			16.0,
			List.of(new DetectedBeat(1.0, 0.8f), new DetectedBeat(2.0, 0.7f)),
			List.of(new EnergyFrame(0.0, 0.2f), new EnergyFrame(8.0, 0.9f)),
			List.of(
				new FrequencyBands(0.0, 0.2f, 0.1f, 0.1f),
				new FrequencyBands(1.0, 0.8f, 0.1f, 0.1f),
				new FrequencyBands(2.0, 0.2f, 0.1f, 0.1f)
			),
			new com.beatblock.audio.analysis.WaveformExtractor.WaveformFrame[0],
			120f,
			null
		);
	}
}
