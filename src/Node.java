import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * <h3>Node class of Star of Stars project</h3>
 * Nodes transmit data from their input text file and output received data to their output text file.
 *
 * @author Ethan Coulthurst
 * @author Antonio Arant
 * @version 1
 */
public class Node {
    private final int casID;
    private final int nodeID;
    private final String fullSrcID;
    private Socket socket;
    private int outgoingACK = 0;
    private DataOutputStream out;
    private DataInputStream in;
    private int readerWaitFlag;
    private final int TIMEOUT_DELAY = 100000;

    /**
     * Creates a node with given AS and Node IDs and connects it to the network.
     * Also creates 2 threads with NodeListener object to call receive and transmit methods.
     * @param casID Arm Switch ID
     * @param nodeID Node ID
     */
    public Node(int casID, int nodeID) {
        this.casID = casID;
        this.nodeID = nodeID;
        fullSrcID = casID + "_" + nodeID;

        try {
            Thread.sleep(100);
            this.socket = new Socket("localhost", 1000 + this.casID);
            System.out.println("Node " + this.casID + "_" + this.nodeID + ": Connecting to port " + (1000 + this.casID));
        } catch (IOException | InterruptedException e) {
            System.out.println("Node " + this.casID + "_" + this.nodeID + ": Connection refused.");
            e.printStackTrace();
            return;
        }

        new Thread(new NodeListener(NodeListener.ListenerType.RECEIVER, this)).start();
        new Thread(new NodeListener(NodeListener.ListenerType.TRANSMITTER, this)).start();
    }

    /**
     * Listens for messages received by socket. Called automatically on separate thread.
     * @throws IOException if there is a stream read or file write error.
     */
    public void receive() throws IOException {
        in = new DataInputStream(socket.getInputStream());
        String outputFilePath = "node" + this.casID + "_" + this.nodeID + "output.txt";
        new File(outputFilePath).createNewFile();
        FileWriter writer = new FileWriter(outputFilePath);

        boolean listening = true;
        while (listening) {
            if (in.available() > 0) {
                byte[] buffer = new byte[1024];
                in.read(buffer);
                if (buffer[4] == 123) {
                    System.out.println("End signal received");
                }
                Frame frame = Frame.decode(buffer);

                //If CRC is wrong, ask for retransmission
                if (!frame.isCrcVerified()) {
                    Frame ackFrame = new Frame(this.casID, this.nodeID, 1, this.fullSrcID + ":");
                    byte[] bytes = Frame.encode(ackFrame);
                    out.write(bytes);
                    continue;
                }

                //Check if frame is an ACK response (or ACK type value that we've hijacked)
                if (!(frame.getAck() == 0b00000111 || frame.getAck() == 0b00000100)) {
                    switch (frame.getAck()) {
                        case 1:
                            setReaderWaitFlag(2);
                            break;
                        case 2:
                        case 3:
                        case 4:
                            setReaderWaitFlag(1);
                            break;
                        case 123:
                            listening = false;
                            System.out.println("Node " + this.casID + "_" + this.nodeID + ": End flag received");
                            break;
                        default:
                            System.out.println("Node " + this.casID + "_" + this.nodeID + ": Invalid ACK response received: " + frame.getAck());
                    }
                } else {
                    //Otherwise, normal frame, write to file and send positive ACK
                    System.out.println("Node " + this.casID + "_" + this.nodeID + ": Writing to file: " + frame.getData());
                    writer.write(frame.getData() + "\n");

                    //If frame hasn't been flooded, send ack back
                    if (frame.getAck() != 4) {
                        int dest1 = buffer[1] & 0b11110000;
                        int dest2 = buffer[1] & 0b00001111;

                        String s1 = String.valueOf(dest1);
                        String s2 = String.valueOf(dest2);
                        String dest = s1 + "_" + s2;

                        Frame ackFrame = new Frame(this.casID, this.nodeID, 3, dest + ":ACK");
                        byte[] ackBytes = Frame.encode(ackFrame);
                        System.out.println("Sending ACK");
                        out.write(ackBytes);
                    }
                }
            }
        } //Loop - Listen for messages
        System.out.println("Node " + this.casID + "_" + this.nodeID + ": Program finished");
        writer.close();
    }

    /**
     * Transmits data from input file to socket and waits for acknowledgement from destination before sending next frame
     * @throws IOException if there is a stream write or file read error.
     */
    public void transmit() throws IOException {
        out = new DataOutputStream(socket.getOutputStream());
        Scanner fileReader = new Scanner(new File("node" + this.casID + "_" + this.nodeID + ".txt"));

        delay(2000);

        while (fileReader.hasNextLine()) {
            Frame frame = new Frame(this.casID, this.nodeID, 111, fileReader.nextLine());

            //Send message, wait for ACK, if retransmit (wait flag set to 2), then repeat up to maxTX times
            //bytes are re-encoded in case CRC error is on this end
            int maxTX = 5;
            do {
                byte[] bytes = Frame.encode(frame);
                System.out.println("Node " + this.casID + "_" + this.nodeID + ": Sending message: " + frame.getData());
                out.write(bytes);
                setReaderWaitFlag(0);
                int timeout = 0;
                --maxTX;
                while (this.readerWaitFlag == 0) {
                    //System.out.println("IN 0 FLAG"); //
                    delay(100);
                    if (timeout >= TIMEOUT_DELAY || maxTX <= 0) {
                        System.out.println("Node " + this.casID + "_" + this.nodeID + " Error: Timed out");
                        break;
                    }
                }
                System.out.println("Node " + this.casID + "_" + this.nodeID + ": Received ACK for message - \"" + frame.getData() + "\"");
            } while (this.readerWaitFlag == 2);
        }

        //Send end signal
        delay(200);
        System.out.println("Node " + this.casID + "_" + this.nodeID + ": Sending end flag");
        Frame closeFrame = new Frame(this.casID, this.nodeID, 123, this.fullSrcID + ":CLOSE");
        byte[] bytes = Frame.encode(closeFrame);
        out.write(bytes);
    }

    /**
     * Synchronously sets transmission wait flag to allow/block transmit method from sending next frame
     * @param flag 0 to wait, 1 to continue, 2 to retransmit
     */
    private synchronized void setReaderWaitFlag(int flag) {
        //0 - wait | 1 - all good | 2 - retransmit
        this.readerWaitFlag = flag;
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
}

//FRAME FORMAT: [DST][SRC][CRC][SIZE/ACK][ACK type][data]
/*  ACK:
    00 No response (ReTX)
    01 CRC Error (ReTX)
    10 Firewall (No TX)
    11 Positive ACK
    100 Frame flooded, no ack necessary
    101 Firewall rules (only used by switches)
    111 Normal message - default
    123 End signal
 */
