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
    private final int switchID;

    public ArmSwitch(int switchID) {
        this.unknownClients = new ArrayList<>();
        this.clients = new HashMap<>();
        this.localBuffer = new ArrayList<>();
        this.switchID = switchID;

        new ClientAcceptor(this, this.switchID);
        new Thread(this).start();
    }

    public void run() {

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

    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public synchronized void addClient(ClientLink client) {
        unknownClients.add(client);
    }
}
