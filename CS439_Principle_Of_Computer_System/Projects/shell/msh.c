/**##############################################################
## MODULE: msh.c
## VERSION: 1.0 
## SINCE: 2013-09-14
## AUTHOR: 
##     Jimmy Lin (xl5224, loginID: jimmylin) - JimmyLin@utexas.edu  
##     Bochao Zhan (bz2892, loginID: bzchao) - bzhan927@gmail.com
## DESCRIPTION: 
##     A mini shell program with more complex job control
## 
#################################################################
## Edited by MacVim
## Class Info auto-generated by Snippet 
################################################################*/

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <ctype.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <errno.h>
#include "util.h"
#include "jobs.h"


/* Global variables */
int verbose = 0;            /* if true, print additional output */

extern char **environ;      /* defined in libc */
static char prompt[] = "msh> ";    /* command line prompt (DO NOT CHANGE) */
static struct job_t jobs[MAXJOBS]; /* The job list */
int MAX_NUM_ARGS = 10;
sigset_t sig_child;
/* End global variables */


/* Function prototypes */

/* Here are the functions that you will implement */
void eval(char *cmdline);
int builtin_cmd(char **argv);
void do_bgfg(char **argv);
void waitfg(pid_t pid);

void sigchld_handler(int sig);
void sigtstp_handler(int sig);
void sigint_handler(int sig);

/* Here are functions implemented for high-level abstraction */

/* Here are helper routines that we've provided for you */
void usage(void);
void sigquit_handler(int sig);



/*
 * main - The shell's main routine 
 */
int main(int argc, char **argv) 
{
    // test code for pid and pgid detection
    // printf("pid: %d, pgid:%d\n", getpid(), getpgid(getpid()));
    char c;
    char cmdline[MAXLINE];
    int emit_prompt = 1; /* emit prompt (default) */

    /* Redirect stderr to stdout (so that driver will get all output
     * on the pipe connected to stdout) */
    dup2(1, 2);

    /* Parse the command line */
    while ((c = getopt(argc, argv, "hvp")) != EOF) {
        switch (c) {
            case 'h':             /* print help message */
                usage();
                break;
            case 'v':             /* emit additional diagnostic info */
                verbose = 1;
                break;
            case 'p':             /* don't print a prompt */
                emit_prompt = 0;  /* handy for automatic testing */
                break;
            default:
                usage();
        }
    }

    /* Install the signal handlers */

    /* These are the ones you will need to implement */
    Signal(SIGINT,  sigint_handler);   /* ctrl-c */
    Signal(SIGTSTP, sigtstp_handler);  /* ctrl-z */
    Signal(SIGCHLD, sigchld_handler);  /* Terminated or stopped child */

    /* This one provides a clean way to kill the shell */
    Signal(SIGQUIT, sigquit_handler); 

    /* Initialize the job list */
    initjobs(jobs);
    sigemptyset (&sig_child);
    sigaddset(&sig_child, SIGCHLD);

    /* Execute the shell's read/eval loop */
    while (1) {

        /* Read command line */
        if (emit_prompt) {
            printf("%s", prompt);
            fflush(stdout);
        }
        if ((fgets(cmdline, MAXLINE, stdin) == NULL) && ferror(stdin))
            app_error("fgets error");
        if (feof(stdin)) { /* End of file (ctrl-d) */
            fflush(stdout);
            exit(0);
        }

        /* Evaluate the command line */
        eval(cmdline);
        fflush(stdout);
        fflush(stdout);
    } 

    exit(0); /* control never reaches here */
}

/* 
 * eval - Evaluate the command line that the user has just typed in
 * 
 * If the user has requested a built-in command (quit, jobs, bg or fg)
 * then execute it immediately. Otherwise, fork a child process and
 * run the job in the context of the child. If the job is running in
 * the foreground, wait for it to terminate and then return.  Note:
 * each child process must have a unique process group ID so that our
 * background children don't receive SIGINT (SIGTSTP) from the kernel
 * when we type ctrl-c (ctrl-z) at the keyboard.  
 */
