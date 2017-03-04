package org.skife.memcake.connection;

public class Version implements Comparable<Version> {
    static final Version ZERO = new Version(0);
    private final long version;

    Version(long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Version version1 = (Version) o;

        return version == version1.version;
    }

    @Override
    public int hashCode() {
        return (int) (version ^ (version >>> 32));
    }


    public long token() {
        return version;
    }

    @Override
    public int compareTo(Version o) {
        return (int) (this.token() - o.token());
    }
}