package com.beatblock.automap.camera;

import org.jspecify.annotations.Nullable;

/**
 * Live-camera eye / look capture used when compiling a pose-anchored {@link CameraShotDraft}.
 * <p>
 * After compile, Timeline geometry is source of truth; this record is creation intent only.
 */
public record CapturedCameraPose(
	double eyeX,
	double eyeY,
	double eyeZ,
	double yawDeg,
	double pitchDeg,
	@Nullable OrbitCapture orbit
) {

	public CapturedCameraPose(double eyeX, double eyeY, double eyeZ, double yawDeg, double pitchDeg) {
		this(eyeX, eyeY, eyeZ, yawDeg, pitchDeg, null);
	}

	/** Orbit fit from the current view (target, radius, height, yaw arc). */
	public record OrbitCapture(
		double targetX,
		double targetY,
		double targetZ,
		double radius,
		double height,
		double yawStartDeg,
		double yawEndDeg
	) {}

	public static CapturedCameraPose fromAnchorFive(double[] a) {
		if (a == null || a.length < 5) {
			return new CapturedCameraPose(0, 0, 0, 0, 0);
		}
		return new CapturedCameraPose(a[0], a[1], a[2], a[3], a[4]);
	}

	public static CapturedCameraPose fromOrbitParams(double[] o) {
		if (o == null || o.length < 7) {
			return new CapturedCameraPose(0, 0, 0, 0, 0,
				new OrbitCapture(0, 0, 0, 10.0, 4.0, 0.0, 270.0));
		}
		OrbitCapture orbit = new OrbitCapture(o[0], o[1], o[2], o[3], o[4], o[5], o[6]);
		// Eye is not required for orbit factory writes; keep zeros for the eye fields.
		return new CapturedCameraPose(0, 0, 0, 0, 0, orbit);
	}
}
