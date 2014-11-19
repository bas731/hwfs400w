/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).
 *
 * Created 2014-11-16 by bastel.
 *
 * Implements a low level interface to the Mustek<sup>&#174;</sup> S400W iScanAir&#8482; scanner.
 * <br>
 * This is a clean room implementation based on written down specs (see commands.txt)
 * and does not contain any code by mustek. Using simple java, constants and non blocking I/O,
 * it's easy to understand, clean and easily portable to other programming languages.
 * <p>
 * Scanner related methods usually return a byte array containing the scanner's response. Please be aware
 * that the response array is not a copy and not constant for unknown responses but will be altered on the next
 * call to any scanner related method. Do not store it 'for later'.
 * Returned known responses are mapped to their static equivalent and can be stored and compared with <code>==</code>.
 * This also means that this class is not thread safe.
 * <p>
 * Note: The class supports {@link Logger} logging.
 * <p>
 * This file is licensed under the <a href="http://creativecommons.org/publicdomain/zero/1.0/">Creative Commons License CC-CC0 1.0</a>.
 *
 * @author bastel
 */

#include <stdio.h>
#include <string.h>

#include <sys/socket.h>
#include <sys/select.h>
#include <netdb.h>
#include <fcntl.h>
#include <unistd.h>

#include <errno.h>


#include "s400w.h"

const char const* S400W_LIB_VERSION = "20141116";

const int MIN_SET_RESOLUTION_FW = 26;

// Responses (note that no response starts with another response, so no terminating character necessary)
const unsigned char DEVICE_BUSY[]   = { 'd','e','v','b','u','s','y',0 };
const unsigned char BATTERY_LOW[]   = { 'b','a','t','t','l','o','w',0 };
const unsigned char NOPAPER[]       = { 'n','o','p','a','p','e','r',0 };
const unsigned char SCAN_READY[]    = { 's','c','a','n','r','e','a','d','y',0 };
const unsigned char CALIBRATE_GO[]  = { 'c','a','l','g','o',0 };
const unsigned char CALIBRATE_END[] = { 'c','a','l','i','b','r','a','t','e',0 };
const unsigned char CLEAN_GO[]      = { 'c','l','e','a','n','g','o',0 };
const unsigned char CLEAN_END[]     = { 'c','l','e','a','n','e','n','d',0 };
const unsigned char DPI_STANDARD[]  = { 'd','p','i','s','t','d',0 };
const unsigned char DPI_HIGH[]      = { 'd','p','i','f','i','n','e',0 };
const unsigned char SCAN_GO[]       = { 's','c','a','n','g','o',0 };
const unsigned char PREVIEW_END[]   = { 'p','r','e','v','i','e','w','e','n','d',0 };
const unsigned char JPEG_SIZE[]     = { 'j','p','e','g','s','i','z','e',0 };
const unsigned char SEOF[]          = { 0 };
const unsigned char SERR[]          = { 0 };



/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Private /
////////////

static const unsigned char* RESPONSES[] = {
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

static const unsigned char GET_VERSION[]       = {  0x30,  0x30, 0x20,  0x20 };
static const unsigned char GET_STATUS[]        = {  0x00,  0x60, 0x00,  0x50 };
static const unsigned char START_CLEANING[]    = { -0x80, -0x80, 0x70,  0x70 };
static const unsigned char START_CALIBRATION[] = {  0x00, -0x50, 0x00, -0x60 };
static const unsigned char SET_DPI_STANDARD[]  = {  0x40,  0x30, 0x20,  0x10 };
static const unsigned char SET_DPI_HIGH[]      = { -0x80,  0x70, 0x60,  0x50 };
static const unsigned char START_SCAN[]        = {  0x00,  0x20, 0x00,  0x10 };
static const unsigned char SEND_PREVIEW_DATA[] = {  0x40,  0x40, 0x30,  0x30 };
static const unsigned char GET_JPEG_SIZE[]     = {  0x00, -0x30, 0x00, -0x40 };
static const unsigned char SEND_JPEG_DATA[]    = {  0x00, -0x10, 0x00, -0x20 };

/** Internal receive buffer, default responses should fit in 16 bytes */
static unsigned char _buffer[16];

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
	if ( fcntl(fd, F_SETFL, O_NONBLOCK)==-1 ) {
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



static int sendCommand(int fd, const unsigned char* command)
{
	int sent = fd ? send(fd, command, 4, 0) : -1;
	fprintf(stderr, "sendCommand(): %x: %d\n", *(unsigned int*)command, sizeof(command));
	if ( sent>0 ) mssleep(100);
	return sent;
}


static int recvResponse(int fd, unsigned char* buffer, int limit, long timeout)
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


static const unsigned char* detectResponse(const unsigned char* buffer, int length)
{
	const unsigned char** res;
	if ( length==0 ) return NULL;
	if ( length <0 ) return SEOF;
	for ( res = RESPONSES; *res; res++ ) {
		int len = strlen(*res);
		if ( len<=length && memcmp(buffer, *res, len)==0 ) return *res;
	}
	return buffer;
}


static const unsigned char* readResponse(int fd, long timeout)
{
	memset(_buffer, 0, sizeof(_buffer));
	return detectResponse(_buffer, recvResponse(fd, _buffer, sizeof(_buffer), timeout));
}	



/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Public /
///////////


int isKnownResponse(const unsigned char* response)
{
	const unsigned char** res;
	for ( res = RESPONSES; *res; res++ ) if ( *res==response ) return 1;
	return 0;
}


const unsigned char* getVersion(const char* host, int port)
{
	const unsigned char* response = SEOF;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		if ( sendCommand(fd, GET_VERSION)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			printf("getVersion(): %s\n", response);
		}
		close(fd);
	}
	return response;
}


const unsigned char* getStatus(const char* host, int port)
{
	const unsigned char* response = SEOF;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		if ( sendCommand(fd, GET_STATUS)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			printf("getStatus(): %s\n", response);
		}
		close(fd);
	}
	return response;
}


