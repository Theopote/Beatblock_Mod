package com.beatblock.imgui;

import imgui.ImGui;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 在 CI（无 Minecraft / 无显示）上验证当前平台的 imgui-java JNI 可被真正加载。
 * 不初始化 GLFW/GL 后端，仅覆盖 {@link UnsatisfiedLinkError} 类崩溃。
 */
class ImGuiNativesTest {

	@Test
	void imguiNativeLibraryLoads() {
		assertDoesNotThrow(() -> {
			var ctx = ImGui.createContext();
			assertNotNull(ctx, "ImGui.createContext() should return a non-null context handle");
			ImGui.destroyContext();
		});
	}

	@Test
	void imguiNativeBindingVersionAvailable() {
		String version = ImGui.getVersion();
		assertNotNull(version, "ImGui.getVersion() should be available after native binding load");
		assertFalse(version.isBlank(), "ImGui version should not be blank");
	}
}
