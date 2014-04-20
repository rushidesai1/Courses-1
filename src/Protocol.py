############################################################
##    FILENAME:   Protocol.py    
##    VERSION:    1.0
##    SINCE:      2014-04-15
##    AUTHOR: 
##        Jimmy Lin (xl5224) - JimmyLin@utexas.edu  
##
############################################################
##    Edited by MacVim
##    Documentation auto-generated by Snippet 
############################################################

# BUFFER SIZE
BUFFER_SIZE = 1024

# NODE TYPE SPECIFICATION
MASTER_TYPE = "MASTER"
CLIENT_TYPE = "CLIENT"
SERVER_TYPE = "SERVER"

# NETWORK PORT BASE
CLIENT_PORT_BASE = 8100+63;
SERVER_PORT_BASE = 8510+83; 
MASTER_PORT = 8070;

# PROTOCOL DESIGN
SENDER_TYPE_IDX = 0;
SENDER_INDEX_IDX = 1;
RECEIVER_TYPE_IDX = 2;
RECEIVER_INDEX_IDX = 3;
TITLE_IDX = 4;
CONTENT_IDX = 5;

# MESSAGE FORMAT
MESSAGE_SEP = "<,>";
MESSAGE = "%s" + MESSAGE_SEP + "%d" + MESSAGE_SEP +"%s" + MESSAGE_SEP \
        +"%d" + MESSAGE_SEP +"%s" + MESSAGE_SEP +"%s";

OPLOG_SEP = ":"
OPLOG_FORMAT = "%s:(%s):%s" # OP_TYPE:OP_VALUE:STABLE_BOOL
OP_VALUE_SEP = ","
OP_VALUE_FORMAT = "%s" + OP_VALUE_SEP + " %s" # songName, URL
GET_FORMAT = "%s:%s"

SU_SEP = ":" # songName and URL
LOG_SEP = "<\log>"
W_SEP = "<\w>"

W_FORMAT = "%d" + W_SEP + "%d" + W_SEP + "%s"

# Operation marocs
PUT = "PUT"
DELETE = "DELETE"


# TITLES
EXIT_TITLE = "EXIT"
JOIN_SERVER_ACK_TITLE = "JOIN_SERVER_ACK"
JOIN_SERVER_TITLE = "JOIN_SERVER"
JOIN_CLIENT_ACK_TITLE = "JOIN_CLIENT_ACK"
JOIN_CLIENT_TITLE = "JOIN_CLIENT"
PAUSE_TITLE = "PAUSE"
PAUSE_ACK_TITLE = "PAUSE_ACK"
RESTART_TITLE = "START"
RESTART_ACK_TITLE = "START_ACK"

PUT_REQUEST_TITLE = "PUT_REQUEST"
GET_REQUEST_TITLE = "GET_REQUEST"
DELETE_REQUEST_TITLE = "DELETE_REQUEST"
PUT_ACK_TITLE = "PUT_ACK"
GET_RESPONSE_TITLE = "GET_REPONSE"
DELETE_ACK_TITLE = "DELETE_ACK"

BREAK_CONNECTION_TITLE = "BREAK_CONNECTION"
RESTORE_CONNECTION_TITLE = "RESTORE_CONNECTION"
PRINT_LOG_TITLE = "PRINT_LOG"
PRINT_LOG_RESPONSE_TITLE = "PRINT_LOG_RESPONSE"

SEND_WRITE_TITLE = "SEND_WRITE"
VERSION_VECTOR_RESPONSE_TITLE = "VERSION_VECTOR_REQUEST"
VERSION_VECTOR_REQUEST_TITLE = "VERSION_VECTOR_RESPONSE"


RETIRE_REQUEST_TITLE = "PLEASE_RETIRE"

# CONTENT
EMPTY_CONTENT = "EMPTY"
ERR_KEY = "ERR_KEY"
