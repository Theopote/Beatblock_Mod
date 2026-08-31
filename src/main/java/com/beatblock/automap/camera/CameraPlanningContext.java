package com.beatblock.automap.camera;

import com.beatblock.automap.engine.AutoMapStyle;

import java.util.List;

/**
 * 自动镜头导演输入：BPM、时长、风格与可用舞台对象。
 */
public record CameraPlanningContext(
	float bpm,
	double durationSeconds,
	AutoMapStyle style,
	List<String> stageObjectIds
) {

	public CameraPlanningContext {
		style = style != null ? style : AutoMapStyle.EDM;
		stageObjectIds = stageObjectIds != null ? List.copyOf(stageObjectIds) : List.of();
	}

	public double beatDurationSeconds() {
		return 60.0 / Math.max(1f, bpm);
	}

	public String primaryStageObjectId() {
		for (String id : stageObjectIds) {
			if (id != null && !id.isBlank()) return id;
		}
		return "";
	}

	public CameraSubject overviewSubject() {
		return CameraSubject.allStageObjects();
	}

	public CameraSubject subjectForSection(int sectionIndex, boolean overview) {
		if (overview) return overviewSubject();
		if (!stageObjectIds.isEmpty()) {
			String id = stageObjectIds.get(Math.floorMod(sectionIndex, stageObjectIds.size()));
			if (id != null && !id.isBlank()) return CameraSubject.stageObject(id);
		}
		String fallback = primaryStageObjectId();
		return fallback.isBlank() ? overviewSubject() : CameraSubject.stageObject(fallback);
	}
}
