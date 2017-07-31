package com.teradyne.adc.fs;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class SSHFileOutputStream extends FileOutputStream {

	private File f = null;
	private Object closelock = new Object();
	
	public SSHFileOutputStream(File file, boolean append) throws FileNotFoundException {
		super(file, append);
		this.f = file;
	}

	public SSHFileOutputStream(File file) throws FileNotFoundException {
		super(file);
		this.f = file;
	}

	public SSHFileOutputStream(FileDescriptor fdObj) {
		super(fdObj);
	}

	public SSHFileOutputStream(String name, boolean append) throws FileNotFoundException {
		super(name, append);
	}

	public SSHFileOutputStream(String name) throws FileNotFoundException {
		super(name);
	}

	/**
	 * Will automatically commit this file to remote via SSH
	 */
	@Override
	public void close() throws IOException {
		synchronized (closelock) {
			super.close();
			if(this.f != null && this.f instanceof SSHLinuxFile) {
				((SSHLinuxFile)this.f).commit(true);
			}
		}
	}
	
}
