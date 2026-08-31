package com.beatblock.automap.camera;

/**
 * 镜头主体引用：可绑定舞台对象、图层、世界坐标等。
 */
public record CameraSubject(
	CameraSubjectKind kind,
	String refId,
	double x,
	double y,
	double z
) {

	public CameraSubject {
		refId = refId != null ? refId : "";
	}

	public static CameraSubject stageObject(String objectId) {
		return new CameraSubject(CameraSubjectKind.STAGE_OBJECT, objectId, 0, 0, 0);
	}

	public static CameraSubject stageGroup(String groupId) {
		return new CameraSubject(CameraSubjectKind.STAGE_GROUP, groupId, 0, 0, 0);
	}

	public static CameraSubject buildLayer(String layerId) {
		return new CameraSubject(CameraSubjectKind.BUILD_LAYER, layerId, 0, 0, 0);
	}

	public static CameraSubject animatedTarget(String targetId) {
		return new CameraSubject(CameraSubjectKind.ANIMATED_TARGET, targetId, 0, 0, 0);
	}

	public static CameraSubject worldPosition(double x, double y, double z) {
		return new CameraSubject(CameraSubjectKind.WORLD_POSITION, "", x, y, z);
	}

	public static CameraSubject allStageObjects() {
		return new CameraSubject(CameraSubjectKind.ALL_STAGE_OBJECTS, "", 0, 0, 0);
	}

	public String displayLabel() {
		return switch (kind) {
			case STAGE_OBJECT -> refId.isBlank() ? "StageObject" : "StageObject " + refId;
			case STAGE_GROUP -> refId.isBlank() ? "StageGroup" : "StageGroup " + refId;
			case BUILD_LAYER -> refId.isBlank() ? "BuildLayer" : "BuildLayer " + refId;
			case ANIMATED_TARGET -> refId.isBlank() ? "AnimatedTarget" : "AnimatedTarget " + refId;
			case WORLD_POSITION -> String.format("World(%.1f,%.1f,%.1f)", x, y, z);
			case ALL_STAGE_OBJECTS -> "All";
		};
	}
}
