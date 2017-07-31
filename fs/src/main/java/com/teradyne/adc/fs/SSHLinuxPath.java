package com.teradyne.adc.fs;

import java.nio.file.*;
import java.nio.file.FileSystem;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.attribute.PosixFileAttributes;
import java.io.*;
import java.net.URI;
import java.util.*;
/**
 * SSH Linux Path
 * @author wang holly
 *
 */
public class SSHLinuxPath implements Path {

	private final SSHLinuxFileSystem fs;
	private byte[] path;
    // array of offsets of elements in path (created lazily)
    private volatile int[] offsets;
    
	public SSHLinuxPath(SSHLinuxFileSystem fs, String path) {
		this.fs = fs;
		this.path = path.getBytes();
	}
	
	public SSHLinuxPath(SSHLinuxFileSystem fs, byte[] path) {
		this.fs = fs;
		this.path = path;
	}

	@Override
	public FileSystem getFileSystem() {
		return this.fs;
	}

	@Override
	public boolean isAbsolute() {
		return (path.length > 0 && path[0] == '/');
	}

	@Override
	public Path getRoot() {
        if (path.length > 0 && path[0] == '/') {
            return ((SSHLinuxFileSystem) getFileSystem()).rootDirectory();
        } else {
            return null;
        }
	}

	@Override
	public Path getFileName() {
		initOffsets();

        int count = offsets.length;

        // no elements so no name
        if (count == 0)
            return null;

        // one name element and no root component
        if (count == 1 && path.length > 0 && path[0] != '/')
            return this;

        int lastOffset = offsets[count-1];
        int len = path.length - lastOffset;
        byte[] result = new byte[len];
        System.arraycopy(path, lastOffset, result, 0, len);
        return new SSHLinuxPath(this.fs, result);
	}

	@Override
	public Path getParent() {
		initOffsets();

        int count = offsets.length;
        if (count == 0) {
            // no elements so no parent
            return null;
        }
        int len = offsets[count-1] - 1;
        if (len <= 0) {
            // parent is root only (may be null)
            return getRoot();
        }
        byte[] result = new byte[len];
        System.arraycopy(path, 0, result, 0, len);
        return new SSHLinuxPath(this.fs, result);
	}

	@Override
	public int getNameCount() {
        initOffsets();
        return offsets.length;
	}

	@Override
	public Path getName(int index) {
		initOffsets();
        if (index < 0)
            throw new IllegalArgumentException();
        if (index >= offsets.length)
            throw new IllegalArgumentException();

        int begin = offsets[index];
        int len;
        if (index == (offsets.length-1)) {
            len = path.length - begin;
        } else {
            len = offsets[index+1] - begin - 1;
        }

        // construct result
        byte[] result = new byte[len];
        System.arraycopy(path, begin, result, 0, len);
        return new SSHLinuxPath(this.fs, result);
	}

	@Override
	public Path subpath(int beginIndex, int endIndex) {
		 initOffsets();

	        if (beginIndex < 0)
	            throw new IllegalArgumentException();
	        if (beginIndex >= offsets.length)
	            throw new IllegalArgumentException();
	        if (endIndex > offsets.length)
	            throw new IllegalArgumentException();
	        if (beginIndex >= endIndex) {
	            throw new IllegalArgumentException();
	        }

	        // starting offset and length
	        int begin = offsets[beginIndex];
	        int len;
	        if (endIndex == offsets.length) {
	            len = path.length - begin;
	        } else {
	            len = offsets[endIndex] - begin - 1;
	        }

	        // construct result
	        byte[] result = new byte[len];
	        System.arraycopy(path, begin, result, 0, len);
	        return new SSHLinuxPath(this.fs, result);
	}

