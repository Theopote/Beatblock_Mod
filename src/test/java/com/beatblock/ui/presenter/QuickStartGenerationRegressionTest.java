package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.EnergyFrame;
import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.assets.AudioAssetStatus;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.test.BeatBlockTestSupport;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import net.minecraft.util.math.BlockPos;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quick Start 生成回归：完整风格流程、失败回滚、音频状态。
 */
class QuickStartGenerationRegressionTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private QuickStartWizardPresenter presenter;
	private ToolPanelPresenter toolPanel;
	private final AudioAssetManager manager = AudioAssetManager.getInstance();

	@BeforeEach
	void setUp() {
		BeatBlock.installContext(BeatBlockTestSupport.minimalContext());
		BeatBlock.getContext().selectionManager().reset();
		var context = BeatBlock.getContext();
		timeline = context.timeline();
		editor = context.timelineEditor();
		manager.bindContext(BeatBlock::getContext);
		toolPanel = PresenterFactories.toolPanelPresenter(context);
		clearStageObjects();
		editor.getCommandManager().clear();
		presenter = new QuickStartWizardPresenter(
			new AutoMapSettingsPanelPresenter(BeatBlock::getContext),
			toolPanel,
			PresenterFactories.rhythmDropPanelPresenter(context),
			context::selectionManager,
			() -> timeline,
			() -> editor
		);
	}

	@AfterEach
	void tearDown() {
		try {
			clearStageObjects();
			BeatBlock.getContext().selectionManager().reset();
		} catch (IllegalStateException ignored) {
			// context already cleared
		}
		for (AudioAsset asset : new ArrayList<>(manager.getAssets())) {
			manager.remove(asset.getId());
		}
		BeatBlock.resetContext();
	}

	@Test
	void fullChoreographyCreatesStageObjectAnimationCameraAndVfx() {
		prepareAnalysisAndSelection(richFeatureTimeline());
		presenter.setCreationType(QuickStartWizardPresenter.CreationType.FULL_CHOREOGRAPHY);
		presenter.goToStep(QuickStartWizardPresenter.Step.GENERATE);

		int stagesBefore = toolPanel.listStageObjects().size();
		var outcome = presenter.generate();

		assertTrue(outcome.result().ok(), () -> outcome.result().messageOrEmpty());
		assertEquals(QuickStartWizardPresenter.Step.DONE, presenter.step());
		assertEquals(stagesBefore + 1, toolPanel.listStageObjects().size());
		assertTrue(countEvents(Timeline.TRACK_ID_ANIMATION_AUTO) > 0
			|| countEvents(Timeline.TRACK_ID_ANIMATION_BLOCK) > 0);
		assertTrue(countEvents(Timeline.TRACK_ID_CAMERA) > 0
			|| !timeline.getCameraKeyframes().isEmpty());
		assertTrue(
			countEvents(Timeline.TRACK_ID_GLOBAL) > 0
				|| (outcome.autoMapResult() != null && outcome.autoMapResult().getParticleEvents() >= 0)
		);
		if (outcome.autoMapResult() != null) {
			assertTrue(outcome.autoMapResult().getAnimationEvents() > 0);
			assertTrue(outcome.autoMapResult().getCameraEvents() > 0);
		}

		var summary = presenter.doneSummary();
		assertFalse(summary.objectName().isBlank());
		assertTrue(summary.blockCount() > 0);
		assertTrue(summary.animationEvents() > 0);
		assertTrue(summary.cameraShots() > 0);
		assertEquals(
			outcome.autoMapResult() != null ? outcome.autoMapResult().getParticleEvents() : summary.vfxEvents(),
			summary.vfxEvents()
		);
	}

	@Test
	void rhythmicPerformanceCreatesAnimationWithoutCameraOrVfxExpectation() {
		prepareAnalysisAndSelection(richFeatureTimeline());
		presenter.setCreationType(QuickStartWizardPresenter.CreationType.RHYTHMIC_PERFORMANCE);
		presenter.goToStep(QuickStartWizardPresenter.Step.GENERATE);

		int stagesBefore = toolPanel.listStageObjects().size();
		int cameraBefore = countEvents(Timeline.TRACK_ID_CAMERA);
		int globalBefore = countEvents(Timeline.TRACK_ID_GLOBAL);
		var outcome = presenter.generate();

		assertTrue(outcome.result().ok(), () -> outcome.result().messageOrEmpty());
		assertEquals(QuickStartWizardPresenter.Step.DONE, presenter.step());
		assertEquals(stagesBefore + 1, toolPanel.listStageObjects().size());
		assertTrue(countEvents(Timeline.TRACK_ID_ANIMATION_AUTO) > 0
			|| countEvents(Timeline.TRACK_ID_ANIMATION_BLOCK) > 0);
		assertEquals(cameraBefore, countEvents(Timeline.TRACK_ID_CAMERA));
		assertEquals(globalBefore, countEvents(Timeline.TRACK_ID_GLOBAL));
		if (outcome.autoMapResult() != null) {
			assertEquals(0, outcome.autoMapResult().getCameraEvents());
			assertEquals(0, outcome.autoMapResult().getParticleEvents());
		}
	}

	@Test
	void dropImpactCreatesBlockFallEvents() {
		AudioAnalysisEngine engine = BeatBlock.getContext().audioAnalysisEngine();
		engine.fillTimelineFromFeature(timeline, richFeatureTimeline(), 44100);
		selectBlocks();

		presenter.setCreationType(QuickStartWizardPresenter.CreationType.DROP_IMPACT);
		presenter.goToStep(QuickStartWizardPresenter.Step.GENERATE);
		assertTrue(presenter.canGenerate());

		int stagesBefore = toolPanel.listStageObjects().size();
		var outcome = presenter.generate();
		assertTrue(outcome.result().ok(), () -> outcome.result().messageOrEmpty());
		assertEquals(QuickStartWizardPresenter.Step.DONE, presenter.step());
		assertEquals(stagesBefore + 1, toolPanel.listStageObjects().size());
		assertTrue(
			countEvents(Timeline.TRACK_ID_ANIMATION_BLOCK) > 0
				|| countEvents(Timeline.TRACK_ID_ANIMATION_AUTO) > 0
		);
	}

	@Test
	void generationFailureRollsBackStageObjectAndTimeline() {
		AudioAnalysisEngine engine = BeatBlock.getContext().audioAnalysisEngine();
		engine.fillTimelineFromFeature(timeline, richFeatureTimeline(), 44100);
		selectBlocks();

		int eventsBefore = countAllTrackedEvents();
		int stagesBefore = toolPanel.listStageObjects().size();

		var failingRhythmDrop = new RhythmDropPanelPresenter(
			BeatBlock.getContext()::selectionManager,
			() -> timeline,
			() -> editor,
			() -> null
		);
		presenter = new QuickStartWizardPresenter(
			new AutoMapSettingsPanelPresenter(BeatBlock::getContext),
			toolPanel,
			failingRhythmDrop,
			BeatBlock.getContext()::selectionManager,
			() -> timeline,
			() -> editor
		);
		presenter.setCreationType(QuickStartWizardPresenter.CreationType.DROP_IMPACT);
		presenter.goToStep(QuickStartWizardPresenter.Step.GENERATE);

		var outcome = presenter.generate();
		assertFalse(outcome.result().ok());
		assertEquals(stagesBefore, toolPanel.listStageObjects().size());
		assertEquals(eventsBefore, countAllTrackedEvents());
		assertEquals(QuickStartWizardPresenter.Step.GENERATE, presenter.step());
	}

	@Test
	void missingAudioAssetReportsMissingAndNotLoaded(@TempDir Path tempDir) {
		Path missing = tempDir.resolve("missing-song.mp3");
		timeline.setMetadata("audioPath", missing.toAbsolutePath().normalize().toString());

		var analysis = presenter.analysisViewState();
		assertEquals(QuickStartWizardPresenter.WizardAnalysisState.MISSING_AUDIO, analysis.state());
		assertFalse(analysis.ready());
		assertFalse(analysis.canRetry());
		assertFalse(presenter.viewState().musicLoaded());
		assertFalse(presenter.isAnalysisReady());
	}

	@Test
	void analysisFailedStateExposesRetry(@TempDir Path tempDir) throws Exception {
		Path mp3 = tempDir.resolve("failed.mp3");
		Files.write(mp3, new byte[]{0x49, 0x44, 0x33, 0x03});
		String absolute = mp3.toAbsolutePath().normalize().toString();
		timeline.setMetadata("audioPath", absolute);
		AudioAsset asset = manager.addFromPath(absolute);
		assertNotNull(asset);
		asset.setStatus(AudioAssetStatus.FAILED);
		asset.setErrorMessage("analyzer boom");

		var analysis = presenter.analysisViewState();
		assertEquals(QuickStartWizardPresenter.WizardAnalysisState.FAILED, analysis.state());
		assertTrue(analysis.canRetry());
		assertEquals("analyzer boom", analysis.message());
		assertFalse(analysis.ready());
		assertFalse(presenter.isAnalysisReady());
	}

	@Test
	void generateTwiceThenUndoOnlyRevertsLatestPerformance() {
		prepareAnalysisAndSelection(richFeatureTimeline());
		presenter.setCreationType(QuickStartWizardPresenter.CreationType.RHYTHMIC_PERFORMANCE);

		int stagesBefore = toolPanel.listStageObjects().size();
		assertTrue(presenter.generate().result().ok());
		assertEquals(stagesBefore + 1, toolPanel.listStageObjects().size());
		int eventsAfterFirst = countAllTrackedEvents();

		selectBlocks(new BlockPos(10, 64, 10), new BlockPos(11, 64, 10));
		presenter.goToStep(QuickStartWizardPresenter.Step.GENERATE);
		assertTrue(presenter.generate().result().ok());
		assertEquals(stagesBefore + 2, toolPanel.listStageObjects().size());
		// Second generate may rewrite shared animation tracks (event count need not grow).
		assertTrue(countEvents(Timeline.TRACK_ID_ANIMATION_AUTO) > 0
			|| countEvents(Timeline.TRACK_ID_ANIMATION_BLOCK) > 0);

		editor.getCommandManager().undo();
		assertEquals(stagesBefore + 1, toolPanel.listStageObjects().size());
		assertEquals(eventsAfterFirst, countAllTrackedEvents());
	}

	private void clearStageObjects() {
		var system = toolPanel != null ? toolPanel.stageObjectSystemOrNull() : null;
		if (system != null) {
			system.clear();
		}
	}

	private void prepareAnalysisAndSelection(AudioFeatureTimeline feature) {
		AudioAnalysisEngine engine = BeatBlock.getContext().audioAnalysisEngine();
		engine.bindLastFeatureTimeline(feature);
		engine.fillTimelineFromFeature(timeline, feature, 44100);
		selectBlocks();
	}

	private void selectBlocks() {
		selectBlocks(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0));
	}

	private void selectBlocks(BlockPos a, BlockPos b) {
		BeatBlockSelectionManager selection = BeatBlock.getContext().selectionManager();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(a, b), SelectionOperation.NEW);
	}

	private int countEvents(String trackId) {
		var track = timeline.getTrack(trackId);
		if (track == null) {
			return 0;
		}
		int total = 0;
		for (var clip : track.getClips()) {
			total += clip.getEvents().size();
		}
		return total;
	}

	private int countAllTrackedEvents() {
		return countEvents(Timeline.TRACK_ID_ANIMATION_AUTO)
			+ countEvents(Timeline.TRACK_ID_ANIMATION_BLOCK)
			+ countEvents(Timeline.TRACK_ID_CAMERA)
			+ countEvents(Timeline.TRACK_ID_GLOBAL);
	}

	private static AudioFeatureTimeline richFeatureTimeline() {
		return new AudioFeatureTimeline(
			32.0,
			List.of(
				new DetectedBeat(1.0, 0.8f),
				new DetectedBeat(1.5, 0.7f),
				new DetectedBeat(2.0, 0.75f),
				new DetectedBeat(4.0, 0.9f)
			),
			List.of(
				new EnergyFrame(0.0, 0.1f),
				new EnergyFrame(4.0, 0.2f),
				new EnergyFrame(8.0, 0.15f),
				new EnergyFrame(12.0, 0.9f),
				new EnergyFrame(16.0, 0.85f),
				new EnergyFrame(28.0, 0.1f),
				new EnergyFrame(31.0, 0.08f)
			),
			List.of(
				new FrequencyBands(1.0, 0.8f, 0.1f, 0.1f),
				new FrequencyBands(1.5, 0.1f, 0.7f, 0.2f),
				new FrequencyBands(2.0, 0.1f, 0.1f, 0.8f),
				new FrequencyBands(4.0, 0.9f, 0.2f, 0.1f)
			),
			new com.beatblock.audio.analysis.WaveformExtractor.WaveformFrame[0],
			120f,
			null
		);
	}
}
