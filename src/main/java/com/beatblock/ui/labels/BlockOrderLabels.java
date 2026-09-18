package com.beatblock.ui.labels;

import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.GroupSpec;
import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

/**
 * 用户可见的方块动画顺序 / GroupSpec 来源文案（底层仍使用 {@link GroupSpec} / {@link GroupSortingStrategy}）。
 */
public final class BlockOrderLabels {

	private BlockOrderLabels() {}

	public static String sortingStrategyLabel(@Nullable GroupSortingStrategy strategy) {
		GroupSortingStrategy effective = strategy != null ? strategy : GroupSortingStrategy.SEQUENTIAL;
		return BBTexts.get(switch (effective) {
			case SEQUENTIAL -> "beatblock.block_order.sequential";
			case RADIAL -> "beatblock.block_order.radial";
			case SPIRAL -> "beatblock.block_order.spiral";
			case RANDOM -> "beatblock.block_order.random";
			case ALL -> "beatblock.block_order.all";
		});
	}

	public static String[] sortingComboLabels() {
		return BBTexts.labels(
			"beatblock.block_order.sequential",
			"beatblock.block_order.radial",
			"beatblock.block_order.spiral",
			"beatblock.block_order.random",
			"beatblock.block_order.all"
		);
	}

	public static String sourceTypeLabel(@Nullable String sourceType) {
		if (sourceType == null || sourceType.isBlank()) {
			return BBTexts.get("beatblock.block_order.source.manual");
		}
		return BBTexts.get(switch (sourceType) {
			case "selection_snapshot" -> "beatblock.block_order.source.selection_snapshot";
			case "selection_cuboid" -> "beatblock.block_order.source.selection_cuboid";
			case "manual_snapshot" -> "beatblock.block_order.source.manual";
			default -> "beatblock.block_order.source.unknown";
		}, sourceType);
	}

	public static String sourceTypeLabel(@Nullable GroupSpec spec) {
		return sourceTypeLabel(spec != null ? spec.getSourceType() : null);
	}
}
