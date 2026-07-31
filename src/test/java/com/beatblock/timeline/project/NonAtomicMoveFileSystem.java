package com.beatblock.timeline.project;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 测试用 FileSystem：包装默认文件系统，但拒绝 ATOMIC_MOVE，用来验证保存回退逻辑。
 */
class NonAtomicMoveFileSystem extends FileSystem {

	private final FileSystem delegate;
	private final Provider provider;

	NonAtomicMoveFileSystem(FileSystem delegate) {
		this.delegate = delegate;
		this.provider = new Provider(delegate.provider(), this);
	}

	static NonAtomicMoveFileSystem ofDefault() {
		return new NonAtomicMoveFileSystem(FileSystems.getDefault());
	}

	@Override
	public FileSystemProvider provider() {
		return provider;
	}

	@Override
	public void close() throws IOException {
		delegate.close();
	}

	@Override
	public boolean isOpen() {
		return delegate.isOpen();
	}

	@Override
	public boolean isReadOnly() {
		return delegate.isReadOnly();
	}

	@Override
	public String getSeparator() {
		return delegate.getSeparator();
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return delegate.getRootDirectories();
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		return delegate.getFileStores();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return delegate.supportedFileAttributeViews();
	}

	@Override
	public Path getPath(String first, String... more) {
		return new PathWrapper(delegate.getPath(first, more), this);
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		return delegate.getPathMatcher(syntaxAndPattern);
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		return delegate.getUserPrincipalLookupService();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		return delegate.newWatchService();
	}

	static class Provider extends FileSystemProvider {

		private final FileSystemProvider delegate;
		private final NonAtomicMoveFileSystem fs;

		Provider(FileSystemProvider delegate, NonAtomicMoveFileSystem fs) {
			this.delegate = delegate;
			this.fs = fs;
		}

		@Override
		public String getScheme() {
			return "nonatomic";
		}

		@Override
		public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
			throw new FileSystemAlreadyExistsException();
		}

		@Override
		public FileSystem getFileSystem(URI uri) {
			return fs;
		}

		@Override
		public Path getPath(URI uri) {
			Path real = Paths.get(uri);
			return new PathWrapper(real, fs);
		}

		@Override
		public void move(Path source, Path target, CopyOption... options) throws IOException {
			for (CopyOption opt : options) {
				if (opt == StandardCopyOption.ATOMIC_MOVE) {
					throw new java.nio.file.AtomicMoveNotSupportedException(
							unwrap(source).toString(),
							unwrap(target).toString(),
							"atomic move not supported in test filesystem"
					);
				}
			}
			delegate.move(unwrap(source), unwrap(target), options);
		}

		private static Path unwrap(Path p) {
			return p instanceof PathWrapper ? ((PathWrapper) p).delegate : p;
		}

