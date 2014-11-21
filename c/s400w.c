/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).
 *
 * Created 2014-11-16 by bastel.
 *
 * See s400w.h for further information.
 *
 * @author bastel
 */

#ifdef WIN32
// needs lib: ws2_32.lib
//#	define FD_SETSIZE	1024
#	include <winsock2.h>
#	pragma comment(lib, "Ws2_32.lib")
#	include <stdlib.h>
#	include <stdio.h>
#	include <string.h>
#else
#	include <stdlib.h>
#	include <stdio.h>
#	include <string.h>
#	include <sys/socket.h>
#	include <sys/select.h>
#	include <netdb.h>
#	include <unistd.h>
#	define closesocket close
#endif

#include <fcntl.h>
#include <errno.h>

#include "s400w.h"

int (*S400W_DEBUG)(const char* format, ...) = NULL;

const char* S400W_LIB_VERSION = "20141116";

const int MIN_SET_RESOLUTION_FW = 26;

// Responses (note that no response starts with another response, so no terminating character necessary)
const char DEVICE_BUSY[]   = { 'd','e','v','b','u','s','y',0 };
const char BATTERY_LOW[]   = { 'b','a','t','t','l','o','w',0 };
const char NOPAPER[]       = { 'n','o','p','a','p','e','r',0 };
const char SCAN_READY[]    = { 's','c','a','n','r','e','a','d','y',0 };
const char CALIBRATE_GO[]  = { 'c','a','l','g','o',0 };
const char CALIBRATE_END[] = { 'c','a','l','i','b','r','a','t','e',0 };
const char CLEAN_GO[]      = { 'c','l','e','a','n','g','o',0 };
const char CLEAN_END[]     = { 'c','l','e','a','n','e','n','d',0 };
const char DPI_STANDARD[]  = { 'd','p','i','s','t','d',0 };
const char DPI_HIGH[]      = { 'd','p','i','f','i','n','e',0 };
const char SCAN_GO[]       = { 's','c','a','n','g','o',0 };
const char PREVIEW_END[]   = { 'p','r','e','v','i','e','w','e','n','d',0 };
const char JPEG_SIZE[]     = { 'j','p','e','g','s','i','z','e',0 };
const char SEOF[]          = { 0 };
const char SERR[]          = { 0 };



/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Private /
////////////

static const char* RESPONSES[] = {
	DEVICE_BUSY,
	BATTERY_LOW,
	NOPAPER,
	SCAN_READY,
	CALIBRATE_GO,
	CALIBRATE_END,
	CLEAN_GO,
	CLEAN_END,
	DPI_STANDARD,
	DPI_HIGH,
	SCAN_GO,
	PREVIEW_END,
	JPEG_SIZE,
	NULL
};

static const char GET_VERSION[]       = {  0x30,  0x30, 0x20,  0x20 };
static const char GET_STATUS[]        = {  0x00,  0x60, 0x00,  0x50 };
static const char START_CLEANING[]    = { -0x80, -0x80, 0x70,  0x70 };
static const char START_CALIBRATION[] = {  0x00, -0x50, 0x00, -0x60 };
static const char SET_DPI_STANDARD[]  = {  0x40,  0x30, 0x20,  0x10 };
static const char SET_DPI_HIGH[]      = { -0x80,  0x70, 0x60,  0x50 };
static const char START_SCAN[]        = {  0x00,  0x20, 0x00,  0x10 };
static const char SEND_PREVIEW_DATA[] = {  0x40,  0x40, 0x30,  0x30 };
static const char GET_JPEG_SIZE[]     = {  0x00, -0x30, 0x00, -0x40 };
static const char SEND_JPEG_DATA[]    = {  0x00, -0x10, 0x00, -0x20 };

/** Internal receive buffer, default responses should fit in 16 bytes */
static char _buffer[16];


/** Internal big receive buffer, size arbitrarily chosen to be 16 preview lines + endmarker + padding byte */
static char _bigBuffer[30720 + sizeof(PREVIEW_END)];


/** Default timeout, sufficient for simple calls that don't start things. */
static const long _timeout = 10000L;


