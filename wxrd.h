#define MESSAGE_DELAY 75000
#define VENDOR 0x2233
#define PRODUCT 0x6322

#define DEFAULT_PORT 9317

#define MAX_CONNECTIONS 5

#define MESSAGE_WAIT 0
#define MESSAGE_STARTED 1
#define MESSAGE_COMPLETE 2

#define U_DELAY 4000

#define CMD_COUNT 4
#define MAX_RAW 100
#define MESSAGE_TIMEOUT 30
#define MAX_CMDS 4
#define PING_INTERVAL 10
#define KEEPALIVE_PING

struct raw_receive {
	unsigned char data[MAX_RAW][8];
	int index;
	time_t time;
};
typedef struct raw_receive raw_receive_t;

struct report {
	time_t expires;
	char message[500];
};
typedef struct report report_t;
