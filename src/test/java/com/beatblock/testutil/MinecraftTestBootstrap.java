package com.beatblock.testutil;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;

/**
 * 最小化 Minecraft 引导，供依赖 {@link net.minecraft.block.BlockState} / 注册表的单元测试使用。
 */
public final class MinecraftTestBootstrap {

	private static boolean initialized;

	private MinecraftTestBootstrap() {}

	public static void ensureInitialized() {
		if (initialized) return;
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		initialized = true;
	}
}
