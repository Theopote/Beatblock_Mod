package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * BeatBlock 方块选区（精简复刻 ChronoBlocks 点击 + 框选工具）。
 * <p>
 * 优化：大框选一次性合并，避免在热路径上分配过多中间集合；超过上限则拒绝并提示。
 */
public final class BeatBlockSelectionManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatBlockSelectionManager.class);

	private static final BeatBlockSelectionManager INSTANCE = new BeatBlockSelectionManager();

	public static BeatBlockSelectionManager get() {
		return INSTANCE;
	}

	private SelectionMode mode = SelectionMode.OFF;
	private SelectionOperation operation = SelectionOperation.NEW;
	private final LinkedHashSet<BlockPos> selected = new LinkedHashSet<>();
	private BlockPos boxFirstCorner;
	private boolean includeAir;
	private int maxBlocks = 100_000;
	private String lastMessage = "";

	private BeatBlockSelectionManager() {}

	public SelectionMode getMode() {
		return mode;
	}

	public void setMode(SelectionMode mode) {
		this.mode = mode != null ? mode : SelectionMode.OFF;
		if (this.mode != SelectionMode.BOX) {
			boxFirstCorner = null;
		}
	}

	public SelectionOperation getOperation() {
		return operation;
	}

	public void setOperation(SelectionOperation operation) {
		this.operation = operation != null ? operation : SelectionOperation.NEW;
	}

	public boolean isIncludeAir() {
		return includeAir;
	}

	public void setIncludeAir(boolean includeAir) {
		this.includeAir = includeAir;
	}

	public int getMaxBlocks() {
		return maxBlocks;
	}

	public void setMaxBlocks(int maxBlocks) {
		this.maxBlocks = Math.max(1024, maxBlocks);
	}

	public Set<BlockPos> getSelectedBlocks() {
		return Collections.unmodifiableSet(selected);
	}

	public int getSelectionCount() {
		return selected.size();
	}

	public BlockPos getBoxFirstCorner() {
		return boxFirstCorner;
	}

	public String getLastMessage() {
		return lastMessage;
	}

	public void clearMessage() {
		lastMessage = "";
	}

	public void clearSelection() {
		selected.clear();
		boxFirstCorner = null;
		lastMessage = "已清空选区。";
	}

	public void cancelBoxCorner() {
		boxFirstCorner = null;
		lastMessage = "已取消框选第一点。";
	}

	/**
	 * 关闭 UI 或重置会话时调用。
	 */
	public void reset() {
		mode = SelectionMode.OFF;
		operation = SelectionOperation.NEW;
		selected.clear();
		boxFirstCorner = null;
		includeAir = false;
		lastMessage = "";
	}

	public void handleLeftClick(World world, BlockPos pos, boolean shiftDown) {
		if (mode == SelectionMode.OFF || world == null || pos == null) return;

		if (!includeAir && world.getBlockState(pos).isAir()) {
			lastMessage = "当前设置跳过空气方块。";
			return;
		}

		if (mode == SelectionMode.CLICK) {
			handleClickTool(world, pos, shiftDown);
			return;
		}

		if (mode == SelectionMode.BOX) {
			handleBoxTool(world, pos);
		}
	}

	private void handleClickTool(World world, BlockPos pos, boolean shiftDown) {
		SelectionOperation op = shiftDown ? SelectionOperation.ADD : operation;
		switch (op) {
			case NEW -> {
				selected.clear();
				selected.add(pos.toImmutable());
				lastMessage = "新建选区：1 个方块";
			}
			case ADD -> {
				selected.add(pos.toImmutable());
				lastMessage = "加选后共 " + selected.size() + " 个方块";
			}
			case SUBTRACT -> {
				selected.remove(pos);
				lastMessage = "减选后共 " + selected.size() + " 个方块";
			}
			case INTERSECT -> {
				if (selected.contains(pos)) {
					BlockPos p = pos.toImmutable();
					selected.clear();
					selected.add(p);
					lastMessage = "交集：保留 1 个方块";
				} else {
					selected.clear();
					lastMessage = "交集：该方块不在选区内，已清空";
				}
			}
		}
		LOGGER.debug("[BeatBlockSelection] click op={} size={}", op, selected.size());
	}

	private void handleBoxTool(World world, BlockPos pos) {
		BlockPos immutable = pos.toImmutable();
		if (boxFirstCorner == null) {
			boxFirstCorner = immutable;
			lastMessage = "框选：已设角点 A，再点角点 B";
			return;
		}

		BlockPos a = boxFirstCorner;
		BlockPos b = immutable;
		boxFirstCorner = null;

		List<BlockPos> boxBlocks = collectBox(world, a, b);
		if (boxBlocks == null) {
			return;
		}
		mergeBoxIntoSelection(boxBlocks);
	}

	private List<BlockPos> collectBox(World world, BlockPos a, BlockPos b) {
		int x0 = Math.min(a.getX(), b.getX());
		int x1 = Math.max(a.getX(), b.getX());
		int y0 = Math.min(a.getY(), b.getY());
		int y1 = Math.max(a.getY(), b.getY());
		int z0 = Math.min(a.getZ(), b.getZ());
		int z1 = Math.max(a.getZ(), b.getZ());
		long dx = (long) x1 - x0 + 1L;
		long dy = (long) y1 - y0 + 1L;
		long dz = (long) z1 - z0 + 1L;
		long vol = dx * dy * dz;
		if (vol > maxBlocks) {
			lastMessage = String.format("框选体积 %d 超过上限 %d，已取消。", vol, maxBlocks);
			return null;
		}
		List<BlockPos> out = new ArrayList<>((int) vol);
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!includeAir && world.getBlockState(p).isAir()) continue;
					out.add(p.toImmutable());
				}
			}
		}
		return out;
	}

	private void mergeBoxIntoSelection(List<BlockPos> boxBlocks) {
		switch (operation) {
			case NEW -> {
				selected.clear();
				selected.addAll(boxBlocks);
				lastMessage = "新建框选：" + boxBlocks.size() + " 个方块";
			}
			case ADD -> {
				selected.addAll(boxBlocks);
				lastMessage = "加选框后共 " + selected.size() + " 个方块";
			}
			case SUBTRACT -> {
				selected.removeAll(boxBlocks);
				lastMessage = "减选框后共 " + selected.size() + " 个方块";
			}
			case INTERSECT -> {
				Set<BlockPos> boxSet = Set.copyOf(boxBlocks);
				selected.retainAll(boxSet);
				lastMessage = "与框求交后共 " + selected.size() + " 个方块";
			}
		}
		LOGGER.debug("[BeatBlockSelection] box op={} size={}", operation, selected.size());
	}

	/**
	 * 用于属性面板或导出：整体包围盒的最小角（含选区中方块的最小坐标）。
	 */
	public BlockPos getBoundingMin() {
		if (selected.isEmpty()) return null;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		for (BlockPos p : selected) {
			minX = Math.min(minX, p.getX());
			minY = Math.min(minY, p.getY());
			minZ = Math.min(minZ, p.getZ());
		}
		return new BlockPos(minX, minY, minZ);
	}

	public BlockPos getBoundingMax() {
		if (selected.isEmpty()) return null;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : selected) {
			maxX = Math.max(maxX, p.getX());
			maxY = Math.max(maxY, p.getY());
			maxZ = Math.max(maxZ, p.getZ());
		}
		return new BlockPos(maxX, maxY, maxZ);
	}

	public List<BlockPos> copySelectionAsList() {
		return new ArrayList<>(selected);
	}

	public void removeBlocks(Collection<BlockPos> toRemove) {
		selected.removeAll(toRemove);
	}

	public void addBlocks(Collection<BlockPos> toAdd) {
		selected.addAll(toAdd);
	}
}
