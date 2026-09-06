package com.beatblock.ui.presenter;

import com.beatblock.audio.ffmpeg.FfmpegLocator;
import com.beatblock.audio.ffmpeg.FfmpegService;
import com.beatblock.audio.ffmpeg.FfmpegVideoEncoder;
import com.beatblock.client.export.VideoExportPreflight;
import com.beatblock.client.export.VideoExportSummary;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.PerformanceCheckController;
import com.beatblock.timeline.playback.TimelineCompiler;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.video.VideoExportAudioSource;
import com.beatblock.video.VideoExportPreferences;
import com.beatblock.video.VideoExportPresets;
import com.beatblock.video.VideoExportService;
import com.beatblock.video.VideoExportSettings;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/** 视频导出弹窗业务逻辑。 */
public final class VideoExportPanelPresenter {

	public record FfmpegStatus(boolean available, @Nullable String executablePath, String searchSummary) {}

	public record ExportDialogState(
		String defaultOutputPath,
		double timelineDurationSeconds,
		double defaultStartSeconds,
		double defaultEndSeconds,
		FfmpegStatus ffmpegStatus,
		boolean canExport,
		@Nullable String blockedReason,
		boolean audioSourceAvailable,
		@Nullable String audioSourcePath,
		VideoExportPreflight.Status preflight,
		VideoExportSummary.Snapshot summary
	) {}

	private static final int[][] RESOLUTION_PRESETS = {
		{ 0, 0 },
		{ 1280, 720 },
		{ 1920, 1080 },
		{ 2560, 1440 }
	};
	private static final int[] FPS_PRESETS = { 24, 30, 60 };

	private final Supplier<BeatBlockContext> contextSource;
	private final Supplier<VideoExportService> exportService;
	private final Supplier<FfmpegStatus> ffmpegStatusProbe;
	private VideoExportPreflight.@Nullable Status cachedPreflight;
	private @Nullable String cachedPreflightKey;

	public VideoExportPanelPresenter(
		Supplier<BeatBlockContext> contextSource,
		Supplier<VideoExportService> exportService
	) {
		this(contextSource, exportService, VideoExportPanelPresenter::probeFfmpeg);
	}

	VideoExportPanelPresenter(
		Supplier<BeatBlockContext> contextSource,
		Supplier<VideoExportService> exportService,
		Supplier<FfmpegStatus> ffmpegStatusProbe
	) {
		this.contextSource = contextSource != null ? contextSource : () -> null;
		this.exportService = exportService;
		this.ffmpegStatusProbe = ffmpegStatusProbe != null ? ffmpegStatusProbe : VideoExportPanelPresenter::probeFfmpeg;
	}

	public static int[][] resolutionPresets() {
		return RESOLUTION_PRESETS;
	}

	public static int[] fpsPresets() {
		return FPS_PRESETS;
	}

	/** 打开对话框时用默认参数做一次完整 Preflight。 */
	public VideoExportPreflight.Status refreshPreflight() {
		BeatBlockContext ctx = contextSource.get();
		Timeline timeline = ctx != null ? ctx.timeline() : null;
		double duration = timeline != null ? Math.max(0.0, timeline.getDurationSeconds()) : 60.0;
		return refreshPreflight(buildRequest(
			defaultOutputPath(timeline),
			0.0,
			duration > 0 ? duration : 60.0,
			0,
			0,
			false
		));
	}

	public VideoExportPreflight.Status refreshPreflight(VideoExportPreflight.Request request) {
		cachedPreflight = VideoExportPreflight.evaluate(request);
		cachedPreflightKey = request.cacheKey();
		return cachedPreflight;
	}

	/** 按当前 UI 参数取 Preflight（参数变化时自动重算）。 */
	public VideoExportPreflight.Status preflightFor(VideoExportPreflight.Request request) {
		if (cachedPreflight == null || cachedPreflightKey == null || !cachedPreflightKey.equals(request.cacheKey())) {
			return refreshPreflight(request);
		}
		return cachedPreflight;
	}

