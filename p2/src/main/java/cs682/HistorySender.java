package cs682;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

import cs682.ChatProto1.ChatProto;
import cs682.ChatProto1.Data;
import cs682.ChatProto1.ZKData;
import cs682.ChatProto1.History;
import cs682.ChatProto1.Data.packetType;

import static cs682.Chat.*;

public class HistorySender implements Runnable {
    private int udpport;
    private InetAddress ip;
    private  int seq;
    private DatagramPacket packet;
    private packetType type;
    private Boolean stopflag=false;

    public HistorySender(DatagramPacket packet) {
        this.seq =0;
        this.packet = packet;
        this.udpport = packet.getPort();
        this.ip = packet.getAddress();
    }

    public void setPacket(DatagramPacket packet) {
        this.packet = packet;
    }

    @Override
    public void run() {
        while (!stopflag) {
            try {
                byte[] rcvdData = packet.getData();
                ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
                Data protoPkt = Data.parseDelimitedFrom(instream);
                type = protoPkt.getType();

                if (type== packetType.REQUEST)
                {

                }

                else if (type == packetType.ACK)
                {

                }
                else if (type == packetType.DATA)
                {

                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}