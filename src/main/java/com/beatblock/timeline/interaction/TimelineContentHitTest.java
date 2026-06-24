package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.editor.TimelineViewState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

import static com.beatblock.timeline.interaction.TimelineInteractiveTrackSlots.InteractiveTrackSlot;
import static com.beatblock.timeline.interaction.TimelineInteractiveTrackSlots.build;

/** 内容区命中测试与相机默认参数采样。 */
public final class TimelineContentHitTest {

	private TimelineContentHitTest() {}

	public static HitResult hitContentAtMouse(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mx,
		float my
	) {
		for (InteractiveTrackSlot slot : build(timeline)) {
			int logicalRow = slot.rowIndex();
			if (!layout.isRowVisible(logicalRow)) continue;
			float rowScreenY = layout.getRowScreenY(logicalRow);
			float rowH = layout.getRowHeight(logicalRow);
			HitResult hit = HitTestSystem.hitTestTrackContent(
				timeline,
				slot.trackId(),
				mx,
				my,
				layout.contentLeft,
				rowScreenY,
				rowH,
				layout.contentWidth,
				viewState);
			if (!hit.isEmpty()) return hit;
		}
		return HitResult.empty();
	}

	/** x, y, z, yawDeg, pitchDeg — 无玩家时为原点与水平朝向 */
	public static double[] readCameraAnchorFive() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc != null && mc.player != null) {
			Vec3d eye = mc.player.getEyePos();
			return new double[]{
				eye.x,
				eye.y,
				eye.z,
				mc.player.getYaw(),
				mc.player.getPitch()
			};
		}
		return new double[]{0.0, 0.0, 0.0, 0.0, 0.0};
	}

	/**
	 * 环绕片段默认值：目标为准星方块命中点（否则为视线前方约 10m），半径/高度/起始角由当前眼点相对目标拟合，弧长默认 270°。
	 */
	public static double[] readOrbitParamsFromView() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null) {
			return new double[]{0.0, 0.0, 0.0, 10.0, 4.0, 0.0, 270.0};
		}
		Vec3d eye = mc.player.getEyePos();
		Vec3d target;
		net.minecraft.util.hit.HitResult ch = mc.crosshairTarget;
		if (ch instanceof BlockHitResult bhr && ch.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
			target = bhr.getPos();
		} else {
			Vec3d dir = mc.player.getRotationVec(1f);
			target = eye.add(dir.multiply(10.0));
		}
		double tx = target.x;
		double ty = target.y;
		double tz = target.z;
		double dx = eye.x - tx;
		double dz = eye.z - tz;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		double radius = Math.max(0.75, horiz);
		double height = eye.y - ty;
		double yawStartDeg = Math.toDegrees(Math.atan2(-dx, dz));
		double yawEndDeg = yawStartDeg + 270.0;
		return new double[]{tx, ty, tz, radius, height, yawStartDeg, yawEndDeg};
	}
}
