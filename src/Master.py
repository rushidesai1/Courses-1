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

def MasterListener():
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


def MasterProcessor():
    for command in sys.stdin:
        if command == "startClient":
            ''' 
            startClient(i,j): Start a client with client id i, which is
            conneted to server j.
            ''' 
            assert (i is not in allClients), "START: Client already exists"
            assert (j is in allServers), "START: Server not exists."
            allClients.update ([i])
            call (["python src/Client.py", str(i)])

        elif command == "clientDisconnect":
            '''
            clientDisconnect(i): Client i disconnects with the server.
            '''

        elif command == "clientReconnect":
            '''
            clientReconnect(i,j): Client i reconnect to server j. We use this
            command after client i has disconnected with server by calling
            clientDisconnect(i).
            '''

        elif command == "pause":
            '''
            Pause the progress of the Bayou protocol, later you can continue the
            execution of the protocol.
            '''

        elif command == "continue":
            '''
            Continue after pause.
            '''

        elif command == "printLog":
            '''
            Print logs of all nodes.
            '''

        elif command == "printLogi":
            '''
            Print log of node i.
            '''

        elif command == "Isolate":
            '''
            Isolate(i): Node i is partitioned from other nodes.
            '''

        elif command == "reconnect":
            '''
            reconnect(i): Node i is connected to all other nodes. It is used when
            we try to recover the connections after we call partition(i).
            '''

        elif command == "breakConnection":
            '''
            breakConnection(i,j): Break the connection between Node i and Node j.
            '''


        elif command == "recoverConnection":
            '''
            recoverConnection(i,j): Recover the connection between Node i and Node j.
            '''


        elif command == "join":
            '''
            join(i): Node i joins the system.
            '''
            assert (i in allServers), "JOIN: server i already exists"
            allServers.update([i])

        elif command == "leave":
            '''
            leave(i): Node i leaves (retires from) the system.
            '''
    # Command processing completes
    # TODO: send messages to terminate the whole system


''' Program entry '''
if __name__ == "__main__":
    # create listener thread 
    listener = Thread(target=MasterListener)
    processor = Thread(target=MasterProcessor)

    listener.start()
    processor.start()

    processor.join()
    listener.join()
