package com.beatblock.client;

import net.minecraft.client.MinecraftClient;

/**
 * 客户端主线程契约断言：编辑、播放状态与 Timeline 结构变更必须在 Minecraft 客户端线程执行。
 * <p>
 * 异步 worker（Python / ffmpeg / 文件 IO）应通过 {@link ClientThreadExecutor#run(Runnable)}
 * 将 UI 与 Timeline 变更派发回主线程。参见 {@code docs/THREADING_CONTRACT.md}。
 */
public final class ClientThreadGuard {

	private ClientThreadGuard() {}

	/**
	 * 若当前不在客户端主线程则抛出 {@link IllegalStateException}。
	 * 无 {@link MinecraftClient} 实例时（单元测试 / 无头环境）视为通过。
	 */
	public static void assertClientThread() {
		if (!isClientThread()) {
			throw new IllegalStateException(
				"BeatBlock client-thread contract violation: this operation must run on the "
					+ "Minecraft client thread. Dispatch async callbacks via ClientThreadExecutor.run(...). "
					+ "See docs/THREADING_CONTRACT.md.");
		}
	}

	public static boolean isClientThread() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return true;
		}
		return client.isOnThread();
	}
}
