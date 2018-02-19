package cs682;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;
import cs682.ChatProto1.ChatProto;
import cs682.ChatProto1.Data;
import cs682.ChatProto1.ZKData;
import cs682.ChatProto1.History;
import cs682.ChatProto1.Data.packetType;

import static cs682.Chat.*;

public class HistorySender implements Runnable {
    private int udpport;
    private InetAddress ip;
    private int seq;
    private boolean isLast;
    private int lastIndex;
    private int packetNo;
    private int lastPacketLen;
    private int count;

    private DatagramPacket packet;
    private packetType type;
    private Boolean stopflag;
    private History hmp;
    private CountDownLatch countdowntimer;
    private int historyLen;
    private DatagramSocket socket;
    private ByteArrayOutputStream outputStream;

    public HistorySender(DatagramPacket packet, History hmp) {
        this.seq = 0;
        this.isLast = false;
        this.stopflag = false;
        this.packet = packet;
        this.udpport = packet.getPort();
        this.ip = packet.getAddress();
        this.hmp = hmp;
        outputStream = new ByteArrayOutputStream( );

    }

    public void setPacket(DatagramPacket packet) {
        this.packet = packet;
        try {
            byte[] rcvdData = packet.getData();
            ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
            Data protoPkt = Data.parseDelimitedFrom(instream);
            type = protoPkt.getType();
            if (protoPkt.getIsLast()) {
                stopflag = true;
            }
            if ((type == packetType.ACK) && (protoPkt.getSeqNo() == seq))
                countdowntimer.countDown();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        try {
            socket = new DatagramSocket(Integer.parseInt(PORT));
            ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
            hmp.writeDelimitedTo(outstream);
            byte[] historyByteArray = outstream.toByteArray();
            historyLen = historyByteArray.length;
            lastPacketLen = historyLen % 10;
            if (lastPacketLen == 0)
                packetNo = historyLen / 10;
            else
                packetNo = historyLen / 10 + 1;

            while (!stopflag) {
                byte[] rcvdData = packet.getData();
                ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
                Data protoPkt = Data.parseDelimitedFrom(instream);
                type = protoPkt.getType();
                if (type == packetType.REQUEST) {

                    do {
                        count = 0;
                        int i = 0;
                        while (i < 4 && packetNo > 0) {
                            if (packetNo - 1 == 0) {
                                isLast = true;
                                lastIndex = i;
                            }
                            byte[] bytearrays = new byte[10];
                            for (int j = 0; j < 10; j++) {
                                bytearrays[j] = historyByteArray[count];
                                count++;
                            }
                            Data singlePacket = Data.newBuilder().setSeqNo(seq).setIsLast(isLast).setData(ByteString.copyFrom(bytearrays)).setType(packetType.DATA).build();
                            singlePacket.writeDelimitedTo(outstream);
                            byte[] singlePacketByteArray = outstream.toByteArray();
                            DatagramPacket datagramPacket = new DatagramPacket(singlePacketByteArray, singlePacketByteArray.length, InetAddress.getLocalHost(), udpport);
                            socket.send(datagramPacket);
                            seq++;
                            packetNo--;
                            i++;
                        }
                        countdowntimer = new CountDownLatch(1);
                    } while (!countdowntimer.await(2000, TimeUnit.MILLISECONDS));


                } else if (type == packetType.ACK) {
                    do {
                        int i = 0;
                        while (i < 4 && packetNo > 0) {
                            if (packetNo - 1 == 0) {
                                isLast = true;
                                lastIndex = i;
                            }
                            byte[] bytearrays = new byte[10];
                            for (int j = 0; j < 10; j++) {
                                bytearrays[j] = historyByteArray[count];
                                count++;
                            }
                            Data singlePacket = Data.newBuilder().setSeqNo(seq).setIsLast(isLast).setData(ByteString.copyFrom(bytearrays)).setType(packetType.DATA).build();
                            singlePacket.writeDelimitedTo(outstream);
                            byte[] singlePacketByteArray = outstream.toByteArray();
                            DatagramPacket datagramPacket = new DatagramPacket(singlePacketByteArray, singlePacketByteArray.length, InetAddress.getLocalHost(), udpport);
                            socket.send(datagramPacket);
                            seq++;
                            packetNo--;
                            i++;
                        }
                        countdowntimer = new CountDownLatch(1);
                    } while (!countdowntimer.await(2000, TimeUnit.MILLISECONDS));


                } else if (type == packetType.DATA) {
                    if (protoPkt.getSeqNo()==seq) {
                        byte[] dataPacket = protoPkt.getData().toByteArray();
                        outputStream.write( dataPacket );
                        if (protoPkt.getIsLast())
                        {

                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}