package cs682;

import com.google.protobuf.InvalidProtocolBufferException;
import cs682.ChatProto1.Reply;
import cs682.ChatProto1.ZKData;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

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
    public static String PORT = "5610";
    static volatile boolean isShutdown = false;

    public static final int ZpPORT = 2181;
    public static final String ZpHOST = "mc01";

    public static final String group = "/CS682_Chat";
    public static String member = "/yifanzhou";
    public static String user = "yifanzhou";

    public static String format = "yyyy-MM-dd HH:mm:ss";

    final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    final ExecutorService threads = Executors.newFixedThreadPool(4);

    private Map<String, ArrayList<String>> userMap = new HashMap();
    private Map<String, String> bcastHistoryMap = new TreeMap<>();
    private HistoryManager hm= new HistoryManager();

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
        if (args[0].equals("-user")) {
            user = args[1];
            member="/"+user;
            //System.out.println(member);
        }
        if (args[2].equals("-port"))
            PORT = args[3];


        client.beginChat();

    }

    /**
     * create a welcoming socket
     * Code from 601
     */
    public void beginChat() {
        ZooKeeper zk = connectZooKeeper(); //Create zookeeper instance
        joinZooKeeper(zk);
        threads.submit(new userInput(zk)); //Create UI thread
        threads.submit(new ReceiveMessageWorker());
    }


    public class userInput implements Runnable {
        ZooKeeper zk;

        private userInput(ZooKeeper zk) {
            this.zk = zk;
        }

        @Override
        public void run() {
            while (!isShutdown) {
                boolean ifPrint = false;
                listZooKeeperMember(zk, userMap, ifPrint);

                Scanner reader = new Scanner(System.in);
                System.out.println("Enter your choices (Enter \"help\" for help): ");
                String userChoice = reader.nextLine();
                String[] splitedUserChoice = userChoice.split(" ");


                switch (splitedUserChoice[0]) {
                    case "help":
                        System.out.println("\n**************************");
                        System.out.println("\n1. send $name ");
                        System.out.println("\n2. bcast ");
                        System.out.println("\n3. list");
                        System.out.println("\n4. history\n");
                        System.out.println("\n**************************");
                        break;

                    case "send":
                        if (!splitedUserChoice[1].isEmpty()) {
                            String name = splitedUserChoice[1];
                            System.out.println("\n\nEnter your message: ");
                            String message = reader.nextLine();
                            threads.submit(new SendMessageWorker(name, message));
                        } else {
                            System.out.println("Wrong data format");
                        }
                        break;

                    case "bcast":
                        boolean isBcast = true;
                        System.out.println("Enter your message: ");
                        String message = reader.nextLine();
                        threads.submit(new SendMessageWorker(message, isBcast));
                        break;

                    case "list":
                        ifPrint = true;
                        listZooKeeperMember(zk, userMap, ifPrint);
                        break;

                    case "history":
                        rwl.readLock().lock();
                        if (bcastHistoryMap.isEmpty()) {
                            System.out.println("\nBroadcast History is empty\n");
                        } else
                            for (Map.Entry<String, String> map : bcastHistoryMap.entrySet()) {
                                System.out.println(map.getValue() + "  Date: " + map.getKey());
                            }
                        rwl.readLock().unlock();
                        break;

                    case "exit":
                        isShutdown = true;
                        threads.shutdownNow();
                        System.exit(0);
                        break;


                    default:
                        System.out.println("\nWrong Input\n");
                        break;
                }
            }

        }


    }

    /**
     * Create zookeeper instance
     * Codes from zp example
     *
     * @return
     */
    private ZooKeeper connectZooKeeper() {

        ZooKeeper zk = null;
        try {
            //Connect to ZK instance
            final CountDownLatch connectedSignal = new CountDownLatch(1);
            zk = new ZooKeeper(ZpHOST + ":" + ZpPORT, 1000, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getState() == Event.KeeperState.SyncConnected) {
                        connectedSignal.countDown();
                    }
                }
            });
            System.out.println("Connecting...");
            connectedSignal.await();
            System.out.println("Connected");
        } catch (IOException e) {
            System.out.println(e);
        } catch (InterruptedException e) {
            System.out.println(e);
        }
        return zk;
    }


    /**
     * Join zookeeper
     * Codes from zp example
     *
     * @return
     */
    private void joinZooKeeper(ZooKeeper zk) {
        ZKData data = ZKData.newBuilder().setIp("mc10").setPort(PORT).build();
        try {
            String createdPath = zk.create(group + member,
                    data.toByteArray(),  //probably should be something more interesting here...
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT);//_SEQUENTIAL
            System.out.println("Joined group " + group + member);

        } catch (KeeperException ke) {
            System.out.println("Unable to or have already joined group " + group + " as " + member);
        } catch (InterruptedException e) {
            System.out.println(e);
        }
    }


    /**
     * List zookeeper members
     * Codes from zp example
     *
     * @return
     */
    private void listZooKeeperMember(ZooKeeper zk, Map<String, ArrayList<String>> userMap, boolean ifPrint) {
        rwl.writeLock().lock();

        try {
            List<String> children = zk.getChildren(group, false);
            for (String child : children) {
                if (ifPrint)
                    System.out.println(child + "\n");

                //get data about each child
                Stat s = new Stat();
                byte[] raw = zk.getData(group + "/" + child, false, s);
                ZKData data = ZKData.parseFrom(raw);
                if (data != null) {
                    String ip = data.getIp();
                    String port = data.getPort();

                    ArrayList<String> userData = new ArrayList();
                    userData.add(ip);
                    userData.add(port);
                    //System.out.print("IP: " + ip + "\tPort: " + port);
                    userMap.put(child, userData);
                    //System.out.print("\n");

                } else {
                    System.out.println("\tNO DATA");
                }
            }
        } catch (KeeperException ke) {
            System.out.println("Unable to list members of group " + group);
        } catch (InvalidProtocolBufferException e) {
            System.out.println(e);
        } catch (InterruptedException e) {
            System.out.println(e);
        }

        rwl.writeLock().unlock();
    }


    private class SendMessageWorker implements Runnable {
        private Socket connectionSocket = new Socket();
        private String name = null;
        private String message = null;
        private boolean isBcast = false;
        private ArrayList<String> userData = new ArrayList();
        private String sip;
        private String sport;
        private int timeout = 1000;

        private SendMessageWorker(String name, String message) {
            this.message = message;
            this.name = name;
        }

        private SendMessageWorker(String message, boolean isBcast) {
            this.message = message;
            this.isBcast = isBcast;
        }

        @Override
        public void run() {
            System.out.println("Your message is: " + message);
            try {
                if (!isBcast) {
                    //get ip and port from user map
                    userData = userMap.get(name);
                    sip = userData.get(0);
                    sport = userData.get(1);
                    InetAddress ip = InetAddress.getByName(sip);
                    int port = Integer.parseInt(sport);
                    //Create connection
                    connectionSocket = new Socket(ip, port);
                    connectionSocket.setSoTimeout(timeout);
                    ChatProto1.ChatProto sendMessage = ChatProto1.ChatProto.newBuilder().setMessage(message).setFrom(member).setIsBcast(isBcast).build();
                    OutputStream outstream = connectionSocket.getOutputStream();
                    sendMessage.writeDelimitedTo(outstream);
                    InputStream instream = connectionSocket.getInputStream();
                    Reply replyMessage = Reply.getDefaultInstance();
                    replyMessage = replyMessage.parseDelimitedFrom(instream);
                    System.out.println(replyMessage.getStatus() + " " + replyMessage.getMessage());

                } else {
                    //System.out.println("Broad cast\n");
                    for (Map.Entry<String, ArrayList<String>> map : userMap.entrySet()) {
                        String name = map.getKey();
                        ArrayList<String> userData = map.getValue();
                        try {
                            if (!name.equals(user)) {
                                sip = userData.get(0);
                                sport = userData.get(1);
                                InetAddress ip = InetAddress.getByName(sip);
                                int port = Integer.parseInt(sport);
                                //Create connection
                                //connectionSocket = new Socket(ip, port);
                                connectionSocket = new Socket();
                                connectionSocket.connect(new InetSocketAddress(ip, port), timeout);
                                ChatProto1.ChatProto sendMessage = ChatProto1.ChatProto.newBuilder().setMessage(message).setFrom(member).setIsBcast(isBcast).build();
                                OutputStream outstream = connectionSocket.getOutputStream();
                                sendMessage.writeDelimitedTo(outstream);
                                InputStream instream = connectionSocket.getInputStream();
                                Reply replyMessage = Reply.getDefaultInstance();
                                replyMessage = replyMessage.parseDelimitedFrom(instream);
                                System.out.println(name + " receive message");
                            }

                        } catch (SocketTimeoutException e) {
                            // System.out.println(e);
                        } catch (UnknownHostException e) {
                            // System.out.println(e);
                        } catch (ConnectException e) {
                            //System.out.println(e);
                        }

                    }
                }

            } catch (IOException e) {
                System.out.println(e);

            }
        }

    }

    private class ReceiveMessageWorker implements Runnable {

        @Override
        public void run() {
            // System.out.println("A client connected.");

            try {
                ServerSocket welcomingSocket = new ServerSocket(Integer.parseInt(PORT));
                while (!isShutdown) {
                    Socket connectionSocket = welcomingSocket.accept();
                    InputStream instream = connectionSocket.getInputStream();
                    ChatProto1.ChatProto receiveMessage = ChatProto1.ChatProto.getDefaultInstance();
                    receiveMessage = receiveMessage.parseDelimitedFrom(instream);
                    String singleMessage = receiveMessage.getFrom() + " says: " + receiveMessage.getMessage();
                    String bcastMessage = receiveMessage.getFrom() + " broadcast: " + receiveMessage.getMessage();

                    SimpleDateFormat sdf = new SimpleDateFormat(format); //Code from Zk dateServer example
                    String date = sdf.format(new Date());
                    System.out.println("\n###################\n");
                    if (!receiveMessage.getIsBcast())
                        System.out.println(singleMessage);
                    else {
                        System.out.println(bcastMessage);
                        rwl.writeLock().lock();
                        hm.addProtoHistory(receiveMessage);
                        bcastHistoryMap.put(date, bcastMessage);
                        rwl.writeLock().unlock();
                    }

                    System.out.println("Response date: " + date);
                    System.out.println("\n###################\n");
                    Reply responseMessage = Reply.newBuilder().setStatus(200).setMessage("Ok").build();
                    OutputStream outstream = connectionSocket.getOutputStream();
                    responseMessage.writeDelimitedTo(outstream);

                }
                if (isShutdown) {
                    welcomingSocket.close();
                }
            } catch (IOException e) {
                System.out.println(e);

            }

        }

    }
}