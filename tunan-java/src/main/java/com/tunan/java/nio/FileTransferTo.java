package com.tunan.java.nio;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

/**
 * 连通两个Channel
 *
 * transferFrom
 * transferTo
 */
public class FileTransferTo {

    public static void main(String[] args) throws Exception {

        String inFile = "tunan-java/data/word.txt";
        String outFile = "tunan-java/out/out.txt";


        FileChannel
                in = new FileInputStream(inFile).getChannel(),
                out = new FileOutputStream(outFile).getChannel();

        in.transferTo(0,in.size(),out);

    }
}
