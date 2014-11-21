/* This file is licensed under CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).  
 *
 * Created 2014-11-20 by bastel.
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>


#ifdef WIN32
/*#	define FD_SETSIZE	1024*/
#	include <winsock2.h>
#	pragma comment(lib, "Ws2_32.lib")
#	define sleep(A) _sleep(1000 * A)
#	define snprintf sprintf_s
#endif


#include "s400w.h"


static const char* name = "";
static FILE* file = NULL;
	
static int preview(const char* data, int offset, int length)
{
	if ( data==SEOF ) {
		if ( file ) fclose(file);
		file = NULL;
	} else {
		if ( !file ) {
			file = fopen(name, "wb");
			return file ? 1 : 0;
		}
		fwrite(data + offset, length, 1, file);
	}
	return 1;
}


static int jpeg(const char* data, int offset, int length)
{
	if ( data==JPEG_SIZE ) {
		file = fopen(name, "wb");
		return file ? 1 : 0;
	}
	if ( data==SEOF ) {
		if ( file ) fclose(file);
		file = NULL;
	} else {
		fwrite(data + offset, length, 1, file);
	}
	return 1;
}


static int init()
{
#ifdef WIN32
	WORD wVersionRequested = MAKEWORD(1, 1);
	WSADATA	wsaData;
 	int err = WSAStartup(wVersionRequested, &wsaData);
	if ( err != 0 ) {
		fprintf(stderr, "main(): Error: Couldn't init winsock\n");
		return -1;
	}
#endif
	return 0;
}

static int cleanup()
{
#ifdef WIN32
	WSACleanup();
#endif
	return 0;
}


static int process(int argc, char *argv[])
{
	const char* host = argv[1];
	const int port = atoi(argv[2]);
	const char* cmd = argv[3];
	const char** ev = argv + 4;
	const int ec = argc - 4;
	const char* buf;
	S400W_DEBUG = printf;
	init();

	if ( !strcmp("version", cmd) ) {
		buf = getVersion(host, port);
		if ( buf==NULL || buf==SEOF || isKnownResponse(buf) ) return -1;
		printf("%s\n", buf);
	}
		
	else if ( !strcmp("status", cmd) ) {
		while ( 1 ) {
			buf = getStatus(host, port);
			if ( buf==NULL || buf==SEOF ) return -1;
			printf("%s\n", buf);
			sleep(5);
		}
	}
		
	else if ( !strcmp("dpi", cmd) ) {
		if ( ec>0 ) {
			if ( strcmp("300", ev[0]) ) return setResolution(host, port, 300) ? 0 : -1;
			if ( strcmp("600", ev[0]) ) return setResolution(host, port, 600) ? 0 : -1;
		}
		return -1;
	}

	else if ( !strcmp("clean", cmd) ) {
		buf = clean(host, port);
		if ( buf!=CLEAN_END ) return -1;
		printf("%s\n", buf);
	}
		
	else if ( !strcmp("calibrate", cmd) ) {
		buf = calibrate(host, port);
		if ( buf!=CALIBRATE_END ) return -1;
		printf("%s\n", buf);
	}

	else if ( !strcmp("preview", cmd) ) {
		char temp[30];
		snprintf(temp, 30, "./%d.raw", time(NULL));
		name = ec>0 ? ev[0] : temp;
		buf = scan(host, port, 0, preview, NULL);
		printf("%s\n", buf);
	}
			
	else if ( !strcmp("scan", cmd) ) {
		const int dpi  = ec<1 ? 0 : !strcmp("dpi300", ev[0]) ? 300 : !strcmp("dpi600", ev[0]) ? 600 : 0;
		char temp[30];
		snprintf(temp, 30, "./%d.jpg", time(NULL));
		name = ec>1 ? ev[1] : ec>0 ? ev[0] : temp;
		buf = scan(host, port, dpi, NULL, jpeg);
		printf("%s\n", buf);
	}
	return 0;
}


int main(int argc, char *argv[])
{
	int ret = 0;
	if ( argc<4 ) {
		printf("usage : %s <host> <port> <command> <options>\n"
			"command:\n"
			"  version\n"
			"  status\n"
			"  clean - use the cleaning sheet\n"
			"  calibrate - use the calibration sheet\n"
			"  dpi <300 | 600>\n"
			"  preview [filename]\n"
			"  scan [filename]\n"
			"  scan <dpi300 | dpi600> <filename>\n"
			, argv[0]);
		return -1;
	}
	init();
	ret = process(argc, argv);
	cleanup();
	return ret;
}
