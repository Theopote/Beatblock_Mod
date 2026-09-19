package com.beatblock.engine;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.selection.BlockStateLookup;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.generation.PacingRequest;
import com.beatblock.timeline.generation.PacingStrategy;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * 累积式建造序列器：将 RuntimeStageObject 的方块按 BuildSequenceMode 排序，
 * 根据事件时长逐步放置（BUILD）或逐步移除（DISSOLVE 反向）。
 * <p>
 * 节拍对齐只消费调用方传入的 {@code referenceBeatTimes} / {@code bpm}
 * （通常来自 {@code CompiledTimelineSnapshot}），不持有 live {@code Timeline}。
 */
public final class BuildSequencer {

	private final StageObjectSystem stageObjectSystem;
	private final BuildLayerManager buildLayerManager;
	/**
	 * 每帧最多产生的世界 mutation 数量。默认无上限；
	 * 播放路径可由 {@code BeatBlockClientDriver} 注入有限预算，避免单 tick 卡顿。
	 */
	private int mutationBudgetPerTick = Integer.MAX_VALUE;
	private BuildExecutionPolicy executionPolicy = BuildExecutionPolicy.REALTIME;

	public BuildSequencer(StageObjectSystem stageObjectSystem, BuildLayerManager buildLayerManager) {
		this.stageObjectSystem = stageObjectSystem;
		this.buildLayerManager = buildLayerManager;
	}

	/** 应用执行策略（预算 + 未加载区块行为）。 */
	public void setExecutionPolicy(BuildExecutionPolicy policy) {
		this.executionPolicy = policy != null ? policy : BuildExecutionPolicy.REALTIME;
		setMutationBudgetPerTick(this.executionPolicy.mutationBudgetPerTick());
	}

	public BuildExecutionPolicy getExecutionPolicy() {
		return executionPolicy;
	}

	/**
	 * 设置每帧 mutation 预算。{@code <= 0} 视为无上限。
	 */
	public void setMutationBudgetPerTick(int budget) {
		this.mutationBudgetPerTick = budget <= 0 ? Integer.MAX_VALUE : budget;
	}

	public int getMutationBudgetPerTick() {
		return mutationBudgetPerTick;
	}

	/**
	 * 活跃的建造序列实例。
	 */
	public static final class BuildInstance {
		private final String eventId;
		private final List<BlockPos> orderedBlocks;
		private final BlockState targetState;
		private final Map<BlockPos, BlockState> perBlockTargetStates;
		private final double startTime;
		private final double endTime;
		private final boolean dissolve;
		/** 每个方块各自的揭示时间戳（卡节拍），null 时回退到线性插值 */
		private final List<Double> blockTimestamps;
		private int placedCount;

		BuildInstance(String eventId, List<BlockPos> orderedBlocks, BlockState targetState,
		              Map<BlockPos, BlockState> perBlockTargetStates,
		              double startTime, double endTime, boolean dissolve,
		              List<Double> blockTimestamps) {
			this.eventId = eventId;
			this.orderedBlocks = orderedBlocks;
			this.targetState = targetState;
			this.perBlockTargetStates = perBlockTargetStates;
			this.startTime = startTime;
			this.endTime = endTime;
			this.dissolve = dissolve;
			this.blockTimestamps = blockTimestamps != null ? List.copyOf(blockTimestamps) : null;
			this.placedCount = 0;
		}

		public String getEventId() { return eventId; }
		public boolean isFinished() { return placedCount >= orderedBlocks.size(); }
		public int getPlacedCount() { return placedCount; }
		public int getTotalBlocks() { return orderedBlocks.size(); }
		List<Double> getBlockTimestamps() { return blockTimestamps; }

		BlockState resolveTargetState(BlockPos pos) {
			if (perBlockTargetStates != null && pos != null) {
				BlockState perBlock = perBlockTargetStates.get(pos);
				if (perBlock != null) return perBlock;
			}
			return targetState;
		}
	}

	private final List<BuildInstance> activeInstances = new ArrayList<>();

	/** 无节拍上下文时回退线性插值（测试 / 兼容）。 */
	public BuildInstance schedule(TimelineAnimationEvent event) {
		return schedule(event, null, 0.0);
	}

