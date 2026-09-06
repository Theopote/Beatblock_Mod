package com.beatblock.automap.camera;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/**
 * Compiles {@link CameraShot} / {@link CameraShotDraft} into Timeline camera clips (one-way).
 * <p>
 * After write, Timeline is source of truth — drafts are not kept in sync with later edits.
 */
public final class CameraShotTimelineWriter {

	/** Context-menu dolly reach (blocks); matches legacy Add Segment defaults. */
	public static final double POSE_DOLLY_REACH_BLOCKS = 8.0;
	/** Context-menu crane rise (blocks). */
	public static final double POSE_CRANE_DELTA_Y = 6.0;

	private CameraShotTimelineWriter() {}

	public record TaggedShot(CameraShot shot, TimelineGenerationMetadata metadata) {
		public CameraShotDraft toDraft() {
			return CameraShotDraft.semantic(shot);
		}
	}

	public record TaggedDraft(CameraShotDraft draft, TimelineGenerationMetadata metadata) {}

	public static int write(Timeline timeline, List<CameraShot> shots) {
		if (timeline == null || shots == null || shots.isEmpty()) return 0;
		int count = 0;
		for (CameraShot shot : shots) {
			if (writeDraft(timeline, CameraShotDraft.semantic(shot),
				TimelineGenerationMetadata.fromOrigin(TimelineEventOrigin.GENERATED))) {
				count++;
			}
		}
		return count;
	}

	public static int writeTagged(Timeline timeline, List<TaggedShot> shots) {
		if (timeline == null || shots == null || shots.isEmpty()) return 0;
		int count = 0;
		for (TaggedShot tagged : shots) {
			if (tagged == null || tagged.shot() == null) continue;
			TimelineGenerationMetadata metadata = tagged.metadata() != null
				? tagged.metadata()
				: TimelineGenerationMetadata.fromOrigin(TimelineEventOrigin.GENERATED);
			if (writeDraft(timeline, CameraShotDraft.semantic(tagged.shot()), metadata)) count++;
		}
		return count;
	}

	public static int writeDrafts(Timeline timeline, List<CameraShotDraft> drafts) {
		if (timeline == null || drafts == null || drafts.isEmpty()) return 0;
		int count = 0;
		for (CameraShotDraft draft : drafts) {
			if (writeDraft(timeline, draft,
				TimelineGenerationMetadata.fromOrigin(TimelineEventOrigin.GENERATED))) {
				count++;
			}
		}
		return count;
	}

	public static int writeTaggedDrafts(Timeline timeline, List<TaggedDraft> drafts) {
		if (timeline == null || drafts == null || drafts.isEmpty()) return 0;
		int count = 0;
		for (TaggedDraft tagged : drafts) {
			if (tagged == null || tagged.draft() == null || tagged.draft().shot() == null) continue;
			TimelineGenerationMetadata metadata = tagged.metadata() != null
				? tagged.metadata()
				: TimelineGenerationMetadata.fromOrigin(TimelineEventOrigin.GENERATED);
			if (writeDraft(timeline, tagged.draft(), metadata)) count++;
		}
		return count;
	}

	public static boolean writeDraft(
		Timeline timeline,
		CameraShotDraft draft,
		TimelineGenerationMetadata metadata
	) {
		if (timeline == null || draft == null || draft.shot() == null) return false;
		CameraShot shot = draft.shot();
		if (CameraShotValidator.hasErrors(CameraShotValidator.validate(shot))) {
			return false;
		}
		TimelineGenerationMetadata meta = metadata != null
			? metadata
			: TimelineGenerationMetadata.fromOrigin(TimelineEventOrigin.GENERATED);
		if (draft.pose() != null) {
			return writePoseAnchored(timeline, shot, draft.pose(), meta);
		}
		return writeFramingResolved(timeline, shot, meta);
	}

