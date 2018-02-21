package cs682;

import com.google.protobuf.ByteString;
import cs682.ChatProto1.Data;
import cs682.ChatProto1.Data.packetType;
import cs682.ChatProto1.History;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static cs682.HistoryReceiver.socket;

public class HistorySender implements Runnable {
    private int udpport;
    private InetAddress ip;
    private int seq;
    private boolean isLast;
    private int packetNo;
    private int packetNoNoChange;
    private int lastPacketLen;
    private int count;

    private int localcount;
    private int localpacketNo;
    private int localseq;
    private int preSeq;
    private int newSeq;


    private HashMap<String, HistorySender> historyHandler;
    private DatagramPacket packet;
    private packetType type;
    private Boolean stopflag;
    private HistoryManager hm;
    private History hmp;
    private CountDownLatch countdowntimer;
    private int historyLen;
    private ByteArrayOutputStream outputStream;
    private byte[] receivedData;
    private ByteArrayOutputStream outstream;

    public HistorySender(DatagramPacket packet, HistoryManager hm, HashMap<String, HistorySender> historyHandler) {
        this.preSeq = 1;
        this.seq = 1;
        this.isLast = false;
        this.stopflag = false;
        this.packet = packet;
        this.udpport = packet.getPort();
        this.ip = packet.getAddress();
        this.hmp = hm.getHistoryPacket();
        this.hm = hm;
        this.historyHandler = historyHandler;
        outputStream = new ByteArrayOutputStream();
        receivedData = new byte[1024];
    }


    public void setPacket(DatagramPacket packet) {
        this.packet = packet; //Renew packet

        try {
            byte[] rcvdData = packet.getData();
            ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
            Data protoPkt = Data.parseDelimitedFrom(instream);

            type = protoPkt.getType();
            if ((type == packetType.ACK) && (protoPkt.getSeqNo() == packetNoNoChange)) {
                System.out.println("Sending data completed");
                stopflag = true;
                historyHandler.remove(ip.toString() + udpport);
                countdowntimer.countDown();
            } else if (type == packetType.ACK && (protoPkt.getSeqNo() > preSeq)) {
                newSeq = protoPkt.getSeqNo();
                packetNo = packetNoNoChange - newSeq;
                countdowntimer.countDown();
                System.out.println("\nreceive ack packet " + protoPkt.getSeqNo());
                System.out.println("\nrelease countdown ");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        try {
            outstream = new ByteArrayOutputStream(1024);
            hmp.writeDelimitedTo(outstream);
            byte[] historyByteArray = outstream.toByteArray();
            packetNoNoChange = getPacketNo(historyByteArray);

            while (!stopflag) {
                byte[] rcvdData = packet.getData();
                ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
                Data protoPkt = Data.parseDelimitedFrom(instream);
                type = protoPkt.getType();

                preSeq = seq; //Set Previous Seq

                if (type == packetType.REQUEST) {
                    System.out.println("Total packet no is " + packetNoNoChange);
                    do {
                        createPackets(historyByteArray, 0, packetNo, seq, 4);
                    } while (!countdowntimer.await(2000, TimeUnit.MILLISECONDS));
                    count = localcount;
                    seq = localseq;


                } else if (type == packetType.ACK) {
                    createPackets(historyByteArray, count, packetNo, seq, 4 - (localseq - 1 - newSeq)); //shift the window and send some packets then wait
                    countdowntimer = new CountDownLatch(1);

                    while (!countdowntimer.await(2000, TimeUnit.MILLISECONDS)) {
                        createPackets(historyByteArray, 10 * newSeq, packetNo, newSeq + 1, 4);//send all 4 packets in this window again and again
                        System.out.println("\nSet countdown timer");
                        countdowntimer = new CountDownLatch(1);
                    }
                    count = localcount;
                    seq = localseq;

                } else if (type == packetType.DATA) {
                    if (protoPkt.getSeqNo() == seq) {
                        System.out.println("\nReceive packet " + seq);
                        isLast = protoPkt.getIsLast();
                        byte[] dataPacket = protoPkt.getData().toByteArray();
                        outputStream.write(dataPacket);

                        Data ack = Data.newBuilder().setType(packetType.ACK).setSeqNo(seq).build();
                        sendPacket(ack);
                        System.out.println("\nSend ack " + seq + " to " + ip + udpport);

                        if (isLast) {
                            receivedData = outputStream.toByteArray();
                            hm.setHistoryByteArray(receivedData);
                            System.out.println("Create byte array successfully");
                            stopflag = true;
                            historyHandler.remove(ip.toString() + udpport);
                        }
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

    public void sendPacket(Data singlePacket) {
        try {
            outstream = new ByteArrayOutputStream(1024);
            singlePacket.writeDelimitedTo(outstream);
            byte[] singlePacketByteArray = outstream.toByteArray();
            DatagramPacket datagramPacket = new DatagramPacket(singlePacketByteArray, singlePacketByteArray.length, ip, udpport);
            socket.send(datagramPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getPacketNo(byte[] historyByteArray) {
        historyLen = historyByteArray.length;
        lastPacketLen = historyLen % 10;
        if (lastPacketLen == 0)
            packetNo = historyLen / 10;
        else
            packetNo = historyLen / 10 + 1;

        return packetNo;
    }

    public void createPackets(byte[] historyByteArray, int count, int packetNo, int seq, int numofpacket) {
        localcount = count;
        localpacketNo = packetNo;
        localseq = seq;
        int i = 0;
        int k = 10;
        while (i < numofpacket && localpacketNo > 0) {
            if (localpacketNo - 1 == 0) {
                isLast = true;
                k = lastPacketLen;
            } else
                isLast = false;

            byte[] bytearrays = new byte[10];
            for (int j = 0; j < k; j++) {
                bytearrays[j] = historyByteArray[localcount];
                localcount++;
            }
            Data singlePacket = Data.newBuilder().setType(packetType.DATA).setSeqNo(localseq).setData(ByteString.copyFrom(bytearrays)).setIsLast(isLast).build();
            sendPacket(singlePacket);
            System.out.println("\nSend packet " + localseq);
            localseq++;
            localpacketNo--;
            i++;
        }
        System.out.println("\nSet countdown timer");
        countdowntimer = new CountDownLatch(1);
    }

}