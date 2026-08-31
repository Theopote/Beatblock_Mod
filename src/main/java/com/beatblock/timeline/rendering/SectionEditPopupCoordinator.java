package com.beatblock.timeline.rendering;

/**
 * 跨面板协调 section 编舞弹窗打开请求（菜单栏与时间轴点击共享）。
 */
public final class SectionEditPopupCoordinator {

	private static boolean openRequested;
	private static int requestedSectionIndex = -1;

	private SectionEditPopupCoordinator() {}

	public static void requestOpen() {
		openRequested = true;
		requestedSectionIndex = -1;
	}

	public static void requestOpen(int sectionIndex) {
		openRequested = true;
		requestedSectionIndex = Math.max(0, sectionIndex);
	}

	public static boolean consumeOpenRequest() {
		boolean requested = openRequested;
		openRequested = false;
		return requested;
	}

	public static int consumeSectionIndex() {
		int index = requestedSectionIndex;
		requestedSectionIndex = -1;
		return index;
	}
}
