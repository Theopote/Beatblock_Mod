package com.beatblock.client.camera;

import com.beatblock.BeatBlock;
import com.beatblock.automap.camera.CameraFramingEngine;
import com.beatblock.automap.camera.CameraSubjectBoundsResolver;
import com.beatblock.automap.camera.StageBounds;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraPathMetadata;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * World overlays for Camera Creator: motion path, playhead frustum, subject bounds.
 * Path still respects per-clip {@link CameraPathMetadata}; Creator toolbar can hide all paths globally.
 */
public final class CameraPathWorldRenderer {

	private static final int COLOR_PATH = 0xCC_FF_CC_66;
	private static final int COLOR_DOLLY = 0xCC_66_CC_FF;
	private static final int COLOR_ORBIT = 0xCC_DD_77_FF;
	private static final int COLOR_CRANE = 0xCC_77_DD_88;
	private static final int COLOR_KEYFRAME = 0xFF_FF_AA_33;
	private static final int COLOR_FRUSTUM = 0xAA_66_DD_FF;
	private static final int COLOR_BOUNDS = 0xAA_88_FF_66;
	private static final float LINE_WIDTH = 2.0f;
	private static final float KEY_CROSS = 0.18f;
	private static final int SAMPLE_STEPS = 56;
	private static final double FRUSTUM_NEAR = 0.35;
	private static final double FRUSTUM_FAR = 8.0;

	private CameraPathWorldRenderer() {}

	public static void renderIfNeeded(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.gameRenderer == null) return;
		Timeline timeline = BeatBlock.getContext().timeline();
		if (timeline == null) return;

		Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
		Vec3d anchor = mc.player != null ? mc.player.getEyePos() : camPos;
		float fallbackYaw = mc.player != null ? mc.player.getYaw() : 0f;
		float fallbackPitch = mc.player != null ? mc.player.getPitch() : 0f;
		matrices.push();
		Matrix4f mat = matrices.peek().getPositionMatrix();
		VertexConsumer buf = consumers.getBuffer(RenderLayers.LINES);

		if (CameraCreatorVisualization.showCameraPath()) {
			renderPaths(timeline, buf, mat, camPos, anchor, fallbackYaw, fallbackPitch);
		}
		if (CameraCreatorVisualization.showFrustum()) {
			renderFrustum(timeline, buf, mat, camPos, anchor, fallbackYaw, fallbackPitch);
		}
		if (CameraCreatorVisualization.showSubjectBounds()) {
			renderSubjectBounds(buf, mat, camPos);
		}

