package com.teradyne.adc.fs;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public final class SSHLinuxFile extends File {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private SSHLinuxPath remote;
	
	SSHLinuxFile(SSHLinuxPath remote, File parent, String child) {
		super(parent, child);
		this.remote = remote;
	}

	SSHLinuxFile(SSHLinuxPath remote, String parent, String child) {
		super(parent, child);
		this.remote = remote;
	}

	public SSHLinuxFile(SSHLinuxPath remote, String localpathname) {
		super(localpathname);
		this.remote = remote;
	}

	SSHLinuxFile(SSHLinuxPath remote, URI uri) {
		super(uri);
		this.remote = remote;
	}
	
	public void commit(boolean recursive) throws IOException {
		SSHLinuxFileSystem fs = (SSHLinuxFileSystem)this.remote.getFileSystem();
		if(this.isDirectory()) {
			fs.commitFolderTo(this.remote.toString(), this.toString(),recursive);
		} else if(this.isFile()) {
			fs.commitFileTo(this.remote.toString(), this.toString());
		}
	}
}
