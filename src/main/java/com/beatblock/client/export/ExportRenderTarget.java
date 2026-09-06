package com.beatblock.client.export;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Export-sized scene render target.
 * <p>
 * Minecraft renders into {@link MinecraftClient#getFramebuffer()}. During export we temporarily
 * resize that main framebuffer to the requested export resolution so the world pass actually
 * draws at 1080p/1440p — not a nearest-neighbor upscale of the window.
 * <p>
 * Lifetime: activate in {@link VideoExportCoordinator#start}, {@link #close()} in cleanup.
 */
public final class ExportRenderTarget implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExportRenderTarget.class);

	private final int exportWidth;
	private final int exportHeight;
	private final int restoreWidth;
	private final int restoreHeight;
	private boolean active;
	private boolean closed;

	private ExportRenderTarget(int exportWidth, int exportHeight, int restoreWidth, int restoreHeight) {
		this.exportWidth = Math.max(1, exportWidth);
		this.exportHeight = Math.max(1, exportHeight);
		this.restoreWidth = Math.max(1, restoreWidth);
		this.restoreHeight = Math.max(1, restoreHeight);
	}

	/**
	 * Resize the main framebuffer to export size. Returns null if the client/window is unavailable.
	 */
	public static @Nullable ExportRenderTarget activate(int exportWidth, int exportHeight) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getWindow() == null) {
			return null;
		}
		Framebuffer main = client.getFramebuffer();
		if (main == null) {
			return null;
		}
		int restoreW = Math.max(1, client.getWindow().getFramebufferWidth());
		int restoreH = Math.max(1, client.getWindow().getFramebufferHeight());
		int targetW = Math.max(1, exportWidth);
		int targetH = Math.max(1, exportHeight);

		ExportRenderTarget target = new ExportRenderTarget(targetW, targetH, restoreW, restoreH);
		if (main.textureWidth != targetW || main.textureHeight != targetH) {
			main.resize(targetW, targetH);
		}
		client.getWindow().setFramebufferWidth(targetW);
		client.getWindow().setFramebufferHeight(targetH);
		LOGGER.info("ExportRenderTarget: resized main framebuffer {}x{} → {}x{}",
			restoreW, restoreH, targetW, targetH);
		target.active = true;
		return target;
	}

	public int width() {
		return exportWidth;
	}

	public int height() {
		return exportHeight;
	}

	public boolean isActive() {
		return active && !closed;
	}

	/**
	 * Read the current main framebuffer as top-down RGBA bytes matching export size.
	 */
	public byte[] readRgbaTopDown() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return emptyFrame();
		}
		Framebuffer main = client.getFramebuffer();
		if (main == null) {
			return emptyFrame();
		}
		ensureExportSize(main);
		AtomicReference<NativeImage> imageRef = new AtomicReference<>();
		ScreenshotRecorder.takeScreenshot(main, imageRef::set);
		NativeImage image = imageRef.get();
		if (image == null) {
			LOGGER.warn("ExportRenderTarget: ScreenshotRecorder returned null image");
			return emptyFrame();
		}
		try {
			return nativeImageToRgbaTopDown(image, exportWidth, exportHeight);
		} finally {
			image.close();
		}
	}

	private void ensureExportSize(Framebuffer main) {
		if (main.textureWidth != exportWidth || main.textureHeight != exportHeight) {
			main.resize(exportWidth, exportHeight);
		}
	}

	private byte[] emptyFrame() {
		return new byte[exportWidth * exportHeight * 4];
	}

	/**
	 * Convert NativeImage ARGB pixels to top-down RGBA bytes (ffmpeg / compositor format).
	 * Package-visible for unit tests without a live Minecraft client.
	 */
	static byte[] nativeImageToRgbaTopDown(NativeImage image, int expectedW, int expectedH) {
		int w = image.getWidth();
		int h = image.getHeight();
		byte[] rgba = new byte[expectedW * expectedH * 4];
		int copyW = Math.min(w, expectedW);
		int copyH = Math.min(h, expectedH);
		for (int y = 0; y < copyH; y++) {
			for (int x = 0; x < copyW; x++) {
				writeArgbAsRgba(image.getColorArgb(x, y), rgba, (y * expectedW + x) * 4);
			}
		}
		return rgba;
	}

	/** ARGB int → RGBA bytes at {@code dst}. Package-visible for tests. */
	static void writeArgbAsRgba(int argb, byte[] rgba, int dst) {
		rgba[dst] = (byte) ((argb >> 16) & 0xFF);
		rgba[dst + 1] = (byte) ((argb >> 8) & 0xFF);
		rgba[dst + 2] = (byte) (argb & 0xFF);
		rgba[dst + 3] = (byte) ((argb >> 24) & 0xFF);
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		active = false;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}
		Framebuffer main = client.getFramebuffer();
		if (main == null) {
			return;
		}
		int windowW = client.getWindow() != null
			? Math.max(1, client.getWindow().getFramebufferWidth())
			: restoreWidth;
		int windowH = client.getWindow() != null
			? Math.max(1, client.getWindow().getFramebufferHeight())
			: restoreHeight;
		int targetW = windowW > 0 ? windowW : restoreWidth;
		int targetH = windowH > 0 ? windowH : restoreHeight;
		// Prefer the size we saved at activate time if window still reports the export size.
		if (targetW == exportWidth && targetH == exportHeight) {
			targetW = restoreWidth;
			targetH = restoreHeight;
		}
		if (main.textureWidth != targetW || main.textureHeight != targetH) {
			main.resize(targetW, targetH);
		}
		client.getWindow().setFramebufferWidth(targetW);
		client.getWindow().setFramebufferHeight(targetH);
		LOGGER.info("ExportRenderTarget: restored main framebuffer to {}x{}", targetW, targetH);
	}
}
