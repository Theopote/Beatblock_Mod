package com.beatblock.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 将方块选区视为体素集合，对暴露表面做贪婪矩形合并后绘制半透明填充与轮廓线，
 * 减少三角面与线段数量（相对逐块立方体）。
 */
public final class SelectionVoxelGreedyMesher {

	private static final float FACE_EPS = 0.002f;

	private SelectionVoxelGreedyMesher() {}

	public static void render(
			Set<BlockPos> selected,
			Vec3d cam,
			MatrixStack matrices,
			VertexConsumer lineBuffer,
			VertexConsumer fillBuffer,
			boolean wantFill,
			double maxDistSq,
			int outlineArgb,
			float lineWidth,
			float fillR,
			float fillG,
			float fillB,
			float fillA) {
		if (selected == null || selected.isEmpty()) {
			return;
		}
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : selected) {
			int x = p.getX(), y = p.getY(), z = p.getZ();
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
		}
		int sx = maxX - minX + 1;
		int sy = maxY - minY + 1;
		int sz = maxZ - minZ + 1;

		Map<Integer, boolean[][]> up = new HashMap<>();
		Map<Integer, boolean[][]> down = new HashMap<>();
		Map<Integer, boolean[][]> east = new HashMap<>();
		Map<Integer, boolean[][]> west = new HashMap<>();
		Map<Integer, boolean[][]> south = new HashMap<>();
		Map<Integer, boolean[][]> north = new HashMap<>();

		for (BlockPos p : selected) {
			int x = p.getX(), y = p.getY(), z = p.getZ();
			int ix = x - minX, iy = y - minY, iz = z - minZ;
			if (!selected.contains(p.offset(Direction.UP))) {
				markXZ(up, y + 1, ix, iz, sx, sz);
			}
			if (!selected.contains(p.offset(Direction.DOWN))) {
				markXZ(down, y, ix, iz, sx, sz);
			}
			if (!selected.contains(p.offset(Direction.EAST))) {
				markZY(east, x + 1, iz, iy, sz, sy);
			}
			if (!selected.contains(p.offset(Direction.WEST))) {
				markZY(west, x, iz, iy, sz, sy);
			}
			if (!selected.contains(p.offset(Direction.SOUTH))) {
				markXY(south, z + 1, ix, iy, sx, sy);
			}
			if (!selected.contains(p.offset(Direction.NORTH))) {
				markXY(north, z, ix, iy, sx, sy);
			}
		}

		matrices.push();
		Matrix4f mat = matrices.peek().getPositionMatrix();
		Matrix3f nmat = matrices.peek().getNormalMatrix();

		// 先写完所有线段再写填充，避免 Immediate 模式下 LINES 与 debugFilledBox 交错导致缓冲错误
		emitMergedFaces(up, cam, mat, nmat, lineBuffer, null, false, maxDistSq, outlineArgb, lineWidth,
				fillR, fillG, fillB, fillA, minX, minZ, FaceKind.UP);
		emitMergedFaces(down, cam, mat, nmat, lineBuffer, null, false, maxDistSq, outlineArgb, lineWidth,
				fillR, fillG, fillB, fillA, minX, minZ, FaceKind.DOWN);
		emitMergedZY(east, cam, mat, nmat, lineBuffer, null, false, maxDistSq, outlineArgb, lineWidth,
				fillR, fillG, fillB, fillA, minY, minZ, +1);
		emitMergedZY(west, cam, mat, nmat, lineBuffer, null, false, maxDistSq, outlineArgb, lineWidth,
				fillR, fillG, fillB, fillA, minY, minZ, -1);
		emitMergedXY(south, cam, mat, nmat, lineBuffer, null, false, maxDistSq, outlineArgb, lineWidth,
				fillR, fillG, fillB, fillA, minX, minY, +1);
		emitMergedXY(north, cam, mat, nmat, lineBuffer, null, false, maxDistSq, outlineArgb, lineWidth,
				fillR, fillG, fillB, fillA, minX, minY, -1);

