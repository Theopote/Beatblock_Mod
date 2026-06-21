package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import net.minecraft.world.World;

/**
 * 删除图层（FREE 状态下会先恢复方块）。
 * <p>
 * 注意：{@link BuildLayerManager#deleteLayer} 对 FREE_HIDDEN 图层会先调用
 * {@code showLayer} 把世界方块恢复可见、并把 {@code layer.state} 改成 FREE_VISIBLE，
 * 再从管理器里移除。这意味着 {@code snapshot} 对象在 execute() 结束后，
 * 其 state 字段已经是 FREE_VISIBLE 而不是删除前的真实状态——必须在 execute() 一开始
 * 就单独记录 previousState，undo() 时按这个记录的状态决定是否需要重新隐藏，
 * 否则撤销删除一个「原本隐藏」的图层会让方块变回可见，而不是回到删除前的隐藏状态。
 */
public final class DeleteLayerCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String layerId;
	private BuildLayer snapshot;
	private LayerVisibilityState previousState;

	public DeleteLayerCommand(BuildLayerManager manager, String layerId) {
		this.manager = manager;
		this.layerId = layerId;
	}

	@Override
	public void execute() {
		BuildLayer layer = manager != null ? manager.get(layerId) : null;
		if (layer == null || !layer.canDelete()) return;
		previousState = layer.getState();
		snapshot = layer;
		World world = BuildLayerManager.currentWorld();
		manager.deleteLayer(layer, world);
	}

	@Override
	public void undo() {
		if (snapshot == null || manager == null) return;
		manager.registerRestored(snapshot);
		if (previousState == LayerVisibilityState.FREE_HIDDEN) {
			World world = BuildLayerManager.currentWorld();
			if (world != null) {
				manager.hideLayer(snapshot, world);
			}
		}
	}
}
