package com.beatblock.ui.presenter;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.input.BeatBlockInputSystem;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.selection.BeatBlockSelectionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

final class ToolPanelPresenterFactory {

	private ToolPanelPresenterFactory() {}

	static ToolPanelPresenter create(BeatBlockContext context) {
		return new ToolPanelPresenter(
			BeatBlockSelectionManager::get,
			() -> context.blockAnimationEngine() != null
				? context.blockAnimationEngine().getStageObjectSystem()
				: null,
			() -> {
				MinecraftClient mc = MinecraftClient.getInstance();
				return mc != null ? mc.world : null;
			},
			ToolPanelPresenterFactory::readCrosshairBlockPos
		);
	}

	private static BlockPos readCrosshairBlockPos() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null) {
			return null;
		}
		if (mc.currentScreen instanceof BeatBlockUIScreen) {
			BlockHitResult bhr = BeatBlockInputSystem.raycastFromImGui();
			if (bhr == null || bhr.getType() != HitResult.Type.BLOCK) {
				return null;
			}
			return bhr.getBlockPos().toImmutable();
		}
		HitResult hit = mc.crosshairTarget;
		if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		return bhr.getBlockPos().toImmutable();
	}
}
