package com.teradyne.adc.fs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
/**
 * Package private because it can only be created by instance of SSHLinuxPath
 * @author wangho
 *
 */
class SSHOutputStream extends ByteArrayOutputStream {

	private SSHLinuxPath path = null;
	private final static int redundantSize = 5000; // Currently set to 5000 because it is fastest by experience
	
	SSHOutputStream(SSHLinuxPath path) {
		super();
		this.path = path;
	}

	SSHOutputStream(SSHLinuxPath path, int size) {
		super(size);
		this.path = path;
	}
	/**
	 * override flush to make sure the stream is flushed to remote system
	 */
	@Override
	public void flush() throws IOException {
		super.flush();
		if(this.path != null) {
			byte[] validBuf = new byte[this.size() + redundantSize];
			System.arraycopy(buf, 0, validBuf, 0, this.size());
			InputStream in = new ByteArrayInputStream(validBuf);
			((SSHLinuxFileSystem)path.getFileSystem()).fromInputStream(path, in, this.size());
		}
	}
}
