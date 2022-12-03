import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * <h3>ClientAcceptor class of Star of Stars project</h3>
 * ClientAcceptor objects are created by Arm Switches that listen for incoming
 * client connections and adds them to that Arm Switch.
 *
 * @author Ethan Coulthurst
 * @author Antonio Arant
 * @version 1
 */
public class ClientAcceptor implements Runnable {
    private ArmSwitch armSwitch;
    private final int port;
    private ServerSocket serverSocket;

    /**
     * Creates a new ClientAcceptor thread
     * @param armSwitch Reference to arm switch
     */
    public ClientAcceptor(ArmSwitch armSwitch, int switchID) {
        this.armSwitch = armSwitch;
        this.port = 1000 + switchID;
        new Thread(this).start();
    }

    /**
     * Loops waiting for incoming connections
     */
    public void run() {
        try {
            this.serverSocket = new ServerSocket(port);
//            System.out.println("Cas " + (port - 1000) + " listening on port " + port);
            while (armSwitch.isRunning()) {
                Socket newClient = serverSocket.accept();
//                System.out.println("Cas " + (port - 1000) + " accepted new client");
                armSwitch.addClient(new ClientLink(newClient, armSwitch));
            }
        } catch (IOException e) {
            System.out.println("Server socket closed.");
        }
    }

    public void closeServer() {
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
