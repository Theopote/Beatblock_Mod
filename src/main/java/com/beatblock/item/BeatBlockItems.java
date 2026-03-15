package com.beatblock.item;

import com.beatblock.BeatBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * 模组物品注册，并加入创造模式「工具与实用品」标签页（参考 ChronoBlocks ModItems）。
 */
public final class BeatBlockItems {

	public static Item BEATBLOCK_CONTROLLER;

	private static Item register(Item item, String id) {
		Identifier identifier = BeatBlock.id(id);
		return Registry.register(Registries.ITEM, identifier, item);
	}

	public static void initialize() {
		Identifier id = BeatBlock.id("beatblock_controller");
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
		BEATBLOCK_CONTROLLER = new BeatBlockControllerItem(new Item.Settings().registryKey(key).maxCount(1));
		Registry.register(Registries.ITEM, id, BEATBLOCK_CONTROLLER);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
			.register(entries -> entries.add(BEATBLOCK_CONTROLLER));
	}
}