	/**
	 * 从事件创建建造序列；节拍 pacing 仅使用冻结的 {@code referenceBeatTimes} / {@code bpm}。
	 */
	public BuildInstance schedule(
		TimelineAnimationEvent event,
		double @Nullable [] referenceBeatTimes,
		double bpm
	) {
		if (event == null) return null;

		var payload = event.getPayload();
		String layerId = null;
		String buildModeRaw = "wall";
		boolean dissolveFlag = false;
		String placeBlockId = null;
		if (payload instanceof com.beatblock.timeline.payload.StageEventPayload.Build build) {
			layerId = build.layerId();
			buildModeRaw = build.buildMode();
			dissolveFlag = build.dissolve();
			placeBlockId = build.placeBlockId();
		} else {
			Map<String, Object> params = event.getParameters();
			layerId = readLayerId(params);
			buildModeRaw = String.valueOf(params.getOrDefault("buildMode", "wall"));
			dissolveFlag = "true".equalsIgnoreCase(String.valueOf(params.get("buildDissolve")));
			Object place = params.get("placeBlock");
			if (place == null) place = params.get("placeBlockId");
			if (place != null) placeBlockId = String.valueOf(place).trim();
		}
		BuildLayer layer = layerId != null && buildLayerManager != null ? buildLayerManager.get(layerId) : null;

		RuntimeStageObject target;
		Map<BlockPos, BlockState> perBlockTargets = null;
		boolean layerReveal = false;

		if (layer != null) {
			target = layer.getStageObject();
			perBlockTargets = new LinkedHashMap<>(layer.getCapturedStates());
			layerReveal = true;
		} else {
			target = stageObjectSystem.get(event.getTargetObjectId());
		}
		if (target == null || target.getBlocks().isEmpty()) return null;

		BuildSequenceMode mode = BuildSequenceMode.fromValue(buildModeRaw);
		boolean dissolve = !layerReveal && dissolveFlag;
		BlockState toState = dissolve
			? Blocks.AIR.getDefaultState()
			: (layerReveal ? Blocks.AIR.getDefaultState() : resolveBuildBlockState(placeBlockId));

		List<BlockPos> ordered = BlockBuildOrder.sortBlocks(
			target.getBlocks(), mode, target.getCenter(), event, target.getId());
		if (dissolve) Collections.reverse(ordered);

		double startTime = event.getTimeSeconds();
		double endTime = startTime + Math.max(0.05, event.getDurationSeconds());

		List<Double> blockTimestamps = computeBlockTimestamps(
			ordered.size(), startTime, endTime, referenceBeatTimes, bpm);

		BuildInstance instance = new BuildInstance(
			event.getEventId(), ordered, toState, perBlockTargets, startTime, endTime, dissolve || layerReveal,
			blockTimestamps);
		activeInstances.add(instance);
		return instance;
	}

	/**
	 * 使用冻结节拍网格预计算每个方块的揭示时间戳；无有效 beats 时返回 null（线性插值）。
	 */
	public static @Nullable List<Double> computeBlockTimestamps(
		int blockCount,
		double startTime,
		double endTime,
		double @Nullable [] referenceBeatTimes,
		double bpm
	) {
		if (blockCount <= 0) return null;
		double[] beats = referenceBeatTimes != null ? referenceBeatTimes : new double[0];
		if (beats.length == 0) return null;
		double safeBpm = bpm > 0 ? bpm : 120.0;

		PacingRequest request = new PacingRequest(
			blockCount,
			startTime,
			true,
			beats,
			safeBpm,
			60.0 / safeBpm
		);

		List<Double> timestamps = PacingStrategy.beatGrid().computeTimestamps(request);
		if (timestamps.isEmpty()) {
			return null;
		}
		List<Double> clamped = new ArrayList<>(timestamps.size());
		for (double t : timestamps) {
			clamped.add(Math.min(endTime, Math.max(startTime, t)));
		}
		return clamped;
	}

	/**
	 * 将本帧 EXISTENCE 维度的建造 mutation 写入 {@link com.beatblock.engine.influence.InfluenceFrame}。
	 */
	public void contributeExistenceMutations(
		com.beatblock.engine.influence.InfluenceFrame frame,
		double currentTime,
		World world
	) {
		if (frame == null || world == null || activeInstances.isEmpty()) return;
		contributeExistenceMutations(frame, currentTime, world::getBlockState, world::isChunkLoaded);
	}

