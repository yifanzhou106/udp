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
        }

        catch(SocketException e)
        {
            e.printStackTrace();
        }
    }

    public void sendRequest(String udpIp, String udpPort) {
        try {
            InetAddress ip = InetAddress.getByName(udpIp);
            int port = Integer.parseInt(udpPort);
            System.out.println("Before Sending a request packet \n");
          //  DatagramSocket socket = new DatagramSocket(Integer.parseInt(PORT));
            ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
            Data request = Data.newBuilder().setType(Data.packetType.REQUEST).build();
            request.writeDelimitedTo(outstream);
            byte[] requestPacketByteArray = outstream.toByteArray();
            DatagramPacket datagramPacket = new DatagramPacket(requestPacketByteArray, requestPacketByteArray.length, ip, port);
            socket.send(datagramPacket);
            System.out.println("Sending a request packet\n");
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
                   // System.out.println(protoPkt);
                    packetType type = protoPkt.getType();

                    int udpPort = packet.getPort();
                    String udpIp = packet.getAddress().toString();
                    //System.out.println(type+udpIp + udpPort);
                    if (type == packetType.REQUEST) {   //Handle REQUEST
                        System.out.println("Receive a Request Packet.\n");
                      //  System.out.println(protoPkt);
                        if (!historyHandler.containsKey(udpIp + udpPort)) {
                            HistorySender hs = new HistorySender(packet, hm,historyHandler);
                            System.out.println("Request Add into hashmap."+ udpIp + udpPort);
                            historyHandler.put(udpIp + udpPort, hs);
                            threads.submit(hs);
                        } else {
                            System.out.println("Your Services are running.\n ");
                        }
                    } else {
                        if (historyHandler.containsKey(udpIp + udpPort)) {  //Handle ACK and DATA

                            threads.submit(new ReceivePacketHandler(historyHandler,packet));
                        }
                        else if (type== packetType.DATA){
                            HistorySender hs = new HistorySender(packet, hm,historyHandler);
                            System.out.println("Data Add into hashmap."+ udpIp + udpPort);
                            historyHandler.put(udpIp + udpPort, hs);
                            threads.submit(hs);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }


        }

    }

}
