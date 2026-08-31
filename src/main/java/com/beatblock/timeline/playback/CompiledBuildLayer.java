package com.beatblock.timeline.playback;

import net.minecraft.util.math.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Immutable build-layer identity for formal playback (Phase B).
 * <p>
 * Captures layer ↔ RuntimeStageObject binding and block positions at compile time so
 * playback does not observe live {@code BuildLayerManager} mutations.
 */
public record CompiledBuildLayer(
	String layerId,
	String name,
	String stageObjectId,
	@Nullable String boundClipId,
	String visibilityState,
	@Nullable String groupId,
	List<BlockPos> blocks
) {
	public CompiledBuildLayer {
		layerId = layerId != null ? layerId : "";
		name = name != null ? name : layerId;
		stageObjectId = stageObjectId != null ? stageObjectId : "";
		if (boundClipId != null && boundClipId.isBlank()) {
			boundClipId = null;
		}
		visibilityState = visibilityState != null && !visibilityState.isBlank()
			? visibilityState
			: "FREE_VISIBLE";
		if (groupId != null && groupId.isBlank()) {
			groupId = null;
		}
		blocks = blocks != null ? List.copyOf(blocks) : List.of();
	}

	public int blockCount() {
		return blocks.size();
	}
}
