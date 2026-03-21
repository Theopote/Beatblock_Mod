package com.beatblock.timeline;

/**
 * 音频播放器的最小接口，供 Timeline 模块注入使用，解耦全局单例依赖，便于独立测试。
 */
public interface IAudioPlayer {
	boolean isPlaying();
	double getCurrentTimeSeconds();
	void setCurrentTimeSeconds(double timeSeconds);
	void play();
	void pause();
	void stop();
}
