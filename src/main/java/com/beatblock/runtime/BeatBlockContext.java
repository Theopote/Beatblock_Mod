package com.beatblock.runtime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.audio.AudioConversionService;
import com.beatblock.audio.AudioLoader;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.audio.StemMixer;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.stage.StageManager;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.video.VideoExportService;

/**
 * 运行时核心服务容器：构造器注入入口，替代原 {@link com.beatblock.BeatBlock} 静态字段访问。
 * 生产环境在 {@link com.beatblock.BeatBlock#onInitialize()} 中构建；测试可通过 {@link Builder} 注入 mock。
 */
public final class BeatBlockContext {

	private final @Nullable AudioLoader audioLoader;
	private final @Nullable MusicPlayer musicPlayer;
	private final @Nullable StemMixer stemMixer;
	private final @Nullable StageManager stageManager;
	private final @Nullable Timeline timeline;
	private final @Nullable TimelineEditor timelineEditor;
	private final @Nullable BlockAnimationEngine blockAnimationEngine;
	private final @Nullable AudioAnalysisEngine audioAnalysisEngine;
	private final @Nullable AudioAnalysisService externalAudioAnalyzer;
	private final @Nullable AudioConversionService audioConversionService;
	private final @Nullable VideoExportService videoExportService;

	public BeatBlockContext(
		@Nullable AudioLoader audioLoader,
		@Nullable MusicPlayer musicPlayer,
		@Nullable StemMixer stemMixer,
		@Nullable StageManager stageManager,
		@Nullable Timeline timeline,
		@Nullable TimelineEditor timelineEditor,
		@Nullable BlockAnimationEngine blockAnimationEngine,
		@Nullable AudioAnalysisEngine audioAnalysisEngine,
		@Nullable AudioAnalysisService externalAudioAnalyzer,
		@Nullable AudioConversionService audioConversionService,
		@Nullable VideoExportService videoExportService
	) {
		this.audioLoader = audioLoader;
		this.musicPlayer = musicPlayer;
		this.stemMixer = stemMixer;
		this.stageManager = stageManager;
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.blockAnimationEngine = blockAnimationEngine;
		this.audioAnalysisEngine = audioAnalysisEngine;
		this.externalAudioAnalyzer = externalAudioAnalyzer;
		this.audioConversionService = audioConversionService;
		this.videoExportService = videoExportService;
	}

	public static Builder builder() {
		return new Builder();
	}

	public @Nullable AudioLoader audioLoader() {
		return audioLoader;
	}

	public @Nullable MusicPlayer musicPlayer() {
		return musicPlayer;
	}

	public @Nullable StemMixer stemMixer() {
		return stemMixer;
	}

	public @Nullable StageManager stageManager() {
		return stageManager;
	}

	public @Nullable Timeline timeline() {
		return timeline;
	}

	public @Nullable TimelineEditor timelineEditor() {
		return timelineEditor;
	}

	public @Nullable BlockAnimationEngine blockAnimationEngine() {
		return blockAnimationEngine;
	}

	public @Nullable AudioAnalysisEngine audioAnalysisEngine() {
		return audioAnalysisEngine;
	}

	public @Nullable AudioAnalysisService externalAudioAnalyzer() {
		return externalAudioAnalyzer;
	}

	public @Nullable AudioConversionService audioConversionService() {
		return audioConversionService;
	}

	public @Nullable IAudioPlayer activeAudioPlayer() {
		if (usesStemPlayback()) {
			return stemMixer;
		}
		return musicPlayer;
	}

	/** Demucs 分轨已加载时，时间轴应只驱动 {@link StemMixer}，不再播放全曲混音。 */
	public boolean usesStemPlayback() {
		return stemMixer != null && stemMixer.hasStems();
	}

	public void pauseFullMixIfStemPlayback() {
		if (!usesStemPlayback() || musicPlayer == null) {
			return;
		}
		if (musicPlayer.isPlaying()) {
			musicPlayer.pause();
		}
	}

	public double playbackTimeSeconds() {
		if (usesStemPlayback() && stemMixer != null) {
			return stemMixer.getCurrentTimeSeconds();
		}
		return musicPlayer != null ? musicPlayer.getCurrentTimeSeconds() : 0.0;
	}

	public @Nullable VideoExportService videoExportService() {
		return videoExportService;
	}

	public @Nullable CommandManager commandManager() {
		return timelineEditor != null ? timelineEditor.getCommandManager() : null;
	}

	public @Nullable BuildLayerManager buildLayerManager() {
		return blockAnimationEngine != null ? blockAnimationEngine.getBuildLayerManager() : null;
	}

	public static final class Builder {
		private @Nullable AudioLoader audioLoader;
		private @Nullable MusicPlayer musicPlayer;
		private @Nullable StemMixer stemMixer;
		private @Nullable StageManager stageManager;
		private @Nullable Timeline timeline;
		private @Nullable TimelineEditor timelineEditor;
		private @Nullable BlockAnimationEngine blockAnimationEngine;
		private @Nullable AudioAnalysisEngine audioAnalysisEngine;
		private @Nullable AudioAnalysisService externalAudioAnalyzer;
		private @Nullable AudioConversionService audioConversionService;
		private @Nullable VideoExportService videoExportService;

		public Builder audioLoader(AudioLoader audioLoader) {
			this.audioLoader = audioLoader;
			return this;
		}

		public Builder musicPlayer(MusicPlayer musicPlayer) {
			this.musicPlayer = musicPlayer;
			return this;
		}

		public Builder stemMixer(StemMixer stemMixer) {
			this.stemMixer = stemMixer;
			return this;
		}

		public Builder stageManager(StageManager stageManager) {
			this.stageManager = stageManager;
			return this;
		}

		public Builder timeline(Timeline timeline) {
			this.timeline = timeline;
			return this;
		}

		public Builder timelineEditor(TimelineEditor timelineEditor) {
			this.timelineEditor = timelineEditor;
			return this;
		}

		public Builder blockAnimationEngine(BlockAnimationEngine blockAnimationEngine) {
			this.blockAnimationEngine = blockAnimationEngine;
			return this;
		}

		public Builder audioAnalysisEngine(AudioAnalysisEngine audioAnalysisEngine) {
			this.audioAnalysisEngine = audioAnalysisEngine;
			return this;
		}

		public Builder externalAudioAnalyzer(AudioAnalysisService externalAudioAnalyzer) {
			this.externalAudioAnalyzer = externalAudioAnalyzer;
			return this;
		}

		public Builder audioConversionService(AudioConversionService audioConversionService) {
			this.audioConversionService = audioConversionService;
			return this;
		}

		public Builder videoExportService(VideoExportService videoExportService) {
			this.videoExportService = videoExportService;
			return this;
		}

		public BeatBlockContext build() {
			return new BeatBlockContext(
				audioLoader,
				musicPlayer,
				stemMixer,
				stageManager,
				timeline,
				timelineEditor,
				blockAnimationEngine,
				audioAnalysisEngine,
				externalAudioAnalyzer,
				audioConversionService,
				videoExportService
			);
		}
	}
}
