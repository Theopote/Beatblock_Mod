package com.beatblock.automap.camera;

import com.beatblock.automap.engine.CameraAction;

/**
 * 镜头运动类型（比 legacy {@link CameraAction} 更贴近 Timeline 片段语义）。
 */
public enum CameraShotMovement {
	ORBIT,
	PUSH_IN,
	PULL_OUT,
	PAN,
	SHAKE,
	HOLD;

	public CameraAction toLegacyAction() {
		return switch (this) {
			case ORBIT -> CameraAction.ORBIT;
			case PUSH_IN -> CameraAction.ZOOM_IN;
			case PULL_OUT -> CameraAction.ZOOM_OUT;
			case PAN -> CameraAction.PAN;
			case SHAKE -> CameraAction.SHAKE;
			case HOLD -> CameraAction.HOLD;
		};
	}

	public static CameraShotMovement fromLegacyAction(CameraAction action) {
		if (action == null) return HOLD;
		return switch (action) {
			case ORBIT -> ORBIT;
			case ZOOM_IN -> PUSH_IN;
			case ZOOM_OUT -> PULL_OUT;
			case PAN -> PAN;
			case SHAKE -> SHAKE;
			case HOLD -> HOLD;
		};
	}
}
