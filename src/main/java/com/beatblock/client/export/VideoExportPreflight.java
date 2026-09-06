package com.beatblock.client.export;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.playback.CompilePolicy;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.TimelineCompilationException;
import com.beatblock.timeline.playback.TimelineCompiler;
import com.beatblock.timeline.playback.TimelineDiagnostic;
import com.beatblock.timeline.playback.TimelineDiagnosticSeverity;
import com.beatblock.timeline.playback.TimelineValidationReport;
import com.beatblock.timeline.playback.TimelineValidator;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.video.VideoExportPresets;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 导出 Preflight：以 {@link TimelineCompiler}{@code STRICT} 为 acceptance gate，
 * 并附带 Creator 导出环境检查。Ready 等价于可启动导出（同一套 compile 规则）。
 */
public final class VideoExportPreflight {

	private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
	private static final Set<String> EXPORT_ELEVATED_WARNINGS = Set.of(
		TimelineValidator.RULE_MISSING_STAGE_OBJECT,
		TimelineValidator.RULE_UNBOUND_TARGET,
		TimelineValidator.RULE_MISSING_BUILD_LAYER,
		TimelineValidator.RULE_MISSING_CAMERA_SUBJECT,
		TimelineValidator.RULE_MISSING_CAMERA_LOOK_AT,
		TimelineValidator.RULE_MISSING_CAMERA_BUILD_LAYER,
		TimelineValidator.RULE_INVALID_CAMERA_FRAMING
	);

	private VideoExportPreflight() {}

	public record Finding(String id, boolean blocksExport, String message) {}

	/**
	 * @param canExport              无阻塞项时可导出（output collision 不算阻塞，需 Replace 确认）
	 * @param readyForStrictExport   STRICT compile 成功（无 ERROR）
	 * @param blockers               Cannot Export 明细
	 * @param notices                非阻塞提示（collision、disk estimate 等）
	 * @param estimatedSizeMb        磁盘体积估算；不可用时为 {@code null}
	 * @param compiledSnapshot       Ready 时可交给 Export 复用的冻结快照；否则 {@code null}
	 */
	public record Status(
		boolean canExport,
		boolean readyForStrictExport,
		int errorCount,
		int warningCount,
		List<Finding> blockers,
		List<Finding> notices,
		@Nullable Double estimatedSizeMb,
		@Nullable CompiledTimelineSnapshot compiledSnapshot
	) {
		public Status {
			blockers = List.copyOf(blockers != null ? blockers : List.of());
			notices = List.copyOf(notices != null ? notices : List.of());
			if (!canExport) {
				compiledSnapshot = null;
			}
		}

		public String summary() {
			if (canExport) {
				if (warningCount > 0 || !notices.isEmpty()) {
					return localize("beatblock.export.preflight.ready_with_warnings",
						"Ready — %d notice(s)", warningCount + notices.size());
				}
				return localize("beatblock.export.preflight.ready", "Ready");
			}
			return localize("beatblock.export.preflight.cannot_export", "Cannot Export");
		}

		public List<String> blockerMessages() {
			List<String> out = new ArrayList<>(blockers.size());
			for (Finding finding : blockers) {
				out.add(finding.message());
			}
			return List.copyOf(out);
		}
	}

	/** 导出对话框当前参数快照。 */
	public record Request(
		@Nullable Timeline timeline,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layers,
		boolean ffmpegAvailable,
		@Nullable String outputPath,
		double startSeconds,
		double endSeconds,
		int width,
		int height,
		int fps,
		boolean includeAudio,
		boolean audioSourceAvailable
	) {
		public String cacheKey() {
			return String.join("|",
				String.valueOf(System.identityHashCode(timeline)),
				String.valueOf(ffmpegAvailable),
				outputPath != null ? outputPath.trim() : "",
				String.format(Locale.ROOT, "%.3f:%.3f", startSeconds, endSeconds),
				width + "x" + height + "@" + fps,
				String.valueOf(includeAudio),
				String.valueOf(audioSourceAvailable)
			);
		}
	}

	/** @deprecated 仅校验时间线；请用 {@link #evaluate(Request)}。 */
	@Deprecated
	public static Status evaluate(
		@Nullable Timeline timeline,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layers
	) {
		return evaluate(new Request(
			timeline, engine, layers,
			true, null, 0.0, 1.0, 1920, 1080, 60,
			false, true
		));
	}

