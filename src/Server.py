############################################################
##    FILENAME:   Server.py 
##    VERSION:    1.4
##    SINCE:      2014-04-15
##    AUTHOR: 
##        Jimmy Lin (xl5224) - JimmyLin@utexas.edu
##
############################################################
##    Edited by MacVim
##    Documentation auto-generated by Snippet 
############################################################

import sys, socket
from Protocol import *
from Logging import *
from Util import *

## TODO: static variable here
logHeader = None
localhost = socket.gethostname() 

def anti_entropy (Senderid, Reiceiverid):
    ''' 
    Anti_entropy algorithm by fig. 1 of FUG paper
    '''
    ## send request to ask for receiver's version vector
    vvRequestMsg = encode(SERVER_TYPE, Senderid, SERVER_TYPE, \
                Reiceiverid, VERSION_VECTOR_REQUEST_TITLE, EMPTY_CONTENT)
    port = getPortByMsg(vvRequestMsg)
    send(localhost, port, vvRequestMsg, logHeader)
    ## block until response
    global vvResponseSema, vvResponseContent
    vvResponseSema = initEmpty
    vvResponseSema.acquire()
    RVersionVector = str2vv(vvResponseContent)
    ## for every write-log of Sender
    for accept_stamp, sid, oplog in writeLogs:
        wStr = W_FORMAT % (accept_stamp, sid, oplog)
        if RVersionVector.get(sid) < accept_stamp:
            # this oplog is new for Receiver
            # send write to receiver
            sendWriteMsg = encode(SERVER_TYPE, Senderid, SERVER_TYPE, \
                   Reiceiverid, SEND_WRITE_TITLE, wStr)
            send(localhost, port, sendWriteMsg, logHeader)

