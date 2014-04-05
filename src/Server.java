/*##############################################################
## MODULE: Server.java
## VERSION: 1.0 
## SINCE: 2014-03-30
## AUTHOR: 
##     JIMMY LIN (xl5224) - JimmyLin@utexas.edu  
##
## DESCRIPTION: 
##     Server Class of PAXOS consensus algorithm 
## 
################################################################# 
## Edited by MacVim 
## Class Info auto-generated by Snippet 
################################################################*/

import java.io.BufferedReader; 
import java.io.InputStreamReader; 
import java.io.IOException;
import java.io.File;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.HashMap; 
import java.util.concurrent.LinkedBlockingQueue;

class Server extends Util { // a.k.a. Replica
    /* Configuration */
    static String logHeader;
    static String logfilename;

    /* Knowledge of global scenario */
    static int serverID;
    static int numServers;
    static int numClients;
    static boolean NeedRecovery;
    static InetAddress localhost;

    /* Replica's attributes */
    static int slot_num;
    static HashMap<Integer, String> proposals;
    static HashMap<Integer, String> decisions;

    static boolean isRecoveryInProgress;

    /* Collocation: put leader and acceptor together */
    static Leader leader;
    static Acceptor acceptor;
    static LinkedBlockingQueue<String> queueLeader;
    static LinkedBlockingQueue<String> queueAcceptor;

    public static boolean propose (String command) throws IOException {
        // STEP ZERO: check if this request has not been decided
        for (String value: decisions.values()) {
            if (command.equals(value)) {
                // ignore this request since decided
                continue; 
            }
        }
        // STEP ONE: Determine the lowest unused slot number
        // Solution: incrementing slot number until we find one
        // that is not proposed yet
        int s = -1; 
        for (int tmp_s = 1; ; tmp_s ++) {
            if (proposals.get(tmp_s) == null) {
                s = tmp_s;
                break;
            }
        }
        if (s < 0) return false;
        // STEP TWO: put <s, p> to proposals
        proposals.put(s, command);
        // STEP THREE: send <propose, s, p> to all leaders
        for (int serverIndex = 0; serverIndex < numServers; serverIndex++) {
            String spArgs = String.format(PROPOSAL_CONTENT, s, command);
            String proposeMessage = String.format(MESSAGE,
                    SERVER_TYPE, serverID, LEADER_TYPE,
                    serverIndex, PROPOSE_TITLE, spArgs);
            int port = SERVER_PORT_BASE + serverIndex;
            send (localhost, port, proposeMessage, logHeader);
        }
        return true;
    }

    public static boolean perform (String command) throws IOException {
        // STEP ONE: Decode the input command
        String [] cmds = command.split(COMMAND_SEP);
        int clientID = Integer.parseInt(cmds[0]); // client indexx
        int cid = Integer.parseInt(cmds[1]); // local-client sequence number
        String operation = cmds[2];  // actually, it is message sent by client
        // STEP TWO: check if it has been decided yet
        for (int s: decisions.keySet()) {
            String tmp_cmd = decisions.get(s);
            if (s < slot_num && command.equals(tmp_cmd)) {
                slot_num += 1;
                return true;
            }
        }
        // STEP THREE: not decided yet, operate the state
        int paxosID = slot_num;
        slot_num += 1;
        for (int clientIndex = 0; clientIndex < numClients; clientIndex++) {
            int port = CLIENT_PORT_BASE + clientIndex;
            String cid_result = String.format(RESPONSE_CONTENT, clientID, cid, paxosID, operation);
            String response = String.format(MESSAGE, SERVER_TYPE, serverID,
                    CLIENT_TYPE, clientIndex, RESPONSE_TITLE, cid_result); 
            send (localhost, port, response, logHeader);
        }
        return true;
    }

