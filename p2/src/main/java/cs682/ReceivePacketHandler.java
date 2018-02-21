package cs682;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.util.HashMap;

public class ReceivePacketHandler implements Runnable {
    private DatagramPacket packet;
    private HashMap<String, HistorySender> historyHandler;
    private int udpPort;
    private String udpIp;
    public ReceivePacketHandler (HashMap<String, HistorySender> historyHandler,DatagramPacket packet)
    {
        this.packet=packet;
        this.historyHandler = historyHandler;
         this.udpPort = packet.getPort();
         this.udpIp = packet.getAddress().toString();
    }
    @Override
    public void run() {
        HistorySender hs = historyHandler.get(udpIp + udpPort); //Get thread from hash map
        hs.setPacket(packet); //Pass packet into HistorySender
    }
}
