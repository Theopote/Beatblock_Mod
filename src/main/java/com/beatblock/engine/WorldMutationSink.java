package com.beatblock.engine;

import java.util.List;

/**
 * BUILD / PLACE / CLEAR 等需要真正写入世界的 mutation 的统一出口。
 * <p>
 * 引擎层（engine/*）只通过这个接口产出 mutation 请求，不直接感知
 * {@code MinecraftClient}/{@code MinecraftServer}，由调用方（通常是
 * {@code com.beatblock.client.BeatBlockClientDriver}）注入具体实现：
 * 决定写到哪个 World、在哪个线程写、是否需要持久化。
 * <p>
 * 这个抽象存在的原因：早期实现里，BUILD/PLACE/CLEAR 直接拿客户端世界
 * （{@code MinecraftClient.world}）调用 {@code setBlockState}，写入只影响客户端本地视图，
 * 不会持久化、不会同步给其他玩家——重进游戏或区块重新加载后改动就消失。
 * 见 {@code com.beatblock.client.BeatBlockAuthoritativeWorldMutator}。
 */
@FunctionalInterface
public interface WorldMutationSink {

	void apply(List<BlockControlExecutor.BlockMutation> mutations);

	/** 什么都不做：用于「仅预览，不应产生任何真实写入」的场景（如编辑器播放头预览）。 */
	WorldMutationSink NO_OP = mutations -> {};

	/**
	 * 兜底实现：同步直接写入给定 World，不做权威世界解析，不保证线程安全或持久化。
	 * 仅用于离线测试，或尚未迁移到 {@code BeatBlockAuthoritativeWorldMutator} 的旧调用路径
	 * （见各处标记 {@code @Deprecated} 的兼容重载）。
	 */
	static WorldMutationSink direct(BlockControlExecutor executor, net.minecraft.world.World world) {
		if (executor == null || world == null) return NO_OP;
		return mutations -> executor.applyMutations(world, mutations);
	}
}
