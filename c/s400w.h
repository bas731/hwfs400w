#ifndef s400w_h_included
#define s400w_h_included

/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).
 *
 * Created 2014-11-16 by bastel.
 *
 * Implements a low level interface to the Mustek(tm) S400W iScanAir(R) scanner.
 *
 * This is a clean room implementation based on written down specs (see commands.txt)
 * and does not contain any code by mustek. Using simple c and non blocking I/O,
 * it's easy to understand, clean and easily portable to other programming languages.
 *
 * Scanner related methods usually return a byte array containing the scanner's response. Please be aware
 * that the response array is not a copy and not constant for unknown responses but will be altered on the next
 * call to any scanner related method. Do not store it 'for later'.
 * Returned known responses are mapped to their static equivalent and can be stored and compared with <code>==</code>.
 * This also means that this class is not thread safe, but only needs a little ram (around 32 kiB).
 * 
 * This library is licensed under the Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).
 *
 * @author bastel
 */


/** Version */
extern const char* S400W_LIB_VERSION;

/** Firmware version necessary to set (higher) resolution. */
extern const int MIN_SET_RESOLUTION_FW;
	
// Responses (note that no response starts with another response, so no terminating character necessary)
/** Response: Device busy */
extern const char DEVICE_BUSY[];

/** Response: Battery low */
extern const char BATTERY_LOW[];

/** Response: No paper inserted */
extern const char NOPAPER[];

/** Response: Paper inserted, ready to scan, calibrate, clean */
extern const char SCAN_READY[];

/** Response: Calibration has started */
extern const char CALIBRATE_GO[];

/** Response: Calibration has finished */
extern const char CALIBRATE_END[];

/** Response: Cleaning has started */
extern const char CLEAN_GO[];

/** Response: Cleaning has finished */
extern const char CLEAN_END[];

/** Response: Standard DPI selected */
extern const char DPI_STANDARD[];

/** Response: High DPI selected */
extern const char DPI_HIGH[];

/** Response: Scanning has started */
extern const char SCAN_GO[];

/** Response: Preview data in stream end marker */
extern const char PREVIEW_END[];

/** Response: JPEG size */
extern const char JPEG_SIZE[];

/** Artifical response: EOF */
extern const char SEOF[];

/** Artifical response: EOF */
extern const char SERR[];


extern int isKnownResponse(const char* response);


/* Notifies clients of preview data, end of preview data, jpeg size, jpeg data, and end of jpeg data.
 * Note: EOF for jpeg is guaranteed to be called if jpeg size has been given (as to close file handles etc).
 * 
 * data: either an array of bytes with preview or jpeg data, SEOF if end of preview/jpeg data,
 *       or JPEG_SIZE for jpeg size.
 * offset: the offset inside the given array if not SEOF or size.
 * length: number of bytes to read from data or jpeg size of data is JPEG_SIZE.
 *
 * Returns <=0 to abort receiving, 1 otherwise. (negative values indicate error condition)
 */
typedef int (*receiveFunc)(const char* data, int offset, int length);


/* Reads the scanner's version. Returns a version string, SEOF, or NULL if timeout. */
extern const char* getVersion(const char* host, int port);

/* Reads scanner's current status. Returns response, SEOF, or NULL if timeout. */
extern const char* getStatus(const char* host, int port);

/* Sets the scanner's resolution if supported (see MIN_SET_RESOLUTION_FW).
 * Supported DPI settings: 300 or 600.
 * Returns 1 if the resolution change was successful, 0 otherwise.
 */
extern int setResolution(const char* host, int port, int dpi);


/* Executes the scanner's cleaning routine.
 * Returns CLEAN_END if sucessfully finished, any other otherwise, 
 * including SEOF or NULL for timeouts. NOPAPER if cleaning sheet is not inserted.
 */
extern const char* clean(const char* host, int port);


/* Executes the scanner's calibration routine.
 * Returns CALIBRATE_END if sucessfully finished, any other otherwise
 * including SEOF or NULL for timeouts. NOPAPER if calibration sheet is not inserted.
 */
extern const char* calibrate(const char* host, int port);


/* Executes the scanner's scanning procedure.
 * resolution: resolution setting, or <code>0</code> if no setting is supported / desired
 * preview: callback handler for preview data, or <code>null</code> if no preview should be read
 * jpeg: callback handler for jpeg data
 * Returns SCAN_READY if sucessfully finished, any other response otherwise, including SEOF or NULL for timeouts.
 */
extern const char* scan(const char* host, int port, int resolution, receiveFunc previewFunc, receiveFunc jpegFunc);


/** For debugging assign printf or your own function. */
extern int (*S400W_DEBUG)(const char* format, ...);

#endif
