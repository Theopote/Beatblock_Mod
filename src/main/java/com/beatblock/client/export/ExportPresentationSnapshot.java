package com.beatblock.client.export;

import com.beatblock.automap.vfx.EnvironmentLightingRuntime;
import com.beatblock.client.camera.CameraRuntime;
import com.beatblock.client.camera.TimelineCameraEvaluator;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 导出开始前捕获的客户端呈现态；{@link ExportPresentationSession#close()} 时统一恢复。
 */
public record ExportPresentationSnapshot(
	double restoreTimelineTimeSeconds,
	EnvironmentLightingRuntime.State environmentLighting,
	float rainGradient,
	float thunderGradient,
	Map<String, Float> stemGains,
	CameraRuntime.Owner cameraOwner,
	TimelineCameraEvaluator.CameraSample preExportCameraSample,
	@Nullable ExportVfxState vfxState
) {
	public ExportPresentationSnapshot {
		environmentLighting = environmentLighting != null
			? environmentLighting
			: EnvironmentLightingRuntime.State.NEUTRAL;
		stemGains = Collections.unmodifiableMap(new LinkedHashMap<>(stemGains != null ? stemGains : Map.of()));
		cameraOwner = cameraOwner != null ? cameraOwner : CameraRuntime.Owner.PLAYER;
	}
}