void eval(char *cmdline) 
{
    // It's Jimmy driving
    int foreground;
    char * ampersand;
    // Examine the bg/fg indicator: ampersand
    if ((ampersand = strchr(cmdline, '&')) == NULL) {
        foreground = 1;
    } else {
        foreground = 0;
        *ampersand = 0;
    }
    // Initialiization of execution information
    char * args[MAX_NUM_ARGS];
    // Set null to all element of args
    int i;  // index through args
    for (i = 0; i < MAX_NUM_ARGS; i ++) 
        args[i] = NULL;

    // configure the arguments
    i = 0;  // reset the index iterator
    char * delimiter = " \n";  // separator between command and arguments
    args[i++] = strtok(cmdline, delimiter);  // command or file name
    char * pch;  // temporary variable for one arg section
    // Bochao driving now, Jimmy modifies it
    while ((pch = strtok(NULL, delimiter)) != NULL) {
        // Exception handling: number limit of arguments
        if (!(i < MAX_NUM_ARGS)) {
            unix_error("Too many arguments..\n");
            break;
        }
        args[i++] = pch;
    }

    // Jimmy driving now, Bochao comments on it
    // Inspect whether it is a built-in command
    if (((pch = strtok(cmdline, delimiter)) != NULL)&&(!builtin_cmd(args))) {
        // It is a executable file
        // First of all, block the SIGCHILD signal
        sigprocmask(SIG_BLOCK, &sig_child, NULL);
        // Create the child process
        pid_t child = fork();

        if (child == 0) { // Child process
            // have its own pgid with exception handling
            if (setpgid(0, 0)) {
                unix_error("Setpgid Failure.\n");
                exit(-2);
            }
            // unblock the SIGCHLD signal
            sigprocmask(SIG_UNBLOCK, &sig_child, NULL);
            // execution with execvp() since it's convenient
            if (execvp(args[0], args) == -1) {
                printf("%s: Command not found.\n", *args);
                exit(-1);
            }
            // normal exit after execution
            exit(0);
        } else {
            // now it's Bochao driving
            // reconstruct the command with unnecessary character removed
            int i = 0;  // index iterating through the args
            char * simCommand;  // the reconstructed command
            char * temp;  // append whitespace
            temp = (char *) malloc (1000);
            *temp = ' ';
            simCommand = (char *) malloc (1000);
            while (args[i] != NULL) {
                simCommand = strcat(strcat(simCommand, temp), args[i++]);
            }
            // add a new job to job list
            int add = 0;
            if (!foreground) simCommand = strcat(simCommand, " &");
            add = addjob(jobs, child, foreground?FG:BG, simCommand);        
            if (!add) {  // exception handling
                unix_error("Add job exception.\n");
                exit(-1);
            } else if (!foreground) {
                // display the background job
                printf("[%d] (%d) %s\n", pid2jid(jobs, child), 
                        child, simCommand);
            }
            // NOTE that no information to be displayed for fg job
            sigprocmask(SIG_UNBLOCK, &sig_child, NULL);
            // parent process waits for the termination of foreground job
            if (foreground) waitfg(child);
        }
    }
    return;
}


/* 
 * builtin_cmd - If the user has typed a built-in command then execute
 *    it immediately.  
 * Return 1 if a builtin command was executed; return 0
 * if the argument passed in is *not* a builtin command.
 */
int builtin_cmd(char **argv) 
{
    // Jimmy's driving 
    // The quit command terminates the shell.
    if (strcmp(*argv, "quit") == 0) {
        exit(1); // user-specified quit 
    }

    // The jobs command lists all background jobs.
    else if (strcmp(*argv, "jobs") == 0) {
        // when command is invoked, there must be no foreground job,
        // thus, all jobs in the "jobs" array are background job.
        listjobs(jobs);
        return 1;
    }

    else if (strcmp(*argv, "fg") == 0 || strcmp(*argv, "bg") == 0) {
        do_bgfg(argv);     
        return 1;
    }

    return 0; // not a built-in command
}

/* 
 * do_bgfg - Execute the builtin bg and fg commands
 */
void do_bgfg(char **argv) 
{
    // Jimmy is driving now, Bochao redrive
    pid_t pid; int jid;
    struct job_t *job;

    if (*(argv+1) == NULL){  // no id input 
        printf("%s command requires PID or %%jobid argument\n", *argv);
        return ;
    }

    // manipulate the job argument
    char * job_argv = *(argv+1);

    if (*job_argv == '%') { // this is jid input
        if (!isdigit(*(job_argv+1))){  // non-numeric input detection
            printf("%s: argument must be PID or %%jobid\n", *argv);
            return ;
        }

        jid = atoi(strtok(job_argv, "%\n"));  // the numeric id value
        job = getjobjid(jobs, jid);
        if (job != NULL)
            pid = job->pid;
        else {  // cannot find specified job
            printf("%%%d: No such Job.\n", jid);   
            return ;
        }
    } else { // this is pid input
        if (!isdigit(*job_argv)){  // non-numeric input detection
            printf("%s: argument must be PID or %%jobid\n", *argv);
            return ;
        }

        pid = atoi(job_argv);
        job = getjobpid(jobs, pid);
        if (job == NULL) {  // cannot find specified process
            printf("(%d): No such process.\n", pid);   
            return ;
        }
    }

    // Bochao's driving, modifies jimmy's work
    if (kill(-1*(job->pid), SIGCONT)) 
        unix_error("Signal Delivery error..\n");
        
    // This bg command starts job in the background.
    if(strcmp(*argv, "fg") == 0) { // This fg command starts job in the foreground.
        job->state = FG;
        waitfg(job->pid);
    } else if (strcmp(*argv, "bg") == 0) {
        job->state = BG;        
        printf("[%d] (%d) %s\n", jid, pid, job->cmdline);
    } 
    return;
}