    public static void main (String [] args) throws IOException, InterruptedException {
        // parse the server id assigned by master
        serverID = Integer.parseInt(args[0]);
        numServers = Integer.parseInt(args[1]);
        numClients = Integer.parseInt(args[2]);
        NeedRecovery = args[3].equals("1");
        // configure the LOG setting
        logHeader = String.format(SERVER_LOG_HEADER, serverID);
        logfilename = String.format(SERVER_LOG_FILENAME, serverID);
        PrintStream log = new PrintStream (new File(logfilename));
        // redirect output to specified file
        localhost = InetAddress.getLocalHost();
        System.setOut(log);
        System.setErr(log);

        // Initialize replica's attribute
        slot_num = 0;
        proposals = new HashMap<Integer, String> ();
        decisions = new HashMap<Integer, String> ();
        isRecoveryInProgress = false;

        // Initialization for collocation technique
        queueLeader = new LinkedBlockingQueue<String> ();
        queueAcceptor = new LinkedBlockingQueue<String> ();
        leader = new Leader(queueLeader, serverID, numServers, localhost); 
        new Thread(leader).start();
        acceptor = new Acceptor(queueAcceptor, serverID, localhost);
        new Thread(acceptor).start();

        // construct stable server socket
        final ServerSocket listener = new ServerSocket(SERVER_PORT_BASE+serverID, 0,
                localhost);
        listener.setReuseAddress(true);
        // send acknowledge to the master
        String setup_ack = String.format(MESSAGE, SERVER_TYPE, serverID,
                MASTER_TYPE, 0, START_ACK_TITLE, EMPTY_CONTENT);
        send (localhost, MASTER_PORT, setup_ack, logHeader);
        // indicate the socket listener setup
        print (listener.toString(), logHeader);
        // TODO: if this server is crashed before, recovery it
        final Integer nClients = numClients;
        if (NeedRecovery) {
            // STEP ONE: create a thread to receive recovery info 
            Thread collectRecoveryInfo = new Thread (new Runnable() {
                public void run () {
                    try { while (true) {
                        Socket socket = listener.accept();
                        try { 
                            BufferedReader in = new BufferedReader(new
                                InputStreamReader(socket.getInputStream()));
                            String recMessage = in.readLine();
                            printReceivedMessage(recMessage, MASTER_LOG_HEADER);
                            String [] recInfo = recMessage.split(",");
                            if (recInfo[TITLE_IDX].equals(HELP_YOU_RECOVER_TITLE)) {
                                String chatLogs = recInfo[CONTENT_IDX];
                                // check if setup is complete
                                boolean isRecoveryInfoReceived = true;
                                // TODO: decode the content
                                String [] chatLogsPart = chatLogs.split(RECOVERY_INFO_SEP);
                                int slot_num = Integer.parseInt(chatLogsPart[0]);
                                String [] recDecisions = chatLogsPart[1].split(DECISION_SEP);
                                for (int dIdx = 0; dIdx < recDecisions.length; dIdx ++) {
                                    String [] oneDecision = recDecisions[dIdx].split(MAP_SEP);
                                    decisions.put(Integer.parseInt(oneDecision[0]), oneDecision[1]);
                                }
                                if (isRecoveryInfoReceived) {
                                    print("Replica Recovered.", MASTER_LOG_HEADER);
                                    break;
                                }
                            } else {
                                continue;
                            }
                        } finally { socket.close(); }
                    }
                    } catch (IOException e) {;} finally { ;}
                }
            });
            collectRecoveryInfo.start();
            // STEP TWO: send message to all replicas, ask for the chat log
            isRecoveryInProgress = true;
            for (int serverIndex = 0; serverIndex < numServers; serverIndex++) {
                if (serverIndex == serverID) continue;  // not to request itself
                // FIXME: failure detection? no need to request if opposite is
                // recovered
                String askForRecovery = String.format(MESSAGE, SERVER_TYPE,
                        serverID, SERVER_TYPE, serverIndex,
                        I_WANNA_RECOVER_TITLE, EMPTY_CONTENT);
                int port = SERVER_PORT_BASE + serverIndex;
                send (localhost, port, askForRecovery, logHeader);
            }

            // STEP THREE: join thread and indicate completion of recovery
            collectRecoveryInfo.join();
            isRecoveryInProgress = false;
            NeedRecovery = false;
        }

        try {
            while (true) {
                Socket socket = listener.accept();
                try {
                    BufferedReader in = new BufferedReader(new
                            InputStreamReader(socket.getInputStream()));
                    // channel is established
                    String recMessage = in.readLine();
                    String [] recInfo = recMessage.split(",");
                    int port;

                    // Decode the incoming message
                    String sender_type = recInfo[SENDER_TYPE_IDX];
                    int sender_idx = Integer.parseInt(recInfo[SENDER_INDEX_IDX]);
                    String receiver_type = recInfo[RECEIVER_TYPE_IDX];
                    int receiver_idx = Integer.parseInt(recInfo[RECEIVER_INDEX_IDX]);
                    String title = recInfo[TITLE_IDX];
                    String content = recInfo[CONTENT_IDX];

                    // Check if message is propose, p1b, p2b, adopted, or preempted
                    // If so, add to Leader queue
                    if (receiver_type.equals(LEADER_TYPE)) {
                        queueLeader.put(recMessage);
                    }
                    // Check if message is p1a or p2a
                    // If so, add to Acceptor queue
                    if (receiver_type.equals(ACCEPTOR_TYPE)) {
                        queueAcceptor.put(recMessage);
                    }

                    printReceivedMessage(recMessage, logHeader);
                    // Check if message is request
                    if (title.equals(REQUEST_TITLE)) {
                        propose (content);
                    } else if (title.equals(DECISION_TITLE)) {
                        // STEP ZERO: decode the content
                        String [] conts = content.split (CONTENT_SEP);
                        int s = Integer.parseInt(conts[0]);
                        String p = conts[1];
                        // STEP ONE: add decision message to decisions
                        decisions.put(s, p);
                        // STEP TWO: find ready decision to be executed
                        // check if exists a decision p' corresponds to
                        // current slot_num s
                        String pprime = null;
                        if ((pprime = decisions.get(slot_num)) != null) {
                            // STEP THREE: check if it has proposed another command
                            // p'' in current slot_num, repropose p'' with new s''
                            String pprimeprime = proposals.get(slot_num);
                            if (!pprime.equals(pprimeprime)) {
                                propose (pprimeprime);
                            }
                            // STEP FOUR: invoke perform
                            perform(pprime);
                        }
                    } else if (title.equals(I_WANNA_RECOVER_TITLE)) {
                        assert (sender_type.equals(SERVER_TYPE)):
                            "Recover Request should come from server.";
                        assert (sender_idx != serverID): "Cannot recover from itself.";
                        String recoverInfo = "";
                        // STEP ONE: encode slot_num
                        recoverInfo += Integer.toString(slot_num);
                        recoverInfo += RECOVERY_INFO_SEP;
                        // STEP TWO: encode decisions
                        for (int sn: decisions.keySet()) {
                            recoverInfo += Integer.toString(sn) + MAP_SEP
                                + decisions.get(sn) + DECISION_SEP;
                        }
                        if (decisions.size() > 0) {
                            recoverInfo = recoverInfo.substring(0,
                                    recoverInfo.length()-DECISION_SEP.length());
                        }
                        // STEP THREE: send message back
                        String recoverMsg = String.format(MESSAGE, SERVER_TYPE,
                           serverID, SERVER_TYPE, sender_idx,
                           HELP_YOU_RECOVER_TITLE, recoverInfo);
                        port = SERVER_PORT_BASE + sender_idx;
                        send (localhost, port, recoverMsg, logHeader);
                    }

                    // this message is only given by master
                    if (title.equals(EXIT_TITLE) && sender_type.equals(MASTER_TYPE)) {
                        socket.close();
                        listener.close();
                        print("Exit.", logHeader);
                        System.exit(0);
                    }

                } finally {
                    socket.close();
                }
            }
        } finally {
            listener.close();
        }
    }
}