static void mssleep(long ms)
{
	//struct timespec tv;
	//tv.tv_sec  = ms / 1000;
	//tv.tv_nsec = ms % 1000 * 1000000L;
	//nanosleep(tv);
	struct timeval tv;
	tv.tv_sec  = ms / 1000;
	tv.tv_usec = ms % 1000 * 1000;
	select(0, NULL, NULL, NULL, &tv);
}


static int openSocket(const char* hostname, int port)
{
#ifdef WIN32
	unsigned long u1 = 1;
#endif
	struct hostent* hp;    // host name lookup
	struct sockaddr_in sa; // socket addr. structure
	int fd;                // stream file descriptor

	hp = gethostbyname(hostname);
	if ( !hp ) {
		fprintf(stderr, "openSocket(): Error: Host not found: %s\n", hostname);
		return -1;
	}

	// fill in the "sockaddr_in" structure with some iformation from "hostent" structure.
	sa.sin_family   = hp->h_addrtype;
	sa.sin_port     = htons(port);
	memcpy((void*)&sa.sin_addr, (void*)hp->h_addr, hp->h_length);

	// open a TCP/STREAM socket
	fd = socket(hp->h_addrtype, SOCK_STREAM, 0);
	if ( fd<0 ) {
		fprintf(stderr, "openSocket(): Error: socket() failed!\n");
		return -1;
	}

	// setup connection to the remote server
	if ( connect(fd, (struct sockaddr*)&sa, sizeof(sa))<0 ) {
		fprintf(stderr, "openSocket(): Error: connect() failed!\n");
		return -1;
	}

	// set socket nonblocking
#ifdef WIN32
	if ( ioctlsocket(fd, FIONBIO, &u1) ) {
#else
	if ( fcntl(fd, F_SETFL, O_NONBLOCK)==-1 ) {
#endif
		fprintf(stderr, "openSocket(): Error: couldn't set to nonblocking!\n");
		return -1;
	}
	return fd;
}


// timeout in milliseconds
static int checkStream(int fd, long timeout)
{
	fd_set  rfds, efds;
	fd_set* rfdsp = &rfds;
	fd_set* efdsp = &efds;
	struct timeval tv;
	int ret;

	FD_ZERO(rfdsp);
	FD_ZERO(efdsp);
	FD_SET(fd, rfdsp);
	FD_SET(fd, efdsp);

	tv.tv_sec  = timeout / 1000;
	tv.tv_usec = timeout % 1000 * 1000;
	
	ret = select(fd + 1, rfdsp, NULL, efdsp, timeout == -1 ? 0 : &tv);
	return ret<=0 ? ret : (FD_ISSET(fd, rfdsp) ? 1 : 0) | (FD_ISSET(fd, efdsp) ? 2 : 0);
}



static int sendCommand(int fd, const char* command)
{
	int sent = fd ? send(fd, command, 4, 0) : -1;
	if ( S400W_DEBUG ) S400W_DEBUG("sendCommand(): %x: %d\n", *(unsigned int*)command, sizeof(command));
	if ( sent>0 ) mssleep(100);
	return sent;
}


static int recvResponse(int fd, char* buffer, int limit, long timeout)
{
	if ( fd>=0 ) {
		if ( checkStream(fd, timeout) & 1 ) {
			int read = recv(fd, buffer, limit, 0);
			// closed socket
			if ( read==0 ) read = -1;
			// check for errors
			else if ( read==-1 ) {
				int err = errno;
				if ( err==EAGAIN || err==EWOULDBLOCK ) read = 0; // shouldn't happen
			}
			return read;
		 }
		 return 0;
	}
	return -1;
}


static const char* detectResponse(const char* buffer, int length)
{
	const char** res;
	if ( length==0 ) return NULL;
	if ( length <0 ) return SEOF;
	for ( res = RESPONSES; *res; res++ ) {
		int len = strlen(*res);
		if ( len<=length && memcmp(buffer, *res, len)==0 ) return *res;
	}
	return buffer;
}


static const char* readResponse(int fd, long timeout)
{
	memset(_buffer, 0, sizeof(_buffer));
	return detectResponse(_buffer, recvResponse(fd, _buffer, sizeof(_buffer), timeout));
}	



/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Public /
///////////


int isKnownResponse(const char* response)
{
	const char** res;
	for ( res = RESPONSES; *res; res++ ) if ( *res==response ) return 1;
	return 0;
}


const char* getVersion(const char* host, int port)
{
	const char* response = SEOF;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		if ( sendCommand(fd, GET_VERSION)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			if ( S400W_DEBUG ) S400W_DEBUG("getVersion(): %s\n", response);
		}
		closesocket(fd);
	}
	return response;
}


