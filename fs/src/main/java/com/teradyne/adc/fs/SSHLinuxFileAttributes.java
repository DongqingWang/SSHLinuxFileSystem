package com.teradyne.adc.fs;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;

class SSHLinuxFileAttributes implements PosixFileAttributes {

	private FileTime lmtime;
	private FileTime latime;
	private boolean isregularfile;
	private boolean isdirectory;
	private boolean issymlink;
	private long sizeinbyte;
	private UserPrincipal user;
	private GroupPrincipal group;
	private Set<PosixFilePermission> permissions;
	
	SSHLinuxFileAttributes setLastModifiedTime(FileTime lmtime) {
		this.lmtime = lmtime;
		return this;
	}

	SSHLinuxFileAttributes setLastAccesstime(FileTime latime) {
		this.latime = latime;
		return this;
	}

	SSHLinuxFileAttributes setIsRegularFile(boolean isregularfile) {
		this.isregularfile = isregularfile;
		return this;
	}

	SSHLinuxFileAttributes setIsDirectory(boolean isdirectory) {
		this.isdirectory = isdirectory;
		return this;
	}

	SSHLinuxFileAttributes setIsSymbolLink(boolean issymlink) {
		this.issymlink = issymlink;
		return this;
	}

	SSHLinuxFileAttributes setSize(long sizeinbyte) {
		this.sizeinbyte = sizeinbyte;
		return this;
	}

	SSHLinuxFileAttributes setUser(UserPrincipal user) {
		this.user = user;
		return this;
	}

	SSHLinuxFileAttributes setGroup(GroupPrincipal group) {
		this.group = group;
		return this;
	}

	SSHLinuxFileAttributes setAttributes(Set<PosixFilePermission> permissions) {
		this.permissions = permissions;
		return this;
	}

	@Override
	public FileTime lastModifiedTime() {
		return this.lmtime;
	}

	@Override
	public FileTime lastAccessTime() {
		return this.latime;
	}

	//Linux system does not support file creation time
	@Override
	public FileTime creationTime() {
		return null;
	}

	@Override
	public boolean isRegularFile() {
		return this.isregularfile;
	}

	@Override
	public boolean isDirectory() {
		return this.isdirectory;
	}

	@Override
	public boolean isSymbolicLink() {
		return this.issymlink;
	}

	@Override
	public boolean isOther() {
		return this.isregularfile || this.isdirectory || this.issymlink ? false : true;
	}

	@Override
	public long size() {
		return this.sizeinbyte;
	}

	@Override
	public Object fileKey() {
		return null;
	}

	@Override
	public UserPrincipal owner() {
		return this.user;
	}

	@Override
	public GroupPrincipal group() {
		return this.group;
	}

	@Override
	public Set<PosixFilePermission> permissions() {
		return this.permissions;
	}

}
