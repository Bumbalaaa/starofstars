import java.io.IOException;

public class NodeListener implements Runnable {
    private final Node node;
    public enum ListenerType {RECEIVER, TRANSMITTER}
    private final ListenerType type;

    public NodeListener(ListenerType type, Node node) {
        this.node = node;
        this.type = type;
    }

    public void run() {
        try {
            if (this.type == ListenerType.RECEIVER) this.node.receive();
            else this.node.transmit();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
