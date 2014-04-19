############################################################
##    FILENAME:   Master.py    
##    VERSION:    1.0
##    SINCE:      2014-04-15
##    AUTHOR: 
##        Jimmy Lin (xl5224) - JimmyLin@utexas.edu  
##
############################################################
##    Edited by MacVim
##    Documentation auto-generated by Snippet 
############################################################

import sys, socket, os, subprocess
from threading import Thread

from Util import *
from Protocol import *
from Logging import *

## TODO: add more static variable here..
'''Utitlity'''
localhost = socket.gethostname()
logHeader = MASTER_LOG_HEADER

'''Global States'''
global allClients, allServers
allClients = set([])
allServers = set([])

'''Boolean Counter'''
global startAcks, pauseAcks
startAcks = None
pauseAcks = None

'''Semaphores'''
global joinClientSema, joinServerSema, startSema, pauseSema
joinClientSema = initEmptySemaphore() 
joinServerSema = initEmptySemaphore()
startSema = None
pauseSema = None
listenerSetUpSema = None
global putSema, getSema, deleteSema
putSema = None
getSema = None
deleteSema = None

def checkConnClients(id1, id2, servers, clients):
    assert id1 in servers or id1 in clients,\
            "CONNECTION: id1 %d unknown." % id1
    assert id2 in servers or id2 in clients,\
            "CONNECTION: id2 %d unknown." % id2
    isServer1 = id1 in servers and id1 not in clients
    isServer2 = id2 in servers and id2 not in clients
    assert isServer1 or isServer2, \
            "CONNECTION: both id are clients."
    return isServer1, isServer2

def MasterListener():
    '''
    Hold on server socket and listen to all incoming message by infinite loop
    '''
    s = socket.socket()         # Create a socket object
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((localhost, MASTER_PORT))        # Bind to the port
    listenerSetUpSema.release()

    s.listen(5)                 # Now wait for client connection.
    while True:
        conn, addr = s.accept()     # Establish connection with client.
        recvMsg = conn.recv(BUFFER_SIZE)      # receive message with BUFFER_SIZE

        printRecvMessage(recvMsg, logHeader)
        st, si, rt, ri, title, content = decode (recvMsg)
        global allClients, allServers

        if title == JOIN_SERVER_ACK_TITLE:
            global joinServerSema, joinServerAcks
            joinServerAcks.update({si:True})
            if checkCounterAllTrue(joinServerAcks):
                joinServerSema.release()

        elif title == JOIN_CLIENT_ACK_TITLE:
            global joinClientSema
            joinClientSema.release()

        elif title == PAUSE_ACK_TITLE:
            global pauseAcks, pauseSema
            pauseAcks.update({si:True})
            if checkCounterAllTrue(pauseAcks):
                pauseSema.release()

        elif title == RESTART_ACK_TITLE:
            global startAcks, startSema
            startAcks.update({si:True})
            if checkCounterAllTrue(startAcks):
                startSema.release()

        elif title == PRINT_LOG_RESPONSE_TITLE:
            recvLogs = content.split(LOG_SEP)
            for plog in recvLogs:
                print plog

        elif title == PUT_ACK_TITLE:
            global putSema
            putSema.release()

        elif title == GET_RESPONSE_TITLE:
            global getSema
            songName, URL = content.split(SU_SEP)
            print GET_FORMAT % (songName, URL)
            getSema.release()

        elif title == DELETE_ACK_TITLE:
            global deleteSema
            deleteSema.release()
            
        elif title == EXIT_TITLE:
            conn.close()
            s.close()
            return 
        else:
            pass

        conn.close()                # Close the connection
    s.close()

