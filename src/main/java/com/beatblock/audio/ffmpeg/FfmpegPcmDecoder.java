package com.beatblock.audio.ffmpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 通过 ffmpeg 进程将任意音频文件解码为 16-bit signed LE PCM 字节。
 */
public final class FfmpegPcmDecoder {

	private FfmpegPcmDecoder() {}

	public static byte[] decodeToPcm(Path inputFile, int sampleRate, int channels, int maxOutputBytes)
		throws IOException, InterruptedException {
		String ffmpeg = FfmpegLocator.resolveExecutable();
		if (ffmpeg == null) {
			throw new IOException("ffmpeg not found");
		}

		List<String> command = List.of(
			ffmpeg, "-y", "-i", inputFile.toAbsolutePath().toString(),
			"-vn", "-ar", String.valueOf(sampleRate), "-ac", String.valueOf(channels),
			"-acodec", "pcm_s16le", "-f", "s16le", "pipe:1"
		);
		Process process = new ProcessBuilder(command)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();

		ByteArrayOutputStream pcmOut = new ByteArrayOutputStream(Math.min(maxOutputBytes, 1 << 20));
		try (var input = process.getInputStream()) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) != -1) {
				pcmOut.write(buffer, 0, read);
				if (pcmOut.size() > maxOutputBytes) {
					process.destroyForcibly();
					throw new IOException("decoded PCM exceeds size limit (" + maxOutputBytes + " bytes)");
				}
			}
		}

		int exitCode = process.waitFor();
		if (exitCode != 0 || pcmOut.size() == 0) {
			throw new IOException("ffmpeg exited with code=" + exitCode + " outBytes=" + pcmOut.size());
		}
		return pcmOut.toByteArray();
	}
}
