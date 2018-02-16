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
import java.util.concurrent.ExecutorService;

import static cs682.Chat.UDPPORT;
import static cs682.Chat.isShutdown;

public class HistoryManager {

    private ArrayList<ChatProto> history;
    public ZooKeeperConnector zkc;


    public HistoryManager(ExecutorService threads, ZooKeeperConnector zkc) {
        history = new ArrayList<>();

        this.zkc = zkc;

    }

    public void addProtoHistory(ChatProto receiveMessage) {
        history.add(receiveMessage);
    }




}
