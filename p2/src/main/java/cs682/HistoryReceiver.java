package cs682;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import static cs682.Chat.UDPPORT;
import static cs682.Chat.isShutdown;

public class HistoryReceiver implements Runnable {
    private HashMap<String, HistorySender> historyHandler;
    public ExecutorService threads;
    private HistoryManager hm;

    public HistoryReceiver(ExecutorService threads, HistoryManager hm) {
        this.threads = threads;
        this.hm = hm;
        historyHandler = new HashMap<>();

    }

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
                    ChatProto1.Data protoPkt = ChatProto1.Data.parseDelimitedFrom(instream);

                    ChatProto1.Data.packetType type = protoPkt.getType();
                    int udpPort = packet.getPort();
                    String udpIp = packet.getAddress().toString();
                    if (type == ChatProto1.Data.packetType.REQUEST) {
                        if (!historyHandler.containsKey(udpIp + udpPort)) {
                            HistorySender hs = new HistorySender(packet);
                            historyHandler.put(udpIp + udpPort, hs);
                            threads.submit(hs);
                        } else {
                            System.out.println("Your Services are running.\n ");
                        }
                    } else {
                        if (historyHandler.containsKey(udpIp + udpPort)) {
                            HistorySender hs = historyHandler.get(udpIp + udpPort);
                            hs.setPacket(packet);
                        }
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
