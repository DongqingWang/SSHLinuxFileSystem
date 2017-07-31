package com.teradyne.adc.fs.util.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

import com.teradyne.adc.fs.util.SSHShell;

public class SSHShellTest {

	SSHShell sh;
	@Before
	public void connect() throws IOException {
		sh = new SSHShell("root", "Hadoopteradyne", "131.101.156.42", 12345);
	}
	
	@Test
	public void getIdByName() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		sh.exec("id root", out);
		Pattern idP = Pattern.compile("uid=(\\d+)\\(.*?\\) gid=(\\d+)\\(.*?\\) groups=(\\d+)\\(.*?\\)\n");
		String result = out.toString();
		System.out.println(result);
		Matcher m = idP.matcher(result);
		m.matches();
		System.out.println(m.group(1));
	}
	
	@Test
	public void RegTest() throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		sh.exec("getent group adm", out);
		String[] split = out.toString().split(":");
		System.out.println(out.toString() + "     " + split.length);
	}
	
	@Test
	public void getNameByUid() throws IOException {
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		sh.exec("getent passwd 0", out);
		System.out.println(out.toString());
	}
	
	@Test
	public void getLs() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		sh.exec("ls -ld --time-style '+%Y-%m-%d %H:%M:%S' /root/Desktop/DEMO.sh" , out);
		System.out.println(out.toString());
	}
	
	@Test
	public void diskStatus() throws IOException {
		sh.exec("df -TH", System.out);
	}
	
	@Test
	public void jvmStatus() throws IOException {
		sh.exec("/usr/java/default/bin/jps", System.out);
	}
	
	@Test
	public void backupStatus() throws IOException {
		sh.exec("/etc/init.d/mysql restart", System.out);
	}
	
	@Test
	public void rsyncTest() throws IOException {
		sh.exec("ls -ld --time-style '+%Y-%m-%d %H:%M:%S' /root/Desktop/asdfasdf", System.out);
	}
	
	@Test
	public void copyToLocalTest() throws IOException {
		System.out.println("Start:" );
		long start = System.currentTimeMillis();
		sh.copyFileFrom("/root/Desktop/DEMO.sh", "C:\\Users\\wangho.TER\\Desktop\\backup");
		System.out.println("End Time:" + ((System.currentTimeMillis() - start) /1000));
		//sh.copyFileFrom("/var/db/savepoint", "C:\\Users\\wangho.TER\\Desktop\\");
	}
	
	@Test
	public void copyIncrementTest() throws IOException {
		System.out.println("Start:" );
		long start = System.currentTimeMillis();
		sh.copyFolderFrom("/var/db/FOR/", "C:\\Users\\wangho.TER\\Desktop\\backup",true,true);
		System.out.println("End Time:" + ((System.currentTimeMillis() - start) /1000));
	}
	
	@Test
	public void copyToRemoteTest() throws IOException {
		System.out.println("Start:" );
		long start = System.currentTimeMillis();
		sh.copyFolderTo("/var/local", "C:\\Users\\wangho.TER\\Desktop\\FOR",true, false);
		System.out.println("End Time:" + ((System.currentTimeMillis() - start) /1000));
	}
}
