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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 管理所有 {@link BuildLayer} 及其世界写入（隐藏/显示/释放）。
 */
public final class BuildLayerManager {

	private final StageObjectSystem stageObjectSystem;
	private final Map<String, BuildLayer> layers = new LinkedHashMap<>();
	private final Map<String, BuildLayerGroup> groups = new LinkedHashMap<>();
	/** 方块 → 所属图层 id，保证同一 BlockPos 只能归属一个图层。 */
	private final Map<BlockPos, String> blockOwnerByPos = new HashMap<>();

	public BuildLayerManager(StageObjectSystem stageObjectSystem) {
		this.stageObjectSystem = stageObjectSystem;
	}

	public Collection<BuildLayer> getAll() {
		return List.copyOf(layers.values());
	}

	public Collection<BuildLayerGroup> getAllGroups() {
		return List.copyOf(groups.values());
	}

	public BuildLayerGroup getGroup(String groupId) {
		return groupId != null ? groups.get(groupId) : null;
	}

	public List<BuildLayer> getLayersInGroup(String groupId) {
		if (groupId == null || groupId.isBlank()) {
			return List.of();
		}
		List<BuildLayer> result = new ArrayList<>();
		for (BuildLayer layer : layers.values()) {
			if (groupId.equals(layer.getGroupId())) {
				result.add(layer);
			}
		}
		return result;
	}

	public List<BuildLayer> getUngroupedLayers() {
		List<BuildLayer> result = new ArrayList<>();
		for (BuildLayer layer : layers.values()) {
			if (layer.getGroupId() == null) {
				result.add(layer);
			}
		}
		return result;
	}

	public List<String> getLayerOrderIds() {
		return new ArrayList<>(layers.keySet());
	}

	public void setLayerOrder(List<String> order) {
		if (order == null || order.isEmpty()) {
			return;
		}
		LinkedHashMap<String, BuildLayer> reordered = new LinkedHashMap<>();
		for (String id : order) {
			BuildLayer layer = layers.get(id);
			if (layer != null) {
				reordered.put(id, layer);
			}
		}
		for (Map.Entry<String, BuildLayer> entry : layers.entrySet()) {
			reordered.putIfAbsent(entry.getKey(), entry.getValue());
		}
		layers.clear();
		layers.putAll(reordered);
	}

	/**
	 * 将 movingId 移到 targetId 之前；仅允许同一分组（含未分组）内排序。
	 */
	public boolean moveLayerBefore(String movingId, String targetId) {
		if (movingId == null || targetId == null || movingId.equals(targetId)) {
			return false;
		}
		BuildLayer moving = layers.get(movingId);
		BuildLayer target = layers.get(targetId);
		if (moving == null || target == null) {
			return false;
		}
		if (!java.util.Objects.equals(moving.getGroupId(), target.getGroupId())) {
			return false;
		}
		List<String> ids = new ArrayList<>(layers.keySet());
		if (!ids.remove(movingId)) {
			return false;
		}
		int targetIndex = ids.indexOf(targetId);
		if (targetIndex < 0) {
			ids.add(movingId);
			setLayerOrder(ids);
			return false;
		}
		ids.add(targetIndex, movingId);
		setLayerOrder(ids);
		return true;
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
		List<BlockPos> available = filterUnclaimedBlocks(blocks);
		if (available.isEmpty()) return null;

		String layerName = uniqueLayerName(name);
		String id = uniqueLayerId(layerName);
		String stageId = id + "_stage";
		StageObject stageObject = StageObjectSystem.fromSelectionSnapshot(
			stageId, layerName, available, com.beatblock.engine.GroupSortingStrategy.SEQUENTIAL, 0.0);
		stageObjectSystem.register(stageObject);

		Map<BlockPos, BlockState> initialCapture = new LinkedHashMap<>();
		World world = currentWorld();
		if (world != null) {
			snapshotBlocksFromWorld(available, world, initialCapture);
		}

		BuildLayer layer = new BuildLayer(
			id, layerName, stageObject, LayerVisibilityState.FREE_HIDDEN, initialCapture, null);
		layers.put(id, layer);
		claimBlocks(layer);
		if (world != null) {
			applyHiddenBlocks(layer, world);
		}
		return layer;
	}

