package com.beatblock;

import com.beatblock.ui.EditorScreen;
import com.beatblock.ui.HUD;
import com.beatblock.ui.ImportScreen;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeatBlockClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(BeatBlock.MOD_ID + "-client");

	public static HUD hud;
	public static EditorScreen editorScreen;
	public static ImportScreen importScreen;

	@Override
	public void onInitializeClient() {
		hud = new HUD();
		editorScreen = new EditorScreen();
		importScreen = new ImportScreen();

		LOGGER.info("BeatBlock 客户端已初始化 — ImGUI 界面就绪");
	}
}
