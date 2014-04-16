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

import sys, socket
from threading import Thread

import Util as U
import Protocol as P
import Logging as L

# TODO: add more static variable here..
logHeader = L.MASTER_LOG_HEADER

allClients = set([])
allServers = set([])

localhost = socket.gethostname()

def MasterListener(log):
    '''
    Hold on server socket and listen to all incoming message by infinite loop
    '''
    s = socket.socket()         # Create a socket object
    host = socket.gethostname() # Get local machine name
    port = P.MASTER_PORT               # Reserve a port for your service.
    s.bind((host, port))        # Bind to the port

    s.listen(5)                 # Now wait for client connection.
    while True:
        conn, addr = s.accept()     # Establish connection with client.
        # c.send('Thank you for connecting')  # send message to client
        recvMsg = conn.recv(P.BUFFER_SIZE)      # receive message with BUFFER_SIZE
        printRecvMessage(recvMsg, logHeader)
        st, si, rt, ri, title, content = decode (recvMsg)
        # TODO: add the processor
        if title == 'startupAck':
            pass
        elif title == '':
            pass
        conn.close()                # Close the connection
    s.close()


def MasterProcessor(log):
    for command in sys.stdin:
        args = command.split(" ")
        cmd = args[0]
        print cmd
        if cmd == "startClient":
            ''' 
            startClient(i,j): Start a client with client id i, which is
            conneted to server j.
            ''' 
            clientIdx = int (args[1])
            serverIdx = int (args[2])
            assert (clientIdx not in allClients), \
                    "START: Client %d already exists" % clientIdx
            assert (serverIdx in allServers), \
                    "START: Server %d not exists." % serverIdx
            allClients.update ([clientIdx])
            # call (["python src/Client.py", str(clientIdx)])

            print "startClient", clientIdx, serverIdx, "completes."

        elif cmd == "clientDisconnect":
            '''
            clientDisconnect(i): Client i disconnects with the server.
            '''

        elif cmd == "clientReconnect":
            '''
            clientReconnect(i,j): Client i reconnect to server j. We use this
            cmd after client i has disconnected with server by calling
            clientDisconnect(i).
            '''

        elif cmd == "pause":
            '''
            Pause the progress of the Bayou protocol, later you can continue the
            execution of the protocol.
            '''

        elif cmd == "continue":
            '''
            Continue after pause.
            '''

        elif cmd == "printLog" and len(args) == 1:
            '''
            Print logs of all nodes.
            '''

        elif cmd == "printLog" and len(args) == 2:
            serverIdx = args[1]
            '''
            Print log of node i.
            '''

        elif cmd == "Isolate":
            serverIdx = args[1]
            '''
            Isolate(i): Node i is partitioned from other nodes.
            '''

        elif cmd == "reconnect":
            serverIdx = args[1]
            '''
            reconnect(i): Node i is connected to all other nodes. It is used when
            we try to recover the connections after we call partition(i).
            '''

        elif cmd == "breakConnection":
            serverOneIdx = args[1]
            serverTwoIdx = args[2]
            '''
            breakConnection(i,j): Break the connection between Node i and Node j.
            '''


        elif cmd == "recoverConnection":
            serverOneIdx = args[1]
            serverTwoIdx = args[2]
            '''
            recoverConnection(i,j): Recover the connection between Node i and Node j.
            '''


        elif cmd == "join":
            serverIdx = int(args[1])
            '''
            join(i): Node i joins the system.
            '''
            assert (serverIdx not in allServers), \
                    "JOIN: server %d already exists." % serverIdx
            allServers.update([serverIdx])

            print "join ", serverIdx, "completes."

        elif cmd == "leave":
            '''
            leave(i): Node i leaves (retires from) the system.
            '''
            serverIdx = args[1]
    # Command processing completes
    # TODO: send messages to terminate the whole system
    for clientIdx in allClients:
        exitMsg = U.encode (P.MASTER_TYPE, 0, P.CLIENT_TYPE, clientIdx, \
                          P.EXIT_TITLE, P.EMPTY_CONTENT)
        port = P.CLIENT_PORT_BASE + clientIdx;
        U.send (localhost, port, exitMsg, logHeader)

    for serverIdx in allServers:
        exitMsg = U.encode (P.MASTER_TYPE, 0, P.SERVER_TYPE, serverIdx, \
                          P.EXIT_TITLE, P.EMPTY_CONTENT)
        port = P.SERVER_PORT_BASE + serverIdx;
        U.send (localhost, port, exitMsg, logHeader)

    exitMsg = U.encode (P.MASTER_TYPE, 0, P.MASTER_TYPE, 0, \
                          P.EXIT_TITLE, P.EMPTY_CONTENT)
    port = P.MASTER_PORT;
    U.send (localhost, port, exitMsg, logHeader)

''' Program entry '''
if __name__ == "__main__":
    # out logging file
    log = open(L.Master_LOG_FILENAME, 'w+')
    # create listener thread 
    listener = Thread(target=MasterListener, args=(log,))
    processor = Thread(target=MasterProcessor, args=(log,))

    listener.start()
    processor.start()

    processor.join()
    listener.join()
    log.close()
