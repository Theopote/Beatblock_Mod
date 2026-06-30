package com.beatblock.ui.presenter;

import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.assets.AudioAssetStatus;
import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * 快速开始向导业务逻辑：导入音乐 → 选择类型 → 框选方块 → 一键生成。
 */
public final class QuickStartWizardPresenter {

	public enum CreationType {
		BUILD_APPEARANCE,
		RHYTHM_JUMP,
		BLOCK_FALL
	}

	public enum Step {
		IMPORT,
		CHOOSE_TYPE,
		SELECT_BLOCKS,
		GENERATE,
		DONE
	}

	public record ViewState(
		Step step,
		boolean musicLoaded,
		boolean analysisReady,
		int selectionCount,
		CreationType creationType,
		String statusMessage
	) {}

	public record OpenSession(
		String audioPath,
		boolean skippedImport
	) {}

	public record AnalysisProgress(
		int percent,
		@Nullable String statusText,
		boolean analyzing
	) {}

	public record GenerateOutcome(
		PresenterResult result,
		SmartAutoMapEngine.AutoMapResult autoMapResult,
		String stageObjectId
	) {}

	private final AutoMapSettingsPanelPresenter autoMapPresenter;
	private final ToolPanelPresenter toolPanelPresenter;
	private final RhythmDropPanelPresenter rhythmDropPresenter;
	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;

	private Step step = Step.IMPORT;
	private CreationType creationType = CreationType.BUILD_APPEARANCE;
	private String statusMessage = "";

	public QuickStartWizardPresenter(
		AutoMapSettingsPanelPresenter autoMapPresenter,
		ToolPanelPresenter toolPanelPresenter,
		RhythmDropPanelPresenter rhythmDropPresenter,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor
	) {
		this.autoMapPresenter = autoMapPresenter;
		this.toolPanelPresenter = toolPanelPresenter;
		this.rhythmDropPresenter = rhythmDropPresenter;
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
	}

	public ViewState viewState() {
		return new ViewState(
			step,
			isMusicLoaded(),
			isAnalysisReady(),
			selectionCount(),
			creationType,
			statusMessage
		);
	}

	public Step step() {
		return step;
	}

	public void reset() {
		step = Step.IMPORT;
		creationType = CreationType.BUILD_APPEARANCE;
		statusMessage = "";
	}

	public OpenSession prepareOpen() {
		reset();
		String path = currentAudioPath();
		if (!path.isBlank() && isMusicLoaded()) {
			step = Step.CHOOSE_TYPE;
			return new OpenSession(path, true);
		}
		return new OpenSession(path, false);
	}

	public void setCreationType(CreationType type) {
		if (type != null) {
			creationType = type;
		}
	}

	public int indexForCreationType(CreationType type) {
		if (type == null) {
			return 0;
		}
		return switch (type) {
			case RHYTHM_JUMP -> 1;
			case BLOCK_FALL -> 2;
			default -> 0;
		};
	}

	public boolean isAnalysisReady() {
		return switch (creationType) {
			case BLOCK_FALL -> hasBeatGrid();
			default -> autoMapPresenter.canGenerate();
		};
	}

	public boolean canGenerate() {
		return selectionCount() > 0 && isAnalysisReady();
	}

	public AnalysisProgress analysisProgress() {
		AudioAsset asset = connectedAsset();
		if (asset == null) {
			return new AnalysisProgress(0, null, false);
		}
		boolean analyzing = asset.getStatus() == AudioAssetStatus.QUEUED
			|| asset.getStatus() == AudioAssetStatus.ANALYZING;
		return new AnalysisProgress(
			asset.getAnalysisProgressPercent(),
			asset.getProcessingStatusText(),
			analyzing
		);
	}

	public String currentAudioPath() {
		Timeline tl = timeline.get();
		if (tl == null) {
			return "";
		}
		Object audioPath = tl.getMetadata("audioPath");
		return audioPath != null ? String.valueOf(audioPath).trim() : "";
	}

	public PresenterResult importMusic(String path) {
		String trimmed = path != null ? path.trim() : "";
		if (trimmed.isEmpty()) {
			statusMessage = BBTexts.get("beatblock.message.path_empty");
			return PresenterResult.failure(statusMessage);
		}

		AudioAssetManager manager = AudioAssetManager.getInstance();
		if (!manager.isSupportedAudioPath(trimmed)) {
			statusMessage = BBTexts.get(
				"beatblock.audio.unsupported_extensions",
				manager.getSupportedAudioExtensionsLabel()
			);
			return PresenterResult.failure(statusMessage);
		}

		AudioAsset asset = manager.addFromPath(trimmed);
		if (asset == null) {
			statusMessage = BBTexts.get("beatblock.audio.path_invalid");
			return PresenterResult.failure(statusMessage);
		}

		TimelineEditor editor = timelineEditor.get();
		if (editor == null) {
			statusMessage = BBTexts.get("beatblock.message.timeline_unavailable");
			return PresenterResult.failure(statusMessage);
		}

		editor.connectAudioAsset(asset);
		manager.startAnalysis(asset);

		statusMessage = BBTexts.get("beatblock.audio.added_and_analyzing", asset.getFileName());
		step = Step.CHOOSE_TYPE;
		return PresenterResult.success("");
	}

	public void continueWithLoadedMusic() {
		if (!isMusicLoaded()) {
			statusMessage = BBTexts.get("beatblock.message.import_music_first");
			return;
		}
		statusMessage = "";
		step = Step.CHOOSE_TYPE;
	}

	public void goToStep(Step target) {
		if (target != null) {
			step = target;
		}
	}

