package cs682;

import com.google.protobuf.InvalidProtocolBufferException;
import cs682.ChatProto1.Reply;
import cs682.ChatProto1.ZKData;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import cs682.HistoryManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * A chat client that uses raw sockets to communicate with clients
 *
 * @Author Yifan Zhou
 */
public class Chat {
    public static String PORT = "8000";
    public static String UDPPORT = "5700";
    static volatile boolean isShutdown = false;

    public static final int ZpPORT = 7000;
    public static final String ZpHOST = "localhost";

    public static final String group = "/CS682_Chat";
    public static String member = "/yifanZhou";
    public static String user = "yifanzhou";

    public static String format = "yyyy-MM-dd HH:mm:ss";

    final static ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    final ExecutorService threads = Executors.newFixedThreadPool(4);

    static Map<String, ArrayList<String>> userMap = new HashMap();
    static Map<String, String> bcastHistoryMap = new TreeMap<>();
    private HistoryManager hm;
    private UI ui;
    private ReceiveMessage rm;

    /**
     * Main function load hotelData and reviews, Then call startServer.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        Chat client = new Chat();
        for (int i = 0; i < args.length; i++) {
            System.out.println(args[i]);
        }
//        if (args[0].equals("-user")) {
//            user = args[1];
//            member = "/" + user;
//            //System.out.println(member);
//        }
//        if (args[2].equals("-port"))
//            PORT = args[3];
//
//        if (args[4].equals("-udpport"))
//            UDPPORT = args[5];


        client.beginChat();

    }

    /**
     * create a welcoming socket
     * Code from 601
     */
    public void beginChat() {
        ZooKeeperConnector zkc= new ZooKeeperConnector();
        zkc.joinZooKeeper();
        hm = new HistoryManager(threads,zkc);
        threads.submit(new HistoryReceiver(threads,hm));
        threads.submit(new UI(threads,zkc)); //Create UI thread
        threads.submit(new ReceiveMessage(threads,zkc,hm));

    }
}