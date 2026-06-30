package com.beatblock.ui.imgui;

import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/** ImGui IO 修饰键 + GLFW 回退（Minecraft 下 IO 有时不同步）。 */
public final class ImGuiModifierKeys {

	private ImGuiModifierKeys() {
	}

	public static boolean ctrl() {
		if (ImGui.getIO().getKeyCtrl()) {
			return true;
		}
		return glfwDown(GLFW.GLFW_KEY_LEFT_CONTROL) || glfwDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
	}

	public static boolean shift() {
		if (ImGui.getIO().getKeyShift()) {
			return true;
		}
		return glfwDown(GLFW.GLFW_KEY_LEFT_SHIFT) || glfwDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	private static boolean glfwDown(int key) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getWindow() == null) {
			return false;
		}
		long handle = client.getWindow().getHandle();
		return handle != 0 && GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
	}
}