int setResolution(const char* host, int port, int dpi)
{
	int result = 0;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		if ( sendCommand(fd, dpi==600 ? SET_DPI_HIGH : SET_DPI_STANDARD)>0 ) {
			mssleep(200);
			const unsigned char* response = readResponse(fd, _timeout);
			printf("setResolution(%d): %s\n", dpi, response);
			result = dpi==600 && response==DPI_HIGH || dpi!=600 && response==DPI_STANDARD ? 1 : 0;
		}
		close(fd);
	}
	return result;
}


const unsigned char* clean(const char* host, int port)
{
	const unsigned char* response = SEOF;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		if ( sendCommand(fd, GET_STATUS)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			printf("clean().check: %s\n", response);
			if ( response==SCAN_READY ) {
				sendCommand(fd, START_CLEANING);
				mssleep(500);
				response = readResponse(fd, _timeout);
				printf("clean().go: %s\n", response);
				if ( response==CLEAN_GO ) {
					response = readResponse(fd, 30000);
					printf("clean().end: %s\n", response);
				}
			}
		}
		close(fd);
	}
	return response;
}


const unsigned char* calibrate(const char* host, int port)
{
	const unsigned char* response = SEOF;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		if ( sendCommand(fd, GET_STATUS)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			printf("calibrate().check: %s\n", response);
			if ( response==SCAN_READY ) {
				sendCommand(fd, START_CALIBRATION);
				mssleep(500);
				response = readResponse(fd, _timeout);
				printf("calibrate().go: %s\n", response);
				if ( response==CALIBRATE_GO ) {
					response = readResponse(fd, 60000);
					printf("calibrate().end: %s\n", response);
				}
			}
		}
		close(fd);
	}
	return response;
}