def MasterProcessor():
    for line in sys.stdin:
        line = line.strip()
        print "[INPUT]", line
        line = line.split()
        global allClients, allServers
        if line[0] ==  "joinServer":
            serverId = int(line[1])
            '''
            Start up a new server with this id and connect it to all servers
            '''
            assert (serverId not in allServers), \
                    "JOIN: server %d already exists." % serverIdx

            global joinServerSema, joinServerAcks
            joinServerAcks = initAllFalseCounter(allServers)
            joinServerAcks.update({serverId:False})
            ## invoke new server
            args = []
            args.append(LOAD_SERVER_CMD) # main command
            args.append(str(serverId))  # arg: server ID
            args.append(set2str(allServers)) # arg: all existing server ID
            cmd = args2cmd(args)
            os.system(cmd + " &")
            print cmd

            ## notify all existing server
            for oldServerId in allServers:
                notifyMsg = encode(MASTER_TYPE, 0, SERVER_TYPE, oldServerId,
                                   JOIN_SERVER_TITLE, str(serverId))
                port = getPortByMsg(notifyMsg)
                send(localhost, port, notifyMsg, logHeader)

            ## block until receiving acks
            joinServerSema.acquire()
            ## confirm to add new server to its list
            allServers.update([serverId])
            print "joinServer ", serverId, "completes."

        if line[0] ==  "retireServer":
            serverId = int(line[1])
            '''
            Retire the server with the id specified. This should block until
            the server can tell another server of its retirement
            ''' 
            retireMsg = encode (MASTER_TYPE, 0, SERVER_TYPE, serverId, \
                               RETIRE_REQUEST_TITLE, EMPTY_CONTENT)
            port = getPortByMsg (retireMsg)
            send (localhost, port, retireMsg, logHeader)
            ## TODO: retirement Protocol

        if line[0] ==  "joinClient":
            clientId = int(line[1])
            serverId = int(line[2])
            """
            Start a new client with the id specified and connect it to 
            the server
            """
            assert (clientId not in allClients), \
                    "JOIN_SERVER: Client %d already exists." % clientId
            assert (clientId not in allServers), \
                    "JOIN_SERVER: Id %d already occupied by certain server." % clientId
            assert (serverId not in allClients), \
                    "JOIN_SERVER: Given Id %d is a clientId." % serverId
            assert (serverId in allServers), \
                    "JOIN_SERVER: Server %d not exists." % serverId
            ## invoke new client
            args = []
            args.append(LOAD_CLIENT_CMD)
            args.append(str(clientId))
            args.append(str(serverId))
            cmd = args2cmd(args)
            os.system(cmd+ " &")
            print cmd

            ## block until received ack
            joinClientSema.acquire()
            ## confirm to add new client to its list
            allServers.update([serverId])
            allClients.update ([clientId])

            print "startClient", clientId, serverId, "completes."

        if line[0] ==  "breakConnection":
            id1 = int(line[1])
            id2 = int(line[2])
            """
            Break the connection between a client and a server or between
            two servers
            """
            isServer1, isServer2 = checkConnClients(id1, id2, allServers, allClients)
            ## Case 1: break connection between two servers
            # remove opponent's id from its allServers 
            if isServer1 and isServer2:
                breakConnMsg1 = encode(MASTER_TYPE, 0, SERVER_TYPE, id1, \
                                     BREAK_CONNECTION_TITLE, str(id2))
                port = getPortByMsg(breakConnMsg1)
                send(localhost, port, breakConnMsg1, logHeader)
                breakConnMsg2 = encode(MASTER_TYPE, 0, SERVER_TYPE, id2, \
                                     BREAK_CONNECTION_TITLE, str(id1))
                port = getPortByMsg(breakConnMsg2)
                send(localhost, port, breakConnMsg2, logHeader)

            ## Case 2: break connection between a client and a server
            if isServer1 and not isServer2:
                breakConnMsg3 = encode(MASTER_TYPE, 0, CLIENT_TYPE, id2, \
                                     BREAK_CONNECTION_TITLE, str(id1))
                port = getPortByMsg(breakConnMsg3)
                send(localhost, port, breakConnMsg3, logHeader)
            if not isServer1 and isServer2:
                breakConnMsg4 = encode(MASTER_TYPE, 0, CLIENT_TYPE, id1, \
                                     BREAK_CONNECTION_TITLE, str(id2))
                port = getPortByMsg(breakConnMsg4)
                send(localhost, port, breakConnMsg4, logHeader)

        if line[0] ==  "restoreConnection":
            id1 = int(line[1])
            id2 = int(line[2])
            """
            Restore the connection between a client and a server or between
            two servers
            """
            isServer1, isServer2 = checkConnClients(id1, id2, allServers, allClients)
            ## Case 1: restore connection between two servers
            if isServer1 and isServer2:
                restoreConnMsg1 = encode(MASTER_TYPE, 0, SERVER_TYPE, id1, \
                                     RESTORE_CONNECTION_TITLE, str(id2))
                port = getPortByMsg(restoreConnMsg1)
                send(localhost, port, breakConnMsg1, logHeader)
                restoreConnMsg2 = encode(MASTER_TYPE, 0, SERVER_TYPE, id2, \
                                     RESTORE_CONNECTION_TITLE, str(id1))
                port = getPortByMsg(restoreConnMsg2)
                send(localhost, port, restoreConnMsg2, logHeader)

            ## Case 2: restore connection between a client and a server
            if isServer1 and not isServer2:
                restoreConnMsg3 = encode(MASTER_TYPE, 0, CLIENT_TYPE, id2, \
                                     RESTORE_CONNECTION_TITLE, str(id1))
                port = getPortByMsg(restoreConnMsg3)
                send(localhost, port, restoreConnMsg3, logHeader)
            if not isServer1 and isServer2:
                restoreConnMsg4 = encode(MASTER_TYPE, 0, CLIENT_TYPE, id1, \
                                     RESTORE_CONNECTION_TITLE, str(id2))
                port = getPortByMsg(restoreConnMsg4)
                send(localhost, port, restoreConnMsg4, logHeader)

        if line[0] ==  "pause":
            """
            Pause the system and don't allow any Anti-Entropy messages to
            propagate through the system
            """
            global pauseAcks, pauseSema
            pauseAcks = initAllFalseCounter(allServers)
            pauseSema = initEmptySemaphore()
            sampleMsg = encode(MASTER_TYPE, 0,"xx",0, PAUSE_TITLE, \
                               EMPTY_CONTENT)
            broadcastServers (localhost, sampleMsg, logHeader, allServers)
            pauseSema.acquire()

        if line[0] ==  "start":
            """
            Resume the system and allow any Anti-Entropy messages to
            propagate through the system
            """
            global startAcks, startSema
            startAcks = initAllFalseCounter(allServers)
            startSema = initEmptySemaphore()
            sampleMsg = encode(MASTER_TYPE, 0,"xx",0, RESTART_TITLE,\
                               EMPTY_CONTENT)
            broadcastServers (localhost, sampleMsg, logHeader, allServers)
            startSema.acquire()

        if line[0] ==  "stabilize":
            """
            Block until there are enough Anti-Entropy messages for all values to 
            propagate through the currently connected servers. In general, the 
            time that this function blocks for should increase linearly with the 
            number of servers in the system.
            """
        if line[0] ==  "printLog":
            serverId = int(line[1])
            """
            Print out a server's operation log in the format specified in the
            handout.
            """
            askForLogMsg = encode(MASTER_TYPE, 0, SERVER_TYPE, serverId, \
                                 PRINT_LOG_TITLE, EMPTY_CONTENT) 
            port = getPortByMsg(askForLogMsg)
            send(localhost, port, askForLogMsg, logHeader)
        if line[0] ==  "put":
            clientId = int(line[1])
            songName = line[2]
            URL = line[3]
            """
            Instruct the client specified to associate the given URL with the given
            songName. This command should block until the client communicates with
            one server.
            """ 
            global putSema
            putSema = initEmptySemaphore()
            putRequestMsg = encode(MASTER_TYPE, 0, CLIENT_TYPE, clientId, \
                                  PUT_REQUEST_TITLE, songName+SU_SEP+URL)
            port = getPortByMsg(putRequestMsg)
            send(localhost, port, putRequestMsg, logHeader)
            putSema.acquire()

        if line[0] ==  "get":
            clientId = int(line[1])
            songName = line[2]
            """
            Instruct the client specified to attempt to get the URL associated with
            the given songName. The value should then be printed to standard out of 
            the master script in the format specified in the handout. This command 
            should block until the client communicates with one server.
            """ 
            global getSema
            getSema = initEmptySemaphore()
            getRequestMsg = encode(MASTER_TYPE, 0, CLIENT_TYPE, clientId, \
                                  GET_REQUEST_TITLE, songName)
            port = getPortByMsg(getRequestMsg)
            send(localhost, port, getRequestMsg, logHeader)
            getSema.acquire()

        if line[0] ==  "delete":
            clientId = int(line[1])
            songName = line[2]
            """
            Instruct the client to delete the given songName from the playlist. 
            This command should block until the client communicates with one server.
            """ 
            global deleteSema
            deleteSema = initEmptySemaphore()
            deleteRequestMsg = encode(MASTER_TYPE, 0, CLIENT_TYPE, clientId, \
                                     DELETE_REQUEST_TITLE, songName)
            port = getPortByMsg(deleteRequestMsg)
            send(localhost, port, deleteRequestMsg, logHeader)
            deleteSema.acquire()

    # Command processing completes
    # TODO: send messages to terminate the whole system
    exitMsg = encode (MASTER_TYPE, 0, "xx", -1, EXIT_TITLE, \
                        EMPTY_CONTENT)
    broadcast (localhost, exitMsg, logHeader, allServers, allClients)

    exitMsg = encode (MASTER_TYPE, 0, MASTER_TYPE, 0, \
                          EXIT_TITLE, EMPTY_CONTENT)
    send (localhost, MASTER_PORT, exitMsg, logHeader)

''' Program entry '''
if __name__ == "__main__":
    # out logging file
    origin_out = sys.stdout
    origin_err = sys.stderr
    '''
    logFile = open(Master_LOG_FILENAME, 'w+')
    sys.stdout = logFile
    sys.stderr = logFile
    '''
    # create listener thread 
    listenerSetUpSema = initEmptySemaphore()
    listener = Thread(target=MasterListener, args=())
    listener.start()
    ## block until master's listener has setup
    listenerSetUpSema.acquire()

    processor = Thread(target=MasterProcessor, args=())
    processor.start()


    processor.join()
    listener.join()
    # sys.stdout.close()
    sys.stdout = origin_out
    sys.stderr = origin_err
    print "Exit."
