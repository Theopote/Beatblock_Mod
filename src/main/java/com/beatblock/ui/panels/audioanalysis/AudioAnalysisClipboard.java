package com.beatblock.ui.panels.audioanalysis;

import net.minecraft.client.MinecraftClient;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** 剪贴板写入（Minecraft 键盘 API → AWT 回退）。 */
final class AudioAnalysisClipboard {

	private AudioAnalysisClipboard() {
	}

	static boolean copy(String text) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc != null && mc.keyboard != null) {
			try {
				mc.keyboard.setClipboard(text);
				return true;
			} catch (RuntimeException ignored) {
				// fallback
			}
		}
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			return true;
		} catch (IllegalStateException | UnsupportedOperationException | SecurityException e) {
			return false;
		}
	}
}