	private static boolean writeFramingResolved(
		Timeline timeline,
		CameraShot shot,
		TimelineGenerationMetadata metadata
	) {
		CameraFramingSolution framing = resolveFraming(shot).withAngle(shot.angle());
		Vec3d target = framing.lookAt();
		double start = shot.startSeconds();
		double duration = shot.durationSeconds();
		String ease = shot.easing().name();
		Map<String, Object> semantics = CameraSegmentSemantics.fromShot(shot);
		double facingYaw = framing.facingYawDeg();
		double azimuth = framing.yawDeg();

		return switch (shot.movement()) {
			case ORBIT -> {
				CameraTrackFactory.addOrbitSegment(
					timeline, start, duration,
					target.x, target.y, target.z,
					framing.orbitRadiusBlocks(), framing.orbitHeightBlocks(),
					azimuth, azimuth + 120.0,
					metadata,
					semantics
				);
				yield true;
			}
			case PUSH_IN -> {
				Vec3d eye = framing.eyePosition();
				CameraTrackFactory.addDollySegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, azimuth, framing.dollyReachBlocks(),
					metadata,
					semantics
				);
				yield true;
			}
			case PULL_OUT -> {
				Vec3d eye = framing.eyePosition(0.5, 1.0);
				CameraTrackFactory.addDollySegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, azimuth + 180.0, framing.dollyReachBlocks(),
					metadata,
					semantics
				);
				yield true;
			}
			case PAN -> {
				Vec3d eye = framing.eyePosition();
				CameraTrackFactory.addCraneSegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, facingYaw, framing.pitchDeg(), 2.5,
					metadata,
					semantics
				);
				yield true;
			}
			case SHAKE -> {
				Vec3d eye = framing.eyePosition(0.7, 1.0);
				CameraTrackFactory.addShakeSegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, facingYaw, framing.pitchDeg(),
					metadata,
					semantics
				);
				yield true;
			}
			case HOLD -> {
				Vec3d eye = framing.eyePosition();
				CameraTrackFactory.addPathSegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, facingYaw, framing.pitchDeg(), ease,
					metadata,
					semantics
				);
				yield true;
			}
		};
	}

	private static boolean writePoseAnchored(
		Timeline timeline,
		CameraShot shot,
		CapturedCameraPose pose,
		TimelineGenerationMetadata metadata
	) {
		double start = shot.startSeconds();
		double duration = shot.durationSeconds();
		String ease = shot.easing().name();
		Map<String, Object> semantics = CameraSegmentSemantics.fromShot(shot);

		return switch (shot.movement()) {
			case ORBIT -> {
				CapturedCameraPose.OrbitCapture orbit = pose.orbit();
				if (orbit == null) {
					yield false;
				}
				CameraTrackFactory.addOrbitSegment(
					timeline, start, duration,
					orbit.targetX(), orbit.targetY(), orbit.targetZ(),
					orbit.radius(), orbit.height(), orbit.yawStartDeg(), orbit.yawEndDeg(),
					metadata,
					semantics
				);
				yield true;
			}
			case PUSH_IN, PULL_OUT -> {
				CameraTrackFactory.addDollySegment(
					timeline, start, duration,
					pose.eyeX(), pose.eyeY(), pose.eyeZ(), pose.yawDeg(), POSE_DOLLY_REACH_BLOCKS,
					metadata,
					semantics
				);
				yield true;
			}
			case PAN -> {
				CameraTrackFactory.addCraneSegment(
					timeline, start, duration,
					pose.eyeX(), pose.eyeY(), pose.eyeZ(),
					pose.yawDeg(), pose.pitchDeg(), POSE_CRANE_DELTA_Y,
					metadata,
					semantics
				);
				yield true;
			}
			case SHAKE -> {
				CameraTrackFactory.addShakeSegment(
					timeline, start, duration,
					pose.eyeX(), pose.eyeY(), pose.eyeZ(), pose.yawDeg(), pose.pitchDeg(),
					metadata,
					semantics
				);
				yield true;
			}
			case HOLD -> {
				CameraTrackFactory.addPathSegment(
					timeline, start, duration,
					pose.eyeX(), pose.eyeY(), pose.eyeZ(), pose.yawDeg(), pose.pitchDeg(), ease,
					metadata,
					semantics
				);
				yield true;
			}
		};
	}

	private static CameraFramingSolution resolveFraming(CameraShot shot) {
		return CameraSubjectBoundsResolver.tryResolve(shot.effectiveLookAt())
			.map(bounds -> CameraFramingEngine.solve(shot.framing(), bounds))
			.orElseGet(() -> {
				Vec3d lookAt = CameraSubjectResolver.resolveRequired(
					shot.effectiveLookAt(), CameraSubjectRole.LOOK_AT);
				return CameraFramingEngine.fallback(shot.framing(), lookAt);
			});
	}
}