	public VideoExportPreflight.Request buildRequest(
		String rawOutputPath,
		double startSeconds,
		double endSeconds,
		int platformPresetIndex,
		int resolutionPresetIndex,
		int fpsPresetIndex,
		boolean includeAudio
	) {
		BeatBlockContext ctx = contextSource.get();
		FfmpegStatus ffmpeg = ffmpegStatusProbe.get();
		int[] size = resolveExportSize(platformPresetIndex, resolutionPresetIndex);
		int fps = resolveExportFps(platformPresetIndex, fpsPresetIndex);
		return new VideoExportPreflight.Request(
			ctx != null ? ctx.timeline() : null,
			ctx != null ? ctx.blockAnimationEngine() : null,
			ctx != null ? ctx.buildLayerManager() : null,
			ffmpeg.available(),
			rawOutputPath,
			startSeconds,
			endSeconds,
			size[0],
			size[1],
			fps,
			includeAudio,
			VideoExportAudioSource.isAvailable(ctx)
		);
	}

	private VideoExportPreflight.Request buildRequest(
		String rawOutputPath,
		double startSeconds,
		double endSeconds,
		int resolutionPresetIndex,
		int fpsPresetIndex,
		boolean includeAudio
	) {
		return buildRequest(
			rawOutputPath,
			startSeconds,
			endSeconds,
			VideoExportPreferences.platformPresetIndex(),
			resolutionPresetIndex,
			fpsPresetIndex,
			includeAudio
		);
	}

	/** 打开既有 Performance Check 问题列表（不启动播放）。 */
	public void openPreflightProblems() {
		BeatBlockContext ctx = contextSource.get();
		PerformanceCheckController.checkOnly(
			ctx != null ? ctx.timeline() : null,
			ctx != null ? ctx.blockAnimationEngine() : null,
			ctx != null ? ctx.buildLayerManager() : null
		);
	}

	public ExportDialogState dialogState() {
		return dialogState(
			null,
			Double.NaN,
			Double.NaN,
			VideoExportPreferences.platformPresetIndex(),
			VideoExportPreferences.resolutionPresetIndex(),
			VideoExportPreferences.fpsPresetIndex(),
			VideoExportPreferences.includeAudio()
		);
	}

	public ExportDialogState dialogState(
		@Nullable String rawOutputPath,
		double startSeconds,
		double endSeconds,
		int platformPresetIndex,
		int resolutionPresetIndex,
		int fpsPresetIndex,
		boolean includeAudio
	) {
		BeatBlockContext ctx = contextSource.get();
		Timeline timeline = ctx != null ? ctx.timeline() : null;
		double duration = timeline != null ? Math.max(0.0, timeline.getDurationSeconds()) : 0.0;
		double start = Double.isFinite(startSeconds) ? startSeconds : 0.0;
		double end = Double.isFinite(endSeconds) ? endSeconds : (duration > 0 ? duration : 60.0);
		String output = rawOutputPath != null ? rawOutputPath : defaultOutputPath(timeline);
		FfmpegStatus ffmpegStatus = ffmpegStatusProbe.get();
		VideoExportPreflight.Request request = buildRequest(
			output, start, end, platformPresetIndex, resolutionPresetIndex, fpsPresetIndex, includeAudio);
		VideoExportPreflight.Status preflight = preflightFor(request);
		String blockedReason = exportBlockedReason(duration, ffmpegStatus, preflight);
		String audioPath = VideoExportAudioSource.displayPath(ctx);
		int[] size = resolveExportSize(platformPresetIndex, resolutionPresetIndex);
		int fps = resolveExportFps(platformPresetIndex, fpsPresetIndex);
		VideoExportPresets.PresetType preset = VideoExportPresets.presetAtIndex(platformPresetIndex);
		String pictureLabel = preset == VideoExportPresets.PresetType.CUSTOM
			? null
			: BBTexts.get("beatblock.export.summary.picture_preset",
				BBTexts.get(VideoExportPresets.labelKey(preset)),
				preset.getWidth(),
				preset.getHeight(),
				preset.getFps());
		VideoExportSummary.Snapshot summary = VideoExportSummary.build(
			timeline,
			start,
			end,
			size[0],
			size[1],
			fps,
			includeAudio,
			audioPath,
			output,
			pictureLabel
		);
		return new ExportDialogState(
			defaultOutputPath(timeline),
			duration,
			0.0,
			duration > 0 ? duration : 60.0,
			ffmpegStatus,
			blockedReason == null && preflight.canExport(),
			blockedReason,
			audioPath != null,
			audioPath,
			preflight,
			summary
		);
	}

