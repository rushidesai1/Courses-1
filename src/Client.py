############################################################
##    FILENAME:   Client.py
##    VERSION:    1.0
##    SINCE:      2014-04-15
##    AUTHOR:
##        Jimmy Lin (xl5224) - JimmyLin@utexas.edu
##
############################################################
##    Edited by MacVim
##    Documentation auto-generated by Snippet
############################################################

import sys, socket, traceback

from Protocol import *
from Logging import *
from Util import *

## TODO: static variable here
logHeader = None
clientID = None
serverToConnect = None
localhost = socket.gethostname()

def main(argv):
    ## TODO: initialize static variables
    assert len(argv) >= 2, "CLIENT: too less arguments"
    print "Client start"
    clientID = int(argv[0])
    serverToConnect = int(argv[1])
    logHeader = CLIENT_LOG_HEADER % clientID
    ## construct server socket
    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    port = CLIENT_PORT_BASE + clientID
    s.bind((localhost, port))

    ## send back acknowledgement
    ackMsg = encode (CLIENT_TYPE, clientID, SERVER_TYPE, serverToConnect,\
                      JOIN_CLIENT_ACK_TITLE, EMPTY_CONTENT)
    port = getPortByMsg(ackMsg)
    send (localhost, port, ackMsg, logHeader)

    s.listen(5)
    while True:
        conn, addr = s.accept()
        recvMsg = conn.recv(BUFFER_SIZE)
        st, si, rt, ri, title, content = decode(recvMsg)
        printRecvMessage(recvMsg, logHeader)
        # TODO: process incoming message
        if title == '':
            pass
        elif title == PUT_REQUEST_TITLE:
            putRequestMsg = encode(CLIENT_TYPE, clientID, SERVER_TYPE, \
                                   serverToConnect, PUT_REQUEST_TITLE, content)
            port = getPortByMsg(putRequestMsg)
            send(localhost, port, putRequestMsg, logHeader)

        elif title == PUT_ACK_TITLE:
            ## send ack to unblock the master
            putAckMsg = encode(CLIENT_TYPE, clientID, MASTER_TYPE, 0, \
                              PUT_ACK_TITLE, EMPTY_CONTENT)
            send(localhost, MASTER_PORT, putAckMsg, logHeader)

        elif title == GET_REQUEST_TITLE:
            getRequestMsg = encode(CLIENT_TYPE, clientID, SERVER_TYPE, \
                                  serverToConnect, GET_REQUEST_TITLE, content)
            port = getPortByMsg(getRequestMsg)
            send(localhost, port, getRequestMsg, logHeader)

        elif title == GET_RESPONSE_TITLE:
            ## send response to inform and unblock the master
            getResponseMsg = encode(CLIENT_TYPE, clientID, MASTER_TYPE, 0,\
                                   GET_RESPONSE_TITLE, content)
            send(localhost, MASTER_PORT, getResponseMsg, logHeader)

        elif title == DELETE_REQUEST_TITLE:
            deleteRequestMsg = encode(CLIENT_TYPE, clientID, SERVER_TYPE, \
                                  serverToConnect, GET_REQUEST_TITLE, content)
            port = getPortByMsg(deleteRequestMsg)
            send(localhost, port, deleteRequestMsg, logHeader)

        elif title == DELETE_ACK_TITLE:
            ## send ack to unblock the master
            putAckMsg = encode(CLIENT_TYPE, clientID, MASTER_TYPE, 0, \
                              DELETE_ACK_TITLE, EMPTY_CONTENT)
            send(localhost, MASTER_PORT, putAckMsg, logHeader)

        elif title == BREAK_CONNECTION_TITLE:
            toBreakServerId = int(content)
            assert (serverToConnect == toBreakServerId)
            serverToConnect = None
            ## TODO: send ack stuff?

        elif title == RESTORE_CONNECTION_TITLE:
            toRestoreServerId = int(content)
            assert (serverToConnect is None)
            serverToConnect = toRestoreServerId
            ## TODO: send ack stuff?

        elif title == CHECK_STABILIZATION_REQUEST_TITLE:
            ## deliver to replica it connects to
            checkMsg = encode(CLIENT_TYPE, clientID, SERVER_TYPE, \
                 serverToConnect, CHECK_STABILIZATION_REQUEST_TITLE,
                              content)
            port = getPortByMsg(checkMsg)
            send(localhost, port, checkMsg, logHeader)

        elif title == EXIT_TITLE:
            conn.close()
            s.close()
            print "Exit."
            return
        else:
            pass

        conn.close()

if __name__ == '__main__':
    ## TODO: process cmd arguments and give it to main
    clientID = int(sys.argv[1])
    origin_out = sys.stdout
    origin_err = sys.stderr
    logFile = open (CLIENT_LOG_FILENAME % clientID, 'w+', 0)
    sys.stdout = logFile
    sys.stderr = logFile
    try:
        main(sys.argv[1:])
    except:
        traceback.print_exc(file=sys.stdout)
    finally:
        print "END OF ROUTINE"
        sys.stdout.flush()
        logFile.close()
        sys.stdout = origin_out
        sys.stderr = origin_err
