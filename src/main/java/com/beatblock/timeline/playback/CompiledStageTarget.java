package com.beatblock.timeline.playback;

import com.beatblock.engine.GroupSortingStrategy;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * 播放阶段的舞台对象视图：只携带播放器真正需要的数据，不持有领域层 {@link com.beatblock.engine.RuntimeStageObject}
 * 或可能包含自定义可变 sourceParams 的 {@link com.beatblock.engine.GroupSpec}。
 * <p>
 * 这样 {@link CompiledStageEvent} 与编辑/运行时模型之间的边界保持清晰：
 * RuntimeStageObject = 编辑/运行模型，CompiledStageTarget = 播放快照。
 */
public record CompiledStageTarget(
	String id,
	String name,
	List<BlockPos> blocks,
	Vec3d center,
	GroupSortingStrategy sorting,
	double staggerDelaySeconds
) {

	public CompiledStageTarget {
		if (id == null) id = "";
		if (name == null) name = id;
		if (blocks == null) blocks = List.of();
		blocks = List.copyOf(blocks);
		if (center == null) center = Vec3d.ZERO;
		if (sorting == null) sorting = GroupSortingStrategy.SEQUENTIAL;
		staggerDelaySeconds = Math.max(0.0, staggerDelaySeconds);
	}
}
