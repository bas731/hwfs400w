#ifndef s400w_h_included
#define s400w_h_included


/** Version */
extern const char const* S400W_LIB_VERSION;

/** Firmware version necessary to set (higher) resolution. */
extern const int MIN_SET_RESOLUTION_FW;
	
// Responses (note that no response starts with another response, so no terminating character necessary)
/** Response: Device busy */
extern const unsigned char DEVICE_BUSY[];

/** Response: Battery low */
extern const unsigned char BATTERY_LOW[];

/** Response: No paper inserted */
extern const unsigned char NOPAPER[];

/** Response: Paper inserted, ready to scan, calibrate, clean */
extern const unsigned char SCAN_READY[];

/** Response: Calibration has started */
extern const unsigned char CALIBRATE_GO[];

/** Response: Calibration has finished */
extern const unsigned char CALIBRATE_END[];

/** Response: Cleaning has started */
extern const unsigned char CLEAN_GO[];

/** Response: Cleaning has finished */
extern const unsigned char CLEAN_END[];

/** Response: Standard DPI selected */
extern const unsigned char DPI_STANDARD[];

/** Response: High DPI selected */
extern const unsigned char DPI_HIGH[];

/** Response: Scanning has started */
extern const unsigned char SCAN_GO[];

/** Response: Preview data in stream end marker */
extern const unsigned char PREVIEW_END[];

/** Response: JPEG size */
extern const unsigned char JPEG_SIZE[];

/** Artifical response: EOF */
extern const unsigned char SEOF[];

/** Artifical response: EOF */
extern const unsigned char SERR[];


extern int isKnownResponse(const unsigned char* response);


/**
 * Notifies clients of preview data, end of preview data, jpeg size, jpeg data, and end of jpeg data.<br>
 * Note: EOF for jpeg is guaranteed to be called if jpeg size has been given (as to close file handles etc).
 * 
 * @param data either an array of bytes with preview or jpeg data, {@link S400W#EOF}
 * 		if end of preview/jpeg data, or {@link S400W#JPEG_SIZE} for jpeg size.
 * @param offset the offset inside the given array if not EOF or size.
 * @param length number of bytes to read from data or jpeg size of data is {@link S400W#JPEG_SIZE}.
 * @return <code>false</code> to abort receiving (not supported yet), <code>true</code> otherwise.
 * @throws IOException If something goes wrong while processing the data.
 */
typedef int (*receiveFunc)(const unsigned char* data, int offset, int length);


// Reads the scanner's version. Returns a version string, SEOF, or NULL if timeout.
extern const unsigned char* getVersion(const char* host, int port);

// Reads scanner's current status. Returns response, SEOF, or NULL if timeout.
extern const unsigned char* getStatus(const char* host, int port);

// Sets the scanner's resolution if supported (see MIN_SET_RESOLUTION_FW).
// Supported DPI settings: 300 or 600.
// Returns 1 if the resolution change was successful, 0 otherwise.
extern int setResolution(const char* host, int port, int dpi);


// Executes the scanner's cleaning routine.
// Returns CLEAN_END if sucessfully finished, any other otherwise, 
// including SEOF or NULL for timeouts. NOPAPER if cleaning sheet is not inserted.
extern const unsigned char* clean(const char* host, int port);


// Executes the scanner's calibration routine.
// Returns CALIBRATE_END if sucessfully finished, any other otherwise
// including SEOF or NULL for timeouts. NOPAPER if calibration sheet is not inserted.
extern const unsigned char* calibrate(const char* host, int port);


#endif