	/**
	 * 仅从已有 validation report 构造状态（不跑 compile）。
	 * 不要求 {@code compiledSnapshot}；不可用于 Export Ready gate。
	 */
	public static Status fromReport(TimelineValidationReport report) {
		return finishStatus(report, null, false, true, null, 0, 1, 1920, 1080, 60, false, true);
	}

	public static Status evaluate(Request request) {
		Objects.requireNonNull(request, "request");
		CompiledTimelineSnapshot snapshot = null;
		TimelineValidationReport report;
		try {
			var result = TimelineCompiler.compile(
				request.timeline(),
				request.engine(),
				request.layers(),
				CompilePolicy.STRICT
			);
			snapshot = result.snapshot();
			report = snapshot != null ? snapshot.validationReport() : null;
			if (report == null) {
				report = TimelineValidator.validate(request.timeline(), request.engine(), request.layers());
			}
		} catch (TimelineCompilationException ex) {
			report = ex.report();
			if (report == null) {
				report = TimelineValidator.validate(request.timeline(), request.engine(), request.layers());
			}
			snapshot = null;
		} catch (RuntimeException ex) {
			report = TimelineValidator.validate(request.timeline(), request.engine(), request.layers());
			snapshot = null;
			Status partial = finishStatus(
				report,
				null,
				true,
				request.ffmpegAvailable(),
				request.outputPath(),
				request.startSeconds(),
				request.endSeconds(),
				request.width(),
				request.height(),
				request.fps(),
				request.includeAudio(),
				request.audioSourceAvailable()
			);
			List<Finding> blockers = new ArrayList<>(partial.blockers());
			blockers.add(0, new Finding("strict_compile_failed", true,
				localize("beatblock.export.preflight.issue.compile_failed",
					"STRICT compile failed: %s",
					ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())));
			return new Status(
				false,
				false,
				partial.errorCount(),
				partial.warningCount(),
				dedupe(blockers),
				partial.notices(),
				partial.estimatedSizeMb(),
				null
			);
		}
		return finishStatus(
			report,
			snapshot,
			true,
			request.ffmpegAvailable(),
			request.outputPath(),
			request.startSeconds(),
			request.endSeconds(),
			request.width(),
			request.height(),
			request.fps(),
			request.includeAudio(),
			request.audioSourceAvailable()
		);
	}

	/**
	 * Preflight 快照是否仍对应当前 Timeline（以 stageEventsGeneration 为 stale 判据）。
	 */
	public static boolean isSnapshotCurrent(
		@Nullable CompiledTimelineSnapshot snapshot,
		@Nullable Timeline timeline
	) {
		if (snapshot == null || timeline == null) {
			return false;
		}
		return snapshot.sourceGeneration() == timeline.getStageEventsGeneration();
	}

