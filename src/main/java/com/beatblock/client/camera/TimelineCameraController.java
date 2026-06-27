package com.beatblock.client.camera;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editor.InteractionMode;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 时间线摄像机控制器
 * <p>
 * 职责：
 * - 根据播放 / 拖动 / 预览状态采样 CameraSample，写入 CameraRuntime
 * - 在需要时接管/释放相机控制权（CameraRuntime.Owner）
 */
public final class TimelineCameraController {

	private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCameraController.class);
	private static final TimelineCameraController INSTANCE = new TimelineCameraController();

	private Supplier<BeatBlockContext> contextSource = BeatBlock::getContext;
	private boolean previewingKeyframe = false;
	private TimelineCameraEvaluator.CameraSample keyframeSample = null;
	private int keyframePreviewFrames = 0;

	private long lastUpdateNanoTime = 0L;

	private TimelineCameraController() {}

	public static TimelineCameraController getInstance() {
		return INSTANCE;
	}

	public void bindContext(Supplier<BeatBlockContext> contextSource) {
		this.contextSource = contextSource != null ? contextSource : BeatBlock::getContext;
	}

	private BeatBlockContext ctx() {
		return contextSource.get();
	}

	public void previewKeyframeDirect(TimelineCameraEvaluator.CameraSample sample) {
		if (sample == null) return;
		this.keyframeSample = sample;
		this.keyframePreviewFrames = 5;
	}

	public void stopKeyframePreview() {
		this.keyframePreviewFrames = 0;
		this.keyframeSample = null;
	}

	public void onTimelineUiClosed() {
		stopKeyframePreview();
		CameraRuntime.getInstance().reset();
		LOGGER.debug("[TimelineCameraController] UI 关闭，释放摄像机控制权");
	}

	/** 视频导出：在指定时间采样镜头并应用到 CameraRuntime。 */
	public void sampleAtExportTime(double timeSeconds) {
		Timeline timeline = ctx().timeline();
		if (timeline == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d anchor = client != null && client.player != null ? client.player.getEyePos() : Vec3d.ZERO;
		float fallbackYaw = client != null && client.player != null ? client.player.getYaw() : 0f;
		float fallbackPitch = client != null && client.player != null ? client.player.getPitch() : 0f;
		TimelineCameraEvaluator.CameraSample sample = TimelineCameraEvaluator.evaluate(
			timeline,
			timeSeconds,
			anchor,
			fallbackYaw,
			fallbackPitch
		);
		CameraRuntime runtime = CameraRuntime.getInstance();
		runtime.setOwner(CameraRuntime.Owner.TIMELINE);
		if (sample != null) {
			runtime.applyTimelineSample(sample);
		}
	}

	public synchronized void tick() {
		long now = System.nanoTime();
		float delta = lastUpdateNanoTime == 0L ? 1f / 60f : (now - lastUpdateNanoTime) / 1_000_000_000f;
		lastUpdateNanoTime = now;
		delta = Math.min(delta, 0.5f);

		updateOwnerAndSample(delta);
	}

	private void updateOwnerAndSample(float deltaSeconds) {
		var musicPlayer = ctx().musicPlayer();
		TimelineEditor editor = ctx().timelineEditor();
		Timeline timeline = ctx().timeline();

		boolean playing = musicPlayer != null && musicPlayer.isPlaying();
		if (editor != null && editor.getClock().isPlaying()) {
			playing = true;
		}

		boolean scrubbing = false;
		if (editor != null && editor.getInteractionState() != null) {
			scrubbing = editor.getInteractionState().getMode() == InteractionMode.SCRUB_TIME;
		}
		if (scrubbing && !ImGui.isMouseDown(0)) {
			scrubbing = false;
		}

		if (keyframePreviewFrames > 0 && !playing && !scrubbing) {
			previewingKeyframe = true;
			keyframePreviewFrames--;
		} else {
			previewingKeyframe = false;
			keyframeSample = null;
			keyframePreviewFrames = 0;
		}

		CameraRuntime runtime = CameraRuntime.getInstance();

		boolean hasCameraTrackClips = false;
		if (timeline != null) {
			var track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
			if (track != null && !track.getClips().isEmpty()) {
				hasCameraTrackClips = true;
			}
		}

		boolean wantsTimeline = previewingKeyframe || ((playing || scrubbing) && hasCameraTrackClips);

		if (wantsTimeline && !runtime.isTimelineOwner()) {
			runtime.setOwner(CameraRuntime.Owner.TIMELINE);
			LOGGER.debug("[CameraController] 接管相机控制");
		} else if (!wantsTimeline && runtime.isTimelineOwner()) {
			runtime.syncPlayerToSample(runtime.getCurrentSample());
			runtime.cancelPlayerLerp();
			runtime.setOwner(CameraRuntime.Owner.PLAYER);
			LOGGER.debug("[CameraController] 恢复玩家控制");
		}

		if (runtime.isTimelineOwner() || wantsTimeline) {
			if (playing || scrubbing) {
				double timeSeconds = BeatBlockClientDriver.previewTimelineTimeSeconds();
				MinecraftClient client = MinecraftClient.getInstance();
				Vec3d anchor = client.player != null ? client.player.getEyePos() : Vec3d.ZERO;
				float fallbackYaw = client.player != null ? client.player.getYaw() : 0f;
				float fallbackPitch = client.player != null ? client.player.getPitch() : 0f;

				TimelineCameraEvaluator.CameraSample sample = TimelineCameraEvaluator.evaluate(
					timeline, timeSeconds, anchor, fallbackYaw, fallbackPitch);

				if (sample != null) {
					runtime.applyTimelineSample(sample);
				}
			} else if (previewingKeyframe && keyframeSample != null) {
				runtime.applyTimelineSample(keyframeSample);
			}
		} else {
			runtime.tickPlayerLerp(deltaSeconds);
		}
	}
}
