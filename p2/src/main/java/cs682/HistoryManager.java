package cs682;

import com.sun.tools.jdi.Packet;
import cs682.ChatProto1.ChatProto;
import cs682.ChatProto1.Data;
import cs682.ChatProto1.ZKData;
import cs682.ChatProto1.History;
import cs682.ChatProto1.Data.packetType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import static cs682.Chat.UDPPORT;
import static cs682.Chat.isShutdown;

public class HistoryManager {

    private ArrayList<ChatProto> history;
    public ExecutorService threads;
    public ZooKeeperConnector zkc;

    public HistoryManager(ExecutorService threads, ZooKeeperConnector zkc) {
        history = new ArrayList<>();
        this.threads = threads;
        this.zkc = zkc;
        threads.submit(new historyReceiver());
    }

    public void addProtoHistory(ChatProto1.ChatProto receiveMessage) {
        history.add(receiveMessage);
    }

    public class historySender implements Runnable {
        int udpport;
        InetAddress ip;

        public historySender(int udpport, InetAddress ip) {
            this.udpport = udpport;
            this.ip = ip;
        }

        @Override
        public void run() {
            while (!isShutdown) {

            }
        }
    }

    public class historyReceiver implements Runnable {

        @Override
        public void run() {
            while (!isShutdown) {

                try {
                    DatagramSocket socket = new DatagramSocket(Integer.parseInt(UDPPORT));

                    byte[] data = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(data, data.length);

                    try {
                        socket.receive(packet);
                        byte[] rcvdData = packet.getData();
                        ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
                        Data protoPkt = Data.parseDelimitedFrom(instream);
                        packetType type = protoPkt.getType();

                        if (type == packetType.REQUEST) {
                            int udpport = packet.getPort();
                            InetAddress ip = packet.getAddress();
                            threads.submit(new historySender(udpport, ip));
                        } else if (type == packetType.ACK) {

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } catch (SocketException e) {
                    e.printStackTrace();
                }
            }

        }

    }

}
