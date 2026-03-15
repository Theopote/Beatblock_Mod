package com.beatblock.audio;

import com.beatblock.beat.Beatmap;
import com.beatblock.beat.BeatEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据音频或 BPM 生成/解析节拍图，供 Beat 系统使用。
 */
public class BeatmapGenerator {

	/**
	 * 根据 BPM 和时长生成简单均匀节拍（用于占位或测试）。
	 */
	public Beatmap generateFromBpm(String name, double bpm, double durationSeconds) {
		List<BeatEvent> events = new ArrayList<>();
		double beatInterval = 60.0 / bpm;
		for (double t = 0; t < durationSeconds; t += beatInterval) {
			events.add(new BeatEvent(t, BeatEvent.Type.KICK, 1f, 0));
		}
		return new Beatmap(name, bpm, durationSeconds, events);
	}

	/**
	 * 从已有事件列表构建 Beatmap。
	 */
	public Beatmap build(String name, double bpm, double durationSeconds, List<BeatEvent> events) {
		return new Beatmap(name, bpm, durationSeconds, events);
	}
}
