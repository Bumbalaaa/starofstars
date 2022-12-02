import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * <h3>ArmSwitch class of Star of Stars project</h3>
 * The ArmSwitch class routes local node traffic and forwards all other traffic to the core switch.
 * It contains a copy of the core switch firewall to block/forward certain traffic.
 *
 * @author Ethan Coulthurst
 * @author Antonio Arant
 * @version 1
 */
public class ArmSwitch implements Runnable {
    private boolean isRunning = true;
    private ArrayList<ClientLink> unknownClients;
    private HashMap<Integer, ClientLink> clients;
    private int completedClients = 0;
    private ArrayList<byte[]> localBuffer;
    private ArrayList<byte[]> globalBuffer;

    private ArrayList<Integer> firewall;
    private Socket ccsLink;
    private CCSLink link;
    private final int switchID;
    private ClientAcceptor acceptor;

    public ArmSwitch(int switchID) {
        this.unknownClients = new ArrayList<>();
        this.clients = new HashMap<>();
        this.localBuffer = new ArrayList<>();
        this.globalBuffer = new ArrayList<>();
        this.firewall = new ArrayList<>();
        this.switchID = switchID;
        try {
            this.ccsLink = new Socket("localhost", 5000);
            System.out.println("Arm Switch " + this.switchID + ": Connecting to central switch");
        } catch (IOException e) {
            System.out.println("Arm Switch " + this.switchID + ": Error connecting to central switch");
            e.printStackTrace();
        }

        this.acceptor = new ClientAcceptor(this, this.switchID);
        link = new CCSLink(this.ccsLink, this);
        new Thread(this).start();
    }