		if (wantFill && fillBuffer != null) {
			emitMergedFaces(up, cam, mat, nmat, null, fillBuffer, true, maxDistSq, outlineArgb, lineWidth,
					fillR, fillG, fillB, fillA, minX, minZ, FaceKind.UP);
			emitMergedFaces(down, cam, mat, nmat, null, fillBuffer, true, maxDistSq, outlineArgb, lineWidth,
					fillR, fillG, fillB, fillA, minX, minZ, FaceKind.DOWN);
			emitMergedZY(east, cam, mat, nmat, null, fillBuffer, true, maxDistSq, outlineArgb, lineWidth,
					fillR, fillG, fillB, fillA, minY, minZ, +1);
			emitMergedZY(west, cam, mat, nmat, null, fillBuffer, true, maxDistSq, outlineArgb, lineWidth,
					fillR, fillG, fillB, fillA, minY, minZ, -1);
			emitMergedXY(south, cam, mat, nmat, null, fillBuffer, true, maxDistSq, outlineArgb, lineWidth,
					fillR, fillG, fillB, fillA, minX, minY, +1);
			emitMergedXY(north, cam, mat, nmat, null, fillBuffer, true, maxDistSq, outlineArgb, lineWidth,
					fillR, fillG, fillB, fillA, minX, minY, -1);
		}