	/** 选区中已被任意图层占用的方块数量。 */
	public int countClaimedBlocks(List<BlockPos> blocks) {
		if (blocks == null || blocks.isEmpty()) return 0;
		int count = 0;
		for (BlockPos pos : blocks) {
			if (pos != null && isBlockClaimed(pos)) count++;
		}
		return count;
	}

	public boolean isBlockClaimed(BlockPos pos) {
		if (pos == null) return false;
		return blockOwnerByPos.containsKey(pos.toImmutable());
	}

	public BuildLayer getLayerOwningBlock(BlockPos pos) {
		if (pos == null) return null;
		String layerId = blockOwnerByPos.get(pos.toImmutable());
		return layerId != null ? layers.get(layerId) : null;
	}

	public List<BlockPos> filterUnclaimedBlocks(List<BlockPos> blocks) {
		if (blocks == null || blocks.isEmpty()) return List.of();
		List<BlockPos> out = new ArrayList<>(blocks.size());
		for (BlockPos pos : blocks) {
			if (pos == null || isBlockClaimed(pos)) continue;
			out.add(pos.toImmutable());
		}
		return out;
	}

	public boolean renameLayer(BuildLayer layer, String newName) {
		if (layer == null || newName == null || newName.isBlank()) return false;
		String trimmed = newName.trim();
		if (trimmed.equals(layer.getName())) return true;
		if (isNameTaken(trimmed, layer.getId())) return false;
		layer.setName(trimmed);
		return true;
	}

	public boolean isNameTaken(String name, String excludeLayerId) {
		if (name == null || name.isBlank()) return true;
		for (BuildLayer layer : layers.values()) {
			if (excludeLayerId != null && excludeLayerId.equals(layer.getId())) continue;
			if (layer.getName().equalsIgnoreCase(name.trim())) return true;
		}
		return false;
	}

	public void registerRestored(BuildLayer layer) {
		if (layer == null) return;
		layers.put(layer.getId(), layer);
		if (layer.getStageObject() != null) {
			stageObjectSystem.register(layer.getStageObject());
		}
		claimBlocks(layer);
	}

