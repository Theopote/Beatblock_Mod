package com.beatblock.ui.presenter;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.BrushShape;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import net.minecraft.util.math.Direction;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 方块选择属性面板业务逻辑：读取视图状态、应用设置变更。
 */
public final class SelectionPropertiesPresenter {

	private final Supplier<BeatBlockSelectionManager> selectionManager;

	public SelectionPropertiesPresenter(Supplier<BeatBlockSelectionManager> selectionManager) {
		this.selectionManager = selectionManager;
	}

	public SelectionPropertiesViewState currentViewState() {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr == null) {
			return new SelectionPropertiesViewState(
				SelectionMode.OFF,
				SelectionOperation.NEW,
				128,
				false,
				false,
				100_000,
				0,
				BrushShape.SPHERE,
				3,
				null,
				64,
				false,
				0,
				null,
				null,
				null,
				null,
				""
			);
		}
		return new SelectionPropertiesViewState(
			mgr.getMode(),
			mgr.getOperation(),
			mgr.getMaxDistanceFromCamera(),
			mgr.isSelectionFillEnabled(),
			mgr.isIncludeAir(),
			mgr.getMaxBlocks(),
			mgr.getLineThicknessRadius(),
			mgr.getBrushShape(),
			mgr.getSphereBrushRadius(),
			mgr.getPlaneSliceFaceOverride(),
			mgr.getMaxMagicWandSpreadFromSeed(),
			mgr.isConnectedMatchFullState(),
			mgr.getSelectionCount(),
			mgr.getBoundingMin(),
			mgr.getBoundingMax(),
			mgr.getBoxFirstCorner(),
			mgr.getLineFirstCorner(),
			mgr.getLastMessage()
		);
	}

	public void setOperation(SelectionOperation operation) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null && operation != null) {
			mgr.setOperation(operation);
		}
	}

	public void setMaxDistanceFromCamera(int value) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.setMaxDistanceFromCamera(value);
		}
	}

	public void setSelectionFillEnabled(boolean enabled) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.setSelectionFillEnabled(enabled);
		}
	}

	public void setIncludeAir(boolean includeAir) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.setIncludeAir(includeAir);
		}
	}

	public void setMaxBlocks(int maxBlocks) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.setMaxBlocks(maxBlocks);
		}
	}

	public void setLineThicknessRadius(int radius) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.setLineThicknessRadius(radius);
		}
	}

	public void setBrushShape(BrushShape shape) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null && shape != null) {
			mgr.setBrushShape(shape);
		}
	}

	public void setSphereBrushRadius(int radius) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.setSphereBrushRadius(radius);
		}
	}

	public void setPlaneSliceFaceOverride(Direction direction) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.setPlaneSliceFaceOverride(direction);
		}
	}

	public void setMaxMagicWandSpreadFromSeed(int spread) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.setMaxMagicWandSpreadFromSeed(spread);
		}
	}

	public void setConnectedMatchFullState(boolean matchFullState) {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.setConnectedMatchFullState(matchFullState);
		}
	}

	public void clearSelection() {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.clearSelection();
		}
	}

	public void clearMessage() {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.clearMessage();
		}
	}

	public void cancelBoxCorner() {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.cancelBoxCorner();
		}
	}

	public void cancelLineCorner() {
		BeatBlockSelectionManager mgr = selectionManager.get();
		if (mgr != null) {
			mgr.cancelLineCorner();
		}
	}

	public static String operationLabel(SelectionOperation op) {
		return switch (op) {
			case NEW -> BBTexts.get("beatblock.selection_op.new");
			case ADD -> BBTexts.get("beatblock.selection_op.add");
			case SUBTRACT -> BBTexts.get("beatblock.selection_op.subtract");
			case INTERSECT -> BBTexts.get("beatblock.selection_op.intersect");
		};
	}

	public static String modeTitle(SelectionMode mode) {
		return switch (mode) {
			case OFF -> BBTexts.get("beatblock.selection_mode.off");
			case CLICK -> BBTexts.get("beatblock.selection_mode.click");
			case BOX -> BBTexts.get("beatblock.selection_mode.box");
			case LINE -> BBTexts.get("beatblock.selection_mode.line");
			case BRUSH -> BBTexts.get("beatblock.selection_mode.brush");
			case CONNECTED -> BBTexts.get("beatblock.selection_mode.connected");
			case COLUMN -> BBTexts.get("beatblock.selection_mode.column");
			case PLANE_SLICE -> BBTexts.get("beatblock.selection_mode.plane_slice");
			case SELECTION_WAND -> BBTexts.get("beatblock.selection_mode.selection_wand");
			case LASSO -> BBTexts.get("beatblock.selection_mode.lasso");
		};
	}

	public static int planeFaceIndex(Direction override, Direction[] planeFaceDirs) {
		for (int i = 0; i < planeFaceDirs.length; i++) {
			if (Objects.equals(planeFaceDirs[i], override)) {
				return i;
			}
		}
		return 0;
	}
}
