package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 选区集合运算（纯逻辑，便于单测）。
 */
public final class SelectionMerge {

	private SelectionMerge() {}

	public static LinkedHashSet<BlockPos> apply(
		LinkedHashSet<BlockPos> current,
		List<BlockPos> incoming,
		SelectionOperation operation
	) {
		LinkedHashSet<BlockPos> selected = current != null ? new LinkedHashSet<>(current) : new LinkedHashSet<>();
		List<BlockPos> blocks = incoming != null ? incoming : List.of();
		SelectionOperation op = operation != null ? operation : SelectionOperation.NEW;
		switch (op) {
			case NEW -> {
				selected.clear();
				selected.addAll(blocks);
			}
			case ADD -> selected.addAll(blocks);
			case SUBTRACT -> blocks.forEach(selected::remove);
			case INTERSECT -> selected.retainAll(Set.copyOf(blocks));
		}
		return selected;
	}
}
