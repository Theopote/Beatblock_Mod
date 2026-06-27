package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.engine.influence.InfluenceDimension;
import com.beatblock.ui.imgui.PresetChannelPreview;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editing.AnimationEventFormInput;
import com.beatblock.timeline.editing.AnimationEventPropertiesEditor;
import com.beatblock.timeline.editing.WorldTrajectoryEventParamsEditor;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.AnimationEditorViewState;
import com.beatblock.ui.presenter.EventPropertiesFormSnapshot;
import com.beatblock.ui.presenter.EventPropertiesOption;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.timeline.rendering.TrackRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 右侧方块动画事件属性面板。
 */
public class EventPropertiesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final int INPUT_BUFFER_SIZE = 128;

	private String boundRefKey;
	private final ImString timeBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString durationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString energyBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString energyThresholdBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString spatialDelayBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString blocksPerBeatBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString distancePaceSecondsBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString distancePaceMinGapBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraNearDistanceBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraFarDistanceBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraNearScaleBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraFarScaleBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraEdgePriorityBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString entryDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString idleDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString exitDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString placeBlockBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString flashBlockBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString singleBlockXBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString singleBlockYBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString singleBlockZBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString meteorHeightBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString meteorScatterBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString impactThresholdBuffer = new ImString(INPUT_BUFFER_SIZE);
	private String validationError;
	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;

	public EventPropertiesPanel() {
		this(PresenterFactories.eventPropertiesPresenter(), BeatBlock::getContext);
	}

	EventPropertiesPanel(EventPropertiesPresenter presenter, Supplier<BeatBlockContext> context) {
		this.presenter = presenter;
		this.context = context;
	}

	private BeatBlockContext runtime() {
		return context.get();
	}

	private static final String[] SPATIAL_MODE_LABELS = {
		"同时 (ALL)",
		"顺序 (SEQUENTIAL)",
		"径向 (RADIAL)",
		"随机 (RANDOM)",
		"螺旋 (SPIRAL)"
	};
	private static final String[] SPATIAL_MODE_VALUES = {
		"ALL",
		"SEQUENTIAL",
		"RADIAL",
		"RANDOM",
		"SPIRAL"
	};
	private static final String[] STEP_START_MODE_LABELS = {
		"下一个节拍 (NEXT_BEAT)",
		"立即开始 (IMMEDIATE)"
	};
	private static final String[] STEP_START_MODE_VALUES = {
		"NEXT_BEAT",
		"IMMEDIATE"
	};
	private static final String[] STEP_COMPLETION_LABELS = {
		"保持结束态 (KEEP)",
		"循环序列 (LOOP)"
	};
	private static final String[] STEP_COMPLETION_VALUES = {
		"KEEP",
		"LOOP"
	};
	private static final String[] PACING_MODE_LABELS = {
		"节拍网格 (BEAT_GRID)",
		"固定间隔 (FIXED_INTERVAL)",
		"跳跃距离 (DISTANCE)"
	};
	private static final String[] PACING_MODE_VALUES = {
		"BEAT_GRID",
		"FIXED_INTERVAL",
		"DISTANCE"
	};

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.EVENT_PROPERTIES_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.EVENT_PROPERTIES_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ImGui.text("事件属性");
			ImGui.separator();

			Timeline timeline = runtime().timeline();
			TimelineEditor editor = runtime().timelineEditor();
			if (timeline == null || editor == null) {
				ImGui.textDisabled("时间线未初始化。");
				return;
			}

			EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, editor.getSelectionState());
			if (!isAnimationRef(ref)) {
				boundRefKey = null;
				validationError = null;
				ImGui.textWrapped("选中方块动画事件后，可在此编辑属性。");
				return;
			}

			String rk = EventPropertiesRef.refKey(ref);
			if (!rk.equals(boundRefKey)) {
				bindBuffers(ref);
			}

			renderEventSummary(ref, timeline);
			ImGui.separator();

			boolean trackLocked = presenter.isTrackLocked(timeline, editor, ref.track().getId());
			if (trackLocked) {
				ImGui.textDisabled("当前轨道已锁定，属性只读。");
				ImGui.separator();
				ImGui.beginDisabled();
			}

			renderAnimationEditor(ref, timeline);

			if (trackLocked) {
				ImGui.endDisabled();
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.EVENT_PROPERTIES_WINDOW);
		}
	}

	private static boolean isAnimationRef(EventPropertiesRef ref) {
		return ref != null && ref.event() != null && ref.event().getType() == EventType.ANIMATION;
	}

	private void renderEventSummary(EventPropertiesRef ref, Timeline timeline) {
		ImGui.textDisabled("Track");
		ImGui.sameLine();
		ImGui.text(ref.track().getName().isBlank() ? ref.track().getId() : ref.track().getName());
		Map<String, Object> params = ref.event().getParameters();
		AnimationEditorViewState viewState = presenter.readAnimationEditorState(params);
		ImGui.textDisabled("Event ID");
		ImGui.sameLine();
		ImGui.text(ref.event().getId());
		if (Timeline.isBlockAnimationFeatureTrackId(ref.track().getId())) {
			ImGui.textDisabled("Feature Lane");
			ImGui.sameLine();
			ImGui.text(TrackRegistry.localizedName(Timeline.blockAnimationFeatureKeyFromTrackId(ref.track().getId())));
		}
		String sourceFeature = viewState.sourceFeature();
		if (!sourceFeature.isBlank()) {
			ImGui.textDisabled("Source Feature");
			ImGui.sameLine();
			ImGui.text(TrackRegistry.localizedName(sourceFeature));
		}
		String generatedBy = viewState.generatedBy();
		if (!generatedBy.isBlank()) {
			ImGui.textDisabled("Generated By");
			ImGui.sameLine();
			ImGui.text(generatedBy);
		}
		ImGui.textDisabled("Action Mode");
		ImGui.sameLine();
		ImGui.text(TimelineAnimationActionMode.fromValue(viewState.actionMode()).name());
	}

	private void renderAnimationEditor(EventPropertiesRef ref, Timeline timeline) {
		Map<String, Object> params = ref.event().getParameters();
		AnimationEditorViewState viewState = presenter.readAnimationEditorState(params);
		List<EventPropertiesOption> actionOptions = presenter.actionOptions();
		List<EventPropertiesOption> animationOptions = presenter.animationOptions();
		List<EventPropertiesOption> targetOptions = presenter.targetOptions();

		ImGui.text("Timing");
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("开始时间 (s)##eventTime", timeBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("持续时间 (s)##eventDuration", durationBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("能量 (0-1)##eventEnergy", energyBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("能量阈值 (0-1)##eventEnergyThreshold", energyThresholdBuffer);

		String currentAnimationId = viewState.animationId();
		String currentTargetId = viewState.targetId();
		String currentActionMode = viewState.actionMode();
		boolean inheritGroupSpatial = viewState.inheritGroupSpatial();
		boolean stepDispatch = viewState.stepDispatch();
		boolean cameraAdaptiveStep = viewState.cameraAdaptiveStep();
		boolean cameraFrustumGating = viewState.cameraFrustumGating();
		boolean usePhaseAnimation = viewState.usePhaseAnimation();
		ImInt stepStartModeIndex = new ImInt(indexOfValue(STEP_START_MODE_VALUES, viewState.stepStartMode()));
		ImInt stepCompletionIndex = new ImInt(indexOfValue(STEP_COMPLETION_VALUES, viewState.stepCompletionMode()));
		ImInt pacingModeIndex = new ImInt(indexOfValue(PACING_MODE_VALUES, viewState.pacingMode()));
		if (pacingModeIndex.get() < 0 || pacingModeIndex.get() >= PACING_MODE_VALUES.length) pacingModeIndex.set(0);
		ImInt actionIndex = new ImInt(indexOfOption(actionOptions, currentActionMode));
		ImInt animationIndex = new ImInt(indexOfOption(animationOptions, currentAnimationId));
		ImInt targetIndex = new ImInt(indexOfOption(targetOptions, currentTargetId));
		ImInt spatialModeIndex = new ImInt(indexOfValue(SPATIAL_MODE_VALUES, viewState.spatialMode()));
		if (spatialModeIndex.get() < 0 || spatialModeIndex.get() >= SPATIAL_MODE_VALUES.length) spatialModeIndex.set(0);
		String[] actionLabels = optionLabels(actionOptions);
		String[] animationLabels = optionLabels(animationOptions);
		String[] targetLabels = optionLabels(targetOptions);

		ImGui.spacing();
		ImGui.text("Binding");
		if (ImGui.combo("动作模式##eventActionMode", actionIndex, actionLabels)) {
			validationError = null;
		}
		if (ImGui.combo("动画模板 (Preset)##eventAnimation", animationIndex, animationLabels)) {
			validationError = null;
		}
		String selectedAnimationId = animationOptions.get(animationIndex.get()).id();
		renderPresetChannelPreview(selectedAnimationId);
		boolean vfxEnabled = viewState.vfxEnabled();
		ImBoolean vfxEnabledProxy = new ImBoolean(vfxEnabled);
		if (ImGui.checkbox("粒子强调 (VFX)##eventVfxEnabled", vfxEnabledProxy)) {
			vfxEnabled = vfxEnabledProxy.get();
			validationError = null;
		}
		BlockInfluencePreset selectedPreset = BlockInfluencePresets.get(animationOptions.get(animationIndex.get()).id());
		if (selectedPreset != null && !selectedPreset.channelsFor(InfluenceDimension.APPEARANCE).isEmpty()) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("踩点闪烁方块##eventFlashBlock", flashBlockBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("APPEARANCE 通道在动画中点切换到此方块，结束后还原。默认: minecraft:gold_block");
			}
		}
		if (ImGui.combo("目标对象##eventTarget", targetIndex, targetLabels)) {
			validationError = null;
		}
		renderWorldTrajectoryParams(selectedAnimationId);
		ImBoolean stepDispatchProxy = new ImBoolean(stepDispatch);
		if (ImGui.checkbox("每拍推进序列 (STEP)##eventDispatchStep", stepDispatchProxy)) {
			stepDispatch = stepDispatchProxy.get();
			validationError = null;
		}
		if (stepDispatch) {
			if (ImGui.combo("节奏来源##eventPacingMode", pacingModeIndex, PACING_MODE_LABELS)) {
				validationError = null;
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("跑酷推荐「跳跃距离」：按方块路径 3D 距离累加时间；建造常用「节拍网格」");
			}
			boolean distancePacing = "DISTANCE".equals(PACING_MODE_VALUES[pacingModeIndex.get()]);
			if (!distancePacing) {
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("每拍方块数##eventBlocksPerBeat", blocksPerBeatBuffer);
			}
			if (distancePacing) {
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("每方块距离 (秒)##eventDistancePaceSeconds", distancePaceSecondsBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("最小间隔 (秒)##eventDistancePaceMinGap", distancePaceMinGapBuffer);
			}
			if (ImGui.combo("起始对齐##eventStepStartMode", stepStartModeIndex, STEP_START_MODE_LABELS)) {
				validationError = null;
			}
			if (ImGui.combo("完成后行为##eventStepCompletionMode", stepCompletionIndex, STEP_COMPLETION_LABELS)) {
				validationError = null;
			}
			ImBoolean cameraAdaptiveProxy = new ImBoolean(cameraAdaptiveStep);
			if (ImGui.checkbox("镜头距离自适应推进##eventCameraAdaptiveStep", cameraAdaptiveProxy)) {
				cameraAdaptiveStep = cameraAdaptiveProxy.get();
				validationError = null;
			}
			if (cameraAdaptiveStep) {
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("近距离阈值##eventCameraNearDistance", cameraNearDistanceBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("远距离阈值##eventCameraFarDistance", cameraFarDistanceBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("近景倍率##eventCameraNearScale", cameraNearScaleBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("远景倍率##eventCameraFarScale", cameraFarScaleBuffer);
			}
			ImBoolean frustumGatingProxy = new ImBoolean(cameraFrustumGating);
			if (ImGui.checkbox("镜头视椎体门控 (暂停出屏)##eventCameraFrustumGating", frustumGatingProxy)) {
				cameraFrustumGating = frustumGatingProxy.get();
				validationError = null;
			}
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("边界优先级 (0-1)##eventCameraEdgePriority", cameraEdgePriorityBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("0 = 无边界优先 | 1 = 优先边界方块");
			}
			ImBoolean phaseAnimationProxy = new ImBoolean(usePhaseAnimation);
			if (ImGui.checkbox("三段式动画 (进-保-退)##eventUsePhaseAnimation", phaseAnimationProxy)) {
				usePhaseAnimation = phaseAnimationProxy.get();
				validationError = null;
			}
			if (usePhaseAnimation) {
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("进入阶段 (%)##eventEntryDuration", entryDurationBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("保持阶段 (%)##eventIdleDuration", idleDurationBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("退出阶段 (%)##eventExitDuration", exitDurationBuffer);
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip("百分比应相加为100% (例如: 20% 入场, 60% 保持, 20% 出场)");
				}
			}
		}
		ImBoolean inheritSpatialProxy = new ImBoolean(inheritGroupSpatial);
		if (ImGui.checkbox("继承组排序/延迟##eventInheritGroupSpatial", inheritSpatialProxy)) {
			inheritGroupSpatial = inheritSpatialProxy.get();
			validationError = null;
		}
		if (!inheritGroupSpatial) {
			if (ImGui.combo("空间调度##eventSpatialMode", spatialModeIndex, SPATIAL_MODE_LABELS)) {
				validationError = null;
			}
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("步进延迟 (s)##eventSpatialDelay", spatialDelayBuffer);
		}
		TimelineAnimationActionMode selectedActionMode = TimelineAnimationActionMode.fromValue(actionOptions.get(actionIndex.get()).id());
		if (selectedActionMode == TimelineAnimationActionMode.PLACE) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("放置方块ID##eventPlaceBlock", placeBlockBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("例如: minecraft:diamond_block");
			}
		}

		ImGui.spacing();
		ImGui.text("Metadata");
		ImGui.textDisabled(String.format(Locale.ROOT, "Mapping: %s", viewState.mappingProfile()));
		ImGui.textDisabled(String.format(Locale.ROOT, "Source Stem: %s", viewState.sourceStem()));
		renderRuntimeStatus(ref);

		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}

		ImGui.spacing();
		boolean applied = ImGui.button("应用##eventPropertiesApply", 120f, 0f);
		ImGui.sameLine();
		boolean reset = ImGui.button("重置##eventPropertiesReset", 120f, 0f);

		if (applied) {
			applyAnimationChanges(ref, timeline,
				actionOptions.get(actionIndex.get()).id(),
				animationOptions.get(animationIndex.get()).id(),
				targetOptions.get(targetIndex.get()).id(),
				inheritGroupSpatial,
				SPATIAL_MODE_VALUES[Math.max(0, Math.min(spatialModeIndex.get(), SPATIAL_MODE_VALUES.length - 1))],
				stepDispatch,
				STEP_START_MODE_VALUES[Math.max(0, Math.min(stepStartModeIndex.get(), STEP_START_MODE_VALUES.length - 1))],
				STEP_COMPLETION_VALUES[Math.max(0, Math.min(stepCompletionIndex.get(), STEP_COMPLETION_VALUES.length - 1))],
				PACING_MODE_VALUES[Math.max(0, Math.min(pacingModeIndex.get(), PACING_MODE_VALUES.length - 1))],
				cameraAdaptiveStep,
				cameraFrustumGating,
				usePhaseAnimation,
				vfxEnabled);
		}
		if (reset) {
			bindBuffers(ref);
		}
	}

	private void renderRuntimeStatus(EventPropertiesRef ref) {
		String eventId = ref != null && ref.event() != null ? ref.event().getId() : "";
		if (eventId == null || eventId.isBlank()) return;
		BeatBlockClientDriver.TimelineActionExecutionReport report = BeatBlockClientDriver.getTimelineActionExecutionReport(eventId);
		if (report == null) return;

		long ageMs = Math.max(0L, System.currentTimeMillis() - report.timestampMs());
		ImGui.textDisabled(String.format(Locale.ROOT,
			"Runtime: %s | %s | mutations=%d | %dms ago",
			report.actionMode().name(),
			report.status(),
			report.mutationCount(),
			ageMs));
		if (report.detail() != null && !report.detail().isBlank()) {
			ImGui.textDisabled("detail: " + report.detail());
		}
	}

	private void applyAnimationChanges(EventPropertiesRef ref, Timeline timeline, String actionMode, String animationId,
	                                  String targetObjectId, boolean inheritGroupSpatial, String spatialMode,
	                                  boolean stepDispatch, String stepStartMode, String stepCompletionMode,
	                                  String pacingMode, boolean cameraAdaptiveStep, boolean cameraFrustumGating,
	                                  boolean usePhaseAnimation, boolean vfxEnabled) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = "时间线编辑器未初始化。";
			return;
		}
		try {
			AnimationEventFormInput input = AnimationEventPropertiesEditor.parseFormInput(
				valueOf(timeBuffer),
				valueOf(durationBuffer),
				valueOf(energyBuffer),
				valueOf(energyThresholdBuffer),
				valueOf(spatialDelayBuffer),
				valueOf(blocksPerBeatBuffer),
				valueOf(distancePaceSecondsBuffer),
				valueOf(distancePaceMinGapBuffer),
				valueOf(cameraNearDistanceBuffer),
				valueOf(cameraFarDistanceBuffer),
				valueOf(cameraNearScaleBuffer),
				valueOf(cameraFarScaleBuffer),
				valueOf(cameraEdgePriorityBuffer),
				valueOf(entryDurationBuffer),
				valueOf(idleDurationBuffer),
				valueOf(exitDurationBuffer),
				valueOf(placeBlockBuffer),
				valueOf(flashBlockBuffer),
				actionMode,
				animationId,
				targetObjectId,
				inheritGroupSpatial,
				spatialMode,
				stepDispatch,
				stepStartMode,
				stepCompletionMode,
				pacingMode,
				cameraAdaptiveStep,
				cameraFrustumGating,
				usePhaseAnimation,
				vfxEnabled
			);
			var result = presenter.applyAnimationEvent(
				ref,
				timeline,
				editor.getCommandManager(),
				input,
				buildTrajectoryFormInput(animationId)
			);
			if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
				validationError = message;
				return;
			}
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = "时间、持续时间或能量格式不正确。";
		}
	}

	private void bindBuffers(EventPropertiesRef ref) {
		applyFormSnapshot(presenter.buildFormSnapshot(ref, runtime().timeline()));
		validationError = null;
	}

	private void applyFormSnapshot(EventPropertiesFormSnapshot snap) {
		boundRefKey = snap.refKey();
		timeBuffer.set(snap.time());
		durationBuffer.set(snap.duration());
		energyBuffer.set(snap.energy());
		energyThresholdBuffer.set(snap.energyThreshold());
		spatialDelayBuffer.set(snap.spatialDelay());
		blocksPerBeatBuffer.set(snap.blocksPerBeat());
		distancePaceSecondsBuffer.set(snap.distancePaceSeconds());
		distancePaceMinGapBuffer.set(snap.distancePaceMinGap());
		cameraNearDistanceBuffer.set(snap.cameraNearDistance());
		cameraFarDistanceBuffer.set(snap.cameraFarDistance());
		cameraNearScaleBuffer.set(snap.cameraNearScale());
		cameraFarScaleBuffer.set(snap.cameraFarScale());
		cameraEdgePriorityBuffer.set(snap.cameraEdgePriority());
		placeBlockBuffer.set(snap.placeBlock());
		flashBlockBuffer.set(snap.flashBlock());
		singleBlockXBuffer.set(snap.singleBlockX());
		singleBlockYBuffer.set(snap.singleBlockY());
		singleBlockZBuffer.set(snap.singleBlockZ());
		meteorHeightBuffer.set(snap.meteorHeight());
		meteorScatterBuffer.set(snap.meteorScatter());
		impactThresholdBuffer.set(snap.impactThreshold());
	}

	private void renderWorldTrajectoryParams(String animationId) {
		if (!WorldTrajectoryEventParamsEditor.supports(animationId)) {
			return;
		}
		boolean rhythmDrop = WorldTrajectoryEventParamsEditor.RHYTHM_DROP_ANIMATION_ID.equalsIgnoreCase(animationId);
		ImGui.spacing();
		ImGui.textDisabled(rhythmDrop ? "节奏坠落 (RhythmDrop)" : "流星轨迹 (Meteor)");
		ImGui.textWrapped(rhythmDrop
			? "精确落点与下落高度决定视觉方块命中坐标；命中阈值控制落地粒子触发时刻（默认 0.92）。"
			: "下落高度与横向散射控制 WORLD_TRAJECTORY 路径；可指定单方块落点。");
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("落点 X##eventSingleBlockX", singleBlockXBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("落点 Y##eventSingleBlockY", singleBlockYBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("落点 Z##eventSingleBlockZ", singleBlockZBuffer);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("留空则使用目标 StageObject 的方块集合；填写则仅对该坐标做动画。");
		}
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("下落高度 (格)##eventMeteorHeight", meteorHeightBuffer);
		if (rhythmDrop) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("命中阈值 (0-1)##eventImpactThreshold", impactThresholdBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("动画进度达到该值时触发 rhythm_impact 粒子；RhythmDrop 横向散射固定为 0。");
			}
		} else {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("横向散射##eventMeteorScatter", meteorScatterBuffer);
		}
	}

	private WorldTrajectoryEventParamsEditor.FormInput buildTrajectoryFormInput(String animationId) {
		if (!WorldTrajectoryEventParamsEditor.supports(animationId)) {
			return null;
		}
		return new WorldTrajectoryEventParamsEditor.FormInput(
			valueOf(singleBlockXBuffer),
			valueOf(singleBlockYBuffer),
			valueOf(singleBlockZBuffer),
			valueOf(meteorHeightBuffer),
			valueOf(meteorScatterBuffer),
			valueOf(impactThresholdBuffer)
		);
	}

	private void renderPresetChannelPreview(String presetId) {
		PresetChannelPreview.renderCollapsible("Preset 通道##eventPresetChannels", BlockInfluencePresets.get(presetId));
	}

	private static int indexOfOption(List<EventPropertiesOption> options, String id) {
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).id().equals(id)) {
				return i;
			}
		}
		return 0;
	}

	private static String[] optionLabels(List<EventPropertiesOption> options) {
		String[] labels = new String[options.size()];
		for (int i = 0; i < options.size(); i++) {
			labels[i] = options.get(i).label();
		}
		return labels;
	}

	private static int indexOfValue(String[] values, String target) {
		if (values == null || values.length == 0) return 0;
		if (target == null) return 0;
		for (int i = 0; i < values.length; i++) {
			if (target.equalsIgnoreCase(values[i])) return i;
		}
		return 0;
	}

	private static String valueOf(ImString text) {
		String value = text != null ? text.get() : null;
		return value != null ? value : "";
	}
}
