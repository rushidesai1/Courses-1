/*##############################################################
## MODULE: Protocol.java
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

/* Protocol configuration */
interface Protocol {
    //  Manually specify the base of client listener port and server
    //  listener port.
    final static int CLIENT_PORT_BASE = 8500;
    final static int SERVER_PORT_BASE = 8400; 
    final static int MASTER_PORT = 8200;

    // Manually specify the format of differnt type of message
    final static String EXIT_MESSAGE = "EXIT";
}
