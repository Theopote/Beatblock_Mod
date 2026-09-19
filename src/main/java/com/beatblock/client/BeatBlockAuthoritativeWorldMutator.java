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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 解析 BUILD / PLACE / CLEAR 这类需要真正持久化的世界 mutation 应该写到哪个 World、在哪个线程写。
 * <p>
 * <b>单人模式</b>（本地集成服务端存在）：解析为对应维度的 {@link ServerWorld}，
 * 通过 {@link MinecraftServer#execute(Runnable)} 调度到服务端线程执行。
 * <p>
 * 导出路径应使用 {@link #awaitingSinkFor} / {@link #applyAuthoritativeAndAwait}，
 * 在截图前等待服务端 mutation 完成，避免 frame N 捕获到 frame N-1 的世界状态。
 */
public final class BeatBlockAuthoritativeWorldMutator {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatBlockAuthoritativeWorldMutator.class);
	private static volatile boolean warnedNoIntegratedServer = false;
	/** 导出帧默认等待权威写入的上限。 */
	public static final Duration DEFAULT_AWAIT_TIMEOUT = Duration.ofSeconds(5);

	private BeatBlockAuthoritativeWorldMutator() {}

	/**
	 * 构造一个绑定到「当前权威世界」的 {@link WorldMutationSink}（异步 enqueue，不阻塞）。
	 */
	public static WorldMutationSink sinkFor(BlockControlExecutor executor, World referenceWorld) {
		if (executor == null || referenceWorld == null) return WorldMutationSink.NO_OP;
		return mutations -> applyAuthoritative(executor, referenceWorld, mutations);
	}

	/**
	 * 导出专用 sink：每次 apply 后阻塞等待服务端线程完成写入。
	 */
	public static WorldMutationSink awaitingSinkFor(BlockControlExecutor executor, World referenceWorld) {
		return awaitingSinkFor(executor, referenceWorld, DEFAULT_AWAIT_TIMEOUT);
	}

	public static WorldMutationSink awaitingSinkFor(
		BlockControlExecutor executor,
		World referenceWorld,
		Duration timeout
	) {
		if (executor == null || referenceWorld == null) return WorldMutationSink.NO_OP;
		Duration effective = timeout != null ? timeout : DEFAULT_AWAIT_TIMEOUT;
		return mutations -> applyAuthoritativeAndAwait(executor, referenceWorld, mutations, effective);
	}

	/** 解析权威世界并调度一次写入；referenceWorld 仅用于解析维度，不会被写入。 */
	public static void applyAuthoritative(
		BlockControlExecutor executor,
		World referenceWorld,
		List<BlockControlExecutor.BlockMutation> mutations
	) {
		applyAuthoritativeAsync(executor, referenceWorld, mutations);
	}

	/**
	 * 异步提交权威写入，返回可等待的 Future。
	 * 无服务端 / 空 mutations 时立即完成。
	 */
	public static CompletableFuture<Void> applyAuthoritativeAsync(
		BlockControlExecutor executor,
		World referenceWorld,
		List<BlockControlExecutor.BlockMutation> mutations
	) {
		if (executor == null || mutations == null || mutations.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}
		ServerWorld serverWorld = resolveServerWorld(referenceWorld);
		if (serverWorld == null) {
			return CompletableFuture.completedFuture(null);
		}
		MinecraftServer server = serverWorld.getServer();
		if (server == null) {
			return CompletableFuture.completedFuture(null);
		}
		if (server.isOnThread()) {
			executor.applyMutations(serverWorld, mutations);
			return CompletableFuture.completedFuture(null);
		}
		CompletableFuture<Void> future = new CompletableFuture<>();
		server.execute(() -> {
			try {
				executor.applyMutations(serverWorld, mutations);
				future.complete(null);
			} catch (Throwable t) {
				future.completeExceptionally(t);
			}
		});
		return future;
	}

	/**
	 * 提交权威写入并等待完成（导出 completion barrier）。
	 * 已在服务端线程时同步执行，避免自等待死锁。
	 */
	public static void applyAuthoritativeAndAwait(
		BlockControlExecutor executor,
		World referenceWorld,
		List<BlockControlExecutor.BlockMutation> mutations,
		Duration timeout
	) {
		CompletableFuture<Void> future = applyAuthoritativeAsync(executor, referenceWorld, mutations);
		awaitFuture(future, timeout != null ? timeout : DEFAULT_AWAIT_TIMEOUT, "world-mutation");
	}

	/** 等待任意已提交的权威 mutation Future。 */
	public static void awaitFuture(CompletableFuture<?> future, Duration timeout, String label) {
		if (future == null || future.isDone()) {
			return;
		}
		Duration effective = timeout != null ? timeout : DEFAULT_AWAIT_TIMEOUT;
		try {
			future.get(Math.max(1L, effective.toMillis()), TimeUnit.MILLISECONDS);
		} catch (TimeoutException ex) {
			LOGGER.warn("BeatBlock：等待权威写入超时 ({} , {}ms)", label, effective.toMillis());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			LOGGER.warn("BeatBlock：等待权威写入被中断 ({})", label);
		} catch (Exception ex) {
			LOGGER.warn("BeatBlock：等待权威写入失败 ({})", label, ex);
		}
	}

	/**
	 * 解析权威世界并调度一次「逐块恢复到给定状态」。
	 */
	public static void restoreAuthoritative(World referenceWorld, Map<BlockPos, BlockState> stateByPos) {
		restoreAuthoritativeAsync(referenceWorld, stateByPos);
	}

	public static CompletableFuture<Void> restoreAuthoritativeAsync(
		World referenceWorld,
		Map<BlockPos, BlockState> stateByPos
	) {
		if (stateByPos == null || stateByPos.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}
		ServerWorld serverWorld = resolveServerWorld(referenceWorld);
		if (serverWorld == null) {
			return CompletableFuture.completedFuture(null);
		}
		MinecraftServer server = serverWorld.getServer();
		if (server == null) {
			return CompletableFuture.completedFuture(null);
		}
		Runnable task = () -> {
			for (Map.Entry<BlockPos, BlockState> entry : stateByPos.entrySet()) {
				BlockPos pos = entry.getKey();
				if (!serverWorld.isChunkLoaded(pos)) continue;
				BlockState target = entry.getValue();
				if (!serverWorld.getBlockState(pos).equals(target)) {
					serverWorld.setBlockState(pos, target, 3);
				}
			}
		};
		if (server.isOnThread()) {
			task.run();
			return CompletableFuture.completedFuture(null);
		}
		CompletableFuture<Void> future = new CompletableFuture<>();
		server.execute(() -> {
			try {
				task.run();
				future.complete(null);
			} catch (Throwable t) {
				future.completeExceptionally(t);
			}
		});
		return future;
	}

	public static void restoreAuthoritativeAndAwait(
		World referenceWorld,
		Map<BlockPos, BlockState> stateByPos,
		Duration timeout
	) {
		awaitFuture(
			restoreAuthoritativeAsync(referenceWorld, stateByPos),
			timeout != null ? timeout : DEFAULT_AWAIT_TIMEOUT,
			"world-restore"
		);
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

	static void resetWarnedForTests() {
		warnedNoIntegratedServer = false;
	}

	/**
	 * 轻量 barrier：累计多个 Future，一次性 await。
	 * 后续远程 ACK 可复用同一抽象。
	 */
	public static final class ServerMutationBarrier {
		private final List<CompletableFuture<?>> pending =
			Collections.synchronizedList(new ArrayList<>());

		public void track(CompletableFuture<?> future) {
			if (future == null) return;
			pending.add(future);
		}

		public void awaitAll(Duration timeout) {
			Duration effective = timeout != null ? timeout : DEFAULT_AWAIT_TIMEOUT;
			List<CompletableFuture<?>> copy;
			synchronized (pending) {
				copy = List.copyOf(pending);
				pending.clear();
			}
			if (copy.isEmpty()) return;
			@SuppressWarnings("unchecked")
			CompletableFuture<Void>[] array = copy.toArray(new CompletableFuture[0]);
			awaitFuture(CompletableFuture.allOf(array), effective, "mutation-barrier");
		}
	}
}
