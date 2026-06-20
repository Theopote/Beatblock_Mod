package com.beatblock.audio.python;

/**
 * Python 解释器探测结果（版本、pip、可执行路径）。
 */
public record PythonProbeInfo(
	boolean probeOk,
	int major,
	int minor,
	int micro,
	boolean hasPip,
	String executablePath,
	String detail,
	long checkedAtMs
) {
	public static PythonProbeInfo ok(int major, int minor, int micro, boolean hasPip, String executablePath) {
		return new PythonProbeInfo(true, major, minor, micro, hasPip, executablePath, "", System.currentTimeMillis());
	}

	public static PythonProbeInfo failed(String detail) {
		return new PythonProbeInfo(false, 0, 0, 0, false, "", detail == null ? "" : detail, System.currentTimeMillis());
	}

	public boolean isSupportedVersion() {
		return major == 3 && minor <= 12;
	}

	public String versionString() {
		if (!probeOk) return "unknown";
		return major + "." + minor + "." + micro;
	}
}
