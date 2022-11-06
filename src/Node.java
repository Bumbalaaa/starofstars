import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Node class of Star of Stars project
 * Nodes transmit data from their input text file and output received data to their output text file.
 *
 * @author Ethan Coulthurst
 * @author Antonio Arant
 */
public class Node {
    private int casID;
    private int nodeID;
    private Socket socket;
    private int outgoingACK = 0;

    public Node(int casID, int nodeID) {
        this.casID = casID;
        this.nodeID = nodeID;

        try {
            this.socket = new Socket("localhost", 1234);
        } catch (IOException e) {
            System.out.println("Node " + this.casID + "_" + this.nodeID + ": Connection refused.");
            e.printStackTrace();
        }

        new Thread(new NodeListener(NodeListener.ListenerType.RECEIVER, this)).start();
        new Thread(new NodeListener(NodeListener.ListenerType.TRANSMITTER, this)).start();
    }

    public void receive() throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        File outputFile = new File("node" + this.casID + "_" + this.nodeID + "output.txt");
        //TODO Open output file and write received lines to it
    }

    public void transmit() throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        Scanner fileReader = new Scanner(new File("node" + this.casID + "_" + this.nodeID + ".txt"));

        while (fileReader.hasNextLine()) {
            byte[] frame = encode(fileReader.nextLine(), 0);
            out.write(frame);
            //TODO Make this function wait for ACK or send again
        }
    }

    private byte[] encode(String data, int ack) {
        byte[] frame = new byte[5 + data.length()];
        String[] dataElements = data.split(":");

        //Destination
        String[] destElements = dataElements[0].split("_");
        int casDest = Integer.parseInt(destElements[0]);
        int nodeDest = Integer.parseInt(destElements[1]);
        frame[0] = (byte) ((casDest << 4) | nodeDest);

        //Source
        frame[1] = (byte) ((this.casID << 4) | this.nodeID);

        //Size
        frame[3] = (byte) dataElements[1].length();

        //Data
        byte[] message = dataElements[1].getBytes();
        int i = 5;
        for (byte b : message) {
            frame[i++] = b;
        }

        //ACK Type
        frame[4] = (byte) ack;

        //CRC - MUST BE LAST
        byte crc = 0;
        for (byte b : frame) {
            //technically this should work cause at this point the crc bit (frame[2]) is 0
            crc += b;
        }
        frame[2] = crc;

        return frame;
    }

    private String decode(byte[] frame) {
        //Verify CRC
        byte crc = (byte) (frame[0] + frame[1]);
        for (int i = 3; i < frame.length; i++) {
            crc += frame[i];
        }
        if (crc != frame[2]) {
            outgoingACK = 1;
            return null;
        } else outgoingACK = 3;

        //Grab data
        String message = null;
        byte[] messageBytes = new byte[frame[3]];
        for (int i = 0; i < frame[3]; i++) {
            messageBytes[i] = frame[5 + i];
        }
        message = new String(messageBytes);

        //Attach source to final string
        int casSrc = (frame[1] & 0b11110000) >> 4;
        int nodeSrc = frame[1] & 0b00001111;
        return casSrc + "_" + nodeSrc + ":" + message;
    }
}

//FRAME FORMAT: [DST][SRC][CRC][SIZE/ACK][ACK type][data]
/*  ACK:
    00 No response (ReTX)
    01 CRC Error (ReTX)
    10 Firewall (No TX)
    11 Positive ACK
 */
