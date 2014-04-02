/*##############################################################
## MODULE: Acceptor.java
## VERSION: 1.0
## SINCE: 2014-04-02
## AUTHOR: 
##         JIMMY LIN (xl5224) - JimmyLin@utexas.edu  
##
## DESCRIPTION: 
##      
#################################################################
## Edited by MacVim
## Class Info auto-generated by Snippet 
################################################################*/

import java.util.concurrent.LinkedBlockingQueue;

class Acceptor implements Runnable{
        private LinkedBlockingQueue queue = null;
        // ballotNum: the current adopted ballot number
        // accepted: set of accepted ballots
        
        public Acceptor(LinkedBlockingQueue queue) {
            this.queue = queue;
        }

        public void run() {
            // while true
                // receive messages from queue
                // if message is a p1a
                    // if the given ballot number is greater than the current one, then adopt it
                    // send a p1b in response with the current ballot number
                // if message is a p2a
                    // if the given ballot number is greater than or equal to the current one
                        // adopt that ballot number and accept the ballot
                    // send a p2b in response with the current ballot number
        }
    }