	private static Status finishStatus(
		TimelineValidationReport report,
		@Nullable CompiledTimelineSnapshot compiledSnapshot,
		boolean requireCompiledSnapshot,
		boolean ffmpegAvailable,
		@Nullable String outputPath,
		double startSeconds,
		double endSeconds,
		int width,
		int height,
		int fps,
		boolean includeAudio,
		boolean audioSourceAvailable
	) {
		List<Finding> blockers = new ArrayList<>();
		List<Finding> notices = new ArrayList<>();
		int errors = report != null ? report.errorCount() : 1;
		int warnings = report != null ? report.warningCount() : 0;
		boolean readyStrict = compiledSnapshot != null && report != null && !report.hasErrors();

		if (report != null) {
			for (TimelineDiagnostic diagnostic : report.problems()) {
				boolean blocks = diagnostic.severity() == TimelineDiagnosticSeverity.ERROR
					|| EXPORT_ELEVATED_WARNINGS.contains(diagnostic.ruleId())
					|| (includeAudio && isAudioAssetRule(diagnostic.ruleId()));
				Finding finding = new Finding(
					diagnostic.ruleId(),
					blocks,
					formatTimelineFinding(diagnostic)
				);
				if (blocks) {
					blockers.add(finding);
				} else {
					notices.add(finding);
				}
			}
		} else {
			blockers.add(new Finding("null_report", true,
				localize("beatblock.export.preflight.issue.timeline_unavailable",
					"Timeline validation unavailable")));
		}

		if (requireCompiledSnapshot) {
			if (compiledSnapshot == null && report != null && report.hasErrors()) {
				// STRICT compile 已失败；若报告未转化为 blocker（极端空报告），补一条
				if (blockers.isEmpty()) {
					blockers.add(new Finding("strict_compile_failed", true,
						localize("beatblock.export.preflight.issue.compile_blocked",
							"STRICT compile failed with %d error(s)", report.errorCount())));
				}
			} else if (compiledSnapshot == null && (report == null || !report.hasErrors())) {
				blockers.add(new Finding("strict_compile_failed", true,
					localize("beatblock.export.preflight.issue.compile_unavailable",
						"STRICT compile did not produce an export snapshot")));
			}
		}

		if (!ffmpegAvailable) {
			blockers.add(new Finding("ffmpeg_missing", true,
				localize("beatblock.export.preflight.issue.ffmpeg_missing", "FFmpeg not found")));
		}

		if (includeAudio && !audioSourceAvailable) {
			blockers.add(new Finding("audio_unavailable", true,
				localize("beatblock.export.preflight.issue.audio_unavailable",
					"Include Audio is on, but no audio source is available")));
		}

		if (!(Double.isFinite(startSeconds) && Double.isFinite(endSeconds) && endSeconds > startSeconds)) {
			blockers.add(new Finding("invalid_range", true,
				localize("beatblock.export.preflight.issue.invalid_range",
					"Export time range is invalid (end must be after start)")));
		}

		if (width < 0 || height < 0 || fps <= 0) {
			blockers.add(new Finding("invalid_resolution", true,
				localize("beatblock.export.preflight.issue.invalid_resolution",
					"Resolution or frame rate is invalid")));
		} else if (width > 0 && height > 0 && ((width & 1) != 0 || (height & 1) != 0)) {
			blockers.add(new Finding("odd_resolution", true,
				localize("beatblock.export.preflight.issue.odd_resolution",
					"Resolution %dx%d must be even for H.264", width, height)));
		}

		Path output = null;
		if (outputPath == null) {
			// 时间线-only / 未绑定输出路径的调用：跳过输出环境检查
		} else {
			String trimmedOutput = outputPath.trim();
			if (trimmedOutput.isBlank()) {
				blockers.add(new Finding("output_empty", true,
					localize("beatblock.export.preflight.issue.output_empty", "Output path is empty")));
			} else {
				try {
					output = Path.of(trimmedOutput).toAbsolutePath().normalize();
				} catch (RuntimeException ex) {
					blockers.add(new Finding("output_invalid", true,
						localize("beatblock.export.preflight.issue.output_invalid",
							"Output path is invalid: %s", ex.getMessage())));
				}
			}
		}

		Double estimateMb = null;
		if (output != null) {
			Path parent = output.getParent();
			if (parent == null) {
				blockers.add(new Finding("output_no_parent", true,
					localize("beatblock.export.preflight.issue.output_no_parent",
						"Output path has no parent directory")));
			} else {
				try {
					Files.createDirectories(parent);
					if (!Files.isWritable(parent)) {
						blockers.add(new Finding("output_not_writable", true,
							localize("beatblock.export.preflight.issue.output_not_writable",
								"Output directory is not writable: %s", parent)));
					}
				} catch (IOException ex) {
					blockers.add(new Finding("output_not_writable", true,
						localize("beatblock.export.preflight.issue.output_not_writable",
							"Output directory is not writable: %s", parent)));
				}
			}

			if (Files.isRegularFile(output)) {
				notices.add(new Finding("output_collision", false,
					localize("beatblock.export.preflight.issue.output_collision",
						"Output file already exists: %s (Replace confirmation required)",
						output.getFileName())));
			}

			int estW = width > 0 ? width : 1920;
			int estH = height > 0 ? height : 1080;
			double duration = Math.max(0.0, endSeconds - startSeconds);
			if (duration > 0 && fps > 0) {
				estimateMb = VideoExportPresets.estimateFileSize(estW, estH, fps, duration);
				notices.add(new Finding("disk_estimate", false,
					localize("beatblock.export.preflight.issue.disk_estimate",
						"Estimated size: ~%.1f MB", estimateMb)));
				if (parent != null) {
					try {
						long usable = Files.getFileStore(parent).getUsableSpace();
						long needed = (long) Math.ceil(estimateMb * 1024.0 * 1024.0 * 1.25);
						if (needed > 0 && usable >= 0 && needed > usable) {
							blockers.add(new Finding("disk_insufficient", true,
								localize("beatblock.export.preflight.issue.disk_insufficient",
									"Not enough free disk space (need ~%.1f MB, free %.1f MB)",
									estimateMb, usable / (1024.0 * 1024.0))));
						}
					} catch (IOException ignored) {
						// 无法探测可用空间时跳过
					}
				}
			}
		}

		blockers = dedupe(blockers);
		notices = dedupe(notices);
		boolean canExport = blockers.isEmpty()
			&& (!requireCompiledSnapshot || (readyStrict && compiledSnapshot != null));
		return new Status(
			canExport,
			readyStrict,
			errors,
			warnings,
			blockers,
			notices,
			estimateMb,
			(canExport && requireCompiledSnapshot) ? compiledSnapshot : null
		);
	}

