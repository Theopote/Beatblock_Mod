package com.beatblock.client.camera;

import com.beatblock.automap.camera.CameraShotInsertionService;
import com.beatblock.automap.camera.CapturedCameraPose;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.command.AddCameraKeyframeCommand;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CreateCameraClipCommand;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.ui.TimelinePanelVisibility;
import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Capture-current-view gateway: Command + one Undo + select keyframe + open Properties.
 * <p>
 * Two explicit Creator actions share pose sampling:
 * <ul>
 *   <li>{@link #createPathFromCurrentView} — always create a new PATH clip (new shot)</li>
 *   <li>{@link #addKeyframeAtPlayhead} — insert a keyframe on the selected PATH clip</li>
 * </ul>
 * Track-header / context-menu uses {@link #addKeyframeAtTime}.
 */
public final class CameraViewCaptureService {

	private static final double KEYFRAME_MERGE_EPS = 0.04;

	public record CaptureResult(
		boolean success,
		@Nullable String eventId,
		@Nullable String clipId,
		String message
	) {
		public static CaptureResult fail(String message) {
			return new CaptureResult(false, null, null, message != null ? message : "");
		}

		public static CaptureResult ok(String eventId, String clipId, String message) {
			return new CaptureResult(true, eventId, clipId, message != null ? message : "");
		}
	}

	private CameraViewCaptureService() {
	}

	/**
	 * Capture Current View: always create a new PATH clip from the live pose (ignores selection).
	 */
	public static CaptureResult createPathFromCurrentView(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		@Nullable CapturedCameraPose pose
	) {
		if (timeline == null || editor == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		if (pose == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.camera.no_camera"));
		}
		CommandManager commands = editor.getCommandManager();
		if (commands == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.common.timeline_editor_not_initialized"));
		}
		double playhead = editor.getClock().getCurrentTimeSeconds();
		return createPathClipWithKeyframe(timeline, editor, playhead, pose);
	}

	/**
	 * Add Keyframe at Playhead: requires a selected PATH camera clip.
	 */
	public static CaptureResult addKeyframeAtPlayhead(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		@Nullable CapturedCameraPose pose
	) {
		if (timeline == null || editor == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		if (pose == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.camera.no_camera"));
		}
		CommandManager commands = editor.getCommandManager();
		if (commands == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.common.timeline_editor_not_initialized"));
		}
		Clip selected = resolveSelectedCameraClip(timeline, editor.getSelectionState());
		if (selected == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.camera_creator.capture_need_path"));
		}
		double playhead = editor.getClock().getCurrentTimeSeconds();
		return addKeyframeToPathClip(timeline, editor, selected, playhead, pose);
	}

	/**
	 * @deprecated Prefer {@link #createPathFromCurrentView} or {@link #addKeyframeAtPlayhead}.
	 * Kept for older call sites: selected PATH → keyframe; else create clip.
	 */
	@Deprecated
	public static CaptureResult captureCurrentView(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		@Nullable CapturedCameraPose pose
	) {
		if (timeline == null || editor == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		Clip selected = resolveSelectedCameraClip(timeline, editor.getSelectionState());
		if (selected != null) {
			return addKeyframeAtPlayhead(timeline, editor, pose);
		}
		return createPathFromCurrentView(timeline, editor, pose);
	}

	/**
	 * Track-header / context-menu: add keyframe into the PATH clip covering {@code timeSeconds}.
	 */
	public static CaptureResult addKeyframeAtTime(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		double timeSeconds,
		@Nullable CapturedCameraPose pose
	) {
		if (timeline == null || editor == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		if (pose == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.camera.no_camera"));
		}
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.camera_creator.capture_failed"));
		}
		Clip clip = findActiveClip(cam, timeSeconds);
		if (clip == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.camera_creator.capture_need_path"));
		}
		return addKeyframeToPathClip(timeline, editor, clip, timeSeconds, pose);
	}

	private static CaptureResult addKeyframeToPathClip(
		Timeline timeline,
		TimelineEditor editor,
		Clip clip,
		double timeSeconds,
		CapturedCameraPose pose
	) {
		TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(clip);
		CameraSegmentKind kind = seg != null
			? CameraSegmentKind.fromParam(seg.getParameters().get("kind"))
			: CameraSegmentKind.PATH;
		if (kind != CameraSegmentKind.PATH) {
			return CaptureResult.fail(BBTexts.get("beatblock.camera_creator.capture_need_path"));
		}

		double t = Math.max(clip.getStartTimeSeconds(), Math.min(timeSeconds, clip.getEndTimeSeconds()));
		for (TimelineEvent e : clip.getEvents()) {
			if (e.getType() != EventType.CAMERA_KEYFRAME) continue;
			if (Math.abs(e.getTimeSeconds() - t) < KEYFRAME_MERGE_EPS) {
				return CaptureResult.fail(BBTexts.get("beatblock.camera_creator.capture_merge_skip"));
			}
		}

		CommandManager commands = editor.getCommandManager();
		var params = CameraTrackFactory.keyframeParams(
			pose.eyeX(), pose.eyeY(), pose.eyeZ(), pose.yawDeg(), pose.pitchDeg(), "SMOOTH");
		AddCameraKeyframeCommand command = new AddCameraKeyframeCommand(
			timeline, clip.getId(), t, params);
		commands.execute(command);
		if (!command.wasApplied()) {
			return CaptureResult.fail(BBTexts.get("beatblock.camera_creator.capture_failed"));
		}
		String eventId = Objects.requireNonNull(command.createdEventId());
		selectKeyframeAndOpenProperties(editor, clip.getId(), eventId);
		editor.syncClockDuration();
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		return CaptureResult.ok(
			eventId,
			clip.getId(),
			BBTexts.get("beatblock.camera_creator.captured_keyframe")
		);
	}

	private static CaptureResult createPathClipWithKeyframe(
		Timeline timeline,
		TimelineEditor editor,
		double startSeconds,
		CapturedCameraPose pose
	) {
		CommandManager commands = editor.getCommandManager();
		double duration = CameraShotInsertionService.DEFAULT_PATH_DURATION_SECONDS;
		CreateCameraClipCommand command = new CreateCameraClipCommand(timeline, tl ->
			CameraTrackFactory.addPathSegment(
				tl,
				startSeconds,
				duration,
				pose.eyeX(), pose.eyeY(), pose.eyeZ(),
				pose.yawDeg(), pose.pitchDeg(),
				"SMOOTH",
				TimelineGenerationMetadata.manual(),
				null
			)
		);
		commands.execute(command);
		if (!command.wasApplied()) {
			return CaptureResult.fail(BBTexts.get("beatblock.camera_creator.capture_failed"));
		}
		String clipId = Objects.requireNonNull(command.createdClipId());
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		Clip clip = cam != null ? cam.getClip(clipId) : null;
		String eventId = findFirstKeyframeId(clip);
		if (eventId == null) {
			return CaptureResult.fail(BBTexts.get("beatblock.camera_creator.capture_failed"));
		}
		selectKeyframeAndOpenProperties(editor, clipId, eventId);
		editor.syncClockDuration();
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		return CaptureResult.ok(
			eventId,
			clipId,
			BBTexts.get("beatblock.camera_creator.captured_path")
		);
	}

	private static void selectKeyframeAndOpenProperties(
		TimelineEditor editor,
		String clipId,
		String eventId
	) {
		SelectionState selection = editor.getSelectionState();
		selection.clearEvents();
		selection.clearClips();
		selection.selectClip(clipId);
		selection.selectEvent(eventId);
		TimelinePanelVisibility.openTimelineProperties();
	}

	private static @Nullable Clip resolveSelectedCameraClip(
		Timeline timeline,
		@Nullable SelectionState selection
	) {
		if (selection == null || selection.getSelectedClips().isEmpty()) {
			return null;
		}
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null) {
			return null;
		}
		for (String clipId : selection.getSelectedClips()) {
			Clip clip = cam.getClip(clipId);
			if (clip != null) {
				return clip;
			}
		}
		return null;
	}

	private static @Nullable Clip findActiveClip(Track cam, double t) {
		Clip best = null;
		double bestStart = -1;
		for (Clip c : cam.getClips()) {
			if (c == null) continue;
			double s = c.getStartTimeSeconds();
			double e = c.getEndTimeSeconds();
			if (t + 1e-6 < s || t > e + 1e-6) continue;
			if (s > bestStart) {
				bestStart = s;
				best = c;
			}
		}
		return best;
	}

	private static @Nullable String findFirstKeyframeId(@Nullable Clip clip) {
		if (clip == null) return null;
		Optional<TimelineEvent> kf = clip.getEvents().stream()
			.filter(e -> e.getType() == EventType.CAMERA_KEYFRAME)
			.findFirst();
		return kf.map(TimelineEvent::getId).orElse(null);
	}
}
