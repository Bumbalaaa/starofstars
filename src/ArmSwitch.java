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
//            System.out.println("Arm Switch " + this.switchID + ": Connecting to central switch");
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
            byte[] frame = null;
            synchronized (localBuffer) {
                if (!localBuffer.isEmpty()) {
                    frame = localBuffer.remove(0);

                    boolean CTS = true;
                    if (frame != null) {
                        String data = getData(frame);

                        //check if ack type denotes firewall table, if so load, otherwise process normally
                        if (frame[4] == 5) {
                            for (int i = 5; i < 5 + frame[3]; i++) {
                                this.firewall.add((int) frame[i]);
                            }
                        //check if ack type denotes end signal, if so forward to core switch
                        } else if (frame[4] == 123) {
                            if (++completedClients >= unknownClients.size() + clients.size()) {
                                System.out.println("ARM " + this.switchID + " SENDING END TO CORE --------------------------------------------");
                                link.write(frame);
                            }
                        } else {
                            for (int blockedNode : firewall) {
                                if (frame[0] == blockedNode)
                                    CTS = false;
                            }
                            if (!CTS) {
                                frame[0] = frame[1]; // Makes destination the source.
                                frame[3] = 0b00000000; // sets size byte to zero to show it's an ack
                                frame[4] = 0b00000010; // sets ack type to firewalled
                                synchronized (clients) {
                                    if (clients.containsKey(frame[0])) {
                                        System.out.println("Message blocked by firewall");
                                        clients.get(frame[0]).write(frame);
                                    }
                                }
                            } else {
                                synchronized (clients) {
                                    //if dest is known send there, else global/flood
                                    if (clients.containsKey(frame[0])) {
                                        System.out.println("Sending local frame to node");
                                        clients.get(frame[0]).write(frame);
                                    } else {
                                        //if dest cas isnt this cas, send to global, else mark ack as flood, then send to global and flood
                                        //shouldn't interfere with ack messages since ack messages should never be targeting unknown nodes
                                        if (!(frame[0] >> 4 == this.switchID)) {
                                            System.out.println("Switch " + this.switchID + " sending local frame to global: \"" + data + "\"");
                                            link.write(frame);
                                        } else {
                                            frame[4] = 0b00000100;
                                            System.out.println("Switch " + this.switchID + " flooding local frame");
                                            flood(frame);

                                            frame[4] = 0b00000011;
                                            frame[0] = frame[1];
                                            int srcNode = frame[0] & 0b00001111;
                                            System.out.println("Switch " + this.switchID + " sending ack from flooded frame to node " + srcNode);
                                            if (clients.containsKey(srcNode)) {
                                                clients.get(srcNode).write(frame);
                                            }
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
                    frame = globalBuffer.remove(0);
                    if (frame != null) {
                        int ackType = frame[4];
                        int destNode = frame[0] & 0b00001111;
                        int destSwitch = frame[0] >> 4;

                        if (ackType == 5) { //check if ack type denotes firewall table, if so load, otherwise process normally
                            for (int i = 5; i < 5 + frame[3]; i++) {
                                this.firewall.add((int) frame[i]);
                            }
                        } else if (ackType == 4) { //check if global is flooding
                            System.out.println("Switch " + this.switchID + " flooding global frame");
                            this.flood(frame);
                        } else if (ackType == 123) { //Final end flag received from global. Forward to nodes then shut down
                            for (ClientLink client : clients.values()) {
                                client.write(frame);
                            }
                            for (ClientLink client : unknownClients) { //This shouldn't ever execute, can't get an end flag from an unknown client
                                client.write(frame);
                            }

                            delay(200);
                            this.isRunning = false;
                            this.acceptor.closeServer();
                        } else if (ackType == 2) {
                            if (clients.containsKey(destNode)) {
                                clients.get(destNode).write(frame);
                            } else {
                                System.out.println("CAS " + this.switchID + ": Source node unknown, can't send ack. Something is wrong");
                            }
                        } else {
                            boolean fireWalled = false;
                            for (int blockedNode : firewall) { //Check if node is in firewall
                                if (destNode == blockedNode) {
                                    fireWalled = true;
                                    break;
                                }
                            }
                            if (fireWalled) {
                                frame[0] = frame[1]; // Makes destination the source.
                                frame[3] = 0b00000000; // sets size byte to zero to show it's an ack
                                frame[4] = 0b00000010; // sets ack type to firewalled

                                link.write(frame); //send back up to core arm switch for redistribution
                            } else {
                                synchronized (clients) {
                                    //if dest is known send there, else global/flood
                                    if (clients.containsKey(destNode)) {
                                        clients.get(destNode).write(frame);
                                    } else {
                                        //if dest switch is not this switch, send to global. Else, flood and send back ack.
                                        if (this.switchID != destSwitch) link.write(frame);
                                        else {
                                            frame[4] = 0b00000100;
                                            flood(frame);

                                            frame[4] = 0b00000011;

                                            frame[0] = frame[1];
                                            link.write(frame);
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
            int srcNode = bytes[1] & 0b00001111;
            if (!clients.containsKey(srcNode)) {
                System.out.println("Switch " + this.switchID + " registered new node, ID: " + srcNode);
                clients.put(srcNode, client);
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

    private void flood(byte[] frame){
        for (ClientLink client : clients.values()) {
            client.write(frame);
        }
        for (ClientLink client : unknownClients) {
            client.write(frame);
        }
    }

    private static void delay(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String getData(byte[] frame) {
        String data = "";
        for (int i = 5; i < 5 + frame[3]; i++) {
            data += (char) frame[i];
        }
        return data;
    }
}
