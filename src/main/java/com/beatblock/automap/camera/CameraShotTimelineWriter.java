package com.beatblock.automap.camera;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/**
 * 将 {@link CameraShot} 编译为 Timeline 摄像机轨道片段。
 */
public final class CameraShotTimelineWriter {

	private CameraShotTimelineWriter() {}

	public record TaggedShot(CameraShot shot, TimelineGenerationMetadata metadata) {}

	public static int write(Timeline timeline, List<CameraShot> shots) {
		if (timeline == null || shots == null || shots.isEmpty()) return 0;
		int count = 0;
		for (CameraShot shot : shots) {
			if (writeOne(timeline, shot, TimelineGenerationMetadata.fromOrigin(TimelineEventOrigin.GENERATED))) {
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
			if (writeOne(timeline, tagged.shot(), metadata)) count++;
		}
		return count;
	}

	private static boolean writeOne(Timeline timeline, CameraShot shot, TimelineGenerationMetadata metadata) {
		if (CameraShotValidator.hasErrors(CameraShotValidator.validate(shot))) {
			return false;
		}
		CameraFramingSolution framing = resolveFraming(shot);
		Vec3d target = framing.lookAt();
		double start = shot.startSeconds();
		double duration = shot.durationSeconds();
		String ease = shot.easing().name();
		Map<String, Object> semantics = CameraSegmentSemantics.fromShot(shot);

		return switch (shot.movement()) {
			case ORBIT -> {
				CameraTrackFactory.addOrbitSegment(
					timeline, start, duration,
					target.x, target.y, target.z,
					framing.orbitRadiusBlocks(), framing.orbitHeightBlocks(), 0.0, 120.0,
					metadata,
					semantics
				);
				yield true;
			}
			case PUSH_IN -> {
				Vec3d eye = framing.eyePositionSouth();
				CameraTrackFactory.addDollySegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, 0.0, framing.dollyReachBlocks(),
					metadata,
					semantics
				);
				yield true;
			}
			case PULL_OUT -> {
				Vec3d eye = framing.eyePositionSouth(0.5, 1.0);
				CameraTrackFactory.addDollySegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, 180.0, framing.dollyReachBlocks(),
					metadata,
					semantics
				);
				yield true;
			}
			case PAN -> {
				Vec3d eye = framing.eyePositionSouth();
				CameraTrackFactory.addCraneSegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, 0.0, framing.pitchDeg(), 2.5,
					metadata,
					semantics
				);
				yield true;
			}
			case SHAKE -> {
				Vec3d eye = framing.eyePositionSouth(0.7, 1.0);
				CameraTrackFactory.addShakeSegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, 0.0, framing.pitchDeg(),
					metadata,
					semantics
				);
				yield true;
			}
			case HOLD -> {
				Vec3d eye = framing.eyePositionSouth();
				CameraTrackFactory.addPathSegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, 0.0, framing.pitchDeg(), ease,
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
