import java.io.*;
import java.net.Socket;

/**
 * <h3>CCSLink class of Star of Stars project</h3>
 * CCSLink functions identically to ClientLink, but connects to the main switch instead of nodes.
 *
 * @see ClientLink
 * @author Ethan Coulthurst
 * @author Antonio Arant
 * @version 1
 */
public class CCSLink implements Runnable {
    private final Socket switchSocket;
    private final ArmSwitch armSwitch;
    private DataInputStream in;
    private DataOutputStream out;

    /**
     * Creates a new link to attach to a core switch socket
     * @param switchSocket Linked core switch socket
     * @param armSwitch Reference to switch
     */
    public CCSLink(Socket switchSocket, ArmSwitch armSwitch) {
        this.switchSocket = switchSocket;
        this.armSwitch = armSwitch;
        new Thread(this).start();
    }

    /**
     * Message listener loop
     */
    public void run() {
        try {
            in = new DataInputStream(switchSocket.getInputStream());
            out = new DataOutputStream(switchSocket.getOutputStream());
            while (armSwitch.isRunning()) {
                byte[] buffer = new byte[255];
                if (in.available() > 0) {
                    in.read(buffer);
                    armSwitch.incomingGlobal(buffer);
                }
            }

            this.switchSocket.close();
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
