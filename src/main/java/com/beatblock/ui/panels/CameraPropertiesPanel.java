package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.client.camera.CameraKeyframeActions;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.editing.CameraEventPropertiesEditor;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.EventPropertiesFormSnapshot;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.util.UiNumberFormatter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 右侧摄像机属性面板：片段起止、分段参数、关键帧位姿。
 */
public class CameraPropertiesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final int INPUT_BUFFER_SIZE = 128;

	private String boundRefKey;
	private final ImString timeBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camSegDurBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camXBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camYBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camZBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camYawBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camPitchBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camEaseBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camClipStartBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camClipEndBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final Map<String, ImString> camSegParamBuffers = new HashMap<>();
	private final ImBoolean camClipPathVisibleProxy = new ImBoolean(true);
	private final ImBoolean camSegPathVisibleProxy = new ImBoolean(true);
	private String validationError;
	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;

	public CameraPropertiesPanel() {
		this(PresenterFactories.eventPropertiesPresenter(), BeatBlock::getContext);
	}

	CameraPropertiesPanel(EventPropertiesPresenter presenter, Supplier<BeatBlockContext> context) {
		this.presenter = presenter;
		this.context = context;
	}

	private BeatBlockContext runtime() {
		return context.get();
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.cameraPropertiesWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.cameraPropertiesWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ImGui.text(BBTexts.get("beatblock.camera.title"));
			ImGui.separator();

			Timeline timeline = runtime().timeline();
			TimelineEditor editor = runtime().timelineEditor();
			if (timeline == null || editor == null) {
				ImGui.textDisabled(BBTexts.get("beatblock.common.timeline_not_initialized"));
				return;
			}

			EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, editor.getSelectionState());
			renderBody(ref, timeline, editor);
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.cameraPropertiesWindow());
		}
	}

	/**
	 * 由 {@link com.beatblock.ui.properties.adapters.CameraPropertyAdapter} 调用的属性编辑区。
	 */
	public void renderBody(EventPropertiesRef ref, Timeline timeline, TimelineEditor editor) {
		if (!TimelinePropertyKinds.isCameraRef(ref)) {
			boundRefKey = null;
			validationError = null;
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
			ImGui.textDisabled(BBTexts.get("beatblock.camera.track_locked"));
			ImGui.separator();
			ImGui.beginDisabled();
		}

		if (ref.event() == null) {
			renderCameraClipOnlyPanel(ref, timeline);
		} else {
			EventType et = ref.event().getType();
			if (et == EventType.CAMERA_SEGMENT) {
				renderCameraSegmentPanel(ref, timeline);
			} else if (et == EventType.CAMERA_KEYFRAME) {
				renderCameraKeyframePanel(ref, timeline, editor.getSelectionState());
			}
		}

		if (trackLocked) {
			ImGui.endDisabled();
		}
	}

	private static boolean isCameraRef(EventPropertiesRef ref) {
		return TimelinePropertyKinds.isCameraRef(ref);
	}

	private void renderEventSummary(EventPropertiesRef ref, Timeline timeline) {
		ImGui.textDisabled(BBTexts.get("beatblock.event.track"));
		ImGui.sameLine();
		ImGui.text(ref.track().getName().isBlank() ? ref.track().getId() : ref.track().getName());
		if (ref.event() == null) {
			ImGui.textDisabled(BBTexts.get("beatblock.camera.clip_id"));
			ImGui.sameLine();
			ImGui.text(ref.clip().getId());
			ImGui.textDisabled(BBTexts.get("beatblock.camera.show_path"));
			ImGui.sameLine();
			ImGui.text(EventPropertiesPresenter.isPathVisible(timeline, ref.clip().getId())
				? BBTexts.get("beatblock.common.yes") : BBTexts.get("beatblock.common.no"));
			return;
		}
		Map<String, Object> params = ref.event().getParameters();
		EventType et = ref.event().getType();
		ImGui.textDisabled(BBTexts.get("beatblock.event.event_id"));
		ImGui.sameLine();
		ImGui.text(ref.event().getId());
		ImGui.textDisabled(BBTexts.get("beatblock.camera.event_type"));
		ImGui.sameLine();
		ImGui.text(et.name());
		if (et == EventType.CAMERA_SEGMENT) {
			ImGui.textDisabled(BBTexts.get("beatblock.camera.segment_kind"));
			ImGui.sameLine();
			ImGui.text(CameraSegmentKind.fromParam(params.get("kind")).name());
		}
	}

	private void bindBuffers(EventPropertiesRef ref) {
		applyFormSnapshot(presenter.buildFormSnapshot(ref, runtime().timeline()));
		validationError = null;
	}

	private void applyFormSnapshot(EventPropertiesFormSnapshot snap) {
		camSegParamBuffers.clear();
		boundRefKey = snap.refKey();
		camClipStartBuffer.set(snap.camClipStart());
		camClipEndBuffer.set(snap.camClipEnd());
		camClipPathVisibleProxy.set(snap.camClipPathVisible());
		timeBuffer.set(snap.time());
		camSegDurBuffer.set(snap.camSegDuration());
		camSegPathVisibleProxy.set(snap.camSegPathVisible());
		for (Map.Entry<String, String> entry : snap.camSegParams().entrySet()) {
			camSegParamBuffers.put(entry.getKey(), new ImString(entry.getValue(), INPUT_BUFFER_SIZE));
		}
		camXBuffer.set(snap.camX());
		camYBuffer.set(snap.camY());
		camZBuffer.set(snap.camZ());
		camYawBuffer.set(snap.camYaw());
		camPitchBuffer.set(snap.camPitch());
		camEaseBuffer.set(snap.camEase());
	}

	private void renderCameraClipOnlyPanel(EventPropertiesRef ref, Timeline timeline) {
		ImGui.text(BBTexts.get("beatblock.camera.clip_times"));
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.camera.start") + "##camClipStart", camClipStartBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.camera.end") + "##camClipEnd", camClipEndBuffer);
		ImGui.checkbox(BBTexts.get("beatblock.camera.show_path") + "##camClipPathVis", camClipPathVisibleProxy);
		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}
		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.common.apply") + "##camClipApply", 120f, 0f)) {
			applyCameraClipOnly(ref, timeline);
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.reset") + "##camClipReset", 120f, 0f)) {
			bindBuffers(ref);
		}
	}

	private void applyCameraClipOnly(EventPropertiesRef ref, Timeline timeline) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
			return;
		}
		try {
			double newStart = Double.parseDouble(valueOf(camClipStartBuffer).trim());
			double newEnd = Double.parseDouble(valueOf(camClipEndBuffer).trim());
			var result = presenter.applyCameraClipOnly(
				ref,
				timeline,
				editor.getCommandManager(),
				newStart,
				newEnd,
				camClipPathVisibleProxy.get()
			);
			if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
				validationError = message;
				return;
			}
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = BBTexts.get("beatblock.camera.invalid_time");
		}
	}

	private static String[] camKindLabels() {
		return BBTexts.labels(
			"beatblock.camera.kind.path",
			"beatblock.camera.kind.dolly",
			"beatblock.camera.kind.orbit",
			"beatblock.camera.kind.crane",
			"beatblock.camera.kind.shake"
		);
	}
	private static final CameraSegmentKind[] CAM_KINDS = CameraSegmentKind.values();

	private static int kindIndex(CameraSegmentKind kind) {
		for (int i = 0; i < CAM_KINDS.length; i++) {
			if (CAM_KINDS[i] == kind) return i;
		}
		return 0;
	}

	private void renderCameraSegmentPanel(EventPropertiesRef ref, Timeline timeline) {
		CameraSegmentKind kind = CameraSegmentKind.fromParam(ref.event().getParameters().get("kind"));

		ImInt kindIdx = new ImInt(kindIndex(kind));
		ImGui.setNextItemWidth(-1f);
		if (ImGui.combo(BBTexts.get("beatblock.camera.segment_kind") + "##camSegKind", kindIdx, camKindLabels())) {
			CameraSegmentKind newKind = CAM_KINDS[kindIdx.get()];
			if (newKind != kind) {
				applyCameraKindChange(ref, timeline, newKind);
				kind = newKind;
			}
		}

		ImGui.text(BBTexts.get("beatblock.camera.segment_duration"));
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("##camSegDurInp", camSegDurBuffer);
		ImGui.checkbox(BBTexts.get("beatblock.camera.show_path") + "##camSegPathVis", camSegPathVisibleProxy);

		ImGui.separator();
		switch (kind) {
			case PATH -> ImGui.textDisabled(BBTexts.get("beatblock.camera.path.hint"));
			case DOLLY -> {
				ImGui.textDisabled(BBTexts.get("beatblock.camera.dolly.params"));
				renderSegParam(BBTexts.get("beatblock.camera.param.start_x"), "startX");
				renderSegParam(BBTexts.get("beatblock.camera.param.start_y"), "startY");
				renderSegParam(BBTexts.get("beatblock.camera.param.start_z"), "startZ");
				renderSegParam(BBTexts.get("beatblock.camera.param.end_x"), "endX");
				renderSegParam(BBTexts.get("beatblock.camera.param.end_y"), "endY");
				renderSegParam(BBTexts.get("beatblock.camera.param.end_z"), "endZ");
				renderSegParam(BBTexts.get("beatblock.camera.param.base_yaw"), "baseYawDeg");
				renderSegParam(BBTexts.get("beatblock.camera.param.base_pitch"), "basePitchDeg");
			}
			case ORBIT -> {
				ImGui.textDisabled(BBTexts.get("beatblock.camera.orbit.params"));
				renderSegParam(BBTexts.get("beatblock.camera.param.target_x"), "targetX");
				renderSegParam(BBTexts.get("beatblock.camera.param.target_y"), "targetY");
				renderSegParam(BBTexts.get("beatblock.camera.param.target_z"), "targetZ");
				renderSegParam(BBTexts.get("beatblock.camera.param.radius"), "radius");
				renderSegParam(BBTexts.get("beatblock.camera.param.height_offset"), "height");
				renderSegParam(BBTexts.get("beatblock.camera.param.yaw_start"), "yawStartDeg");
				renderSegParam(BBTexts.get("beatblock.camera.param.yaw_end"), "yawEndDeg");
			}
			case CRANE -> {
				ImGui.textDisabled(BBTexts.get("beatblock.camera.crane.params"));
				renderSegParam(BBTexts.get("beatblock.camera.param.start_x"), "startX");
				renderSegParam(BBTexts.get("beatblock.camera.param.start_y"), "startY");
				renderSegParam(BBTexts.get("beatblock.camera.param.start_z"), "startZ");
				renderSegParam(BBTexts.get("beatblock.camera.param.end_x"), "endX");
				renderSegParam(BBTexts.get("beatblock.camera.param.end_y"), "endY");
				renderSegParam(BBTexts.get("beatblock.camera.param.end_z"), "endZ");
				renderSegParam(BBTexts.get("beatblock.camera.param.yaw"), "yawDeg");
				renderSegParam(BBTexts.get("beatblock.camera.param.pitch"), "pitchDeg");
			}
			case SHAKE -> {
				ImGui.textDisabled(BBTexts.get("beatblock.camera.shake.params"));
				renderSegParam(BBTexts.get("beatblock.camera.param.anchor_x"), "anchorX");
				renderSegParam(BBTexts.get("beatblock.camera.param.anchor_y"), "anchorY");
				renderSegParam(BBTexts.get("beatblock.camera.param.anchor_z"), "anchorZ");
				renderSegParam(BBTexts.get("beatblock.camera.param.yaw"), "yawDeg");
				renderSegParam(BBTexts.get("beatblock.camera.param.pitch"), "pitchDeg");
				renderSegParam(BBTexts.get("beatblock.camera.param.distance"), "distance");
				renderSegParam(BBTexts.get("beatblock.camera.param.amplitude"), "amplitude");
				renderSegParam(BBTexts.get("beatblock.camera.param.frequency"), "frequencyHz");
				renderSegParam(BBTexts.get("beatblock.camera.param.beat_sync"), "beatSync");
				renderSegParam(BBTexts.get("beatblock.camera.param.beats_per_pulse"), "beatsPerPulse");
			}
		}

		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}
		ImGui.spacing();

		if (kind != CameraSegmentKind.PATH) {
			if (ImGui.button(BBTexts.get("beatblock.camera.capture_view") + "##camSegCapture", 160f, 0f)) {
				captureCurrentViewToSegment(kind);
			}
			ImGui.sameLine();
		}
		if (ImGui.button(BBTexts.get("beatblock.common.apply") + "##camSegApply", 120f, 0f)) {
			applyCameraSegmentPanel(ref, timeline);
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.reset") + "##camSegReset", 120f, 0f)) {
			bindBuffers(ref);
		}
	}

	private void renderSegParam(String label, String key) {
		ImString buf = camSegParamBuffers.get(key);
		if (buf == null) {
			buf = new ImString("", INPUT_BUFFER_SIZE);
			camSegParamBuffers.put(key, buf);
		}
		ImGui.text(label);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("##camSegP_" + key, buf);
	}

	private void captureCurrentViewToSegment(CameraSegmentKind kind) {
		var captured = presenter.captureSegmentViewParams(kind);
		if (captured.isEmpty()) {
			validationError = BBTexts.get("beatblock.camera.no_camera");
			return;
		}
		for (Map.Entry<String, String> entry : captured.get().entrySet()) {
			ImString buf = camSegParamBuffers.computeIfAbsent(entry.getKey(), k -> new ImString(INPUT_BUFFER_SIZE));
			buf.set(entry.getValue());
		}
		validationError = null;
	}

	private void applyCameraKindChange(EventPropertiesRef ref, Timeline timeline, CameraSegmentKind newKind) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
			return;
		}
		var result = presenter.applyCameraKindChange(ref, timeline, editor.getCommandManager(), newKind);
		if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
			validationError = message;
			return;
		}
		validationError = null;
		bindBuffers(ref);
	}

	private void applyCameraSegmentPanel(EventPropertiesRef ref, Timeline timeline) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
			return;
		}
		try {
			double duration = Double.parseDouble(valueOf(camSegDurBuffer).trim());
			CameraSegmentKind currentKind = CameraSegmentKind.fromParam(ref.event().getParameters().get("kind"));
			Map<String, String> rawParams = new HashMap<>();
			for (String key : CameraEventPropertiesEditor.paramKeysForKind(currentKind)) {
				ImString buf = camSegParamBuffers.get(key);
				if (buf != null) {
					rawParams.put(key, valueOf(buf));
				}
			}
			var result = presenter.applyCameraSegment(
				ref,
				timeline,
				editor.getCommandManager(),
				duration,
				camSegPathVisibleProxy.get(),
				rawParams
			);
			if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
				validationError = message;
				return;
			}
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = BBTexts.get("beatblock.camera.invalid_duration");
		}
	}

	private void renderCameraKeyframePanel(EventPropertiesRef ref, Timeline timeline, SelectionState selectionState) {
		if (ref.clip() != null) {
			TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(ref.clip());
			CameraSegmentKind clipKind = seg != null
				? CameraSegmentKind.fromParam(seg.getParameters().get("kind"))
				: null;
			ImGui.textDisabled(BBTexts.get("beatblock.camera.belongs_to_clip"));
			ImGui.sameLine();
			ImGui.text(ref.clip().getId());
			if (clipKind != null) {
				ImGui.textDisabled(BBTexts.get("beatblock.camera.clip_type"));
				ImGui.sameLine();
				ImGui.text(clipKind.name());
			}
			ImGui.textDisabled(BBTexts.get("beatblock.camera.clip_range",
				ref.clip().getStartTimeSeconds(), ref.clip().getEndTimeSeconds()));
			if (clipKind != null && clipKind != CameraSegmentKind.PATH) {
				ImGui.spacing();
				ImGui.textColored(1f, 0.65f, 0.2f, 1f, BBTexts.get("beatblock.camera.non_path_warning"));
			}
			ImGui.separator();
		}

		ImGui.text(BBTexts.get("beatblock.camera.time_pose"));
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.camera.time") + "##camKfTime", timeBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.common.coord_x") + "##camKfX", camXBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.common.coord_y") + "##camKfY", camYBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.common.coord_z") + "##camKfZ", camZBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.camera.param.yaw") + "##camKfYaw", camYawBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.camera.param.pitch") + "##camKfPitch", camPitchBuffer);
		ImGui.setNextItemWidth(-1f);
		String[] easeOptions = { BBTexts.get("beatblock.camera.ease.smooth"), BBTexts.get("beatblock.camera.ease.linear") };
		String[] easeValues = { "SMOOTH", "LINEAR" };
		int easeIdx = "LINEAR".equalsIgnoreCase(valueOf(camEaseBuffer).trim()) ? 1 : 0;
		ImInt easeInt = new ImInt(easeIdx);
		if (ImGui.combo(BBTexts.get("beatblock.camera.ease") + "##camKfEase", easeInt, easeOptions)) {
			camEaseBuffer.set(easeValues[easeInt.get()]);
		}
		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}
		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.camera.capture_view") + "##camKfCapture", 160f, 0f)) {
			var view = presenter.currentCameraView();
			if (view.isEmpty()) {
				validationError = BBTexts.get("beatblock.camera.no_camera");
			} else {
				EventPropertiesPresenter.CameraViewSample sample = view.get();
				camXBuffer.set(UiNumberFormatter.format(sample.x()));
				camYBuffer.set(UiNumberFormatter.format(sample.y()));
				camZBuffer.set(UiNumberFormatter.format(sample.z()));
				camYawBuffer.set(UiNumberFormatter.format(sample.yaw()));
				camPitchBuffer.set(UiNumberFormatter.format(sample.pitch()));
				validationError = null;
			}
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.apply") + "##camKfApply", 120f, 0f)) {
			applyCameraKeyframe(ref, timeline);
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.reset") + "##camKfReset", 120f, 0f)) {
			bindBuffers(ref);
		}
		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.camera.delete_keyframe") + "##camKfDelete", 160f, 0f)) {
			String id = ref.event().getId();
			if (CameraKeyframeActions.deleteKeyframeEvent(timeline, id) && selectionState != null) {
				selectionState.deselectEvent(id);
				boundRefKey = null;
			}
		}

		try {
			double x = Double.parseDouble(valueOf(camXBuffer).trim());
			double y = Double.parseDouble(valueOf(camYBuffer).trim());
			double z = Double.parseDouble(valueOf(camZBuffer).trim());
			double yaw = Double.parseDouble(valueOf(camYawBuffer).trim());
			double pitch = Double.parseDouble(valueOf(camPitchBuffer).trim());
			com.beatblock.client.camera.TimelineCameraController.getInstance().previewKeyframeDirect(
				new com.beatblock.client.camera.TimelineCameraEvaluator.CameraSample(
					new net.minecraft.util.math.Vec3d(x, y, z), (float) yaw, (float) pitch
				)
			);
		} catch (NumberFormatException e) {
			com.beatblock.BeatBlock.LOGGER.debug("Invalid camera preview coordinates", e);
		}
	}

	private void applyCameraKeyframe(EventPropertiesRef ref, Timeline timeline) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
			return;
		}
		try {
			double newTime = Double.parseDouble(valueOf(timeBuffer).trim());
			double x = Double.parseDouble(valueOf(camXBuffer).trim());
			double y = Double.parseDouble(valueOf(camYBuffer).trim());
			double z = Double.parseDouble(valueOf(camZBuffer).trim());
			double yaw = Double.parseDouble(valueOf(camYawBuffer).trim());
			double pitch = Double.parseDouble(valueOf(camPitchBuffer).trim());
			String ease = valueOf(camEaseBuffer).trim();
			var result = presenter.applyCameraKeyframe(
				ref,
				timeline,
				editor.getCommandManager(),
				newTime,
				x,
				y,
				z,
				yaw,
				pitch,
				ease
			);
			if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
				validationError = message;
				return;
			}
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = BBTexts.get("beatblock.camera.invalid_coords");
		}
	}

	private static String valueOf(ImString text) {
		String value = text != null ? text.get() : null;
		return value != null ? value : "";
	}
}
