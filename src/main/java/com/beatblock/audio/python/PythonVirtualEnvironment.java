package com.beatblock.audio.python;

import com.beatblock.audio.process.ProcessIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * BeatBlock 音频分析专用 Python 虚拟环境（config/beatblock/analyzer/.venv）。
 * <p>
 * 与系统 Python 隔离，避免 pip 安装污染用户全局环境。
 */
public final class PythonVirtualEnvironment {

	public static final String VENV_DIR_NAME = ".venv";

	private PythonVirtualEnvironment() {}

	public static Path venvDirectory(Path beatblockConfigDir) {
		return beatblockConfigDir.resolve("analyzer").resolve(VENV_DIR_NAME);
	}

	public static Path venvPythonExecutable(Path beatblockConfigDir) {
		Path venv = venvDirectory(beatblockConfigDir);
		Path python = isWindows()
			? venv.resolve("Scripts").resolve("python.exe")
			: venv.resolve("bin").resolve("python");
		return Files.isRegularFile(python) ? python : null;
	}

	public static boolean isReady(Path beatblockConfigDir, PythonEnvironmentDiagnostics diagnostics) {
		Path python = venvPythonExecutable(beatblockConfigDir);
		return python != null && diagnostics.isUsablePythonForAnalyzer(python.toString());
	}

	/**
	 * 使用系统 Python 创建虚拟环境；若已存在且可用则直接返回其解释器路径。
	 *
	 * @return venv 内 python 可执行文件路径，失败时返回 null
	 */
	public static String ensureCreated(Path beatblockConfigDir, String basePythonExe) throws IOException {
		Path existing = venvPythonExecutable(beatblockConfigDir);
		if (existing != null) {
			return existing.toAbsolutePath().toString();
		}

		Path venvDir = venvDirectory(beatblockConfigDir);
		Files.createDirectories(venvDir.getParent());

		Process create = new ProcessBuilder(
			basePythonExe,
			"-m",
			"venv",
			venvDir.toAbsolutePath().toString()
		).redirectErrorStream(true).start();
		String output = ProcessIo.readProcessOutput(create);
		int code = ProcessIo.waitProcess(create);
		if (code != 0) {
			throw new IOException("venv 创建失败（退出码 " + code + "）：" + ProcessIo.sanitizeProcessOutput(output));
		}

		Path created = venvPythonExecutable(beatblockConfigDir);
		if (created == null) {
			throw new IOException("venv 创建完成但未找到 Python 解释器：" + venvDir);
		}
		return created.toAbsolutePath().toString();
	}

	private static boolean isWindows() {
		String os = System.getProperty("os.name", "").toLowerCase();
		return os.contains("win");
	}
}
