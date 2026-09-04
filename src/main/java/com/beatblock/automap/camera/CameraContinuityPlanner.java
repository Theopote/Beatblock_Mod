package com.beatblock.automap.camera;

import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Shot Continuity：评估相邻镜头位姿跳跃，为后一镜选择 {@link CameraShotTransition}。
 * <p>
 * 防止 north→south 等大角度在极短间隔内被平滑穿模；几何估算与
 * {@link CameraShotTimelineWriter} 默认眼点约定对齐（无舞台 bounds 时用 framing fallback）。
 */
public final class CameraContinuityPlanner {

	private CameraContinuityPlanner() {}

	public static List<CameraShot> plan(@Nullable List<CameraShot> shots) {
		return plan(shots, CameraContinuityOptions.defaults());
	}

	public static List<CameraShot> plan(
		@Nullable List<CameraShot> shots,
		@Nullable CameraContinuityOptions options
	) {
		if (shots == null || shots.isEmpty()) return List.of();
		CameraContinuityOptions opts = options != null ? options : CameraContinuityOptions.defaults();
		List<CameraShot> ordered = new ArrayList<>(shots);
		ordered.sort(Comparator.comparingDouble(CameraShot::startSeconds));

		List<CameraShot> out = new ArrayList<>(ordered.size());
		out.add(ordered.get(0));
		for (int i = 1; i < ordered.size(); i++) {
			CameraShot previous = ordered.get(i - 1);
			CameraShot next = ordered.get(i);
			CameraContinuityJump jump = evaluate(previous, next);
			CameraShotTransition transition = chooseTransition(jump, opts);
			out.add(withTransition(next, transition));
		}
		return List.copyOf(out);
	}

	public static CameraContinuityJump evaluate(CameraShot previous, CameraShot next) {
		Objects.requireNonNull(previous, "previous");
		Objects.requireNonNull(next, "next");
		EstimatedPose a = estimatePose(previous);
		EstimatedPose b = estimatePose(next);
		double gap = Math.max(0.0, next.startSeconds() - previous.endSeconds());
		double positionDelta = a.eye.distanceTo(b.eye);
		double yawDelta = shortestYawDeltaDeg(a.yawDeg, b.yawDeg);
		double pitchDelta = Math.abs(a.pitchDeg - b.pitchDeg);
		boolean subjectChanged = !sameSubject(previous.effectiveLookAt(), next.effectiveLookAt());
		boolean opposite = a.screenDir.dotProduct(b.screenDir) < -0.35;
		return new CameraContinuityJump(gap, positionDelta, yawDelta, pitchDelta, subjectChanged, opposite);
	}

	public static CameraShotTransition chooseTransition(
		CameraContinuityJump jump,
		@Nullable CameraContinuityOptions options
	) {
		CameraContinuityOptions opts = options != null ? options : CameraContinuityOptions.defaults();
		if (jump == null) return CameraShotTransition.CUT;

		if (jump.gapSeconds() >= opts.largeGapCutSeconds()) {
			return CameraShotTransition.CUT;
		}

		boolean hardSpatial =
			jump.oppositeScreenDirection()
				|| jump.yawDeltaDeg() >= opts.hardYawDeg()
				|| jump.positionDeltaBlocks() >= opts.hardPositionBlocks();

		if (hardSpatial) {
			// 极短间隔大跳跃：硬切，避免穿模平滑
			if (jump.gapSeconds() <= opts.abuttingGapSeconds()) {
				return CameraShotTransition.CUT;
			}
			return CameraShotTransition.WHIP;
		}

		boolean soft =
			!jump.subjectChanged()
				&& jump.positionDeltaBlocks() <= opts.softPositionBlocks()
				&& jump.yawDeltaDeg() <= opts.softYawDeg()
				&& jump.pitchDeltaDeg() <= opts.softPitchDeg();

		if (soft && jump.abutting(opts.abuttingGapSeconds() * 2.0)) {
			return CameraShotTransition.SMOOTH_MOVE;
		}

		if (jump.subjectChanged()
			|| jump.positionDeltaBlocks() > opts.softPositionBlocks()
			|| jump.yawDeltaDeg() > opts.softYawDeg()) {
			return CameraShotTransition.DISSOLVE;
		}

		return CameraShotTransition.SMOOTH_MOVE;
	}

