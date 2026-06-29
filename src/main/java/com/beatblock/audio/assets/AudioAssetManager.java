package com.beatblock.audio.assets;

import com.beatblock.BeatBlock;
import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.BeatmapMeta;
import com.beatblock.audio.beatmap.FrequencyBand;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * 音频资产管理器：维护「音频解析」面板的数据源，并串联 AudioAnalysisEngine。
 * 目前为同步实现，后续可引入后台线程。
 */
public final class AudioAssetManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioAssetManager.class);

	private static final AudioAssetManager INSTANCE = new AudioAssetManager();

	public static AudioAssetManager getInstance() {
		return INSTANCE;
	}

	private final List<AudioAsset> assets = new CopyOnWriteArrayList<>();
	private final Map<String, Future<?>> analysisTasks = new ConcurrentHashMap<>();
	private @Nullable AudioAsset currentDragAsset;
	private @Nullable ConversionRequestHandler conversionRequestHandler;
	private long nextQueueTicket = 1L;
	private Supplier<BeatBlockContext> contextSource = BeatBlock::getContext;
	private static final String[] SUPPORTED_AUDIO_EXTENSIONS = {"mp3", "wav", "ogg", "flac"};

	private AudioAssetManager() {
	}

	public void bindContext(Supplier<BeatBlockContext> source) {
		this.contextSource = source != null ? source : BeatBlock::getContext;
	}

	static void resetContextBindingForTests() {
		INSTANCE.bindContext(BeatBlock::getContext);
	}

	private BeatBlockContext ctx() {
		return contextSource.get();
	}

	private @Nullable AudioAnalysisService externalAnalyzer() {
		return ctx().externalAudioAnalyzer();
	}

	public List<AudioAsset> getAssets() {
		return Collections.unmodifiableList(assets);
	}

	/** 当前正在作为拖拽源的资产（由 UI 设置，仅在一次拖拽操作期间有效）。 */
	public @Nullable AudioAsset getCurrentDragAsset() {
		return currentDragAsset;
	}

	public void setCurrentDragAsset(@Nullable AudioAsset asset) {
		this.currentDragAsset = asset;
	}

	public @Nullable AudioAsset findById(@Nullable String id) {
		if (id == null || id.isBlank()) return null;
		for (AudioAsset asset : assets) {
			if (id.equals(asset.getId())) return asset;
		}
		return null;
	}

	public void setConversionRequestHandler(@Nullable ConversionRequestHandler conversionRequestHandler) {
		this.conversionRequestHandler = conversionRequestHandler;
	}

	public boolean requestConvertToMp3(AudioAsset asset) {
		if (asset == null || asset.getPath() == null) return false;
		if (conversionRequestHandler == null) {
			LOGGER.info("BeatBlock AudioAssetManager: 转换请求已记录（尚未接入转换器） file={}", asset.getPath());
			return false;
		}
		conversionRequestHandler.requestConversion(asset, "mp3");
		return true;
	}

	public @Nullable AudioAsset addFromPath(@Nullable String pathStr) {
		if (pathStr == null || pathStr.isEmpty()) return null;
		Path path = normalizeAudioPath(pathStr);
		if (path == null) {
			LOGGER.warn("BeatBlock AudioAssetManager: 无法解析路径: {}", pathStr);
			return null;
		}
		if (!isSupportedAudioFile(path)) {
			LOGGER.warn("BeatBlock AudioAssetManager: 不支持的音频格式: {}", path);
			return null;
		}
		if (!Files.isRegularFile(path)) {
			LOGGER.warn("BeatBlock AudioAssetManager: 非文件或不存在: {}", pathStr);
			return null;
		}
		AudioAsset asset = new AudioAsset(path);
		assets.add(asset);
		return asset;
	}

	public boolean isSupportedAudioPath(String rawPath) {
		Path path = normalizeAudioPath(rawPath);
		return isSupportedAudioFile(path);
	}

	public String getSupportedAudioExtensionsLabel() {
		return "MP3/WAV/OGG/FLAC";
	}

	private @Nullable Path normalizeAudioPath(@Nullable String raw) {
		if (raw == null) return null;
		String v = raw.trim();
		if (v.isEmpty()) return null;

		if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
			v = v.substring(1, v.length() - 1).trim();
		}

		try {
			if (v.startsWith("file:/")) {
				Path p = Paths.get(URI.create(v));
				return p.toAbsolutePath().normalize();
			}
		} catch (Exception e) {
			LOGGER.debug("BeatBlock AudioAssetManager: URI 路径解析失败: {}", e.getMessage());
		}

		try {
			Path p = Paths.get(v);
			if (!p.isAbsolute()) {
				Path gameDir = FabricLoader.getInstance().getGameDir();
				p = gameDir.resolve(p);
			}
			return p.toAbsolutePath().normalize();
		} catch (Exception e) {
			LOGGER.debug("BeatBlock AudioAssetManager: 普通路径解析失败: {}", e.getMessage());
			return null;
		}
	}

	private boolean isSupportedAudioFile(@Nullable Path path) {
		if (path == null || path.getFileName() == null) return false;
		String name = path.getFileName().toString();
		int idx = name.lastIndexOf('.');
		if (idx < 0 || idx == name.length() - 1) return false;
		String ext = name.substring(idx + 1).toLowerCase(Locale.ROOT);
		for (String e : SUPPORTED_AUDIO_EXTENSIONS) {
			if (e.equals(ext)) return true;
		}
		return false;
	}

	public void remove(String id) {
		if (id == null) return;
		AudioAnalysisService service = externalAnalyzer();
		if (service != null) {
			service.cancelAnalysis(id);
		}
		analysisTasks.remove(id);
		assets.removeIf(a -> id.equals(a.getId()));
	}

	/**
	 * 删除当前音频对应的 basic/demucs 缓存后重新分析。
	 *
	 * @return 用户可读结果信息
	 */
	public String clearCacheAndReanalyze(AudioAsset asset) {
		AudioAnalysisService analyzer = externalAnalyzer();
		AudioAnalysisMode mode = analyzer != null && analyzer.isUseDemucs()
			? AudioAnalysisMode.DEMUCS
			: AudioAnalysisMode.BASIC;
		return clearCacheAndReanalyze(asset, mode);
	}

	public String clearCacheAndReanalyze(AudioAsset asset, AudioAnalysisMode mode) {
		if (asset == null || asset.getPath() == null) return BBTexts.get("beatblock.audio.invalid_asset");
		AudioAnalysisService service = externalAnalyzer();
		if (service == null) return BBTexts.get("beatblock.audio.analyzer_uninitialized");

		AudioAnalysisMode resolvedMode = mode != null ? mode : AudioAnalysisMode.BASIC;
		int removed = service.clearAllAnalysisCacheForAudio(asset.getPath());
		startAnalysis(asset, resolvedMode);
		return BBTexts.get("beatblock.audio.cache_cleared_reanalyze", removed, resolvedMode.label());
	}

	public int getQueuePosition(String assetId) {
		if (assetId == null || assetId.isBlank()) return -1;
		AudioAsset target = findById(assetId);
		if (target == null || target.getStatus() != AudioAssetStatus.QUEUED) return -1;
		long targetTicket = target.getQueueTicket();
		if (targetTicket < 0) return -1;

		int position = 1;
		for (AudioAsset asset : assets) {
			if (asset == target) continue;
			if (asset.getStatus() != AudioAssetStatus.QUEUED) continue;
			long ticket = asset.getQueueTicket();
			if (ticket >= 0 && ticket < targetTicket) {
				position++;
			}
		}
		return position;
	}

	public void moveQueueUp(String assetId) {
		moveQueueBy(assetId, -1);
	}

	public void moveQueueDown(String assetId) {
		moveQueueBy(assetId, 1);
	}

	public boolean canMoveQueueUp(String assetId) {
		int pos = getQueuePosition(assetId);
		return pos > 1;
	}

	public boolean canMoveQueueDown(String assetId) {
		int pos = getQueuePosition(assetId);
		return pos > 0 && pos < getQueuedCount();
	}

	public void moveQueueBefore(String movingAssetId, String targetAssetId) {
		if (movingAssetId == null || targetAssetId == null) return;
		if (movingAssetId.isBlank() || targetAssetId.isBlank()) return;
		if (movingAssetId.equals(targetAssetId)) return;

		List<AudioAsset> queued = getQueuedAssetsSorted();
		int movingIdx = -1;
		int targetIdx = -1;
		for (int i = 0; i < queued.size(); i++) {
			String id = queued.get(i).getId();
			if (movingAssetId.equals(id)) movingIdx = i;
			if (targetAssetId.equals(id)) targetIdx = i;
		}
		if (movingIdx < 0 || targetIdx < 0 || movingIdx == targetIdx) return;

		AudioAsset moving = queued.remove(movingIdx);
		if (movingIdx < targetIdx) {
			targetIdx--;
		}
		queued.add(targetIdx, moving);

		long t = 1L;
		for (AudioAsset asset : queued) {
			asset.setQueueTicket(t++);
		}
		nextQueueTicket = t;
	}

	public int getQueuedCount() {
		int count = 0;
		for (AudioAsset asset : assets) {
			if (asset.getStatus() == AudioAssetStatus.QUEUED) count++;
		}
		return count;
	}

	private void moveQueueBy(String assetId, int delta) {
		if (assetId == null || assetId.isBlank() || delta == 0) return;
		List<AudioAsset> queued = getQueuedAssetsSorted();
		int idx = -1;
		for (int i = 0; i < queued.size(); i++) {
			if (assetId.equals(queued.get(i).getId())) {
				idx = i;
				break;
			}
		}
		if (idx < 0) return;
		int newIdx = idx + delta;
		if (newIdx < 0 || newIdx >= queued.size()) return;

		AudioAsset a = queued.get(idx);
		AudioAsset b = queued.get(newIdx);
		long t = a.getQueueTicket();
		a.setQueueTicket(b.getQueueTicket());
		b.setQueueTicket(t);
		normalizeQueueTickets();
	}

	private List<AudioAsset> getQueuedAssetsSorted() {
		List<AudioAsset> queued = new ArrayList<>();
		for (AudioAsset asset : assets) {
			if (asset.getStatus() == AudioAssetStatus.QUEUED && asset.getQueueTicket() >= 0) {
				queued.add(asset);
			}
		}
		queued.sort(Comparator.comparingLong(AudioAsset::getQueueTicket));
		return queued;
	}

	private void normalizeQueueTickets() {
		List<AudioAsset> queued = getQueuedAssetsSorted();
		long t = 1L;
		for (AudioAsset asset : queued) {
			asset.setQueueTicket(t++);
		}
		nextQueueTicket = t;
	}

	/**
	 * 异步执行完整音频解析（Python + librosa），更新 asset 状态与统计信息。
	 */
	public void startAnalysis(AudioAsset asset) {
		AudioAnalysisService analyzer = externalAnalyzer();
		AudioAnalysisMode mode = analyzer != null && analyzer.isUseDemucs()
			? AudioAnalysisMode.DEMUCS
			: AudioAnalysisMode.BASIC;
		startAnalysis(asset, mode);
	}

	public void startAnalysis(AudioAsset asset, AudioAnalysisMode requestedMode) {
		if (asset == null) return;
		Path path = asset.getPath();
		if (path == null) return;
		if (asset.getStatus() == AudioAssetStatus.QUEUED || analysisTasks.containsKey(asset.getId())) {
			return;
		}
		asset.setStatus(AudioAssetStatus.QUEUED);
		asset.setAnalysisPhase(AudioAnalysisPhase.QUEUED);
		asset.setQueueTicket(nextQueueTicket++);
		asset.setAnalysisProgressPercent(0);
		asset.setProcessingStatusText(BBTexts.get("beatblock.audio.queued"));
		asset.setBeatmap(null); // 新一轮解析期间避免继续使用旧 beatmap/stem
		asset.getFinishedSteps().clear();
		asset.setErrorMessage(null);
		asset.setInfoMessage(null);
		asset.setRequestedAnalysisMode(requestedMode != null ? requestedMode : AudioAnalysisMode.BASIC);
		asset.setResolvedAnalysisMode(null);
		asset.setCacheSource("");

		AudioAnalysisService service = externalAnalyzer();
		if (service == null) {
			asset.setStatus(AudioAssetStatus.FAILED);
			asset.setAnalysisPhase(AudioAnalysisPhase.FAILED);
			asset.setQueueTicket(-1L);
			asset.setErrorMessage(BBTexts.get("beatblock.audio.analyzer_uninitialized"));
			return;
		}

		Future<?> task = service.analyze(
			asset.getId(),
			path,
			(step, pct) -> {
				asset.setAnalysisProgressPercent(pct);
				asset.setProcessingStatusText(stepDisplayName(step));
				asset.setAnalysisPhase(mapPhase(step));
				// 解析 Python 步骤名映射到 UI 步骤
				switch (step) {
					case "DEPENDENCY_INSTALL" -> {
						// 依赖安装是前置步骤，不映射到 beatmap 步骤枚举
					}
					case "DEMUCS_DEP_CHECK" -> asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.checking_deps"));
					case "DEMUCS_DEP_INSTALL" -> asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.installing_deps"));
					case "DEMUCS_DEP_INSTALL_SUCCESS" -> asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.deps_ready"));
					case "DEMUCS_DEP_INSTALL_FAILED_NETWORK" -> asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.install_failed_network"));
					case "DEMUCS_DEP_INSTALL_FAILED_PERMISSION" -> asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.install_failed_permission"));
					case "DEMUCS_DEP_INSTALL_FAILED_VERSION" -> asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.install_failed_version"));
					case "DEMUCS_DEP_INSTALL_FAILED_PIP" -> asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.install_failed_pip"));
					case "DEMUCS_DEP_INSTALL_FAILED_DLL" -> asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.install_failed_dll"));
					case "DEMUCS_DEP_INSTALL_FAILED_UNKNOWN" -> asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.install_failed_unknown"));
					case "DEMUCS_DEP_INSTALL_FAILED" -> {
						if (asset.getInfoMessage() == null || asset.getInfoMessage().isBlank()) {
							asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.install_failed"));
						}
					}
					case "BPM_DETECTION" -> asset.markStepFinished(AudioAnalysisStep.BPM_DETECTION);
					case "BEAT_DETECTION" -> {
						asset.markStepFinished(AudioAnalysisStep.BEAT_DETECTION);
						asset.markStepFinished(AudioAnalysisStep.BAND_SPLIT);
					}
					case "SECTION_DETECTION" -> asset.markStepFinished(AudioAnalysisStep.SECTION_DETECTION);
					case "DEMUCS_SEPARATE" -> {
						// Demucs 模型分离中，不单独计入已完成步骤
					}
					case "DEMUCS_FALLBACK" -> {
						String info = asset.getInfoMessage();
						String fallbackSuffix = BBTexts.get("beatblock.audio.demucs_info.fallback_suffix");
						if (info == null || info.isBlank()) {
							asset.setInfoMessage(BBTexts.get("beatblock.audio.demucs_info.unavailable_basic"));
						} else if (!info.contains(fallbackSuffix.trim())) {
							asset.setInfoMessage(info + fallbackSuffix);
						}
					}
					case "STEM_ANALYSIS" -> asset.markStepFinished(AudioAnalysisStep.STEM_SEPARATION);
					case "WRITE_BEATMAP" -> asset.markStepFinished(AudioAnalysisStep.WRITE_BEATMAP);
					default -> {
					}
				}
			},
			(Beatmap beatmap) -> {
				asset.setBeatmap(beatmap);
				asset.setAnalysisProgressPercent(100);
				asset.setProcessingStatusText(null);
				asset.setQueueTicket(-1L);
				BeatmapMeta meta = beatmap.meta;
				asset.setDurationSeconds(meta.durationMs() / 1000.0);
				asset.setSampleRate(meta.sampleRate());
				asset.setBpm((float) meta.bpm());
				asset.setBeatCount(beatmap.beats.size());
				asset.setSectionCount(beatmap.sections.size());

				int low = 0, mid = 0, high = 0;
				for (BeatEvent e : beatmap.beats) {
					if (e.band() == FrequencyBand.LOW) low++;
					else if (e.band() == FrequencyBand.MID) mid++;
					else if (e.band() == FrequencyBand.HIGH) high++;
				}
				asset.setLowCount(low);
				asset.setMidCount(mid);
				asset.setHighCount(high);
				asset.setResolvedAnalysisMode(meta.hasStemSeparation() ? AudioAnalysisMode.DEMUCS : AudioAnalysisMode.BASIC);
				if (asset.getCacheSource() == null || asset.getCacheSource().isBlank()) {
					asset.setCacheSource("unknown");
				}

				asset.setStatus(AudioAssetStatus.COMPLETED);
				asset.setAnalysisPhase(AudioAnalysisPhase.COMPLETED);
				analysisTasks.remove(asset.getId());
			},
			err -> {
				LOGGER.warn("BeatBlock AudioAssetManager: 外部解析失败: {}", err);
				asset.setStatus(AudioAssetStatus.FAILED);
				asset.setAnalysisPhase(AudioAnalysisPhase.FAILED);
				asset.setAnalysisProgressPercent(0);
				asset.setProcessingStatusText(null);
				asset.setQueueTicket(-1L);
				asset.setErrorMessage(normalizeErrorMessage(path, err));
				analysisTasks.remove(asset.getId());
			},
			summary -> {
				if (summary.durationMs() > 0) {
					asset.setDurationSeconds(summary.durationMs() / 1000.0);
				}
				if (summary.bpm() > 0) {
					asset.setBpm(summary.bpm());
				}
				if (summary.beatCount() >= 0) {
					asset.setBeatCount(summary.beatCount());
				}
				if (summary.sectionCount() >= 0) {
					asset.setSectionCount(summary.sectionCount());
				}
				asset.setResolvedAnalysisMode("demucs".equalsIgnoreCase(summary.separationMode()) ? AudioAnalysisMode.DEMUCS : AudioAnalysisMode.BASIC);
				asset.setCacheSource(summary.cacheSource());
			},
			() -> {
				asset.setStatus(AudioAssetStatus.ANALYZING);
				asset.setAnalysisPhase(AudioAnalysisPhase.ENVIRONMENT);
				asset.setQueueTicket(-1L);
				asset.setProcessingStatusText(BBTexts.get("beatblock.audio.analyzing"));
			},
			asset.getRequestedAnalysisMode() == AudioAnalysisMode.DEMUCS
		);
		analysisTasks.put(asset.getId(), task);
	}

	private AudioAnalysisPhase mapPhase(String step) {
		if (step == null || step.isBlank()) return AudioAnalysisPhase.ENVIRONMENT;
		return switch (step) {
            case "DEMUCS_SEPARATE", "DEMUCS_FALLBACK", "STEM_ANALYSIS" -> AudioAnalysisPhase.STEM_SEPARATION;
			case "BPM_DETECTION", "BEAT_DETECTION" -> AudioAnalysisPhase.RHYTHM;
			case "SECTION_DETECTION" -> AudioAnalysisPhase.STRUCTURE;
			case "WAVEFORM" -> AudioAnalysisPhase.WAVEFORM;
			case "WRITE_BEATMAP" -> AudioAnalysisPhase.WRITE_RESULT;
			default -> AudioAnalysisPhase.ENVIRONMENT;
		};
	}

	private String stepDisplayName(String step) {
		if (step == null || step.isBlank()) return BBTexts.get("beatblock.audio.processing_generic");
		return switch (step) {
			case "DEPENDENCY_INSTALL" -> BBTexts.get("beatblock.audio.step.dependency_install");
			case "DEMUCS_DEP_CHECK" -> BBTexts.get("beatblock.audio.step.demucs_dep_check");
			case "DEMUCS_DEP_INSTALL" -> BBTexts.get("beatblock.audio.step.demucs_dep_install");
			case "DEMUCS_DEP_INSTALL_SUCCESS" -> BBTexts.get("beatblock.audio.step.demucs_dep_ready");
			case "DEMUCS_DEP_INSTALL_FAILED_NETWORK" -> BBTexts.get("beatblock.audio.step.demucs_install_failed_network");
			case "DEMUCS_DEP_INSTALL_FAILED_PERMISSION" -> BBTexts.get("beatblock.audio.step.demucs_install_failed_permission");
			case "DEMUCS_DEP_INSTALL_FAILED_VERSION" -> BBTexts.get("beatblock.audio.step.demucs_install_failed_version");
			case "DEMUCS_DEP_INSTALL_FAILED_PIP" -> BBTexts.get("beatblock.audio.step.demucs_install_failed_pip");
			case "DEMUCS_DEP_INSTALL_FAILED_DLL" -> BBTexts.get("beatblock.audio.step.demucs_install_failed_dll");
			case "DEMUCS_DEP_INSTALL_FAILED_UNKNOWN" -> BBTexts.get("beatblock.audio.step.demucs_install_failed_unknown");
			case "DEMUCS_DEP_INSTALL_FAILED" -> BBTexts.get("beatblock.audio.step.demucs_install_failed");
			case "BPM_DETECTION" -> BBTexts.get("beatblock.audio.step.bpm");
			case "BEAT_DETECTION" -> BBTexts.get("beatblock.audio.step.beat");
			case "DEMUCS_SEPARATE" -> BBTexts.get("beatblock.audio.step.stem");
			case "DEMUCS_FALLBACK" -> BBTexts.get("beatblock.audio.step.demucs_fallback");
			case "STEM_ANALYSIS" -> BBTexts.get("beatblock.audio.step.stem_analysis");
			case "SECTION_DETECTION" -> BBTexts.get("beatblock.audio.step.section");
			case "WAVEFORM" -> BBTexts.get("beatblock.audio.step.waveform_generate");
			case "WRITE_BEATMAP" -> BBTexts.get("beatblock.audio.step.write");
			default -> step;
		};
	}

	private String normalizeErrorMessage(Path audioPath, String raw) {
		String safe = raw == null ? BBTexts.get("beatblock.audio.error.unknown") : raw.trim();
		if (looksLikeUnsupportedAudioFormat(audioPath, safe)) {
			return BBTexts.get("beatblock.audio.error.unsupported_format");
		}
		return safe;
	}

	/**
	 * 判断是否为「音频格式/解码」类错误。避免把 Python traceback.format_exc 等误判为格式问题。
	 */
	static boolean looksLikeUnsupportedAudioFormat(Path audioPath, String raw) {
		if (raw == null || raw.isBlank()) return false;
		String lower = raw.toLowerCase(Locale.ROOT);
		String ext = extensionOf(audioPath);
		if ("wma".equals(ext) || "aac".equals(ext) || "m4a".equals(ext) || lower.contains(".wma")) {
			return true;
		}
		if (lower.contains("unsupported audio")
			|| lower.contains("unsupported format")
			|| lower.contains("format not supported")
			|| lower.contains("unknown format")
			|| lower.contains("invalid format")
			|| lower.contains("cannot load audio")
			|| lower.contains("can't load audio")
			|| lower.contains("could not load audio")
			|| lower.contains("failed to load audio")
			|| lower.contains("no backend")
			|| lower.contains("audioread")) {
			return true;
		}
		// librosa/soundfile 常见 MP3 解码失败（缺 ffmpeg）
		return lower.contains("ffmpeg") && (lower.contains("not found") || lower.contains("找不到"));
	}

	private static String extensionOf(Path audioPath) {
		if (audioPath == null || audioPath.getFileName() == null) return "";
		String name = audioPath.getFileName().toString();
		int idx = name.lastIndexOf('.');
		if (idx < 0 || idx >= name.length() - 1) return "";
		return name.substring(idx + 1).toLowerCase(Locale.ROOT);
	}

	@FunctionalInterface
	public interface ConversionRequestHandler {
		void requestConversion(AudioAsset asset, String targetFormat);
	}
}
