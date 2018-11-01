package edu.ucsd.mmenarini.memipc;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Timer;

public class Producer implements WritableByteChannel {

    private long watchdog_timeout = IPCChannelProperties.DefaultWatchdogTimeout;
    private boolean doYeld = false;

    public int getPageSize() {
        return pageSize;
    }

    public int getBlockPages() {
        return blockPages;
    }

    public int getBlocksNumber() {
        return blocksNumber;
    }

    public int getBlockSize() {
        return blockSize;
    }

    private int pageSize;
    private int blockPages;
    private int blocksNumber;
    private int blockSize;
    private String ipc_tmp_filename;
    private MappedByteBuffer mbb;
    private ByteBuffer control_buf;
    private ByteBuffer current_buf;

    private int ConsumerBlockAddr;
    private int ProducerBlockAddr;
    private int ProducerDoneAddr;

    private byte getConsumerBlock(){
        return control_buf.get(ConsumerBlockAddr);
    }
    private byte getProducerBlock(){
        return control_buf.get(ProducerBlockAddr);
    }
    private void setConsumerBlock(byte val){
        control_buf.put(ConsumerBlockAddr, val);
    }
    private void setProducerBlock(byte val){
        control_buf.put(ProducerBlockAddr, val);
    }
    private byte getProducerDone(){
        return control_buf.get(ProducerDoneAddr);
    }
    private void setProducerDone(byte val){
        control_buf.put(ProducerDoneAddr, val);
    }

    public String getIPCFileName() {return ipc_tmp_filename;}

    public long getWatchDogTimeout() { return watchdog_timeout; }
    public void setWatchDogTimeout(long val) { watchdog_timeout = val; }
    public boolean isDoYeld() { return doYeld; }
    public void setDoYeld(boolean doYeld) { this.doYeld = doYeld; }


    private void nextBuf() throws IOException {
        if (current_buf.hasRemaining()){
            int buf_end = current_buf.position();
            current_buf.rewind();
            current_buf.putInt(buf_end);
        }
        int blk = getProducerBlock()+1;
        if(blk==blocksNumber) blk=0;
        long watchdog = System.nanoTime();
        while(getConsumerBlock()==blk) {
            if ((System.nanoTime()-watchdog)>watchdog_timeout) {
                throw new IOException("Producer watchdog expired while waiting on consumer to read more data");
            }
            if (isDoYeld()) Thread.yield();
        }
        setProducerBlock((byte)blk);
        //System.out.println("Prod - Consumer Blk "+getConsumerBlock() + " Producer Blk " + getProducerBlock());
        int blk_addr = pageSize + blockSize*blk;
        mbb.position(blk_addr);
        current_buf=mbb.slice();
        current_buf.limit(blockSize);
        current_buf.putInt(blockSize);
        //System.out.println("Using Blk "+blk);
    }

    public Producer() throws IOException {
        this(IPCChannelProperties.DefaultPageSize,
                IPCChannelProperties.DefaultBlockPages,
                IPCChannelProperties.DefaultBlocks);
    }

    public Producer(int pageSize, int blockPages, byte blocksNumber) throws IOException {
        this.pageSize = pageSize;
        this.blockPages = blockPages;
        this.blocksNumber = blocksNumber;
        this.blockSize = pageSize*blockPages;
        long fileSize = blocksNumber*blockSize+pageSize;

        File tmpFile = File.createTempFile("memipc-", ".dat");
        try (FileChannel fc = new RandomAccessFile(tmpFile, "rw").getChannel()) {
            ipc_tmp_filename = tmpFile.getCanonicalPath();
            mbb = fc.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            control_buf = mbb.slice();
            control_buf.limit(pageSize);
            ProducerBlockAddr=0;
            ProducerDoneAddr=1;
            ConsumerBlockAddr=pageSize/2;
            setConsumerBlock((byte)0);
            setProducerBlock((byte)0);
            setProducerDone((byte)0);
            mbb.position(pageSize);
            current_buf=mbb.slice();
            current_buf.limit(blockSize);
            current_buf.putInt(blockSize);
        }
    }

    @Override
    public boolean isOpen() {
        return getProducerBlock()!=-1;
    }

    @Override
    public void close() throws IOException {
        nextBuf();
        setProducerDone((byte)1);
    }


    @Override
    public int write(ByteBuffer src) throws IOException {
        int src_remaining = src.remaining();
        int src_limit = src.limit();
        while(src.remaining()>current_buf.remaining()) {
            src.limit(src.position()+current_buf.remaining());
            current_buf.put(src);
            src.limit(src_limit);
            nextBuf();
        }
        current_buf.put(src);
        return src_remaining;
    }


}
