/*
 *   
 *   Copyright 2011-2013, bechter.com - All Rights Reserved
 *   
 *      1. All files, software, schematics and designs are provided as-is with no warranty.
 *      2. All files, software, schematics and designs are for experimental/hobby use. 
 *         Under no circumstances should any part be used for critical systems where safety, 
 *         life or property depends upon it. You are responsible for all use.
 *      3. You are free to use, modify, derive or otherwise extend for your own non-commercial purposes provided
 *         1. No part of this software or design may be used to cause injury or death to humans or animals.
 *         2. Use is non-commercial.
 *         3. Credit is given to the author (i.e. portions © bechter.com), 
 *            and provide a link to this site (http://projects.bechter.com).
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <signal.h>
#include <ctype.h>
#include <usb.h>
#include <time.h>
#include <signal.h>
#include <sys/param.h>
#include <utime.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <syslog.h>


#include "wxrd.h"

struct usb_dev_handle *devh;
char *progname;
char error[80];
int *current = 0;
int opt_d = 0;
int opt_l = 0;
FILE *logfile;


void release_usb_device() {
    if (opt_d)
        printf("release_usb_device");
    int ret;
    ret = usb_release_interface(devh, 0);
    if (!ret)
        fprintf(stderr, "failed to release interface: %d\n", ret);
    usb_close(devh);
    if (!ret)
        fprintf(stderr, "failed to close interface: %d\n", ret);
}

void release_and_terminate() {
    release_usb_device();
    if (opt_l)
        fclose(logfile);
    exit(1);
}

struct usb_device *find_device(int vendor, int product) {
    struct usb_bus *bus;
    
    for (bus = usb_get_busses(); bus; bus = bus->next) {
        struct usb_device *dev;
    
        for (dev = bus->devices; dev; dev = dev->next) {
                if (dev->descriptor.idVendor == vendor
                    && dev->descriptor.idProduct == product)
            return dev;
        }
    }
    return NULL;
}

void print_bytes(unsigned char *bytes, int len) {
    int i;
    
    for (i = 0; i < len; i++)
        printf("%02x ", (int)((unsigned char)bytes[i]));
    printf("\"");
    for (i = 0; i < len; i++) 
        printf("%c", isprint(bytes[i]) ? bytes[i] : '.');
    printf("\"\n");
    fflush(stdout);
}

int send_cmd(unsigned char *cmd, unsigned char *buf) { 
    int ret;
        memcpy(buf, cmd, 8);
    ret = usb_control_msg(devh, USB_TYPE_CLASS + USB_RECIP_INTERFACE, 0x0000009, 0x0000200, 0x0000000, 
            (char *)buf, 0x0000008, 1000);
    if (ret > 0 && cmd[1] != 0x86) {
            usleep(U_DELAY);
            ret = usb_interrupt_read(devh, 0x00000081, (char *)buf, 0x0000008, 1000);
            if (ret > 0 && opt_d) 
                print_bytes(buf, 8);
        }
        usleep(U_DELAY);
        return ret;
}

void save_message(raw_receive_t *raw, unsigned char *msg) {
    if (opt_d)
        printf("save_message\n");
    if (raw->index < MAX_RAW - 1) {
        if (memcmp(raw->data[raw->index], msg, 8) != 0) {
            memcpy(raw->data[++(raw->index)], msg, 8);
            if (opt_d)
                print_bytes(msg, 8);
        }
    }
}

void new_message(raw_receive_t *raw, unsigned char *msg) {
    if (opt_d)
        printf("new_message\n");
    time(&(raw->time));
    raw->index = 0;
    memcpy(raw->data[0], msg, 8);
    if (opt_d)
        print_bytes(msg, 8);
}

void device_setup() {
    struct usb_device *dev;
    int ret;
    unsigned char buf[512];

    usb_init();
    usb_set_debug(opt_d * 255);
    usb_find_busses();
    usb_find_devices();

    dev = find_device(VENDOR, PRODUCT);
    assert(dev);

    devh = usb_open(dev);
    assert(devh);
    
    signal(SIGTERM, release_and_terminate);

    ret = usb_claim_interface(devh, 0);
    if (ret != 0) {
        fprintf(stderr, "claim failed with error %d\n", ret);
        exit(1);
    }
    
    ret = usb_set_altinterface(devh, 0);
    assert(ret >= 0);

    ret = usb_get_descriptor(devh, 0x0000001, 0x0000000, buf, 0x0000012);
    if (opt_d) {
        printf("1 get descriptor returned %d, bytes: ", ret);
        print_bytes(buf, ret);
        printf("\n");
    }
    ret = usb_get_descriptor(devh, 0x0000002, 0x0000000, buf, 0x0000009);
    if (opt_d) {
        printf("2 get descriptor returned %d, bytes: ", ret);
        print_bytes(buf, ret);
        printf("\n");
    }
    usleep(5000);
    ret = usb_get_descriptor(devh, 0x0000002, 0x0000000, buf, 0x0000022);
    if (opt_d) {
        printf("3 get descriptor returned %d, bytes: ", ret);
        print_bytes(buf, ret);
        printf("\n");
    }
    usleep(80000);
    ret = usb_release_interface(devh, 0);
    if (ret != 0) 
        fprintf(stderr, "failed to release interface before set_configuration: %d\n", ret);
    ret = usb_set_configuration(devh, 0x0000001);
    if (opt_d) {
        printf("4 set configuration returned %d\n", ret);
    }
    ret = usb_claim_interface(devh, 0);
    if (ret != 0) 
        fprintf(stderr, "claim after set_configuration failed with error %d\n", ret);
    ret = usb_set_altinterface(devh, 0);
    if (opt_d) {
        printf("4 set alternate setting returned %d\n", ret);
    }
}

int is_complete(unsigned char *msg) {
    return memcmp("\xab\x0a\x04", msg, 3) == 0 || memcmp("\xab\x84\x00", msg, 3) == 0;
}

int is_timeout(raw_receive_t *raw) {
    time_t now;
    
    time(&now);
    return difftime(now, raw->time) > MESSAGE_TIMEOUT;
}

int is_start(unsigned char *msg) {
    return memcmp("WXR", msg + 3, 3) == 0 || memcmp("EAS", msg + 3, 3) == 0
            || memcmp("CIV", msg + 3, 3) == 0 || memcmp("PEP", msg + 3, 3) == 0;
}

void parse_message(char *report, raw_receive_t *raw) {
    int i, len;
    char origin[4];
    char event[4];
    char areas[256];
    char area[7];
    unsigned char *msg;
    int hours;
    int minutes;
    struct tm *start_time;
    
    if (opt_d)
        printf("parse_message\n");
    *areas = '\x0';
    *event = '\x0';
    hours = 0;
    minutes = 32;
    len = (raw->index) + 1;
    if (len > 0 && len <= MAX_RAW) {
        for (i = 0; i < len; i++) {
            msg = raw->data[i];
            if (msg[1] == 4) {
                memcpy(origin, msg+3, 3);
                origin[3] = '\x0';
            }
            else if (msg[1] == 5) {
                memcpy(event, msg+3, 3);
                event[3] = '\x0';
            }
            else if (msg[1] >= 16 && msg[1] < 128) {
                sprintf(area, "%02x%02x%02x", (int)msg[3], (int)msg[4], (int)msg[5]);
                if (strlen(areas) > 0)
                    strcat(areas, "-");
                strcat(areas, area);
            }
            else if (msg[1] == 7) {
                hours = (int)msg[3];
                minutes = (int)msg[4];
            }
        }
        start_time = localtime(&(raw->time));
        sprintf(report, "%s %s %s %04d%02d%02dT%02d%02d%02d+%02X%02X\n", origin, event, areas,
                start_time->tm_year + 1900, start_time->tm_mon + 1, start_time->tm_mday,
                start_time->tm_hour, start_time->tm_min, start_time->tm_sec,
                hours, minutes);
    }
}

void term_handler(int sig) {        
    if (opt_d)
        printf("exit: sig term\n");
      release_usb_device();
      exit(1);
}

void hup_handler(int sig) {        
    if (opt_d)
        printf("exit: sig hup\n");
      release_usb_device();
      exit(1);
}

void pipe_handler(int sig) {        
    if (opt_d)
        printf("sig pipe\n");
    signal(SIGPIPE, SIG_IGN);
    if (sig == SIGPIPE && *current >= 0) {
        shutdown(*current, 2);
        close(*current);
        if (opt_d) {
            printf("Client disconnected, socket id = %d\n", *current);
            fflush(stdout);
        }
        *current = -1;
      }
}

#ifdef KEEPALIVE_PING
void ping_clients(int *fd) {
    int i;
    for (i = 0; i < MAX_CONNECTIONS; i++) {
        current = fd + i;
        if (*current > 0) {
            signal(SIGPIPE, pipe_handler);
            write(*current, "*\n", 2);
        }
    }
}
#endif

void send_to_clients(int *fd, char *report) {
    if (opt_d)
        printf("send_to_clients\n");
    int i;
    if (opt_l) {
        fprintf(logfile, "%s\n", report);
        fflush(logfile);
    }
    for (i = 0; i < MAX_CONNECTIONS; i++) {
        current = fd + i;
        if (*current > 0) {
            signal(SIGPIPE, pipe_handler);
            write(*current, report, strlen(report));
        }
    }
}

void usage() {
  printf("usage: %s [-d] [-p] [-l logfile]\n", progname );
  printf("  -h    show this help and exit\n -d debug; don't fork to be a daemon\n -p port\n -l logfile\n");
  exit(0);
}

int main(int argc, char **argv) {
    int sd, pid, s, clen = sizeof(struct sockaddr_in), fd[MAX_CONNECTIONS];
    int *max = 0, afile = -1, bfile = -1, /* mfile = -1, */ c, first = 1;
    int opt_p = DEFAULT_PORT;
    int i;
    struct sockaddr_in server, client;
    struct in_addr bind_address;
    fd_set rfds;
    int mode;
    int ret;
    int cmd_id;
    unsigned char result[8];
    unsigned char buf[8];
    unsigned char *cmds[MAX_CMDS] = {
        "\xab\x80\x00\x2b\x00\x00\x00\x6a",
        "\xab\x80\x00\x2b\x00\x00\x00\x6a",
        "\xab\x84\x00\x2f\x00\x00\x00\x6a",
        "\xab\x86\x00\x31\x00\x00\x00\x6a"
    };
    unsigned char *start_cmd = "\xab\xb4\x04\x76\x31\x30\x30\x6a";
    char *notice[] = {
        "WXRD v0.2 - (C) 2006, bechter.com\n",
        "  All rights reserved.\n"
    };
    raw_receive_t raw;
    report_t reports[10];
    char report[500];
    struct timeval tv;
    struct utimbuf ut;
    char *logfile_name;
 
    for (i = 0; i < MAX_CONNECTIONS; i++) 
        fd[i] = -1;
    tv.tv_sec = 0;
    tv.tv_usec = 0;
    progname = strrchr(argv[0], '/');
    if (progname == NULL)
        progname = argv[0];
    else
        progname++;
    while ((c = getopt(argc, argv, "Hhdp:l:")) != EOF) {
        switch(c) {
            case '?':
            case 'h':
            case 'H':            /* help */
                usage();
                break;
            case 'd':            /* do not fork and become a daemon */
                opt_d = 1;
                break;
            case 'p':            /* port to use */
                opt_p = strtol(optarg, NULL, 0);
                break;
            case 'l':
                opt_l = 1;
                logfile_name = optarg;
                break;
        }
    }

    server.sin_family = AF_INET;
    bind_address.s_addr = htonl(INADDR_ANY);
    server.sin_addr = bind_address;
    server.sin_port = htons(opt_p);

    if ((s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)) == -1) {
        sprintf(error, "%s: socket", progname);
        perror(error);
    }


    i = 1;
    if (setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &i, sizeof(i)) == -1) {
        sprintf(error, "%s: setsockopt", progname);
        perror(error);
    }

    if (bind(s, (struct sockaddr *)&server, sizeof(struct sockaddr_in)) == -1) {
        sprintf(error, "%s: bind", progname);
        perror(error);
        exit(11);
    }
      
    if (listen(s, MAX_CONNECTIONS) == -1) {
        sprintf(error, "%s: listen", progname);
        perror(error);
        exit(12);
    }

    device_setup();

    for (i = 0; i < MAX_CONNECTIONS; i++) fd[i] = -1;

    openlog(progname, LOG_PID | (opt_d ? LOG_PERROR : 0), LOG_LOCAL4);

    if (!opt_d) {
        if ((pid = fork()) == -1) {
            syslog(LOG_ERR, "can't fork() to become daemon: %m");
            exit(20);
        } else if (pid)
            exit (0);
        setsid();
        for (i = 0; i < NOFILE; i++)
              if (i != s) 
                  close(i);
        i = open("/dev/null", O_RDWR);
        dup(i);
        dup(i);
    }
      
    if (opt_l) {
          logfile = fopen(logfile_name, "w");
          if (logfile <= 0)
              opt_l = 0;
    }

    signal( SIGTERM, term_handler );
