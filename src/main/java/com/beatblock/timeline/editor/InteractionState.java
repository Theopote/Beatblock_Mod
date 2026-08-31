package com.beatblock.timeline.editor;

/**
 * 交互状态：当前模式、按下时的鼠标位置、正在操作的对象 ID。
 */
public class InteractionState {

	private InteractionMode mode = InteractionMode.NONE;
	private float mouseStartX;
	private float mouseStartY;
	private String activeEventId;
	private String activeClipId;
	private String activeTrackId;
	private String activeMarkerId;
	/** MARKER_DRAG 按下时标记的原始时间（松开时提交 Undo）。 */
	private double markerDragStartTimeSeconds;
	private String markerDragName = "";
	private boolean resizeLeft; // RESIZE_CLIP 时 true=左边缘
	private float resizeStartHeaderWidth; // RESIZE_HEADER 时按下时的轨道头宽度
	/** SECTION_BOUNDARY_DRAG：边界 index（section[i-1] | section[i]）。 */
	private int sectionBoundaryIndex = -1;
	private double sectionBoundaryDragStartSeconds;
	private double[] alignmentGuideTimes = new double[0];

	public double[] getAlignmentGuideTimes() {
		return alignmentGuideTimes;
	}

	public void setAlignmentGuideTimes(double[] times) {
		alignmentGuideTimes = times != null ? times : new double[0];
	}

	public void clearAlignmentGuideTimes() {
		alignmentGuideTimes = new double[0];
	}

	public InteractionMode getMode() {
		return mode;
	}

	public void setMode(InteractionMode mode) {
		this.mode = mode != null ? mode : InteractionMode.NONE;
	}

	public float getMouseStartX() { return mouseStartX; }
	public float getMouseStartY() { return mouseStartY; }
	public void setMouseStart(float x, float y) {
		mouseStartX = x;
		mouseStartY = y;
	}

	public String getActiveEventId() { return activeEventId; }
	public void setActiveEventId(String id) { activeEventId = id; }
	public String getActiveClipId() { return activeClipId; }
	public void setActiveClipId(String id) { activeClipId = id; }
	public String getActiveTrackId() { return activeTrackId; }
	public void setActiveTrackId(String id) { activeTrackId = id; }
	public String getActiveMarkerId() { return activeMarkerId; }
	public void setActiveMarkerId(String id) { activeMarkerId = id; }

	public double getMarkerDragStartTimeSeconds() { return markerDragStartTimeSeconds; }
	public void setMarkerDragStartTimeSeconds(double t) { markerDragStartTimeSeconds = t; }

	public String getMarkerDragName() { return markerDragName != null ? markerDragName : ""; }
	public void setMarkerDragName(String name) { markerDragName = name != null ? name : ""; }

	public boolean isResizeLeft() { return resizeLeft; }
	public void setResizeLeft(boolean left) { resizeLeft = left; }

	public float getResizeStartHeaderWidth() { return resizeStartHeaderWidth; }
	public void setResizeStartHeaderWidth(float w) { resizeStartHeaderWidth = w; }

	public int getSectionBoundaryIndex() { return sectionBoundaryIndex; }
	public void setSectionBoundaryIndex(int index) { sectionBoundaryIndex = index; }

	public double getSectionBoundaryDragStartSeconds() { return sectionBoundaryDragStartSeconds; }
	public void setSectionBoundaryDragStartSeconds(double seconds) { sectionBoundaryDragStartSeconds = seconds; }

	public void clearActive() {
		activeEventId = null;
		activeClipId = null;
		activeTrackId = null;
		activeMarkerId = null;
		markerDragStartTimeSeconds = 0;
		markerDragName = "";
		sectionBoundaryIndex = -1;
		sectionBoundaryDragStartSeconds = 0;
		clearAlignmentGuideTimes();
	}
}
