package com.beatblock.ui.imgui;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.ChannelSpec;
import imgui.ImGui;

import java.util.Locale;

/**
 * 方块影响预设的 ImGui 通道预览（事件属性表单与动画库共用）。
 */
public final class PresetChannelPreview {

	private PresetChannelPreview() {}

	/**
	 * 可折叠树节点：展示预设摘要行与各启用通道。
	 *
	 * @param treeLabelId ImGui 树节点标签（须含 ## 唯一后缀）
	 * @param preset      预设；为 null 或无通道时不渲染
	 */
	public static void renderCollapsible(String treeLabelId, BlockInfluencePreset preset) {
		if (preset == null || preset.getChannels().isEmpty()) {
			return;
		}
		if (!ImGui.treeNode(treeLabelId)) {
			return;
		}
		try {
			renderSummaryLine(preset);
			renderChannelBullets(preset);
		} finally {
			ImGui.treePop();
		}
	}

	/**
	 * 可折叠树节点：仅展示通道列表（树标签由调用方携带预设信息时使用）。
	 */
	public static void renderCollapsibleChannelsOnly(String treeLabelId, BlockInfluencePreset preset) {
		if (preset == null || preset.getChannels().isEmpty()) {
			return;
		}
		if (!ImGui.treeNode(treeLabelId)) {
			return;
		}
		try {
			renderChannelBullets(preset);
		} finally {
			ImGui.treePop();
		}
	}

	public static void renderSummaryLine(BlockInfluencePreset preset) {
		if (preset == null) {
			return;
		}
		ImGui.textDisabled(preset.getDisplayName() + " · " + preset.getDefaultDurationSeconds() + "s");
	}

	public static void renderChannelBullets(BlockInfluencePreset preset) {
		if (preset == null) {
			return;
		}
		for (ChannelSpec channel : preset.getChannels()) {
			if (channel == null || !channel.enabled()) {
				continue;
			}
			ImGui.bulletText(String.format(Locale.ROOT, "%s / %s / %s (%.2f→%.2f)",
				channel.dimension(), channel.path(), channel.curve(), channel.from(), channel.to()));
		}
	}
}
