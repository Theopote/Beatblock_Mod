package com.beatblock.timeline.playback;

import com.beatblock.client.camera.TimelineCameraEvaluator;
import com.beatblock.client.export.ExportVfxState;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * 某一时刻舞台的统一逻辑态：Scrub / Formal Seek / Loop / Export 共用同一份语义。
 * <p>
 * 应用路径可以不同（视觉预览 vs 权威世界写入），但「time T 应该是什么」只能有一份。
 */
public record ResolvedStageState(
	double timeSeconds,
	Map<String, String> stageLabels,
	Map<String, BuildProgressDigest> buildProgress,
	Map<String, String> globalStates,
	ExportVfxState vfxState,
	List<BlockPos> requiredBuildBlockPositions,
	TimelineCameraEvaluator.CameraSample camera
) {
	public ResolvedStageState {
		stageLabels = Map.copyOf(stageLabels != null ? stageLabels : Map.of());
		buildProgress = Map.copyOf(buildProgress != null ? buildProgress : Map.of());
		globalStates = Map.copyOf(globalStates != null ? globalStates : Map.of());
		vfxState = vfxState != null ? vfxState : new ExportVfxState(null, null);
		requiredBuildBlockPositions = List.copyOf(
			requiredBuildBlockPositions != null ? requiredBuildBlockPositions : List.of());
	}

	/** 与历史 {@link PlaybackStateDigest} 对齐的视图。 */
	public PlaybackStateDigest toPlaybackDigest() {
		return new PlaybackStateDigest(stageLabels, globalStates, buildProgress);
	}

	/** 导出前需要确保加载的 chunk 覆盖这些方块。 */
	public List<BlockPos> requiredChunkSamplePositions() {
		return requiredBuildBlockPositions;
	}
}
