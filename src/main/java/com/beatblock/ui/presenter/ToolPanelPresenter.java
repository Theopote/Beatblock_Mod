package com.beatblock.ui.presenter;

import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 工具面板业务逻辑：选择工具切换、StageObject 角点与创建、对象列表管理。
 */
public final class ToolPanelPresenter {

	public static final int MAX_STAGE_OBJECT_BLOCKS = 32768;

	public static final GroupSortingStrategy[] STAGE_GROUP_SORTING_VALUES = {
		GroupSortingStrategy.SEQUENTIAL,
		GroupSortingStrategy.RADIAL,
		GroupSortingStrategy.SPIRAL,
		GroupSortingStrategy.RANDOM,
		GroupSortingStrategy.ALL
	};

	public static final SelectionMode[] SELECTION_COMBO_ORDER = {
		SelectionMode.OFF,
		SelectionMode.CLICK,
		SelectionMode.BOX,
		SelectionMode.LINE,
		SelectionMode.BRUSH,
		SelectionMode.CONNECTED,
		SelectionMode.COLUMN,
		SelectionMode.PLANE_SLICE,
		SelectionMode.SELECTION_WAND,
		SelectionMode.LASSO
	};

	@FunctionalInterface
	public interface CrosshairBlockPicker {
		BlockPos pickBlock();
	}

	public record SelectionToolViewState(
		SelectionMode mode,
		int selectionCount,
		BlockPos boundingMin,
		BlockPos boundingMax
	) {}

	public record StageObjectCreateRequest(
		String name,
		boolean includeAir,
		GroupSortingStrategy sortingStrategy,
		double staggerSeconds
	) {}

	public record StageObjectListItem(String id, String name, int blockCount, String sourceType) {}

	public record CornerState(BlockPos posA, BlockPos posB) {}

	public record CornerUpdateOutcome(PresenterResult result, CornerState corners) {}

	public record CreateStageObjectOutcome(PresenterResult result, String objectId) {}

	private final Supplier<BeatBlockSelectionManager> selectionManager;
	private final Supplier<StageObjectSystem> stageObjectSystem;
	private final Supplier<World> world;
	private final CrosshairBlockPicker crosshairPicker;

	private BlockPos selectionPosA;
	private BlockPos selectionPosB;

	public ToolPanelPresenter(
		Supplier<BeatBlockSelectionManager> selectionManager,
		Supplier<StageObjectSystem> stageObjectSystem,
		Supplier<World> world,
		CrosshairBlockPicker crosshairPicker
	) {
		this.selectionManager = selectionManager;
		this.stageObjectSystem = stageObjectSystem;
		this.world = world;
		this.crosshairPicker = crosshairPicker;
	}

	public CornerState currentCorners() {
		return new CornerState(selectionPosA, selectionPosB);
	}

