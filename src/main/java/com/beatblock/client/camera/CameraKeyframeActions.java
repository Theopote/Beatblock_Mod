package com.beatblock.client.camera;

import com.beatblock.BeatBlock;
import com.beatblock.automap.camera.CapturedCameraPose;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.TimelineClock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 摄像机路径关键帧：捕获当前游戏视角写入 Timeline。
 * <p>
 * 新增关键帧走 {@link CameraViewCaptureService}（Command / one Undo）；删除仍为直接移除
 *（Properties 删除走 {@code DeleteEventCommand}）。
 */
public final class CameraKeyframeActions {

	private CameraKeyframeActions() {}

	public static void addKeyframeAtPlayhead(Timeline timeline, TimelineClock clock) {
		if (timeline == null || clock == null) return;
		addKeyframeAtTime(timeline, clock.getCurrentTimeSeconds());
	}

	public static void addKeyframeAtTime(Timeline timeline, double timeSeconds) {
		if (timeline == null) return;
		TimelineEditor editor = timelineEditorOrNull();
		Optional<CapturedCameraPose> pose = sampleCurrentView();
		if (editor == null || pose.isEmpty()) {
			return;
		}
		CameraViewCaptureService.addKeyframeAtTime(timeline, editor, timeSeconds, pose.get());
	}

	public static Optional<CapturedCameraPose> sampleCurrentView() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.gameRenderer == null) {
			return Optional.empty();
		}
		Camera camera = mc.gameRenderer.getCamera();
		if (camera == null) {
			return Optional.empty();
		}
		Vec3d eye = camera.getCameraPos();
		return Optional.of(new CapturedCameraPose(
			eye.x, eye.y, eye.z, camera.getYaw(), camera.getPitch()));
	}

	public static boolean deleteKeyframeEvent(Timeline timeline, String eventId) {
		if (timeline == null || eventId == null || eventId.isBlank()) return false;
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null) return false;
		for (Clip clip : cam.getClips()) {
			if (clip == null) continue;
			for (TimelineEvent e : List.copyOf(clip.getEvents())) {
				if (e.getType() == EventType.CAMERA_KEYFRAME && eventId.equals(e.getId())) {
					return TimelineOperations.removeEvent(clip, eventId);
				}
			}
		}
		return false;
	}

	private static @Nullable TimelineEditor timelineEditorOrNull() {
		try {
			return BeatBlock.getContext().timelineEditor();
		} catch (Exception ignored) {
			return null;
		}
	}
}