		matrices.pop();
	}

	private enum FaceKind {
		UP, DOWN
	}

	private static void markXZ(Map<Integer, boolean[][]> map, int key, int ix, int iz, int w, int h) {
		boolean[][] m = map.computeIfAbsent(key, k -> new boolean[w][h]);
		m[ix][iz] = true;
	}

	private static void markZY(Map<Integer, boolean[][]> map, int key, int iz, int iy, int w, int h) {
		boolean[][] m = map.computeIfAbsent(key, k -> new boolean[w][h]);
		m[iz][iy] = true;
	}

	private static void markXY(Map<Integer, boolean[][]> map, int key, int ix, int iy, int w, int h) {
		boolean[][] m = map.computeIfAbsent(key, k -> new boolean[w][h]);
		m[ix][iy] = true;
	}

	private static void emitMergedFaces(
			Map<Integer, boolean[][]> slices,
			Vec3d cam,
			Matrix4f mat,
			Matrix3f nmat,
			VertexConsumer lines,
			VertexConsumer fill,
			boolean wantFill,
			double maxDistSq,
			int outlineArgb,
			float lineWidth,
			float fr, float fg, float fb, float fa,
			int minX,
			int minZ,
			FaceKind kind) {
		for (Map.Entry<Integer, boolean[][]> e : slices.entrySet()) {
			int yPlane = e.getKey();
			boolean[][] mask = e.getValue();
			int w = mask.length;
			int h = mask[0].length;
			greedyMerge(mask, w, h, (i0, j0, rw, rh) -> {
				double x0w = minX + i0;
				double z0w = minZ + j0;
				double x1w = minX + i0 + rw;
				double z1w = minZ + j0 + rh;
				double yw = yPlane + (kind == FaceKind.UP ? FACE_EPS : -FACE_EPS);
				double cx = (x0w + x1w) * 0.5;
				double cz = (z0w + z1w) * 0.5;
				if (cam.squaredDistanceTo(cx, yPlane, cz) > maxDistSq) {
					return;
				}
				float x0 = (float) (x0w - cam.x);
				float x1 = (float) (x1w - cam.x);
				float z0 = (float) (z0w - cam.z);
				float z1 = (float) (z1w - cam.z);
				float y = (float) (yw - cam.y);
				float nx = 0f;
				float ny = kind == FaceKind.UP ? 1f : -1f;
				float nz = 0f;
				if (wantFill && fill != null) {
					if (kind == FaceKind.UP) {
						emitQuad(fill, mat, nmat, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, nx, ny, nz, fr, fg, fb, fa);
					} else {
						emitQuad(fill, mat, nmat, x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, nx, ny, nz, fr, fg, fb, fa);
					}
				}
				if (lines != null) {
					emitRectOutline(lines, mat, outlineArgb, lineWidth, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1);
				}
			});
		}
	}

	/** EAST (+1) / WEST (-1)：mask 维 (z,y)，平面 x = sliceKey。 */
	private static void emitMergedZY(
			Map<Integer, boolean[][]> slices,
			Vec3d cam,
			Matrix4f mat,
			Matrix3f nmat,
			VertexConsumer lines,
			VertexConsumer fill,
			boolean wantFill,
			double maxDistSq,
			int outlineArgb,
			float lineWidth,
			float fr, float fg, float fb, float fa,
			int minY,
			int minZ,
			int xSign) {
		for (Map.Entry<Integer, boolean[][]> e : slices.entrySet()) {
			int xPlane = e.getKey();
			boolean[][] mask = e.getValue();
			int w = mask.length;
			int h = mask[0].length;
			float nx = xSign > 0 ? 1f : -1f;
			float xw = xPlane + (xSign > 0 ? FACE_EPS : -FACE_EPS);
			greedyMerge(mask, w, h, (i0, j0, rw, rh) -> {
				double z0w = minZ + i0;
				double y0w = minY + j0;
				double z1w = minZ + i0 + rw;
				double y1w = minY + j0 + rh;
				double cx = xPlane + 0.5 * xSign;
				double cy = (y0w + y1w) * 0.5;
				double cz = (z0w + z1w) * 0.5;
				if (cam.squaredDistanceTo(cx, cy, cz) > maxDistSq) {
					return;
				}
				float x = (float) (xw - cam.x);
				float z0 = (float) (z0w - cam.z);
				float z1 = (float) (z1w - cam.z);
				float y0 = (float) (y0w - cam.y);
				float y1 = (float) (y1w - cam.y);
				if (wantFill && fill != null) {
					if (xSign > 0) {
						emitQuad(fill, mat, nmat, x, y0, z0, x, y0, z1, x, y1, z1, x, y1, z0, nx, 0f, 0f, fr, fg, fb, fa);
					} else {
						emitQuad(fill, mat, nmat, x, y0, z0, x, y1, z0, x, y1, z1, x, y0, z1, nx, 0f, 0f, fr, fg, fb, fa);
					}
				}
				if (lines != null) {
					emitRectOutline(lines, mat, outlineArgb, lineWidth, x, y0, z0, x, y0, z1, x, y1, z1, x, y1, z0);
				}
			});
		}
	}

	/** SOUTH (+Z) / NORTH (-Z)：mask 维 (x,y)，平面 z = sliceKey。 */
	private static void emitMergedXY(
			Map<Integer, boolean[][]> slices,
			Vec3d cam,
			Matrix4f mat,
			Matrix3f nmat,
			VertexConsumer lines,
			VertexConsumer fill,
			boolean wantFill,
			double maxDistSq,
			int outlineArgb,
			float lineWidth,
			float fr, float fg, float fb, float fa,
			int minX,
			int minY,
			int zSign) {
		for (Map.Entry<Integer, boolean[][]> e : slices.entrySet()) {
			int zPlane = e.getKey();
			boolean[][] mask = e.getValue();
			int w = mask.length;
			int h = mask[0].length;
			float nz = zSign > 0 ? 1f : -1f;
			float zw = zPlane + (zSign > 0 ? FACE_EPS : -FACE_EPS);
			greedyMerge(mask, w, h, (i0, j0, rw, rh) -> {
				double x0w = minX + i0;
				double y0w = minY + j0;
				double x1w = minX + i0 + rw;
				double y1w = minY + j0 + rh;
				double cx = (x0w + x1w) * 0.5;
				double cy = (y0w + y1w) * 0.5;
				double cz = zPlane + 0.5 * zSign;
				if (cam.squaredDistanceTo(cx, cy, cz) > maxDistSq) {
					return;
				}
				float z = (float) (zw - cam.z);
				float x0 = (float) (x0w - cam.x);
				float x1 = (float) (x1w - cam.x);
				float y0 = (float) (y0w - cam.y);
				float y1 = (float) (y1w - cam.y);
				if (wantFill && fill != null) {
					if (zSign > 0) {
						emitQuad(fill, mat, nmat, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, 0f, 0f, nz, fr, fg, fb, fa);
					} else {
						emitQuad(fill, mat, nmat, x0, y0, z, x0, y1, z, x1, y1, z, x1, y0, z, 0f, 0f, nz, fr, fg, fb, fa);
					}
				}
				if (lines != null) {
					emitRectOutline(lines, mat, outlineArgb, lineWidth, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z);
				}
			});
		}
	}

	@FunctionalInterface
	private interface MergedRectCallback {
		void accept(int i0, int j0, int rw, int rh);
	}

	private static void greedyMerge(boolean[][] mask, int w, int h, MergedRectCallback out) {
		boolean[][] used = new boolean[w][h];
		for (int j = 0; j < h; j++) {
			for (int i = 0; i < w; i++) {
				if (!mask[i][j] || used[i][j]) {
					continue;
				}
				int rw = 0;
				while (i + rw < w && mask[i + rw][j] && !used[i + rw][j]) {
					rw++;
				}
				int rh = 1;
				boolean grow = true;
				while (grow && j + rh < h) {
					for (int k = 0; k < rw; k++) {
						if (!mask[i + k][j + rh] || used[i + k][j + rh]) {
							grow = false;
							break;
						}
					}
					if (grow) {
						rh++;
					}
				}
				for (int jj = j; jj < j + rh; jj++) {
					for (int ii = i; ii < i + rw; ii++) {
						used[ii][jj] = true;
					}
				}
				out.accept(i, j, rw, rh);
			}
		}
	}

	private static void emitQuad(
			VertexConsumer buf,
			Matrix4f m,
			Matrix3f nmat,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float nx, float ny, float nz,
			float r, float g, float b, float a) {
		Vector3f nn = new Vector3f(nx, ny, nz);
		nmat.transform(nn);
		buf.vertex(m, x0, y0, z0).color(r, g, b, a).normal(nn.x, nn.y, nn.z);
		buf.vertex(m, x1, y1, z1).color(r, g, b, a).normal(nn.x, nn.y, nn.z);
		buf.vertex(m, x2, y2, z2).color(r, g, b, a).normal(nn.x, nn.y, nn.z);
		buf.vertex(m, x3, y3, z3).color(r, g, b, a).normal(nn.x, nn.y, nn.z);
	}

	private static void emitRectOutline(
			VertexConsumer buf,
			Matrix4f mat,
			int argb,
			float lineWidth,
			float ax, float ay, float az,
			float bx, float by, float bz,
			float cx, float cy, float cz,
			float dx, float dy, float dz) {
		float ca = ((argb >>> 24) & 255) / 255f;
		float cr = ((argb >>> 16) & 255) / 255f;
		float cg = ((argb >>> 8) & 255) / 255f;
		float cb = (argb & 255) / 255f;
		emitLine(buf, mat, cr, cg, cb, ca, lineWidth, ax, ay, az, bx, by, bz);
		emitLine(buf, mat, cr, cg, cb, ca, lineWidth, bx, by, bz, cx, cy, cz);
		emitLine(buf, mat, cr, cg, cb, ca, lineWidth, cx, cy, cz, dx, dy, dz);
		emitLine(buf, mat, cr, cg, cb, ca, lineWidth, dx, dy, dz, ax, ay, az);
	}

	private static void emitLine(
			VertexConsumer buf,
			Matrix4f mat,
			float cr, float cg, float cb, float ca,
			float lineWidth,
			float x0, float y0, float z0,
			float x1, float y1, float z1) {
		buf.vertex(mat, x0, y0, z0).color(cr, cg, cb, ca).normal(0f, 1f, 0f).lineWidth(lineWidth);
		buf.vertex(mat, x1, y1, z1).color(cr, cg, cb, ca).normal(0f, 1f, 0f).lineWidth(lineWidth);
	}
}
