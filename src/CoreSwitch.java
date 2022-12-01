import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class CoreSwitch implements Runnable {
    private boolean isRunning = true;
    private ArrayList<CASLink> unknownSwitches;
    private HashMap<Integer, CASLink> switches;
    private int completedSwitches = 0;
    private ArrayList<byte[]> frameBuffer;
    private ArrayList<Integer> blockedNodes;
    private ArrayList<Integer> blockedSwitches;
    private byte[] firewallPacket;

    /**
     * Creates a new core switch and loads firewall table
     */
    public CoreSwitch() {
        this.unknownSwitches = new ArrayList<>();
        this.switches = new HashMap<>();
        this.frameBuffer = new ArrayList<>();

        this.blockedSwitches = new ArrayList<>();
        this.blockedNodes = new ArrayList<>();
        loadFirewall();

        new SwitchAcceptor(this);
        new Thread(this).start(); //Reminder: for the love of god, stop forgetting to include this line
    }

    public void run() {
        while (isRunning) {
            byte[] frame;
            synchronized (frameBuffer) {
                if (!frameBuffer.isEmpty()) {
                    System.out.println("Core received frame");
                    frame = frameBuffer.remove(0);

                    boolean allowFrame = true;
                    if (frame != null) {
                        //If ack type is end signal, check if all other switches have sent end signal, if so, flood end signal back to all nodes
                        if (frame[4] == 255) {
                            if (++completedSwitches >= unknownSwitches.size() + switches.size()) {
                                for (CASLink armSwitch : unknownSwitches) {
                                    armSwitch.write(frame);
                                }

                                for (CASLink armSwitch : switches.values()) {
                                    armSwitch.write(frame);
                                }
                            }
                        } else {
                            //firewall check
                            for (int blockedSwitch : blockedSwitches) {
                                if (frame[0] >> 4 == blockedSwitch) allowFrame = false;
                            }

                            if (!allowFrame) {
                                frame[0] = frame[1]; // Makes destination the source.
                                frame[3] = 0b00000000; // sets size byte to zero to show it's an ack
                                frame[4] = 0b00000010; // sets ack type to firewalled

                                synchronized (switches) {
                                    if (switches.containsKey(frame[0])) {
                                        System.out.println("Global frame firewalled, sending back");
                                        switches.get(frame[0]).write(frame);
                                    } else {
                                        System.out.println("Error: Arm switch source unknown. How did this even happen");
                                    }
                                }
                            } else {
                                synchronized (switches) {
                                    if (switches.containsKey(frame[0] >> 4)) {
                                        System.out.println("Core sending frame to arm");
                                        Integer dest = (int) frame[0] >> 4;
                                        if (switches.get(dest) != null) {
                                            switches.get(dest).write(frame);
                                        } else {
                                            System.out.println("CAS Instance returned null");
                                        }
                                        //TODO - Figure out why this could be null, especially since the above if statement checks if the object exists - debugger even shows it as populated
                                    } else {
                                        frame[4] = 0b00000100; // sets ack type to no return needed
                                        System.out.println("Core flooding frame");
                                        for (CASLink armSwitch : unknownSwitches) {
                                            armSwitch.write(frame);
                                        }

                                        for (CASLink armSwitch : switches.values()) {
                                            armSwitch.write(frame);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Get running state of core switch
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return this.isRunning;
    }

    /**
     * Reads in an incoming frame from an arm switch. Called by CASLink instances
     * @param bytes packet
     * @param armLink source arm switch
     */
    public void incomingFrame(byte[] bytes, CASLink armLink) {
        synchronized (switches) {
            if (!switches.containsKey(bytes[1] >> 4)) {
                System.out.println("Core found new switch, adding to list");
                Integer src = (int) bytes[1];
                switches.put(src, armLink);
            }
        }
        System.out.println("Core adding frame to buffer");
        synchronized (frameBuffer) {
            frameBuffer.add(bytes);
        }
    }

    /**
     * Adds a new arm switch connection to list of unknown clients and sends firewall table to switch
     * @param armSwitch Instance of CASLink connected to respective arm switch
     */
    public synchronized void addSwitch(CASLink armSwitch) {
        unknownSwitches.add(armSwitch);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        armSwitch.write(firewallPacket);
    }

    /**
     * Reads in "firewall.txt" and loads firewall rules into 2 arrays: blocked switches and blocked nodes
     */
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

            //Sets up frame for list of blocked nodes. The blocked switch list only has to be processed by the core switch, so it wont be sent out.
            //Sent to all new arm switches on connection
            firewallPacket = new byte[5 + blockedNodes.size()];
            firewallPacket[0] = 0;
            firewallPacket[1] = 0;
            firewallPacket[3] = (byte) (firewallPacket.length - 5);
            firewallPacket[4] = 5;
            int i = 5;
            for (Integer node : blockedNodes) {
                firewallPacket[i] = node.byteValue();
            }
            byte crc = 0;
            for (byte data : firewallPacket) {
                crc += data;
            }
            firewallPacket[2] = crc;

        } catch (IOException e) {
            System.out.println("Firewall read error.");
        }
    }
}
