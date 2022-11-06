import java.io.IOException;

/**
 * NodeListener class of Star of Stars project
 * NodeListener is a helper class instantiated by a Node's constructor that
 * creates 2 threads to run that Node's transmit and receive methods
 *
 * @author Ethan Coulthurst
 * @author Antonio Arant
 */
public class NodeListener implements Runnable {
    private final Node node;
    public enum ListenerType {RECEIVER, TRANSMITTER}
    private final ListenerType type;

    /**
     * Create a new listener bound to a specific node.
     * The type field determines if this thread will run
     * the node's transmit or receive method.
     * @param type Enum: RECEIVER or TRANSMITTER
     * @param node Reference to this listener's Node object
     */
    public NodeListener(ListenerType type, Node node) {
        this.node = node;
        this.type = type;
    }

    /**
     * Calls Node's receive or transmit method specified on listener creation
     */
    public void run() {
        try {
            if (this.type == ListenerType.RECEIVER) this.node.receive();
            else this.node.transmit();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
