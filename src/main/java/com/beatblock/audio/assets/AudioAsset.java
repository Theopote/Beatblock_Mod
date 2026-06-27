package com.beatblock.audio.assets;

import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.beatmap.Beatmap;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.UUID;

/**
 * 单个音频资产：路径 + 解析结果 + 进度状态。
 */
public final class AudioAsset {

	private final String id;
	private @Nullable Path path;
	private String fileName;

	private double durationSeconds;
	private int sampleRate;

	private float bpm;
	private int beatCount;
	private int sectionCount;
	private int lowCount;
	private int midCount;
	private int highCount;

	private AudioAssetStatus status = AudioAssetStatus.PENDING;
	private long queueTicket = -1L;
	private int analysisProgressPercent;
	private @Nullable String processingStatusText;
	private AudioAnalysisPhase analysisPhase = AudioAnalysisPhase.PENDING;
	private final EnumSet<AudioAnalysisStep> finishedSteps = EnumSet.noneOf(AudioAnalysisStep.class);
	private @Nullable String errorMessage;
	private @Nullable String infoMessage;
	private AudioAnalysisMode requestedAnalysisMode = AudioAnalysisMode.BASIC;
	private @Nullable AudioAnalysisMode resolvedAnalysisMode;
	private String cacheSource = "";

	private @Nullable AudioFeatureTimeline featureTimeline;
	private @Nullable Beatmap beatmap;

	public AudioAsset(@Nullable Path path) {
		this.id = UUID.randomUUID().toString();
		this.path = path;
        this.fileName = path != null ? path.getFileName().toString() : "";
	}

	public String getId() { return id; }
	public @Nullable Path getPath() { return path; }
	public String getFileName() { return fileName; }
	public void setPath(@Nullable Path path) {
		this.path = path;
        this.fileName = path != null ? path.getFileName().toString() : "";
	}

	public double getDurationSeconds() { return durationSeconds; }
	public void setDurationSeconds(double durationSeconds) { this.durationSeconds = Math.max(0, durationSeconds); }

	public int getSampleRate() { return sampleRate; }
	public void setSampleRate(int sampleRate) { this.sampleRate = Math.max(1, sampleRate); }

	public float getBpm() { return bpm; }
	public void setBpm(float bpm) { this.bpm = bpm; }

	public int getBeatCount() { return beatCount; }
	public void setBeatCount(int beatCount) { this.beatCount = Math.max(0, beatCount); }

	public int getSectionCount() { return sectionCount; }
	public void setSectionCount(int sectionCount) { this.sectionCount = Math.max(0, sectionCount); }

	public int getLowCount() { return lowCount; }
	public void setLowCount(int lowCount) { this.lowCount = Math.max(0, lowCount); }

	public int getMidCount() { return midCount; }
	public void setMidCount(int midCount) { this.midCount = Math.max(0, midCount); }

	public int getHighCount() { return highCount; }
	public void setHighCount(int highCount) { this.highCount = Math.max(0, highCount); }

	public AudioAssetStatus getStatus() { return status; }
	public void setStatus(AudioAssetStatus status) { this.status = status != null ? status : AudioAssetStatus.PENDING; }
	public long getQueueTicket() { return queueTicket; }
	public void setQueueTicket(long queueTicket) { this.queueTicket = queueTicket; }

	public int getAnalysisProgressPercent() { return analysisProgressPercent; }
	public void setAnalysisProgressPercent(int analysisProgressPercent) {
		this.analysisProgressPercent = Math.max(0, Math.min(100, analysisProgressPercent));
	}

	public @Nullable String getProcessingStatusText() { return processingStatusText; }
	public void setProcessingStatusText(@Nullable String processingStatusText) { this.processingStatusText = processingStatusText; }

	public AudioAnalysisPhase getAnalysisPhase() { return analysisPhase; }
	public void setAnalysisPhase(AudioAnalysisPhase analysisPhase) {
		this.analysisPhase = analysisPhase != null ? analysisPhase : AudioAnalysisPhase.PENDING;
	}

	public EnumSet<AudioAnalysisStep> getFinishedSteps() { return finishedSteps; }
	public void markStepFinished(AudioAnalysisStep step) {
		if (step != null) finishedSteps.add(step);
	}

	public @Nullable String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(@Nullable String errorMessage) { this.errorMessage = errorMessage; }

	public @Nullable String getInfoMessage() { return infoMessage; }
	public void setInfoMessage(@Nullable String infoMessage) { this.infoMessage = infoMessage; }

	public AudioAnalysisMode getRequestedAnalysisMode() { return requestedAnalysisMode; }
	public void setRequestedAnalysisMode(AudioAnalysisMode requestedAnalysisMode) {
		this.requestedAnalysisMode = requestedAnalysisMode != null ? requestedAnalysisMode : AudioAnalysisMode.BASIC;
	}

	public @Nullable AudioAnalysisMode getResolvedAnalysisMode() { return resolvedAnalysisMode; }
	public void setResolvedAnalysisMode(@Nullable AudioAnalysisMode resolvedAnalysisMode) { this.resolvedAnalysisMode = resolvedAnalysisMode; }

	public String getCacheSource() { return cacheSource; }
	public void setCacheSource(String cacheSource) { this.cacheSource = cacheSource != null ? cacheSource : ""; }

	public @Nullable AudioFeatureTimeline getFeatureTimeline() { return featureTimeline; }
	public void setFeatureTimeline(@Nullable AudioFeatureTimeline featureTimeline) { this.featureTimeline = featureTimeline; }

	public @Nullable Beatmap getBeatmap() { return beatmap; }
	public void setBeatmap(@Nullable Beatmap beatmap) { this.beatmap = beatmap; }
}
