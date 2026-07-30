package com.beatblock.timeline.playback;

import com.beatblock.timeline.TimelineAnimationEvent;

import java.util.List;

/**
 * 一次正式播放使用的不可变时间线快照。编辑模型后续变化不会影响本对象。
 */
public final class CompiledTimelineSnapshot {

	private final List<TimelineAnimationEvent> stageEvents;
	private final List<CompiledStageEvent> compiledStageEvents;
	private final CompiledCameraTrack cameraTrack;
	private final double[] referenceBeatTimesSeconds;
	private final double bpm;
	private final boolean restoreWorldMutations;
	private final int sourceGeneration;

	CompiledTimelineSnapshot(
		List<TimelineAnimationEvent> stageEvents,
		List<CompiledStageEvent> compiledStageEvents,
		CompiledCameraTrack cameraTrack,
		double[] referenceBeatTimesSeconds,
		double bpm,
		boolean restoreWorldMutations,
		int sourceGeneration
	) {
		this.stageEvents = List.copyOf(stageEvents);
		this.compiledStageEvents = List.copyOf(compiledStageEvents);
		this.cameraTrack = cameraTrack;
		this.referenceBeatTimesSeconds = referenceBeatTimesSeconds.clone();
		this.bpm = bpm;
		this.restoreWorldMutations = restoreWorldMutations;
		this.sourceGeneration = sourceGeneration;
	}

	public List<TimelineAnimationEvent> stageEvents() { return stageEvents; }
	public List<CompiledStageEvent> compiledStageEvents() { return compiledStageEvents; }
	public CompiledCameraTrack cameraTrack() { return cameraTrack; }
	public double[] referenceBeatTimesSeconds() { return referenceBeatTimesSeconds.clone(); }
	public double bpm() { return bpm; }
	public boolean restoreWorldMutations() { return restoreWorldMutations; }
	public int sourceGeneration() { return sourceGeneration; }
}
