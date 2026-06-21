package com.beatblock.engine.layer;

import com.beatblock.client.BeatBlockAuthoritativeWorldMutator;
import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理所有 {@link BuildLayer} 及其世界写入（隐藏/显示/释放）。
 */
public final class BuildLayerManager {

	private final StageObjectSystem stageObjectSystem;
	private final Map<String, BuildLayer> layers = new LinkedHashMap<>();

	public BuildLayerManager(StageObjectSystem stageObjectSystem) {
		this.stageObjectSystem = stageObjectSystem;
	}

	public Collection<BuildLayer> getAll() {
		return List.copyOf(layers.values());
	}

	public BuildLayer get(String id) {
		return id != null ? layers.get(id) : null;
	}

	public BuildLayer getByClipId(String clipId) {
		if (clipId == null || clipId.isBlank()) return null;
		for (BuildLayer layer : layers.values()) {
			if (clipId.equals(layer.getBoundClipId())) return layer;
		}
		return null;
	}

	public BuildLayer createFromSelection(String name, List<BlockPos> blocks) {
		if (blocks == null || blocks.isEmpty()) return null;
		String layerName = name != null && !name.isBlank() ? name.trim() : "layer";
		String id = uniqueLayerId(layerName);
		String stageId = id + "_stage";
		StageObject stageObject = StageObjectSystem.fromSelectionSnapshot(
			stageId, layerName, blocks, com.beatblock.engine.GroupSortingStrategy.SEQUENTIAL, 0.0);
		stageObjectSystem.register(stageObject);
		BuildLayer layer = new BuildLayer(
			id, layerName, stageObject, LayerVisibilityState.FREE_VISIBLE, Map.of(), null);
		layers.put(id, layer);
		return layer;
	}

	public void registerRestored(BuildLayer layer) {
		if (layer == null) return;
		layers.put(layer.getId(), layer);
		if (layer.getStageObject() != null) {
			stageObjectSystem.register(layer.getStageObject());
		}
	}

	public boolean hideLayer(BuildLayer layer, World world) {
		if (layer == null || world == null || !layer.canToggleVisibility()) return false;
		if (layer.getState() == LayerVisibilityState.FREE_HIDDEN) return true;
		Map<BlockPos, BlockState> captured = new LinkedHashMap<>();
		List<BlockControlExecutor.BlockMutation> mutations = new ArrayList<>();
		for (BlockPos pos : layer.getStageObject().getBlocks()) {
			if (pos == null || !world.isChunkLoaded(pos)) continue;
			BlockState current = world.getBlockState(pos);
			captured.put(pos.toImmutable(), current);
			if (!current.isAir()) {
				mutations.add(new BlockControlExecutor.BlockMutation(pos.toImmutable(), current, Blocks.AIR.getDefaultState()));
			}
		}
		layer.mutableCapturedStates().clear();
		layer.mutableCapturedStates().putAll(captured);
		layer.setState(LayerVisibilityState.FREE_HIDDEN);
		applyMutations(world, mutations);
		return true;
	}

	public boolean showLayer(BuildLayer layer, World world) {
		if (layer == null || world == null || !layer.canToggleVisibility()) return false;
		if (layer.getState() == LayerVisibilityState.FREE_VISIBLE) return true;
		List<BlockControlExecutor.BlockMutation> mutations = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> entry : layer.getCapturedStates().entrySet()) {
			BlockPos pos = entry.getKey();
			BlockState target = entry.getValue();
			if (pos == null || target == null || !world.isChunkLoaded(pos)) continue;
			BlockState current = world.getBlockState(pos);
			if (!current.equals(target)) {
				mutations.add(new BlockControlExecutor.BlockMutation(pos.toImmutable(), current, target));
			}
		}
		layer.setState(LayerVisibilityState.FREE_VISIBLE);
		applyMutations(world, mutations);
		return true;
	}

	public boolean deleteLayer(BuildLayer layer, World world) {
		if (layer == null || !layer.canDelete()) return false;
		if (layer.getState() == LayerVisibilityState.FREE_HIDDEN && world != null) {
			showLayer(layer, world);
		}
		layers.remove(layer.getId());
		stageObjectSystem.remove(layer.getStageObjectId());
		return true;
	}

	public void bindToClip(BuildLayer layer, String clipId) {
		if (layer == null || !layer.canBindToTrack()) return;
		layer.setBoundClipId(clipId);
		layer.setState(LayerVisibilityState.BOUND_TO_TRACK);
	}

	public void unbindFromClip(BuildLayer layer) {
		if (layer == null) return;
		layer.setBoundClipId(null);
		if (layer.getState() == LayerVisibilityState.BOUND_TO_TRACK) {
			layer.setState(LayerVisibilityState.FREE_HIDDEN);
		}
	}

	public void restoreBinding(BuildLayer layer, String clipId, LayerVisibilityState state) {
		if (layer == null) return;
		layer.setBoundClipId(clipId);
		layer.setState(state != null ? state : LayerVisibilityState.FREE_HIDDEN);
	}

	public void clear() {
		layers.clear();
	}

	/** 工程加载后：将 FREE_HIDDEN / BOUND_TO_TRACK 图层写回空气（不重新捕获）。 */
	public void applyPersistedWorldState(World world) {
		if (world == null) return;
		for (BuildLayer layer : layers.values()) {
			if (layer.getState() == LayerVisibilityState.FREE_HIDDEN
				|| layer.getState() == LayerVisibilityState.BOUND_TO_TRACK) {
				applyHiddenBlocks(layer, world);
			}
		}
	}

	private void applyHiddenBlocks(BuildLayer layer, World world) {
		List<BlockControlExecutor.BlockMutation> mutations = new ArrayList<>();
		for (BlockPos pos : layer.getStageObject().getBlocks()) {
			if (pos == null || !world.isChunkLoaded(pos)) continue;
			BlockState current = world.getBlockState(pos);
			if (!current.isAir()) {
				mutations.add(new BlockControlExecutor.BlockMutation(
					pos.toImmutable(), current, Blocks.AIR.getDefaultState()));
			}
		}
		applyMutations(world, mutations);
	}

	private static void applyMutations(World world, List<BlockControlExecutor.BlockMutation> mutations) {
		if (mutations == null || mutations.isEmpty()) return;
		BlockControlExecutor executor = new BlockControlExecutor(null);
		BeatBlockAuthoritativeWorldMutator.applyAuthoritative(executor, world, mutations);
	}

	private String uniqueLayerId(String baseName) {
		String slug = baseName.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
		if (slug.isBlank()) slug = "layer";
		String candidate = "layer_" + slug;
		if (!layers.containsKey(candidate)) return candidate;
		return candidate + "_" + UUID.randomUUID().toString().substring(0, 8);
	}

	public static World currentWorld() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc != null ? mc.world : null;
	}
}
