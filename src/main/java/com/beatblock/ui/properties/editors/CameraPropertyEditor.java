package com.beatblock.ui.properties.editors;

import com.beatblock.BeatBlock;
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
import com.beatblock.ui.presenter.EventPropertiesFormSnapshot;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import com.beatblock.ui.util.MusicalDurationField;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 摄像机片段 / 分段 / 关键帧属性编辑器。
 * <p>
 * Numeric pose / segment params use {@link ImGui#dragFloat}; duration / times use
 * {@link MusicalDurationField} (Seconds / Beats / Bars).
 */
public final class CameraPropertyEditor {

	private static final int INPUT_BUFFER_SIZE = 128;

	private String boundRefKey;
	private final MusicalDurationField keyframeTime = new MusicalDurationField();
	private final MusicalDurationField segmentDuration = new MusicalDurationField();
	private final MusicalDurationField clipStart = new MusicalDurationField();
	private final MusicalDurationField clipEnd = new MusicalDurationField();
	private final float[] camX = new float[]{0f};
	private final float[] camY = new float[]{0f};
	private final float[] camZ = new float[]{0f};
	private final float[] camYaw = new float[]{0f};
	private final float[] camPitch = new float[]{0f};
	private final ImString camEaseBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final Map<String, float[]> camSegParamFloats = new HashMap<>();
	private final ImBoolean camClipPathVisibleProxy = new ImBoolean(true);
	private final ImBoolean camSegPathVisibleProxy = new ImBoolean(true);
	private String validationError;
	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;

	public CameraPropertyEditor() {
		this(PresenterFactories.eventPropertiesPresenter(), BeatBlock::getContext);
	}

	public CameraPropertyEditor(EventPropertiesPresenter presenter, Supplier<BeatBlockContext> context) {
		this.presenter = presenter;
		this.context = context;
	}

	private BeatBlockContext runtime() {
		return context.get();
	}

	private double bpm(Timeline timeline) {
		return timeline != null ? timeline.getBpm() : 0.0;
	}

	/**
	 * 由 {@link com.beatblock.ui.properties.adapters.CameraPropertyAdapter} 调用。
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
		camSegParamFloats.clear();
		boundRefKey = snap.refKey();
		Timeline timeline = runtime().timeline();
		double bpm = bpm(timeline);
		clipStart.setSeconds(parseDouble(snap.camClipStart(), 0.0), bpm);
		clipEnd.setSeconds(parseDouble(snap.camClipEnd(), 0.0), bpm);
		camClipPathVisibleProxy.set(snap.camClipPathVisible());
		keyframeTime.setSeconds(parseDouble(snap.time(), 0.0), bpm);
		segmentDuration.setSeconds(parseDouble(snap.camSegDuration(), 0.05), bpm);
		camSegPathVisibleProxy.set(snap.camSegPathVisible());
		for (Map.Entry<String, String> entry : snap.camSegParams().entrySet()) {
			camSegParamFloats.put(entry.getKey(), new float[]{(float) parseDouble(entry.getValue(), 0.0)});
		}
		camX[0] = (float) parseDouble(snap.camX(), 0.0);
		camY[0] = (float) parseDouble(snap.camY(), 0.0);
		camZ[0] = (float) parseDouble(snap.camZ(), 0.0);
		camYaw[0] = (float) parseDouble(snap.camYaw(), 0.0);
		camPitch[0] = (float) parseDouble(snap.camPitch(), 0.0);
		camEaseBuffer.set(snap.camEase() != null ? snap.camEase() : "SMOOTH");
	}

	private void renderCameraClipOnlyPanel(EventPropertiesRef ref, Timeline timeline) {
		ImGui.text(BBTexts.get("beatblock.camera.clip_times"));
		double bpm = bpm(timeline);
		clipStart.render("camClipStart", BBTexts.get("beatblock.camera.start"), bpm);
		clipEnd.render("camClipEnd", BBTexts.get("beatblock.camera.end"), bpm);
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
		var result = presenter.applyCameraClipOnly(
			ref,
			timeline,
			editor.getCommandManager(),
			clipStart.seconds(),
			clipEnd.seconds(),
			camClipPathVisibleProxy.get()
		);
		if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
			validationError = message;
			return;
		}
		validationError = null;
		bindBuffers(ref);
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
		double bpm = bpm(timeline);

		ImInt kindIdx = new ImInt(kindIndex(kind));
		ImGui.setNextItemWidth(-1f);
		if (ImGui.combo(BBTexts.get("beatblock.camera.segment_kind") + "##camSegKind", kindIdx, camKindLabels())) {
			CameraSegmentKind newKind = CAM_KINDS[kindIdx.get()];
			if (newKind != kind) {
				applyCameraKindChange(ref, timeline, newKind);
				kind = newKind;
			}
		}

		segmentDuration.render("camSegDur", BBTexts.get("beatblock.camera.segment_duration"), bpm);
		ImGui.checkbox(BBTexts.get("beatblock.camera.show_path") + "##camSegPathVis", camSegPathVisibleProxy);

		ImGui.separator();
		switch (kind) {
			case PATH -> ImGui.textDisabled(BBTexts.get("beatblock.camera.path.hint"));
			case DOLLY -> {
				ImGui.textDisabled(BBTexts.get("beatblock.camera.dolly.params"));
				renderSegDrag(BBTexts.get("beatblock.camera.param.start_x"), "startX", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.start_y"), "startY", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.start_z"), "startZ", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.end_x"), "endX", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.end_y"), "endY", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.end_z"), "endZ", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.base_yaw"), "baseYawDeg", 1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.base_pitch"), "basePitchDeg", 1f);
			}
			case ORBIT -> {
				ImGui.textDisabled(BBTexts.get("beatblock.camera.orbit.params"));
				renderSegDrag(BBTexts.get("beatblock.camera.param.target_x"), "targetX", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.target_y"), "targetY", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.target_z"), "targetZ", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.radius"), "radius", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.height_offset"), "height", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.yaw_start"), "yawStartDeg", 1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.yaw_end"), "yawEndDeg", 1f);
			}
			case CRANE -> {
				ImGui.textDisabled(BBTexts.get("beatblock.camera.crane.params"));
				renderSegDrag(BBTexts.get("beatblock.camera.param.start_x"), "startX", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.start_y"), "startY", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.start_z"), "startZ", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.end_x"), "endX", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.end_y"), "endY", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.end_z"), "endZ", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.yaw"), "yawDeg", 1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.pitch"), "pitchDeg", 1f);
			}
			case SHAKE -> {
				ImGui.textDisabled(BBTexts.get("beatblock.camera.shake.params"));
				renderSegDrag(BBTexts.get("beatblock.camera.param.anchor_x"), "anchorX", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.anchor_y"), "anchorY", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.anchor_z"), "anchorZ", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.yaw"), "yawDeg", 1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.pitch"), "pitchDeg", 1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.distance"), "distance", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.amplitude"), "amplitude", 0.01f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.frequency"), "frequencyHz", 0.1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.beat_sync"), "beatSync", 1f);
				renderSegDrag(BBTexts.get("beatblock.camera.param.beats_per_pulse"), "beatsPerPulse", 0.05f);
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

	private void renderSegDrag(String label, String key, float speed) {
		float[] value = camSegParamFloats.computeIfAbsent(key, k -> new float[]{0f});
		ImGui.text(label);
		ImGui.setNextItemWidth(-1f);
		ImGui.dragFloat("##camSegP_" + key, value, speed, -1.0e6f, 1.0e6f, "%.2f");
	}

	private void captureCurrentViewToSegment(CameraSegmentKind kind) {
		var captured = presenter.captureSegmentViewParams(kind);
		if (captured.isEmpty()) {
			validationError = BBTexts.get("beatblock.camera.no_camera");
			return;
		}
		for (Map.Entry<String, String> entry : captured.get().entrySet()) {
			camSegParamFloats.put(entry.getKey(), new float[]{(float) parseDouble(entry.getValue(), 0.0)});
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
		CameraSegmentKind currentKind = CameraSegmentKind.fromParam(ref.event().getParameters().get("kind"));
		Map<String, String> rawParams = new HashMap<>();
		for (String key : CameraEventPropertiesEditor.paramKeysForKind(currentKind)) {
			float[] value = camSegParamFloats.get(key);
			if (value != null) {
				rawParams.put(key, formatFloat(value[0]));
			}
		}
		var result = presenter.applyCameraSegment(
			ref,
			timeline,
			editor.getCommandManager(),
			segmentDuration.seconds(),
			camSegPathVisibleProxy.get(),
			rawParams
		);
		if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
			validationError = message;
			return;
		}
		validationError = null;
		bindBuffers(ref);
	}

	private void renderCameraKeyframePanel(EventPropertiesRef ref, Timeline timeline, SelectionState selectionState) {
		double bpm = bpm(timeline);
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
		keyframeTime.render("camKfTime", BBTexts.get("beatblock.camera.time"), bpm);
		renderPoseDrag(BBTexts.get("beatblock.common.coord_x") + "##camKfX", camX, 0.1f);
		renderPoseDrag(BBTexts.get("beatblock.common.coord_y") + "##camKfY", camY, 0.1f);
		renderPoseDrag(BBTexts.get("beatblock.common.coord_z") + "##camKfZ", camZ, 0.1f);
		renderPoseDrag(BBTexts.get("beatblock.camera.param.yaw") + "##camKfYaw", camYaw, 1f);
		renderPoseDrag(BBTexts.get("beatblock.camera.param.pitch") + "##camKfPitch", camPitch, 1f);
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
				camX[0] = (float) sample.x();
				camY[0] = (float) sample.y();
				camZ[0] = (float) sample.z();
				camYaw[0] = sample.yaw();
				camPitch[0] = sample.pitch();
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
			TimelineEditor editor = runtime().timelineEditor();
			if (editor == null) {
				validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
			} else {
				var result = presenter.deleteCameraKeyframe(ref, timeline, editor.getCommandManager());
				if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
					validationError = message;
				} else {
					validationError = null;
					String id = ref.event().getId();
					if (selectionState != null) {
						selectionState.deselectEvent(id);
					}
					boundRefKey = null;
				}
			}
		}

		com.beatblock.client.camera.TimelineCameraController.getInstance().previewKeyframeDirect(
			new com.beatblock.client.camera.TimelineCameraEvaluator.CameraSample(
				new net.minecraft.util.math.Vec3d(camX[0], camY[0], camZ[0]),
				camYaw[0],
				camPitch[0]
			)
		);
	}

	private static void renderPoseDrag(String label, float[] value, float speed) {
		ImGui.setNextItemWidth(-1f);
		ImGui.dragFloat(label, value, speed, -1.0e6f, 1.0e6f, "%.2f");
	}

	private void applyCameraKeyframe(EventPropertiesRef ref, Timeline timeline) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
			return;
		}
		var result = presenter.applyCameraKeyframe(
			ref,
			timeline,
			editor.getCommandManager(),
			keyframeTime.seconds(),
			camX[0],
			camY[0],
			camZ[0],
			camYaw[0],
			camPitch[0],
			valueOf(camEaseBuffer).trim()
		);
		if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
			validationError = message;
			return;
		}
		validationError = null;
		bindBuffers(ref);
	}

	private static String valueOf(ImString text) {
		String value = text != null ? text.get() : null;
		return value != null ? value : "";
	}

	private static double parseDouble(String raw, double fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return Double.parseDouble(raw.trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private static String formatFloat(float value) {
		return String.format(Locale.ROOT, "%.6f", value);
	}
}