// Executes the scanner's scanning procedure.
// resolution: resolution setting, or <code>0</code> if no setting is supported / desired
// preview: callback handler for preview data, or <code>null</code> if no preview should be read
// jpeg: callback handler for jpeg data
// Returns SCAN_READY if sucessfully finished, any other response otherwise, including SEOF or NULL for timeouts.
const unsigned char* scan(const char* host, int port, int resolution, receiveFunc previewFunc, receiveFunc jpegFunc)
{
	const unsigned char* response = SEOF;
	int fd = openSocket(host, port);
	if ( fd>0 ) {
		const long timeoutData = 30000;
		const long timeoutBoth = 20000;
		const long timeoutJpeg = 60000;
		const int  bufferSize  = 30720; // buffer size arbitrarily chosen to be 16 preview lines
		const int  tagLength   = strlen(PREVIEW_END) + 1;
		unsigned char buffer[bufferSize + tagLength];  
		int abort = 0;
		
		if ( sendCommand(fd, GET_STATUS)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			printf("scan().check: %s\n", response);
			if ( response!=SCAN_READY ) abort = 1;
		} else abort = 1;
			
		if ( !abort && resolution>0 ) {
			if ( sendCommand(fd, resolution==600 ? SET_DPI_HIGH : SET_DPI_STANDARD)>0 ) {
				mssleep(200);
				response = readResponse(fd, _timeout);
				printf("scan().dpi(%d): %s\n", resolution, response);
				abort = resolution==600 && response==DPI_HIGH || resolution!=600 && response==DPI_STANDARD ? 0 : 1;
			} else abort = 1;
		}
		
		if ( !abort && sendCommand(fd, START_SCAN)>0 ) {
			mssleep(200);
			response = readResponse(fd, _timeout);
			printf("scan().go: %s\n", response);
			if ( response!=SCAN_GO ) abort = 1;
		} else abort = 1;

		if ( !abort && previewFunc ) {
			if ( sendCommand(fd, SEND_PREVIEW_DATA)>0 ) {
				unsigned char* partBuf = buffer + tagLength;
				int read, total = 0;

				mssleep(1000);
				read = recvResponse(fd, partBuf, bufferSize, timeoutData);
				printf("scan().preview: %d\n", read);
				
				// if known response = error
				if ( read>0 && read<=sizeof(_buffer) && detectResponse(partBuf, read)!=partBuf ) {
					abort = 1;
					memset(_buffer, 0, sizeof(_buffer));
					memcpy(_buffer, partBuf, read);
					response = _buffer;
				} else {
					previewFunc(SEND_PREVIEW_DATA, 0, 0);
					// this is a bit tricky. we need to carry over bytes in between fetching 
					// so we can detect the end marker even if it is torn apart.
					while ( read>0 ) {
						total += read;
						printf("scan().preview: %d (%d lines)\n", total, total / 1920);
						previewFunc(partBuf, 0, read);
						memmove(buffer, partBuf + read - tagLength, tagLength);
						if ( memcmp(PREVIEW_END, buffer, tagLength - 1)==0 ) break; 					
						read = recvResponse(fd, partBuf, bufferSize, timeoutData);
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
				printf("scan().jpegsize: %s\n", response);
				if ( response==JPEG_SIZE ) {
					int jslen = strlen(response);
					// TODO: a bit lazy here, not checking if size read == JPEG_SIZE.length + 4...
					int size = 0x000000FF & _buffer[jslen] 
						| 0x0000FF00 & (_buffer[jslen + 1] << 8)
						| 0x00FF0000 & (_buffer[jslen + 2] << 16)
						| 0xFF000000 & (_buffer[jslen + 3] << 24);
					printf("scan().jpeg: %d bytes\n", size);
		
					jpegFunc(JPEG_SIZE, 0, size);
					if ( sendCommand(fd, SEND_JPEG_DATA)>0 ) {
						int read, total = 0;
						mssleep(500);
						do {
							read = recvResponse(fd, buffer, sizeof(buffer), timeoutData);
							if ( read>0 ) {
								total += read;
								printf("scan().jpeg: %d / %d bytes\n", total, size);
								jpegFunc(buffer, 0, read);
							}
						} while ( total<size && read>0 );
					}
					jpegFunc(SEOF,  0, 0);
					if ( read <0 ) { response = SEOF; abort = 1; }
					if ( read==0 ) { response = NULL; abort = 1; }
					if ( read >0 ) { response = SCAN_READY; }
				}
			}
		}
			
		close(fd);
	}
	return response;
}
	
FILE* file;
	
static int preview(const unsigned char* data, int offset, int length)
{
	if ( data==SEND_PREVIEW_DATA ) file = fopen("./test.raw", "w");
	else if ( data==SEOF ) fclose(file);
	else fwrite(data + offset, length, 1, file);
	return 1;
}


int jpeg(const unsigned char* data, int offset, int length)
{
	if ( data==JPEG_SIZE ) file = fopen("./test.jpg", "w");
	else if ( data==SEOF ) fclose(file);
	else fwrite(data + offset, length, 1, file);
	return 1;
}


void main()
{
	printf("version: %s\n",   getVersion   ("192.168.18.33", 23));
	printf("status: %s\n",    getStatus    ("192.168.18.33", 23));
//	printf("clean: %s\n",     clean        ("192.168.18.33", 23));
//	printf("calibrate: %s\n", calibrate    ("192.168.18.33", 23));
//	printf("%d\n",            setResolution("192.168.18.33", 23, 300));
//	printf("%d\n",            setResolution("192.168.18.33", 23, 600));
	printf("scan: %s\n",      scan         ("192.168.18.33", 23, 300, &preview, &jpeg));
}