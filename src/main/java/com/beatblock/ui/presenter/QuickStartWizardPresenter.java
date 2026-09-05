package com.beatblock.ui.presenter;

import com.beatblock.automap.choreography.ChoreographyLayerProfile;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.assets.AudioAssetStatus;
import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.CreateQuickStartPerformanceCommand;
import com.beatblock.ui.i18n.BBTexts;

import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * 快速开始向导业务逻辑：导入音乐 → 选择创作风格 → 框选方块 → 一键生成。
 */
public final class QuickStartWizardPresenter {

	/**
	 * 向导里的创作风格（产品层）。EDM / Complexity / LayerProfile 等仅为内部映射，不对用户暴露。
	 * <ul>
	 *   <li>{@link #CINEMATIC_BUILD} — 建筑逐步出现、镜头克制</li>
	 *   <li>{@link #RHYTHMIC_PERFORMANCE} — 跟随节奏运动</li>
	 *   <li>{@link #DROP_IMPACT} — 高潮坠落 / 爆发命中</li>
	 *   <li>{@link #FULL_CHOREOGRAPHY} — Accent + Phrase + Hero + Camera + VFX</li>
	 * </ul>
	 */
	public enum CreationType {
		CINEMATIC_BUILD,
		RHYTHMIC_PERFORMANCE,
		DROP_IMPACT,
		FULL_CHOREOGRAPHY
	}

	public enum Step {
		IMPORT,
		CHOOSE_TYPE,
		SELECT_BLOCKS,
		GENERATE,
		/** 生成进行中：分帧推进，UI 可显示阶段文案。 */
		GENERATING,
		DONE
	}

	/**
	 * 生成流水线阶段。Camera / VFX 目前可能已包含在编舞编译中，仍单独保留阶段以便未来拆分与 UI 进度。
	 */
	public enum GenerationPhase {
		IDLE,
		CREATE_STAGE_OBJECT,
		CREATE_CHOREOGRAPHY,
		CREATE_CAMERA,
		CREATE_VFX,
		CREATE_DROP_IMPACT,
		FINALIZE
	}

	public record GenerationProgress(
		GenerationPhase phase,
		String message,
		float fraction,
		boolean active
	) {
		public static GenerationProgress idle() {
			return new GenerationProgress(GenerationPhase.IDLE, "", 0f, false);
		}
	}

	/**
	 * 框选步骤任务状态：先点「开始选择」，再在世界中框选，最后确认继续。
	 */
	public enum SelectionPhase {
		/** 尚未开始：引导点击 Start Selecting */
		IDLE,
		/** 已激活选择：在世界中框选，可 Clear / Reselect / Continue */
		SELECTING
	}

	public record ViewState(
		Step step,
		boolean musicLoaded,
		boolean analysisReady,
		int selectionCount,
		CreationType creationType,
		String stageObjectName,
		String statusMessage
	) {}

	/**
	 * 框选步骤视图：任务导向文案 + 实时选区统计。
	 */
	public record SelectionGuideState(
		SelectionPhase phase,
		int blockCount,
		int sizeX,
		int sizeY,
		int sizeZ,
		boolean hasBounds
	) {
		public boolean canContinue() {
			return blockCount > 0;
		}
	}

	/**
	 * Generate 页预告：告诉用户即将创建什么，避免「突然多了 selection_1」。
	 */
	public record GenerationPlan(
		int selectionCount,
		String objectName,
		String styleLabel,
		String animationSummary,
		String cameraSummary,
		String vfxSummary
	) {}

	public record OpenSession(
		String audioPath,
		boolean skippedImport
	) {}

	public record AnalysisViewState(
		WizardAnalysisState state,
		int percent,
		@Nullable String message,
		boolean canRetry
	) {
		public boolean ready() {
			return state == WizardAnalysisState.READY;
		}

		public boolean analyzing() {
			return state == WizardAnalysisState.QUEUED || state == WizardAnalysisState.ANALYZING;
		}
	}

	public enum WizardAnalysisState {
		NONE,
		QUEUED,
		ANALYZING,
		READY,
		FAILED,
		MISSING_AUDIO
	}

	public record GenerateOutcome(
		PresenterResult result,
		SmartAutoMapEngine.AutoMapResult autoMapResult,
		String stageObjectId
	) {}

