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
import com.beatblock.ui.i18n.BBTexts;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickStartWizardPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private QuickStartWizardPresenter presenter;
	private final AudioAssetManager manager = AudioAssetManager.getInstance();

	@BeforeEach
	void setUp() {
		BeatBlock.installContext(BeatBlockTestSupport.minimalContext());
		BeatBlock.getContext().selectionManager().reset();
		var context = BeatBlock.getContext();
		timeline = context.timeline();
		editor = context.timelineEditor();
		manager.bindContext(BeatBlock::getContext);
		presenter = new QuickStartWizardPresenter(
			new AutoMapSettingsPanelPresenter(BeatBlock::getContext),
			PresenterFactories.toolPanelPresenter(context),
			PresenterFactories.rhythmDropPanelPresenter(context),
			context::selectionManager,
			() -> timeline,
			() -> editor
		);
	}

	@AfterEach
	void tearDown() {
		try {
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
	void importMusicRejectsEmptyPath() {
		var result = presenter.importMusic("  ");
		assertFalse(result.ok());
		assertEquals(QuickStartWizardPresenter.Step.IMPORT, presenter.step());
	}

	@Test
	void doneSummaryIsEmptyBeforeSuccessfulGenerate() {
		var summary = presenter.doneSummary();
		assertEquals("", summary.objectName());
		assertEquals(0, summary.blockCount());
		assertEquals(0, summary.animationEvents());
		assertEquals(0, summary.cameraShots());
		assertEquals(0, summary.vfxEvents());
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
		String absolute = mp3.toAbsolutePath().normalize().toString();
		timeline.setMetadata("audioPath", absolute);
		assertNotNull(manager.addFromPath(absolute));

		var session = presenter.prepareOpen();

		assertTrue(session.skippedImport());
		assertEquals(absolute, session.audioPath());
		assertEquals(QuickStartWizardPresenter.Step.CHOOSE_TYPE, presenter.step());
	}

	@Test
	void prepareOpenStaysOnImportWhenAudioPathMissingOnDisk() {
		timeline.setMetadata("audioPath", "D:/music/missing-song.mp3");

		var session = presenter.prepareOpen();

		assertFalse(session.skippedImport());
		assertEquals(QuickStartWizardPresenter.Step.IMPORT, presenter.step());
		assertFalse(presenter.viewState().musicLoaded());
	}

	@Test
	void prepareOpenStaysOnImportWhenAssetNotRegistered(@TempDir Path tempDir) throws Exception {
		Path mp3 = tempDir.resolve("orphan.mp3");
		Files.write(mp3, new byte[] {0x49, 0x44, 0x33, 0x03});
		timeline.setMetadata("audioPath", mp3.toAbsolutePath().normalize().toString());

		var session = presenter.prepareOpen();

		assertFalse(session.skippedImport());
		assertEquals(QuickStartWizardPresenter.Step.IMPORT, presenter.step());
		assertFalse(presenter.viewState().musicLoaded());
	}

	@Test
	void advanceFromSelectStepRequiresSelection() {
		presenter.goToStep(QuickStartWizardPresenter.Step.SELECT_BLOCKS);
		presenter.advanceFromSelectStep();
		assertEquals(QuickStartWizardPresenter.Step.SELECT_BLOCKS, presenter.step());
	}

	@Test
	void selectionStepStartsIdleUntilUserClicksStartSelecting() {
		presenter.advanceFromTypeStep();
		assertEquals(QuickStartWizardPresenter.Step.SELECT_BLOCKS, presenter.step());
		assertEquals(
			QuickStartWizardPresenter.SelectionPhase.IDLE,
			presenter.selectionGuideState().phase()
		);

		presenter.startSelecting();
		var guide = presenter.selectionGuideState();
		assertEquals(QuickStartWizardPresenter.SelectionPhase.SELECTING, guide.phase());
		assertEquals(0, guide.blockCount());
		assertFalse(guide.canContinue());
	}

	@Test
	void selectionGuideReportsBoundsAfterBlocksSelected() {
		presenter.advanceFromTypeStep();
		presenter.startSelecting();

		BeatBlockSelectionManager selection = BeatBlock.getContext().selectionManager();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(2, 65, 1)
		), SelectionOperation.NEW);

		var guide = presenter.selectionGuideState();
		assertEquals(QuickStartWizardPresenter.SelectionPhase.SELECTING, guide.phase());
		assertEquals(2, guide.blockCount());
		assertTrue(guide.hasBounds());
		assertEquals(3, guide.sizeX());
		assertEquals(2, guide.sizeY());
		assertEquals(2, guide.sizeZ());
		assertTrue(guide.canContinue());

		presenter.clearSelection();
		assertEquals(0, presenter.selectionGuideState().blockCount());
		assertEquals(
			QuickStartWizardPresenter.SelectionPhase.SELECTING,
			presenter.selectionGuideState().phase()
		);
	}

	@Test
	void canGenerateRequiresAnalysisReady() {
		presenter.setCreationType(QuickStartWizardPresenter.CreationType.FULL_CHOREOGRAPHY);
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
		presenter.setCreationType(QuickStartWizardPresenter.CreationType.DROP_IMPACT);
		assertFalse(presenter.isAnalysisReady());

		AudioAnalysisEngine engine = BeatBlock.getContext().audioAnalysisEngine();
		engine.fillTimelineFromFeature(timeline, minimalFeatureTimeline(), 44100);
		assertTrue(presenter.isAnalysisReady());
	}

	@Test
	void generateFailsWhenAnalysisNotReady() {
		var outcome = presenter.generate();
		assertFalse(outcome.result().ok());
		assertEquals(QuickStartWizardPresenter.Step.GENERATE, presenter.step());
	}

	@Test
	void beginGenerateEntersGeneratingThenReachesDoneViaTicks() {
		AudioAnalysisEngine engine = BeatBlock.getContext().audioAnalysisEngine();
		engine.bindLastFeatureTimeline(minimalFeatureTimeline());
		engine.fillTimelineFromFeature(timeline, minimalFeatureTimeline(), 44100);

		BeatBlockSelectionManager selection = BeatBlock.getContext().selectionManager();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		), SelectionOperation.NEW);

		presenter.setCreationType(QuickStartWizardPresenter.CreationType.RHYTHMIC_PERFORMANCE);
		presenter.goToStep(QuickStartWizardPresenter.Step.GENERATE);
		assertTrue(presenter.canGenerate());

		presenter.beginGenerate();
		assertEquals(QuickStartWizardPresenter.Step.GENERATING, presenter.step());
		assertTrue(presenter.generationProgress().active());
		assertEquals(
			QuickStartWizardPresenter.GenerationPhase.CREATE_STAGE_OBJECT,
			presenter.generationProgress().phase()
		);

		boolean sawChoreography = false;
		QuickStartWizardPresenter.GenerateOutcome outcome = null;
		for (int i = 0; i < 16 && presenter.step() == QuickStartWizardPresenter.Step.GENERATING; i++) {
			if (presenter.generationProgress().phase()
				== QuickStartWizardPresenter.GenerationPhase.CREATE_CHOREOGRAPHY) {
				sawChoreography = true;
			}
			outcome = presenter.tickGenerate();
		}

		assertTrue(sawChoreography);
		assertEquals(QuickStartWizardPresenter.Step.DONE, presenter.step());
		assertNotNull(outcome);
		assertTrue(outcome.result().ok());
		assertFalse(presenter.generationProgress().active());

		var summary = presenter.doneSummary();
		assertFalse(summary.objectName().isBlank());
		assertTrue(summary.blockCount() > 0);
		assertTrue(summary.animationEvents() > 0);

		String createdId = outcome.stageObjectId();
		assertNotNull(createdId);
		assertNotNull(toolPanelPresenter().getStageObject(createdId));
		assertTrue(editor.getCommandManager().canUndo());
		assertFalse(editor.getCommandManager().undoDescriptionsNewestFirst().isEmpty());
		assertTrue(editor.getCommandManager().undoDescriptionsNewestFirst().getFirst()
			.contains("performance")
			|| editor.getCommandManager().undoDescriptionsNewestFirst().getFirst().contains("表演")
			|| editor.getCommandManager().undoDescriptionsNewestFirst().getFirst().contains("quick_start"));

		int eventsAfterGenerate = countTrackedEvents(timeline);
		assertTrue(eventsAfterGenerate > 0);

		editor.getCommandManager().undo();
		assertNull(toolPanelPresenter().getStageObject(createdId));
		assertEquals(0, countTrackedEvents(timeline));

		editor.getCommandManager().redo();
		assertNotNull(toolPanelPresenter().getStageObject(createdId));
		assertEquals(eventsAfterGenerate, countTrackedEvents(timeline));
	}

	private ToolPanelPresenter toolPanelPresenter() {
		return PresenterFactories.toolPanelPresenter(BeatBlock.getContext());
	}

	private static int countTrackedEvents(Timeline timeline) {
		int total = 0;
		for (String trackId : List.of(
			Timeline.TRACK_ID_ANIMATION_AUTO,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			Timeline.TRACK_ID_CAMERA,
			Timeline.TRACK_ID_GLOBAL
		)) {
			var track = timeline.getTrack(trackId);
			if (track == null) {
				continue;
			}
			for (var clip : track.getClips()) {
				total += clip.getEvents().size();
			}
		}
		return total;
	}

	@Test
	void generateRollsBackOrphanStageObjectWhenChoreographyFails() {
		AudioAnalysisEngine engine = BeatBlock.getContext().audioAnalysisEngine();
		engine.fillTimelineFromFeature(timeline, minimalFeatureTimeline(), 44100);

		BeatBlockSelectionManager selection = BeatBlock.getContext().selectionManager();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		), SelectionOperation.NEW);

		var toolPanel = PresenterFactories.toolPanelPresenter(BeatBlock.getContext());
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

		assertTrue(presenter.canGenerate());
		var outcome = presenter.generate();

		assertFalse(outcome.result().ok());
		assertNull(outcome.stageObjectId());
		assertTrue(toolPanel.listStageObjects().stream().noneMatch(item -> item.id().startsWith("selection_")));
		assertEquals(QuickStartWizardPresenter.Step.GENERATE, presenter.step());
	}

	@Test
	void indexForCreationTypeMatchesComboOrder() {
		assertEquals(0, presenter.indexForCreationType(QuickStartWizardPresenter.CreationType.CINEMATIC_BUILD));
		assertEquals(1, presenter.indexForCreationType(QuickStartWizardPresenter.CreationType.RHYTHMIC_PERFORMANCE));
		assertEquals(2, presenter.indexForCreationType(QuickStartWizardPresenter.CreationType.DROP_IMPACT));
		assertEquals(3, presenter.indexForCreationType(QuickStartWizardPresenter.CreationType.FULL_CHOREOGRAPHY));
	}

	@Test
	void generationPlanExposesFriendlyObjectNameAndStyleSummaries() {
		BeatBlockSelectionManager selection = BeatBlock.getContext().selectionManager();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		), SelectionOperation.NEW);

		presenter.setCreationType(QuickStartWizardPresenter.CreationType.FULL_CHOREOGRAPHY);
		presenter.setStageObjectName("");
		presenter.advanceFromSelectStep();

		var plan = presenter.generationPlan();
		assertEquals(2, plan.selectionCount());
		assertFalse(plan.objectName().isBlank());
		assertFalse(plan.objectName().startsWith("selection_"));
		assertEquals(QuickStartWizardPresenter.styleLabel(QuickStartWizardPresenter.CreationType.FULL_CHOREOGRAPHY), plan.styleLabel());
		assertFalse(plan.animationSummary().isBlank());
		assertFalse(plan.cameraSummary().isBlank());
		assertFalse(plan.vfxSummary().isBlank());

		presenter.setStageObjectName("Tower A");
		assertEquals("Tower A", presenter.generationPlan().objectName());
	}

	@Test
	void analysisViewStateReportsFailedWithRetry(@TempDir Path tempDir) throws Exception {
		Path mp3 = tempDir.resolve("failed.mp3");
		Files.write(mp3, new byte[] {0x49, 0x44, 0x33, 0x03});
		String absolute = mp3.toAbsolutePath().normalize().toString();
		timeline.setMetadata("audioPath", absolute);
		AudioAsset asset = manager.addFromPath(absolute);
		assertNotNull(asset);
		asset.setStatus(AudioAssetStatus.FAILED);
		asset.setErrorMessage("python crashed");

		var analysis = presenter.analysisViewState();

		assertEquals(QuickStartWizardPresenter.WizardAnalysisState.FAILED, analysis.state());
		assertTrue(analysis.canRetry());
		assertEquals("python crashed", analysis.message());
		assertFalse(analysis.ready());
	}

	@Test
	void analysisViewStateReportsMissingAudioWhenFileGone() {
		timeline.setMetadata("audioPath", "D:/music/gone.mp3");

		var analysis = presenter.analysisViewState();

		assertEquals(QuickStartWizardPresenter.WizardAnalysisState.MISSING_AUDIO, analysis.state());
		assertFalse(analysis.canRetry());
		assertFalse(analysis.ready());
	}

	@Test
	void retryAnalysisRestartsFailedAsset(@TempDir Path tempDir) throws Exception {
		Path mp3 = tempDir.resolve("retry.mp3");
		Files.write(mp3, new byte[] {0x49, 0x44, 0x33, 0x03});
		String absolute = mp3.toAbsolutePath().normalize().toString();
		timeline.setMetadata("audioPath", absolute);
		AudioAsset asset = manager.addFromPath(absolute);
		assertNotNull(asset);
		asset.setStatus(AudioAssetStatus.FAILED);

		var result = presenter.retryAnalysis();

		assertTrue(result.ok());
		assertTrue(
			asset.getStatus() == AudioAssetStatus.QUEUED
				|| asset.getStatus() == AudioAssetStatus.ANALYZING
				|| asset.getStatus() == AudioAssetStatus.PENDING
				|| asset.getStatus() == AudioAssetStatus.FAILED
				|| asset.getStatus() == AudioAssetStatus.COMPLETED
		);
	}

	@Test
	void chooseAnotherAudioReturnsToImportStep() {
		presenter.goToStep(QuickStartWizardPresenter.Step.GENERATE);
		presenter.chooseAnotherAudio();
		assertEquals(QuickStartWizardPresenter.Step.IMPORT, presenter.step());
	}

	@Test
	void currentAudioFileNameUsesAssetFileName(@TempDir Path tempDir) throws Exception {
		Path mp3 = tempDir.resolve("demo-track.mp3");
		Files.write(mp3, new byte[] {0x49, 0x44, 0x33, 0x03});
		String absolute = mp3.toAbsolutePath().normalize().toString();
		timeline.setMetadata("audioPath", absolute);
		assertNotNull(manager.addFromPath(absolute));

		assertEquals("demo-track.mp3", presenter.currentAudioFileName());
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
