package com.beatblock.audio;

import com.beatblock.BeatBlock;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapReader;
import com.beatblock.audio.scheduler.AnimationScheduler;
import com.beatblock.audio.scheduler.AnimationScheduler.ScheduledEvent;
import com.beatblock.audio.scheduler.BeatClock;

import java.nio.file.Path;

/**
 * BeatBlockRuntime
 * ─────────────────────────────────────────────────────────────────────────────
 * 把 BeatClock + AnimationScheduler 连接起来的运行时入口。
 */
public final class BeatBlockRuntime {

	private static BeatBlockRuntime instance;

	private final BeatClock beatClock;
	private final AnimationScheduler scheduler;
	private final AudioAnalysisService analysisService;

	public static BeatBlockRuntime getInstance() {
		if (instance == null) instance = new BeatBlockRuntime();
		return instance;
	}

	private BeatBlockRuntime() {
		BeatClock.IAudioPlayer player = new MusicPlayerAdapter();
		this.beatClock = new BeatClock(player);
		this.scheduler = new AnimationScheduler();
		this.analysisService = new AudioAnalysisService();
		scheduler.addListener(this::onBeatEvent);
	}

	public void onServerTick() {
		beatClock.tick();
		scheduler.tick(beatClock.getPositionMs());
	}

	public void loadBeatmap(Path beatmapPath) {
		try {
			Beatmap beatmap = BeatmapReader.read(beatmapPath);
			scheduler.load(beatmap);
		} catch (Exception e) {
			System.err.println("[BeatBlock] 加载 beatmap 失败：" + e.getMessage());
		}
	}

	public void loadBeatmap(Beatmap beatmap) {
		if (beatmap == null) return;
		scheduler.load(beatmap);
	}

	public void analyzeAndLoad(Path audioPath) {
		analysisService.analyze(
			audioPath,
			(step, pct) -> System.out.printf("[BeatBlock] 分析进度 %s %d%%%n", step, pct),
			beatmap -> {
				scheduler.load(beatmap);
				System.out.println("[BeatBlock] 分析完成，Beatmap 已加载");
			},
			err -> System.err.println("[BeatBlock] 分析失败：" + err)
		);
	}

	public void play()  { beatClock.play(); }
	public void pause() { beatClock.pause(); }
	public void stop()  { beatClock.stop(); scheduler.reset(); }
	public boolean isPlaying() { return beatClock.isPlaying(); }

	public void seekTo(long ms) {
		beatClock.seekTo(ms);
		scheduler.reset();
	}

	private void onBeatEvent(ScheduledEvent se) {
		// TODO: 将事件转发到 BlockAnimationEngine
		if (se.needsLookahead()) {
			System.out.printf("[Scheduler] ARRIVE band=%s energy=%.2f remain=%dms%n",
				se.event().band(), se.event().energy(), se.timeUntilAnchorMs());
		} else {
			System.out.printf("[Scheduler] DEPART band=%s energy=%.2f t=%dms%n",
				se.event().band(), se.event().energy(), se.event().timeMs());
		}
	}

	private static final class MusicPlayerAdapter implements BeatClock.IAudioPlayer {
		@Override
		public long getPlaybackPositionMs() {
			if (BeatBlock.musicPlayer == null) return 0;
			return Math.round(BeatBlock.musicPlayer.getCurrentTimeSeconds() * 1000.0);
		}
		@Override
		public void seekTo(long ms) {
			if (BeatBlock.musicPlayer == null) return;
			BeatBlock.musicPlayer.setCurrentTimeSeconds(ms / 1000.0);
		}
	}
}

