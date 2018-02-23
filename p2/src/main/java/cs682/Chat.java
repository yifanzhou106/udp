package cs682;

import cs682.UdpHistory.HistoryManager;
import cs682.UdpHistory.HistoryReceiver;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * A chat client that uses raw sockets to communicate with clients
 * Project 02
 * Last Version
 *
 * @Author Yifan Zhou
 */
public class Chat {
    public static String PORT = "8000";
    // public static String HOST = "mc10";
    public static String UDPPORT = "5700";

    public static Boolean DEBUGMODE = false;
    public static Boolean NOACK = false;
    public static Boolean NODATA = false;
    public static Boolean NOREQUEST = false;

    public static volatile boolean isShutdown = false;

    public static final int ZpPORT = 2181;//2181
    public static final String ZpHOST = "mc01";

    public static final String group = "/CS682_Chat";
    public static String member = "/yifanZhou1";
    public static String user = "yifanzhou";

    public static String format = "yyyy-MM-dd HH:mm:ss";

    public final static ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    public final ExecutorService threads = Executors.newFixedThreadPool(10);

    public static Map<String, ArrayList<String>> userMap = new HashMap();
    public static Map<String, String> bcastHistoryMap = new TreeMap<>();
    private HistoryManager hm;
    private HistoryReceiver hr;
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
        if (args[0].equals("-user")) {
            user = args[1];
            member = "/" + user;
        }
        if (args[2].equals("-port"))
            PORT = args[3];

        if (args[4].equals("-udpport"))
            UDPPORT = args[5];

        if (args.length > 6) {
            if (args[6].equals("-debug"))
                DEBUGMODE = true;
            if (args.length > 7) {
                if (args[7].equals("nodata"))
                    NODATA = true;
                if (args[7].equals("noack"))
                    NOACK = true;
                if (args[7].equals("norequest"))
                    NOREQUEST = true;
            }
        }
        client.beginChat();
    }

    /**
     * create threads
     */
    public void beginChat() {
        ZooKeeperConnector zkc = new ZooKeeperConnector();
        zkc.joinZooKeeper();
        hm = new HistoryManager(threads, zkc);
        hr = new HistoryReceiver(threads, hm);
        ui = new UI(threads, zkc, hr, hm);
        rm = new ReceiveMessage(threads, zkc, hm);
        threads.submit(hr);
        threads.submit(ui); //Create UI thread
        threads.submit(rm);

    }
}