package com.beatblock.engine.layer;

import com.beatblock.engine.StageObject;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BUILD 专属图层：包裹一个 {@link StageObject}，管理隐藏快照与轨道绑定。
 */
public final class BuildLayer {

	private final String id;
	private String name;
	private final StageObject stageObject;
	private LayerVisibilityState state;
	private final Map<BlockPos, BlockState> capturedStates;
	private String boundClipId;
	private String groupId;
	private int colorArgb;

	public BuildLayer(
		String id,
		String name,
		StageObject stageObject,
		LayerVisibilityState state,
		Map<BlockPos, BlockState> capturedStates,
		String boundClipId
	) {
		this.id = id != null ? id : "";
		this.name = name != null && !name.isBlank() ? name : this.id;
		this.stageObject = stageObject;
		this.state = state != null ? state : LayerVisibilityState.FREE_VISIBLE;
		this.capturedStates = capturedStates != null ? new LinkedHashMap<>(capturedStates) : new LinkedHashMap<>();
		this.boundClipId = boundClipId;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId != null && !groupId.isBlank() ? groupId : null;
	}

	public int getColorArgb() {
		return colorArgb;
	}

	public void setColorArgb(int colorArgb) {
		this.colorArgb = Math.max(0, colorArgb);
	}

	public String getId() { return id; }
	public String getName() { return name; }
	public void setName(String name) {
		if (name != null && !name.isBlank()) this.name = name.trim();
	}
	public StageObject getStageObject() { return stageObject; }
	public String getStageObjectId() {
		return stageObject != null ? stageObject.getId() : "";
	}
	public LayerVisibilityState getState() { return state; }
	public void setState(LayerVisibilityState state) {
		if (state != null) this.state = state;
	}
	public Map<BlockPos, BlockState> getCapturedStates() {
		return Collections.unmodifiableMap(capturedStates);
	}
	Map<BlockPos, BlockState> mutableCapturedStates() { return capturedStates; }
	public String getBoundClipId() { return boundClipId; }
	public void setBoundClipId(String boundClipId) { this.boundClipId = boundClipId; }

	public boolean canToggleVisibility() {
		return state == LayerVisibilityState.FREE_VISIBLE || state == LayerVisibilityState.FREE_HIDDEN;
	}

	public boolean canDelete() {
		return state != LayerVisibilityState.BOUND_TO_TRACK;
	}

	public boolean canBindToTrack() {
		return state == LayerVisibilityState.FREE_HIDDEN;
	}
}
