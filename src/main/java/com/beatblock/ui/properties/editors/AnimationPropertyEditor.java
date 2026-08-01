package com.beatblock.ui.properties.editors;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editing.AnimationEventFormInput;
import com.beatblock.timeline.editing.AnimationEventPropertiesEditor;
import com.beatblock.timeline.editing.WorldTrajectoryEventParamsEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.imgui.PresetChannelPreview;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.presenter.AnimationEditorViewState;
import com.beatblock.ui.presenter.EventPropertiesFormSnapshot;
import com.beatblock.ui.presenter.EventPropertiesOption;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 方块动画事件属性编辑器：编排 {@link EventPropertySection}，自身只保留
 * 缓冲字段、批量编辑、Apply/Reset 与预览弹窗。
 * <p>
 * 单事件 UI 结构：
 * <pre>
 *   for (Tab tab : tabs) {
 *     for (EventPropertySection section : registry.forTab(tab)) {
 *       if (section.supports(ctx)) section.render(ctx);
 *     }
 *   }
 * </pre>
 * 插件通过 {@link #sectionRegistry()}{@code .register(...)} 扩展。
 */
public final class AnimationPropertyEditor {

	private static final int INPUT_BUFFER_SIZE = 128;

	private final EventPropertySectionRegistry sectionRegistry;
	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;

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
		this(presenter, context, EventPropertySectionRegistry.createDefault());
	}

	AnimationPropertyEditor(
		EventPropertiesPresenter presenter,
		Supplier<BeatBlockContext> context,
		EventPropertySectionRegistry sectionRegistry
	) {
		this.presenter = presenter;
		this.context = context;
		this.sectionRegistry = sectionRegistry != null
			? sectionRegistry
			: EventPropertySectionRegistry.createDefault();
	}

	/** Plugin / test access to register extra property sections. */
	public EventPropertySectionRegistry sectionRegistry() {
		return sectionRegistry;
	}

	private BeatBlockContext runtime() {
		return context.get();
	}

	static final String[] SPATIAL_MODE_VALUES = {
		"ALL",
		"SEQUENTIAL",
		"RADIAL",
		"RANDOM",
		"SPIRAL"
	};
	static final String[] STEP_START_MODE_VALUES = {
		"NEXT_BEAT",
		"IMMEDIATE"
	};
	static final String[] STEP_COMPLETION_VALUES = {
		"KEEP",
		"LOOP"
	};
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
		Map<String, Object> customParameters = null;
		if (batchApplyPlaceBlock.get()) {
			String placeBlock = batchPlaceBlockBuffer.get().trim();
			if (!placeBlock.isBlank()) {
				customParameters = Map.of("placeBlock", placeBlock);
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

		EventEditContext ctx = new EventEditContext(
			ref,
			timeline,
			editor,
			presenter,
			viewState,
			actionOptions,
			animationOptions,
			targetOptions,
			optionLabels(actionOptions),
			optionLabels(animationOptions),
			optionLabels(targetOptions),
			this
		);

		if (batchCount > 1) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.batch.primary_hint", batchCount));
			ImGui.spacing();
		}
		if (isUnboundAnimationTarget(ref)) {
			ImGui.textColored(1f, 0.78f, 0.2f, 1f, BBTexts.get("beatblock.event.unbound_target_banner"));
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.event.unbound_target_banner.tooltip"));
			}
			ImGui.spacing();
		}

		if (ImGui.beginTabBar("##eventPropTabs")) {
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.basic"))) {
				sectionRegistry.renderTab(EventPropertySection.Tab.BASIC, ctx);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.spatial"))) {
				sectionRegistry.renderTab(EventPropertySection.Tab.SPATIAL, ctx);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.advanced"))) {
				sectionRegistry.renderTab(EventPropertySection.Tab.ADVANCED, ctx);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem(BBTexts.get("beatblock.event.tab.info"))) {
				sectionRegistry.renderTab(EventPropertySection.Tab.INFO, ctx);
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

	/** Called by {@link PresetSection} when the user opens the preset preview popup. */
	void openAnimationPreview(String presetId) {
		animationPreviewPresetId = presetId;
		ImGui.openPopup("##eventAnimPreviewPopup");
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

	/** Package-visible for sections that need live-preview seek on edit. */
	void trackLivePreviewEdit() {
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

	private static boolean isUnboundAnimationTarget(EventPropertiesRef ref) {
		if (ref == null || ref.event() == null) {
			return false;
		}
		Object raw = ref.event().getParameters().get("targetObject");
		if (raw == null) {
			return true;
		}
		return String.valueOf(raw).trim().isEmpty();
	}
}
