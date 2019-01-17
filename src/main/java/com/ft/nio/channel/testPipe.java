package com.ft.nio.channel;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.LongAdder;

public class testPipe {
    static class MySinkRunnable implements Runnable {
        private int num;
        private Pipe.SinkChannel sinkChannel;
        public MySinkRunnable(int num, Pipe.SinkChannel sinkChannel) {
            this.num = num;
            this.sinkChannel = sinkChannel;
        }

        @Override
        public void run() {
            byte[] bts = new byte[100];
            for (int i=0; i<100; i++) {
                bts[i] = (byte)(num + 'a');
            }
            for (int i=0; i<1; i++) {
                try {
                    int wtLen = sinkChannel.write(ByteBuffer.wrap(bts));
                    //System.out.println(Thread.currentThread().getId() + " wtLen is " + wtLen);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class ExitFlag {
        public LongAdder finishFlag = new LongAdder();
    }

    @Test
    public void testPipeMultithreadBlook() {
        try {
            Pipe pipe = Pipe.open();
            Pipe.SinkChannel    sinkChannel  = pipe.sink();
            Pipe.SourceChannel sourceChannel = pipe.source();

            List<Thread> wtThreads = new LinkedList<>();
            for (int i=0; i<1; i++) {
                Thread wtThread = new Thread(new MySinkRunnable(i, sinkChannel));
                wtThreads.add(wtThread);
            }

            Thread rdThread = new Thread(()->{
                ByteBuffer rdBuf = ByteBuffer.allocate(100);
                int rdLen = 0;
                boolean isFinished = false;

                int selNum = 0;
                while (true) {
                    rdBuf.clear();
                    int rdNum = 0;
                    while (rdBuf.hasRemaining()) {
                        try {
                            rdLen = sourceChannel.read(rdBuf);
                        } catch (IOException e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                    rdBuf.flip();
                    System.out.println("receive from pipe data is [" + new String(rdBuf.array(), 0, rdBuf.limit()) + "]");
                }
            });

            for (Thread wtThread : wtThreads) {
                wtThread.start();
            }
            rdThread.start();

            try {
                for (Thread wtThread : wtThreads) {
                    wtThread.join();
                }
                rdThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPipeMultithreadNonBlook() {
        try {
            Pipe pipe = Pipe.open();
            Pipe.SinkChannel    sinkChannel  = pipe.sink();
            Pipe.SourceChannel sourceChannel = pipe.source();
            //sinkChannel.configureBlocking(false);
            sourceChannel.configureBlocking(false);

            Selector sel = Selector.open();
            sourceChannel.register(sel, SelectionKey.OP_READ);

            List<Thread> wtThreads = new LinkedList<>();
            for (int i=0; i<1; i++) {
                Thread wtThread = new Thread(new MySinkRunnable(i, sinkChannel));
                wtThreads.add(wtThread);
            }

            ExitFlag exitF = new ExitFlag();
            System.out.println("init value is " + exitF.finishFlag.intValue());
            List<Thread> rdThreads = new LinkedList<>();
            for (int i=0; i<1; i++) {
                Thread rdThread = new Thread(() -> {
                    ByteBuffer rdBuf = ByteBuffer.allocate(100);
                    int rdLen = 0;

                    int selNum = 0;
                    while (exitF.finishFlag.intValue() < 1) {
                        try {
                            selNum = sel.select(2000);
                            if (selNum < 1) {
                                continue;
                            }

                            //synchronized (this)
                            {
                                Set<SelectionKey> keys = sel.selectedKeys();
                                for (SelectionKey key : keys) {
                                    if (!key.isValid()) {
                                        continue;
                                    }

                                    if (key.isReadable()) {
                                        rdBuf.clear();
                                        int rdNum = 0;
                                        boolean readEnd = false;
                                        while (!readEnd) {
                                            while (rdBuf.hasRemaining()) {
                                                try {
                                                    rdLen = sourceChannel.read(rdBuf);
                                                    if (rdLen < 1) {
                                                        rdNum++;
                                                        if (rdNum > 10) {
                                                            readEnd = true;
                                                            break;
                                                        }
                                                    }
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                    readEnd = true;
                                                    break;
                                                }
                                            }
                                            rdBuf.flip();
                                            //synchronized (this)
                                            {
                                                System.out.println(Thread.currentThread().getId() + " [" + new String(rdBuf.array(), 0, rdBuf.limit()) + "]");
                                            }
                                        }
                                    }
                                }
                                keys.clear();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                rdThreads.add(rdThread);
            }

            new Thread(()->{
                while (true) {
                    System.out.println("please input exit to quit");
                    BufferedReader bufRd = new BufferedReader(new InputStreamReader(System.in));
                    try {
                        if (bufRd.readLine().equals("exit")) {
                            exitF.finishFlag.increment();
                            break;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        exitF.finishFlag.increment();
                        break;
                    }
                }
            }).start();

            for (Thread wtThread : wtThreads) {
                wtThread.start();
            }
            for (Thread rdThread : rdThreads) {
                rdThread.start();
            }

            try {
                for (Thread wtThread : wtThreads) {
                    wtThread.join();
                }
                for (Thread rdThread : rdThreads) {
                    rdThread.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPipeChannel() {
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
                            if (!key.isValid()) {
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
    public static void main(String[] args) {
        testPipe tp = new testPipe();
        tp.testPipeMultithreadNonBlook();
    }
}
