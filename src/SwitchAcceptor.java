import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * <h3>SwitchAcceptor class of Star of Stars project</h3>
 * A SwitchAcceptor object is created by the core switch that listens for incoming
 * arm switch connections and adds them to the core switch.
 *
 * @author Ethan Coulthurst
 * @author Antonio Arant
 * @version 1
 */
public class SwitchAcceptor implements Runnable {
    private CoreSwitch coreSwitch;
    private final int port = 5000;
    private ServerSocket serverSocket;

    /**
     * Creates a new SwitchAcceptor thread
     * @param coreSwitch Reference to arm switch
     */
    public SwitchAcceptor(CoreSwitch coreSwitch) {
        this.coreSwitch = coreSwitch;
        new Thread(this).start();
    }

    /**
     * Loops waiting for incoming connections
     */
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            while (coreSwitch.isRunning()) {
                Socket newSwitch = serverSocket.accept();
                System.out.println("Core switch accepted new client");
                coreSwitch.addSwitch(new CASLink(newSwitch, coreSwitch));
            }
        } catch (IOException e) {
            System.out.println("Core switch server socket closed.");
        }
    }

    /**
     * Closes server socket and interrupts accept() method
     */
    public void closeServer() {
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}