		@Override
		public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
			return delegate.newByteChannel(unwrap(path), options, attrs);
		}

		@Override
		public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
			DirectoryStream<Path> real = delegate.newDirectoryStream(unwrap(dir), filter);
			return new DirectoryStream<>() {
				@Override
				public Iterator<Path> iterator() {
					List<Path> wrapped = new ArrayList<>();
					real.forEach(p -> wrapped.add(new PathWrapper(p, fs)));
					return wrapped.iterator();
				}
				@Override
				public void close() throws IOException { real.close(); }
			};
		}

		@Override
		public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
			delegate.createDirectory(unwrap(dir), attrs);
		}

		@Override
		public void delete(Path path) throws IOException {
			delegate.delete(unwrap(path));
		}

		@Override
		public void copy(Path source, Path target, CopyOption... options) throws IOException {
			delegate.copy(unwrap(source), unwrap(target), options);
		}

		@Override
		public boolean isSameFile(Path path, Path path2) throws IOException {
			return delegate.isSameFile(unwrap(path), unwrap(path2));
		}

		@Override
		public boolean isHidden(Path path) throws IOException {
			return delegate.isHidden(unwrap(path));
		}

		@Override
		public FileStore getFileStore(Path path) throws IOException {
			return delegate.getFileStore(unwrap(path));
		}

		@Override
		public void checkAccess(Path path, AccessMode... modes) throws IOException {
			delegate.checkAccess(unwrap(path), modes);
		}

		@Override
		public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
			return delegate.getFileAttributeView(unwrap(path), type, options);
		}

		@Override
		public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
			return delegate.readAttributes(unwrap(path), type, options);
		}

		@Override
		public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
			return delegate.readAttributes(unwrap(path), attributes, options);
		}

		@Override
		public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
			delegate.setAttribute(unwrap(path), attribute, value, options);
		}
	}

	static class PathWrapper implements Path {

		final Path delegate;
		private final NonAtomicMoveFileSystem fs;

		PathWrapper(Path delegate, NonAtomicMoveFileSystem fs) {
			this.delegate = delegate;
			this.fs = fs;
		}

		@Override
		public FileSystem getFileSystem() {
			return fs;
		}

		@Override
		public FileSystemProvider provider() {
			return fs.provider();
		}

		@Override
		public boolean isAbsolute() {
			return delegate.isAbsolute();
		}

		@Override
		public Path getRoot() {
			Path r = delegate.getRoot();
			return r == null ? null : new PathWrapper(r, fs);
		}

		@Override
		public Path getFileName() {
			Path f = delegate.getFileName();
			return f == null ? null : new PathWrapper(f, fs);
		}

		@Override
		public Path getParent() {
			Path p = delegate.getParent();
			return p == null ? null : new PathWrapper(p, fs);
		}

		@Override
		public int getNameCount() {
			return delegate.getNameCount();
		}

		@Override
		public Path getName(int index) {
			return new PathWrapper(delegate.getName(index), fs);
		}

		@Override
		public Path subpath(int beginIndex, int endIndex) {
			return new PathWrapper(delegate.subpath(beginIndex, endIndex), fs);
		}

		@Override
		public boolean startsWith(Path other) {
			return delegate.startsWith(unwrap(other));
		}

		@Override
		public boolean startsWith(String other) {
			return delegate.startsWith(other);
		}

		@Override
		public boolean endsWith(Path other) {
			return delegate.endsWith(unwrap(other));
		}

		@Override
		public boolean endsWith(String other) {
			return delegate.endsWith(other);
		}

		@Override
		public Path normalize() {
			return new PathWrapper(delegate.normalize(), fs);
		}

		@Override
		public Path resolve(Path other) {
			return new PathWrapper(delegate.resolve(unwrap(other)), fs);
		}

		@Override
		public Path resolve(String other) {
			return new PathWrapper(delegate.resolve(other), fs);
		}

		@Override
		public Path resolveSibling(Path other) {
			return new PathWrapper(delegate.resolveSibling(unwrap(other)), fs);
		}

		@Override
		public Path resolveSibling(String other) {
			return new PathWrapper(delegate.resolveSibling(other), fs);
		}

		@Override
		public Path relativize(Path other) {
			return new PathWrapper(delegate.relativize(unwrap(other)), fs);
		}

		@Override
		public URI toUri() {
			return delegate.toUri();
		}

		@Override
		public Path toAbsolutePath() {
			return new PathWrapper(delegate.toAbsolutePath(), fs);
		}

		@Override
		public Path toRealPath(LinkOption... options) throws IOException {
			return new PathWrapper(delegate.toRealPath(options), fs);
		}

		@Override
		public java.io.File toFile() {
			return delegate.toFile();
		}

		@Override
		public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
			return delegate.register(watcher, events, modifiers);
		}

		@Override
		public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
			return delegate.register(watcher, events);
		}

		@Override
		public Iterator<Path> iterator() {
			return new Iterator<>() {
				private final Iterator<Path> it = delegate.iterator();
				@Override public boolean hasNext() { return it.hasNext(); }
				@Override public Path next() { return new PathWrapper(it.next(), fs); }
			};
		}

		@Override
		public int compareTo(Path other) {
			return delegate.compareTo(unwrap(other));
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof PathWrapper) {
				return delegate.equals(((PathWrapper) obj).delegate);
			}
			return delegate.equals(obj);
		}

		@Override
		public int hashCode() {
			return delegate.hashCode();
		}

		@Override
		public String toString() {
			return delegate.toString();
		}

		private static Path unwrap(Path p) {
			return p instanceof PathWrapper ? ((PathWrapper) p).delegate : p;
		}
	}
}
