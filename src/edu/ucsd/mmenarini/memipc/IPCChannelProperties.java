package edu.ucsd.mmenarini.memipc;

class IPCChannelProperties {
    static final int DefaultPageSize = 4096;
    static final int DefaultBlockPages = 1000;
    static final byte DefaultBlocks = 3;
    static final long DefaultWatchdogTimeout = 120000000000L; //2 minutes in nanoseconds
}
