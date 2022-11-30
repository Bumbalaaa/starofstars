/**
 * <h3>Main class of Star of Stars project</h3>
 * Creates core switch as well as a number of arm switches and nodes per arm switch determined by command line args.
 *
 * @author Ethan Coulthurst
 * @author Antonio Arant
 * @version 1
 */
public class Main {
    public static void main(String[] args) {
//        int nodeAmt = Integer.parseInt(args[1]);
//        int armSwitchAmt = Integer.parseInt(args[0]);
        int nodeAmt = 3;
        int armSwitchAmt = 3;

        if (nodeAmt < 2 || nodeAmt > 16) {
            System.out.println("Node per arm amount (" + nodeAmt + ") must be between 2 and 16");
            return;
        }

        if (armSwitchAmt < 2 || armSwitchAmt > 16) {
            System.out.println("Arm switch amount (" + armSwitchAmt + ") must be between 2 and 16");
            return;
        }

        //Central Switch
        new CoreSwitch();

        //Arm Switches + Nodes
        for (int i = 0; i < armSwitchAmt; i++) {
            new ArmSwitch(i);
            for (int j = 0; j < nodeAmt; j++) {
                new Node(i, j);
            }
        }
    }
}
