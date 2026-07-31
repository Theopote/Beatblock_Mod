package com.beatblock.ui.properties.editors;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.engine.influence.InfluenceDimension;
import com.beatblock.ui.imgui.PresetChannelPreview;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editing.AnimationEventFormInput;
import com.beatblock.timeline.editing.AnimationEventPropertiesEditor;
import com.beatblock.timeline.editing.WorldTrajectoryEventParamsEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.presenter.AnimationEditorViewState;
import com.beatblock.ui.presenter.EventPropertiesFormSnapshot;
import com.beatblock.ui.presenter.EventPropertiesOption;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import com.beatblock.timeline.rendering.TrackRegistry;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 方块动画事件属性编辑器。
 */
public final class AnimationPropertyEditor {

	private static final int INPUT_BUFFER_SIZE = 128;

	private String boundRefKey;
	final ImString timeBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString durationBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString energyBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString energyThresholdBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString spatialDelayBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString blocksPerBeatBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString distancePaceSecondsBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString distancePaceMinGapBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString cameraNearDistanceBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString cameraFarDistanceBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString cameraNearScaleBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString cameraFarScaleBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString cameraEdgePriorityBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString entryDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString idleDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString exitDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString placeBlockBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString flashBlockBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString singleBlockXBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString singleBlockYBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString singleBlockZBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString meteorHeightBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString meteorScatterBuffer = new ImString(INPUT_BUFFER_SIZE);
	final ImString impactThresholdBuffer = new ImString(INPUT_BUFFER_SIZE);
	String validationError;
	private String batchMessage;
	private String animationPreviewPresetId;
	private final EventPropertySection worldTrajectorySection = new WorldTrajectorySection();
	private final EventPropertySection stepSequenceSection = new StepSequenceSection();
	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;
	private final ImString batchEnergyBuffer = new ImString(16);
	private final ImString batchTimeOffsetBuffer = new ImString(16);
	private final ImInt batchAnimationIndex = new ImInt(0);
	private final ImBoolean batchApplyEnergy = new ImBoolean(true);
	private final ImBoolean batchApplyAnimation = new ImBoolean(false);
	private final ImBoolean batchApplyTimeOffset = new ImBoolean(false);
	private final ImBoolean batchApplyActionMode = new ImBoolean(false);
	private final ImBoolean batchApplyDurationScale = new ImBoolean(false);
	private final ImBoolean batchApplyFixedDuration = new ImBoolean(false);
	private final ImBoolean batchApplyPlaceBlock = new ImBoolean(false);
	private final ImInt batchActionModeIndex = new ImInt(0);
	private final ImString batchDurationScaleBuffer = new ImString(16);
	private final ImString batchFixedDurationBuffer = new ImString(16);
	private final ImString batchPlaceBlockBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImBoolean livePreviewOnApply = new ImBoolean(true);
	private boolean pendingLivePreview;

	public AnimationPropertyEditor() {
		this(PresenterFactories.eventPropertiesPresenter(), BeatBlock::getContext);
	}

