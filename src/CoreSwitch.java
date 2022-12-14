import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

/**
 * <h3>CoreSwitch class of Star of Stars project</h3>
 * The core switch routes traffic between switches and loads/distributes a predefined firewall table.
 *
 * @author Ethan Coulthurst
 * @author Antonio Arant
 */
public class CoreSwitch implements Runnable {
    private boolean isRunning = true;
    private ArrayList<CASLink> unknownSwitches;
    private HashMap<Integer, CASLink> switches;
    private int completedSwitches = 0;
    private ArrayList<byte[]> frameBuffer;
    private ArrayList<Integer> blockedNodes;
    private ArrayList<Integer> blockedSwitches;
    private byte[] firewallPacket;
    private SwitchAcceptor acceptor;

    /**
     * Creates a new core switch and loads firewall table. Creates a SwitchAcceptor object to listen for new arm switch connections
     */
    public CoreSwitch() {
        this.unknownSwitches = new ArrayList<>();
        this.switches = new HashMap<>();
        this.frameBuffer = new ArrayList<>();

        this.blockedSwitches = new ArrayList<>();
        this.blockedNodes = new ArrayList<>();
        loadFirewall();

        this.acceptor = new SwitchAcceptor(this);
        new Thread(this).start(); //Reminder: for the love of god, stop forgetting to include this line
    }

    /**
     * Switching loop
     */
    public void run() {
        while (isRunning) {
            synchronized (frameBuffer) {
                if (!frameBuffer.isEmpty()) {
                    byte[] frame = frameBuffer.remove(0);

                    if (frame != null) {
                        System.out.println("Core received frame: \"" + getData(frame) + "\"");
                        int dest = frame[0] >> 4;
                        //If ack type is end signal, check if all other switches have sent end signal, if so, flood end signal back to all nodes
                        if (frame[4] == 123) {
                            completedSwitches++;
                            if (completedSwitches >= unknownSwitches.size() + switches.size()) {
                                System.out.println("Core sending end signal");
                                flood(frame);

                                delay(200);
                                this.isRunning = false;
                                this.acceptor.closeServer();
                            }
                        } else if (frame[4] == 2) {
                            if (switches.containsKey(dest)) {
                                switches.get(dest).write(frame);
                            } else {
                                System.out.println("Line 69 Error: Source switch unknown, can't send firewalled ack. Something is wrong");
                            }
                        } else {
                            //firewall check
                            boolean firewalled = false;
                            for (int blockedSwitch : blockedSwitches) {
                                if (dest == blockedSwitch) firewalled = true;
                            }

                            if (firewalled) {
                                frame[0] = frame[1]; // Makes destination the source.
                                frame[3] = 0b00000000; // sets size byte to zero to show it's an ack
                                frame[4] = 0b00000010; // sets ack type to firewalled

                                synchronized (switches) {
                                    if (switches.containsKey(dest)) {
                                        System.out.println("Global frame firewalled, sending back");
                                        switches.get(dest).write(frame);
                                    } else {
                                        System.out.println("Error: Arm switch source unknown. How did this even happen");
                                    }
                                }
                            } else {
                                synchronized (switches) {
                                    if (switches.containsKey(dest)){
                                        System.out.println("Core sending frame to arm");

                                        if (switches.get(dest) != null) {
                                            switches.get(dest).write(frame);
                                        } else {
                                            System.out.println("CAS Instance returned null");
                                        }
                                    } else {
                                        frame[4] = 0b00000100; // sets ack type to no return needed
                                        System.out.println("Core flooding frame");
                                        flood(frame);

                                        //Send ack back to src
                                        frame[4] = 0b00000011;
                                        frame[0] = frame[1];
                                        if (switches.containsKey(frame[0] >> 4)) {
                                            System.out.println("Core sending ack back from flooded frame to switch " + (frame[0] >> 4));
                                            switches.get(frame[0] >> 4).write(frame);
                                        } else {
                                            System.out.println("Line 112 Error: Source switch unknown, can't send  firewalled ack. Something is wrong");
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
                System.out.println("Core registered new switch, ID: " + (bytes[1] >> 4));
                switches.put(bytes[1] >> 4, armLink);
                unknownSwitches.remove(armLink);
            }
        }

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
        delay(500);
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
                if (newRule[1].equalsIgnoreCase(" local")) {
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

    /**
     * Floods given frame to all known and unknown switches
     * @param frame Formatted data frame
     */
    private void flood(byte[] frame){
        for (CASLink armSwitch : unknownSwitches) {
            armSwitch.write(frame);
        }

        for (CASLink armSwitch : switches.values()) {
            armSwitch.write(frame);
        }
    }

    /**
     * Halts thread for specified amount of time in millis
     * @param millis Amount of delay time in milliseconds
     */
    private static void delay(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets data section of frame for debugging purposes
     * @param frame Formatted data frame
     * @return Message component of frame as a string
     */
    private static String getData(byte[] frame) {
        String data = "";
        for (int i = 5; i < 5 + frame[3]; i++) {
            data += (char) frame[i];
        }
        return data;
    }
}