const char* getStatus(const char* host, int port)
{
	const char* response = SEOF;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		if ( sendCommand(fd, GET_STATUS)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			if ( S400W_DEBUG ) S400W_DEBUG("getStatus(): %s\n", response);
		}
		closesocket(fd);
	}
	return response;
}


int setResolution(const char* host, int port, int dpi)
{
	int result = 0;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		if ( sendCommand(fd, dpi==600 ? SET_DPI_HIGH : SET_DPI_STANDARD)>0 ) {
			const char* response = readResponse(fd, _timeout);
			mssleep(200);
			if ( S400W_DEBUG ) S400W_DEBUG("setResolution(%d): %s\n", dpi, response);
			result = dpi==600 && response==DPI_HIGH || dpi!=600 && response==DPI_STANDARD ? 1 : 0;
		}
		closesocket(fd);
	}
	return result;
}


const char* clean(const char* host, int port)
{
	const char* response = SEOF;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		if ( sendCommand(fd, GET_STATUS)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			if ( S400W_DEBUG ) S400W_DEBUG("clean().check: %s\n", response);
			if ( response==SCAN_READY ) {
				sendCommand(fd, START_CLEANING);
				mssleep(500);
				response = readResponse(fd, _timeout);
				if ( S400W_DEBUG ) S400W_DEBUG("clean().go: %s\n", response);
				if ( response==CLEAN_GO ) {
					response = readResponse(fd, 30000);
					if ( S400W_DEBUG ) S400W_DEBUG("clean().end: %s\n", response);
				}
			}
		}
		closesocket(fd);
	}
	return response;
}


const char* calibrate(const char* host, int port)
{
	const char* response = SEOF;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		if ( sendCommand(fd, GET_STATUS)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			if ( S400W_DEBUG ) S400W_DEBUG("calibrate().check: %s\n", response);
			if ( response==SCAN_READY ) {
				sendCommand(fd, START_CALIBRATION);
				mssleep(500);
				response = readResponse(fd, _timeout);
				if ( S400W_DEBUG ) S400W_DEBUG("calibrate().go: %s\n", response);
				if ( response==CALIBRATE_GO ) {
					response = readResponse(fd, 60000);
					if ( S400W_DEBUG ) S400W_DEBUG("calibrate().end: %s\n", response);
				}
			}
		}
		closesocket(fd);
	}
	return response;
}


