package com.beatblock.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;

/**
 * 客户端主线程契约断言：编辑、播放状态与 Timeline 结构变更必须在 Minecraft 客户端线程执行。
 * <p>
 * 异步 worker（Python / ffmpeg / 文件 IO）应通过 {@link com.beatblock.client.export.ClientThreadExecutor#run(Runnable)}
 * 将 UI 与 Timeline 变更派发回主线程。参见 {@code docs/playback-compiler.md}。
 * <p>
 * Minecraft 1.21+ 可能拆分 game thread 与 {@link RenderSystem} render thread；
 * ImGui / Screen 侧的 Timeline 变更允许在二者任一之上执行。
 */
public final class ClientThreadGuard {

	private ClientThreadGuard() {}

	/**
	 * 若当前不在客户端主线程（或 render thread）则抛出 {@link IllegalStateException}。
	 * 无 {@link MinecraftClient} 实例时（单元测试 / 无头环境）视为通过。
	 */
	public static void assertClientThread() {
		if (!isClientThread()) {
			MinecraftClient client = MinecraftClient.getInstance();
			boolean onGame = client != null && client.isOnThread();
			boolean onRender = isOnRenderThreadSafe();
			throw new IllegalStateException(
				"BeatBlock client-thread contract violation: this operation must run on the "
					+ "Minecraft client thread. Dispatch async callbacks via ClientThreadExecutor.run(...). "
					+ "See docs/playback-compiler.md. "
					+ "thread=" + Thread.currentThread().getName()
					+ " isOnThread=" + onGame
					+ " isOnRenderThread=" + onRender
			);
		}
	}

	public static boolean isClientThread() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return true;
		}
		if (client.isOnThread()) {
			return true;
		}
		// 1.21+: ImGui/Screen 可能在 RenderSystem render thread 上驱动 UI 回调
		return isOnRenderThreadSafe();
	}

	private static boolean isOnRenderThreadSafe() {
		try {
			return RenderSystem.isOnRenderThread();
		} catch (Throwable ignored) {
			return false;
		}
	}
}
