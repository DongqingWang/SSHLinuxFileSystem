package com.teradyne.adc.fs.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.teradyne.adc.fs.SSHFileOutputStream;
import com.teradyne.adc.fs.SSHLinuxFile;
import com.teradyne.adc.fs.SSHLinuxFileSystem;
import com.teradyne.adc.fs.SSHLinuxPath;

public class SSHLinuxFileSystemTest {

	private SSHLinuxFileSystem fs;
	@Before
	public void initialize() throws IOException {
		long t1 = System.currentTimeMillis();
		fs = new SSHLinuxFileSystem("/", "root", "Hadoopteradyne", "131.101.156.42", 22);
		System.out.println(System.currentTimeMillis() - t1);
	}
	
	@After
	public void close() throws IOException {
		fs.close();
	}
	
	@Test
	public void getFile() throws IOException {
		Long t = System.currentTimeMillis();
		File f = fs.getPath("/root/Desktop/DEMO.sh").toFile();
		System.out.println(System.currentTimeMillis() - t);
	}
	
	@Test
	public void getFileMD5() throws IOException {
		System.out.println(fs.getUtils().getMD5("/root/Desktop/DEMO.sh"));
	}
	
	@Test
	public void putFile() throws IOException {
		SSHLinuxFile f = new SSHLinuxFile(new SSHLinuxPath(fs, "/root/Desktop"),"C:\\Users\\wangho.TER\\Desktop\\backup\\DEMO233.sh");
		f.commit(false);
		
	}
	
	@Test
	public void remotecopy() throws IOException {
		long t1 = System.currentTimeMillis();
		fs.getUtils().copy("/root/Desktop/DEMO.sh", "/root/Desktop/DEMO2.sh");
		fs.getUtils().move("/root/Desktop/DEMO2.sh", "/root/Desktop/DEMORENAME.sh");
		System.out.println(System.currentTimeMillis() - t1);
	}
	
	@Test
	public void getAndPutFile() throws IOException {
		System.out.println(System.getProperty("java.io.tmpdir"));
		File f = fs.getPath("/root/Desktop/DEMO.sh").toFile();
		try(FileReader in = new FileReader(f);
			BufferedReader b = new BufferedReader(in)) {
					System.out.println(b.readLine());
		} finally {
			
		}
		try (FileOutputStream out = new FileOutputStream(f)){
			out.write("This is a test".getBytes());
			out.flush();
			out.close();
		} finally {
			
		}
		((SSHLinuxFile) f).commit(false);
	}
	
	@Test
	public void getAndPutFileUsingSSHFileOutputStream() throws FileNotFoundException, IOException {
		File f = fs.getPath("/root/Desktop/DEMO.sh").toFile();
		try (FileOutputStream out = new SSHFileOutputStream(f,true)) {
			out.write("This is another line to test appending".getBytes());
			out.flush();
			out.close();
		} finally {
			
		}
	}
	
	@Test
	public void getAndPutFileUsingStream() throws IOException {
		long start =  System.currentTimeMillis();
		SSHLinuxPath path = (SSHLinuxPath) fs.getPath("/root/Desktop/DEMO.sh");
		try(InputStream in = path.getInputStream()) {
			InputStreamReader reader = new InputStreamReader(in);
			BufferedReader br = new BufferedReader(reader);
				System.out.println(br.readLine());
			} finally {
				System.out.println(System.currentTimeMillis() - start);
				start = System.currentTimeMillis();
			}
		try(OutputStream out = path.getOutputStream()) {
			for (int i = 0; i < 1000000; i++) { 
				out.write("This is stream r/w test! Please revise for testing\n".getBytes());
			}		
			out.write("This is end!".getBytes());
			out.flush();
			out.close();
		} finally {
			System.out.println(System.currentTimeMillis() - start);
		}
	}
	
	@Test
	public void createNewFile() throws IOException {
		long start =  System.currentTimeMillis();
		SSHLinuxPath path = (SSHLinuxPath) fs.getPath("/root/Desktop/newfiletest.sh");
		try(OutputStream out = path.getOutputStream()) {
			for (int i = 0; i < 1000000; i++) { 
				out.write("This is stream r/w test! Please revise for testing\n".getBytes());
			}		
			out.write("This is end!".getBytes());
			out.flush();
			out.close();
		} finally {
			System.out.println(System.currentTimeMillis() - start);
		}
		fs.getUtils().move("/root/Desktop/newfiletest.sh", "/root/Desktop/DEMO.sh");
	}
	
	@Test
	public void getFolder() throws IOException {
		System.out.println(System.getProperty("java.io.tmpdir"));
		File f = fs.getPath("/root/Desktop/ToTest").toFile();
		System.out.println(f.getAbsolutePath());
	}
	
	@Test
	public void putFolder() throws IOException {
		SSHLinuxFile f = new SSHLinuxFile(new SSHLinuxPath(fs, "/root/Desktop"), "C:\\Users\\wangho.TER\\Desktop\\TEST");
		f.commit(true);
	}
	
	@Test 
	public void messDownload() throws IOException {
		fs.getPath("/var/local").toFile();// result from linux -> PC  10Mb/s
	}
	
	@Test
	public void overallTest() throws IOException {
		this.putFolder();
		this.getFolder();
		this.getAndPutFile();
		this.getAndPutFileUsingSSHFileOutputStream();
		this.getAndPutFileUsingStream();
		this.getFile();
		this.putFile();
	}
}
