package com.beatblock.client.export;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** 从当前 OpenGL framebuffer 读取 RGBA 像素（顶部为原点）。 */
public final class VideoFrameCapturer {

	private VideoFrameCapturer() {}

	public static byte[] captureRgbaTopDown(int targetWidth, int targetHeight) {
		MinecraftClient client = MinecraftClient.getInstance();
		int nativeWidth = client != null && client.getWindow() != null
			? Math.max(1, client.getWindow().getFramebufferWidth())
			: targetWidth;
		int nativeHeight = client != null && client.getWindow() != null
			? Math.max(1, client.getWindow().getFramebufferHeight())
			: targetHeight;

		byte[] nativeRgba = readFramebufferRgba(nativeWidth, nativeHeight);
		if (targetWidth == nativeWidth && targetHeight == nativeHeight) {
			return nativeRgba;
		}
		return scaleNearest(nativeRgba, nativeWidth, nativeHeight, targetWidth, targetHeight);
	}

	private static byte[] readFramebufferRgba(int width, int height) {
		int rowBytes = width * 4;
		int size = rowBytes * height;
		ByteBuffer buffer = MemoryUtil.memAlloc(size);
		try {
			GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
			byte[] rgba = new byte[size];
			for (int y = 0; y < height; y++) {
				buffer.position((height - 1 - y) * rowBytes);
				buffer.get(rgba, y * rowBytes, rowBytes);
			}
			return rgba;
		} finally {
			MemoryUtil.memFree(buffer);
		}
	}

	private static byte[] scaleNearest(byte[] source, int srcW, int srcH, int dstW, int dstH) {
		byte[] dest = new byte[dstW * dstH * 4];
		for (int y = 0; y < dstH; y++) {
			int srcY = Math.min(srcH - 1, (int) ((y / (double) dstH) * srcH));
			for (int x = 0; x < dstW; x++) {
				int srcX = Math.min(srcW - 1, (int) ((x / (double) dstW) * srcW));
				int srcIndex = (srcY * srcW + srcX) * 4;
				int dstIndex = (y * dstW + x) * 4;
				dest[dstIndex] = source[srcIndex];
				dest[dstIndex + 1] = source[srcIndex + 1];
				dest[dstIndex + 2] = source[srcIndex + 2];
				dest[dstIndex + 3] = source[srcIndex + 3];
			}
		}
		return dest;
	}
}
