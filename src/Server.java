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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    // TODO: use Integer to represent reason
    public static Integer interruptReason; 
    static boolean electionInProgress;
    static boolean isRecoveryInProgress;
    static boolean carryLeader;
    static int leaderID;
    
    static boolean proposeAsLeader;
    static HeartbeatTimer heartbeatTimer;
    static Long lastHeartbeatReceived;
    static Lock timerLock;
    static Thread replica;

    /* Collocation: put leader and acceptor together */
    static Thread leader;
    static Acceptor acceptor;
    static LinkedBlockingQueue<String> queueLeader;
    static LinkedBlockingQueue<String> queueAcceptor;

    static class HeartbeatTimer extends Thread implements Runnable {

        public void run() {
            long diff = 0;
            long current = 0;
            try {
                Thread.sleep(HB_INITIAL_WAIT);
            } catch (InterruptedException ie) {
                ;
            }
            while (true) {
                try {
                    // Periodically check the lastHeartbeatReceived variable
                    timerLock.tryLock();
                    current = System.currentTimeMillis();
                    diff = current - lastHeartbeatReceived;
                    // System.out.println("check heartbeat: " + lastHeartbeatReceived + " " + current);
                    timerLock.unlock();
                    // If last heartbeat is too old, then interrupt replica
                    if (diff > HB_TIMEOUT) {
                        print ("DETECT LEADER CRASH.", logHeader);
                        interruptReason = LEADER_FAILURE_INTERRUPT;
                        replica.interrupt();
                        return ;
                    }
                    // take a rest and re-check after sleep!
                    Thread.sleep(HB_CHECK_INTERVAL);
                } catch (InterruptedException ie) {
                    return ;
                }
            }
        }
    }

    public static boolean propose (String command) throws IOException {
        // STEP ZERO: check if this request has not been decided
        System.out.println(command);
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
        for (int tmp_s = 0; ; tmp_s ++) {
            if (proposals.get(tmp_s) == null) {
                s = tmp_s;
                break;
            }
        }
        if (s < 0) return false;
        // STEP TWO: put <s, p> to proposals
        proposals.put(s, command);
        // STEP THREE: send <propose, s, p> to the leader
        String spArgs = String.format(PROPOSAL_CONTENT, s, command);
        String proposeMessage = String.format(MESSAGE,
                SERVER_TYPE, serverID, LEADER_TYPE,
                leaderID, PROPOSE_TITLE, spArgs);
        int port = SERVER_PORT_BASE + leaderID;
        boolean success = send(localhost, port, proposeMessage, logHeader);
        if (!success)
            Thread.currentThread().interrupt(); 
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
        electionInProgress = false;
        isRecoveryInProgress = false;
        carryLeader = true;
        leaderID = -1;
        proposeAsLeader = false;
        replica = Thread.currentThread();
        interruptReason = NO_REASON;

        lastHeartbeatReceived = System.currentTimeMillis();
        timerLock = new ReentrantLock();

        // Initialization for collocation technique
        queueAcceptor = new LinkedBlockingQueue<String> ();
        acceptor = new Acceptor(queueAcceptor, serverID, localhost);
        new Thread(acceptor).start();

        final Boolean [] leaderProposalAcks = new Boolean [numServers];
        final Thread mainThread = Thread.currentThread();

        // construct stable server socket
        final ServerSocket listener = new ServerSocket(SERVER_PORT_BASE+serverID, 0,
                localhost);
        listener.setSoTimeout(20);
        listener.setReuseAddress(true);
        // send acknowledge to the master
        String setup_ack = String.format(MESSAGE, SERVER_TYPE, serverID,
                MASTER_TYPE, 0, START_ACK_TITLE, EMPTY_CONTENT);
        send (localhost, MASTER_PORT, setup_ack, logHeader);

        // indicate the socket listener setup
        print (listener.toString(), logHeader);
        // if this server is crashed before, recovery it
        final Integer nClients = numClients;
        // TODO: TEST THIS PART
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
                            String [] recInfo = recMessage.split(MESSAGE_SEP);
                            if (recInfo[TITLE_IDX].equals(HELP_YOU_RECOVER_TITLE)) {
                                String chatLogs = recInfo[CONTENT_IDX];
                                // check if setup is complete
                                boolean isRecoveryInfoReceived = true;
                                // decode the content
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

        Socket socket = null;
        while (true) {
            try {
                try {
                    socket = listener.accept();
                } catch (IOException e ){
                    if(Thread.currentThread().interrupted()) {
                        throw new InterruptedException();
                    }
                    continue;
                }
                BufferedReader in = new BufferedReader(new
                        InputStreamReader(socket.getInputStream()));
                // channel is established
                String recMessage = in.readLine();
                String [] recInfo = recMessage.equals(EMPTY_CONTENT) ? null : recMessage.split(",");
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
                    continue;
                }
                // Check if message is p1a or p2a
                // If so, add to Acceptor queue
                if (receiver_type.equals(ACCEPTOR_TYPE)) {
                    queueAcceptor.put(recMessage);
                    continue;
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
                    while ((pprime = decisions.get(slot_num)) != null) {
                        // STEP THREE: check if it has proposed another command
                        // p'' in current slot_num, repropose p'' with new s''
                        String pprimeprime = proposals.get(slot_num);
                        if (!pprime.equals(pprimeprime) && pprimeprime != null) {
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
                }  else if (title.equals(SKIP_SLOT_TITLE)) {
                    int amountToSkip = Integer.parseInt(content);
                    // STEP ONE: update the proposals
                    for (int i = slot_num; i < slot_num + amountToSkip; i++) {
                        assert(!proposals.containsKey(i));
                        proposals.put(i, SKIPPED_MARKER);
                    }
                    // STEP TWO: update the decisions
                    for (int i = slot_num; i < slot_num + amountToSkip; i++) {
                        assert(!decisions.containsKey(i));
                        decisions.put(i, SKIPPED_MARKER);
                    }
                    // STEP THREE: update the slot_num
                    slot_num += amountToSkip;
                    // STEP FOUR: send ack message back to master
                    String ackSkipSlot = String.format(MESSAGE,
                            SERVER_TYPE, serverID, MASTER_TYPE, 0,
                            SKIP_SLOT_ACK_TITLE, EMPTY_CONTENT);
                    send (localhost, MASTER_PORT, ackSkipSlot, logHeader);
                } else if (title.equals(TIME_BOMB_TITLE)) {
                    int numMessages = Integer.parseInt(content);
                    // Pass the time bomb message to Leader
                    queueLeader.put(recMessage); 
                }
                // =============================================================
                // THE FOLLOWING IS ABOUT LEADER
                else if (title.equals(LEADER_REQUEST_TITLE)) {
                    leaderID = Integer.parseInt(content);
                    if (leaderID == serverID) {
                        // remove heartbeatTimer
                        if (heartbeatTimer != null)
                            heartbeatTimer.interrupt();
                        // create Leader instance
                        carryLeader = true;
                        queueLeader = new LinkedBlockingQueue<String> ();
                        leader = new Thread(new Leader(queueLeader, serverID,
                                    numServers, localhost,
                                    Thread.currentThread())); 
                        leader.start();
                    } else {
                        heartbeatTimer = new HeartbeatTimer ();
                        heartbeatTimer.start();
                    }
                    int portBase;
                    if(sender_type.equals(SERVER_TYPE)) {
                        portBase = SERVER_PORT_BASE;
                    } else if(sender_type.equals(MASTER_TYPE)){
                        portBase = MASTER_PORT;
                    } else {
                        portBase = 0;
                        System.out.println("Exception.");
                    }
                    port = portBase + sender_idx;
                    String ackLeader = String.format(MESSAGE, SERVER_TYPE,
                            serverID, sender_type, sender_idx, LEADER_ACK_TITLE,
                            EMPTY_CONTENT);
                    send (localhost, port, ackLeader, logHeader);
                } else if (title.equals(LEADER_PROPOSAL_TITLE)) {
                    if (sender_idx < serverID) {
                        // Accept this proposal only when prior 
                        port = SERVER_PORT_BASE + sender_idx;
                        String acceptMsg = String.format (MESSAGE, SERVER_TYPE,
                                serverID, SERVER_TYPE, sender_idx,
                                LEADER_PROPOSAL_ACCEPT_TITLE, EMPTY_CONTENT);
                        send (localhost, port, acceptMsg, logHeader);
                    } else {
                        // reject it, propose itself as leader if not proposed
                        port = SERVER_PORT_BASE + sender_idx;
                        String acceptMsg = String.format (MESSAGE, SERVER_TYPE,
                                serverID, SERVER_TYPE, sender_idx,
                                LEADER_PROPOSAL_REJECT_TITLE, EMPTY_CONTENT);
                        send (localhost, port, acceptMsg, logHeader);
                    }
                } else if (title.equals(LEADER_PROPOSAL_ACCEPT_TITLE)) {
                    // once ge the leader ack from other server, it will get
                    // set the proposal ack to true
                    leaderProposalAcks[sender_idx] = true;
                } else if (title.equals(LEADER_PROPOSAL_REJECT_TITLE)) {
                    // once ge the leader ack from other server, it will get
                    // set the proposal ack to true
                    leaderProposalAcks[sender_idx] = false;
                } 
                // =============================================================
                else if (title.equals(HEARTBEAT_TITLE)) {
                    timerLock.tryLock();
                    lastHeartbeatReceived = System.currentTimeMillis();
                    timerLock.unlock();
                } else if (title.equals(EXIT_TITLE) &&
                        sender_type.equals(MASTER_TYPE)) {
                    carryLeader = false;
                    socket.close();
                    listener.close();
                    print("Exit.", logHeader);
                    System.exit(0);
                }
            } catch (InterruptedException ie) {
                if (interruptReason == TIMEBOMB_INTERRUPT) {
                    // Leader interrupts when it exits, signalling a crash
                    listener.close();
                    print("Crashing.", logHeader);
                    System.exit(0);
                } else if (interruptReason == LEADER_FAILURE_INTERRUPT) {
                    print ("Elect new leader start..",logHeader);
                    electionInProgress = true;
                    // STEP ZERO: add the code for electing new leader 
                    Thread electNewLeader = new Thread (new Runnable () {
                        public void run () {
                            // STEP ONE: propose itself as new leader
                            for (int serverIndex = 0; serverIndex < numServers; serverIndex++) {
                                if (serverIndex == serverID) { // ack for itself
                                    leaderProposalAcks[serverIndex] = true;
                                } else { // not ack yet for others
                                    leaderProposalAcks[serverIndex] = false;
                                }
                            }
                            // send message to ask for ack
                            for (int serverIndex = 0; serverIndex < numServers; serverIndex++) {
                                if (serverIndex == serverID) continue; 
                                int port = SERVER_PORT_BASE + serverIndex;
                                String leaderProposeMsg = String.format(MESSAGE,
                                    SERVER_TYPE, serverID, SERVER_TYPE,
                                    serverIndex, LEADER_PROPOSAL_TITLE, EMPTY_CONTENT);
                                    boolean success = send (localhost, port, leaderProposeMsg, logHeader);
                                    if(!success) {
                                        // the destination server is dead
                                        leaderProposalAcks[serverIndex] = null;
                                    }
                            }
                            try {
                                Thread.sleep(LEADER_PROPOSAL_TIMEOUT);
                            } catch (InterruptedException ie) {
                                // ignore
                                ;
                            }
                            // interrupt main thread
                            interruptReason = LEADER_CHECK_INTERRUPT;
                            mainThread.interrupt();
                        }
                    });
                    electNewLeader.start();
                } else if (interruptReason == LEADER_CHECK_INTERRUPT) {
                    // check the acks of its proposal
                    boolean amILeader = true;
                    for (int serverIndex = 0; serverIndex < numServers; serverIndex++) {
                        if (leaderProposalAcks[serverIndex] == null) continue;
                        if (!leaderProposalAcks[serverIndex]) {
                            amILeader = false;
                            break;
                        }
                    }
                    if (amILeader) {
                        // broadcast I am leader to clients and wait for acks
                        for (int clientIndex = 0; clientIndex < numClients; clientIndex ++) {
                            String IamLeaderMsg = String.format(MESSAGE,
                                    SERVER_TYPE, serverID, CLIENT_TYPE,
                                    clientIndex, LEADER_REQUEST_TITLE,
                                    Integer.toString(clientIndex));
                            int port = CLIENT_PORT_BASE + clientIndex;
                            send (localhost, port, IamLeaderMsg, logHeader);
                        } 
                        // broadcast I am leader to all servers, including
                        // itself to construct leader object
                        for (int serverIndex = 0; serverIndex < numServers; serverIndex ++) {
                            String IamLeaderMsg = String.format(MESSAGE,
                                    SERVER_TYPE, serverID, SERVER_TYPE,
                                    serverIndex, LEADER_REQUEST_TITLE,
                                    Integer.toString(serverIndex));
                            int port = SERVER_PORT_BASE + serverIndex;
                            send (localhost, port, IamLeaderMsg, logHeader);
                        }
                    } else {
                        // do nothing because I am not the new elected leader

                    }
                } else {
                    ie.printStackTrace();
                } 
            } catch (IOException e) {
                print ("IOException catched", logHeader);
                break;
            } finally {
                if (socket != null)
                    socket.close();
            }
        } 
        listener.close();
    }
}
