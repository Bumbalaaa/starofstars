# starofstars

## Group Members
Ethan Coulthurst\
Antonio Arant

## Git Repo Link
https://github.com/Bumbalaaa/starofstars

## Run Instructions
Java 17 is required to run this program. Place all input files formatted as `nodex_y.txt` in the same directory as the jar file. From the terminal, navigate to that directory and run the command `java -jar starofstars.jar x y` with x being the number of arm switches and y being the number of nodes per arm switch. Both numbers must be between 2 and 16. Output files will be generated in the same directory.

## Frame Format
The frame consists in order: source, destination, crc, size, ACK, data. Source is a single byte and is the source node of the message. Destination is the destination node and is one byte. Size is the size of the message, in bytes and is one byte long. CRC is an error checking mechanism, and is one byte long. The ACK is used to communicate needed acknowlegement info, such as if a frame needs to be re-transmitted, if there was a CRC error, if a frame had been firewalled, or if a frame had been sucessfully transmitted. It also is used during the shutdown phase, to let nodes and switches know to shutdown. Data is the meat of the frame. It is the actual data communicated, and can be 255 bytes long.

## Feature Checklist
| Feature        |Status/Description                        |  
|----------------|-------------------------------|  
|Project Compiles and Builds without warnings or errors| Complete            |  
|Switch class     |Complete          |  
|CAS, CCS Switches has a frame queue, and reads/writes appropriately  | Complete |  
|Switch allows multiple connections        |Complete |  
|CAS, CCS Switches allows multiple connections       |Complete |  
|CAS, CCS Switches flood frames when it doesn't know the destination |Complete |  
|CAS, CCS Switches learn destinations, and doesn't forward packets to any port except the one required      |Complete |  
|CAS receives local firewall rules| Complete |    
|CAS forwards traffic and ACKs properly        | Complete|
|CCS switch opens the firewall file and gets the rules| Complete|
|CCS passes global traffic| Complete|
|CCS does the global firewalls| Complete|
|CCS Shadow switches run and test properly| Not Present|
|Node class| Partially Complete See below|
|Nodes instantiate, and open connection to the switch| Complete|
|Nodes open their input files, and send data to switch.| Complete|
|Nodes open their output files, and save data that they received| Complete|  
|Node will sometimes drop acknowledgment| Not Present|
|Node will sometimes create erroneous frame| Not Present|
|Node will sometimes reject traffic| Not Present| 

## Project Files
- Main: Creates core arm switch, arm switches and nodes
- ClientLink: Creates a socket link from a node to an arm switch
- CCSLink: Creates a new link to attach to a core switch socket
- CASLink: Connects arm switch to core switch
- ArmSwitch: Distributes local traffic amognst the nodes connected, and forwards global traffic to core arm switch
- ClientAcceptor: Objects created to listen for nodes that are trying to connect to the arm switch
- SwitchAcceptor: A SwitchAcceptor object is created by the core switch that listens for incoming arm switch connections and adds them to the core switch.
- CoreSwitch: The main switch that handles all global traffic between arm switches. Also, loads and distributes firewall rules.
- NodeListener: A helper class instantiated by a Node's constructor that creates two threads to run that Node's transmit and receive methods
- Node: Creates node to read data from text file and sends it to the switch
- Frame: Contains frame format and helper methods for frame creation

## Bugs
- We do not have the intentional network faults set up, so no packets and ack's will be intentionally dropped to simulate network faults. 