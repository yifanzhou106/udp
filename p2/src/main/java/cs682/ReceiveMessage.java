package cs682;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;

import static cs682.Chat.*;

import cs682.ChatProto1.*;
import cs682.UdpHistory.HistoryManager;

/**
 * Get into receive message threads
 * Will create a socket to receive messages
 */
public class ReceiveMessage implements Runnable {

    private ExecutorService threads;
    private ZooKeeperConnector zkc;
    private HistoryManager hm;

    public ReceiveMessage(ExecutorService threads, ZooKeeperConnector zkc, HistoryManager hm) {
        this.threads = threads;
        this.zkc = zkc;
        this.hm = hm;
    }

    @Override
    public void run() {
        try {
            ServerSocket welcomingSocket = new ServerSocket(Integer.parseInt(PORT));
            while (!isShutdown) {
                Socket connectionSocket = welcomingSocket.accept();
                InputStream instream = connectionSocket.getInputStream();
                ChatProto receiveMessage = ChatProto.getDefaultInstance();
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

