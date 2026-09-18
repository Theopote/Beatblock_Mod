package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.EnergyFrame;
import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.creator.CreationPreset;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.test.BeatBlockTestSupport;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.project.OscProjectStore;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quick Start 创建的 StageObject 必须随 .osc 保存/重载后仍可被 Timeline 目标解析。
 */
class QuickStartStageObjectPersistenceTest {

	@TempDir
	Path tempDir;

	private Timeline timeline;
	private TimelineEditor editor;
	private QuickStartWizardPresenter presenter;
	private ToolPanelPresenter toolPanel;
	private StageObjectSystem stageObjectSystem;
	private BuildLayerManager layerManager;

	@BeforeEach
	void setUp() {
		BeatBlock.installContext(BeatBlockTestSupport.minimalContext());
		var context = BeatBlock.getContext();
		context.selectionManager().reset();
		timeline = context.timeline();
		editor = context.timelineEditor();
		layerManager = context.buildLayerManager();
		stageObjectSystem = context.blockAnimationEngine().getStageObjectSystem();
		toolPanel = PresenterFactories.toolPanelPresenter(context);
		clearStageObjects();
		editor.getCommandManager().clear();
		presenter = new QuickStartWizardPresenter(
			new AutoMapSettingsPanelPresenter(BeatBlock::getContext),
			toolPanel,
			PresenterFactories.buildLayersPresenter(context),
			PresenterFactories.rhythmDropPanelPresenter(context),
			PresenterFactories.timelineBindingEditorPresenter(context),
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
		}
		BeatBlock.resetContext();
	}

	@Test
	void quickStartStageObjectSurvivesSaveAndReload() throws Exception {
		prepareAnalysisAndSelection(richFeatureTimeline());
		presenter.setCreationPreset(CreationPreset.RHYTHM_PULSE);
		presenter.goToStep(QuickStartWizardPresenter.Step.GENERATE);

		var outcome = presenter.generate();
		assertTrue(outcome.result().ok(), outcome.result().messageOrEmpty());
		assertEquals(QuickStartWizardPresenter.Step.DONE, presenter.step());
		assertTrue(stageObjectSystem.size() >= 1);
		assertFalse(timeline.getAutoAnimationEvents().isEmpty());

		String targetId = timeline.getAutoAnimationEvents().stream()
			.map(ev -> ev.getTargetObjectId())
			.filter(id -> id != null && !id.isBlank())
			.findFirst()
			.orElseThrow();
		assertNotNull(stageObjectSystem.get(targetId));

		Path projectFile = tempDir.resolve("quickstart.osc");
		OscProjectStore.save(projectFile, timeline, layerManager);

		StageObjectSystem reloadedStages = new StageObjectSystem();
		BuildLayerManager reloadedLayers = new BuildLayerManager(reloadedStages);
		Timeline reloadedTimeline = Timeline.createDefault();
		OscProjectStore.LoadedProject loaded = OscProjectStore.load(projectFile, reloadedLayers, reloadedTimeline);

		assertNotNull(reloadedStages.get(targetId), "Quick Start target must restore from stageObjects[]");
		assertFalse(reloadedTimeline.getAutoAnimationEvents().isEmpty());
		assertTrue(reloadedTimeline.getAutoAnimationEvents().stream()
			.anyMatch(ev -> targetId.equals(ev.getTargetObjectId())));
		assertFalse(loaded.hasBrokenReferences());
	}

	private void prepareAnalysisAndSelection(AudioFeatureTimeline feature) {
		AudioAnalysisEngine engine = BeatBlock.getContext().audioAnalysisEngine();
		engine.bindLastFeatureTimeline(feature);
		engine.fillTimelineFromFeature(timeline, feature, 44100);
		BeatBlockSelectionManager selection = BeatBlock.getContext().selectionManager();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		), SelectionOperation.NEW);
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

	private void clearStageObjects() {
		if (stageObjectSystem != null) {
			stageObjectSystem.clear();
		}
	}
}
