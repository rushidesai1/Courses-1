############################################################
##    FILENAME:   Server.py    
##    VERSION:    1.0
##    SINCE:      2014-04-15
##    AUTHOR: 
##        Jimmy Lin (xl5224) - JimmyLin@utexas.edu  
##
############################################################
##    Edited by MacVim
##    Documentation auto-generated by Snippet 
############################################################

import Protocol as P
import Logging as L
import Util as U

## TODO: static variable here
logHeader = None

def main():
    '''
    Main function of server
    '''
    ## TODO: initialize static variables
    serverID
    logHeader = L.SERVER_LOG_HEADER % serverID
    ## construct server socket
    s = socket.socket()         
    host = socket.gethostname() 
    port = P.SERVER_PORT_BASE + clientID
    s.bind((host, port))        

    s.listen(5)  
    while True:
        conn, addr = s.accept()     
        recvMsg = conn.recv(P.BUFFER_SIZE)
        st, si, rt, ri, title, content = U.decode(recvMsg)
        # TODO: process incoming message
        if title == '':
            pass

        conn.close() 
    pass

if __name__ == '__main__':
    ## TODO: process cmd arguments and give it to main
    main()
