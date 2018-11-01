package edu.ucsd.mmenarini.memipc;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ConsumerRun {
    public static void main(String[] args) {
        long startTime = System.nanoTime();
        if (args.length!=4)
            System.err.println("Wrong number of parameters, need 4");
        String fileName = args[0];
        int pageSize = Integer.parseInt(args[1]);
        int blockPages = Integer.parseInt(args[2]);
        byte blocksNumber = Byte.parseByte(args[3]);
        int lastVal=-1;
        try(Consumer cons = new Consumer(fileName,pageSize, blockPages, blocksNumber)) {
            //System.out.println("IPC Stream Read Start...");
            ByteBuffer intBuf = ByteBuffer.allocate(4);
            while(cons.read(intBuf)==4){
                intBuf.rewind();
                int val = intBuf.getInt();
                if(val!=lastVal+1)
                    System.err.printf("Error i expected the value %d but read %d\n",lastVal+1, val);
                if (val%100000==0) System.out.print("*");
                lastVal = val;
                intBuf.rewind();
            }
            //System.out.println("IPC Stream Read Completed");
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        long endTime = System.nanoTime();
        System.out.printf("\nConsumer finished in %f seconds, received %d integers\n",(endTime-startTime)/1000000000.0, lastVal+1);
    }
}
