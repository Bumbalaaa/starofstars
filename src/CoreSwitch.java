import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class CoreSwitch implements Runnable {
    private boolean isRunning = true;
    private ArrayList<CASLink> unknownSwitches;
    private HashMap<Integer, CASLink> switches;
    private ArrayList<byte[]> frameBuffer;
    private ArrayList<Integer> blockedNodes;
    private ArrayList<Integer> blockedSwitches;

    public CoreSwitch() {
        this.unknownSwitches = new ArrayList<>();
        this.switches = new HashMap<>();
        this.frameBuffer = new ArrayList<>();

        this.blockedSwitches = new ArrayList<>();
        this.blockedNodes = new ArrayList<>();
        loadFirewall();

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

    private void loadFirewall() {
        try {
            Scanner fileReader = new Scanner(new File("firewall.txt"));
            while (fileReader.hasNextLine()) {
                String[] newRule = fileReader.nextLine().split(":");
                //Check for firewall rule: local -- no other firewall rules i guess but checking anyway
                if (newRule[1].equalsIgnoreCase("local")) {
                    //Check if firewall rule is for switch or specific node. If switch, add the number before the _ to the list
                    //Else, add both numbers compressed into 1 byte to the list
                    if (newRule[0].charAt(2) == '#') blockedSwitches.add(Integer.parseInt(newRule[0].split("_")[0]));
                    else {
                        String[] newBlockedNode = newRule[0].split("_");
                        blockedNodes.add((Integer.parseInt(newBlockedNode[0]) << 4) | Integer.parseInt(newBlockedNode[1]));
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Firewall read error.");
        }
    }
}
