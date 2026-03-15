package com.beatblock.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 时间线数据模型：统一持有所有轨道数据，供 UI 与播放/自动编排使用。
 * 结构：Audio（Waveform + Low/Mid/High）| Animation（Block + Auto）| Camera | Global Event。
 */
public final class TimelineModel {

	private double durationSeconds = 0;
	private WaveformData waveform;
	private final List<FrequencyEvent> frequencyEvents = new ArrayList<>();
	private final List<TimelineAnimationEvent> blockAnimationEvents = new ArrayList<>();
	private final List<TimelineAnimationEvent> autoAnimationEvents = new ArrayList<>();
	private final List<CameraKeyframe> cameraKeyframes = new ArrayList<>();
	private final List<GlobalEvent> globalEvents = new ArrayList<>();

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(double durationSeconds) {
		this.durationSeconds = Math.max(0, durationSeconds);
	}

	public WaveformData getWaveform() {
		return waveform;
	}

	public void setWaveform(WaveformData waveform) {
		this.waveform = waveform;
	}

	public List<FrequencyEvent> getFrequencyEvents() {
		return Collections.unmodifiableList(frequencyEvents);
	}

	public List<FrequencyEvent> getFrequencyEventsByBand(FrequencyBand band) {
		List<FrequencyEvent> out = new ArrayList<>();
		for (FrequencyEvent e : frequencyEvents) {
			if (e.getBand() == band) out.add(e);
		}
		out.sort(Comparator.comparingDouble(FrequencyEvent::getTimeSeconds));
		return out;
	}

	public void addFrequencyEvent(FrequencyEvent e) {
		if (e != null) frequencyEvents.add(e);
	}

	public void clearFrequencyEvents() {
		frequencyEvents.clear();
	}

	public List<TimelineAnimationEvent> getBlockAnimationEvents() {
		return Collections.unmodifiableList(blockAnimationEvents);
	}

	public void addBlockAnimationEvent(TimelineAnimationEvent e) {
		if (e != null) blockAnimationEvents.add(e);
	}

	/** 清空方块动画事件（与 v2 同步时使用）。 */
	public void clearBlockAnimationEvents() {
		blockAnimationEvents.clear();
	}

	public List<TimelineAnimationEvent> getAutoAnimationEvents() {
		return Collections.unmodifiableList(autoAnimationEvents);
	}

	public void addAutoAnimationEvent(TimelineAnimationEvent e) {
		if (e != null) autoAnimationEvents.add(e);
	}

	/** 清空自动编排生成的动画事件（Smart Auto Map 可先清空再生成）。 */
	public void clearAutoAnimationEvents() {
		autoAnimationEvents.clear();
	}

	public List<CameraKeyframe> getCameraKeyframes() {
		return Collections.unmodifiableList(cameraKeyframes);
	}

	public void addCameraKeyframe(CameraKeyframe k) {
		if (k != null) cameraKeyframes.add(k);
	}

	public void clearCameraKeyframes() {
		cameraKeyframes.clear();
	}

	public List<GlobalEvent> getGlobalEvents() {
		return Collections.unmodifiableList(globalEvents);
	}

	public void addGlobalEvent(GlobalEvent e) {
		if (e != null) globalEvents.add(e);
	}

	public void clearGlobalEvents() {
		globalEvents.clear();
	}

	public void sortAll() {
		frequencyEvents.sort(Comparator.comparingDouble(FrequencyEvent::getTimeSeconds));
		blockAnimationEvents.sort(Comparator.comparingDouble(TimelineAnimationEvent::getTimeSeconds));
		autoAnimationEvents.sort(Comparator.comparingDouble(TimelineAnimationEvent::getTimeSeconds));
		cameraKeyframes.sort(Comparator.comparingDouble(CameraKeyframe::getTimeSeconds));
		globalEvents.sort(Comparator.comparingDouble(GlobalEvent::getTimeSeconds));
	}
}