	@Override
	public boolean startsWith(Path other) {
		if (!(Objects.requireNonNull(other) instanceof SSHLinuxPath))
            return false;
		SSHLinuxPath that = (SSHLinuxPath)other;

        // other path is longer
        if (that.path.length > path.length)
            return false;

        int thisOffsetCount = getNameCount();
        int thatOffsetCount = that.getNameCount();

        // other path has no name elements
        if (thatOffsetCount == 0 && this.isAbsolute()) {
            return that.isEmpty() ? false : true;
        }

        // given path has more elements that this path
        if (thatOffsetCount > thisOffsetCount)
            return false;

        // same number of elements so must be exact match
        if ((thatOffsetCount == thisOffsetCount) &&
            (path.length != that.path.length)) {
            return false;
        }

        // check offsets of elements match
        for (int i=0; i<thatOffsetCount; i++) {
            Integer o1 = offsets[i];
            Integer o2 = that.offsets[i];
            if (!o1.equals(o2))
                return false;
        }

        // offsets match so need to compare bytes
        int i=0;
        while (i < that.path.length) {
            if (this.path[i] != that.path[i])
                return false;
            i++;
        }

        // final check that match is on name boundary
        if (i < path.length && this.path[i] != '/')
            return false;

        return true;
	}

	@Override
	public boolean startsWith(String other) {
		return startsWith(getFileSystem().getPath(other));
	}

	@Override
	public boolean endsWith(Path other) {
		 if (!(Objects.requireNonNull(other) instanceof SSHLinuxPath))
	            return false;
		 SSHLinuxPath that = (SSHLinuxPath)other;

	        int thisLen = path.length;
	        int thatLen = that.path.length;

	        // other path is longer
	        if (thatLen > thisLen)
	            return false;

	        // other path is the empty path
	        if (thisLen > 0 && thatLen == 0)
	            return false;

	        // other path is absolute so this path must be absolute
	        if (that.isAbsolute() && !this.isAbsolute())
	            return false;

	        int thisOffsetCount = getNameCount();
	        int thatOffsetCount = that.getNameCount();

	        // given path has more elements that this path
	        if (thatOffsetCount > thisOffsetCount) {
	            return false;
	        } else {
	            // same number of elements
	            if (thatOffsetCount == thisOffsetCount) {
	                if (thisOffsetCount == 0)
	                    return true;
	                int expectedLen = thisLen;
	                if (this.isAbsolute() && !that.isAbsolute())
	                    expectedLen--;
	                if (thatLen != expectedLen)
	                    return false;
	            } else {
	                // this path has more elements so given path must be relative
	                if (that.isAbsolute())
	                    return false;
	            }
	        }

	        // compare bytes
	        int thisPos = offsets[thisOffsetCount - thatOffsetCount];
	        int thatPos = that.offsets[0];
	        if ((thatLen - thatPos) != (thisLen - thisPos))
	            return false;
	        while (thatPos < thatLen) {
	            if (this.path[thisPos++] != that.path[thatPos++])
	                return false;
	        }

	        return true;
	}

	@Override
	public boolean endsWith(String other) {
		return endsWith(getFileSystem().getPath(other));
	}

