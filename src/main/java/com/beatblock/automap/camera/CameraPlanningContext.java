package com.beatblock.automap.camera;

import com.beatblock.automap.cast.StageCast;
import com.beatblock.automap.choreography.BuildSequencePlan;
import com.beatblock.automap.engine.AutoMapStyle;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 自动镜头导演输入：BPM、时长、风格、舞台卡司与可选建造序列。
 */
public record CameraPlanningContext(
	float bpm,
	double durationSeconds,
	AutoMapStyle style,
	List<String> stageObjectIds,
	StageCast stageCast,
	List<BuildSequencePlan> buildSequences
) {

	public CameraPlanningContext(
		float bpm,
		double durationSeconds,
		AutoMapStyle style,
		List<String> stageObjectIds
	) {
		this(bpm, durationSeconds, style, stageObjectIds, StageCast.fromTargetIds(stageObjectIds), List.of());
	}

	public CameraPlanningContext {
		style = style != null ? style : AutoMapStyle.EDM;
		stageObjectIds = stageObjectIds != null ? List.copyOf(stageObjectIds) : List.of();
		stageCast = stageCast != null
			? stageCast
			: StageCast.fromTargetIds(stageObjectIds);
		buildSequences = buildSequences != null ? List.copyOf(buildSequences) : List.of();
	}

	public double beatDurationSeconds() {
		return 60.0 / Math.max(1f, bpm);
	}

	public String primaryStageObjectId() {
		String hero = stageCast.primaryHeroId();
		if (hero != null && !hero.isBlank()) {
			return hero;
		}
		for (String id : stageObjectIds) {
			if (id != null && !id.isBlank()) return id;
		}
		return "";
	}

	public CameraSubject overviewSubject() {
		return CameraSubject.allStageObjects();
	}

	/**
	 * @deprecated 使用 {@link CameraSubjectPlanner}；保留供旧测试/回退。
	 */
	@Deprecated
	public CameraSubject subjectForSection(int sectionIndex, boolean overview) {
		if (overview) return overviewSubject();
		if (!stageObjectIds.isEmpty()) {
			String id = stageObjectIds.get(Math.floorMod(sectionIndex, stageObjectIds.size()));
			if (id != null && !id.isBlank()) return CameraSubject.stageObject(id);
		}
		String fallback = primaryStageObjectId();
		return fallback.isBlank() ? overviewSubject() : CameraSubject.stageObject(fallback);
	}

	public CameraPlanningContext withBuildSequences(@Nullable List<BuildSequencePlan> sequences) {
		return new CameraPlanningContext(
			bpm, durationSeconds, style, stageObjectIds, stageCast,
			sequences != null ? sequences : List.of()
		);
	}

	public CameraPlanningContext withStageCast(@Nullable StageCast cast) {
		return new CameraPlanningContext(
			bpm, durationSeconds, style, stageObjectIds,
			cast != null ? cast : stageCast,
			buildSequences
		);
	}
}
