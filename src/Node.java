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
            this.socket = new Socket("localhost", 1234);
        } catch (IOException e) {
            System.out.println("Node " + this.casID + "_" + this.nodeID + ": Connection refused.");
            e.printStackTrace();
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
                Frame frame = Frame.decode(buffer);

                //If CRC is wrong, ask for retransmission
                if (!frame.isCrcVerified()) {
                    Frame ackFrame = new Frame(this.casID, this.nodeID, 1, this.fullSrcID + ":");
                    byte[] bytes = Frame.encode(ackFrame);
                    out.write(bytes);
                    continue;
                }

                //Check if frame is an ACK response (or ACK type value that we've hijacked)
                if (frame.getSize() == 0) {
                    switch (frame.getAck()) {
                        case 1:
                            setReaderWaitFlag(2);
                            break;
                        case 2:
                        case 3:
                            setReaderWaitFlag(1);
                            break;
                        case 255:
                            listening = false;
                            break;
                        default:
                            System.out.println("Node " + this.casID + "_" + this.nodeID + ": Invalid ACK response received");
                    }
                } else {
                    //Otherwise, normal frame, write to file and send positive ACK
                    writer.write(frame.getData());

                    Frame ackFrame = new Frame(this.casID, this.nodeID, 3, this.fullSrcID + ":");
                    byte[] bytes = Frame.encode(ackFrame);
                    out.write(bytes);
                }
            }
        } //Loop - Listen for messages

        writer.close();
    }

    /**
     * Transmits data from input file to socket and waits for acknowledgement from destination before sending next frame
     * @throws IOException if there is a stream write or file read error.
     */
    public void transmit() throws IOException {
        out = new DataOutputStream(socket.getOutputStream());
        Scanner fileReader = new Scanner(new File("node" + this.casID + "_" + this.nodeID + ".txt"));

        while (fileReader.hasNextLine()) {
            Frame frame = new Frame(this.casID, this.nodeID, 0, fileReader.nextLine());

            //Send message, wait for ACK, if retransmit (wait flag set to 2), then repeat
            //bytes are re-encoded in case CRC error is on this end
            do {
                byte[] bytes = Frame.encode(frame);
                out.write(bytes);
                setReaderWaitFlag(0);
                while (this.readerWaitFlag == 0);
            } while (this.readerWaitFlag == 2);
        }

        //Send end signal
        Frame closeFrame = new Frame(this.casID, this.nodeID, 255, this.fullSrcID + ":");
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
}

//FRAME FORMAT: [DST][SRC][CRC][SIZE/ACK][ACK type][data]
/*  ACK:
    00 No response (ReTX)
    01 CRC Error (ReTX)
    10 Firewall (No TX)
    11 Positive ACK
 */