	/** DONE 页展示的生成结果摘要（块数与事件统计）。 */
	public record DoneSummary(
		String objectName,
		int blockCount,
		int animationEvents,
		int cameraShots,
		int vfxEvents
	) {
		public static DoneSummary empty() {
			return new DoneSummary("", 0, 0, 0, 0);
		}
	}

	private final AutoMapSettingsPanelPresenter autoMapPresenter;
	private final ToolPanelPresenter toolPanelPresenter;
	private final RhythmDropPanelPresenter rhythmDropPresenter;
	private final Supplier<BeatBlockSelectionManager> selectionManager;
	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;

	private Step step = Step.IMPORT;
	private CreationType creationType = CreationType.FULL_CHOREOGRAPHY;
	private String stageObjectName = "";
	private String statusMessage = "";
	/** 用户是否已在本步骤点击「开始选择」（或从已有选区进入）。 */
	private boolean selectionSessionStarted = false;

	private GenerationPhase generationPhase = GenerationPhase.IDLE;
	private String generationMessage = "";
	private float generationFraction;
	private @Nullable QuickStartGenerationTransaction activeTx;
	private @Nullable String pendingObjectId;
	private @Nullable String pendingObjectName;
	private SmartAutoMapEngine.@Nullable AutoMapResult pendingAutoMapResult;
	private boolean pendingCamera;
	private boolean pendingVfx;
	private @Nullable GenerateOutcome lastGenerateOutcome;

