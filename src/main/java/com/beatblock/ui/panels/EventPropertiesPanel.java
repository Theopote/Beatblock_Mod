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
import com.beatblock.ui.i18n.BBTexts;
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
	private String batchMessage;
	private String animationPreviewPresetId;
	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;
	private final ImString batchEnergyBuffer = new ImString(16);
	private final ImString batchTimeOffsetBuffer = new ImString(16);
	private final ImInt batchAnimationIndex = new ImInt(0);
	private final ImBoolean batchApplyEnergy = new ImBoolean(true);
	private final ImBoolean batchApplyAnimation = new ImBoolean(false);
	private final ImBoolean batchApplyTimeOffset = new ImBoolean(false);
	private final ImBoolean livePreviewOnApply = new ImBoolean(true);

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

	private static String[] spatialModeLabels() {
		return BBTexts.labels(
			"beatblock.event.spatial.all",
			"beatblock.event.spatial.sequential",
			"beatblock.event.spatial.radial",
			"beatblock.event.spatial.random",
			"beatblock.event.spatial.spiral"
		);
	}
	private static final String[] SPATIAL_MODE_VALUES = {
		"ALL",
		"SEQUENTIAL",
		"RADIAL",
		"RANDOM",
		"SPIRAL"
	};
	private static String[] stepStartModeLabels() {
		return BBTexts.labels(
			"beatblock.event.step_start.next_beat",
			"beatblock.event.step_start.immediate"
		);
	}
	private static final String[] STEP_START_MODE_VALUES = {
		"NEXT_BEAT",
		"IMMEDIATE"
	};
	private static String[] stepCompletionLabels() {
		return BBTexts.labels(
			"beatblock.event.step_completion.keep",
			"beatblock.event.step_completion.loop"
		);
	}
	private static final String[] STEP_COMPLETION_VALUES = {
		"KEEP",
		"LOOP"
	};
	private static String[] pacingModeLabels() {
		return BBTexts.labels(
			"beatblock.event.pacing.beat_grid",
			"beatblock.event.pacing.fixed_interval",
			"beatblock.event.pacing.distance"
		);
	}
	private static final String[] PACING_MODE_VALUES = {
		"BEAT_GRID",
		"FIXED_INTERVAL",
		"DISTANCE"
	};

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.eventPropertiesWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.eventPropertiesWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ImGui.text(BBTexts.get("beatblock.event.title"));
			ImGui.separator();

			Timeline timeline = runtime().timeline();
			TimelineEditor editor = runtime().timelineEditor();
			if (timeline == null || editor == null) {
				ImGui.textDisabled(BBTexts.get("beatblock.common.timeline_not_initialized"));
				return;
			}

			EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, editor.getSelectionState());
			if (!isAnimationRef(ref)) {
				boundRefKey = null;
				validationError = null;
				batchMessage = null;
				ImGui.textWrapped(BBTexts.get("beatblock.event.select_hint"));
				return;
			}

			int batchCount = presenter.countSelectedAnimationEvents(timeline, editor.getSelectionState());
			if (batchCount > 1) {
				renderBatchEditor(timeline, editor, batchCount);
				ImGui.separator();
			}

			String rk = EventPropertiesRef.refKey(ref);
			if (!rk.equals(boundRefKey)) {
				bindBuffers(ref);
			}

			boolean trackLocked = presenter.isTrackLocked(timeline, editor, ref.track().getId());
			if (trackLocked) {
				ImGui.textDisabled(BBTexts.get("beatblock.event.track_locked"));
				ImGui.separator();
				ImGui.beginDisabled();
			}

			renderAnimationEditor(ref, timeline, editor, batchCount);

			if (trackLocked) {
				ImGui.endDisabled();
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.eventPropertiesWindow());
		}
	}

	private static boolean isAnimationRef(EventPropertiesRef ref) {
		return ref != null && ref.event() != null && ref.event().getType() == EventType.ANIMATION;
	}

	private void renderEventSummary(EventPropertiesRef ref, Timeline timeline) {
		ImGui.textDisabled(BBTexts.get("beatblock.event.track"));
		ImGui.sameLine();
		ImGui.text(ref.track().getName().isBlank() ? ref.track().getId() : ref.track().getName());
		Map<String, Object> params = ref.event().getParameters();
		AnimationEditorViewState viewState = presenter.readAnimationEditorState(params);
		ImGui.textDisabled(BBTexts.get("beatblock.event.event_id"));
		ImGui.sameLine();
		ImGui.text(ref.event().getId());
		if (Timeline.isBlockAnimationFeatureTrackId(ref.track().getId())) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.feature_lane"));
			ImGui.sameLine();
			ImGui.text(TrackRegistry.localizedName(Timeline.blockAnimationFeatureKeyFromTrackId(ref.track().getId())));
		}
		String sourceFeature = viewState.sourceFeature();
		if (!sourceFeature.isBlank()) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.source_feature"));
			ImGui.sameLine();
			ImGui.text(TrackRegistry.localizedName(sourceFeature));
		}
		String generatedBy = viewState.generatedBy();
		if (!generatedBy.isBlank()) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.generated_by"));
			ImGui.sameLine();
			ImGui.text(generatedBy);
		}
		ImGui.textDisabled(BBTexts.get("beatblock.event.action_mode"));
		ImGui.sameLine();
		ImGui.text(TimelineAnimationActionMode.fromValue(viewState.actionMode()).name());
	}

	private void renderBatchEditor(Timeline timeline, TimelineEditor editor, int batchCount) {
		ImGui.textColored(0.4f, 0.8f, 1f, 1f, BBTexts.get("beatblock.event.batch.title", batchCount));
		ImGui.spacing();

		List<EventPropertiesOption> animationOptions = presenter.animationOptions();
		String[] animationLabels = optionLabels(animationOptions);

		ImGui.checkbox(BBTexts.get("beatblock.event.batch.apply_energy") + "##batchEnergy", batchApplyEnergy);
		ImGui.setNextItemWidth(-1f);
		if (!batchApplyEnergy.get()) ImGui.beginDisabled();
		ImGui.inputText(BBTexts.get("beatblock.event.energy") + "##batchEnergyVal", batchEnergyBuffer);
		if (!batchApplyEnergy.get()) ImGui.endDisabled();

		ImGui.checkbox(BBTexts.get("beatblock.event.batch.apply_animation") + "##batchAnim", batchApplyAnimation);
		ImGui.setNextItemWidth(-1f);
		if (!batchApplyAnimation.get()) ImGui.beginDisabled();
		ImGui.combo(BBTexts.get("beatblock.event.animation_preset") + "##batchAnimVal", batchAnimationIndex, animationLabels);
		if (!batchApplyAnimation.get()) ImGui.endDisabled();

		ImGui.checkbox(BBTexts.get("beatblock.event.batch.apply_time_offset") + "##batchTime", batchApplyTimeOffset);
		ImGui.setNextItemWidth(-1f);
		if (!batchApplyTimeOffset.get()) ImGui.beginDisabled();
		ImGui.inputText(BBTexts.get("beatblock.event.batch.time_offset") + "##batchTimeVal", batchTimeOffsetBuffer);
		if (!batchApplyTimeOffset.get()) ImGui.endDisabled();
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.batch.time_offset.tooltip"));
		}

		if (ImGui.button(BBTexts.get("beatblock.event.batch.apply") + "##batchApply", -1f, 0f)) {
			applyBatchEdit(timeline, editor, animationOptions);
		}

		if (batchMessage != null && !batchMessage.isBlank()) {
			ImGui.textWrapped(batchMessage);
		}
	}

	private void applyBatchEdit(Timeline timeline, TimelineEditor editor, List<EventPropertiesOption> animationOptions) {
		Float energy = null;
		if (batchApplyEnergy.get()) {
			try {
				energy = Float.parseFloat(batchEnergyBuffer.get().trim());
			} catch (NumberFormatException ex) {
				batchMessage = BBTexts.get("beatblock.event.invalid_number");
				return;
			}
		}
		String animationId = null;
		if (batchApplyAnimation.get() && batchAnimationIndex.get() >= 0
				&& batchAnimationIndex.get() < animationOptions.size()) {
			animationId = animationOptions.get(batchAnimationIndex.get()).id();
		}
		Double timeOffset = null;
		if (batchApplyTimeOffset.get()) {
			try {
				timeOffset = Double.parseDouble(batchTimeOffsetBuffer.get().trim());
			} catch (NumberFormatException ex) {
				batchMessage = BBTexts.get("beatblock.event.invalid_number");
				return;
			}
		}
		var outcome = presenter.applyBatchAnimationEdit(
			timeline,
			editor.getSelectionState(),
			editor.getCommandManager(),
			new EventPropertiesPresenter.BatchAnimationEditRequest(energy, animationId, timeOffset)
		);
		batchMessage = outcome.success()
			? BBTexts.get("beatblock.event.batch.applied", outcome.updatedCount())
			: outcome.errorMessage();
	}

	private void renderAnimationEditor(EventPropertiesRef ref, Timeline timeline, TimelineEditor editor, int batchCount) {
		Map<String, Object> params = ref.event().getParameters();
		AnimationEditorViewState viewState = presenter.readAnimationEditorState(params);
		List<EventPropertiesOption> actionOptions = presenter.actionOptions();
		List<EventPropertiesOption> animationOptions = presenter.animationOptions();
		List<EventPropertiesOption> targetOptions = presenter.targetOptions();

		ImInt stepStartModeIndex = new ImInt(indexOfValue(STEP_START_MODE_VALUES, viewState.stepStartMode()));
		ImInt stepCompletionIndex = new ImInt(indexOfValue(STEP_COMPLETION_VALUES, viewState.stepCompletionMode()));
		ImInt pacingModeIndex = new ImInt(indexOfValue(PACING_MODE_VALUES, viewState.pacingMode()));
		if (pacingModeIndex.get() < 0 || pacingModeIndex.get() >= PACING_MODE_VALUES.length) pacingModeIndex.set(0);
		ImInt actionIndex = new ImInt(indexOfOption(actionOptions, viewState.actionMode()));
		ImInt animationIndex = new ImInt(indexOfOption(animationOptions, viewState.animationId()));
		ImInt targetIndex = new ImInt(indexOfOption(targetOptions, viewState.targetId()));
		ImInt spatialModeIndex = new ImInt(indexOfValue(SPATIAL_MODE_VALUES, viewState.spatialMode()));
		if (spatialModeIndex.get() < 0 || spatialModeIndex.get() >= SPATIAL_MODE_VALUES.length) spatialModeIndex.set(0);
		ImBoolean inheritGroupSpatial = new ImBoolean(viewState.inheritGroupSpatial());
		ImBoolean stepDispatch = new ImBoolean(viewState.stepDispatch());
		ImBoolean cameraAdaptiveStep = new ImBoolean(viewState.cameraAdaptiveStep());
		ImBoolean cameraFrustumGating = new ImBoolean(viewState.cameraFrustumGating());
		ImBoolean usePhaseAnimation = new ImBoolean(viewState.usePhaseAnimation());
		ImBoolean vfxEnabled = new ImBoolean(viewState.vfxEnabled());

		String[] actionLabels = optionLabels(actionOptions);
		String[] animationLabels = optionLabels(animationOptions);
		String[] targetLabels = optionLabels(targetOptions);

		if (batchCount > 1) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.batch.primary_hint", batchCount));
			ImGui.spacing();
		}

		if (ImGui.beginTabBar("##eventPropTabs")) {
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.basic"))) {
				renderBasicTab(animationOptions, actionIndex, animationIndex, targetIndex,
					actionLabels, animationLabels, targetLabels, vfxEnabled);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.spatial"))) {
				renderSpatialTab(actionOptions, actionIndex, animationOptions, animationIndex,
					inheritGroupSpatial, spatialModeIndex);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.advanced"))) {
				renderAdvancedTab(stepDispatch, stepStartModeIndex, stepCompletionIndex, pacingModeIndex,
					cameraAdaptiveStep, cameraFrustumGating, usePhaseAnimation);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.info"))) {
				renderEventSummary(ref, timeline);
				ImGui.spacing();
				ImGui.text(BBTexts.get("beatblock.event.metadata"));
				ImGui.textDisabled(BBTexts.get("beatblock.event.mapping", viewState.mappingProfile()));
				ImGui.textDisabled(BBTexts.get("beatblock.event.source_stem", viewState.sourceStem()));
				renderRuntimeStatus(ref);
				ImGui.endTabItem();
			}
			ImGui.endTabBar();
		}

		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}

		ImGui.spacing();
		ImGui.checkbox(BBTexts.get("beatblock.event.live_preview") + "##eventLivePreview", livePreviewOnApply);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.live_preview.tooltip"));
		}

		ImGui.spacing();
		boolean applied = ImGui.button(BBTexts.get("beatblock.common.apply") + "##eventPropertiesApply", 120f, 0f);
		ImGui.sameLine();
		boolean reset = ImGui.button(BBTexts.get("beatblock.common.reset") + "##eventPropertiesReset", 120f, 0f);

		if (applied) {
			applyAnimationChanges(ref, timeline,
				actionOptions.get(actionIndex.get()).id(),
				animationOptions.get(animationIndex.get()).id(),
				targetOptions.get(targetIndex.get()).id(),
				inheritGroupSpatial.get(),
				SPATIAL_MODE_VALUES[Math.max(0, Math.min(spatialModeIndex.get(), SPATIAL_MODE_VALUES.length - 1))],
				stepDispatch.get(),
				STEP_START_MODE_VALUES[Math.max(0, Math.min(stepStartModeIndex.get(), STEP_START_MODE_VALUES.length - 1))],
				STEP_COMPLETION_VALUES[Math.max(0, Math.min(stepCompletionIndex.get(), STEP_COMPLETION_VALUES.length - 1))],
				PACING_MODE_VALUES[Math.max(0, Math.min(pacingModeIndex.get(), PACING_MODE_VALUES.length - 1))],
				cameraAdaptiveStep.get(),
				cameraFrustumGating.get(),
				usePhaseAnimation.get(),
				vfxEnabled.get());
		}
		if (reset) {
			bindBuffers(ref);
		}
		renderAnimationPreviewPopup();
	}

	private void renderBasicTab(
		List<EventPropertiesOption> animationOptions,
		ImInt actionIndex,
		ImInt animationIndex,
		ImInt targetIndex,
		String[] actionLabels,
		String[] animationLabels,
		String[] targetLabels,
		ImBoolean vfxEnabled
	) {
		ImGui.text(BBTexts.get("beatblock.event.timing"));
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.start_time") + "##eventTime", timeBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.duration") + "##eventDuration", durationBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.energy") + "##eventEnergy", energyBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.energy_threshold") + "##eventEnergyThreshold", energyThresholdBuffer);

		ImGui.spacing();
		ImGui.text(BBTexts.get("beatblock.event.binding"));
		if (ImGui.combo(BBTexts.get("beatblock.event.action_mode_combo") + "##eventActionMode", actionIndex, actionLabels)) {
			validationError = null;
		}
		if (ImGui.combo(BBTexts.get("beatblock.event.animation_preset") + "##eventAnimation", animationIndex, animationLabels)) {
			validationError = null;
		}
		String selectedAnimationId = animationOptions.get(animationIndex.get()).id();
		renderPresetChannelPreview(selectedAnimationId);
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.event.preview_animation") + "##eventAnimPreview")) {
			animationPreviewPresetId = selectedAnimationId;
			ImGui.openPopup("##eventAnimPreviewPopup");
		}
		if (ImGui.checkbox(BBTexts.get("beatblock.event.vfx") + "##eventVfxEnabled", vfxEnabled)) {
			validationError = null;
		}
		BlockInfluencePreset selectedPreset = BlockInfluencePresets.get(selectedAnimationId);
		if (selectedPreset != null && !selectedPreset.channelsFor(InfluenceDimension.APPEARANCE).isEmpty()) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.flash_block") + "##eventFlashBlock", flashBlockBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.event.vfx.tooltip"));
			}
		}
		if (ImGui.combo(BBTexts.get("beatblock.event.target") + "##eventTarget", targetIndex, targetLabels)) {
			validationError = null;
		}
	}

	private void renderSpatialTab(
		List<EventPropertiesOption> actionOptions,
		ImInt actionIndex,
		List<EventPropertiesOption> animationOptions,
		ImInt animationIndex,
		ImBoolean inheritGroupSpatial,
		ImInt spatialModeIndex
	) {
		String selectedAnimationId = animationOptions.get(animationIndex.get()).id();
		renderWorldTrajectoryParams(selectedAnimationId);
		if (ImGui.checkbox(BBTexts.get("beatblock.event.inherit_spatial") + "##eventInheritGroupSpatial", inheritGroupSpatial)) {
			validationError = null;
		}
		if (!inheritGroupSpatial.get()) {
			if (ImGui.combo(BBTexts.get("beatblock.event.spatial_mode") + "##eventSpatialMode", spatialModeIndex, spatialModeLabels())) {
				validationError = null;
			}
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.spatial_delay") + "##eventSpatialDelay", spatialDelayBuffer);
		}
		TimelineAnimationActionMode selectedActionMode = TimelineAnimationActionMode.fromValue(actionOptions.get(actionIndex.get()).id());
		if (selectedActionMode == TimelineAnimationActionMode.PLACE) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.place_block") + "##eventPlaceBlock", placeBlockBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.event.place_block.tooltip"));
			}
		}
	}

	private void renderAdvancedTab(
		ImBoolean stepDispatch,
		ImInt stepStartModeIndex,
		ImInt stepCompletionIndex,
		ImInt pacingModeIndex,
		ImBoolean cameraAdaptiveStep,
		ImBoolean cameraFrustumGating,
		ImBoolean usePhaseAnimation
	) {
		if (ImGui.checkbox(BBTexts.get("beatblock.event.step_dispatch") + "##eventDispatchStep", stepDispatch)) {
			validationError = null;
		}
		if (!stepDispatch.get()) {
			return;
		}
		if (ImGui.combo(BBTexts.get("beatblock.event.pacing_mode") + "##eventPacingMode", pacingModeIndex, pacingModeLabels())) {
			validationError = null;
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.pacing_mode.tooltip"));
		}
		boolean distancePacing = "DISTANCE".equals(PACING_MODE_VALUES[pacingModeIndex.get()]);
		if (!distancePacing) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.blocks_per_beat") + "##eventBlocksPerBeat", blocksPerBeatBuffer);
		}
		if (distancePacing) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.distance_pace") + "##eventDistancePaceSeconds", distancePaceSecondsBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.min_gap") + "##eventDistancePaceMinGap", distancePaceMinGapBuffer);
		}
		if (ImGui.combo(BBTexts.get("beatblock.event.step_start") + "##eventStepStartMode", stepStartModeIndex, stepStartModeLabels())) {
			validationError = null;
		}
		if (ImGui.combo(BBTexts.get("beatblock.event.step_completion") + "##eventStepCompletionMode", stepCompletionIndex, stepCompletionLabels())) {
			validationError = null;
		}
		if (ImGui.checkbox(BBTexts.get("beatblock.event.camera_adaptive") + "##eventCameraAdaptiveStep", cameraAdaptiveStep)) {
			validationError = null;
		}
		if (cameraAdaptiveStep.get()) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.near_distance") + "##eventCameraNearDistance", cameraNearDistanceBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.far_distance") + "##eventCameraFarDistance", cameraFarDistanceBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.near_scale") + "##eventCameraNearScale", cameraNearScaleBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.far_scale") + "##eventCameraFarScale", cameraFarScaleBuffer);
		}
		if (ImGui.checkbox(BBTexts.get("beatblock.event.frustum_gating") + "##eventCameraFrustumGating", cameraFrustumGating)) {
			validationError = null;
		}
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.edge_priority") + "##eventCameraEdgePriority", cameraEdgePriorityBuffer);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.edge_priority.tooltip"));
		}
		if (ImGui.checkbox(BBTexts.get("beatblock.event.phase_animation") + "##eventUsePhaseAnimation", usePhaseAnimation)) {
			validationError = null;
		}
		if (usePhaseAnimation.get()) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.entry_phase") + "##eventEntryDuration", entryDurationBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.idle_phase") + "##eventIdleDuration", idleDurationBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.exit_phase") + "##eventExitDuration", exitDurationBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.event.phase.tooltip"));
			}
		}
	}

	private void renderAnimationPreviewPopup() {
		if (!ImGui.beginPopup("##eventAnimPreviewPopup")) {
			return;
		}
		BlockInfluencePreset preset = BlockInfluencePresets.get(animationPreviewPresetId);
		if (preset == null) {
			ImGui.textDisabled(BBTexts.get("beatblock.common.unbound"));
		} else {
			PresetChannelPreview.renderSummaryLine(preset);
			PresetChannelPreview.renderChannelBullets(preset);
		}
		ImGui.endPopup();
	}

	private void previewEventAtTime(double timeSeconds) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			return;
		}
		editor.getClock().seek(timeSeconds);
		var music = runtime().musicPlayer();
		if (music != null) {
			music.setCurrentTimeSeconds(timeSeconds);
		}
		if (!BeatBlockClientDriver.isDriving()) {
			BeatBlockClientDriver.startDriving();
		}
	}

	private void renderRuntimeStatus(EventPropertiesRef ref) {
		String eventId = ref != null && ref.event() != null ? ref.event().getId() : "";
		if (eventId == null || eventId.isBlank()) return;
		BeatBlockClientDriver.TimelineActionExecutionReport report = BeatBlockClientDriver.getTimelineActionExecutionReport(eventId);
		if (report == null) return;

		long ageMs = Math.max(0L, System.currentTimeMillis() - report.timestampMs());
		ImGui.textDisabled(BBTexts.get("beatblock.event.runtime_status",
			report.actionMode().name(),
			report.status(),
			report.mutationCount(),
			ageMs));
		if (report.detail() != null && !report.detail().isBlank()) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.runtime_detail", report.detail()));
		}
	}

	private void applyAnimationChanges(EventPropertiesRef ref, Timeline timeline, String actionMode, String animationId,
	                                  String targetObjectId, boolean inheritGroupSpatial, String spatialMode,
	                                  boolean stepDispatch, String stepStartMode, String stepCompletionMode,
	                                  String pacingMode, boolean cameraAdaptiveStep, boolean cameraFrustumGating,
	                                  boolean usePhaseAnimation, boolean vfxEnabled) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
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
			if (livePreviewOnApply.get()) {
				previewEventAtTime(input.timeSeconds());
			}
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = BBTexts.get("beatblock.event.invalid_number");
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
		ImGui.textDisabled(rhythmDrop ? BBTexts.get("beatblock.event.rhythm_drop") : BBTexts.get("beatblock.event.meteor"));
		ImGui.textWrapped(rhythmDrop
			? BBTexts.get("beatblock.event.rhythm_drop.hint")
			: BBTexts.get("beatblock.event.meteor.hint"));
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.landing_x") + "##eventSingleBlockX", singleBlockXBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.landing_y") + "##eventSingleBlockY", singleBlockYBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.landing_z") + "##eventSingleBlockZ", singleBlockZBuffer);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.landing.tooltip"));
		}
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.fall_height") + "##eventMeteorHeight", meteorHeightBuffer);
		if (rhythmDrop) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.impact_threshold") + "##eventImpactThreshold", impactThresholdBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.event.impact_threshold.tooltip"));
			}
		} else {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.scatter") + "##eventMeteorScatter", meteorScatterBuffer);
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
		PresetChannelPreview.renderCollapsible(BBTexts.get("beatblock.event.preset_channels") + "##eventPresetChannels", BlockInfluencePresets.get(presetId));
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