	@Override
	public Path normalize() {
        final int count = getNameCount();
        if (count == 0 || isEmpty())
            return this;

        boolean[] ignore = new boolean[count];      // true => ignore name
        int[] size = new int[count];                // length of name
        int remaining = count;                      // number of names remaining
        boolean hasDotDot = false;                  // has at least one ..
        boolean isAbsolute = isAbsolute();

        // first pass:
        //   1. compute length of names
        //   2. mark all occurrences of "." to ignore
        //   3. and look for any occurrences of ".."
        for (int i=0; i<count; i++) {
            int begin = offsets[i];
            int len;
            if (i == (offsets.length-1)) {
                len = path.length - begin;
            } else {
                len = offsets[i+1] - begin - 1;
            }
            size[i] = len;

            if (path[begin] == '.') {
                if (len == 1) {
                    ignore[i] = true;  // ignore  "."
                    remaining--;
                }
                else {
                    if (path[begin+1] == '.')   // ".." found
                        hasDotDot = true;
                }
            }
        }

        // multiple passes to eliminate all occurrences of name/..
        if (hasDotDot) {
            int prevRemaining;
            do {
                prevRemaining = remaining;
                int prevName = -1;
                for (int i=0; i<count; i++) {
                    if (ignore[i])
                        continue;

                    // not a ".."
                    if (size[i] != 2) {
                        prevName = i;
                        continue;
                    }

                    int begin = offsets[i];
                    if (path[begin] != '.' || path[begin+1] != '.') {
                        prevName = i;
                        continue;
                    }

                    // ".." found
                    if (prevName >= 0) {
                        // name/<ignored>/.. found so mark name and ".." to be
                        // ignored
                        ignore[prevName] = true;
                        ignore[i] = true;
                        remaining = remaining - 2;
                        prevName = -1;
                    } else {
                        // Case: /<ignored>/.. so mark ".." as ignored
                        if (isAbsolute) {
                            boolean hasPrevious = false;
                            for (int j=0; j<i; j++) {
                                if (!ignore[j]) {
                                    hasPrevious = true;
                                    break;
                                }
                            }
                            if (!hasPrevious) {
                                // all proceeding names are ignored
                                ignore[i] = true;
                                remaining--;
                            }
                        }
                    }
                }
            } while (prevRemaining > remaining);
        }

        // no redundant names
        if (remaining == count)
            return this;

        // corner case - all names removed
        if (remaining == 0) {
            return isAbsolute ? this.fs.rootDirectory() : emptyPath();
        }

        // compute length of result
        int len = remaining - 1;
        if (isAbsolute)
            len++;

        for (int i=0; i<count; i++) {
            if (!ignore[i])
                len += size[i];
        }
        byte[] result = new byte[len];

        // copy names into result
        int pos = 0;
        if (isAbsolute)
            result[pos++] = '/';
        for (int i=0; i<count; i++) {
            if (!ignore[i]) {
                System.arraycopy(path, offsets[i], result, pos, size[i]);
                pos += size[i];
                if (--remaining > 0) {
                    result[pos++] = '/';
                }
            }
        }
        return new SSHLinuxPath(this.fs, result);
	}

	@Override
	public Path resolve(Path obj) {
		byte[] other = toSSHLinuxPath(obj).path;
        if (other.length > 0 && other[0] == '/')
            return ((SSHLinuxPath)obj);
        byte[] result = resolve(path, other);
        return new SSHLinuxPath(this.fs, result);
	}

	@Override
	public Path resolve(String other) {
		return resolve(getFileSystem().getPath(other));
	}

	@Override
	public Path resolveSibling(Path other) {
		if (other == null)
            throw new NullPointerException();
        Path parent = getParent();
        return (parent == null) ? other : parent.resolve(other);
	}

	@Override
	public Path resolveSibling(String other) {
		return resolveSibling(this.fs.getPath(other));
	}

	@Override
	public Path relativize(Path obj) {
		SSHLinuxPath other = toSSHLinuxPath(obj);
        if (other.equals(this))
            return emptyPath();

        // can only relativize paths of the same type
        if (this.isAbsolute() != other.isAbsolute())
            throw new IllegalArgumentException("'other' is different type of Path");

        // this path is the empty path
        if (this.isEmpty())
            return other;

        int bn = this.getNameCount();
        int cn = other.getNameCount();

        // skip matching names
        int n = (bn > cn) ? cn : bn;
        int i = 0;
        while (i < n) {
            if (!this.getName(i).equals(other.getName(i)))
                break;
            i++;
        }

        int dotdots = bn - i;
        if (i < cn) {
            // remaining name components in other
            SSHLinuxPath remainder = (SSHLinuxPath) other.subpath(i, cn);
            if (dotdots == 0)
                return remainder;

            // other is the empty path
            boolean isOtherEmpty = other.isEmpty();

            // result is a  "../" for each remaining name in base
            // followed by the remaining names in other. If the remainder is
            // the empty path then we don't add the final trailing slash.
            int len = dotdots*3 + remainder.path.length;
            if (isOtherEmpty) {
                assert remainder.isEmpty();
                len--;
            }
            byte[] result = new byte[len];
            int pos = 0;
            while (dotdots > 0) {
                result[pos++] = (byte)'.';
                result[pos++] = (byte)'.';
                if (isOtherEmpty) {
                    if (dotdots > 1) result[pos++] = (byte)'/';
                } else {
                    result[pos++] = (byte)'/';
                }
                dotdots--;
            }
            System.arraycopy(remainder.path, 0, result, pos, remainder.path.length);
            return new SSHLinuxPath(this.fs, result);
        } else {
            // no remaining names in other so result is simply a sequence of ".."
            byte[] result = new byte[dotdots*3 - 1];
            int pos = 0;
            while (dotdots > 0) {
                result[pos++] = (byte)'.';
                result[pos++] = (byte)'.';
                // no tailing slash at the end
                if (dotdots > 1)
                    result[pos++] = (byte)'/';
                dotdots--;
            }
            return new SSHLinuxPath(this.fs, result);
        }
	}

