package com.beatblock.imgui;

import imgui.ImGui;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 在 CI（无 Minecraft / 无显示）上验证当前平台的 imgui-java JNI 可被加载。
 * 不初始化 GLFW/GL 后端，仅覆盖 {@link UnsatisfiedLinkError} 类崩溃。
 */
class ImGuiNativesTest {
	@Test
	void imguiNativeLibraryLoads() {
		assertDoesNotThrow(() -> {
			ImGui.createContext();
			ImGui.destroyContext();
		});
	}
}
