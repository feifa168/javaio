package com.ft.nio.channel;

import org.junit.Test;

import java.io.IOException;
import java.nio.*;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;

public class TestChannel {
    @Test
    public void testAsynchronousFileChanneFuture() {
        Path pt = Paths.get("pom.xml");
        try {
            AsynchronousFileChannel aschannel = AsynchronousFileChannel.open(pt, StandardOpenOption.READ);
            final int bufLen = 128;
            int offset = 0;
            final int maxLen = (int)aschannel.size();
            ByteBuffer buf = ByteBuffer.allocate(bufLen);

            while (offset < maxLen) {
                Future<Integer> f = aschannel.read(buf, offset);
                offset += bufLen;

                while (!f.isDone()) {
                    //System.out.println("is continue……");
                }
                //System.out.println("is done");

                buf.flip();
                byte[] data = new byte[buf.limit()];
                buf.get(data);
                System.out.print(new String(data));
                buf.clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testAsynchronousFileChanneCallbackNormal() {
        Path pt = Paths.get("pom.xml");
        try {
            AsynchronousFileChannel aschannel = AsynchronousFileChannel.open(pt, StandardOpenOption.READ);
            final int maxLen = (int)aschannel.size();
            ByteBuffer buf = ByteBuffer.allocate(maxLen);
            aschannel.read(buf, 0, buf, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    attachment.flip();
                    byte[] data = new byte[attachment.limit()];
                    attachment.get(data);
                    System.out.print(new String(data));
                    attachment.clear();
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    System.out.println("read fail");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testAsynchronousFileChanneCallback() {
        Path pt = Paths.get("pom.xml");
        try {
            AsynchronousFileChannel aschannel = AsynchronousFileChannel.open(pt, StandardOpenOption.READ);
            final int bufLen = 128;
            final int maxLen = (int)aschannel.size();
            String[] text = new String[maxLen];
            ByteBuffer textBuf = ByteBuffer.allocate(maxLen);
            StringBuilder sb = new StringBuilder(maxLen);
            textBuf.clear();

            for (int i=0; i<maxLen/bufLen; i++ ) {
                ByteBuffer buf = ByteBuffer.allocate(bufLen);
                final int pos = bufLen * i;
                aschannel.read(buf, bufLen*i, buf, new CompletionHandler<Integer, ByteBuffer>() {
                    @Override
                    public void completed(Integer result, ByteBuffer attachment) {
                        attachment.flip();
                        byte[] data = new byte[attachment.limit()];
                        attachment.get(data);
                        sb.insert(pos, new String(data).toCharArray(), 0, bufLen);
                        //System.out.print(new String(data));
                        attachment.clear();
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        System.out.println("read fail");
                    }
                });
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            int lastPos = maxLen/bufLen*bufLen;
            ByteBuffer buf = ByteBuffer.allocate(bufLen);
            aschannel.read(buf, lastPos, buf, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    attachment.flip();
                    byte[] data = new byte[attachment.limit()];
                    attachment.get(data);
                    char[] cs = new String(data).toCharArray();
                    sb.insert(lastPos, cs, 0, maxLen-lastPos);
                    //System.out.print(new String(data));
                    attachment.clear();

                    System.out.print(sb.toString());
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    System.out.println("read fail");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}