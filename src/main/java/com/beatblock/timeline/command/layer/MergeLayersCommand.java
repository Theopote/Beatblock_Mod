package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.timeline.StageObjectTargetRemapper;
import com.beatblock.timeline.Timeline;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 合并多个图层为一个新图层（不改变世界方块状态）。 */
public final class MergeLayersCommand implements com.beatblock.timeline.command.Command {

	private record LayerSnapshot(
		String id,
		String name,
		RuntimeStageObject stageObject,
		LayerVisibilityState state,
		Map<BlockPos, BlockState> capturedStates,
		String boundClipId,
		String groupId,
		int colorArgb
	) {}

	private final BuildLayerManager manager;
	private final @Nullable Timeline timeline;
	private final List<String> sourceLayerIds;
	private final String mergedName;
	private final List<LayerSnapshot> snapshots = new ArrayList<>();
	private final List<BuildLayerGroup> removedGroups = new ArrayList<>();
	private @Nullable BuildLayer mergedLayer;
	private StageObjectTargetRemapper.@Nullable RemapResult targetRemap;

	public MergeLayersCommand(BuildLayerManager manager, List<String> sourceLayerIds, String mergedName) {
		this(manager, sourceLayerIds, mergedName, null);
	}

	public MergeLayersCommand(
		BuildLayerManager manager,
		List<String> sourceLayerIds,
		String mergedName,
		@Nullable Timeline timeline
	) {
		this.manager = manager;
		this.timeline = timeline;
		this.sourceLayerIds = sourceLayerIds != null ? List.copyOf(sourceLayerIds) : List.of();
		this.mergedName = mergedName;
	}

	public @Nullable BuildLayer getMergedLayer() {
		return mergedLayer;
	}

	@Override
	public void execute() {
		if (manager == null || sourceLayerIds.size() < 2) {
			return;
		}
		captureSnapshots();
		Set<String> dissolvedStageIds = collectSourceStageIds();
		mergedLayer = manager.mergeLayers(sourceLayerIds, mergedName);
		targetRemap = null;
		if (timeline != null && mergedLayer != null && !dissolvedStageIds.isEmpty()) {
			targetRemap = StageObjectTargetRemapper.remap(
				timeline,
				dissolvedStageIds,
				mergedLayer.getStageObjectId()
			);
		}
	}

	@Override
	public void undo() {
		if (manager == null || mergedLayer == null) {
			return;
		}
		manager.dissolveLayer(mergedLayer);
		mergedLayer = null;
		for (BuildLayerGroup group : removedGroups) {
			manager.registerGroup(group);
		}
		for (LayerSnapshot snapshot : snapshots) {
			BuildLayer restored = new BuildLayer(
				snapshot.id(),
				snapshot.name(),
				snapshot.stageObject(),
				snapshot.state(),
				snapshot.capturedStates(),
				snapshot.boundClipId()
			);
			restored.setGroupId(snapshot.groupId());
			restored.setColorArgb(snapshot.colorArgb());
			manager.registerRestored(restored);
		}
		if (timeline != null && targetRemap != null) {
			StageObjectTargetRemapper.restore(timeline, targetRemap);
			targetRemap = null;
		}
	}

	private Set<String> collectSourceStageIds() {
		Set<String> stageIds = new LinkedHashSet<>();
		for (LayerSnapshot snapshot : snapshots) {
			if (snapshot.stageObject() == null) continue;
			String stageId = snapshot.stageObject().getId();
			if (stageId != null && !stageId.isBlank()) {
				stageIds.add(stageId);
			}
		}
		return stageIds;
	}

	private void captureSnapshots() {
		snapshots.clear();
		removedGroups.clear();
		for (String layerId : sourceLayerIds) {
			BuildLayer layer = manager.get(layerId);
			if (layer == null) {
				continue;
			}
			String groupId = layer.getGroupId();
			if (groupId != null) {
				BuildLayerGroup group = manager.getGroup(groupId);
				if (group != null && removedGroups.stream().noneMatch(g -> g.getId().equals(groupId))) {
					removedGroups.add(new BuildLayerGroup(group.getId(), group.getName(), group.getColorArgb()));
				}
			}
			snapshots.add(new LayerSnapshot(
				layer.getId(),
				layer.getName(),
				layer.getStageObject(),
				layer.getState(),
				new LinkedHashMap<>(layer.getCapturedStates()),
				layer.getBoundClipId(),
				layer.getGroupId(),
				layer.getColorArgb()
			));
		}
	}
}
