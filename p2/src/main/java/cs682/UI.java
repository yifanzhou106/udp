package cs682;

import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;

import static cs682.Chat.*;

public class UI {

    public ExecutorService threads;
    public ZooKeeperConnector zkc;

    public UI (ExecutorService threads, ZooKeeperConnector zkc)
    {
        this.threads = threads;
        this.zkc = zkc;
        threads.submit(new userInput(zkc)); //Create UI thread
    }

    public class userInput implements Runnable {
        ZooKeeper zk;
        ZooKeeperConnector zkc;

        private userInput(ZooKeeperConnector zkc) {
            this.zkc = zkc;
        }

        @Override
        public void run() {
            while (!isShutdown) {
                boolean ifPrint = false;
                zkc.listZooKeeperMember(ifPrint);

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
                        zkc.listZooKeeperMember(ifPrint);
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
                    ChatProto1.Reply replyMessage = ChatProto1.Reply.getDefaultInstance();
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
                                ChatProto1.Reply replyMessage = ChatProto1.Reply.getDefaultInstance();
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

}
