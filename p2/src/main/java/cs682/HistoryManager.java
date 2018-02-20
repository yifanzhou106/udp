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
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static cs682.Chat.UDPPORT;
import static cs682.Chat.isShutdown;

public class HistoryManager {

    private List<ChatProto> history;
    public ZooKeeperConnector zkc;



    public HistoryManager(ExecutorService threads, ZooKeeperConnector zkc) {
        history = new ArrayList<>();
        this.zkc = zkc;

    }

    public void addProtoHistory(ChatProto receiveMessage) {
        history.add(receiveMessage);
    }

    public History getHistoryPacket() {
        History packet = History.newBuilder().addAllHistory(history).build();

        return packet;
    }

    public void setHistoryByteArray(byte[] historyByteArray) {

        try {
            ByteArrayInputStream instream = new ByteArrayInputStream(historyByteArray);
            History packet = History.parseDelimitedFrom(instream);
            history= packet.getHistoryList();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
    public void printList (){
        for (ChatProto item:history)
        {
            System.out.println("From"+item.getFrom()+": "+ item.getMessage()+"\n");
        }

    }


}
