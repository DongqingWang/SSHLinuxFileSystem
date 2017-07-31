package com.teradyne.adc.fs;

public class SSHLinuxMountEntry {
	private byte[] name;        // file system name
    private byte[] dir;         // directory (mount point)
    private byte[] fstype;      // ufs, nfs, ...
    private byte[] opts;        // mount options
    private long dev;           // device ID

    private volatile String fstypeAsString;
    private volatile String optionsAsString;

    SSHLinuxMountEntry() {
    }

    String name() {
        return this.name.toString();
    }

    String fstype() {
        if (fstypeAsString == null)
            fstypeAsString = fstype.toString();
        return fstypeAsString;
    }

    String dir() {
        return dir.toString();
    }

    long dev() {
        return dev;
    }

    /**
     * Tells whether the mount entry has the given option.
     */
    boolean hasOption(String requested) {
        if (optionsAsString == null)
            optionsAsString = opts.toString();
        for (String opt: optionsAsString.split(",")) {
            if (opt.equals(requested))
                return true;
        }
        return false;
    }

    // generic option
    boolean isIgnored() {
        return hasOption("ignore");
    }

    // generic option
    boolean isReadOnly() {
        return hasOption("ro");
    }
}