def main(argv):
    '''
    Main function of server
    '''
    assert len(argv) >= 2, "SERVER: too less arguments"

    ## initialize static variables
    pause = False
    serverID = int(argv[0])
    allServers = str2set(argv[1])
    logHeader = SERVER_LOG_HEADER % serverID

    writeLogs = initWriteLogs()
    versionVector = initVersionVector()
    localData = {}

    accept_stamp = 0
    global cachedMessages
    cachedMessages = []

    ## construct server socket
    s = socket.socket()         
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    port = SERVER_PORT_BASE + serverID
    s.bind((localhost, port))        
    ## send back acknowledgement
    ackMsg = encode(SERVER_TYPE, serverID, MASTER_TYPE, 0,\
                      JOIN_SERVER_ACK_TITLE, EMPTY_CONTENT)
    send(localhost, MASTER_PORT, ackMsg, logHeader)

    s.listen(5)
    while True:
        ## prioritize uncached message if system is non-paused
        if not pause and len(cachedMessages) > 0:
            ## uncached the cached messages
            recvMsg = cachedMessages.pop(0)
        else:
            conn, addr = s.accept()     
            recvMsg = conn.recv(BUFFER_SIZE)

        '''Incoming message preprocessing'''
        st, si, rt, ri, title, content = decode(recvMsg)

        if pause and title in ANTI_ENTROPY_TITLES:
            printCachedMessage(recvMsg, False)
            cachedMessages.append(recvMsg)

        printRecvMessage(recvMsg, logHeader)

        '''Processing incoming messages'''
        if title == JOIN_SERVER_TITLE:
            newServerId = int(content)
            allServers.add(newServerId)
            versionVector.update({newServerId:0})
            ackMsg = encode(SERVER_TYPE, serverID, MASTER_TYPE, 0, \
                           JOIN_SERVER_ACK_TITLE, str(newServerId))
            send(localhost, MASTER_PORT, ackMsg, logHeader)

        elif title == RETIRE_SERVER_TITLE:
            ## STEP ONE: exchange local log to at least one server it knows

            ## STEP TWO: notify all servers to forget it

            ## STEP THREE: lots of close

            pass
        elif title == JOIN_CLIENT_ACK_TITLE:
            deliverAckMsg = encode (SERVER_TYPE, serverID, \
               MASTER_TYPE, 0, JOIN_CLIENT_ACK_TITLE, EMPTY_CONTENT)
            port = getPortByMsg(deliverAckMsg)
            send(localhost, port, deliverAckMsg, logHeader)

        elif title == PRINT_LOG_TITLE:
            assert (st == MASTER_TYPE)
            ## generate logstr
            opLogs = [oplog for (_, _, opLogs) in writeLogs]
            logstr = writeLogs.join(LOG_SEP)

            ## send print log response back to master
            logMsg = encode(SERVER_TYPE, serverID, st, si, \
                           PRINT_LOG_RESPONSE_TITLE, logstr)
            send(localhost, MASTER_PORT, logMsg, logString)

        elif title == BREAK_CONNECTION_TITLE:
            toBreakServerId = int(content)
            assert (toBreakServerId in allServers)
            allServers.remove(toBreakServerId)
            ## remove that toBreakServerId from its version vector
            assert versionVector.pop(toBreakServerId, None) is not None, \
                "The server to break is unknown."
                
            ## TODO: send ack stuff?

            
        elif title == RESTORE_CONNECTION_TITLE:
            toRestoreServerId = int(content)
            assert (toRestoreServerId not in allServers)
            allServers.add(toRestoreServerId)
            ## add toRestoreServerId to versionVector
            versionVector.update({toRestoreServerId:0}) 
            ## trigger anti_entropy to exchange info
            anti_entropy(serverID, toRestoreServerId)
            ## TODO: send ack stuff??

        elif title == PAUSE_TITLE:
            ## switch the pause indicator
            assert not pause, "PAUSE: Cannot pause a paused system."
            pause = True
            ##  cache the following incoming messages
            cachedMessages = initCachedMessages()
            ## send pause acknowledgement to master
            pauseAckMsg = encode(SERVER_TYPE, serverID, MASTER_TYPE, 0, \
                                 PAUSE_ACK_TITLE, EMPTY_CONTENT)
            send (localhost, MASTER_PORT, pauseAckMsg, logHeader)

        elif title == RESTART_TITLE:
            ## switch the pause indicator
            assert pause, "START: Cannot restart a non-paused system."
            pause = False
            ## send restart acknowledgement to master
            restartAckMsg = encode(SERVER_TYPE, serverID, MASTER_TYPE, 0 ,\
                                  RESTART_ACK_TITLE, EMPTY_CONTENT)
            send(localhost, MASTER_PORT, restartAckMsg, logHeader)

        elif title == PUT_REQUEST_TITLE:
            ## update locallog
            [sn, URL] = content.split(SU_SEP)
            putlog = OPLOG_FORMAT % (PUT, OP_VALUE_FORMAT % (sn, URL), bool2str(False))
            writeLogs.append((accept_stamp, serverID, putlog))
            ## update the version vector
            versionVector.update({serverID:accept_stamp})
            ## update the accept stamp
            accept_stamp += 1
            ## update local datastore
            localData.update({sn:URL})
            ## send ack back
            putAckMsg = encode(rt, ri, st, si, PUT_ACK_TITLE, EMPTY_CONTENT)
            port = getPortByMsg(putAckMsg)
            send(localhost, port, putAckMsg, logHeader)

        elif title == GET_REQUEST_TITLE: ## look up local data store
            sn = content
            response_content = localData.get(sn)
            if response_content is None:
                response_content = sn + SU_SEP + ERR_KEY
            else:
                response_content = sn + SU_SEP + response_content
            ## send response back
            reponseMsg = encode(rt, ri, st, si, GET_RESPONSE_TITLE, \
                                response_content)
            port = getPortByMsg(reponseMsg)
            send(localhost, port, reponseMsg, logHeader)

        elif title == DELETE_REQUEST_TITLE:
            ## update locallog
            sn = content
            deletelog = LOG_FORMAT % (PUT, sn, bool2str(False))
            writeLogs.append((accept_stamp, serverID, deletelog))
            ## update version vector
            versionVector.update({serverID:accept_stamp})
            ## update the local accept stamp
            accept_stamp += 1
            ## update local datastore
            localData.pop(sn, None)
            ## send ack back
            deleteAckMsg = encode(rt, ri, st, si, DELETE_ACK_TITLE,\
                                  EMPTY_CONTENT)
            port = getPortByMsg(deleteAckMsg)
            send(localhost, port, deleteAckMsg, logHeader)

        elif title == VERSION_VECTOR_REQUEST_TITLE:
            ## convert version vector to string
            vvstr = vv2str(versionVector)
            vvMsg = encode(SERVER_TYPE, serverID, st, si, \
                           VERSION_VECTOR_RESPONSE_TITLE, vvstr)
            port = getPortByMsg(vvMsg)
            send(localhost, port, vvMsg, logHeader)

        elif title == VERSION_VECTOR_RESPONSE_TITLE:
            ## sema up
            global vvResponseSema, vvResponseContent
            vvResponseContent = content
            vvResponseSema.release()

        elif title == SEND_WRITE_TITLE:
            ## decode the content
            [accept_stamp, sid, oplog] = content.split(W_SEP)
            [accept_stamp, sid, oplog] = [int(accept_stamp), int(sid), oplog]
            assert accept_stamp > versionVector.get(sid), \
                    "SEND_WRITE: got a known update"
            w = (accept_stamp, sid, oplog)
            ## update the local write-logs
            writeLogs.append(w)
            ## apply update on local data 
            [op_type, op_value, stable_bool] = oplog.split(OPLOG_SEP)
            if op_type == PUT:
                [sn, URL] = op_value.strip("()").split(OP_VALUE_SEP)
                localData.update({sn:URL})
            elif op_type == DELETE:
                sn = op_value.strip("()")
                localData.pop(sn, None)

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
    serverID = int(sys.argv[1])
    origin_out = sys.stdout
    origin_err = sys.stderr
    logFile = open (SERVER_LOG_FILENAME % serverID, 'w+', 0)
    sys.stdout = logFile
    sys.stderr = logFile
    main(sys.argv[1:])
    print "END OF ROUTINE"
    logFile.close()
    sys.stdout = origin_out 
    sys.stderr = origin_err
