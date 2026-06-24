package com.beatblock.selection.tools;

import com.beatblock.selection.SelectionMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;

/** 将 {@link SelectionMode} 映射到点击处理工具。 */
public final class SelectionToolRegistry {

	private static final Map<SelectionMode, SelectionClickHandler> HANDLERS = buildHandlers();

	private SelectionToolRegistry() {}

	private static Map<SelectionMode, SelectionClickHandler> buildHandlers() {
		Map<SelectionMode, SelectionClickHandler> map = new EnumMap<>(SelectionMode.class);
		map.put(SelectionMode.CLICK, ClickSelectionTool::handle);
		map.put(SelectionMode.BOX, BoxSelectionTool::handle);
		map.put(SelectionMode.LINE, LineSelectionTool::handle);
		map.put(SelectionMode.BRUSH, BrushSelectionTool::handle);
		map.put(SelectionMode.CONNECTED, ConnectedSelectionTool::handle);
		map.put(SelectionMode.COLUMN, ColumnSelectionTool::handle);
		map.put(SelectionMode.PLANE_SLICE, PlaneSliceSelectionTool::handle);
		map.put(SelectionMode.SELECTION_WAND, SelectionWandSelectionTool::handle);
		return Map.copyOf(map);
	}

	public static void dispatchClick(
		SelectionMode mode,
		SelectionToolHost host,
		World world,
		BlockPos pos,
		Direction face,
		boolean shiftDown
	) {
		if (mode == null || host == null || world == null || pos == null) {
			return;
		}
		SelectionClickHandler handler = HANDLERS.get(mode);
		if (handler != null) {
			handler.handle(host, world, pos, face, shiftDown);
		}
	}

	static Map<SelectionMode, SelectionClickHandler> handlersForTests() {
		return HANDLERS;
	}
}
