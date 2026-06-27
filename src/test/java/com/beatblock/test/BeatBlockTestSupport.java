package com.beatblock.test;

import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.audio.AudioConversionService;
import com.beatblock.video.VideoExportService;
import com.beatblock.audio.AudioLoader;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.audio.StemMixer;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
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
			.audioLoader(new AudioLoader())
			.musicPlayer(musicPlayer)
			.stemMixer(new StemMixer())
			.stageManager(stageManager)
			.timeline(timeline)
			.timelineEditor(editor)
			.blockAnimationEngine(engine)
			.audioAnalysisEngine(new AudioAnalysisEngine())
			.externalAudioAnalyzer(new AudioAnalysisService())
			.audioConversionService(new AudioConversionService())
			.videoExportService(new VideoExportService(Runnable::run))
			.build();
	}
}