		matrices.pop();
	}

	private static void renderPaths(
		Timeline timeline,
		VertexConsumer buf,
		Matrix4f mat,
		Vec3d camPos,
		Vec3d anchor,
		float fallbackYaw,
		float fallbackPitch
	) {
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null || cam.getClips().isEmpty()) return;

		for (Clip clip : cam.getClips()) {
			if (clip == null) continue;
			if (!CameraPathMetadata.isPathVisible(timeline, clip.getId())) continue;
			TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(clip);
			if (seg == null) continue;
			CameraSegmentKind kind = CameraSegmentKind.fromParam(seg.getParameters().get("kind"));
			if (kind == CameraSegmentKind.SHAKE) continue;

			int lineArgb = switch (kind) {
				case DOLLY -> COLOR_DOLLY;
				case ORBIT -> COLOR_ORBIT;
				case CRANE -> COLOR_CRANE;
				default -> COLOR_PATH;
			};

			double t0 = clip.getStartTimeSeconds();
			double t1 = clip.getEndTimeSeconds();
			if (t1 <= t0) continue;

			Vec3d prev = null;
			for (int i = 0; i <= SAMPLE_STEPS; i++) {
				double u = i / (double) SAMPLE_STEPS;
				double t = t0 + (t1 - t0) * u;
				TimelineCameraEvaluator.CameraSample sm = TimelineCameraEvaluator.evaluateClip(
					clip, timeline, t, anchor, fallbackYaw, fallbackPitch);
				if (sm == null) continue;
				Vec3d p = sm.position();
				if (prev != null) {
					emitLine(buf, mat, camPos, prev, p, lineArgb);
				}
				prev = p;
			}

			if (kind == CameraSegmentKind.PATH) {
				List<TimelineEvent> kf = new ArrayList<>();
				for (TimelineEvent e : clip.getEvents()) {
					if (e.getType() == EventType.CAMERA_KEYFRAME) kf.add(e);
				}
				kf.sort(Comparator.comparingDouble(TimelineEvent::getTimeSeconds));
				for (TimelineEvent e : kf) {
					drawCross(buf, mat, camPos, keyframePos(e));
				}
			}
		}
	}

	private static void renderFrustum(
		Timeline timeline,
		VertexConsumer buf,
		Matrix4f mat,
		Vec3d camPos,
		Vec3d anchor,
		float fallbackYaw,
		float fallbackPitch
	) {
		double time = playheadSeconds();
		TimelineCameraEvaluator.CameraSample sample = TimelineCameraEvaluator.evaluate(
			timeline, time, anchor, fallbackYaw, fallbackPitch);
		if (sample == null || sample.position() == null) return;

		Vec3d eye = sample.position();
		double yawRad = Math.toRadians(-sample.yawDeg());
		double pitchRad = Math.toRadians(-sample.pitchDeg());
		double cosY = Math.cos(yawRad);
		double sinY = Math.sin(yawRad);
		double cosP = Math.cos(pitchRad);
		double sinP = Math.sin(pitchRad);
		// Forward / right / up in Minecraft yaw/pitch convention
		Vec3d forward = new Vec3d(-sinY * cosP, -sinP, cosY * cosP).normalize();
		Vec3d worldUp = new Vec3d(0, 1, 0);
		Vec3d right = forward.crossProduct(worldUp);
		if (right.lengthSquared() < 1e-8) {
			right = new Vec3d(1, 0, 0);
		} else {
			right = right.normalize();
		}
		Vec3d up = right.crossProduct(forward).normalize();

		double fovV = Math.toRadians(CameraFramingEngine.DEFAULT_FOV_DEG);
		double aspect = CameraFramingEngine.DEFAULT_ASPECT_RATIO;
		double nearH = Math.tan(fovV * 0.5) * FRUSTUM_NEAR;
		double nearW = nearH * aspect;
		double farH = Math.tan(fovV * 0.5) * FRUSTUM_FAR;
		double farW = farH * aspect;

		Vec3d nCenter = eye.add(forward.multiply(FRUSTUM_NEAR));
		Vec3d fCenter = eye.add(forward.multiply(FRUSTUM_FAR));
		Vec3d[] near = corners(nCenter, right, up, nearW, nearH);
		Vec3d[] far = corners(fCenter, right, up, farW, farH);

		for (int i = 0; i < 4; i++) {
			emitLine(buf, mat, camPos, near[i], near[(i + 1) % 4], COLOR_FRUSTUM);
			emitLine(buf, mat, camPos, far[i], far[(i + 1) % 4], COLOR_FRUSTUM);
			emitLine(buf, mat, camPos, near[i], far[i], COLOR_FRUSTUM);
		}
		emitLine(buf, mat, camPos, eye, nCenter, COLOR_FRUSTUM);
	}

	private static Vec3d[] corners(Vec3d center, Vec3d right, Vec3d up, double halfW, double halfH) {
		return new Vec3d[]{
			center.add(right.multiply(-halfW)).add(up.multiply(halfH)),
			center.add(right.multiply(halfW)).add(up.multiply(halfH)),
			center.add(right.multiply(halfW)).add(up.multiply(-halfH)),
			center.add(right.multiply(-halfW)).add(up.multiply(-halfH))
		};
	}

	private static void renderSubjectBounds(VertexConsumer buf, Matrix4f mat, Vec3d camPos) {
		Optional<StageBounds> bounds = CameraSubjectBoundsResolver.tryResolve(
			CameraCreatorVisualization.subjectForBounds());
		if (bounds.isEmpty()) return;
		StageBounds b = bounds.get();
		Vec3d min = b.min();
		Vec3d max = b.max();
		Vec3d[] c = {
			new Vec3d(min.x, min.y, min.z),
			new Vec3d(max.x, min.y, min.z),
			new Vec3d(max.x, min.y, max.z),
			new Vec3d(min.x, min.y, max.z),
			new Vec3d(min.x, max.y, min.z),
			new Vec3d(max.x, max.y, min.z),
			new Vec3d(max.x, max.y, max.z),
			new Vec3d(min.x, max.y, max.z)
		};
		int[][] edges = {
			{0, 1}, {1, 2}, {2, 3}, {3, 0},
			{4, 5}, {5, 6}, {6, 7}, {7, 4},
			{0, 4}, {1, 5}, {2, 6}, {3, 7}
		};
		for (int[] e : edges) {
			emitLine(buf, mat, camPos, c[e[0]], c[e[1]], COLOR_BOUNDS);
		}
	}

	private static double playheadSeconds() {
		try {
			TimelineEditor editor = BeatBlock.getContext().timelineEditor();
			if (editor != null) {
				return editor.getClock().getCurrentTimeSeconds();
			}
		} catch (Exception ignored) {
		}
		return 0.0;
	}

	private static Vec3d keyframePos(TimelineEvent e) {
		Map<String, Object> p = e.getParameters();
		return new Vec3d(num(p, "x", 0), num(p, "y", 0), num(p, "z", 0));
	}

	private static double num(Map<String, Object> p, String key, double def) {
		if (p == null) return def;
		Object o = p.get(key);
		if (o instanceof Number n) return n.doubleValue();
		if (o != null) {
			try {
				return Double.parseDouble(String.valueOf(o).trim());
			} catch (NumberFormatException e) {
				BeatBlock.LOGGER.debug("Invalid numeric camera parameter '{}', using default {}", key, def, e);
				return def;
			}
		}
		return def;
	}

	private static void emitLine(VertexConsumer buf, Matrix4f mat, Vec3d cam, Vec3d a, Vec3d b, int argb) {
		Vec3d ra = a.subtract(cam);
		Vec3d rb = b.subtract(cam);
		emitLineSegment(buf, mat, ra.x, ra.y, ra.z, rb.x, rb.y, rb.z, argb);
	}

	private static void drawCross(VertexConsumer buf, Matrix4f mat, Vec3d cam, Vec3d center) {
		Vec3d c = center.subtract(cam);
		emitLineSegment(buf, mat, c.x - KEY_CROSS, c.y, c.z, c.x + KEY_CROSS, c.y, c.z, COLOR_KEYFRAME);
		emitLineSegment(buf, mat, c.x, c.y - KEY_CROSS, c.z, c.x, c.y + KEY_CROSS, c.z, COLOR_KEYFRAME);
		emitLineSegment(buf, mat, c.x, c.y, c.z - KEY_CROSS, c.x, c.y, c.z + KEY_CROSS, COLOR_KEYFRAME);
	}

	private static void emitLineSegment(VertexConsumer buf, Matrix4f mat,
			double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
		float ca = ((argb >>> 24) & 255) / 255f;
		float cr = ((argb >>> 16) & 255) / 255f;
		float cg = ((argb >>> 8) & 255) / 255f;
		float cb = (argb & 255) / 255f;
		buf.vertex(mat, (float) x0, (float) y0, (float) z0).color(cr, cg, cb, ca).normal(0f, 1f, 0f).lineWidth(LINE_WIDTH);
		buf.vertex(mat, (float) x1, (float) y1, (float) z1).color(cr, cg, cb, ca).normal(0f, 1f, 0f).lineWidth(LINE_WIDTH);
	}
}
