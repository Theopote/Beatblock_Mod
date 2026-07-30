package com.beatblock.audio;

/** Observable lifecycle of one serialized audio-analysis task. */
public enum AnalysisTaskState {
	QUEUED,
	STARTING,
	RUNNING,
	CANCELLING,
	CANCELLED,
	SUCCEEDED,
	FAILED;

	public boolean isTerminal() {
		return this == CANCELLED || this == SUCCEEDED || this == FAILED;
	}
}