    public void run() {
        while(isRunning){
            byte[] frameByte = null;
            synchronized (localBuffer) {
                if (!localBuffer.isEmpty()) {
                    frameByte = localBuffer.remove(0);

                    boolean CTS = true;
                    if (frameByte != null) {
                        //check if ack type denotes firewall table, if so load, otherwise process normally
                        if (frameByte[4] == 5) {
                            for (int i = 5; i < 5 + frameByte[3]; i++) {
                                this.firewall.add((int) frameByte[i]);
                            }
                        //check if ack type denotes end signal, if so forward to core switch
                        } else if (frameByte[4] == 123) {
                            if (++completedClients >= unknownClients.size() + clients.size()) {
                                System.out.println("ARM " + this.switchID + " SENDING END TO CORE --------------------------------------------");
                                link.write(frameByte);
                            }
                        } else {
                            for (int blockedNode : firewall) {
                                if (frameByte[0] == blockedNode)
                                    CTS = false;
                            }
                            if (!CTS) {
                                frameByte[0] = frameByte[1]; // Makes destination the source.
                                frameByte[3] = 0b00000000; // sets size byte to zero to show it's an ack
                                frameByte[4] = 0b00000010; // sets ack type to firewalled
                                synchronized (clients) {
                                    if (clients.containsKey(frameByte[0])) {
                                        System.out.println("Message blocked by firewall");
                                        clients.get(frameByte[0]).write(frameByte);
                                    }
                                }
                            } else {
                                synchronized (clients) {
                                    //if dest is known send there, else global/flood
                                    if (clients.containsKey(frameByte[0])) {
                                        System.out.println("Sending frame");
                                        clients.get(frameByte[0]).write(frameByte);
                                    } else {
                                        //if dest cas isnt this cas, send to global, else mark ack as flood, then send to global and flood
                                        //shouldn't interfere with ack messages since ack messages should never be targeting unknown nodes
                                        if (!(frameByte[0] >> 4 == this.switchID)) {
                                            System.out.println("Sending frame to global");
                                            link.write(frameByte);
                                        } else {
                                            frameByte[4] = 0b00000100;
                                            System.out.println("Arm Flooding frame");
                                            this.flood(frameByte);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            synchronized (globalBuffer){
                if(!globalBuffer.isEmpty()){
                    frameByte = globalBuffer.remove(0);

                    boolean allowFrame = true;
                    if (frameByte != null) {
                        //check if ack type denotes firewall table, if so load, otherwise process normally
                        if (frameByte[4] == 5) {
                            for (int i = 5; i < 5 + frameByte[3]; i++) {
                                this.firewall.add((int) frameByte[i]);
                            }
                        } else {
                            if (frameByte[4] == 123) {
                                for (ClientLink client : clients.values()) {
                                    client.write(frameByte);
                                }
                                for (ClientLink client : unknownClients) {
                                    client.write(frameByte);
                                }

                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                this.isRunning = false;
                                this.acceptor.closeServer();
                            } else {
                                for (int blockedNode : firewall) {
                                    if (frameByte[0] == blockedNode)
                                        allowFrame = false;
                                }
                                if (!allowFrame) {
                                    frameByte[0] = frameByte[1]; // Makes destination the source.
                                    frameByte[3] = 0b00000000; // sets size byte to zero to show it's an ack
                                    frameByte[4] = 0b00000010; // sets ack type to firewalled
//                                synchronized (clients) {
//                                    if (clients.containsKey(frameByte[0])) {
//                                        clients.get(frameByte[0]).write(frameByte);
//                                    }
//                                }

                                    //send back up to core arm switch for redistribution
                                    link.write(frameByte);
                                } else {
                                    synchronized (clients) {
                                        //if dest is known send there, else global/flood
                                        if (clients.containsKey(frameByte[0])) {
                                            clients.get(frameByte[0]).write(frameByte);
                                        } else {
                                            //if no unknown clients, send to global, else mark ack as flood, then send to global and flood
                                            //shouldn't interfere with ack messages since ack messages should never be targeting unknown nodes
                                            if (this.switchID != frameByte[1] >> 4) link.write(frameByte);
                                            else {
                                                frameByte[4] = 0b00000100;
                                                flood(frameByte);
//                                                for (ClientLink client : clients.values()) {
//                                                    client.write(frameByte);
//                                                }
//                                                for (ClientLink client : unknownClients) {
//                                                    client.write(frameByte);
//                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void incomingLocal(byte[] bytes, ClientLink client) {
        synchronized (clients) {
            if (!clients.containsKey(bytes[1])) {
                clients.put((int) bytes[1], client);
                unknownClients.remove(client);
            }
        }

        localBuffer.add(bytes);
    }

    public void incomingGlobal(byte[] bytes) {
        globalBuffer.add(bytes);
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public synchronized void addClient(ClientLink client) {
        unknownClients.add(client);
    }
    public void flood(byte[] frameByte){
        for (ClientLink client : clients.values()) {
            client.write(frameByte);
        }
        for (ClientLink client : unknownClients) {
            client.write(frameByte);
        }

        frameByte[4] = 0b00000011;

        frameByte[0] = frameByte[1];
        frameByte[1] = 0b00000000;
        Integer destination = (int) frameByte[0];
        if (clients.containsKey(destination)){
            System.out.println("SENDING ACK");
            clients.get(destination).write(frameByte);
        }

//        //System.out.println("Source " +frameByte[1]);
//        int dest1 = frameByte[1] & 0b11110000;
//        //System.out.println("dest1" + dest1);
//        int dest2 = frameByte[1] & 0b00001111;
//        //System.out.println("dest2" + dest2);
//        String s1 = String.valueOf(dest1);
//        String s2 = String.valueOf(dest2);
//        String dest = s1 + "_" + s2;
//
//        Frame ackFrame = new Frame(switchID, 0, 3, dest+ ":ACK");
////        System.out.println("ACKFRAME SIZE: " + ackFrame.getSize());
//        byte[] ackBytes = Frame.encode(ackFrame);
//        //System.out.println((int) ackBytes[0] + " ACK DEST");
//        Integer destination = (int) ackBytes[0];
//        if(clients.containsKey(destination)){
////            System.out.println("MEMBER Present " + destination);
//            clients.get(destination).write(frameByte);
////            System.out.println("SENT ACK");
//        }
//        else{
//            System.out.println("MEMBER not Present " + ackBytes[0]);
//            //link.write(ackBytes);
//        }

    }
}
