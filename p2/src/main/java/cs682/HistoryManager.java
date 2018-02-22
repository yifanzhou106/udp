package cs682;

import com.google.protobuf.InvalidProtocolBufferException;
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

import static cs682.Chat.*;

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
        rwl.readLock().lock();
        History.Builder packet = History.newBuilder();
        for (int i=0;i<history.size();i++)
        {
            packet.addHistory(history.get(i));
        }
        rwl.readLock().unlock();
        return packet.build();
    }

    public void setHistoryByteArray(byte[] historyByteArray) {

        try {
            rwl.writeLock().lock();
            ByteArrayInputStream instream = new ByteArrayInputStream(historyByteArray);
            History packet = History.parseDelimitedFrom(instream);
            if (DEBUGMODE) System.out.println(packet);
            history = new ArrayList<>();
            for (int i=0;i<packet.getHistoryCount();i++)
                history.add(packet.getHistory(i));
            rwl.writeLock().unlock();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    public void printList (){
        if (history.isEmpty())
        {
            System.out.println("List is empty.\n");
        }
        else
            for (ChatProto item:history)
        {
            System.out.println("From"+item.getFrom()+": "+ item.getMessage()+"\n");
        }

    }


}