	public PresenterResult startExport(
		String rawOutputPath,
		int platformPresetIndex,
		int resolutionPresetIndex,
		int fpsPresetIndex,
		double startSeconds,
		double endSeconds,
		boolean includeAudio
	) {
		return startExport(
			rawOutputPath,
			platformPresetIndex,
			resolutionPresetIndex,
			fpsPresetIndex,
			startSeconds,
			endSeconds,
			includeAudio,
			false
		);
	}

	/**
	 * @param replaceConfirmed 目标文件已存在时，必须为 {@code true} 才允许覆盖（Creator policy）。
	 */
	public PresenterResult startExport(
		String rawOutputPath,
		int platformPresetIndex,
		int resolutionPresetIndex,
		int fpsPresetIndex,
		double startSeconds,
		double endSeconds,
		boolean includeAudio,
		boolean replaceConfirmed
	) {
		VideoExportPreflight.Request request = buildRequest(
			rawOutputPath, startSeconds, endSeconds,
			platformPresetIndex, resolutionPresetIndex, fpsPresetIndex, includeAudio);
		VideoExportPreflight.Status preflight = refreshPreflight(request);
		if (!preflight.canExport()) {
			String first = preflight.blockers().isEmpty()
				? BBTexts.get("beatblock.export.blocked.generic")
				: preflight.blockers().getFirst().message();
			return PresenterResult.failure(first);
		}

		String output = rawOutputPath != null ? rawOutputPath.trim() : "";
		if (output.isBlank()) {
			return PresenterResult.failure(BBTexts.get("beatblock.export.error.output_empty"));
		}
		Path outputPath = Path.of(output).toAbsolutePath().normalize();
		if (requiresReplaceConfirm(outputPath) && !replaceConfirmed) {
			return PresenterResult.failure(BBTexts.get("beatblock.export.replace_confirm_required"));
		}

		VideoExportPresets.PresetType preset = VideoExportPresets.presetAtIndex(platformPresetIndex);
		Path parent = outputPath.getParent();
		if (parent != null) {
			VideoExportPreferences.setLastOutputDirectory(parent.toString());
		}
		VideoExportPreferences.setPlatformPresetIndex(platformPresetIndex);
		VideoExportPreferences.setIncludeAudio(includeAudio);

		VideoExportSettings settings;
		if (preset == VideoExportPresets.PresetType.CUSTOM) {
			int presetIndex = Math.max(0, Math.min(resolutionPresetIndex, RESOLUTION_PRESETS.length - 1));
			int fpsIdx = Math.max(0, Math.min(fpsPresetIndex, FPS_PRESETS.length - 1));
			int[] resolution = RESOLUTION_PRESETS[presetIndex];
			int fps = FPS_PRESETS[fpsIdx];
			VideoExportPreferences.setResolutionPresetIndex(presetIndex);
			VideoExportPreferences.setFpsPresetIndex(fpsIdx);
			settings = new VideoExportSettings(
				outputPath,
				resolution[0],
				resolution[1],
				fps,
				startSeconds,
				endSeconds,
				includeAudio
			);
		} else {
			settings = VideoExportPresets.fromPreset(
				preset,
				outputPath,
				startSeconds,
				endSeconds,
				includeAudio
			);
		}

		VideoExportService service = exportService.get();
		if (service == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.export.error.service_unavailable"));
		}
		if (service.isExporting()) {
			return PresenterResult.failure(BBTexts.get("beatblock.export.error.already_running"));
		}
		BeatBlockContext ctx = contextSource.get();
		CompiledTimelineSnapshot program = preflight.compiledSnapshot();
		if (program == null || !VideoExportPreflight.isSnapshotCurrent(program, ctx != null ? ctx.timeline() : null)) {
			try {
				program = TimelineCompiler.compile(
					ctx != null ? ctx.timeline() : null,
					ctx != null ? ctx.blockAnimationEngine() : null,
					ctx != null ? ctx.buildLayerManager() : null
				);
			} catch (RuntimeException ex) {
				return PresenterResult.failure(BBTexts.get(
					"beatblock.export.error.timeline_compile",
					ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
			}
		}
		if (!service.startExport(settings, program)) {
			return PresenterResult.failure(BBTexts.get("beatblock.export.error.start_failed"));
		}
		return PresenterResult.success(BBTexts.get("beatblock.export.started"));
	}

	/** 用户指定的最终输出路径已存在常规文件时，需要显式 Replace 确认。 */
	public boolean requiresReplaceConfirm(String rawOutputPath) {
		String output = rawOutputPath != null ? rawOutputPath.trim() : "";
		if (output.isBlank()) {
			return false;
		}
		return requiresReplaceConfirm(Path.of(output).toAbsolutePath().normalize());
	}

	static boolean requiresReplaceConfirm(Path outputPath) {
		return outputPath != null && Files.isRegularFile(outputPath);
	}

	public void cancelExport() {
		VideoExportService service = exportService.get();
		if (service != null) {
			service.cancelExport();
		}
	}

	public @Nullable VideoExportService activeService() {
		return exportService.get();
	}

	private int[] resolveExportSize(int platformPresetIndex, int resolutionPresetIndex) {
		VideoExportPresets.PresetType preset = VideoExportPresets.presetAtIndex(platformPresetIndex);
		if (preset == VideoExportPresets.PresetType.CUSTOM) {
			int idx = Math.max(0, Math.min(resolutionPresetIndex, RESOLUTION_PRESETS.length - 1));
			return RESOLUTION_PRESETS[idx];
		}
		return new int[] { preset.getWidth(), preset.getHeight() };
	}

	private int resolveExportFps(int platformPresetIndex, int fpsPresetIndex) {
		VideoExportPresets.PresetType preset = VideoExportPresets.presetAtIndex(platformPresetIndex);
		if (preset == VideoExportPresets.PresetType.CUSTOM) {
			int idx = Math.max(0, Math.min(fpsPresetIndex, FPS_PRESETS.length - 1));
			return FPS_PRESETS[idx];
		}
		return preset.getFps();
	}

	private static FfmpegStatus probeFfmpeg() {
		String executable = FfmpegService.resolveExecutable();
		boolean available = executable != null;
		String summary = String.join("\n", FfmpegLocator.describeSearchLocations(FabricLoader.getInstance().getGameDir()));
		return new FfmpegStatus(available, executable, summary);
	}

	private @Nullable String exportBlockedReason(
		double duration,
		FfmpegStatus ffmpegStatus,
		VideoExportPreflight.Status preflight
	) {
		if (!ffmpegStatus.available()) {
			return BBTexts.get("beatblock.export.error.ffmpeg_missing");
		}
		if (duration <= 0.0) {
			return BBTexts.get("beatblock.export.error.no_timeline_duration");
		}
		VideoExportService service = exportService.get();
		if (service != null && service.isExporting()) {
			return BBTexts.get("beatblock.export.error.already_running");
		}
		if (preflight != null && !preflight.canExport() && !preflight.blockers().isEmpty()) {
			return preflight.blockers().getFirst().message();
		}
		return null;
	}

	private static String defaultOutputPath(@Nullable Timeline timeline) {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path exportsDir = gameDir.resolve("exports");
		String lastDir = VideoExportPreferences.lastOutputDirectory();
		Path baseDir = !lastDir.isBlank() ? Path.of(lastDir) : exportsDir;
		String hint = "";
		if (timeline != null) {
			Object projectPath = timeline.getMetadata("projectPath");
			if (projectPath != null) {
				hint = String.valueOf(projectPath);
			} else {
				Object audioPath = timeline.getMetadata("audioPath");
				if (audioPath != null) {
					hint = String.valueOf(audioPath);
				}
			}
		}
		return baseDir.resolve(FfmpegVideoEncoder.defaultOutputFileName(hint)).toString();
	}
}
