package com.beatblock.automap.camera;

import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

/** 摄像机主体解析结果：成功携带坐标，失败携带验收规则 id。 */
public record CameraSubjectResolveResult(
	boolean resolved,
	@Nullable Vec3d position,
	@Nullable String ruleId,
	@Nullable String detail
) {
	public static CameraSubjectResolveResult ok(Vec3d position) {
		return new CameraSubjectResolveResult(true, position, null, null);
	}

	public static CameraSubjectResolveResult fail(String ruleId, String detail) {
		return new CameraSubjectResolveResult(false, null, ruleId, detail);
	}
}
