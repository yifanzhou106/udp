package cs682.UdpHistory;

import cs682.ChatProto1.ChatProto;
import cs682.ChatProto1.History;
import cs682.ZooKeeperConnector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static cs682.Chat.*;

/**
 * HistoryManager will handle with history arraylist
 */
public class HistoryManager {

    private List<ChatProto> history;
    public ZooKeeperConnector zkc;

    public HistoryManager(ExecutorService threads, ZooKeeperConnector zkc) {
        history = new ArrayList<>();
        this.zkc = zkc;

    }

    /**
     * Added a message into arraylist
     *
     * @param receiveMessage
     */
    public void addProtoHistory(ChatProto receiveMessage) {
        history.add(receiveMessage);
    }

    public History getHistoryPacket() {
        rwl.readLock().lock();
        History.Builder packet = History.newBuilder();
        for (int i = 0; i < history.size(); i++) {
            packet.addHistory(history.get(i));
        }
        rwl.readLock().unlock();
        return packet.build();
    }

    /**
     * After user receive all DATA packet, and create a byte[]
     * Will call this method to translate to History packet
     * Then Overwrite the local arraylist
     *
     * @param historyByteArray
     */
    public void setHistoryByteArray(byte[] historyByteArray) {

        try {
            rwl.writeLock().lock();
            ByteArrayInputStream instream = new ByteArrayInputStream(historyByteArray);
            History packet = History.parseDelimitedFrom(instream);

            if (DEBUGMODE) System.out.println(packet);

            history = new ArrayList<>();
            for (int i = 0; i < packet.getHistoryCount(); i++)
                history.add(packet.getHistory(i));
            rwl.writeLock().unlock();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to print all history messages in arraylist
     */
    public void printList() {
        if (history.isEmpty()) {
            System.out.println("List is empty.\n");
        } else
            for (ChatProto item : history) {
                System.out.println("From" + item.getFrom() + ": " + item.getMessage() + "\n");
            }
    }
}
