package cs682;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
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

    private int localcount;
    private int localpacketNo;
    private int localseq;

    private DatagramPacket packet;
    private packetType type;
    private Boolean stopflag;
    private HistoryManager hm;
    private History hmp;
    private CountDownLatch countdowntimer;
    private int historyLen;
    private DatagramSocket socket;
    private ByteArrayOutputStream outputStream;
    private byte[] receivedData;

    public HistorySender(DatagramPacket packet, HistoryManager hm) {
        this.seq = 0;
        this.isLast = false;
        this.stopflag = false;
        this.packet = packet;
        this.udpport = packet.getPort();
        this.ip = packet.getAddress();
        this.hmp = hm.getHistoryPacket();
        outputStream=new ByteArrayOutputStream(1024);
        receivedData =new byte[1024];
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
            if ((type == packetType.ACK) && (protoPkt.getSeqNo() == seq - 1))
                countdowntimer.countDown();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        try {
            socket = new DatagramSocket(Integer.parseInt(UDPPORT));
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
                        localcount = 0;
                        localpacketNo = packetNo;
                        localseq = seq;
                        int i = 0;
                        while (i < 4 && localpacketNo > 0) {
                            if (localpacketNo - 1 == 0) {
                                isLast = true;
                                lastIndex = i;
                            } else
                                isLast = false;

                            byte[] bytearrays = new byte[10];
                            for (int j = 0; j < 10; j++) {
                                bytearrays[j] = historyByteArray[localcount];
                                localcount++;
                            }
                            Data singlePacket = Data.newBuilder().setSeqNo(localseq).setIsLast(isLast).setData(ByteString.copyFrom(bytearrays)).setType(packetType.DATA).build();
                            singlePacket.writeDelimitedTo(outstream);
                            byte[] singlePacketByteArray = outstream.toByteArray();
                            DatagramPacket datagramPacket = new DatagramPacket(singlePacketByteArray, singlePacketByteArray.length, ip, udpport);
                            socket.send(datagramPacket);
                            localseq++;
                            localpacketNo--;
                            i++;
                        }
                        countdowntimer = new CountDownLatch(1);
                    } while (!countdowntimer.await(2000, TimeUnit.MILLISECONDS));
                    count = localcount;
                    packetNo = localpacketNo;
                    seq = localseq;

                } else if (type == packetType.ACK) {
                    do {
                        int i = 0;
                        localcount = count;
                        localpacketNo = packetNo;
                        localseq = seq;
                        while (i < 4 && localpacketNo > 0) {
                            if (localpacketNo - 1 == 0) {
                                isLast = true;
                                lastIndex = i;
                            } else
                                isLast = false;

                            byte[] bytearrays = new byte[10];
                            for (int j = 0; j < 10; j++) {
                                bytearrays[j] = historyByteArray[localcount];
                                localcount++;
                            }
                            Data singlePacket = Data.newBuilder().setSeqNo(localseq).setIsLast(isLast).setData(ByteString.copyFrom(bytearrays)).setType(packetType.DATA).build();
                            singlePacket.writeDelimitedTo(outstream);
                            byte[] singlePacketByteArray = outstream.toByteArray();
                            DatagramPacket datagramPacket = new DatagramPacket(singlePacketByteArray, singlePacketByteArray.length, ip, udpport);
                            socket.send(datagramPacket);
                            localseq++;
                            localpacketNo--;
                            i++;
                        }
                        countdowntimer = new CountDownLatch(1);
                    } while (!countdowntimer.await(2000, TimeUnit.MILLISECONDS));
                    count = localcount;
                    packetNo = localpacketNo;
                    seq = localseq;

                } else if (type == packetType.DATA) {
                    if (protoPkt.getSeqNo() == seq) {
                        isLast = protoPkt.getIsLast();
                        byte[] dataPacket = protoPkt.getData().toByteArray();
                        outputStream.write(dataPacket);
                        if (isLast) {
                            receivedData = outputStream.toByteArray();
                            hm.setHistoryByteArray(receivedData);
                            stopflag=true;
                        }
                        Data ack = Data.newBuilder().setSeqNo(seq).setType(packetType.ACK).build();
                        ack.writeDelimitedTo(outstream);
                        byte[] ackPacketByteArray = outstream.toByteArray();
                        DatagramPacket datagramPacket = new DatagramPacket(ackPacketByteArray, ackPacketByteArray.length, ip, udpport);
                        socket.send(datagramPacket);
                        seq++;
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