#if HAVE_SIGPWR
    signal( SIGPWR, term_handler );
#endif
    signal( SIGHUP, hup_handler );


    mode = MESSAGE_WAIT;
    cmd_id = 0;
    send_cmd(start_cmd, result);
    time_t ping_time;
    time(&ping_time);
    while (1) {
        usleep(200 * U_DELAY);
        
        if (mode == MESSAGE_STARTED) {
            for (i = 0; i < CMD_COUNT; i++) {
                ret = send_cmd(cmds[i], result);
                if (i == cmd_id) {
                    save_message(&raw, result);
                    if (is_complete(result) || is_timeout(&raw)) {
                        mode = MESSAGE_COMPLETE;
                        if (opt_d) {
                            printf("**** Message Complete\n");
                            fflush(stdout);
                        }
                    }
                }
            }
        } else if (mode == MESSAGE_WAIT) {
            for (i = 0; i < CMD_COUNT; i++) {
                ret = send_cmd(cmds[i], result);
                if (ret > 0) {
                    if (is_start(result) && mode == MESSAGE_WAIT) {
                        cmd_id = i;
                        mode = MESSAGE_STARTED;
                        new_message(&raw, result);
                        if (opt_d) {
                            printf("**** Message Started\n");
                            fflush(stdout);
                        }
                    }
                }
            }    
        }
        else if (mode == MESSAGE_COMPLETE) {
            parse_message(report, &raw);
            if (opt_l)
                fprintf(logfile, "%s", report);
            send_to_clients(fd, report);
            mode = MESSAGE_WAIT;
            if (opt_d) {
                printf("Sending report '%s' to clients\n", report);
                fflush(stdout);
            }
        }

        if (mode == MESSAGE_WAIT) {
            FD_ZERO(&rfds);
            FD_SET(s, &rfds);
            if (select(s + 1, &rfds, NULL, NULL, &tv) > 0) {
                  for (current = fd;
                           (*current > 0) && (current < fd + MAX_CONNECTIONS - 1); current++);
                  if (current > max) 
                      max = current;
                  if ((*current = accept(s, (struct sockaddr *)&client, &clen)) != -1) {
                      write(*current, notice[0], strlen(notice[0]));
                      write(*current, notice[1], strlen(notice[1]));
                      if (opt_d) {
                          printf("Accepted connection from client, socket id=%d\n", *current);
                          fflush(stdout);
                      }
                }
            }
#ifdef KEEPALIVE_PING
            time_t now;
            time(&now);
            if (difftime(now, ping_time) > PING_INTERVAL) {
                 ping_clients(fd);
                 ping_time = now;
            }
#endif
            usleep(U_DELAY);
        }
    }
}
