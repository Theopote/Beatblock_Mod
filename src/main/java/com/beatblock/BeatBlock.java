package com.beatblock;

import com.beatblock.animation.AnimationManager;
import com.beatblock.animation.AnimationRegistry;
import com.beatblock.audio.AudioLoader;
import com.beatblock.audio.BeatmapGenerator;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.beat.BeatScheduler;
import com.beatblock.stage.StageManager;
import com.beatblock.visual.BlockDisplayPool;
import com.beatblock.visual.BlockSpawner;
import com.beatblock.visual.TransformUpdater;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeatBlock implements ModInitializer {
	public static final String MOD_ID = "beatblock";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// 各系统单例，供 Client 与网络等使用
	public static AudioLoader audioLoader;
	public static BeatmapGenerator beatmapGenerator;
	public static MusicPlayer musicPlayer;
	public static BeatScheduler beatScheduler;
	public static AnimationRegistry animationRegistry;
	public static AnimationManager animationManager;
	public static BlockDisplayPool blockDisplayPool;
	public static BlockSpawner blockSpawner;
	public static TransformUpdater transformUpdater;
	public static StageManager stageManager;

	@Override
	public void onInitialize() {
		audioLoader = new AudioLoader();
		beatmapGenerator = new BeatmapGenerator();
		musicPlayer = new MusicPlayer();
		beatScheduler = new BeatScheduler();
		animationRegistry = new AnimationRegistry();
		animationManager = new AnimationManager();
		blockDisplayPool = new BlockDisplayPool();
		blockSpawner = new BlockSpawner();
		transformUpdater = new TransformUpdater();
		stageManager = new StageManager();

		LOGGER.info("BeatBlock 模组已加载 — 音乐驱动方块动画引擎");
	}
}