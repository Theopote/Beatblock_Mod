package com.beatblock.ui.notification;

import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayDeque;
import java.util.Deque;

/** 轻量 Toast 通知：右下角堆叠显示，自动淡出。 */
public final class ToastNotificationSystem {

	private static final int MAX_VISIBLE = 4;
	private static final long DEFAULT_DURATION_MS = 3500L;

	private static final Deque<Toast> toasts = new ArrayDeque<>();

	private ToastNotificationSystem() {
	}

	public record Toast(String message, boolean success, long expiresAtMs) {}

	public static void showSuccess(String message) {
		show(message, true, DEFAULT_DURATION_MS);
	}

	public static void showError(String message) {
		show(message, false, DEFAULT_DURATION_MS);
	}

	public static void show(String message, boolean success) {
		show(message, success, DEFAULT_DURATION_MS);
	}

	public static void show(String message, boolean success, long durationMs) {
		if (message == null || message.isBlank()) {
			return;
		}
		long now = System.currentTimeMillis();
		toasts.addFirst(new Toast(message, success, now + Math.max(1000L, durationMs)));
		while (toasts.size() > MAX_VISIBLE) {
			toasts.removeLast();
		}
	}

	public static void clear() {
		toasts.clear();
	}

	public static void render() {
		long now = System.currentTimeMillis();
		toasts.removeIf(t -> t.expiresAtMs() <= now);
		if (toasts.isEmpty()) {
			return;
		}

		ImGuiViewport viewport = ImGui.getMainViewport();
		float margin = 16f;
		float toastWidth = 340f;
		float y = viewport.getWorkPosY() + viewport.getWorkSizeY() - margin;
		int index = 0;

		for (Toast toast : toasts) {
			float remaining = Math.max(0f, (toast.expiresAtMs() - now) / (float) DEFAULT_DURATION_MS);
			float alpha = Math.min(1f, remaining);

			ImGui.setNextWindowBgAlpha(0.92f * alpha);
			ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowRounding, 8f);
			ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 12f, 10f);
			if (toast.success()) {
				ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.12f, 0.28f, 0.14f, 1f);
				ImGui.pushStyleColor(ImGuiCol.Border, 0.25f, 0.65f, 0.35f, 1f);
			} else {
				ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.30f, 0.14f, 0.12f, 1f);
				ImGui.pushStyleColor(ImGuiCol.Border, 0.75f, 0.35f, 0.25f, 1f);
			}

			String windowId = BBTexts.get("beatblock.toast.window") + "##toast_" + index;
			ImGui.setNextWindowSize(toastWidth, 0f);
			float posX = viewport.getWorkPosX() + viewport.getWorkSizeX() - toastWidth - margin;
			ImGui.setNextWindowPos(posX, y, imgui.flag.ImGuiCond.Always, 0f, 1f);

			int flags = ImGuiWindowFlags.NoDecoration
				| ImGuiWindowFlags.NoInputs
				| ImGuiWindowFlags.NoNav
				| ImGuiWindowFlags.NoFocusOnAppearing
				| ImGuiWindowFlags.NoMove
				| ImGuiWindowFlags.AlwaysAutoResize;

			if (ImGui.begin(windowId, flags)) {
				String prefix = toast.success()
					? BBTexts.get("beatblock.toast.success_prefix")
					: BBTexts.get("beatblock.toast.error_prefix");
				ImGui.textColored(1f, 1f, 1f, alpha, prefix + " " + toast.message());
			}
			ImGui.end();

			ImGui.popStyleColor(2);
			ImGui.popStyleVar(2);

			y -= ImGui.getWindowHeight() + 8f;
			index++;
		}
	}
}