/* 
 * waitfg - Block until process pid is no longer the foreground process
 */
void waitfg(pid_t pid)
{
    // Bochao is driving
    int i=0;
    while(i<MAXJOBS){
        for(i=0;i<MAXJOBS;i++){
            if(jobs[i].state==FG && jobs[i].pid==pid)break;	
        }
    }
    return;
}

/*****************
 * Signal handlers
 *****************/

/* 
 * sigchld_handler - The kernel sends a SIGCHLD to the shell whenever
 *     a child job terminates (becomes a zombie), or stops because it
 *     received a SIGSTOP or SIGTSTP signal. The handler reaps all
 *     available zombie children, but doesn't wait for any other
 *     currently running children to terminate.  
 */
void sigchld_handler(int sig) 
{
    // Bochao is driving
    // Jimmy redrived
    int pid, status, i;
    while((pid = waitpid(-1, &status, WUNTRACED|WNOHANG)) > 0){
        if(WIFSTOPPED(status)) {
            for(i = 0; i < MAXJOBS; i++) {
                if(jobs[i].pid == pid) {
                    printf("Job [%d] (%d) stopped by signal %d \n", 
                            jobs[i].jid, jobs[i].pid, SIGTSTP);
                    jobs[i].state=ST;
                    kill(-1*jobs[i].pid,SIGTSTP);
                    break;
                }
            }
        }
        else if(WIFSIGNALED(status)){
            for( i = 0; i < MAXJOBS; i++){
                if(jobs[i].pid == pid){
                    printf("Job [%d] (%d) terminated by signal %d \n", 
                            jobs[i].jid, jobs[i].pid, SIGINT);
                    deletejob(jobs, jobs[i].pid);
                    kill(jobs[i].pid,SIGINT);
                    break;
                }
            }
        }
        else{ 
            for(i = 0; i < MAXJOBS; i++){
                if(jobs[i].pid == pid){ 
                    deletejob(jobs, jobs[i].pid); 
                    break;
                }
            }
        }
    }
}

/* 
 * sigint_handler - The kernel sends a SIGINT to the shell whenver the
 *    user types ctrl-c at the keyboard.  Catch it and send it along
 *    to the foreground job.  
 */
void sigint_handler(int sig) 
{
    // Jimmy is driving
    int i;  // index iterating through jobs

    // print information to screen
    for (i = 0; i < MAXJOBS; i ++) {
        if (jobs[i].state == FG) {
            // send the signal SIGINT to foreground job
            if (kill(-1*jobs[i].pid, SIGINT)) {
                // error handling
                unix_error("\n Signal Delivery Failure. \n");  
                exit(-3);
            }
            // feedback if signal delivery succeed
            printf("Job [%d] (%d) terminated by signal %d \n", 
                    jobs[i].jid, jobs[i].pid, SIGINT);
            // remove the job from jobs array
            deletejob(jobs, jobs[i].pid);
        }
    }
    return;
}

/*
 * sigtstp_handler - The kernel sends a SIGTSTP to the shell whenever
 *     the user types ctrl-z at the keyboard. Catch it and suspend the
 *     foreground job by sending it a SIGTSTP.    
 */
void sigtstp_handler(int sig) 
{
    // Jimmy is driving
    // Bochao is redriving
    int i;  // index as above
    // print information to screen
    for (i = 0; i < MAXJOBS; i ++) {
        // only for the foreground job
        if (jobs[i].state == FG) {
            jobs[i].state=ST;  // change the state to stop
            // send the signal SIGINT to foreground job
            if (kill(-1*jobs[i].pid, SIGTSTP)) {
                // error handling
                printf("\n Signal Delivery Failure. \n");  
                exit(-3);
            }
            // remove the job from jobs array
            printf("Job [%d] (%d) stopped by signal %d \n", 
                    jobs[i].jid, jobs[i].pid, SIGTSTP);
        }
    }
    return;
}

/*********************
 * End signal handlers
 *********************/



/***********************
 * Other helper routines
 ***********************/

/*
 * usage - print a help message
 */
void usage(void) 
{
    printf("Usage: shell [-hvp]\n");
    printf("   -h   print this message\n");
    printf("   -v   print additional diagnostic information\n");
    printf("   -p   do not emit a command prompt\n");
    exit(1);
}

/*
 * sigquit_handler - The driver program can gracefully terminate the
 *    child shell by sending it a SIGQUIT signal.
 */
void sigquit_handler(int sig) 
{
    printf("Terminating after receipt of SIGQUIT signal\n");
    exit(1);
}