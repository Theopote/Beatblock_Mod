package com.beatblock.automap.camera;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.camera.CameraTrackFactory;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * 将 {@link CameraShot} 编译为 Timeline 摄像机轨道片段。
 */
public final class CameraShotTimelineWriter {

	private CameraShotTimelineWriter() {}

	public static int write(Timeline timeline, List<CameraShot> shots) {
		if (timeline == null || shots == null || shots.isEmpty()) return 0;
		int count = 0;
		for (CameraShot shot : shots) {
			if (writeOne(timeline, shot)) count++;
		}
		return count;
	}

	private static boolean writeOne(Timeline timeline, CameraShot shot) {
		Vec3d target = CameraSubjectResolver.resolve(shot.effectiveLookAt());
		double start = shot.startSeconds();
		double duration = shot.durationSeconds();
		String ease = shot.easing().name();

		return switch (shot.movement()) {
			case ORBIT -> {
				double radius = shot.framing().orbitRadiusBlocks();
				double height = shot.framing().orbitHeightBlocks();
				CameraTrackFactory.addOrbitSegment(
					timeline, start, duration,
					target.x, target.y, target.z,
					radius, height, 0.0, 120.0
				);
				yield true;
			}
			case PUSH_IN -> {
				Vec3d eye = offsetSouth(target, shot.framing().orbitRadiusBlocks(), shot.framing().orbitHeightBlocks());
				CameraTrackFactory.addDollySegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, 0.0, shot.framing().dollyReachBlocks()
				);
				yield true;
			}
			case PULL_OUT -> {
				Vec3d eye = offsetSouth(target, shot.framing().orbitRadiusBlocks() * 0.5, shot.framing().orbitHeightBlocks());
				CameraTrackFactory.addDollySegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, 180.0, shot.framing().dollyReachBlocks()
				);
				yield true;
			}
			case PAN -> {
				Vec3d eye = offsetSouth(target, shot.framing().orbitRadiusBlocks(), shot.framing().orbitHeightBlocks());
				CameraTrackFactory.addCraneSegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, 0.0, -12.0, 2.5
				);
				yield true;
			}
			case SHAKE -> {
				Vec3d eye = offsetSouth(target, shot.framing().orbitRadiusBlocks() * 0.7, shot.framing().orbitHeightBlocks());
				CameraTrackFactory.addShakeSegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, 0.0, -10.0
				);
				yield true;
			}
			case HOLD -> {
				Vec3d eye = offsetSouth(target, shot.framing().orbitRadiusBlocks(), shot.framing().orbitHeightBlocks());
				CameraTrackFactory.addPathSegment(
					timeline, start, duration,
					eye.x, eye.y, eye.z, 0.0, -8.0, ease
				);
				yield true;
			}
		};
	}

	private static Vec3d offsetSouth(Vec3d target, double radius, double height) {
		return new Vec3d(target.x, target.y + height, target.z + radius);
	}
}
