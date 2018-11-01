package edu.ucsd.mmenarini.memipc;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ProducerRun {
    public static void main(String[] args) {
        long startTime = System.nanoTime();
        int i=-1;
        int pageSize = IPCChannelProperties.DefaultPageSize;
        int blockPages = IPCChannelProperties.DefaultBlockPages;
        byte blocks = IPCChannelProperties.DefaultBlocks;
        try(Producer prod = new Producer()) {
            startConsumer(prod.getIPCFileName(), pageSize, blockPages, blocks);
            for (i=0; i<1000000000; i++){
                if (i%100000==0) System.out.print(".");
                if (i%10000000==0) System.out.println();
                prod.write((ByteBuffer) ByteBuffer.allocate(4).putInt(i).rewind());
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        long endTime = System.nanoTime();
        System.out.printf("\nProducer finished in %f seconds, sent %d integers\n",(endTime-startTime)/1000000000.0, i);
    }

    private static void startConsumer(String filename, int pageSize, int blockPages, byte blocksNumber) throws IOException {
        Runtime rt = Runtime.getRuntime();
        String cp = System.getProperty("java.class.path");
        String java_home = System.getProperty("java.home");
        String file_separator = System.getProperty("file.separator");
        String java_program = "java";//"C:\\Program Files\\Java\\jdk1.8.0_161\\bin\\java.exe";
        String heap_size = "1000m";
        if (cp == null) cp = ".";

        String cmdstr = String.format(" -Xmx%s -cp \"%s\" -ea edu.ucsd.mmenarini.memipc.ConsumerRun %s %d %d %d",
                heap_size, cp, filename, pageSize, blockPages, blocksNumber);
        System.out.printf("\nExecuting: %s\n", cmdstr);
        Process consumerProce = new ProcessBuilder().inheritIO()
                .command(new String[]{
                java_program, "-Xmx"+heap_size, "-cp", cp, "edu.ucsd.mmenarini.memipc.ConsumerRun",
                filename, String.valueOf(pageSize), String.valueOf(blockPages), String.valueOf(blocksNumber)})
                .start();
    }
}