	public SelectionToolViewState selectionToolViewState() {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr == null) {
			return new SelectionToolViewState(SelectionMode.OFF, 0, null, null);
		}
		return new SelectionToolViewState(
			mgr.getMode(),
			mgr.getSelectionCount(),
			mgr.getBoundingMin(),
			mgr.getBoundingMax()
		);
	}

	public void setSelectionMode(SelectionMode mode) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null && mode != null) {
			mgr.setMode(mode);
		}
	}

	public CornerUpdateOutcome fillCornersFromSelection() {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr == null) {
			return new CornerUpdateOutcome(PresenterResult.failure("选择管理器不可用。"), currentCorners());
		}
		BlockPos smin = mgr.getBoundingMin();
		BlockPos smax = mgr.getBoundingMax();
		if (smin == null || smax == null || mgr.getSelectionCount() <= 0) {
			return new CornerUpdateOutcome(
				PresenterResult.failure("没有可用的方块选区或包围盒。"),
				currentCorners()
			);
		}
		selectionPosA = smin.toImmutable();
		selectionPosB = smax.toImmutable();
		return new CornerUpdateOutcome(
			PresenterResult.success("已从方块选区外接包围盒填入 A、B。"),
			currentCorners()
		);
	}

	public CornerUpdateOutcome setCornerFromCrosshair(boolean cornerA) {
		BlockPos picked = crosshairPicker != null ? crosshairPicker.pickBlock() : null;
		if (picked == null) {
			return new CornerUpdateOutcome(
				PresenterResult.failure("未命中方块。"),
				currentCorners()
			);
		}
		if (cornerA) {
			selectionPosA = picked.toImmutable();
		} else {
			selectionPosB = picked.toImmutable();
		}
		return new CornerUpdateOutcome(
			PresenterResult.success(cornerA ? "已设置 A。" : "已设置 B。"),
			currentCorners()
		);
	}

	public CornerUpdateOutcome clearCorners() {
		selectionPosA = null;
		selectionPosB = null;
		return new CornerUpdateOutcome(PresenterResult.success("已清空 A/B。"), currentCorners());
	}

	public CreateStageObjectOutcome createFromCuboid(StageObjectCreateRequest request) {
		StageObjectSystem system = stageObjectSystem.get();
		if (system == null) {
			return new CreateStageObjectOutcome(PresenterResult.failure("动画引擎未初始化，无法创建对象。"), null);
		}
		World currentWorld = world.get();
		if (currentWorld == null) {
			return new CreateStageObjectOutcome(PresenterResult.failure("当前无世界上下文，无法读取选区。"), null);
		}
		if (selectionPosA == null || selectionPosB == null) {
			return new CreateStageObjectOutcome(PresenterResult.failure("请先设置 A/B 两个选区点。"), null);
		}

		long volume = estimateSelectionVolume(selectionPosA, selectionPosB);
		if (volume > MAX_STAGE_OBJECT_BLOCKS) {
			return new CreateStageObjectOutcome(PresenterResult.failure(String.format(Locale.ROOT,
				"选区过大（%d blocks），上限为 %d。", volume, MAX_STAGE_OBJECT_BLOCKS)), null);
		}

		List<BlockPos> blocks = collectCuboidBlocks(currentWorld, selectionPosA, selectionPosB, request.includeAir());
		if (blocks.isEmpty()) {
			String message = request.includeAir()
				? "选区为空，未创建对象。"
				: "选区内没有非空气方块，未创建对象。";
			return new CreateStageObjectOutcome(PresenterResult.failure(message), null);
		}

		String name = normalizeName(request.name());
		String id = buildUniqueStageObjectId(system, name);
		StageObject obj = StageObjectSystem.fromBlocks(
			id,
			name,
			blocks,
			com.beatblock.engine.GroupSpec.fromSelectionCuboid(
				selectionPosA,
				selectionPosB,
				request.includeAir(),
				request.sortingStrategy(),
				request.staggerSeconds()
			)
		);
		system.register(obj);
		return new CreateStageObjectOutcome(
			PresenterResult.success(String.format(Locale.ROOT, "已创建 StageObject: %s (%d blocks)", id, blocks.size())),
			id
		);
	}

	public CreateStageObjectOutcome createFromSelectionSnapshot(StageObjectCreateRequest request) {
		StageObjectSystem system = stageObjectSystem.get();
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (system == null) {
			return new CreateStageObjectOutcome(PresenterResult.failure("动画引擎未初始化，无法创建对象。"), null);
		}
		if (mgr == null) {
			return new CreateStageObjectOutcome(PresenterResult.failure("选择管理器不可用。"), null);
		}

		List<BlockPos> blocks = new ArrayList<>(mgr.getSelectedBlocks());
		if (blocks.isEmpty()) {
			return new CreateStageObjectOutcome(PresenterResult.failure("当前没有方块选区。请先使用选择工具。"), null);
		}
		if (blocks.size() > MAX_STAGE_OBJECT_BLOCKS) {
			return new CreateStageObjectOutcome(PresenterResult.failure(String.format(Locale.ROOT,
				"选区过大（%d blocks），上限为 %d。", blocks.size(), MAX_STAGE_OBJECT_BLOCKS)), null);
		}

		String name = normalizeName(request.name());
		String id = buildUniqueStageObjectId(system, name);
		StageObject obj = StageObjectSystem.fromSelectionSnapshot(
			id,
			name,
			blocks,
			request.sortingStrategy(),
			request.staggerSeconds()
		);
		system.register(obj);
		return new CreateStageObjectOutcome(
			PresenterResult.success(String.format(Locale.ROOT, "已创建快照 StageObject: %s (%d blocks)", id, blocks.size())),
			id
		);
	}

	public PresenterResult removeStageObject(String id) {
		StageObjectSystem system = stageObjectSystem.get();
		if (system == null || id == null || id.isBlank()) {
			return PresenterResult.failure("无法删除对象。");
		}
		if (!system.remove(id)) {
			return PresenterResult.failure("对象不存在：" + id);
		}
		return PresenterResult.success("已删除 StageObject: " + id);
	}

	public List<StageObjectListItem> listStageObjects() {
		StageObjectSystem system = stageObjectSystem.get();
		if (system == null) {
			return List.of();
		}
		List<StageObjectListItem> items = new ArrayList<>();
		for (StageObject obj : system.getAll()) {
			items.add(new StageObjectListItem(
				obj.getId(),
				obj.getName(),
				obj.getBlocks().size(),
				obj.getGroupSpec().getSourceType()
			));
		}
		items.sort(Comparator.comparing(StageObjectListItem::name, String.CASE_INSENSITIVE_ORDER));
		return items;
	}

	public static String selectionModeLabel(SelectionMode mode) {
		return switch (mode) {
			case OFF -> "关闭";
			case CLICK -> "点击选择";
			case BOX -> "框选（两角 + 预览）";
			case LINE -> "线选（两端点 + 预览）";
			case BRUSH -> "笔刷（球/立方，单击或涂抹）";
			case CONNECTED -> "魔棒（同色六邻域）";
			case COLUMN -> "整列（同 XZ）";
			case PLANE_SLICE -> "平面切片";
			case SELECTION_WAND -> "选区魔棒（盒内）";
			case LASSO -> "套索（屏幕多边形）";
		};
	}

	public static String formatPos(BlockPos pos) {
		if (pos == null) {
			return "(未设置)";
		}
		return String.format(Locale.ROOT, "%d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
	}

	public static long estimateSelectionVolume(BlockPos a, BlockPos b) {
		if (a == null || b == null) {
			return 0;
		}
		long dx = Math.abs((long) a.getX() - b.getX()) + 1L;
		long dy = Math.abs((long) a.getY() - b.getY()) + 1L;
		long dz = Math.abs((long) a.getZ() - b.getZ()) + 1L;
		return dx * dy * dz;
	}

	public static double parseStaggerSeconds(String raw) {
		if (raw == null || raw.isBlank()) {
			return 0.0;
		}
		try {
			return Math.max(0.0, Double.parseDouble(raw.trim()));
		} catch (Exception ex) {
			return 0.0;
		}
	}

	public static GroupSortingStrategy sortingStrategyAtIndex(int index) {
		int idx = Math.max(0, Math.min(index, STAGE_GROUP_SORTING_VALUES.length - 1));
		return STAGE_GROUP_SORTING_VALUES[idx];
	}

	private List<BlockPos> collectCuboidBlocks(World currentWorld, BlockPos a, BlockPos b, boolean includeAir) {
		List<BlockPos> out = new ArrayList<>();
		int minX = Math.min(a.getX(), b.getX());
		int maxX = Math.max(a.getX(), b.getX());
		int minY = Math.min(a.getY(), b.getY());
		int maxY = Math.max(a.getY(), b.getY());
		int minZ = Math.min(a.getZ(), b.getZ());
		int maxZ = Math.max(a.getZ(), b.getZ());

		long volume = (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
		if (volume > MAX_STAGE_OBJECT_BLOCKS) {
			return out;
		}

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!includeAir && currentWorld.getBlockState(pos).isAir()) {
						continue;
					}
					out.add(pos);
				}
			}
		}
		return out;
	}

	private static String normalizeName(String name) {
		String trimmed = name != null ? name.trim() : "";
		return trimmed.isEmpty() ? "selection_object" : trimmed;
	}

	static String buildUniqueStageObjectId(StageObjectSystem system, String name) {
		String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
		if (base.isBlank()) {
			base = "selection_object";
		}
		if (base.startsWith("_")) {
			base = base.substring(1);
		}
		if (base.endsWith("_")) {
			base = base.substring(0, base.length() - 1);
		}
		if (base.isEmpty()) {
			base = "selection_object";
		}

		String candidate = base;
		int suffix = 2;
		while (system.get(candidate) != null) {
			candidate = base + "_" + suffix;
			suffix++;
		}
		return candidate;
	}
}