	static EstimatedPose estimatePose(CameraShot shot) {
		Vec3d lookAt = approximateLookAt(shot.effectiveLookAt());
		CameraFramingSolution framing = CameraFramingEngine.fallback(shot.framing(), lookAt);
		Vec3d eye = eyeForMovement(shot.movement(), framing);
		Vec3d toSubject = lookAt.subtract(eye);
		double horizontal = Math.sqrt(toSubject.x * toSubject.x + toSubject.z * toSubject.z);
		float yaw = horizontal < 1e-6
			? 0f
			: (float) Math.toDegrees(Math.atan2(-toSubject.x, toSubject.z));
		float pitch = (float) framing.pitchDeg();
		Vec3d screen = new Vec3d(eye.x - lookAt.x, 0.0, eye.z - lookAt.z);
		if (screen.lengthSquared() < 1e-8) {
			screen = new Vec3d(0.0, 0.0, 1.0);
		} else {
			screen = screen.normalize();
		}
		return new EstimatedPose(eye, yaw, pitch, screen);
	}

	private static Vec3d eyeForMovement(CameraShotMovement movement, CameraFramingSolution framing) {
		return switch (movement != null ? movement : CameraShotMovement.HOLD) {
			case ORBIT -> framing.eyePositionSouth(); // 与 writer 起始角 0°（+Z）一致
			case PULL_OUT -> eyePositionNorth(framing, 0.5, 1.0); // 与默认南向眼点相对，便于检测对穿
			case SHAKE -> framing.eyePositionSouth(0.7, 1.0);
			case PUSH_IN, PAN, HOLD -> framing.eyePositionSouth();
		};
	}

	private static Vec3d eyePositionNorth(CameraFramingSolution framing, double distanceScale, double heightScale) {
		Vec3d lookAt = framing.lookAt();
		return new Vec3d(
			lookAt.x,
			lookAt.y + framing.eyeHeightAboveLookAt() * heightScale,
			lookAt.z - framing.horizontalDistance() * distanceScale
		);
	}

	private static Vec3d approximateLookAt(CameraSubject subject) {
		if (subject == null) return Vec3d.ZERO;
		return switch (subject.kind()) {
			case WORLD_POSITION -> new Vec3d(subject.x(), subject.y(), subject.z());
			case STAGE_OBJECT, STAGE_GROUP, BUILD_LAYER, ANIMATED_TARGET, ALL_STAGE_OBJECTS -> {
				try {
					yield CameraSubjectResolver.resolveRequired(subject, CameraSubjectRole.LOOK_AT);
				} catch (RuntimeException ignored) {
					yield Vec3d.ZERO;
				}
			}
		};
	}

	private static boolean sameSubject(CameraSubject a, CameraSubject b) {
		if (a == null || b == null) return a == b;
		if (a.kind() != b.kind()) return false;
		return switch (a.kind()) {
			case WORLD_POSITION ->
				Math.abs(a.x() - b.x()) < 1e-3
					&& Math.abs(a.y() - b.y()) < 1e-3
					&& Math.abs(a.z() - b.z()) < 1e-3;
			default -> Objects.equals(a.refId(), b.refId());
		};
	}

	private static double shortestYawDeltaDeg(float from, float to) {
		double delta = Math.abs(to - from) % 360.0;
		if (delta > 180.0) delta = 360.0 - delta;
		return delta;
	}

	private static CameraShot withTransition(CameraShot shot, CameraShotTransition transition) {
		return new CameraShot(
			shot.startSeconds(),
			shot.durationSeconds(),
			shot.subject(),
			shot.framing(),
			shot.movement(),
			shot.lookAt(),
			transition,
			shot.easing(),
			shot.collisionPolicy(),
			shot.beatAlignment(),
			shot.sectionIndex()
		);
	}

	record EstimatedPose(Vec3d eye, float yawDeg, float pitchDeg, Vec3d screenDir) {}
}