const char* scan(const char* host, int port, int resolution, receiveFunc previewFunc, receiveFunc jpegFunc)
{
	const char* response = SEOF;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		const long timeoutData = 30000;
		const long timeoutBoth = 20000;
		const long timeoutJpeg = 60000;
		const int  tagLength   = sizeof(PREVIEW_END);
		int abort = 0;
		
		if ( sendCommand(fd, GET_STATUS)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			if ( S400W_DEBUG ) S400W_DEBUG("scan().check: %s\n", response);
			if ( response!=SCAN_READY ) abort = 1; else mssleep(200);
		} else abort = 1;
			
		if ( !abort && resolution>0 ) {
			if ( sendCommand(fd, resolution==600 ? SET_DPI_HIGH : SET_DPI_STANDARD)>0 ) {
				mssleep(200);
				response = readResponse(fd, _timeout);
				if ( S400W_DEBUG ) S400W_DEBUG("scan().dpi(%d): %s\n", resolution, response);
				abort = resolution==600 && response==DPI_HIGH || resolution!=600 && response==DPI_STANDARD ? 0 : 1;
				if ( !abort ) mssleep(200);
			} else abort = 1;
		}
		
		if ( !abort && sendCommand(fd, START_SCAN)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			if ( S400W_DEBUG ) S400W_DEBUG("scan().go: %s\n", response);
			if ( response!=SCAN_GO ) abort = 1; else mssleep(200);
		} else abort = 1;

		if ( !abort && previewFunc ) {
			if ( sendCommand(fd, SEND_PREVIEW_DATA)>0 ) {
				char* partBuf = _bigBuffer + tagLength;
				int read, total = 0;

				mssleep(1000);
				read = recvResponse(fd, partBuf, sizeof(_bigBuffer) - tagLength, timeoutData);
				if ( S400W_DEBUG ) S400W_DEBUG("scan().preview: %d\n", read);
				
				// if known response = error
				if ( read>0 && read<=sizeof(_buffer) && detectResponse(partBuf, read)!=partBuf ) {
					abort = 1;
					memset(_buffer, 0, sizeof(_buffer));
					memcpy(_buffer, partBuf, read);
					response = _buffer;
				} else {
					int processPreview = 1;
					//previewFunc(SEND_PREVIEW_DATA, 0, 0);
					// this is a bit tricky. we need to carry over bytes in between fetching 
					// so we can detect the end marker even if it is torn apart.
					while ( read>0 ) {
						total += read;
						if ( S400W_DEBUG ) S400W_DEBUG("scan().preview: %d (%d lines)\n", total, total / 1920);
						if ( processPreview>0 ) processPreview = previewFunc(partBuf, 0, read);
						memmove(_bigBuffer, partBuf + read - tagLength, tagLength);
						if ( memcmp(PREVIEW_END, _bigBuffer, tagLength - 1)==0 ) break; 					
						read = recvResponse(fd, partBuf, sizeof(_bigBuffer) - tagLength, timeoutData);
					}
					if ( read <0 ) { response = SEOF; abort = 1; }
					if ( read==0 ) { response = NULL; abort = 1; }
					if ( read >0 ) { response = SCAN_READY; }
				}
				previewFunc(SEOF, 0, 0);
			}
		}

		if ( !abort && jpegFunc ) {
			if ( previewFunc ) mssleep(1000);
			if ( sendCommand(fd, GET_JPEG_SIZE)>0 ) {
				mssleep(200);
				response = readResponse(fd, previewFunc ? timeoutBoth : timeoutJpeg);
				if ( S400W_DEBUG ) S400W_DEBUG("scan().jpegsize: %s\n", response);
				if ( response==JPEG_SIZE ) {
					int jslen = strlen(response);
					// TODO: a bit lazy here, not checking if size read == JPEG_SIZE.length + 4...
					int size = 0x000000FF & _buffer[jslen] 
						| 0x0000FF00 & (_buffer[jslen + 1] << 8)
						| 0x00FF0000 & (_buffer[jslen + 2] << 16)
						| 0xFF000000 & (_buffer[jslen + 3] << 24);
					if ( S400W_DEBUG ) S400W_DEBUG("scan().jpeg: %d bytes\n", size);
					
					response = SEOF;
					if ( jpegFunc(JPEG_SIZE, 0, size)>0 && sendCommand(fd, SEND_JPEG_DATA)>0 ) {
						int read, total = 0;
						mssleep(500);
						do {
							read = recvResponse(fd, _bigBuffer, sizeof(_bigBuffer), timeoutData);
							if ( read>0 ) {
								total += read;
								if ( S400W_DEBUG ) S400W_DEBUG("scan().jpeg: %d / %d bytes\n", total, size);
								if ( jpegFunc(_bigBuffer, 0, read)<1 ) break;
							}
						} while ( total<size && read>0 );
						if ( read <0 ) { response = SEOF; abort = 1; }
						if ( read==0 ) { response = NULL; abort = 1; }
						if ( read >0 ) { response = SCAN_READY; }
					}
					jpegFunc(SEOF,  0, 0);
				}
			}
		}
			
		closesocket(fd);
	}
	return response;
}
