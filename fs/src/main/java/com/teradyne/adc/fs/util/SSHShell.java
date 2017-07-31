/**
 * 
 */
package com.teradyne.adc.fs.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.scp.ScpClient;
import org.apache.sshd.client.scp.ScpClient.Option;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.io.NoCloseOutputStream;

/**
 * @author wangho
 *
 */
public class SSHShell {

	private String user;
	private String password;
	private String host;
	private int port;

	public SSHShell(String user, String password, String host, int port) throws IOException {
		this.user = user;
		this.password = password;
		this.host = host;
		this.port = port;
	}

	@SuppressWarnings("serial")
	public void exec(String cmd, OutputStream out) throws IOException {
		try (SshClient client = SshClient.setUpDefaultClient()) {
			client.start();
			ConnectFuture con = client.connect(user, host, port);
			con.await();
			try (ClientSession session = con.getSession()) {
				session.addPasswordIdentity(password);
				session.auth().verify().await();
				
				try(ChannelExec channel = session.createExecChannel(cmd)) {
					channel.setOut(new NoCloseOutputStream(out));

			        channel.open();
			        channel.waitFor(new ArrayList<ClientChannelEvent>(){{add( ClientChannelEvent.CLOSED);}}, 0);
			        channel.close(false);
		          } finally {
		              session.close(false);
		          }
			} finally {
				client.stop();
			}
		}
	}

	public void copyFolderTo(String remoteFolder, String localFolder, boolean recursive, boolean incremental) throws IOException {
		List<Option> options = new ArrayList<>();
		options.add(Option.TargetIsDirectory);
		
		if(recursive) {
			options.add(Option.Recursive);
		}
		
		if(incremental) {
			localFolder = Paths.get(remoteFolder, "*").toString();
		}
		
		scpTo(remoteFolder, localFolder, options.toArray(new Option[0]));
	}
	
	public void copyFolderTo(String remoteFile, String localFile) throws IOException {
		scpTo(remoteFile, localFile);
	}
	
	public void copyFolderFrom(String remoteFolder, String localFolder, boolean recursive, boolean incremental) throws IOException {
		
		List<Option> options = new ArrayList<>();
		options.add(Option.TargetIsDirectory);
		
		if(recursive) {
			options.add(Option.Recursive);
		}
		
		if(incremental) {
			if(!remoteFolder.endsWith("/")) {
				remoteFolder += "/";
			}
			remoteFolder += "*";
		}
		scpFrom(remoteFolder, localFolder, options.toArray(new Option[0]));
	}
	
	public void copyFileFrom(String remoteFile, String localPath) throws IOException {
		scpFrom(remoteFile, localPath);
	}

	private void scpFrom(String remotePath, String localPath, Option... options) throws IOException {
		try (SshClient client = SshClient.setUpDefaultClient()) {
			client.start();
			ConnectFuture con = client.connect(user, host, port);
			con.await();
			try (ClientSession session = con.getSession()) {
				session.addPasswordIdentity(password);
				session.auth().verify().await();
				ScpClient scp = session.createScpClient();
				scp.download(remotePath, localPath, options);
				session.close();
			} finally {
				client.stop();
			}
		}
	}
	
	private void scpTo(String remotePath, String localPath, Option... options) throws IOException {
		try (SshClient client = SshClient.setUpDefaultClient()) {
			client.start();
			ConnectFuture con = client.connect(user, host, port);
			con.await();
			try (ClientSession session = con.getSession()) {
				session.addPasswordIdentity(password);
				session.auth().verify().await();
				ScpClient scp = session.createScpClient();
				scp.upload(localPath, remotePath, options);
				session.close();
			} finally {
				client.stop();
			}
		}
	}
}
