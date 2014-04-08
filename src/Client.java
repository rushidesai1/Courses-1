/*##############################################################
## MODULE: Client.java
## VERSION: 1.0 
## SINCE: 2014-03-30
## AUTHOR: 
##     JIMMY LIN (xl5224) - JimmyLin@utexas.edu  
##     CALVIN SZETO - Szeto.calvin@gmail.com
## DESCRIPTION: 
##      
#################################################################
## Edited by MacVim
## Class Info auto-generated by Snippet 
################################################################*/

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;

public class Client extends Util {
    static String logHeader;
    static String logfilename;

    /* Knowledge of global scenario */
    static int clientID;
    static int numServers;
    static int numClients;

    static InetAddress localhost;

    /* sequence number */
    static int cid; // client-local command sequence number
    static HashMap<Integer, String> chatLogOperations;
    static HashMap<Integer, Integer> chatLogSpeakerID;
    static HashSet<Integer> requestSet;
    static HashSet<Integer> responseSet;

    /* Functional Field */
    static boolean needToCheckClear;
    static int leaderID;
    static int max_paxosid;

    public static boolean check_clear () throws IOException {
        // STEP ONE: check if it is clear 
        boolean isClear = true;
        for (int cid: requestSet) {
            // add send history and chat log comparison
            if (!responseSet.contains(cid)) {
                isClear = false;
            }
            // System.out.println(cid);
        }
        // STEP TWO: send CHECK_CLEAR_ACK to Master
        if (isClear) {
            String ack = String.format(MESSAGE, CLIENT_TYPE, clientID,
                    MASTER_TYPE, 0, CHECK_CLEAR_ACK_TITLE, EMPTY_CONTENT); 
            send (localhost, MASTER_PORT, ack, logHeader);
        }
        return isClear;
    }

    public static void main (String [] args) throws IOException {
        // parse the given id
        clientID = Integer.parseInt(args[0]);
        numServers = Integer.parseInt(args[1]);
        numClients = Integer.parseInt(args[2]);

        // initialize local chat log
        chatLogOperations = new HashMap<Integer, String> ();
        chatLogSpeakerID = new HashMap<Integer, Integer> ();
        requestSet = new HashSet<Integer> ();
        responseSet = new HashSet<Integer> ();

        // configure the LOG setting
        logHeader = String.format(CLIENT_LOG_HEADER, clientID);
        logfilename = String.format(CLIENT_LOG_FILENAME, clientID);
        PrintStream log = new PrintStream (new File(logfilename));

        // redirect output to specified file
        System.setOut(log);
        System.setErr(log);

        // functional initialization
        needToCheckClear = false;
        max_paxosid = -1;

        // derive localhost object
        localhost = InetAddress.getLocalHost();
        // construct stable server socket
        ServerSocket listener = new ServerSocket(CLIENT_PORT_BASE+clientID, 0,
                InetAddress.getLocalHost());
        listener.setReuseAddress(true);
        // send acknowledgement to the master
        String setup_ack = String.format(MESSAGE, CLIENT_TYPE, clientID,
                MASTER_TYPE, 0, START_ACK_TITLE, EMPTY_CONTENT);
        send (localhost, MASTER_PORT, setup_ack, logHeader);
        // indicate the listener setup
        System.out.println(logHeader + listener.toString());
        int port;
        try { while (true) {
            Socket socket = listener.accept();
            try {
                BufferedReader in = new BufferedReader(new
                        InputStreamReader(socket.getInputStream()));
                // CHANNEL IS ESTABLISHED
                String recMessage = in.readLine();
                printReceivedMessage(recMessage, logHeader);
                String [] recInfo = recMessage.split(MESSAGE_SEP);

                String sender_type = recInfo[SENDER_TYPE_IDX];
                int sender_idx = Integer.parseInt(recInfo[SENDER_INDEX_IDX]);
                String receiver_type = recInfo[RECEIVER_TYPE_IDX];
                int receiver_idx = Integer.parseInt(recInfo[RECEIVER_INDEX_IDX]);
                String title = recInfo[TITLE_IDX];
                String content = recInfo[CONTENT_IDX];

                if (title.equals(SEND_MESSAGE_TITLE)) {
                    // send chat message (command) to all servers
                    String command = String.format(COMMAND, clientID, cid, content);
                    String request = String.format(MESSAGE, CLIENT_TYPE,
                            clientID, SERVER_TYPE, leaderID,
                            REQUEST_TITLE, command);
                    port = SERVER_PORT_BASE + leaderID;
                    send (localhost, port, request, logHeader);
                    // add that message to send history
                    requestSet.add(cid++);
                } else if (title.equals(RESPONSE_TITLE)) {
                    // STEP ONE: decode the pvalue
                    String [] responseParts = content.split(CONTENT_SEP);
                    int clientIndex = Integer.parseInt(responseParts[0]);
                    int cid = Integer.parseInt(responseParts[1]);
                    int paxosId = Integer.parseInt(responseParts[2]);
                    String operation = responseParts[3];
                    // STEP TWO: put received response into local records
                    chatLogOperations.put(paxosId, operation);
                    chatLogSpeakerID.put(paxosId, clientIndex);
                    if (paxosId > max_paxosid) max_paxosid = paxosId;
                    if (clientID == clientIndex) {
                        responseSet.add(cid);
                        // STEP THREE: check clear since there is a update 
                        if (needToCheckClear)
                            needToCheckClear = !check_clear();
                    }
                } else if (title.equals(PRINT_CHAT_LOG_TITLE)) {
                    // print all local chat log
                    String allChatMessages = "";
                    for (int paxosid = 0; paxosid <= max_paxosid; paxosid ++) {
                        String message = chatLogOperations.get(paxosid);
                        if (message != null) {
                            int speakerIdx = chatLogSpeakerID.get(paxosid);
                            String outMessage = String.format
                                (OUTPUT_MESSAGE, paxosid, speakerIdx, message);
                            allChatMessages += outMessage + CHAT_PIECE_SEP;
                        } 
                    }
                    if (allChatMessages.length() > 0) {
                        allChatMessages = allChatMessages.substring(0,
                           allChatMessages.length() - CHAT_PIECE_SEP.length());
                    }
                    String toMasterMsg = String.format(MESSAGE, CLIENT_TYPE,
                          clientID, MASTER_TYPE, 0, HERE_IS_CHAT_LOG_TITLE,
                          allChatMessages);
                    send (localhost, MASTER_PORT, toMasterMsg, logHeader);
                } else if (title.equals(CHECK_CLEAR_TITLE)) {
                    boolean success = check_clear();
                    // if not clear now, need to check after response is
                    // updated
                    if (!success) needToCheckClear = true;
                } else if (title.equals(LEADER_REQUEST_TITLE)) {
                    leaderID = Integer.parseInt(content);
                    String ackLeaderMsg = String.format (MESSAGE, CLIENT_TYPE,
                            clientID, MASTER_TYPE, 0, LEADER_ACK_TITLE,
                            EMPTY_CONTENT); 
                    send (localhost, MASTER_PORT, ackLeaderMsg, logHeader);
                } else if (title.equals(EXIT_TITLE)) {
                    socket.close();
                    listener.close();
                    System.out.println(logHeader + "Exit.");
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
