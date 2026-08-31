package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * 管理演出对象（RuntimeStageObject）：注册、按 id 查找。可选与 Timeline/StageManager 解析联动。
 */
public final class StageObjectSystem {

	private final Map<String, RuntimeStageObject> objects = new LinkedHashMap<>();

	public void register(RuntimeStageObject stageObject) {
		if (stageObject != null) objects.put(stageObject.getId(), stageObject);
	}

	public RuntimeStageObject get(String id) {
		return objects.get(id);
	}

	public Collection<RuntimeStageObject> getAll() {
		return Collections.unmodifiableCollection(objects.values());
	}

	public boolean remove(String id) {
		return objects.remove(id) != null;
	}

	public int size() {
		return objects.size();
	}

	public void clear() {
		objects.clear();
	}

	/** 从方块列表快速创建一个临时 RuntimeStageObject（中心自动计算） */
	public static RuntimeStageObject fromBlocks(String id, String name, List<BlockPos> blocks) {
		return new RuntimeStageObject(id, name, blocks, null, GroupSpec.manualSnapshot());
	}

	public static RuntimeStageObject fromBlocks(String id, String name, List<BlockPos> blocks, GroupSpec groupSpec) {
		return new RuntimeStageObject(id, name, blocks, null, groupSpec);
	}

	public static RuntimeStageObject fromSelectionCuboid(String id, String name, List<BlockPos> blocks,
	                                              BlockPos posA, BlockPos posB, boolean includeAir) {
		return new RuntimeStageObject(id, name, blocks, null, GroupSpec.fromSelectionCuboid(posA, posB, includeAir));
	}

	public static RuntimeStageObject fromSelectionSnapshot(String id, String name, List<BlockPos> blocks,
	                                                GroupSortingStrategy sortingStrategy,
	                                                double staggerDelaySeconds) {
		return new RuntimeStageObject(
			id,
			name,
			blocks,
			null,
			GroupSpec.fromSelectionSnapshot(blocks, sortingStrategy, staggerDelaySeconds)
		);
	}
}
