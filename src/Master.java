/*##############################################################
## MODULE: Master.java
## VERSION: 1.0 
## SINCE: 2014-03-31
## AUTHOR: 
##         JIMMY LIN (xl5224) - JimmyLin@utexas.edu  
##
## DESCRIPTION: 
##      
#################################################################
## Edited by MacVim
## Class Info auto-generated by Snippet 
################################################################*/

import java.util.Scanner;
import java.util.ArrayList;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Master extends Util {
    final static String RUN_SERVER_CMD = "java -cp bin/ Server";
    final static String RUN_CLIENT_CMD = "java -cp bin/ Client";

    public static void main(String [] args) throws IOException, InterruptedException {
        Scanner scan = new Scanner(System.in);
        int numNodes, numClients;
        int clientIndex, nodeIndex;
        Process [] serverProcesses = null;
        Process [] clientProcesses = null;
        // server socket of master
        InetAddress localhost = InetAddress.getLocalHost();
        final ServerSocket listener = new ServerSocket(MASTER_PORT, 0,
                localhost);
        listener.setReuseAddress(true);

        while (scan.hasNextLine()) {
            int port;
            // parse the input instruction
            String input = scan.nextLine();
            String [] inputLine = input.split(" ");
            System.out.println("[INPUT] "+input);
            
            // process creator
            Runtime runtime = Runtime.getRuntime();
            switch (inputLine[0]) {
                case "start":
                    numNodes = Integer.parseInt(inputLine[1]);
                    numClients = Integer.parseInt(inputLine[2]);
                    /*
                     * start up the right number of nodes and clients, and store the 
                     *  connections to them for sending further commands
                     */
                    // ============================================================
                    // DRIVEN BY JIMMY LIN STARTS
                    final ArrayList<Boolean> serversSetup = new ArrayList<Boolean> ();
                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        serversSetup.add(false);
                    }
                    final ArrayList<Boolean> clientsSetup = new ArrayList<Boolean> ();
                    for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
                        clientsSetup.add(false);
                    }

                    Thread collectSetUpAcks = new Thread (new Runnable() {
                        public void run () {
                            try {
                            while (true) {
                            Socket socket = listener.accept();
                            try { 
                                BufferedReader in = new BufferedReader(new
                                        InputStreamReader(socket.getInputStream()));
                                String recMessage = in.readLine();
                                String [] recInfo = recMessage.split(",");
                                if (recInfo[TITLE_IDX].equals(START_ACK_TITLE)) {
                                    int index = Integer.parseInt(recInfo[SENDER_INDEX_IDX]);
                                    if (recInfo[SENDER_TYPE_IDX].equals(SERVER_TYPE)) {
                                        serversSetup.set(index, true);
                                    } else if (recInfo[SENDER_TYPE_IDX].equals(CLIENT_TYPE)){
                                        clientsSetup.set(index, true);
                                    }
                                } else {
                                    continue;
                                }
                            } finally { socket.close(); }
                            }
                        } catch (IOException e) {;} finally { ;}
                        }
                    });
                    collectSetUpAcks.start();

                    serverProcesses = new Process [numNodes];
                    clientProcesses = new Process [numClients];

                    for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
                        Integer clientID = new Integer(clientIndex);
                        String [] arguments = new String [1];
                        arguments[0] = clientID.toString();
                        String cmd = RUN_CLIENT_CMD + " " + arguments[0];
                        System.out.println(MASTER_LOG_HEADER + cmd);
                        Process pclient = runtime.exec(cmd);
                        clientProcesses[clientIndex] = pclient;
                    }

                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        Integer serverID = new Integer(nodeIndex);
                        String [] arguments = new String [2];
                        arguments[0] = serverID.toString();
                        arguments[1] = Integer.toString(numNodes);
                        String cmd = RUN_SERVER_CMD + " " + arguments[0] + " " + arguments[1];
                        System.out.println(MASTER_LOG_HEADER + cmd);
                        Process pserver = runtime.exec(cmd); 
                        serverProcesses[nodeIndex] = pserver;
                    }
                    // Confirm all clients and servers have set up their listeners
                    while (true) {
                        boolean isSetUpComplete = true;
                        // first check the socket setup of clients
                        for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
                            if (!clientsSetup.get(clientIndex)) { 
                                isSetUpComplete = false; 
                                break;
                            }
                        }
                        for (nodeIndex = 0; nodeIndex < numClients; nodeIndex ++) {
                            if (!serversSetup.get(nodeIndex)) {
                                isSetUpComplete = false;
                                break;
                            }
                        }
                        if (isSetUpComplete) {
                            collectSetUpAcks.interrupt();
                            System.out.println(MASTER_LOG_HEADER + "setup Completes..");
                            break;
                        }
                    }


                    // ============================================================
                    break;
                case "sendMessage":
                    clientIndex = Integer.parseInt(inputLine[1]);
                    String message = "";
                    for (int i = 2; i < inputLine.length; i++) {
                        message += inputLine[i];
                        if (i != inputLine.length - 1) {
                            message += " ";
                        }
                    }
                    /*
                     * Instruct the client specified by clientIndex to send the message
                     * to the proper paxos node
                     */
                    InetAddress host = InetAddress.getLocalHost();
                    port = CLIENT_PORT_BASE + clientIndex;
                    String pmessage = String.format(MESSAGE, MASTER_TYPE, 0, CLIENT_TYPE, clientIndex, 
                            SEND_MESSAGE_TITLE, message);
                    send (host, port, pmessage, MASTER_LOG_HEADER);
                    break;
                case "printChatLog":
                    clientIndex = Integer.parseInt(inputLine[1]);
                    /*
                     * Print out the client specified by clientIndex's chat history
                     * in the format described on the handout.	     
                     */
                    break;
                case "allClear":
                    /*
                     * Ensure that this blocks until all messages that are going to 
                     * come to consensus in PAXOS do, and that all clients have heard
                     * of them 
                     */
                    break;
                case "crashServer":
                    nodeIndex = Integer.parseInt(inputLine[1]);
                    /*
                     * Immediately crash the server specified by nodeIndex
                     */
                    // ======================================================
                    // We directly kill that process
                    serverProcesses[nodeIndex].destroy();
                    // ======================================================
                    break;
                case "restartServer":
                    nodeIndex = Integer.parseInt(inputLine[1]);
                    /*
                     * Restart the server specified by nodeIndex
                     */
                    break;
                case "skipSlots":
                    int amountToSkip = Integer.parseInt(inputLine[1]);
                    /*
                     * Instruct the leader to skip slots in the chat message sequence  
                     */ 
                    break;
                case "timeBombLeader":
                    int numMessages = Integer.parseInt(inputLine[1]);
                    /*
                     * Instruct the leader to crash after sending the number of paxos
                     * related messages specified by numMessages
                     */ 
                    break;
            }
        }
        /* Ask all clients and server to terminate */
        if (clientProcesses != null) {
            for (clientIndex = 0; clientIndex < clientProcesses.length; clientIndex ++) {
                if (clientProcesses[clientIndex] != null) {
                    int port = CLIENT_PORT_BASE + clientIndex;
                    String exitMessage = String.format(MESSAGE, MASTER_TYPE, 0,
                       CLIENT_TYPE, clientIndex, EXIT_TITLE, EMPTY_CONTENT);
                    send(localhost, port, exitMessage, MASTER_LOG_HEADER);
                }
            }
        }
        if (serverProcesses != null) {
            for (nodeIndex = 0; nodeIndex < serverProcesses.length; nodeIndex ++) {
                if (serverProcesses[nodeIndex] != null) {
                    InetAddress host = InetAddress.getLocalHost();
                    int port = SERVER_PORT_BASE + nodeIndex;
                    String exitMessage = String.format(MESSAGE, MASTER_TYPE, 0,
                       SERVER_TYPE, nodeIndex, EXIT_TITLE, EMPTY_CONTENT);
                    send(localhost, port, exitMessage, MASTER_LOG_HEADER);
                }
            }
        }
        listener.close();
    }
}