	private static List<Finding> dedupe(List<Finding> findings) {
		LinkedHashSet<String> seen = new LinkedHashSet<>();
		List<Finding> out = new ArrayList<>();
		for (Finding finding : findings) {
			String key = finding.id() + "\0" + finding.message();
			if (seen.add(key)) {
				out.add(finding);
			}
		}
		return out;
	}

	private static boolean isAudioAssetRule(String ruleId) {
		return TimelineValidator.RULE_AUDIO_FILE_MISSING.equals(ruleId)
			|| TimelineValidator.RULE_MISSING_AUDIO.equals(ruleId);
	}

	static String formatTimelineFinding(TimelineDiagnostic diagnostic) {
		String rule = diagnostic.ruleId();
		String quoted = firstQuoted(diagnostic.message());
		return switch (rule) {
			case TimelineValidator.RULE_MISSING_STAGE_OBJECT ->
				localize("beatblock.export.preflight.issue.missing_stage",
					"Missing StageObject \"%s\"", quoted != null ? quoted : "?");
			case TimelineValidator.RULE_UNBOUND_TARGET ->
				localize("beatblock.export.preflight.issue.unbound_target",
					"Stage event has no StageObject target");
			case TimelineValidator.RULE_MISSING_BUILD_LAYER ->
				localize("beatblock.export.preflight.issue.missing_layer",
					"Missing BuildLayer \"%s\"", quoted != null ? quoted : "?");
			case TimelineValidator.RULE_MISSING_CAMERA_SUBJECT ->
				localize("beatblock.export.preflight.issue.missing_camera_subject",
					"Camera keyframe missing subject");
			case TimelineValidator.RULE_MISSING_CAMERA_LOOK_AT ->
				localize("beatblock.export.preflight.issue.missing_camera_look_at",
					"Camera keyframe missing look-at");
			case TimelineValidator.RULE_MISSING_CAMERA_BUILD_LAYER ->
				localize("beatblock.export.preflight.issue.missing_camera_layer",
					"Camera keyframe missing BuildLayer");
			case TimelineValidator.RULE_INVALID_CAMERA_FRAMING ->
				localize("beatblock.export.preflight.issue.invalid_camera_framing",
					"Camera framing is invalid");
			case TimelineValidator.RULE_MISSING_ANIMATION_PRESET ->
				localize("beatblock.export.preflight.issue.missing_preset",
					"Missing animation preset: %s",
					quoted != null ? quoted : diagnostic.message());
			case TimelineValidator.RULE_INVALID_GLOBAL_PAYLOAD ->
				localize("beatblock.export.preflight.issue.invalid_vfx",
					"Invalid Global/VFX payload: %s", diagnostic.message());
			case TimelineValidator.RULE_AUDIO_FILE_MISSING, TimelineValidator.RULE_MISSING_AUDIO ->
				localize("beatblock.export.preflight.issue.timeline_audio",
					"Timeline audio asset is missing or unreachable");
			default -> diagnostic.message();
		};
	}

	/** 测试环境可能无 Minecraft I18n：未翻译时回退到英文模板。 */
	static String localize(String key, String englishFallback, Object... args) {
		String translated = args.length == 0 ? BBTexts.get(key) : BBTexts.get(key, args);
		if (translated == null || translated.isBlank() || translated.equals(key) || translated.startsWith("beatblock.")) {
			return args.length == 0
				? englishFallback
				: String.format(Locale.ROOT, englishFallback, args);
		}
		return translated;
	}

	private static @Nullable String firstQuoted(String message) {
		if (message == null) {
			return null;
		}
		Matcher matcher = QUOTED.matcher(message);
		return matcher.find() ? matcher.group(1) : null;
	}
}
