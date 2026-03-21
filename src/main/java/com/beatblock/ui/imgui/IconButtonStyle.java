package com.beatblock.ui.imgui;

import com.beatblock.client.imgui.ImGuiFontManager;
import imgui.ImFont;
import imgui.ImGui;
import imgui.flag.ImGuiStyleVar;

/**
 * 图标 / 方形工具按钮样式。
 *
 * <p>使用 {@link ImGuiStyleVar#FramePadding} 为 0、{@link ImGuiStyleVar#ButtonTextAlign} 为 (0.5, 0.5)，
 * 让标签在按钮可绘区域内居中；BeatBlock 图标另用 {@link ImGuiFontManager#getIconButtonFont()} 大字重绘制，接近铺满按钮。
 */
public final class IconButtonStyle {

	private IconButtonStyle() {
		throw new AssertionError();
	}

	/**
	 * 仅方形槽：零内边距 + 标签居中。用于主字体字符（如 “+”），不要与 {@link #pushBeatBlockIconButton()} 混用同一对 push/pop。
	 */
	public static void pushSquareIconSlot() {
		ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f);
		ImGui.pushStyleVar(ImGuiStyleVar.ButtonTextAlign, 0.5f, 0.5f);
	}

	public static void popSquareIconSlot() {
		ImGui.popStyleVar(2);
	}

	/**
	 * 方形槽 + BeatBlock.ttf 专用字号（与轨道行高等对齐），仅用于 {@link com.beatblock.ui.icons.Icons} 私用区字符。
	 */
	public static void pushBeatBlockIconButton() {
		pushSquareIconSlot();
		ImFont f = ImGuiFontManager.getIconButtonFont();
		if (f != null) {
			ImGui.pushFont(f);
		}
	}

	public static void popBeatBlockIconButton() {
		if (ImGuiFontManager.getIconButtonFont() != null) {
			ImGui.popFont();
		}
		popSquareIconSlot();
	}
}