	@Override
	public URI toUri() {
		return SSHLinuxUriUtils.toUri(this);
	}

	@Override
	public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        return new SSHLinuxPath(this.fs,
            resolve(this.fs.defaultDirectory(), path));
	}

	@Override
	public Path toRealPath(LinkOption... options) throws IOException {
	        SSHLinuxPath absolute = (SSHLinuxPath) toAbsolutePath();

	        // if resolving links then use realpath
	        if (followLinks(options)) {
	            try {
	                byte[] rp = absolute.asByteArray();
	                return new SSHLinuxPath(this.fs, rp);
	            } catch (Exception x) {
	                throw new IOException(x);
	            }
	        }

	        // if not resolving links then eliminate "." and also ".."
	        // where the previous element is not a link.
	        SSHLinuxPath result = fs.rootDirectory();
	        for (int i=0; i<absolute.getNameCount(); i++) {
	            SSHLinuxPath element = (SSHLinuxPath) absolute.getName(i);

	            // eliminate "."
	            if ((element.asByteArray().length == 1) && (element.asByteArray()[0] == '.'))
	                continue;

	            // cannot eliminate ".." if previous element is a link
	            if ((element.asByteArray().length == 2) && (element.asByteArray()[0] == '.') &&
	                (element.asByteArray()[1] == '.'))
	            {
	                PosixFileAttributes attrs = null;
	                try {
	                    attrs = ((SSHLinuxFileSystem) result.getFileSystem()).getFileAttributes(result,false);
	                } catch (Exception x) {
	                    throw new IOException(x);
	                }
	                if (!attrs.isSymbolicLink()) {
	                    result = (SSHLinuxPath) result.getParent();
	                    if (result == null) {
	                        result = fs.rootDirectory();
	                    }
	                    continue;
	                }
	            }
	            result = (SSHLinuxPath) result.resolve(element);
	        }

	        // check file exists (without following links)
	        try {
                ((SSHLinuxFileSystem) result.getFileSystem()).getFileAttributes(result,false);
            } catch (Exception x) {
                throw new IOException(x);
            }
	        return result;
	}

	/**
	 * The remote file/directory will be download and saved in local path {@link java.io.tmpdir}.
	 * The directory structure of this file/directory is also automatically created same as remote.
	 * Then return {@link java.io.File} of this local file/directory.
	 */
	@Override
	public File toFile() {
		try {
			String local = this.fs.localize(this);
			return new SSHLinuxFile(this, local);
		} catch (IOException e) {
			return null;
		}
	}

    @Override
    public String toString() {
        return new String(this.path);
    }
	
	@Override
	public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
        if (watcher == null)
            throw new NullPointerException();
        if (!(watcher instanceof SSHLinuxWatchService))
            throw new ProviderMismatchException();
        return ((SSHLinuxWatchService)watcher).register(this, events, modifiers);
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
		return register(watcher, events, new WatchEvent.Modifier[0]);
	}

	@Override
	public Iterator<Path> iterator() {
		 return new Iterator<Path>() {
	            private int i = 0;
	            @Override
	            public boolean hasNext() {
	                return (i < getNameCount());
	            }
	            @Override
	            public Path next() {
	                if (i < getNameCount()) {
	                    Path result = getName(i);
	                    i++;
	                    return result;
	                } else {
	                    throw new NoSuchElementException();
	                }
	            }
	            @Override
	            public void remove() {
	                throw new UnsupportedOperationException();
	            }
	        };
	}

	@Override
	public int compareTo(Path other) {
		int len1 = path.length;
        int len2 = ((SSHLinuxPath) other).path.length;

        int n = Math.min(len1, len2);
        byte v1[] = path;
        byte v2[] = ((SSHLinuxPath) other).path;

        int k = 0;
        while (k < n) {
            int c1 = v1[k] & 0xff;
            int c2 = v2[k] & 0xff;
            if (c1 != c2) {
                return c1 - c2;
            }
           k++;
        }
        return len1 - len2;
	}

	private void initOffsets() {
        if (offsets == null) {
            int count, index;

            // count names
            count = 0;
            index = 0;
            if (isEmpty()) {
                // empty path has one name
                count = 1;
            } else {
                while (index < path.length) {
                    byte c = path[index++];
                    if (c != '/') {
                        count++;
                        while (index < path.length && path[index] != '/')
                            index++;
                    }
                }
            }

            // populate offsets
            int[] result = new int[count];
            count = 0;
            index = 0;
            while (index < path.length) {
                byte c = path[index];
                if (c == '/') {
                    index++;
                } else {
                    result[count++] = index++;
                    while (index < path.length && path[index] != '/')
                        index++;
                }
            }
            synchronized (this) {
                if (offsets == null)
                    offsets = result;
            }
        }
    }
	
    private boolean isEmpty() {
        return path.length == 0;
    }
	
    // returns an empty path
    private SSHLinuxPath emptyPath() {
        return new SSHLinuxPath(this.fs, new byte[0]);
    }
    
    // Checks that the given file is a SSHLinuxPath
    static SSHLinuxPath toSSHLinuxPath(Path obj) {
        if (obj == null)
            throw new NullPointerException();
        if (!(obj instanceof SSHLinuxPath))
            throw new ProviderMismatchException();
        return (SSHLinuxPath)obj;
    }
    
    private static byte[] resolve(byte[] base, byte[] child) {
        int baseLength = base.length;
        int childLength = child.length;
        if (childLength == 0)
            return base;
        if (baseLength == 0 || child[0] == '/')
            return child;
        byte[] result;
        if (baseLength == 1 && base[0] == '/') {
            result = new byte[childLength + 1];
            result[0] = '/';
            System.arraycopy(child, 0, result, 1, childLength);
        } else {
            result = new byte[baseLength + 1 + childLength];
            System.arraycopy(base, 0, result, 0, baseLength);
            result[base.length] = '/';
            System.arraycopy(child, 0, result, baseLength+1, childLength);
        }
        return result;
    }
	
	byte[] asByteArray() {
		return this.path;
	}
	
    static boolean followLinks(LinkOption... options) {
        boolean followLinks = true;
        for (LinkOption option: options) {
            if (option == LinkOption.NOFOLLOW_LINKS) {
                followLinks = false;
            } else if (option == null) {
                throw new NullPointerException();
            } else {
                throw new AssertionError("Should not get here");
            }
        }
        return followLinks;
    }

    /**
     * Get input stream from SSH Linux Path.
     * @return Input stream used to read content of remote file
     * @throws IOException
     */
    public InputStream getInputStream() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		this.fs.toOutputStream(new String(path), out);
		byte[] buffer = out.toByteArray();
		return new ByteArrayInputStream(buffer);
	}
	/**
	 * Get output stream from SSH Linux Path
	 * @return Output stream used to write remote file
	 */
	public OutputStream getOutputStream() {
		return new SSHOutputStream(this);
	}
}
