package com.teradyne.adc.fs;

import java.io.IOException;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

public class SSHLinuxWatchService implements WatchService {

	
	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public WatchKey poll() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public WatchKey take() throws InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	public WatchKey register(SSHLinuxPath sshLinuxPath, Kind<?>[] events, Modifier[] modifiers) {
		// TODO Auto-generated method stub
		return null;
	}

}
