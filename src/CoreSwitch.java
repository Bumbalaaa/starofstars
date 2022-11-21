import java.util.ArrayList;
import java.util.HashMap;

public class CoreSwitch implements Runnable {
    private boolean isRunning = true;
    private ArrayList<CASLink> unknownSwitches;
    private HashMap<Integer, CASLink> switches;
    private ArrayList<byte[]> frameBuffer;
    private ArrayList<Integer> firewall;

    public CoreSwitch() {
        this.unknownSwitches = new ArrayList<>();
        this.switches = new HashMap<>();
        this.frameBuffer = new ArrayList<>();

        this.firewall = new ArrayList<>();
        //TODO: Read in firewall

        new SwitchAcceptor(this);

    }

    public void run() {
        while (isRunning) {
            byte[] frame;
            if (!frameBuffer.isEmpty()) {
                frame = frameBuffer.remove(0);

                boolean CTS = true;

            }
        }
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public void incomingFrame(byte[] bytes, CASLink armLink) {
        synchronized (switches) {
            if (!switches.containsKey(bytes[1])) {
                switches.put((int) bytes[1], armLink);
            }
        }

        frameBuffer.add(bytes);
    }

    public synchronized void addSwitch(CASLink armSwitch) {
        unknownSwitches.add(armSwitch);
    }
}
