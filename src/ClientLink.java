import java.io.*;
import java.net.Socket;

/**
 * <h3>ClientLink class of Star of Stars project</h3>
 * ClientLink objects are threads storing the socket connection to their respective node.
 * Each node has a ClientLink object created by its arm switch that listens for packets
 * and sends them to the switch's frame buffer, and is used by the switch to send
 * packets back to its node.
 *
 * @author Ethan Coulthurst
 * @author Antonio Arant
 * @version 1
 */
public class ClientLink implements Runnable {
    private Socket clientSocket;
    private ArmSwitch armSwitch;
    private DataInputStream in;
    private DataOutputStream out;

    /**
     * Creates a new client link to attach to a node socket
     * @param clientSocket Linked client socket
     * @param armSwitch Reference to switch
     */
    public ClientLink(Socket clientSocket, ArmSwitch armSwitch) {
        this.clientSocket = clientSocket;
        this.armSwitch = armSwitch;
        new Thread(this).start();
    }

    /**
     * Message listener loop
     */
    public void run() {
        try {
            in = new DataInputStream(clientSocket.getInputStream());
            out = new DataOutputStream(clientSocket.getOutputStream());
            while (armSwitch.isRunning()) {
                byte[] buffer = new byte[1024];
                if (in.available() > 0) {
                    in.read(buffer);
                    armSwitch.incomingLocal(buffer, this);
                }
            }

            this.clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes given frame to socket's output stream
     * @param frame Frame to send
     */
    public void write(byte[] frame) {
        try {
            this.out.write(frame);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
