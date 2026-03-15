package com.beatblock.timeline.rendering;

import java.util.HashMap;
import java.util.Map;
import imgui.type.ImString;

/**
 * 轨道列表左侧状态：每行的「可见」「锁定」、可自定义轨道名称，与 TimelineLayout.CONTENT_ROW_COUNT 对应。
 */
public final class TimelineTrackListState {

	private static final int RENAME_BUFFER_SIZE = 64;

	private final boolean[] visible = new boolean[TimelineLayout.CONTENT_ROW_COUNT];
	private final boolean[] locked = new boolean[TimelineLayout.CONTENT_ROW_COUNT];
	private final Map<Integer, String> customNames = new HashMap<>();
	/** 正在编辑名称的行，-1 表示未在编辑 */
	private int editingRowIndex = -1;
	private final ImString renameBuffer = new ImString(RENAME_BUFFER_SIZE);

	public TimelineTrackListState() {
		for (int i = 0; i < visible.length; i++) {
			visible[i] = true;
		}
	}

	public boolean isVisible(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= visible.length) return true;
		return visible[rowIndex];
	}

	public void setVisible(int rowIndex, boolean v) {
		if (rowIndex >= 0 && rowIndex < visible.length) visible[rowIndex] = v;
	}

	public void toggleVisible(int rowIndex) {
		if (rowIndex >= 0 && rowIndex < visible.length) visible[rowIndex] = !visible[rowIndex];
	}

	public boolean isLocked(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= locked.length) return false;
		return locked[rowIndex];
	}

	public void setLocked(int rowIndex, boolean v) {
		if (rowIndex >= 0 && rowIndex < locked.length) locked[rowIndex] = v;
	}

	public void toggleLocked(int rowIndex) {
		if (rowIndex >= 0 && rowIndex < locked.length) locked[rowIndex] = !locked[rowIndex];
	}

	/** 当前显示名称：自定义名优先，否则用 TimelineTrackMeta 的默认名 */
	public String getDisplayName(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= TimelineLayout.CONTENT_ROW_COUNT) return "";
		String custom = customNames.get(rowIndex);
		return custom != null && !custom.isBlank() ? custom.trim() : TimelineTrackMeta.getDefaultName(rowIndex);
	}

	public void setCustomName(int rowIndex, String name) {
		if (rowIndex < 0 || rowIndex >= TimelineLayout.CONTENT_ROW_COUNT) return;
		if (name == null || name.isBlank()) {
			customNames.remove(rowIndex);
		} else {
			customNames.put(rowIndex, name.trim());
		}
	}

	public void clearCustomName(int rowIndex) {
		if (rowIndex >= 0 && rowIndex < TimelineLayout.CONTENT_ROW_COUNT) customNames.remove(rowIndex);
	}

	public int getEditingRowIndex() { return editingRowIndex; }

	public void startEditing(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= TimelineLayout.CONTENT_ROW_COUNT) return;
		editingRowIndex = rowIndex;
		renameBuffer.set(getDisplayName(rowIndex));
	}

	public void finishEditing(boolean save) {
		if (save && editingRowIndex >= 0 && editingRowIndex < TimelineLayout.CONTENT_ROW_COUNT) {
			String s = renameBuffer.get();
			setCustomName(editingRowIndex, s == null ? "" : s);
		}
		editingRowIndex = -1;
	}

	public ImString getRenameBuffer() { return renameBuffer; }
}
