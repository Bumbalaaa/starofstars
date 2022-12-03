/**
 * <h3>Frame class of Star of Stars project</h3>
 * Frames are used to build data packets to be sent to the network
 * Frames can be encoded into a byte array to be sent, and vice versa
 *
 * @author Ethan Coulthurst
 * @author Antonio Arant
 * @version 1
 */
public class Frame {

    private int casSrc;
    private int nodeSrc;
    private int ack;
    private String data;

    private boolean crcVerified;
    private int size;

    /**
     * Creates a new Frame with the given data
     * @param casSrc Source arm switch
     * @param nodeSrc Source node
     * @param ack Acknowledgement type
     * @param data Raw message (including "x_y:")
     */
    public Frame(int casSrc, int nodeSrc, int ack, String data) {
        this.casSrc = casSrc;
        this.nodeSrc = nodeSrc;
        this.ack = ack;
        this.data = data;
    }

    /**
     * Encodes a frame object into a byte[] ready to be sent over a Socket
     * @param frame Frame to encode
     * @return Full packet byte array
     */
    public static byte[] encode(Frame frame) {
        byte[] bytes = new byte[5 + frame.data.length()];
        String[] dataElements = frame.data.split(":");

        //Destination
        String[] destElements = dataElements[0].split("_");
        int casDest = Integer.parseInt(destElements[0]);
        int nodeDest = Integer.parseInt(destElements[1]);
        bytes[0] = (byte) ((casDest << 4) | nodeDest);

        //Source
        bytes[1] = (byte) ((frame.casSrc << 4) | frame.nodeSrc);

        //Size
        if (dataElements.length > 1) bytes[3] = (byte) dataElements[1].length();
        else bytes[3] = 0;

        //Data
        byte[] message = dataElements.length > 1 ? dataElements[1].getBytes() : "".getBytes();
        int i = 5;
        for (byte b : message) {
            bytes[i++] = b;
        }

        //ACK Type
        bytes[4] = (byte) frame.ack;

        //CRC - MUST BE LAST
        byte crc = 0;
        for (byte b : bytes) {
            //technically this should work cause at this point the crc byte (bytes[2]) is 0
            crc += b;
        }
        bytes[2] = crc;

        return bytes;
    }

    /**
     * Decodes a byte array back into a Frame object
     * @param bytes Byte array to decode
     * @return Populated frame object
     */
    public static Frame decode(byte[] bytes) {
        //Verify CRC
        byte crc = (byte) (bytes[0] + bytes[1]);
        for (int i = 3; i < bytes.length; i++) {
            crc += bytes[i];
        }
        boolean crcVerified = crc != bytes[2];

        //Get source
        int casSrc = (bytes[1] & 0b11110000) >> 4;
        int nodeSrc = bytes[1] & 0b00001111;

        //Grab data
        byte[] messageBytes = new byte[bytes[3]];
        for (int i = 0; i < bytes[3]; i++) {
            messageBytes[i] = bytes[5 + i];
        }
        String data = casSrc + "_" + nodeSrc + ":" + new String(messageBytes);

        //Get ACK type
        int ack = bytes[4];

        //Build frame
        Frame frame = new Frame(casSrc, nodeSrc, ack, data);
        frame.crcVerified = true;
        frame.size = messageBytes.length;

        return frame;
    }

    /**
     * Get acknowledgement value
     * @return ACK
     */
    public int getAck() {
        return ack;
    }

    /**
     * Get message data (including source header x_y:)
     * @return Message
     */
    public String getData() {
        return data;
    }

    /**
     * Check if CRC from last decode was verified.
     * This field is not set on user-created frames.
     * @return CRC verified state as boolean
     */
    public boolean isCrcVerified() {
        return crcVerified;
    }
}
