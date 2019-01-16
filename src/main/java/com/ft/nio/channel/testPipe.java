package com.ft.nio.channel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

public class testPipe {
    public static void main(String[] args) {
        try {
            Pipe pipe = Pipe.open();
            Pipe.SinkChannel    sinkChannel  = pipe.sink();
            Pipe.SourceChannel sourceChannel = pipe.source();
            sourceChannel.configureBlocking(false);

            Selector sel = Selector.open();
            sourceChannel.register(sel, SelectionKey.OP_READ);

            Thread wtThread = new Thread(()->{
                ByteBuffer wtBuf = ByteBuffer.wrap("hello, this is for test pipe".getBytes());
                try {
                    int wtNum = sinkChannel.write(wtBuf);
                    System.out.println("input into pipe data is [" + new String(wtBuf.array(), 0, wtNum) +"]");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            Thread rdThread = new Thread(()->{
                ByteBuffer rdBuf = ByteBuffer.allocate(10);
                int rdLen = 0;
                boolean isFinished = false;

                int selNum = 0;
                while (true) {
                    try {
                        selNum = sel.select();
                        if (selNum < 0) {
                            continue;
                        }

                        Set<SelectionKey> keys = sel.selectedKeys();
                        for (SelectionKey key : keys) {
                            if (key.isValid()) {
                                continue;
                            }

                            if (key.isReadable()) {
                                rdBuf.clear();
                                int rdNum = 0;
                                while (rdBuf.hasRemaining()) {
                                    try {
                                        rdLen = sourceChannel.read(rdBuf);
                                        if (rdLen < 1) {
                                            rdNum++;
                                            if (rdNum > 10) {
                                                isFinished = true;
                                                break;
                                            }
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        isFinished = true;
                                        break;
                                    }
                                }
                                rdBuf.flip();
                                System.out.println("receive from pipe data is [" + new String(rdBuf.array(), 0, rdBuf.limit()) + "]");
                            }
                        }
                        keys.clear();

                        if (isFinished) {
                            break;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            wtThread.start();
            rdThread.start();

            try {
                wtThread.join();
                rdThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
