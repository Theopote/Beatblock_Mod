package com.beatblock.item;

import com.beatblock.BeatBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * 手持此物品右键时打开 BeatBlock UI 界面（仅客户端，通过 BeatBlock.openUICallback 桥接）。
 * 参考 TreeFactory TreeFactoryTool 的 use/useOnBlock 写法。
 */
public class BeatBlockControllerItem extends Item {

	public BeatBlockControllerItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (world.isClient() && BeatBlock.openUICallback != null) {
			BeatBlock.openUICallback.run();
			return ActionResult.SUCCESS;
		}
		return ActionResult.SUCCESS;
	}
}