	public QuickStartWizardPresenter(
		AutoMapSettingsPanelPresenter autoMapPresenter,
		ToolPanelPresenter toolPanelPresenter,
		RhythmDropPanelPresenter rhythmDropPresenter,
		Supplier<BeatBlockSelectionManager> selectionManager,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor
	) {
		this.autoMapPresenter = autoMapPresenter;
		this.toolPanelPresenter = toolPanelPresenter;
		this.rhythmDropPresenter = rhythmDropPresenter;
		this.selectionManager = selectionManager;
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
			resolvedStageObjectName(),
			statusMessage
		);
	}

	public SelectionGuideState selectionGuideState() {
		BeatBlockSelectionManager mgr = selectionManager.get();
		int count = selectionCount();
		SelectionPhase phase = (!selectionSessionStarted && count <= 0)
			? SelectionPhase.IDLE
			: SelectionPhase.SELECTING;

		int sizeX = 0;
		int sizeY = 0;
		int sizeZ = 0;
		boolean hasBounds = false;
		if (mgr != null && count > 0) {
			var min = mgr.getBoundingMin();
			var max = mgr.getBoundingMax();
			if (min != null && max != null) {
				sizeX = Math.max(1, max.getX() - min.getX() + 1);
				sizeY = Math.max(1, max.getY() - min.getY() + 1);
				sizeZ = Math.max(1, max.getZ() - min.getZ() + 1);
				hasBounds = true;
			}
		}
		return new SelectionGuideState(phase, count, sizeX, sizeY, sizeZ, hasBounds);
	}

	public GenerationPlan generationPlan() {
		return new GenerationPlan(
			selectionCount(),
			resolvedStageObjectName(),
			styleLabel(creationType),
			animationSummary(creationType),
			cameraSummary(creationType),
			vfxSummary(creationType)
		);
	}

	public GenerationProgress generationProgress() {
		if (step != Step.GENERATING) {
			return GenerationProgress.idle();
		}
		return new GenerationProgress(generationPhase, generationMessage, generationFraction, true);
	}

	public Step step() {
		return step;
	}

	public void reset() {
		abortGenerationIfNeeded();
		step = Step.IMPORT;
		creationType = CreationType.FULL_CHOREOGRAPHY;
		stageObjectName = "";
		statusMessage = "";
		selectionSessionStarted = false;
		clearGenerationState();
	}

	public OpenSession prepareOpen() {
		reset();
		String path = currentAudioPath();
		if (isMusicLoaded()) {
			step = Step.CHOOSE_TYPE;
			return new OpenSession(path, true);
		}
		if (!path.isBlank()) {
			statusMessage = musicUnavailableReason(path);
		}
		return new OpenSession(path, false);
	}

	public void setCreationType(CreationType type) {
		if (type != null) {
			creationType = type;
		}
	}

	public void setStageObjectName(String name) {
		stageObjectName = name != null ? name.trim() : "";
	}

	public String stageObjectName() {
		return resolvedStageObjectName();
	}

	public int indexForCreationType(CreationType type) {
		if (type == null) {
			return 3;
		}
		return switch (type) {
			case CINEMATIC_BUILD -> 0;
			case RHYTHMIC_PERFORMANCE -> 1;
			case DROP_IMPACT -> 2;
			case FULL_CHOREOGRAPHY -> 3;
		};
	}

	public boolean isAnalysisReady() {
		return switch (creationType) {
			case DROP_IMPACT -> hasBeatGrid();
			default -> autoMapPresenter.canGenerate();
		};
	}

	public boolean canGenerate() {
		return selectionCount() > 0 && isAnalysisReady();
	}

	public AnalysisViewState analysisViewState() {
		String path = currentAudioPath();
		if (path.isBlank()) {
			return new AnalysisViewState(WizardAnalysisState.NONE, 0, null, false);
		}

		Path file = resolveExistingAudioFile(path);
		AudioAsset asset = file != null ? findAssetByPath(file) : null;
		if (file == null || asset == null) {
			return new AnalysisViewState(
				WizardAnalysisState.MISSING_AUDIO,
				0,
				BBTexts.get("beatblock.wizard.analysis.missing_audio"),
				false
			);
		}

		if (isAnalysisReady()) {
			return new AnalysisViewState(
				WizardAnalysisState.READY,
				100,
				BBTexts.get("beatblock.wizard.music_imported"),
				false
			);
		}

		return switch (asset.getStatus()) {
			case QUEUED -> new AnalysisViewState(
				WizardAnalysisState.QUEUED,
				asset.getAnalysisProgressPercent(),
				statusOrDefault(asset, BBTexts.get("beatblock.wizard.analysis.queued")),
				false
			);
			case ANALYZING -> new AnalysisViewState(
				WizardAnalysisState.ANALYZING,
				asset.getAnalysisProgressPercent(),
				statusOrDefault(asset, null),
				false
			);
			case FAILED -> new AnalysisViewState(
				WizardAnalysisState.FAILED,
				asset.getAnalysisProgressPercent(),
				failedMessage(asset),
				true
			);
			case PENDING -> new AnalysisViewState(
				WizardAnalysisState.FAILED,
				asset.getAnalysisProgressPercent(),
				statusOrDefault(asset, BBTexts.get("beatblock.wizard.analysis.queued")),
				true
			);
			case COMPLETED -> new AnalysisViewState(
				WizardAnalysisState.FAILED,
				asset.getAnalysisProgressPercent(),
				BBTexts.get("beatblock.wizard.analysis_pending"),
				true
			);
		};
	}

	public PresenterResult retryAnalysis() {
		AudioAsset asset = resolveLoadedAudioAsset();
		if (asset == null) {
			statusMessage = BBTexts.get("beatblock.wizard.analysis.missing_audio");
			return PresenterResult.failure(statusMessage);
		}
		AudioAssetManager.getInstance().startAnalysis(asset);
		statusMessage = BBTexts.get("beatblock.audio.added_and_analyzing", asset.getFileName());
		return PresenterResult.success(statusMessage);
	}

	public void chooseAnotherAudio() {
		statusMessage = "";
		step = Step.IMPORT;
	}

	public String currentAudioFileName() {
		AudioAsset asset = resolveLoadedAudioAsset();
		if (asset != null && asset.getFileName() != null && !asset.getFileName().isBlank()) {
			return asset.getFileName();
		}
		String path = currentAudioPath();
		if (path.isBlank()) {
			return "";
		}
		try {
			Path file = Path.of(path.trim()).getFileName();
			return file != null ? file.toString() : path;
		} catch (RuntimeException ignored) {
			return path;
		}
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
		if (target == null) {
			return;
		}
		if (step == Step.GENERATING) {
			return;
		}
		step = target;
		if (target == Step.SELECT_BLOCKS) {
			// 从 Generate 返回时若已有选区，直接进入 SELECTING 状态
			selectionSessionStarted = selectionCount() > 0;
		}
	}

	public void advanceFromTypeStep() {
		step = Step.SELECT_BLOCKS;
		selectionSessionStarted = selectionCount() > 0;
		// 不自动激活工具：由「开始选择方块」显式启动，避免新人不知接下来做什么
	}

	public void advanceFromSelectStep() {
		if (selectionCount() > 0) {
			if (stageObjectName == null || stageObjectName.isBlank()) {
				stageObjectName = suggestStageObjectName();
			}
			step = Step.GENERATE;
		} else {
			statusMessage = BBTexts.get("beatblock.wizard.select_blocks_hint");
		}
	}

	/** 开始选择：激活框选工具，进入世界框选阶段。 */
	public void startSelecting() {
		selectionSessionStarted = true;
		statusMessage = "";
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.setMode(SelectionMode.BOX);
			mgr.setOperation(SelectionOperation.NEW);
		}
	}

	/** 清空当前选区，保持选择会话与框选模式。 */
	public void clearSelection() {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.clearSelection();
			mgr.setMode(SelectionMode.BOX);
		}
		selectionSessionStarted = true;
		statusMessage = "";
	}

	/** 重新选择：清空后再次激活框选。 */
	public void reselect() {
		clearSelection();
		startSelecting();
	}

	/** @deprecated 使用 {@link #startSelecting()} */
	@Deprecated
	public void activateBoxSelectTool() {
		startSelecting();
	}

	/**
	 * 启动生成：进入 {@link Step#GENERATING}，由 UI 每帧调用 {@link #tickGenerate()} 推进阶段。
	 * 校验失败时停在 {@link Step#GENERATE} 并写入 {@link #lastGenerateOutcome()}。
	 */
	public void beginGenerate() {
		if (step == Step.GENERATING) {
			return;
		}
		lastGenerateOutcome = null;
		if (selectionCount() <= 0) {
			failBeforeStart(BBTexts.get("beatblock.wizard.select_blocks_hint"));
			return;
		}
		if (!isAnalysisReady()) {
			failBeforeStart(BBTexts.get("beatblock.wizard.analysis_pending"));
			return;
		}

		pendingObjectName = resolvedStageObjectName();
		pendingObjectId = null;
		pendingAutoMapResult = null;
		pendingCamera = creationType == CreationType.CINEMATIC_BUILD
			|| creationType == CreationType.FULL_CHOREOGRAPHY;
		pendingVfx = creationType == CreationType.FULL_CHOREOGRAPHY;
		activeTx = QuickStartGenerationTransaction.begin(timeline.get());
		generationPhase = GenerationPhase.CREATE_STAGE_OBJECT;
		setGenerationProgress(GenerationPhase.CREATE_STAGE_OBJECT, 0.05f);
		step = Step.GENERATING;
		statusMessage = "";
	}

	/**
	 * 推进一帧生成阶段。仅在 {@link Step#GENERATING} 时有效。
	 * @return 本帧结束后的结果；进行中为 null
	 */
	public @Nullable GenerateOutcome tickGenerate() {
		if (step != Step.GENERATING) {
			return lastGenerateOutcome;
		}
		try {
			switch (generationPhase) {
				case CREATE_STAGE_OBJECT -> runCreateStageObjectPhase();
				case CREATE_CHOREOGRAPHY -> runChoreographyPhase();
				case CREATE_CAMERA -> runCameraPhase();
				case CREATE_VFX -> runVfxPhase();
				case CREATE_DROP_IMPACT -> runDropImpactPhase();
				case FINALIZE -> runFinalizePhase();
				case IDLE -> step = Step.GENERATE;
			}
		} catch (RuntimeException ex) {
			failGeneration(
				PresenterResult.failure(ex.getMessage() != null ? ex.getMessage() : BBTexts.get("beatblock.wizard.unknown_type")),
				pendingAutoMapResult
			);
		}
		return step == Step.GENERATING ? null : lastGenerateOutcome;
	}

	/**
	 * 同步跑完全部分帧（测试 / 兼容旧调用）。UI 应使用 {@link #beginGenerate()} + {@link #tickGenerate()}。
	 */
	public GenerateOutcome generate() {
		beginGenerate();
		if (step != Step.GENERATING) {
			return lastGenerateOutcome != null
				? lastGenerateOutcome
				: new GenerateOutcome(PresenterResult.failure(statusMessage), null, null);
		}
		GenerateOutcome outcome = null;
		int guard = 32;
		while (step == Step.GENERATING && guard-- > 0) {
			outcome = tickGenerate();
		}
		if (step == Step.GENERATING) {
			failGeneration(PresenterResult.failure(BBTexts.get("beatblock.wizard.unknown_type")), pendingAutoMapResult);
			return lastGenerateOutcome;
		}
		return outcome != null ? outcome : lastGenerateOutcome;
	}

	public @Nullable GenerateOutcome lastGenerateOutcome() {
		return lastGenerateOutcome;
	}

	/**
	 * DONE 页摘要：优先使用 AutoMap 统计；Rhythm Drop 等无 AutoMap 结果时从 Timeline 计数。
	 */
	public DoneSummary doneSummary() {
		GenerateOutcome outcome = lastGenerateOutcome;
		if (outcome == null || !outcome.result().ok()) {
			return DoneSummary.empty();
		}
		String objectId = outcome.stageObjectId();
		String objectName = "";
		int blockCount = 0;
		if (objectId != null && !objectId.isBlank()) {
			RuntimeStageObject obj = toolPanelPresenter.getStageObject(objectId);
			if (obj != null) {
				objectName = obj.getName() != null ? obj.getName() : "";
				blockCount = obj.getBlocks().size();
			}
		}
		if (objectName.isBlank()) {
			objectName = resolvedStageObjectName();
		}

		SmartAutoMapEngine.AutoMapResult autoMap = outcome.autoMapResult();
		if (autoMap != null) {
			return new DoneSummary(
				objectName,
				blockCount,
				autoMap.getAnimationEvents(),
				autoMap.getCameraEvents(),
				autoMap.getParticleEvents()
			);
		}

		Timeline tl = timeline.get();
		return new DoneSummary(
			objectName,
			blockCount,
			countTrackEvents(tl, Timeline.TRACK_ID_ANIMATION_AUTO)
				+ countTrackEvents(tl, Timeline.TRACK_ID_ANIMATION_BLOCK),
			countTrackEvents(tl, Timeline.TRACK_ID_CAMERA),
			countTrackEvents(tl, Timeline.TRACK_ID_GLOBAL)
		);
	}

	private void runCreateStageObjectPhase() {
		setGenerationProgress(GenerationPhase.CREATE_STAGE_OBJECT, 0.15f);
		ToolPanelPresenter.StageObjectCreateRequest createRequest = new ToolPanelPresenter.StageObjectCreateRequest(
			pendingObjectName,
			false,
			GroupSortingStrategy.SEQUENTIAL,
			0.0
		);
		ToolPanelPresenter.CreateStageObjectOutcome createOutcome =
			toolPanelPresenter.createFromSelectionSnapshot(createRequest);
		if (!createOutcome.result().ok()) {
			failGeneration(createOutcome.result(), null);
			return;
		}
		pendingObjectId = createOutcome.objectId();
		activeTx.recordCreatedStageObject(pendingObjectId);
		if (creationType == CreationType.DROP_IMPACT) {
			generationPhase = GenerationPhase.CREATE_DROP_IMPACT;
			setGenerationProgress(GenerationPhase.CREATE_DROP_IMPACT, 0.35f);
		} else {
			generationPhase = GenerationPhase.CREATE_CHOREOGRAPHY;
			setGenerationProgress(GenerationPhase.CREATE_CHOREOGRAPHY, 0.35f);
		}
	}

	private void runChoreographyPhase() {
		setGenerationProgress(GenerationPhase.CREATE_CHOREOGRAPHY, 0.55f);
		AutoMapSettings settings = switch (creationType) {
			case CINEMATIC_BUILD -> buildAutoMapSettings(
				AutoMapStyle.CINEMATIC, Complexity.MEDIUM, true, false,
				ChoreographyLayerProfile.PHRASE, pendingObjectId
			);
			case RHYTHMIC_PERFORMANCE -> buildAutoMapSettings(
				AutoMapStyle.EDM, Complexity.MEDIUM, false, false,
				ChoreographyLayerProfile.PHRASE, pendingObjectId
			);
			case FULL_CHOREOGRAPHY -> buildAutoMapSettings(
				AutoMapStyle.EDM, Complexity.MEDIUM, true, true,
				ChoreographyLayerProfile.HERO_FULL, pendingObjectId
			);
			default -> null;
		};
		if (settings == null) {
			failGeneration(PresenterResult.failure(BBTexts.get("beatblock.wizard.unknown_type")), null);
			return;
		}
		var outcome = autoMapPresenter.generate(settings);
		pendingAutoMapResult = outcome.autoMapResult();
		if (pendingAutoMapResult != null) {
			activeTx.recordGenerationId(pendingAutoMapResult.getGenerationId());
		}
		if (!outcome.result().ok()) {
			failGeneration(outcome.result(), pendingAutoMapResult);
			return;
		}
		if (pendingCamera) {
			generationPhase = GenerationPhase.CREATE_CAMERA;
			setGenerationProgress(GenerationPhase.CREATE_CAMERA, 0.75f);
		} else if (pendingVfx) {
			generationPhase = GenerationPhase.CREATE_VFX;
			setGenerationProgress(GenerationPhase.CREATE_VFX, 0.85f);
		} else {
			generationPhase = GenerationPhase.FINALIZE;
			setGenerationProgress(GenerationPhase.FINALIZE, 0.95f);
		}
	}

	private void runCameraPhase() {
		// 预留：当前镜头已由 Smart Auto Map 一并生成；未来可拆成独立 Camera planner 调用。
		setGenerationProgress(GenerationPhase.CREATE_CAMERA, 0.8f);
		if (pendingVfx) {
			generationPhase = GenerationPhase.CREATE_VFX;
			setGenerationProgress(GenerationPhase.CREATE_VFX, 0.88f);
		} else {
			generationPhase = GenerationPhase.FINALIZE;
			setGenerationProgress(GenerationPhase.FINALIZE, 0.95f);
		}
	}

	private void runVfxPhase() {
		// 预留：当前 VFX 已由 Smart Auto Map 一并生成；未来可拆成独立 VFX pass。
		setGenerationProgress(GenerationPhase.CREATE_VFX, 0.92f);
		generationPhase = GenerationPhase.FINALIZE;
		setGenerationProgress(GenerationPhase.FINALIZE, 0.95f);
	}

	private void runDropImpactPhase() {
		setGenerationProgress(GenerationPhase.CREATE_DROP_IMPACT, 0.7f);
		RhythmDropPanelPresenter.GenerateRequest request = new RhythmDropPanelPresenter.GenerateRequest(
			RhythmDropPanelPresenter.defaultRequest().fallDurationSeconds(),
			RhythmDropPanelPresenter.defaultRequest().fallHeightBlocks(),
			true,
			pendingObjectId
		);
		var outcome = rhythmDropPresenter.generateFromSelection(request);
		if (outcome.generationId() != null) {
			activeTx.recordGenerationId(outcome.generationId());
		}
		if (!outcome.result().ok()) {
			failGeneration(outcome.result(), null);
			return;
		}
		generationPhase = GenerationPhase.FINALIZE;
		setGenerationProgress(GenerationPhase.FINALIZE, 0.95f);
	}

	private void runFinalizePhase() {
		setGenerationProgress(GenerationPhase.FINALIZE, 1f);
		QuickStartTimelineSnapshot before = activeTx.state().beforeTimeline();
		String objectId = pendingObjectId;
		RuntimeStageObject createdObject = objectId != null
			? toolPanelPresenter.getStageObject(objectId)
			: null;
		QuickStartTimelineSnapshot after = QuickStartTimelineSnapshot.capture(timeline.get());

		activeTx.commit();
		activeTx = null;

		boolean includeCamera = pendingCamera;
		boolean includeVfx = pendingVfx;
		pushGenerateUndoCommand(before, after, createdObject, includeCamera, includeVfx);

		statusMessage = successMessage(pendingObjectName, pendingAutoMapResult);
		lastGenerateOutcome = new GenerateOutcome(
			PresenterResult.success(statusMessage),
			pendingAutoMapResult,
			pendingObjectId
		);
		clearGenerationState();
		step = Step.DONE;
	}

	private void pushGenerateUndoCommand(
		QuickStartTimelineSnapshot before,
		QuickStartTimelineSnapshot after,
		@Nullable RuntimeStageObject createdObject,
		boolean includeCamera,
		boolean includeVfx
	) {
		TimelineEditor editor = timelineEditor.get();
		if (editor == null) {
			return;
		}
		editor.getCommandManager().execute(CreateQuickStartPerformanceCommand.alreadyApplied(
			timeline.get(),
			toolPanelPresenter.stageObjectSystemOrNull(),
			before,
			after,
			createdObject,
			includeCamera,
			includeVfx
		));
	}

	private String successMessage(String objectName, SmartAutoMapEngine.@Nullable AutoMapResult autoMapResult) {
		int events = autoMapResult != null ? autoMapResult.getAnimationEvents() : 0;
		return switch (creationType) {
			case CINEMATIC_BUILD -> BBTexts.get("beatblock.wizard.generated_cinematic", objectName, events);
			case RHYTHMIC_PERFORMANCE -> BBTexts.get("beatblock.wizard.generated_rhythmic", objectName, events);
			case DROP_IMPACT -> BBTexts.get("beatblock.wizard.generated_drop", objectName);
			case FULL_CHOREOGRAPHY -> BBTexts.get("beatblock.wizard.generated_full", objectName, events);
		};
	}

	private void setGenerationProgress(GenerationPhase phase, float fraction) {
		generationPhase = phase;
		generationFraction = Math.max(0f, Math.min(1f, fraction));
		generationMessage = switch (phase) {
			case CREATE_STAGE_OBJECT -> BBTexts.get("beatblock.wizard.generating.stage_object");
			case CREATE_CHOREOGRAPHY -> BBTexts.get("beatblock.wizard.generating.choreography");
			case CREATE_CAMERA -> BBTexts.get("beatblock.wizard.generating.camera");
			case CREATE_VFX -> BBTexts.get("beatblock.wizard.generating.vfx");
			case CREATE_DROP_IMPACT -> BBTexts.get("beatblock.wizard.generating.drop");
			case FINALIZE -> BBTexts.get("beatblock.wizard.generating.finalize");
			case IDLE -> "";
		};
	}

	private void failBeforeStart(String message) {
		statusMessage = message;
		lastGenerateOutcome = new GenerateOutcome(PresenterResult.failure(message), null, null);
		step = Step.GENERATE;
		clearGenerationState();
	}

	private void failGeneration(PresenterResult result, SmartAutoMapEngine.@Nullable AutoMapResult autoMapResult) {
		if (activeTx != null) {
			activeTx.rollback(toolPanelPresenter, timeline.get());
			activeTx = null;
		}
		statusMessage = result.messageOrEmpty();
		if (statusMessage.isBlank()) {
			statusMessage = BBTexts.get("beatblock.wizard.unknown_type");
		}
		lastGenerateOutcome = new GenerateOutcome(result, autoMapResult, null);
		clearGenerationState();
		step = Step.GENERATE;
	}

	private void abortGenerationIfNeeded() {
		if (activeTx != null) {
			activeTx.rollback(toolPanelPresenter, timeline.get());
			activeTx = null;
		}
	}

	private void clearGenerationState() {
		generationPhase = GenerationPhase.IDLE;
		generationMessage = "";
		generationFraction = 0f;
		pendingObjectId = null;
		pendingObjectName = null;
		pendingAutoMapResult = null;
		pendingCamera = false;
		pendingVfx = false;
		activeTx = null;
	}

	private static AutoMapSettings buildAutoMapSettings(
		AutoMapStyle style,
		Complexity complexity,
		boolean camera,
		boolean particles,
		ChoreographyLayerProfile layerProfile,
		String objectId
	) {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setStyle(style);
		settings.setComplexity(complexity);
		settings.setCameraEnabled(camera);
		settings.setParticlesEnabled(particles);
		settings.setLayerProfile(layerProfile);
		settings.setTargetObjectIds(List.of(objectId));
		return settings;
	}

	private static String failedMessage(AudioAsset asset) {
		String error = asset.getErrorMessage();
		if (error != null && !error.isBlank()) {
			return error.trim();
		}
		String info = asset.getInfoMessage();
		if (info != null && !info.isBlank()) {
			return info.trim();
		}
		return BBTexts.get("beatblock.wizard.analysis.failed");
	}

	private static @Nullable String statusOrDefault(AudioAsset asset, @Nullable String fallback) {
		String text = asset.getProcessingStatusText();
		if (text != null && !text.isBlank()) {
			return text.trim();
		}
		return fallback;
	}

	private boolean isMusicLoaded() {
		return resolveLoadedAudioAsset() != null;
	}

	private boolean hasBeatGrid() {
		Timeline tl = timeline.get();
		return ReferenceBeatResolver.resolveBeatTimesSeconds(tl).length > 0;
	}

	private int selectionCount() {
		BeatBlockSelectionManager mgr = selectionManager.get();
		return mgr != null ? mgr.getSelectionCount() : 0;
	}

	private static int countTrackEvents(@Nullable Timeline timeline, String trackId) {
		if (timeline == null || trackId == null || trackId.isBlank()) {
			return 0;
		}
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

	/**
	 * 音乐已可用于向导：Timeline 有 audioPath、磁盘文件仍在、且 AudioAssetManager 中有对应资产。
	 * 分析是否完成由 {@link #isAnalysisReady()} 单独判断。
	 */
	private @Nullable AudioAsset resolveLoadedAudioAsset() {
		String path = currentAudioPath();
		if (path.isBlank()) {
			return null;
		}
		Path file = resolveExistingAudioFile(path);
		if (file == null) {
			return null;
		}
		return findAssetByPath(file);
	}

	private static @Nullable Path resolveExistingAudioFile(String rawPath) {
		try {
			Path path = Path.of(rawPath.trim()).toAbsolutePath().normalize();
			return Files.isRegularFile(path) ? path : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static @Nullable AudioAsset findAssetByPath(Path file) {
		String normalized = file.toAbsolutePath().normalize().toString();
		for (AudioAsset asset : AudioAssetManager.getInstance().getAssets()) {
			if (asset.getPath() == null) {
				continue;
			}
			if (asset.getPath().toAbsolutePath().normalize().toString().equals(normalized)) {
				return asset;
			}
		}
		return null;
	}

	private String musicUnavailableReason(String path) {
		if (resolveExistingAudioFile(path) == null) {
			return BBTexts.get("beatblock.audio.path_invalid");
		}
		return BBTexts.get("beatblock.message.import_music_first");
	}

	private String resolvedStageObjectName() {
		if (stageObjectName != null && !stageObjectName.isBlank()) {
			return stageObjectName.trim();
		}
		return suggestStageObjectName();
	}

	String suggestStageObjectName() {
		var existingObjects = toolPanelPresenter.listStageObjects();
		int counter = 1;
		while (counter < 10_000) {
			String candidate = formatBuildingName(counter);
			boolean taken = existingObjects.stream().anyMatch(obj ->
				candidate.equalsIgnoreCase(obj.name()) || candidate.equalsIgnoreCase(obj.id()));
			if (!taken) {
				return candidate;
			}
			counter++;
		}
		return "Building " + counter;
	}

	/** Localize "Building N"; fall back when I18n is unavailable (unit tests). */
	private static String formatBuildingName(int counter) {
		String key = "beatblock.wizard.object_name.building";
		String formatted = BBTexts.get(key, counter);
		if (formatted == null || formatted.isBlank() || formatted.equals(key) || !formatted.contains(Integer.toString(counter))) {
			return "Building " + counter;
		}
		return formatted;
	}

	static String styleLabel(CreationType type) {
		return switch (type) {
			case CINEMATIC_BUILD -> BBTexts.get("beatblock.wizard.style.cinematic");
			case RHYTHMIC_PERFORMANCE -> BBTexts.get("beatblock.wizard.style.rhythmic");
			case DROP_IMPACT -> BBTexts.get("beatblock.wizard.style.drop");
			case FULL_CHOREOGRAPHY -> BBTexts.get("beatblock.wizard.style.full");
		};
	}

	private static String animationSummary(CreationType type) {
		return switch (type) {
			case CINEMATIC_BUILD -> BBTexts.get("beatblock.wizard.plan.animation.cinematic");
			case RHYTHMIC_PERFORMANCE -> BBTexts.get("beatblock.wizard.plan.animation.rhythmic");
			case DROP_IMPACT -> BBTexts.get("beatblock.wizard.plan.animation.drop");
			case FULL_CHOREOGRAPHY -> BBTexts.get("beatblock.wizard.plan.animation.full");
		};
	}

	private static String cameraSummary(CreationType type) {
		return switch (type) {
			case CINEMATIC_BUILD -> BBTexts.get("beatblock.wizard.plan.camera.restrained");
			case RHYTHMIC_PERFORMANCE, DROP_IMPACT -> BBTexts.get("beatblock.wizard.plan.camera.off");
			case FULL_CHOREOGRAPHY -> BBTexts.get("beatblock.wizard.plan.camera.auto");
		};
	}

	private static String vfxSummary(CreationType type) {
		return switch (type) {
			case FULL_CHOREOGRAPHY -> BBTexts.get("beatblock.wizard.plan.vfx.auto");
			case DROP_IMPACT -> BBTexts.get("beatblock.wizard.plan.vfx.impact");
			default -> BBTexts.get("beatblock.wizard.plan.vfx.off");
		};
	}
}
