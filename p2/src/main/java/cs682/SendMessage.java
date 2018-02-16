package cs682;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Map;

import static cs682.Chat.member;
import static cs682.Chat.user;
import static cs682.Chat.userMap;

public class SendMessage implements Runnable {
    private Socket connectionSocket = new Socket();
    private String name = null;
    private String message = null;
    private boolean isBcast = false;
    private ArrayList<String> userData = new ArrayList();
    private String sip;
    private String sport;
    private int timeout = 1000;

    public SendMessage(String name, String message) {
        this.message = message;
        this.name = name;
    }

    public SendMessage(String message, boolean isBcast) {
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
