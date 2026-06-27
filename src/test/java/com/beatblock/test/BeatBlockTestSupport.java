package com.beatblock.test;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.stage.StageManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;

/**
 * 单元测试用 {@link BeatBlockContext} 工厂；替代已删除的 {@code fromLegacyStatics}。
 */
public final class BeatBlockTestSupport {

	private BeatBlockTestSupport() {}

	public static BeatBlockContext minimalContext() {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		StageManager stageManager = new StageManager();
		Timeline timeline = Timeline.createDefault();
		MusicPlayer musicPlayer = new MusicPlayer();
		TimelineEditor editor = new TimelineEditor(timeline, musicPlayer);
		return BeatBlockContext.builder()
			.blockAnimationEngine(engine)
			.stageManager(stageManager)
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build();
	}
}
