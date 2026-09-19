package com.beatblock.client.export;

import com.beatblock.client.BeatBlockAuthoritativeWorldMutator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Export 重建前确保 BUILD 相关 chunk 已在权威 ServerWorld 加载，
 * 避免 {@link com.beatblock.engine.BuildExecutionPolicy#OFFLINE_EXPORT} 因未加载而停在缺块状态。
 */
public final class ExportChunkPreloader {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExportChunkPreloader.class);

	private ExportChunkPreloader() {}

	/**
	 * 同步确保 positions 所在 chunk 已加载（集成服务端）。无服务端时 no-op。
	 *
	 * @return 请求加载的 distinct chunk 数量
	 */
	public static int ensureChunksLoadedAndAwait(
		World referenceWorld,
		Collection<BlockPos> positions,
		Duration timeout
	) {
		if (referenceWorld == null || positions == null || positions.isEmpty()) {
			return 0;
		}
		Set<ChunkPos> chunks = new LinkedHashSet<>();
		for (BlockPos pos : positions) {
			if (pos != null) {
				chunks.add(new ChunkPos(pos));
			}
		}
		if (chunks.isEmpty()) {
			return 0;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		MinecraftServer server = mc != null ? mc.getServer() : null;
		if (server == null) {
			return 0;
		}
		ServerWorld serverWorld = server.getWorld(referenceWorld.getRegistryKey());
		if (serverWorld == null) {
			return 0;
		}

		Runnable load = () -> {
			for (ChunkPos chunkPos : chunks) {
				serverWorld.getChunk(chunkPos.x, chunkPos.z);
			}
		};

		if (server.isOnThread()) {
			load.run();
		} else {
			CompletableFuture<Void> done = new CompletableFuture<>();
			server.execute(() -> {
				try {
					load.run();
					done.complete(null);
				} catch (Throwable t) {
					done.completeExceptionally(t);
				}
			});
			BeatBlockAuthoritativeWorldMutator.awaitFuture(
				done,
				timeout != null ? timeout : BeatBlockAuthoritativeWorldMutator.DEFAULT_AWAIT_TIMEOUT,
				"export-chunk-preload"
			);
		}
		LOGGER.debug("Export chunk preload requested {} chunks for {} blocks", chunks.size(), positions.size());
		return chunks.size();
	}
}
