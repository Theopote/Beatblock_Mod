package com.beatblock.automap.engine;

import com.beatblock.automap.camera.CameraShot;

/**
 * 自动镜头事件：包装完整 {@link CameraShot}，并保留 legacy time + action 访问器。
 */
public final class CameraEvent {

	private final CameraShot shot;

	public CameraEvent(CameraShot shot) {
		this.shot = shot != null ? shot : new CameraShot(
			0, 3.0,
			com.beatblock.automap.camera.CameraSubject.allStageObjects(),
			com.beatblock.automap.camera.CameraShotFraming.MEDIUM,
			com.beatblock.automap.camera.CameraShotMovement.HOLD,
			com.beatblock.automap.camera.CameraSubject.allStageObjects(),
			com.beatblock.automap.camera.CameraShotTransition.CUT,
			com.beatblock.automap.camera.CameraShotEasing.SMOOTH,
			com.beatblock.automap.camera.CameraCollisionPolicy.AVOID_BLOCKS,
			com.beatblock.automap.camera.CameraShotBeatAlignment.none(),
			-1
		);
	}

	public CameraEvent(double timeSeconds, CameraAction action) {
		this(com.beatblock.automap.camera.CameraShotCodec.legacyShot(timeSeconds, action, -1));
	}

	public double getTimeSeconds() {
		return shot.startSeconds();
	}

	public CameraAction getAction() {
		return shot.legacyAction();
	}

	public CameraShot getShot() {
		return shot;
	}
}