	public boolean hideLayer(BuildLayer layer, World world) {
		if (layer == null || world == null || !layer.canToggleVisibility()) return false;
		if (layer.getState() == LayerVisibilityState.FREE_HIDDEN) return true;

		Map<BlockPos, BlockState> captured = new LinkedHashMap<>(layer.getCapturedStates());
		List<BlockControlExecutor.BlockMutation> mutations = new ArrayList<>();
		for (BlockPos pos : layer.getStageObject().getBlocks()) {
			if (pos == null) continue;
			BlockPos immutable = pos.toImmutable();
			if (isBlockPosInLoadedChunk(world, pos)) {
				BlockState current = world.getBlockState(pos);
				captured.put(immutable, current);
				if (!current.isAir()) {
					mutations.add(new BlockControlExecutor.BlockMutation(immutable, current, Blocks.AIR.getDefaultState()));
				}
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
		for (BlockPos pos : layer.getStageObject().getBlocks()) {
			if (pos == null) continue;
			BlockPos immutable = pos.toImmutable();
			BlockState target = layer.getCapturedStates().get(immutable);
			if (target == null || !isBlockPosInLoadedChunk(world, immutable)) continue;
			BlockState current = world.getBlockState(immutable);
			if (!current.equals(target)) {
				mutations.add(new BlockControlExecutor.BlockMutation(immutable, current, target));
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
		releaseBlocks(layer);
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
		groups.clear();
		blockOwnerByPos.clear();
	}

	public BuildLayerGroup createGroup(String name, List<String> layerIds) {
		if (layerIds == null || layerIds.isEmpty()) {
			return null;
		}
		List<BuildLayer> members = new ArrayList<>();
		for (String layerId : layerIds) {
			BuildLayer layer = layers.get(layerId);
			if (layer == null) {
				return null;
			}
			members.add(layer);
		}
		String groupName = name != null && !name.isBlank() ? name.trim() : "group";
		String groupId = uniqueGroupId(groupName);
		BuildLayerGroup group = new BuildLayerGroup(groupId, groupName, 0);
		groups.put(groupId, group);
		for (BuildLayer layer : members) {
			layer.setGroupId(groupId);
		}
		return group;
	}

	public boolean dissolveGroup(String groupId) {
		if (groupId == null || !groups.containsKey(groupId)) {
			return false;
		}
		groups.remove(groupId);
		for (BuildLayer layer : layers.values()) {
			if (groupId.equals(layer.getGroupId())) {
				layer.setGroupId(null);
			}
		}
		return true;
	}

	public boolean ungroupLayers(List<String> layerIds) {
		if (layerIds == null || layerIds.isEmpty()) {
			return false;
		}
		Set<String> affectedGroups = new HashSet<>();
		for (String layerId : layerIds) {
			BuildLayer layer = layers.get(layerId);
			if (layer == null || layer.getGroupId() == null) {
				continue;
			}
			affectedGroups.add(layer.getGroupId());
			layer.setGroupId(null);
		}
		for (String groupId : affectedGroups) {
			boolean anyLeft = layers.values().stream().anyMatch(layer -> groupId.equals(layer.getGroupId()));
			if (!anyLeft) {
				groups.remove(groupId);
			}
		}
		return true;
	}

	public boolean renameGroup(BuildLayerGroup group, String newName) {
		if (group == null || newName == null || newName.isBlank()) {
			return false;
		}
		String trimmed = newName.trim();
		if (trimmed.equals(group.getName())) {
			return true;
		}
		if (isGroupNameTaken(trimmed, group.getId())) {
			return false;
		}
		group.setName(trimmed);
		return true;
	}

	public boolean isGroupNameTaken(String name, String excludeGroupId) {
		if (name == null || name.isBlank()) {
			return true;
		}
		for (BuildLayerGroup group : groups.values()) {
			if (excludeGroupId != null && excludeGroupId.equals(group.getId())) {
				continue;
			}
			if (group.getName().equalsIgnoreCase(name.trim())) {
				return true;
			}
		}
		return false;
	}

	public BuildLayer mergeLayers(List<String> layerIds, String requestedName) {
		if (layerIds == null || layerIds.size() < 2) {
			return null;
		}
		List<BuildLayer> sources = new ArrayList<>(layerIds.size());
		for (String layerId : layerIds) {
			BuildLayer layer = layers.get(layerId);
			if (layer == null || !layer.canDelete()) {
				return null;
			}
			sources.add(layer);
		}

		LayerVisibilityState state = sources.getFirst().getState();
		if (state == LayerVisibilityState.BOUND_TO_TRACK) {
			return null;
		}
		for (BuildLayer layer : sources) {
			if (layer.getState() != state) {
				return null;
			}
		}

		List<BlockPos> mergedBlocks = new ArrayList<>();
		Map<BlockPos, BlockState> mergedCapture = new LinkedHashMap<>();
		Set<String> groupIds = new HashSet<>();
		int mergedColor = 0;
		for (BuildLayer layer : sources) {
			mergedBlocks.addAll(layer.getStageObject().getBlocks());
			mergedCapture.putAll(layer.getCapturedStates());
			if (layer.getGroupId() != null) {
				groupIds.add(layer.getGroupId());
			}
			if (layer.getColorArgb() != 0) {
				mergedColor = layer.getColorArgb();
			}
		}

		for (BuildLayer layer : sources) {
			dissolveLayer(layer);
		}

		String baseName = requestedName != null && !requestedName.isBlank()
			? requestedName.trim()
			: sources.getFirst().getName() + "_merged";
		String layerName = uniqueLayerName(baseName);
		String id = uniqueLayerId(layerName);
		StageObject stageObject = StageObjectSystem.fromSelectionSnapshot(
			id + "_stage", layerName, mergedBlocks, com.beatblock.engine.GroupSortingStrategy.SEQUENTIAL, 0.0);
		stageObjectSystem.register(stageObject);
		BuildLayer merged = new BuildLayer(id, layerName, stageObject, state, mergedCapture, null);
		if (groupIds.size() == 1) {
			merged.setGroupId(groupIds.iterator().next());
		}
		merged.setColorArgb(mergedColor);
		layers.put(id, merged);
		claimBlocks(merged);
		return merged;
	}

	public void registerGroup(BuildLayerGroup group) {
		if (group != null) {
			groups.put(group.getId(), group);
		}
	}

	public void dissolveLayer(BuildLayer layer) {
		if (layer == null) {
			return;
		}
		String groupId = layer.getGroupId();
		layers.remove(layer.getId());
		releaseBlocks(layer);
		stageObjectSystem.remove(layer.getStageObjectId());
		if (groupId != null) {
			boolean anyLeft = layers.values().stream().anyMatch(candidate -> groupId.equals(candidate.getGroupId()));
			if (!anyLeft) {
				groups.remove(groupId);
			}
		}
	}

	private void claimBlocks(BuildLayer layer) {
		if (layer == null || layer.getStageObject() == null) return;
		for (BlockPos pos : layer.getStageObject().getBlocks()) {
			if (pos == null) continue;
			blockOwnerByPos.put(pos.toImmutable(), layer.getId());
		}
	}

	private void releaseBlocks(BuildLayer layer) {
		if (layer == null || layer.getStageObject() == null) return;
		for (BlockPos pos : layer.getStageObject().getBlocks()) {
			if (pos == null) continue;
			BlockPos immutable = pos.toImmutable();
			String owner = blockOwnerByPos.get(immutable);
			if (layer.getId().equals(owner)) {
				blockOwnerByPos.remove(immutable);
			}
		}
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
			if (pos == null || !isBlockPosInLoadedChunk(world, pos)) continue;
			BlockState current = world.getBlockState(pos);
			if (!current.isAir()) {
				mutations.add(new BlockControlExecutor.BlockMutation(
					pos.toImmutable(), current, Blocks.AIR.getDefaultState()));
			}
		}
		applyMutations(world, mutations);
	}

	private static boolean isBlockPosInLoadedChunk(World world, BlockPos pos) {
		if (world == null || pos == null) return false;
		return world.getChunkAsView(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private static void applyMutations(World world, List<BlockControlExecutor.BlockMutation> mutations) {
		if (mutations == null || mutations.isEmpty()) return;
		BlockControlExecutor executor = new BlockControlExecutor(null);
		BeatBlockAuthoritativeWorldMutator.applyAuthoritative(executor, world, mutations);
	}

	private static void snapshotBlocksFromWorld(
		List<BlockPos> blocks,
		World world,
		Map<BlockPos, BlockState> target
	) {
		if (blocks == null || world == null || target == null) return;
		for (BlockPos pos : blocks) {
			if (pos == null || !isBlockPosInLoadedChunk(world, pos)) continue;
			target.put(pos.toImmutable(), world.getBlockState(pos));
		}
	}

	private String uniqueGroupId(String baseName) {
		String slug = baseName.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
		if (slug.isBlank()) {
			slug = "group";
		}
		String candidate = "group_" + slug;
		if (!groups.containsKey(candidate)) {
			return candidate;
		}
		return candidate + "_" + UUID.randomUUID().toString().substring(0, 8);
	}

	private String uniqueLayerName(String requestedName) {
		String base = requestedName != null && !requestedName.isBlank() ? requestedName.trim() : "layer";
		if (!isNameTaken(base, null)) return base;
		for (int i = 2; i < 10_000; i++) {
			String candidate = base + "_" + i;
			if (!isNameTaken(candidate, null)) return candidate;
		}
		return base + "_" + UUID.randomUUID().toString().substring(0, 4);
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