	void contributeExistenceMutations(
		com.beatblock.engine.influence.InfluenceFrame frame,
		double currentTime,
		BlockStateLookup blockStates,
		Predicate<BlockPos> chunkLoaded
	) {
		if (frame == null || blockStates == null || chunkLoaded == null || activeInstances.isEmpty()) return;
		int remainingBudget = mutationBudgetPerTick;
		Iterator<BuildInstance> it = activeInstances.iterator();
		while (it.hasNext()) {
			if (remainingBudget <= 0) {
				break;
			}
			BuildInstance inst = it.next();
			if (currentTime < inst.startTime) continue;
			int target = computeTargetCount(inst, currentTime);
			while (inst.placedCount < target
				&& inst.placedCount < inst.orderedBlocks.size()
				&& remainingBudget > 0) {
				BlockPos pos = inst.orderedBlocks.get(inst.placedCount);
				BlockState desired = inst.resolveTargetState(pos);
				boolean loaded = chunkLoaded.test(pos);
				if (!loaded) {
					if (executionPolicy.advancePastUnloadedChunks()) {
						// REALTIME / PREVIEW：跳过并推进，避免远距 chunk 卡住
						inst.placedCount++;
						remainingBudget--;
						continue;
					}
					// OFFLINE_EXPORT：停在未加载块，等 chunk ready
					break;
				}
				BlockState current = blockStates.getBlockState(pos);
				if (!current.equals(desired)) {
					frame.addWorldMutation(new BlockControlExecutor.BlockMutation(
						pos.toImmutable(), current, desired));
					frame.addVfxTrigger(new com.beatblock.engine.influence.VfxTrigger(
						inst.dissolve ? "existence_dissolve" : "existence_place",
						pos.toImmutable(),
						currentTime,
						1f
					));
				}
				inst.placedCount++;
				remainingBudget--;
			}
			if (inst.isFinished()) it.remove();
		}
	}

	/**
	 * @deprecated 由 {@link com.beatblock.engine.influence.BlockInfluenceOrchestrator} 统一 tick
	 */
	@Deprecated
	public int tick(double currentTime, World world) {
		if (world == null || activeInstances.isEmpty()) return 0;
		com.beatblock.engine.influence.InfluenceFrame frame = new com.beatblock.engine.influence.InfluenceFrame();
		contributeExistenceMutations(frame, currentTime, world);
		BlockControlExecutor executor = new BlockControlExecutor(stageObjectSystem);
		executor.applyMutations(world, frame.getWorldMutations());
		return frame.getWorldMutations().size();
	}

	public List<BuildInstance> getActiveInstances() {
		return Collections.unmodifiableList(activeInstances);
	}

	public void clear() {
		activeInstances.clear();
	}

	void enqueueBuildInstance(BuildInstance instance) {
		if (instance != null) activeInstances.add(instance);
	}

	BuildInstance createInstanceForTest(
		String eventId,
		List<BlockPos> orderedBlocks,
		BlockState targetState,
		Map<BlockPos, BlockState> perBlockTargets,
		double startTime,
		double endTime,
		boolean dissolve
	) {
		return new BuildInstance(eventId, orderedBlocks, targetState, perBlockTargets, startTime, endTime, dissolve, null);
	}

	private static String readLayerId(Map<String, Object> params) {
		if (params == null) return null;
		Object raw = params.get("layerId");
		if (raw == null) return null;
		String id = String.valueOf(raw).trim();
		return id.isEmpty() ? null : id;
	}

	static int computeTargetCount(BuildInstance inst, double currentTime) {
		if (inst.blockTimestamps != null && !inst.blockTimestamps.isEmpty()) {
			int count = 0;
			for (double timestamp : inst.blockTimestamps) {
				if (currentTime >= timestamp - 1e-6) {
					count++;
				} else {
					break;
				}
			}
			return count;
		}
		return BlockBuildOrder.computeTargetBlockCount(
			inst.orderedBlocks.size(),
			inst.startTime,
			inst.endTime,
			currentTime
		);
	}

	/** 公开：给定时间戳列表时，已揭示方块数（供 digest / 测试）。 */
	public static int countRevealedBlocks(@Nullable List<Double> blockTimestamps, double currentTime, int totalBlocks) {
		if (blockTimestamps == null || blockTimestamps.isEmpty()) {
			return -1;
		}
		int count = 0;
		for (double timestamp : blockTimestamps) {
			if (currentTime >= timestamp - 1e-6) {
				count++;
			} else {
				break;
			}
		}
		return Math.min(count, Math.max(0, totalBlocks));
	}

	private static BlockState resolveBuildBlockState(@Nullable String placeBlockId) {
		if (placeBlockId == null || placeBlockId.isBlank()) {
			return Blocks.DIAMOND_BLOCK.getDefaultState();
		}
		String str = placeBlockId.trim();
		try {
			Identifier id = Identifier.of(str);
			if (!Registries.BLOCK.containsId(id)) return Blocks.DIAMOND_BLOCK.getDefaultState();
			Block block = Registries.BLOCK.get(id);
			return block.getDefaultState();
		} catch (Exception ex) {
			return Blocks.DIAMOND_BLOCK.getDefaultState();
		}
	}
}
