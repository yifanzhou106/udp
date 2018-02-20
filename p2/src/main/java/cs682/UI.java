package cs682;

import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;

import static cs682.Chat.*;

public class UI implements Runnable {

    public ExecutorService threads;
    public ZooKeeperConnector zkc;
    private List<String> userData = new ArrayList();
    private HistoryReceiver hr;
    private HistoryManager hm;

    public UI(ExecutorService threads, ZooKeeperConnector zkc, HistoryReceiver hr, HistoryManager hm) {
        this.threads = threads;
        this.zkc = zkc;
        this.hr = hr;
        this.hm = hm;
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
                    System.out.println("\n4. history");
                    System.out.println("\n5. request\n");
                    System.out.println("\n**************************");
                    break;

                case "send":
                    if (!splitedUserChoice[1].isEmpty()) {
                        String name = splitedUserChoice[1];
                        System.out.println("\n\nEnter your message: ");
                        String message = reader.nextLine();
                        threads.submit(new SendMessage(name, message));
                    } else {
                        System.out.println("Wrong data format");
                    }
                    break;

                case "bcast":
                    boolean isBcast = true;
                    System.out.println("Enter your message: ");
                    String message = reader.nextLine();
                    threads.submit(new SendMessage(message, isBcast));
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
//                            for (Map.Entry<String, String> map : bcastHistoryMap.entrySet()) {
//                                System.out.println(map.getValue() + "  Date: " + map.getKey());
//                            }
                    hm.printList();
                        rwl.readLock().unlock();
                    break;

                case "request":
                    System.out.println("Request history data from? ");
                    String name = reader.nextLine();
                    userData = userMap.get(name);
                    String sip = userData.get(0);
                    String udpport = userData.get(2);
                    hr.sendRequest(sip, udpport);
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