	AnimationPropertyEditor(EventPropertiesPresenter presenter, Supplier<BeatBlockContext> context) {
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
	static final String[] SPATIAL_MODE_VALUES = {
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
	static final String[] STEP_START_MODE_VALUES = {
		"NEXT_BEAT",
		"IMMEDIATE"
	};
	private static String[] stepCompletionLabels() {
		return BBTexts.labels(
			"beatblock.event.step_completion.keep",
			"beatblock.event.step_completion.loop"
		);
	}
	static final String[] STEP_COMPLETION_VALUES = {
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
	static final String[] PACING_MODE_VALUES = {
		"BEAT_GRID",
		"FIXED_INTERVAL",
		"DISTANCE"
	};

	/**
	 * 由 {@link com.beatblock.ui.properties.adapters.AnimationEventPropertyAdapter} 调用。
	 */
	public void renderBody(EventPropertiesRef ref, Timeline timeline, TimelineEditor editor) {
		int batchCount = presenter.countSelectedAnimationEvents(timeline, editor.getSelectionState());

		if (batchCount == 0 && !TimelinePropertyKinds.isAnimationRef(ref)) {
			boundRefKey = null;
			validationError = null;
			batchMessage = null;
			return;
		}

		if (batchCount > 1) {
			renderBatchEditor(timeline, editor, batchCount);
			ImGui.separator();
		}

		if (!TimelinePropertyKinds.isAnimationRef(ref)) {
			return;
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

		List<EventPropertiesOption> actionOptions = presenter.actionOptions();
		String[] actionLabels = optionLabels(actionOptions);

		ImGui.checkbox(BBTexts.get("beatblock.event.batch.apply_action_mode") + "##batchAction", batchApplyActionMode);
		ImGui.setNextItemWidth(-1f);
		if (!batchApplyActionMode.get()) ImGui.beginDisabled();
		ImGui.combo(BBTexts.get("beatblock.event.action_mode") + "##batchActionVal", batchActionModeIndex, actionLabels);
		if (!batchApplyActionMode.get()) ImGui.endDisabled();

		ImGui.checkbox(BBTexts.get("beatblock.event.batch.apply_duration_scale") + "##batchDurScale", batchApplyDurationScale);
		ImGui.setNextItemWidth(-1f);
		if (!batchApplyDurationScale.get()) ImGui.beginDisabled();
		ImGui.inputText(BBTexts.get("beatblock.event.batch.duration_scale") + "##batchDurScaleVal", batchDurationScaleBuffer);
		if (!batchApplyDurationScale.get()) ImGui.endDisabled();
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.batch.duration_scale.tooltip"));
		}

		ImGui.checkbox(BBTexts.get("beatblock.event.batch.apply_fixed_duration") + "##batchFixedDur", batchApplyFixedDuration);
		ImGui.setNextItemWidth(-1f);
		if (!batchApplyFixedDuration.get()) ImGui.beginDisabled();
		ImGui.inputText(BBTexts.get("beatblock.event.batch.fixed_duration") + "##batchFixedDurVal", batchFixedDurationBuffer);
		if (!batchApplyFixedDuration.get()) ImGui.endDisabled();
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.batch.fixed_duration.tooltip"));
		}

		ImGui.checkbox(BBTexts.get("beatblock.event.batch.apply_place_block") + "##batchPlace", batchApplyPlaceBlock);
		ImGui.setNextItemWidth(-1f);
		if (!batchApplyPlaceBlock.get()) ImGui.beginDisabled();
		ImGui.inputText(BBTexts.get("beatblock.event.place_block") + "##batchPlaceVal", batchPlaceBlockBuffer);
		if (!batchApplyPlaceBlock.get()) ImGui.endDisabled();

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
		TimelineAnimationActionMode actionMode = null;
		if (batchApplyActionMode.get() && batchActionModeIndex.get() >= 0
				&& batchActionModeIndex.get() < presenter.actionOptions().size()) {
			String modeId = presenter.actionOptions().get(batchActionModeIndex.get()).id();
			actionMode = TimelineAnimationActionMode.fromValue(modeId);
		}
		Double durationScale = null;
		if (batchApplyDurationScale.get()) {
			try {
				durationScale = Double.parseDouble(batchDurationScaleBuffer.get().trim());
			} catch (NumberFormatException ex) {
				batchMessage = BBTexts.get("beatblock.event.invalid_number");
				return;
			}
		}
		Double fixedDuration = null;
		if (batchApplyFixedDuration.get()) {
			try {
				fixedDuration = Double.parseDouble(batchFixedDurationBuffer.get().trim());
			} catch (NumberFormatException ex) {
				batchMessage = BBTexts.get("beatblock.event.invalid_number");
				return;
			}
		}
		java.util.Map<String, Object> customParameters = null;
		if (batchApplyPlaceBlock.get()) {
			String placeBlock = batchPlaceBlockBuffer.get().trim();
			if (!placeBlock.isBlank()) {
				customParameters = java.util.Map.of("placeBlock", placeBlock);
			}
		}
		var outcome = presenter.applyBatchAnimationEdit(
			timeline,
			editor.getSelectionState(),
			editor.getCommandManager(),
			new EventPropertiesPresenter.BatchAnimationEditRequest(
				energy,
				animationId,
				timeOffset,
				actionMode,
				durationScale,
				fixedDuration,
				customParameters
			)
		);
		batchMessage = outcome.success()
			? BBTexts.get("beatblock.event.batch.applied", outcome.updatedCount())
			: outcome.errorMessage();
		if (outcome.success()) {
			ToastNotificationSystem.showSuccess(batchMessage);
		} else if (batchMessage != null && !batchMessage.isBlank()) {
			ToastNotificationSystem.showError(batchMessage);
		}
	}

	private void renderAnimationEditor(EventPropertiesRef ref, Timeline timeline, TimelineEditor editor, int batchCount) {
		pendingLivePreview = false;
		Map<String, Object> params = ref.event().getParameters();
		AnimationEditorViewState viewState = presenter.readAnimationEditorState(params);
		List<EventPropertiesOption> actionOptions = presenter.actionOptions();
		List<EventPropertiesOption> animationOptions = presenter.animationOptions();
		List<EventPropertiesOption> targetOptions = presenter.targetOptions();

		String[] actionLabels = optionLabels(actionOptions);
		String[] animationLabels = optionLabels(animationOptions);
		String[] targetLabels = optionLabels(targetOptions);

		EventEditContext ctx = new EventEditContext(
			ref,
			timeline,
			editor,
			presenter,
			viewState,
			actionOptions,
			animationOptions,
			targetOptions,
			actionLabels,
			animationLabels,
			targetLabels,
			this
		);

		if (batchCount > 1) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.batch.primary_hint", batchCount));
			ImGui.spacing();
		}

		if (ImGui.beginTabBar("##eventPropTabs")) {
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.basic"))) {
				renderBasicTab(ctx);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.spatial"))) {
				renderSpatialTab(ctx);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.advanced"))) {
				if (stepSequenceSection.supports(ctx)) {
					stepSequenceSection.render(ctx);
				}
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

		flushLivePreviewOnEdit();

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
			applyAnimationChanges(ctx);
		}
		if (reset) {
			bindBuffers(ref);
		}
		renderAnimationPreviewPopup();
	}

	private void renderBasicTab(EventEditContext ctx) {
		ImGui.text(BBTexts.get("beatblock.event.timing"));
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.start_time") + "##eventTime", timeBuffer);
		trackLivePreviewEdit();
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.duration") + "##eventDuration", durationBuffer);
		trackLivePreviewEdit();
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.energy") + "##eventEnergy", energyBuffer);
		trackLivePreviewEdit();
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.energy_threshold") + "##eventEnergyThreshold", energyThresholdBuffer);
		trackLivePreviewEdit();

		ImGui.spacing();
		ImGui.text(BBTexts.get("beatblock.event.binding"));
		if (ImGui.combo(BBTexts.get("beatblock.event.action_mode_combo") + "##eventActionMode", ctx.actionIndex, ctx.actionLabels())) {
			validationError = null;
		}
		trackLivePreviewEdit();
		if (ImGui.combo(BBTexts.get("beatblock.event.animation_preset") + "##eventAnimation", ctx.animationIndex, ctx.animationLabels())) {
			validationError = null;
		}
		trackLivePreviewEdit();
		String selectedAnimationId = ctx.selectedAnimationId();
		renderPresetChannelPreview(selectedAnimationId);
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.event.preview_animation") + "##eventAnimPreview")) {
			animationPreviewPresetId = selectedAnimationId;
			ImGui.openPopup("##eventAnimPreviewPopup");
		}
		if (ImGui.checkbox(BBTexts.get("beatblock.event.vfx") + "##eventVfxEnabled", ctx.vfxEnabled)) {
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
		if (ImGui.combo(BBTexts.get("beatblock.event.target") + "##eventTarget", ctx.targetIndex, ctx.targetLabels())) {
			validationError = null;
		}
	}

	private void renderSpatialTab(EventEditContext ctx) {
		if (worldTrajectorySection.supports(ctx)) {
			worldTrajectorySection.render(ctx);
		}
		if (ImGui.checkbox(BBTexts.get("beatblock.event.inherit_spatial") + "##eventInheritGroupSpatial", ctx.inheritGroupSpatial)) {
			validationError = null;
		}
		if (!ctx.inheritGroupSpatial.get()) {
			if (ImGui.combo(BBTexts.get("beatblock.event.spatial_mode") + "##eventSpatialMode", ctx.spatialModeIndex, spatialModeLabels())) {
				validationError = null;
			}
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.spatial_delay") + "##eventSpatialDelay", spatialDelayBuffer);
		}
		TimelineAnimationActionMode selectedActionMode = ctx.selectedActionMode();
		if (selectedActionMode == TimelineAnimationActionMode.PLACE) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.place_block") + "##eventPlaceBlock", placeBlockBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.event.place_block.tooltip"));
			}
		}
	}

	private void renderAdvancedTab(EventEditContext ctx) {
		if (stepSequenceSection.supports(ctx)) {
			stepSequenceSection.render(ctx);
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
		editor.getPlaybackSession().seek(timeSeconds);
		if (!BeatBlockClientDriver.isDriving()) {
			BeatBlockClientDriver.startDriving();
		}
	}

	private void trackLivePreviewEdit() {
		if (ImGui.isItemDeactivatedAfterEdit()) {
			pendingLivePreview = true;
		}
	}

	private void flushLivePreviewOnEdit() {
		if (!pendingLivePreview || !livePreviewOnApply.get()) {
			return;
		}
		pendingLivePreview = false;
		try {
			previewEventAtTime(Double.parseDouble(timeBuffer.get().trim()));
		} catch (NumberFormatException ignored) {
		}
	}

	private void renderRuntimeStatus(EventPropertiesRef ref) {
		String eventId = ref != null && ref.event() != null ? ref.event().getId() : "";
		if (eventId.isBlank()) return;
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

	private void applyAnimationChanges(EventEditContext ctx) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
			return;
		}
		String actionMode = ctx.selectedActionId();
		String animationId = ctx.selectedAnimationId();
		String targetObjectId = ctx.selectedTargetId();
		boolean inheritGroupSpatial = ctx.inheritGroupSpatial.get();
		String spatialMode = SPATIAL_MODE_VALUES[Math.max(0, Math.min(ctx.spatialModeIndex.get(), SPATIAL_MODE_VALUES.length - 1))];
		boolean stepDispatch = ctx.stepDispatch.get();
		String stepStartMode = STEP_START_MODE_VALUES[Math.max(0, Math.min(ctx.stepStartModeIndex.get(), STEP_START_MODE_VALUES.length - 1))];
		String stepCompletionMode = STEP_COMPLETION_VALUES[Math.max(0, Math.min(ctx.stepCompletionIndex.get(), STEP_COMPLETION_VALUES.length - 1))];
		String pacingMode = PACING_MODE_VALUES[Math.max(0, Math.min(ctx.pacingModeIndex.get(), PACING_MODE_VALUES.length - 1))];
		boolean cameraAdaptiveStep = ctx.cameraAdaptiveStep.get();
		boolean cameraFrustumGating = ctx.cameraFrustumGating.get();
		boolean usePhaseAnimation = ctx.usePhaseAnimation.get();
		boolean vfxEnabled = ctx.vfxEnabled.get();
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
				ctx.ref(),
				ctx.timeline(),
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
			ToastNotificationSystem.showSuccess(BBTexts.get("beatblock.toast.event_applied"));
			bindBuffers(ctx.ref());
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

	static int indexOfOption(List<EventPropertiesOption> options, String id) {
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).id().equals(id)) {
				return i;
			}
		}
		return 0;
	}

	static String[] optionLabels(List<EventPropertiesOption> options) {
		String[] labels = new String[options.size()];
		for (int i = 0; i < options.size(); i++) {
			labels[i] = options.get(i).label();
		}
		return labels;
	}

	static int indexOfValue(String[] values, String target) {
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
