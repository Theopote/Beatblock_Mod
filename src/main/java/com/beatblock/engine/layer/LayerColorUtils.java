package com.beatblock.engine.layer;

/** ARGB 颜色与 ImGui 浮点 RGBA 互转。 */
public final class LayerColorUtils {

	private LayerColorUtils() {
	}

	public static int fromFloatRgb(float r, float g, float b) {
		int ri = Math.max(0, Math.min(255, Math.round(r * 255f)));
		int gi = Math.max(0, Math.min(255, Math.round(g * 255f)));
		int bi = Math.max(0, Math.min(255, Math.round(b * 255f)));
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}

	public static float[] toFloatRgb(int argb) {
		if (argb == 0) {
			return new float[]{0.75f, 0.78f, 0.85f};
		}
		return new float[]{
			((argb >> 16) & 0xFF) / 255f,
			((argb >> 8) & 0xFF) / 255f,
			(argb & 0xFF) / 255f
		};
	}

	public static void pushTextColor(int argb) {
		float[] rgb = toFloatRgb(argb);
		imgui.ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, rgb[0], rgb[1], rgb[2], 1f);
	}

	public static void popTextColor(int argb) {
		if (argb != 0) {
			imgui.ImGui.popStyleColor();
		}
	}
}
