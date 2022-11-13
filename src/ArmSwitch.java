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
    private ArrayList<byte[]> localBuffer;
    private ArrayList<byte[]> globalBuffer;

    private ArrayList<Integer> fireWall;
    private Socket ccsLink;
    private CCSLink link;
    private final int switchID;

    public ArmSwitch(int switchID) {
        this.unknownClients = new ArrayList<>();
        this.clients = new HashMap<>();
        this.localBuffer = new ArrayList<>();
        this.switchID = switchID;
        try {
            this.ccsLink = new Socket("localhost", 5000);
        } catch (IOException e) {
            System.out.println("Arm Switch " + this.switchID + ": Error connecting to central switch");
            e.printStackTrace();
        }

        new ClientAcceptor(this, this.switchID);
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
                        for (int blockedNode : fireWall) {
                            if (frameByte[0] == blockedNode)
                                CTS = false;
                        }
                        if (!CTS) {
                            frameByte[0] = frameByte[1]; // Makes destination the source.
                            frameByte[3] = 0b00000000; // sets size byte to zero to show it's an ack
                            frameByte[4] = 0b00000010; // sets ack type to firewalled
                            synchronized (clients) {
                                if (clients.containsKey(frameByte[0])) {
                                    clients.get(frameByte[0]).write(frameByte);
                                }
                            }
                        } else {
                            synchronized (clients) {
                                if (clients.containsKey(frameByte[0])) {
                                    clients.get(frameByte[0]).write(frameByte);
                                } else {
                                    link.write(frameByte);
                                }
                            }
                        }
                    }
                }
            }

            synchronized (globalBuffer){
                if(!globalBuffer.isEmpty()){
                    frameByte = globalBuffer.remove(0);
                    link.write(frameByte);
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
        //TODO Process incoming global frames (could check firewall at this step)
        globalBuffer.add(bytes);
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public synchronized void addClient(ClientLink client) {
        unknownClients.add(client);
    }
}
