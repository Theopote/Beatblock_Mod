package com.beatblock.audio;

/**
 * 负责加载音频资源（路径/资源位置），供 BeatmapGenerator 与 MusicPlayer 使用。
 * 具体实现可依赖客户端音频 API 或服务端仅做元数据。
 */
public class AudioLoader {

	/**
	 * 占位：是否已加载指定资源。
	 */
	public boolean isLoaded(String pathOrId) {
		return false;
	}

	/**
	 * 占位：加载音频资源，返回是否成功。
	 */
	public boolean load(String pathOrId) {
		return false;
	}

	/**
	 * 占位：卸载资源。
	 */
	public void unload(String pathOrId) {
	}
}
