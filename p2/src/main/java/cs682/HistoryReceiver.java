package cs682;

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
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

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

                byte[] rcvdData = packet.getData();
                ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
                Data protoPkt = Data.parseDelimitedFrom(instream);
                packetType type = protoPkt.getType();
                int udpPort = packet.getPort();
                String udpIp = packet.getAddress().toString();
                rwl.readLock().lock();
                boolean isexist = historyHandler.containsKey(udpIp + udpPort);
                rwl.readLock().unlock();
                if (type == packetType.REQUEST) {   //Handle REQUEST
                    if (DEBUGMODE) System.out.println("Receive a Request Packet.\n");

                    if (!isexist) {
                        HistorySender hs = new HistorySender(packet, hm, historyHandler);
                        if (DEBUGMODE) System.out.println("Request Add into hashmap." + udpIp + udpPort);
                        rwl.writeLock().lock();
                        historyHandler.put(udpIp + udpPort, hs);
                        rwl.writeLock().unlock();
                        threads.submit(hs);
                    } else {
                        if (DEBUGMODE) System.out.println("Your Services are running.\n ");
                        System.out.println("You are working with " + udpIp + " " + udpPort+ " Please wait");
                    }
                } else {
                    if (isexist) {  //Handle ACK and DATA
                        threads.submit(new ReceivePacketHandler(historyHandler, packet));//Create threads to set packets
//                        HistorySender hs = historyHandler.get(udpIp + udpPort); //Get thread from hash map
//                       hs.setPacket(packet); //Pass packet into HistorySender
                    } else if (type == packetType.DATA) { //Create a new thread when receive a new DATA
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
