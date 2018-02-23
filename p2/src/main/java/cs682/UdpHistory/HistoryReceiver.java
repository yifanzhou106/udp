package cs682.UdpHistory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import static cs682.Chat.*;

import cs682.ChatProto1.*;
import cs682.ChatProto1.Data.packetType;

/**
 * Get into history receiver thread
 * Will create an UDP connection
 * An hash map to hold request threads, key is udpip + udpport
 * Send Request Packet method included in it.
 * When receive a DatagramPacket, it will determine packet type, and do if else statement
 */
public class HistoryReceiver implements Runnable {
    private HashMap<String, HistorySender> historyHandler;
    public ExecutorService threads;
    private HistoryManager hm;
    static DatagramSocket socket;

    public HistoryReceiver(ExecutorService threads, HistoryManager hm) {
        this.threads = threads;
        this.hm = hm;
        historyHandler = new HashMap<>();
        try {
            socket = new DatagramSocket(Integer.parseInt(UDPPORT));
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * Send Request Packet method
     * Get user input in UI class
     * Then create a Request packet
     * finally, send it.
     *
     * @param udpIp
     * @param udpPort
     */
    public void sendRequest(String udpIp, String udpPort) {
        try {
            InetAddress ip = InetAddress.getByName(udpIp);
            int port = Integer.parseInt(udpPort);

            ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
            Data request = Data.newBuilder().setType(Data.packetType.REQUEST).build();
            request.writeDelimitedTo(outstream);
            byte[] requestPacketByteArray = outstream.toByteArray();
            DatagramPacket datagramPacket = new DatagramPacket(requestPacketByteArray, requestPacketByteArray.length, ip, port);
            if (!NOREQUEST)
                socket.send(datagramPacket);
            if (NOREQUEST)
                System.out.println("No request Mode, no request will be send");
            if (DEBUGMODE) System.out.println("Sending a request packet\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (!isShutdown) {
            byte[] data = new byte[1024];
            DatagramPacket packet = new DatagramPacket(data, data.length);

            try {
                socket.receive(packet);

                /**
                 * Receive packet and find out its type and other info
                 */
                byte[] rcvdData = packet.getData();
                ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
                Data protoPkt = Data.parseDelimitedFrom(instream);
                packetType type = protoPkt.getType();
                int udpPort = packet.getPort();
                String udpIp = packet.getAddress().toString();
                rwl.readLock().lock();
                boolean isexist = historyHandler.containsKey(udpIp + udpPort);
                rwl.readLock().unlock();

                /**
                 * After get the type, run if statement
                 * First, receive REQUEST packet
                 */
                if (type == packetType.REQUEST) {   //Handle REQUEST
                    if (DEBUGMODE) System.out.println("Receive a Request Packet.\n");

                    /**
                     *Check if key existed in hash map. If new, create new thread, else report error message
                     */
                    if (!isexist) {
                        HistorySender hs = new HistorySender(packet, hm, historyHandler);
                        if (DEBUGMODE) System.out.println("Request Add into hashmap." + udpIp + udpPort);
                        rwl.writeLock().lock();
                        historyHandler.put(udpIp + udpPort, hs);
                        rwl.writeLock().unlock();
                        threads.submit(hs);
                    } else {
                        if (DEBUGMODE) System.out.println("Your Services are running.\n ");
                        System.out.println("You are working with " + udpIp + " " + udpPort + " Please wait");
                    }
                }

                /**
                 * Or receive ACK or DATA
                 */
                else {

                    /**
                     * Submit a new thread to set packet.
                     * Make the program faster
                     */
                    if (isexist) {  //Handle ACK and DATA
                        threads.submit(new ReceivePacketHandler(historyHandler, packet));//Create threads to set packets
                    }

                    /**
                     * After sent REQUEST, user will receive a DATA packet
                     * If Hash map do not have this key, here will create a new thread to receive DATA packet
                     */
                    else if (type == packetType.DATA) { //Create a new thread when receive a new DATA
                        HistorySender hs = new HistorySender(packet, hm, historyHandler);
                        System.out.println("Connected, let's loading");
                        if (DEBUGMODE) System.out.println("Data Add into hashmap." + udpIp + udpPort);
                        rwl.writeLock().lock();
                        historyHandler.put(udpIp + udpPort, hs);
                        rwl.writeLock().unlock();
                        threads.submit(hs);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
