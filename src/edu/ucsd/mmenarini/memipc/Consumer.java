package edu.ucsd.mmenarini.memipc;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class Consumer implements ReadableByteChannel {

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
    private byte getProducerDone(){
        return control_buf.get(ProducerDoneAddr);
    }

    public long getWatchDogTimeout() { return watchdog_timeout; }
    public void setWatchDogTimeout(long val) { watchdog_timeout = val; }
    public boolean isDoYeld() { return doYeld; }
    public void setDoYeld(boolean doYeld) { this.doYeld = doYeld; }

    public String getIPCFileName() {return ipc_tmp_filename;}

    private void nextBuf() throws IOException {
        int blk = getConsumerBlock()+1;
        if(blk==blocksNumber) blk=0;
        openBuf((byte)blk);
    }

    private void openBuf(byte blk) throws IOException {
        //System.out.println("Consumer opening block " + blk);
        long watchdog = System.nanoTime();
        while(getProducerBlock()==blk) {
            if (getProducerDone()==1){
                current_buf=null;
                close();
                return;
            }
            if ((System.nanoTime()-watchdog)>watchdog_timeout) {
                throw new IOException("Consumer watchdog expired while waiting on producer to send more data");
            }
            if (isDoYeld()) Thread.yield();
        }
        setConsumerBlock(blk);
        //System.out.println("Cons - Consumer Blk "+getConsumerBlock() + " Producer Blk " + getProducerBlock());
        int blk_addr = pageSize + blockSize*blk;
        mbb.position(blk_addr);
        current_buf=mbb.slice();
        int this_block_size = current_buf.getInt();
        current_buf.limit(this_block_size);
    }

    public Consumer(String filename) throws IOException {
        this(filename,
                IPCChannelProperties.DefaultPageSize,
                IPCChannelProperties.DefaultBlockPages,
                IPCChannelProperties.DefaultBlocks);
    }
    public Consumer(String filename, int pageSize, int blockPages, byte blocksNumber) throws IOException {
        this.pageSize = pageSize;
        this.blockPages = blockPages;
        this.blocksNumber = blocksNumber;
        this.blockSize = pageSize*blockPages;
        this.ipc_tmp_filename = filename;
        long fileSize = blocksNumber*blockSize+pageSize;
        try (FileChannel fc = new RandomAccessFile(filename, "rw").getChannel()) {
            mbb = fc.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            control_buf = mbb.slice();
            control_buf.limit(pageSize);
            ProducerBlockAddr=0;
            ProducerDoneAddr=1;
            ConsumerBlockAddr=pageSize/2;
            openBuf(getConsumerBlock());
        }
    }

    @Override
    public boolean isOpen() {
        return getConsumerBlock()!=-1;
    }

    @Override
    public void close()  {
        setConsumerBlock((byte)-1);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (current_buf==null) return -1;
        while(dst.remaining()>current_buf.remaining()) {
            dst.put(current_buf);
            nextBuf();
            if (current_buf==null) return -1;
        }
        int src_limit = current_buf.limit();
        current_buf.limit(current_buf.position()+dst.remaining());
        dst.put(current_buf);
        current_buf.limit(src_limit);
        return dst.limit();
    }

}
