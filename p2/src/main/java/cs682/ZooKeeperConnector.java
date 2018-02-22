package cs682;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static cs682.Chat.*;


public class ZooKeeperConnector {
    public ZooKeeper zk;

    public ZooKeeperConnector() {
        zk = connectZooKeeper();
    }

    public ZooKeeper getZk() {
        return zk;
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
    public void joinZooKeeper() {
        try {
            ChatProto1.ZKData data = ChatProto1.ZKData.newBuilder().setIp(InetAddress.getLocalHost().toString().split("/")[1]).setPort(PORT).setUdpport(UDPPORT).build();

            String createdPath = zk.create(group + member,
                    data.toByteArray(),  //probably should be something more interesting here...
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL );//_SEQUENTIAL
            System.out.println("Joined group " + group + member);

        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
        }
        catch (KeeperException e) {
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
    public void listZooKeeperMember( boolean ifPrint) {
        rwl.writeLock().lock();

        try {
            List<String> children = zk.getChildren(group, false);
            for (String child : children) {
                if (ifPrint)
                    System.out.println(child + "\n");

                //get data about each child
                Stat s = new Stat();
                byte[] raw = zk.getData(group + "/" + child, false, s);
                ChatProto1.ZKData data = ChatProto1.ZKData.parseFrom(raw);
                if (data != null) {
                    String ip = data.getIp();
                    String port = data.getPort();
                    String udpport = data.getUdpport();

                    ArrayList<String> userData = new ArrayList();
                    userData.add(ip);
                    userData.add(port);
                    userData.add(udpport);
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


}
