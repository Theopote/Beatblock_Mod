package com.beatblock.timeline.rendering;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;

import com.beatblock.ui.icons.Icons;

/**
 * 绘制轨道列表左侧表头：折叠（组轨道）→ 类型 → 名称（可双击改名、预留宽度）→ 右对齐可见/锁定图标按钮 → 与内容区分界由 {@link TimelineRenderer} 统一竖线。
 */
public final class TrackRenderer {

	private static final float CHILD_INDENT_PX = 14f;
	private static final float PAD = 4f;
	private static final float FOLD_BTN = 18f;
	/** 「音频/动画/摄像机/事件」等类型列宽 */
	private static final float TYPE_COL_W = 50f;
	private static final float ICON_BTN = 20f;
	private static final float ICON_GAP = 2f;

	/**
	 * @param trackHeaderWidth 左侧轨道头总宽（与可拖动分割线一致）
	 */
	public float drawTrackLabel(float rowY, int rowIndex, String displayName, boolean isGroup, TimelineTrackListState listState, float trackHeaderWidth) {
		ImGui.setCursorPosY(rowY);
		float headW = trackHeaderWidth > 0 ? trackHeaderWidth : TimelineLayout.TRACK_LABEL_WIDTH;

		float cursorX = PAD;

		// —— 折叠（仅音频/动画组）——
		if (isGroup && listState != null) {
			boolean collapsed = listState.isGroupCollapsed(rowIndex);
			ImGui.setCursorPosX(cursorX);
			ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2f, 2f);
			if (ImGui.button((collapsed ? Icons.Timeline.TRACK_COLLAPSE : Icons.Timeline.TRACK_EXPAND) + "##fold" + rowIndex, FOLD_BTN, FOLD_BTN)) {
				listState.toggleGroupCollapsed(rowIndex);
			}
			ImGui.popStyleVar();
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(collapsed ? "Expand sub-tracks" : "Collapse sub-tracks");
			}
			cursorX += FOLD_BTN + 4f;
		} else if (TimelineTrackMeta.hasParent(rowIndex)) {
			cursorX += CHILD_INDENT_PX;
		}

		// —— 类型 ——
		ImGui.setCursorPos(cursorX, rowY);
		ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.52f, 0.62f, 1f);
		ImGui.text(TimelineTrackMeta.getCategoryTypeLabel(rowIndex));
		ImGui.popStyleColor();
		float nameX = cursorX + TYPE_COL_W;

		float rightBlock = (listState != null) ? (ICON_BTN * 2 + ICON_GAP + PAD) : PAD;
		float nameW = Math.max(48f, headW - nameX - rightBlock);

		boolean isEditing = listState != null && listState.getEditingRowIndex() == rowIndex;

		// —— 轨道名称 ——
		ImGui.setCursorPos(nameX, rowY);
		if (isEditing && listState != null) {
			ImGui.setNextItemWidth(nameW);
			if (ImGui.inputText("##name" + rowIndex, listState.getRenameBuffer(), ImGuiInputTextFlags.EnterReturnsTrue)) {
				listState.finishEditing(true);
			}
			if (ImGui.isItemDeactivatedAfterEdit()) {
				listState.finishEditing(true);
			}
		} else {
			ImGui.invisibleButton("##nameHit" + rowIndex, nameW, TimelineLayout.ROW_HEIGHT);
			boolean nameHovered = ImGui.isItemHovered();
			ImGui.setCursorPos(nameX, rowY);
			float clipX1 = ImGui.getCursorScreenPosX();
			float clipY1 = ImGui.getCursorScreenPosY();
			ImGui.pushClipRect(clipX1, clipY1, clipX1 + nameW, clipY1 + TimelineLayout.ROW_HEIGHT, true);
			if (isGroup) {
				ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.85f, 0.7f, 1f);
			}
			ImGui.text(displayName);
			if (isGroup) {
				ImGui.popStyleColor();
			}
			ImGui.popClipRect();
			if (listState != null && nameHovered && ImGui.isMouseDoubleClicked(0)) {
				listState.startEditing(rowIndex);
			}
			if (listState != null && nameHovered) {
				ImGui.setTooltip("双击可修改轨道名称");
			}
		}

		// —— 可见 / 锁定：右对齐，纯图标按钮 ——
		if (listState != null && !isEditing) {
			float lockRight = headW - PAD;
			float lockX = lockRight - ICON_BTN;
			float visX = lockX - ICON_GAP - ICON_BTN;

			ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2f, 2f);

			ImGui.setCursorPos(visX, rowY);
			boolean vis = listState.isVisible(rowIndex);
			if (ImGui.button((vis ? Icons.EYE : Icons.Action.HIDDEN) + "##vis" + rowIndex, ICON_BTN, ICON_BTN)) {
				listState.toggleVisible(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(vis ? "可见 (点击隐藏)" : "隐藏 (点击显示)");
			}

			ImGui.setCursorPos(lockX, rowY);
			boolean lock = listState.isLocked(rowIndex);
			if (ImGui.button((lock ? Icons.Action.LOCK : Icons.Action.UNLOCK) + "##lock" + rowIndex, ICON_BTN, ICON_BTN)) {
				listState.toggleLocked(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(lock ? "已锁定 (点击解锁)" : "未锁定 (点击锁定)");
			}

			ImGui.popStyleVar();
		}

		return isGroup ? rowY + 22f : rowY;
	}
}