	public void advanceFromTypeStep() {
		step = Step.SELECT_BLOCKS;
		activateBoxSelectTool();
	}

	public void advanceFromSelectStep() {
		if (selectionCount() > 0) {
			step = Step.GENERATE;
		} else {
			statusMessage = BBTexts.get("beatblock.wizard.select_blocks_hint");
		}
	}

	public void activateBoxSelectTool() {
		BeatBlockSelectionManager mgr = BeatBlockSelectionManager.get();
		if (mgr != null) {
			mgr.setMode(SelectionMode.BOX);
		}
	}

	public GenerateOutcome generate() {
		if (selectionCount() <= 0) {
			PresenterResult failure = PresenterResult.failure(BBTexts.get("beatblock.wizard.select_blocks_hint"));
			statusMessage = failure.messageOrEmpty();
			return new GenerateOutcome(failure, null, null);
		}
		if (!isAnalysisReady()) {
			PresenterResult failure = PresenterResult.failure(BBTexts.get("beatblock.wizard.analysis_pending"));
			statusMessage = failure.messageOrEmpty();
			return new GenerateOutcome(failure, null, null);
		}

		String autoName = generateAutoObjectName();
		ToolPanelPresenter.StageObjectCreateRequest createRequest = new ToolPanelPresenter.StageObjectCreateRequest(
			autoName,
			false,
			GroupSortingStrategy.SEQUENTIAL,
			0.0
		);
		ToolPanelPresenter.CreateStageObjectOutcome createOutcome =
			toolPanelPresenter.createFromSelectionSnapshot(createRequest);
		if (!createOutcome.result().ok()) {
			statusMessage = createOutcome.result().messageOrEmpty();
			return new GenerateOutcome(createOutcome.result(), null, null);
		}

		String objectId = createOutcome.objectId();
		PresenterResult genResult;
		SmartAutoMapEngine.AutoMapResult autoMapResult = null;

		switch (creationType) {
			case BUILD_APPEARANCE -> {
				AutoMapSettings settings = buildAutoMapSettings(
					AutoMapStyle.EDM, Complexity.MEDIUM, true, true, objectId);
				var outcome = autoMapPresenter.generate(settings);
				genResult = outcome.result();
				autoMapResult = outcome.autoMapResult();
				if (genResult.ok()) {
					statusMessage = BBTexts.get("beatblock.wizard.generated_build",
						autoMapResult != null ? autoMapResult.getAnimationEvents() : 0);
				}
			}
			case RHYTHM_JUMP -> {
				AutoMapSettings settings = buildAutoMapSettings(
					AutoMapStyle.MINIMAL, Complexity.LOW, false, false, objectId);
				var outcome = autoMapPresenter.generate(settings);
				genResult = outcome.result();
				autoMapResult = outcome.autoMapResult();
				if (genResult.ok()) {
					statusMessage = BBTexts.get("beatblock.wizard.generated_rhythm",
						autoMapResult != null ? autoMapResult.getAnimationEvents() : 0);
				}
			}
			case BLOCK_FALL -> {
				RhythmDropPanelPresenter.GenerateRequest request = new RhythmDropPanelPresenter.GenerateRequest(
					RhythmDropPanelPresenter.defaultRequest().fallDurationSeconds(),
					RhythmDropPanelPresenter.defaultRequest().fallHeightBlocks(),
					true,
					objectId
				);
				genResult = rhythmDropPresenter.generateFromSelection(request);
				if (genResult.ok()) {
					statusMessage = BBTexts.get("beatblock.wizard.generated_fall", autoName);
				}
			}
			default -> genResult = PresenterResult.failure(BBTexts.get("beatblock.wizard.unknown_type"));
		}

		if (!genResult.ok() && statusMessage.isBlank()) {
			statusMessage = genResult.messageOrEmpty();
		}
		if (genResult.ok()) {
			step = Step.DONE;
		}
		return new GenerateOutcome(genResult, autoMapResult, objectId);
	}

	private static AutoMapSettings buildAutoMapSettings(
		AutoMapStyle style,
		Complexity complexity,
		boolean camera,
		boolean particles,
		String objectId
	) {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setStyle(style);
		settings.setComplexity(complexity);
		settings.setCameraEnabled(camera);
		settings.setParticlesEnabled(particles);
		settings.setTargetObjectIds(List.of(objectId));
		return settings;
	}

	private boolean isMusicLoaded() {
		return !currentAudioPath().isBlank();
	}

	private boolean hasBeatGrid() {
		Timeline tl = timeline.get();
		return ReferenceBeatResolver.resolveBeatTimesSeconds(tl).length > 0;
	}

	private int selectionCount() {
		BeatBlockSelectionManager mgr = BeatBlockSelectionManager.get();
		return mgr != null ? mgr.getSelectionCount() : 0;
	}

	private @Nullable AudioAsset connectedAsset() {
		String path = currentAudioPath();
		if (path.isBlank()) {
			return null;
		}
		for (AudioAsset asset : AudioAssetManager.getInstance().getAssets()) {
			if (asset.getPath() == null) {
				continue;
			}
			if (asset.getPath().toAbsolutePath().normalize().toString().equals(path)) {
				return asset;
			}
		}
		return null;
	}

	private String generateAutoObjectName() {
		var existingObjects = toolPanelPresenter.listStageObjects();
		int counter = 1;
		while (true) {
			String candidate = "selection_" + counter;
			boolean exists = existingObjects.stream()
				.anyMatch(obj -> obj.id().equals(candidate));
			if (!exists) {
				return candidate;
			}
			counter++;
		}
	}
}
