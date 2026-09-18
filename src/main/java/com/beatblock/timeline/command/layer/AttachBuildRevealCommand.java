package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.RuntimeStageObject;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

/** 为已有 StageObject 启用 Build / Reveal（附加 BuildLayer 包装）。 */
public final class AttachBuildRevealCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final RuntimeStageObject stageObject;
	private final String layerName;
	private @Nullable BuildLayer attached;

	public AttachBuildRevealCommand(BuildLayerManager manager, RuntimeStageObject stageObject, String layerName) {
		this.manager = manager;
		this.stageObject = stageObject;
		this.layerName = layerName != null ? layerName.trim() : "";
	}

	public @Nullable BuildLayer getAttachedLayer() {
		return attached;
	}

	@Override
	public void execute() {
		if (manager == null || stageObject == null) {
			return;
		}
		attached = manager.attachBuildReveal(stageObject, layerName);
	}

	@Override
	public void undo() {
		if (attached == null || manager == null) {
			return;
		}
		World world = BuildLayerManager.currentWorld();
		manager.removeLayerKeepStageObject(attached, world);
		attached = null;
	}
}
