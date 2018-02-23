package cs682.UdpHistory;

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
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static cs682.Chat.*;
import static cs682.UdpHistory.HistoryReceiver.socket;

/**
 * HistorySender is a thread handling sending and receive packet with a ucp port
 */
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

    /**
     * ReceivePacketHandler will call this method and set a new packet
     * If meet all conditions, it will count down countdowntimer and release sending loop
     *
     * @param packet
     */
    public void setPacket(DatagramPacket packet) {
        this.packet = packet; //Renew packet

        try {
            byte[] rcvdData = packet.getData();
            ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
            Data protoPkt = Data.parseDelimitedFrom(instream);

            type = protoPkt.getType();
            if ((type == packetType.ACK) && (protoPkt.getSeqNo() == packetNoNoChange)) {

                if (DEBUGMODE) System.out.println("\nreceive ack packet " + protoPkt.getSeqNo() + "\n");

                System.out.println("Sending data completed");
                stopflag = true;
                rwl.writeLock().lock();
                historyHandler.remove(ip.toString() + udpport);
                rwl.writeLock().unlock();

                if (DEBUGMODE) System.out.println("Remove user" + ip.toString() + udpport + "successfully");

                countdowntimer.countDown();
            } else if (type == packetType.ACK && (protoPkt.getSeqNo() > preSeq)) {
                newSeq = protoPkt.getSeqNo();
                packetNo = packetNoNoChange - newSeq;
                countdowntimer.countDown();

                if (DEBUGMODE) System.out.println("\nreceive ack packet " + protoPkt.getSeqNo());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        try {

            /**
             * Get history packet from HistoryManager
             */
            outstream = new ByteArrayOutputStream(1024);
            hmp.writeDelimitedTo(outstream);
            byte[] historyByteArray = outstream.toByteArray();
            packetNoNoChange = getPacketNo(historyByteArray);

            while (!stopflag) {
                byte[] rcvdData = packet.getData();
                ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
                Data protoPkt = Data.parseDelimitedFrom(instream);
                type = protoPkt.getType();
                preSeq = newSeq; //Set Previous Seq
                boolean timeoutflag;

                /**
                 * Handle with REQUEST packet
                 * Will keep send packets from 1-4, until receive an ACK packet
                 * Debug: 50% drop DATA packet
                 */
                if (type == packetType.REQUEST) {

                    if (DEBUGMODE) System.out.println("Total packet no is " + packetNoNoChange);
                    if (DEBUGMODE) System.out.println("Total len of ths packet is " + historyLen);

                    do {
                        if (NODATA) System.out.println("NO data mode. 50% chances data will be send, keep waiting");
                        createPackets(historyByteArray, 0, packetNo, seq, 4);
                        timeoutflag = countdowntimer.await(2000, TimeUnit.MILLISECONDS);
                        if (!timeoutflag) {
                            if (DEBUGMODE) System.out.println("\nTime out!!!!!! Resending\n");
                        }
                    } while (!timeoutflag);
                    count = localcount;
                    seq = localseq;
                }

                /**
                 *Handle with ACK packet, will use createPackets method twice
                 * * Debug: 50% drop DATA packet
                 * 1. Find out how many packets need to be sent, then send them
                 */
                else if (type == packetType.ACK) {
                    createPackets(historyByteArray, count, packetNo, seq, 4 - (seq - 1 - newSeq)); //shift the window and send some packets then wait
                    countdowntimer = new CountDownLatch(1);
                    timeoutflag = countdowntimer.await(2000, TimeUnit.MILLISECONDS);
                    if (!timeoutflag) {
                        if (DEBUGMODE) System.out.println("\nTime out!!!!!! Resending\n");
                    }

                    /**
                     * 2. If timeout, Then resend 4 packets
                     */
                    while (!timeoutflag) {
                        createPackets(historyByteArray, 10 * newSeq, packetNo, newSeq + 1, 4);//send all 4 packets in this window again and again
                        countdowntimer = new CountDownLatch(1);

                        timeoutflag = countdowntimer.await(2000, TimeUnit.MILLISECONDS);
                        if (!timeoutflag) {
                            if (DEBUGMODE) System.out.println("\nTime out!!!!!! Resending\n");
                        }
                    }
                    count = localcount;
                    seq = localseq;
                }

                /**
                 * Handle with DATA packet, will store byte data into a ByteArrayOutputStream
                 * Then send ACK packet
                 * Debug: 30% drop ACK packet
                 */
                else if (type == packetType.DATA) {
                    if (protoPkt.getSeqNo() == seq) {
                        if (DEBUGMODE) System.out.println("\nReceive packet " + seq);
                        int n = 0;
                        isLast = protoPkt.getIsLast();

                        if (NOACK) {
                            Random rand = new Random();
                            n = rand.nextInt(10) + 1;
                        }

                        if (n < 7) {
                            byte[] dataPacket = protoPkt.getData().toByteArray();
                            outputStream.write(dataPacket);
                            Data ack = Data.newBuilder().setType(packetType.ACK).setSeqNo(seq).build();

                            if (NOACK) System.out.println("Send ACK packet " + seq);

                            sendPacket(ack);

                            if (DEBUGMODE) System.out.println("\nSend ack " + seq + " to " + ip + udpport);

                            if (isLast) {
                                receivedData = outputStream.toByteArray();
                                hm.setHistoryByteArray(receivedData);
                                System.out.println("Create byte array successfully");
                                stopflag = true;
                                rwl.writeLock().lock();
                                historyHandler.remove(ip.toString() + udpport);
                                rwl.writeLock().unlock();

                                if (DEBUGMODE) System.out.println("Remove user successfully");
                            }
                            seq++;
                        } else {
                            System.out.println("ACK packet " + seq + " has been dropped");
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

    /**
     * Function to send packets
     *
     * @param singlePacket
     */
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

    /**
     * Function to find out packet numbers
     *
     * @param historyByteArray
     * @return
     */
    public int getPacketNo(byte[] historyByteArray) {
        historyLen = historyByteArray.length;
        lastPacketLen = historyLen % 10;
        if (lastPacketLen == 0) {
            packetNo = historyLen / 10;
            lastPacketLen = 10;
        } else
            packetNo = historyLen / 10 + 1;
        return packetNo;
    }

    /**
     * Function to create packets
     * Debug: 50% drop DATA packets
     *
     * @param historyByteArray
     * @param count
     * @param packetNo
     * @param seq
     * @param numofpacket
     */
    public synchronized void createPackets(byte[] historyByteArray, int count, int packetNo, int seq, int numofpacket) {
        localcount = count;
        localpacketNo = packetNo;
        localseq = seq;
        int i = 0;
        int k = 10;

        while (i < numofpacket && localpacketNo > 0) {
            if (localseq > packetNoNoChange)  //for some cases: [1234] receive 3,but seq=5>total packet number 4,so do nothing
                break;
            if (localseq == packetNoNoChange) {
                isLast = true;
                k = lastPacketLen;
            } else {
                isLast = false;
            }

            /**
             * Build byte array
             */
            byte[] bytearrays = new byte[k];
            for (int j = 0; j < k; j++) {
                bytearrays[j] = historyByteArray[localcount];
                localcount++;
            }
            Data singlePacket = Data.newBuilder().setType(packetType.DATA).setSeqNo(localseq).setData(ByteString.copyFrom(bytearrays)).setIsLast(isLast).build();

            /**
             * Enter debug mode
             */
            if (NODATA) {
                Random rand = new Random();
                int n = rand.nextInt(10) + 1;
                if (n > 5) {
                    sendPacket(singlePacket);

                    if (NODATA) System.out.println("Send packet " + localseq);
                } else if (NODATA) System.out.println("Packet " + localseq + " has been dropped");
            }
            if (!NODATA) {
                sendPacket(singlePacket);

                if (DEBUGMODE) System.out.println("Send packet " + localseq);
            }
            localseq++;
            localpacketNo--;
            i++;
        }
        countdowntimer = new CountDownLatch(1);
    }
}