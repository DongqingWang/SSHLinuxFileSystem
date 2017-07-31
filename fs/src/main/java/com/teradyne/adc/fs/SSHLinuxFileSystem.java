package com.teradyne.adc.fs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.spi.FileSystemProvider;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.scp.ScpClient;
import org.apache.sshd.client.scp.ScpClient.Option;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.scp.ScpTimestamp;
import org.apache.sshd.common.util.io.NoCloseOutputStream;

/**
 * Implementation of  Linux FileSystem though SSH protocol implementations.
 */

public class SSHLinuxFileSystem
    extends FileSystem
{
	private Utils util;
	private Map<String, SoftReference<SSHLinuxFileAttributes>> fileattrcache;
	
    //private final UnixFileSystemProvider provider;
    private final byte[] defaultDirectory;
    private final SSHLinuxPath rootDirectory;
    
    private SSHClient client;
    private String localDir;
    
    public SSHLinuxFileSystem(String dir, String user, String password, String host, int port) throws IOException {
        this.defaultDirectory = dir.getBytes();
        if (this.defaultDirectory[0] != '/') {
            throw new RuntimeException("default directory must be absolute");
        }

        // the root directory
        this.rootDirectory = new SSHLinuxPath(this, "/");
        this.client = new SSHClient(user, password, host, port);
        localDir = System.getProperty("java.io.tmpdir");
        fileattrcache = new ConcurrentHashMap<String, SoftReference<SSHLinuxFileAttributes>>();
        this.util = new Utils();
    }

    private class SSHClient
    {
    	private String user;
    	private String password;
    	private String host;
    	private int port;
    	
        private SshClient client;
        private ClientSession session;
    	
		public SSHClient(String user, String password, String host, int port) throws IOException {
			this.user = user;
			this.password = password;
			this.host = host;
			this.port = port;
	        this.start();
		}
		
		public void exec(String cmd, OutputStream out) throws IOException {	
			ChannelExec channel = getSession().createExecChannel(cmd); 
			channel.setOut(new NoCloseOutputStream(out));

			channel.open();
			channel.waitFor(new ArrayList<ClientChannelEvent>(){
					private static final long serialVersionUID = 1L;
					{add( ClientChannelEvent.CLOSED);}
				}, 0);
			channel.close(false);		
		}
		
		public void scpFrom(String remotePath, String localPath, Boolean isDirectory) throws IOException {
			ScpClient scp = getSession().createScpClient();
			if(isDirectory) {
				scp.download(remotePath, localPath, ScpClient.Option.TargetIsDirectory, ScpClient.Option.Recursive);
			} else {
				scp.download(remotePath, localPath);
			}
		}
		
		public void scpFrom(String remotePath, OutputStream out) throws IOException {
			ScpClient scp = getSession().createScpClient();
			scp.download(remotePath, out);
		}
		
		public void scpTo(InputStream in, String remote, long size, Collection<PosixFilePermission> perms, ScpTimestamp time) throws IOException {
			ScpClient scp = getSession().createScpClient();
			scp.upload(in, remote, size, perms, time);
		}
		
		public void scpTo(String remotePath, String localPath, Option... options) throws IOException {
			ScpClient scp = getSession().createScpClient();
			scp.upload(localPath, remotePath, options);
		}

		public boolean isOpen() {
			return this.client.isOpen();
		}
		
		public void close() throws IOException {
			if(session.isOpen()) {
				session.close();
			}
			if(client.isOpen()) {
				this.client.stop();
				this.client.close();
			}
		}

		private ClientSession getSession() throws IOException {
			if(session !=null && session.isOpen()) {
				return session;
			}
			start();
			return session;
		}
		
		private void start() throws IOException {
	    	if(client!= null && client.isOpen() && session != null && session.isOpen()) {
	    		return;
	    	}
	    	client = SshClient.setUpDefaultClient();
	    	client.start();
	    	ConnectFuture con = client.connect(user, host, port);
			con.await();
			session = con.getSession();
			session.addPasswordIdentity(password);
			session.auth().verify().await();
		}
    }
    
    public class Utils
    {
    	private final static String COMMAND_CP = "cp ";
    	private final static String COMMAND_MV = "mv ";
    	private final static String COMMAND_MD5SUM = "md5sum ";
    	
    	Utils() {
			// Package-private
		}
    	/**
    	 * This method will do copy action in remote system
    	 * @param src the full path of source file to be copied in remote sys
    	 * @param dst the full path of target to put copied file in remote sys
    	 * @return information returned by SSH
    	 * @throws IOException
    	 */
    	public String copy(String src, String dst) throws IOException {
    		OutputStream out = new ByteArrayOutputStream();
    		SSHLinuxFileSystem.this.client.exec(COMMAND_CP + src + " " + dst, out);
    		return out.toString();
    	}
    	/**
    	 * This method will do move action in remote system
    	 * @param src the full path of source file to be moved in remote sys
    	 * @param dst the full path of target to move file to in remote sys
    	 * @return information returned by SSH
    	 * @throws IOException
    	 */
    	public String move(String src, String dst) throws IOException {
    		OutputStream out = new ByteArrayOutputStream();
    		SSHLinuxFileSystem.this.client.exec(COMMAND_MV + src + " " + dst, out);
    		return out.toString();
    	}
    	
    	public String getMD5(String path) throws IOException {
    		OutputStream out = new ByteArrayOutputStream();
    		SSHLinuxFileSystem.this.client.exec(COMMAND_MD5SUM + path, out);
    		String result = out.toString();
    		if(result.equalsIgnoreCase("") || result.contains("No such file or directory") || result.contains("Is a directory")) {
    			throw new IOException("No such file or is a directory.");
    		}
    		String[] md5 = result.split("\\s+");
    		if(md5.length != 2) {
    			throw new IOException("Incomplete information of MD5.");
    		}
    		return md5[0];
    	}
    }
    
    public Utils getUtils() {
    	return this.util;
    }
    
    // package-private
    byte[] defaultDirectory() {
        return defaultDirectory;
    }

    SSHLinuxPath rootDirectory() {
        return rootDirectory;
    }

    static List<String> standardFileAttributeViews() {
        return Arrays.asList("basic", "posix", "unix", "owner");
    }

    @Override
    public final FileSystemProvider provider() {
    	throw new UnsupportedOperationException("provider() N/A");
    }

    @Override
    public final String getSeparator() {
        return "/";
    }

    @Override
    public final boolean isOpen() {
    	return client.isOpen();
    }

    @Override
    public final boolean isReadOnly() {
        return false;
    }

    @Override
    public final void close() throws IOException {
    	client.close();
    }
    
    @Override
    public final Iterable<Path> getRootDirectories() {
        final List<Path> allowedList =
           Collections.unmodifiableList(Arrays.asList((Path)rootDirectory));
        return new Iterable<Path>() {
            public Iterator<Path> iterator() {
                try {
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null)
                        sm.checkRead(rootDirectory.toString());
                    return allowedList.iterator();
                } catch (SecurityException x) {
                    List<Path> disallowed = Collections.emptyList();
                    return disallowed.iterator();
                }
            }
        };
    }

    public Iterable<SSHLinuxMountEntry> getMountEntries()
    {
    	return getMountEntries("/etc/mtab");
    }
    
    Iterable<SSHLinuxMountEntry> getMountEntries(String fstab) {
        ArrayList<SSHLinuxMountEntry> entries = new ArrayList<>();
        try {
            client.start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            client.exec(fstab, out);
            //TODO:
        } catch (Exception e) {
            // nothing to do
        }
        return entries;
    }

    /**
     * Returns a FileStore to represent the file system for the given mount
     * mount.
     */
    public FileStore getFileStore(SSHLinuxMountEntry entry) throws IOException {
		return null;
    	
    }

    /**
     * Iterator returned by getFileStores method.
     */
    private class FileStoreIterator implements Iterator<FileStore> {
        private final Iterator<SSHLinuxMountEntry> entries;
        private FileStore next;

        FileStoreIterator() {
            this.entries = getMountEntries().iterator();
        }

        private FileStore readNext() {
            assert Thread.holdsLock(this);
            for (;;) {
                if (!entries.hasNext())
                    return null;
                SSHLinuxMountEntry entry = entries.next();

                // skip entries with the "ignore" option
                if (entry.isIgnored())
                    continue;

                // check permission to read mount point
                SecurityManager sm = System.getSecurityManager();
                if (sm != null) {
                    try {
                        sm.checkRead(entry.dir());
                    } catch (SecurityException x) {
                        continue;
                    }
                }
                try {
                    return getFileStore(entry);
                } catch (IOException ignore) {
                    // ignore as per spec
                }
            }
        }

        @Override
        public synchronized boolean hasNext() {
            if (next != null)
                return true;
            next = readNext();
            return next != null;
        }

        @Override
        public synchronized FileStore next() {
            if (next == null)
                next = readNext();
            if (next == null) {
                throw new NoSuchElementException();
            } else {
                FileStore result = next;
                next = null;
                return result;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public final Iterable<FileStore> getFileStores() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                sm.checkPermission(new RuntimePermission("getFileStoreAttributes"));
            } catch (SecurityException se) {
                return Collections.emptyList();
            }
        }
        return new Iterable<FileStore>() {
            public Iterator<FileStore> iterator() {
                return new FileStoreIterator();
            }
        };
    }

    @Override
    public final Path getPath(String first, String... more) {
        String path;
        if (more.length == 0) {
            path = first;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(first);
            for (String segment: more) {
                if (segment.length() > 0) {
                    if (sb.length() > 0)
                        sb.append('/');
                    sb.append(segment);
                }
            }
            path = sb.toString();
        }
        return new SSHLinuxPath(this, path);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndInput) {
        int pos = syntaxAndInput.indexOf(':');
        if (pos <= 0 || pos == syntaxAndInput.length())
            throw new IllegalArgumentException();
        String syntax = syntaxAndInput.substring(0, pos);
        String input = syntaxAndInput.substring(pos+1);

        String expr;
        if (syntax.equals(GLOB_SYNTAX)) {
            expr = Globs.toUnixRegexPattern(input);
        } else {
            if (syntax.equals(REGEX_SYNTAX)) {
                expr = input;
            } else {
                throw new UnsupportedOperationException("Syntax '" + syntax +
                    "' not recognized");
            }
        }

        // return matcher
        final Pattern pattern = Pattern.compile(expr);

        return new PathMatcher() {
            @Override
            public boolean matches(Path path) {
                return pattern.matcher(path.toString()).matches();
            }
        };
    }

    private static final String GLOB_SYNTAX = "glob";
    private static final String REGEX_SYNTAX = "regex";
    private static final Pattern idP = Pattern.compile("uid=(\\d+)\\(.*?\\) gid=(\\d+)\\(.*?\\) groups=(\\d+)\\(.*?\\)\n");
    
    @Override
    public final UserPrincipalLookupService getUserPrincipalLookupService() {
        return new UserPrincipalLookupService() {
			
			@Override
			public UserPrincipal lookupPrincipalByName(String name) throws IOException {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				client.exec("id " + name, out);
				
				Matcher result = idP.matcher(out.toString());

				if(result.matches()) {
					try {
						return new User(Integer.parseInt(result.group(1)), name);
					} catch (NumberFormatException e) {
						throw new UserPrincipalNotFoundException(name);
					}

				}
				out = new ByteArrayOutputStream();
				client.exec("getenv passwd " + name, out);
				//login_name:passwd:UID:GID:user_name:home_directory:shell
				String[] split = out.toString().split(":");
				if(split.length == 7) {
					try {
						return new User(Integer.parseInt(split[2]), name);
					} catch (NumberFormatException e) {
						throw new UserPrincipalNotFoundException(name);
					}
					
				}
				throw new UserPrincipalNotFoundException(name);
			}
			
			@Override
			public GroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
				//group_name:passwd:GID:use_list
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				client.exec("getenv group " + group, out);
				String[] split = out.toString().split(":");
				if(split.length == 4) {
					try {
						return new Group(Integer.parseInt(split[2]), group);
					} catch (NumberFormatException e) {
						throw new UserPrincipalNotFoundException(group);
					}
				}
				throw new UserPrincipalNotFoundException(group);
			}
		};
    }
    
    static class User implements UserPrincipal {
        private final int id;             // uid or gid
        private final boolean isGroup;
        private final String name;

        private User(int id, boolean isGroup, String name) {
            this.id = id;
            this.isGroup = isGroup;
            this.name = name;
        }

        User(int id, String name) {
            this(id, false, name);
        }

        int uid() {
            if (isGroup)
                throw new AssertionError();
            return id;
        }

        int gid() {
            if (isGroup)
                return id;
            throw new AssertionError();
        }

        boolean isSpecial() {
            return id == -1;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!(obj instanceof User))
                return false;
            User other = (User)obj;
            if ((this.id != other.id) ||
                (this.isGroup != other.isGroup)) {
                return false;
            }
            // specials
            if (this.id == -1 && other.id == -1)
                return this.name.equals(other.name);

            return true;
        }

        @Override
        public int hashCode() {
            return (id != -1) ? id : name.hashCode();
        }
    }

    static class Group extends User implements GroupPrincipal {
        Group(int id, String name) {
            super(id, true, name);
        }
    }

	@Override
	public Set<String> supportedFileAttributeViews() {
		return new HashSet<String>(){
			private static final long serialVersionUID = 1L;
			{ add("linux");}};
	}

	@Override
	public WatchService newWatchService() throws IOException {
		//TODO Implement in future
		return null;
	}
	
	public PosixFileAttributes getFileAttributes(Path path) throws IOException {
		return getFileAttributes(path, false);
	}
	
	public PosixFileAttributes getFileAttributes(Path path, boolean touch) throws IOException {
		SoftReference<SSHLinuxFileAttributes> ref = fileattrcache.get(path.toString());
		if(ref != null) {
			 SSHLinuxFileAttributes attr = ref.get();
			 if(attr != null) {
				 return attr;
			 }
		}
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream out2 = new ByteArrayOutputStream();//last access time
		client.exec("ls -ld --time-style '+%Y-%m-%d %H:%M:%S' " + path.toString(), out);
		String result = out.toString().replaceAll(path.toString() + "\n", "");
		if(result.equalsIgnoreCase("") || result.contains("No such file or directory")) {
			if(touch) {
				this.client.exec("touch " + path, null);
				out = new ByteArrayOutputStream();
				client.exec("ls -ld --time-style '+%Y-%m-%d %H:%M:%S' " + path.toString(), out);
				result = out.toString().replaceAll(path.toString() + "\n", "");
			} else {
				throw new IOException("No such file or directory: " + path);
			}
		}
		client.exec("ls -ldu --time-style '+%Y-%m-%d %H:%M:%S' "+ path.toString(), out2);
		String result2 = out2.toString().replaceAll(path.toString() + "\n", "");
		//drwxr-xr-x　3　user　group　102　2017/05/01 13:24:56　Filename
		//property inode user group size(byte) month date year/time filename
		
		String[] split = result.split(" ");
		String[] split2 = result2.split(" ");
		if(split.length == 7 && split2.length == 7) {
			//get authorities
			byte[] property = split[0].getBytes();
			Set<PosixFilePermission> permissions = new HashSet<PosixFilePermission>();
			if(property[1] == 'r') permissions.add(PosixFilePermission.OWNER_READ);
			if(property[2] == 'w') permissions.add(PosixFilePermission.OWNER_WRITE);
			if(property[3] == 'x') permissions.add(PosixFilePermission.OWNER_EXECUTE);
			if(property[4] == 'r') permissions.add(PosixFilePermission.GROUP_READ);
			if(property[5] == 'w') permissions.add(PosixFilePermission.GROUP_WRITE);
			if(property[6] == 'x') permissions.add(PosixFilePermission.GROUP_EXECUTE);
			if(property[7] == 'r') permissions.add(PosixFilePermission.OTHERS_READ);
			if(property[8] == 'w') permissions.add(PosixFilePermission.OTHERS_WRITE);
			if(property[9] == 'x') permissions.add(PosixFilePermission.OTHERS_EXECUTE);
			//Get File Modified time
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			FileTime mTime,aTime;
			try {
				mTime = FileTime.from(sdf.parse(split[5] + " " + split[6]).toInstant());
				aTime = FileTime.from(sdf.parse(split2[5] + " " + split2[6]).toInstant());
			} catch (ParseException e) {
				throw new IOException("Failed to parse modified time of:  " + path);
			}
			
			SSHLinuxFileAttributes toCache = new SSHLinuxFileAttributes()
					.setIsDirectory(property[0] == 'd')
					.setIsSymbolLink(property[0] == 'l')
					.setIsRegularFile(property[0] == '-')
					.setAttributes(permissions)
					.setUser(new UserPrincipal() {
						
						@Override
						public String getName() {
							return split[2];
						}
					})
					.setGroup(new GroupPrincipal() {
						
						@Override
						public String getName() {
							return split[3];
						}
					})
					.setSize(Integer.parseInt(split[4]))
					.setLastModifiedTime(mTime)
					.setLastAccesstime(aTime);
			fileattrcache.put(path.toString(), new SoftReference<SSHLinuxFileAttributes>(toCache));
			return toCache;
		} else {
			throw new IOException("Incomplete information of file or directory: " + path);
		}
		
	}

	/**
	 * This method will commit folder to remote via SSH
	 * @param to url of remote path
	 * @param from local path
	 * @param recursive whether recursively commit
	 * @throws IOException
	 */
	public void commitFolderTo(String to, String from, boolean recursive) throws IOException {
		List<Option> options = new ArrayList<>();
		options.add(Option.TargetIsDirectory);
		if(recursive) {
			options.add(Option.Recursive);
		}
		
		this.client.scpTo(to, from, options.toArray(new Option[0]));
	}

	/**
	 * The method commit file to remote via SSH
	 * @param to url of remote path
	 * @param from local path
	 * @throws IOException
	 */
	public void commitFileTo(String to, String from) throws IOException {
		this.client.scpTo(to, from);
	}
	
	public String localize(Path remote) throws IOException
	{		
		PosixFileAttributes attr = this.getFileAttributes(remote, false);
		Path localPath;
		
		if(remote.getParent() != null) {
			localPath = FileSystems.getDefault().getPath(this.localDir, remote.getParent().toString());
			Files.createDirectories(localPath);
		} else {
			localPath = FileSystems.getDefault().getPath(this.localDir, remote.toString());
		}
		this.client.scpFrom(remote.toString(), localPath.toString(),attr.isDirectory());
		return  FileSystems.getDefault().getPath(this.localDir, remote.toString()).toString();
	}

	/**
	 * Get output stream from specified remote path
	 * @param path can only indicate a remote file but not a folder because folder cannot be transferred into stream
	 * @param out the output stream of remote file
	 * @throws IOException
	 */
	void toOutputStream(String path, OutputStream out) throws IOException {
		this.client.scpFrom(path, out);
	}
	/**
	 * Upload a file from an input stream, if the file does not exist, will create a file first
	 * @param path remote path
	 * @param in the input stream to upload its content
	 * @param size the size of bytes to upload
	 * @throws IOException
	 */
	void fromInputStream(SSHLinuxPath path, InputStream in, long size) throws IOException {
		PosixFileAttributes attr = this.getFileAttributes(path, true);
		this.client.scpTo(in, path.toString(), size, attr.permissions(), new ScpTimestamp(System.currentTimeMillis(), System.currentTimeMillis()));
	}
}