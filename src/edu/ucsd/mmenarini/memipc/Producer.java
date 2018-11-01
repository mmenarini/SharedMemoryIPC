package edu.ucsd.mmenarini.memipc;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class Producer implements Closeable {

    private int pageSize;
    private int blockPages;
    private int blocksNumber;
    private int blockSize;
    private MappedByteBuffer mbb;
    private ByteBuffer control_buf;
    private ByteBuffer current_buf;

    private int ConsumerBlockAddr;
    private int ProducerBlockAddr;

    protected byte getConsumerBlock(){
        return control_buf.get(ConsumerBlockAddr);
    }
    protected byte getProducerBlock(){
        return control_buf.get(ProducerBlockAddr);
    }
    protected void setConsumerBlock(byte val){
        control_buf.put(ConsumerBlockAddr, val);
    }
    protected void setProducerBlock(byte val){
        control_buf.put(ProducerBlockAddr, val);
    }

    protected void nextBuf() {
        if (current_buf.hasRemaining()){
            int buf_end = current_buf.position();
            current_buf.rewind();
            current_buf.putInt(buf_end);
        }
        int blk = getProducerBlock()+1;
        if(blk==blocksNumber) blk=0;
        while(getConsumerBlock()==blk) {Thread.yield();}
        int blk_addr = pageSize + blockSize*blk;
        mbb.position(blk_addr);
        current_buf=mbb.slice();
        current_buf.limit(blockSize);
        current_buf.putInt(blockSize);
    }



    public Producer(int pageSize, int blockPages, byte blocksNumber) throws IOException {
        this.pageSize = pageSize;
        this.blockPages = blockPages;
        this.blocksNumber = blocksNumber;
        this.blockSize = pageSize*blockPages;
        long fileSize = blocksNumber*blockSize+pageSize;

        File tmpFile = File.createTempFile("memipc-", ".dat");
        try (FileChannel fc = new RandomAccessFile(tmpFile, "rw").getChannel()) {
            mbb = fc.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            control_buf = mbb.slice();
            control_buf.limit(pageSize);
            ProducerBlockAddr=0;
            ConsumerBlockAddr=pageSize/2;
            setConsumerBlock((byte)0);
            setProducerBlock((byte)0);
        }
    }

    @Override
    public void close() throws IOException {
        setProducerBlock((byte)-1);
    }


    public static void startConsumer() {

    }

    public static void main(String[] args) {
        try(Producer prod = new Producer(4096, 10000, (byte)4)) {
            startConsumer();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }


}
