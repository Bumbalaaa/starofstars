import java.io.*;
import java.net.Socket;

/**
 * <h3>CASLink class of Star of Stars project</h3>
 * CASLink functions identically to ClientLink, but connects to Arm Switches instead of nodes.
 *
 * @see ClientLink
 * @author Ethan Coulthurst
 * @author Antonio Arant
 * @version 1
 */
public class CASLink implements Runnable {
    private final Socket switchSocket;
    private final CoreSwitch coreSwitch;
    private DataInputStream in;
    private DataOutputStream out;

    /**
     * Creates a new link to attach to a core switch socket
     * @param switchSocket Linked core switch socket
     * @param coreSwitch Reference to switch
     */
    public CASLink(Socket switchSocket, CoreSwitch coreSwitch) {
        this.switchSocket = switchSocket;
        this.coreSwitch = coreSwitch;
        new Thread(this).start();
    }

    /**
     * Message listener loop
     */
    public void run() {
        try {
            in = new DataInputStream(switchSocket.getInputStream());
            out = new DataOutputStream(switchSocket.getOutputStream());
            while (coreSwitch.isRunning()) {
                byte[] buffer = new byte[255];
                if (in.available() > 0) {
                    in.read(buffer);
                    coreSwitch.incomingFrame(buffer, this);
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
