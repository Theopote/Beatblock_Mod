package com.beatblock.client.export;

import com.beatblock.client.BeatBlockClientDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 视频导出期间的呈现会话：在 {@link #begin()} 时记录并冻结用户编辑态 presentation，
 * 导出帧 seek 仅作用于舞台世界写入与离屏合成；每帧捕获后恢复编辑态，{@link #close()} 时
 * 最终恢复世界 mutation、Camera、Weather、Lighting、Overlay、AudioMix 与时间线 seek。
 */
public final class ExportPresentationSession implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExportPresentationSession.class);

	private final ExportPresentationSnapshot snapshot;
	private boolean closed;

	private ExportPresentationSession(ExportPresentationSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	public static ExportPresentationSession begin() {
		ExportPresentationSnapshot snapshot = BeatBlockClientDriver.beginExportPresentation();
		LOGGER.debug(
			"ExportPresentationSession begin (restore seek={}s)",
			snapshot.restoreTimelineTimeSeconds()
		);
		return new ExportPresentationSession(snapshot);
	}

	public ExportPresentationSnapshot snapshot() {
		return snapshot;
	}

	public double restoreTimelineTimeSeconds() {
		return snapshot.restoreTimelineTimeSeconds();
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		BeatBlockClientDriver.endExportPresentation(snapshot);
		LOGGER.debug(
			"ExportPresentationSession closed (restored seek={}s)",
			snapshot.restoreTimelineTimeSeconds()
		);
	}
}
