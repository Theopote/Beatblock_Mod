package com.beatblock.client;

import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.engine.WorldMutationSink;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 解析 BUILD / PLACE / CLEAR 这类需要真正持久化的世界 mutation 应该写到哪个 World、在哪个线程写。
 * <p>
 * <b>单人模式</b>（本地集成服务端存在）：解析为对应维度的 {@link ServerWorld}，
 * 通过 {@link MinecraftServer#execute(Runnable)} 调度到服务端线程执行——这样写入才会被保存、
 * 被光照 / 区块系统正确处理、并通过正常的区块同步机制让其他观察者看到。
 * <p>
 * <b>联机模式</b>（连接远程专用服务端，没有本地集成服务端）：当前不支持权威写入。
 * 这里故意不回退到「直接改客户端世界」，因为那样会悄悄重新引入「画面看起来变了，
 * 但服务端和其他玩家都不知道、重进游戏后打回原状」的问题——而这正是本类要解决的问题。
 * 后续若要支持真正联机场景，需要新增一对 C2S/S2C 自定义网络包：客户端请求 → 服务端执行
 * 同样的 mutation → 服务端通过正常区块更新包广播给所有观察者。本类预留了调用点，
 * 实现时只需替换 {@link #resolveServerWorld(World)} 里 {@code server == null} 分支。
 */
public final class BeatBlockAuthoritativeWorldMutator {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatBlockAuthoritativeWorldMutator.class);
	private static volatile boolean warnedNoIntegratedServer = false;

	private BeatBlockAuthoritativeWorldMutator() {}

	/**
	 * 构造一个绑定到「当前权威世界」的 {@link WorldMutationSink}。
	 * referenceWorld 仅用于解析维度（通常传入 {@code MinecraftClient.world}），本身不会被写入。
	 */
	public static WorldMutationSink sinkFor(BlockControlExecutor executor, World referenceWorld) {
		if (executor == null || referenceWorld == null) return WorldMutationSink.NO_OP;
		return mutations -> applyAuthoritative(executor, referenceWorld, mutations);
	}

	/** 解析权威世界并调度一次写入；referenceWorld 仅用于解析维度，不会被写入。 */
	public static void applyAuthoritative(
		BlockControlExecutor executor,
		World referenceWorld,
		List<BlockControlExecutor.BlockMutation> mutations
	) {
		if (executor == null || mutations == null || mutations.isEmpty()) return;
		ServerWorld serverWorld = resolveServerWorld(referenceWorld);
		if (serverWorld == null) return;
		MinecraftServer server = serverWorld.getServer();
		server.execute(() -> executor.applyMutations(serverWorld, mutations));
	}

	/**
	 * 解析权威世界并调度一次「逐块恢复到给定状态」（用于编辑器播放头回退/预览安全网，
	 * 见 {@code BeatBlockClientDriver#restoreTimelineMutationSnapshot()}）。
	 * referenceWorld 仅用于解析维度。stateByPos 必须是调用方拍的不可变快照
	 * （不要传入会被并发修改的可变 Map，因为实际写入发生在稍后的服务端 tick 上）。
	 */
	public static void restoreAuthoritative(World referenceWorld, Map<BlockPos, BlockState> stateByPos) {
		if (stateByPos == null || stateByPos.isEmpty()) return;
		ServerWorld serverWorld = resolveServerWorld(referenceWorld);
		if (serverWorld == null) return;
		MinecraftServer server = serverWorld.getServer();
		server.execute(() -> {
			for (Map.Entry<BlockPos, BlockState> entry : stateByPos.entrySet()) {
				BlockPos pos = entry.getKey();
				if (!serverWorld.isChunkLoaded(pos)) continue;
				BlockState target = entry.getValue();
				if (!serverWorld.getBlockState(pos).equals(target)) {
					serverWorld.setBlockState(pos, target, 3);
				}
			}
		});
	}

	/** @return 与 referenceWorld 同维度的权威 ServerWorld；联机/无本地集成服务端时返回 null。 */
	private static ServerWorld resolveServerWorld(World referenceWorld) {
		if (referenceWorld == null) return null;
		MinecraftClient mc = MinecraftClient.getInstance();
		MinecraftServer server = mc != null ? mc.getServer() : null;
		if (server == null) {
			warnNoIntegratedServerOnce();
			return null;
		}
		ServerWorld serverWorld = server.getWorld(referenceWorld.getRegistryKey());
		if (serverWorld == null) {
			LOGGER.warn("BeatBlock：未能解析到与当前维度 {} 对应的服务端世界，写入已跳过。",
				referenceWorld.getRegistryKey().getValue());
		}
		return serverWorld;
	}

	private static void warnNoIntegratedServerOnce() {
		if (warnedNoIntegratedServer) return;
		warnedNoIntegratedServer = true;
		LOGGER.warn(
			"BeatBlock：未检测到本地集成服务端（可能正连接到联机服务器）。"
			+ "BUILD / PLACE / CLEAR 写入暂不支持在该场景下持久化，本次及后续同类写入将被跳过，"
			+ "直到该限制被移除（需要新增网络包支持，见类注释）。");
	}
}
