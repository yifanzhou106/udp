package cs682;

import cs682.UdpHistory.HistoryManager;
import cs682.UdpHistory.HistoryReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;

import static cs682.Chat.*;

/**
 * Get into UI threads
 * User will input choices
 * Switch function will handle with user input
 * If user want to send message, it will create a socket to send messages
 * Last version:
 * Update request into switch
 */
public class UI implements Runnable {

    public ExecutorService threads;
    public ZooKeeperConnector zkc;
    private List<String> userData;
    private HistoryReceiver hr;
    private HistoryManager hm;

    public UI(ExecutorService threads, ZooKeeperConnector zkc, HistoryReceiver hr, HistoryManager hm) {
        this.threads = threads;
        this.zkc = zkc;
        this.hr = hr;
        this.hm = hm;
        userData = new ArrayList();
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
                    hm.printList();
                    rwl.readLock().unlock();
                    break;

                case "request":
                    System.out.println("Request history data from? ");
                    String name = reader.nextLine();
                    if (userMap.containsKey(name)) {
                        userData = userMap.get(name);
                        String sip = userData.get(0);
                        String udpport = userData.get(2);
                        if (udpport == null)
                            System.out.println("This person do not support UDP\n");
                        hr.sendRequest(sip, udpport);
                    } else {
                        System.out.println("This person not in the list\n");
                    }
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




