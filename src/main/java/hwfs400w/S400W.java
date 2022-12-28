/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).  
 *
 * Created 2014-10-12 by bastel.
 */

package hwfs400w;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
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
public class S400W
{
	private final static Logger log = Logger.getLogger(S400W.class.getName());
	
	
	/** Version */
	public final static String VERSION = "20141116";
	
	
	/**
	 * A simple callback interface used for receiving preview and jpeg data.
	 */
	@FunctionalInterface
	public interface Receiver
	{
		/**
		 * Notifies clients of preview data, end of preview data, jpeg size, jpeg data, and end of jpeg data.<br>
		 * Note: EOF for jpeg is guaranteed to be called if jpeg size has been given (as to close file handles etc).
		 * 
		 * @param data either an array of bytes with preview or jpeg data, {@link S400W#EOF}
		 * 		if end of preview/jpeg data, or {@link S400W#JPEG_SIZE} for jpeg size.
		 * @param offset the offset inside the given array if not EOF or jpg size.
		 * @param length number of bytes to read from data or jpeg size of data is {@link S400W#JPEG_SIZE}.
		 * @return <code>false</code> to abort receiving (not supported yet), <code>true</code> otherwise.
		 * @throws IOException If something goes wrong while processing the data.
		 */
		public boolean receive(byte[] data, int offset, int length) throws IOException;
	}
	
	
	/** Prefix for properties, usually &lt;package-name&gt;.&lt;class-name&gt; of this class. */ 
	public final static String PROPERTY_KEY = S400W.class.getName();
	
	/** Firmware version necessary to set (higher) resolution. */
	public final static int MIN_SET_RESOLUTION_FW = 26;
	
	// Commands
	/** Command: Get version */
	private final static byte[] GET_VERSION       = {  0x30,  0x30, 0x20,  0x20 };
	/** Command: Get status */
	private final static byte[] GET_STATUS        = {  0x00,  0x60, 0x00,  0x50 };
	/** Command: Start cleaning */
	private final static byte[] START_CLEANING    = { -0x80, -0x80, 0x70,  0x70 };
	/** Command: Start calibration */
	private final static byte[] START_CALIBRATION = {  0x00, -0x50, 0x00, -0x60 };
	/** Command: Set standard DPI (300) */
	private final static byte[] SET_DPI_STANDARD  = {  0x40,  0x30, 0x20,  0x10 };
	/** Command: Set high DPI (600) */
	private final static byte[] SET_DPI_HIGH      = { -0x80,  0x70, 0x60,  0x50 };
	/** Command: Start scanning */
	private final static byte[] START_SCAN        = {  0x00,  0x20, 0x00,  0x10 };
	/** Command: Start sending preview data */
	private final static byte[] SEND_PREVIEW_DATA = {  0x40,  0x40, 0x30,  0x30 };
	/** Command: Get jpeg size */
	private final static byte[] GET_JPEG_SIZE     = {  0x00, -0x30, 0x00, -0x40 };
	/** Command: Start sending jpeg data */
	private final static byte[] SEND_JPEG_DATA    = {  0x00, -0x10, 0x00, -0x20 };

	// Responses (note that no response starts with another response, so no terminating character necessary)
	/** Response: Device busy */
	public final static byte[] DEVICE_BUSY   = { 'd','e','v','b','u','s','y' };
	/** Response: Battery low */
	public final static byte[] BATTERY_LOW   = { 'b','a','t','t','l','o','w' };
	/** Response: No paper inserted */
	public final static byte[] NOPAPER       = { 'n','o','p','a','p','e','r' };
	/** Response: Paper inserted, ready to scan, calibrate, clean */
	public final static byte[] SCAN_READY    = { 's','c','a','n','r','e','a','d','y' };
	/** Response: Calibration has started */
	public final static byte[] CALIBRATE_GO  = { 'c','a','l','g','o' };
	/** Response: Calibration has finished */
	public final static byte[] CALIBRATE_END = { 'c','a','l','i','b','r','a','t','e' };
	/** Response: Cleaning has started */
	public final static byte[] CLEAN_GO      = { 'c','l','e','a','n','g','o' };
	/** Response: Cleaning has finished */
	public final static byte[] CLEAN_END     = { 'c','l','e','a','n','e','n','d' };
	/** Response: Standard DPI selected */
	public final static byte[] DPI_STANDARD  = { 'd','p','i','s','t','d' };
	/** Response: High DPI selected */
	public final static byte[] DPI_HIGH      = { 'd','p','i','f','i','n','e' };
	/** Response: Scanning has started */
	public final static byte[] SCAN_GO       = { 's','c','a','n','g','o' };
	/** Response: Preview data in stream end marker */
	public final static byte[] PREVIEW_END   = { 'p','r','e','v','i','e','w','e','n','d' };
	/** Response: JPEG size */
	public final static byte[] JPEG_SIZE     = { 'j','p','e','g','s','i','z','e' };
	/** Artifical response: EOF */
	public final static byte[] EOF           = {};
	
	/** for mapping raw to constants */
	private final static byte[][] RESPONSES = {
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
	};
	
	/** Internal receive buffer, default responses should fit in 16 bytes */
	private byte[] _buffer = new byte[16];
	
	/** To check socket for incoming bytes */ 
	private Selector _selector;
	
	/** IO Channel */
	private SocketChannel _socket = null;
	
	/** Default timeout, sufficient for simple calls that don't start things. */
	private int _timeout = 10000;
	
	private String _targetHost;
	private int _targetPort;
	

	/**
	 * Creates a new S400W connection to the default host and port
	 * as defined in <code>{@link #PROPERTY_KEY}.target.host</code>
	 * and <code>{@link #PROPERTY_KEY}.target.port</code>.
	 * Default values are <i>192.168.18.33</i> and <i>23</i>.
	 */
	public S400W()
	{
		this(System.getProperty(PROPERTY_KEY + ".target.host", "192.168.18.33"),
			Integer.getInteger (PROPERTY_KEY + ".target.port", 23));
	}
	

	/**
	 * Creates a new S400W connection to the given host and port.
	 */
	public S400W(String host, int port)
	{
		_targetHost = host;
		_targetPort = port;
	}
	

	/**
	 * Reads the scanner's version. The returned bytes can be converted into a string using {@link #toString(byte[])}.
	 * 
	 * @return a version string, {@link #EOF}, or <code>null</code> if timeout
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public byte[] getVersion() throws IOException, InterruptedIOException
	{
		try {
			openSocket();
			sendCommand(GET_VERSION);
			byte[] response = readResponse();
			logResponse("getVersion", response);
			return response;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "getVersion()", e);
			throw e;
		}
		finally {
			closeSocket();
		}
	}

	
	/**
	 * Reads scanner's current status. Valid results can be converted into a string using US-ASCII encoding.
	 * 
	 * @return response, {@link #EOF}, or <code>null</code> if timeout.
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public byte[] getStatus() throws IOException, InterruptedIOException
	{
		try {
			openSocket();
			sendCommand(GET_STATUS);
			byte[] response = readResponse();
			logResponse("getStatus()", response);
			return response;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "getStatus()", e);
			throw e;
		}
		finally {
			closeSocket();
		}
	}
	
	
	/**
	 * Sets the scanner's resolution if supported (see {@link #MIN_SET_RESOLUTION_FW}).
	 * 
	 * @param dpi supported DPI setting: <code>300</code> or <code>600</code>
	 * @return <code>true</code> if the resolution change was successful.
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public boolean setResolution(int dpi) throws IOException, InterruptedIOException
	{
		try {
			openSocket();
			sendCommand(dpi==600 ? SET_DPI_HIGH : SET_DPI_STANDARD);
			byte[] response = readResponse();
			logResponse("setResolution(" + dpi + ")", response);
			return dpi==600 && response==DPI_HIGH || dpi!=600 && response==DPI_STANDARD;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "setResolution()", e);
			throw e;
		}
		finally {
			closeSocket();
		}
	}


	/**
	 * Executes the scanner's cleaning routine.
	 * <p>
	 * Timeout to wait for end of cleaning can be set via
	 * <code>{@link #PROPERTY_KEY}.timeout.clean</code>, default is 40000 ms.
	 * 
	 * @return {@link #CLEAN_END} if sucessfully finished, any other otherwise, 
	 * 		including {@link #EOF} and <code>null</code> for timeouts, ({@link #NOPAPER} if cleaning sheet is not inserted).
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public byte[] clean() throws IOException, InterruptedIOException
	{
		try {
			openSocket();
			sendCommand(GET_STATUS);
			byte[] response = readResponse();
			logResponse("clean().check", response);
			if ( response!=SCAN_READY ) return response;

			sendCommand(START_CLEANING);
			sleep(500);
			response = readResponse();
			logResponse("clean().go", response);
			if ( response!=CLEAN_GO ) return response;
			
			response = readResponse(Integer.getInteger(PROPERTY_KEY + ".timeout.clean", 40000));
			logResponse("clean().end", response);
			return response;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "clean()", e);
			throw e;
		}
		finally {
			closeSocket();
		}
	}

	
	/**
	 * Executes the scanner's calibration routine.
	 * <p>
	 * Timeout to wait for end of calibration can be set via
	 * <code>{@link #PROPERTY_KEY}.timeout.calibrate</code>, default is 60000 ms.
	 * 
	 * @return {@link #CALIBRATE_END} if sucessfully finished, any other otherwise
	 * 		including {@link #EOF} and <code>null</code> for timeouts, ({@link #NOPAPER} if calibration sheet is not inserted).
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public byte[] calibrate() throws IOException, InterruptedIOException
	{
		try {
			openSocket();
			sendCommand(GET_STATUS);
			byte[] response = readResponse();
			logResponse("calibrate().check", response);
			if ( response!=SCAN_READY ) return response;
			
			sendCommand(START_CALIBRATION);
			sleep(500);
			response = readResponse();
			logResponse("calibrate().go", response);
			if ( response!=CALIBRATE_GO ) return response;
			
			response = readResponse(Integer.getInteger(PROPERTY_KEY + ".timeout.calibrate", 60000));
			logResponse("calibrate().end", response);
			return response;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "calibrate()", e);
			throw e;
		}
		finally {
			closeSocket();
		}
	}
	

	/**
	 * Executes the scanner's scanning procedure.
	 * <p>
	 * Timeouts to wait for data can be set via
	 * <code>{@link #PROPERTY_KEY}.timeout.data</code> (preview and jpeg data),
	 * <code>{@link #PROPERTY_KEY}.timeout.jpeg-size</code> (jpeg size after preview), 
	 * <code>{@link #PROPERTY_KEY}.timeout.jpeg-only</code> (jpeg size w/o preview),
	 * defaults are 30000, 20000, 60000 ms.
	 * 
	 * @param resolution resolution setting, or <code>0</code> if no setting is supported / desired
	 * @param preview callback handler for preview data, or <code>null</code> if no preview should be read
	 * @param jpeg callback handler for jpeg data
	 * @return {@link #SCAN_READY} if sucessfully finished, any other response otherwise,
	 * 		including {@link #EOF} and <code>null</code> for timeouts
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public byte[] scan(int resolution, Receiver preview, Receiver jpeg) throws IOException, InterruptedIOException
	{
		try {
			openSocket();
			sendCommand(GET_STATUS);
			byte[] response = readResponse();
			logResponse("scan().check", response);
			if ( response!=SCAN_READY ) return response;

			if ( resolution>0 ) {
				sendCommand(resolution==600 ? SET_DPI_HIGH : SET_DPI_STANDARD);
				response = readResponse();
				logResponse("scan().dpi" + resolution, response);
				if ( resolution==600 && response!=DPI_HIGH || resolution!=600 && response!=DPI_STANDARD ) return response;
			}
			
			sendCommand(START_SCAN);
			response = readResponse();
			logResponse("scan().go", response);
			if ( response!=SCAN_GO ) return response;

			final boolean isFiner = log.isLoggable(Level.FINER);
			final int dataTimeout = Integer.getInteger(PROPERTY_KEY + ".timeout.data", 30000);
			final int tagLength   = PREVIEW_END.length + 1;
			final byte[] buffer   = new byte[61440 + tagLength];  // buffer size arbitrarily chosen to be 32 lines
			
			if ( preview!=null ) {
				sendCommand(SEND_PREVIEW_DATA);
				sleep(1000);
				ByteBuffer partBuf = ByteBuffer.wrap(buffer, tagLength, buffer.length - tagLength).slice();
				int read = receive(partBuf, dataTimeout);

				// if known response = error
				if ( read>0 ) {
					response = detectResponse(buffer, partBuf.arrayOffset(), read);
					if ( response!=buffer ) {
						logResponse("scan().preview", response);
						return response;
					}
				}

				// this is a bit tricky. we need to carry over bytes in between fetching 
				// so we can detect the end marker even if it is torn apart.
				int total = 0;
				boolean readPreview = true;
				while ( read>0 ) {
					total += read;
					if ( isFiner ) log.finer("scan().preview: " + total + " ( " + (total / (1920)) + " lines)");
					if ( readPreview ) readPreview &= preview.receive(buffer, partBuf.arrayOffset(), read);
					System.arraycopy(buffer, read, buffer, 0, tagLength);
					if ( cmp(PREVIEW_END, 0, buffer, 0, PREVIEW_END.length) ) break;
					partBuf.clear();
					read = receive(partBuf, dataTimeout);
				}
				// TODO: not in finally, hmm
				preview.receive(EOF,  0, 0);
				if ( read==0 ) return null;
				if ( read <0 ) return EOF;
			}
			
			if ( jpeg!=null ) {
				if ( preview!=null ) sleep(1000);
				final int sizeTimeout = preview==null
					? Integer.getInteger(PROPERTY_KEY + ".timeout.jpeg-only", 60000)
					: Integer.getInteger(PROPERTY_KEY + ".timeout.jpeg-size", 20000);

				sendCommand(GET_JPEG_SIZE);
				response = readResponse(sizeTimeout);
				logResponse("scan().jpegsize", response);
				if ( response!=JPEG_SIZE ) return response;
				// TODO: a bit lazy here, not checking if size read == JPEG_SIZE.length + 4...
				// TODO: also a little endian byte buffer has a getInteger method...
				final int size = 0x000000FF & _buffer[JPEG_SIZE.length] 
					| 0x0000FF00 & (_buffer[JPEG_SIZE.length + 1] << 8)
					| 0x00FF0000 & (_buffer[JPEG_SIZE.length + 2] << 16)
					| 0xFF000000 & (_buffer[JPEG_SIZE.length + 3] << 24);
				if ( log.isLoggable(Level.FINE) ) log.log(Level.FINE, "scan().jpeg: {0,number} bytes", size);

				try {
					if ( !jpeg.receive(JPEG_SIZE, 0, size) ) return EOF;
					ByteBuffer buf = ByteBuffer.wrap(buffer);
					sendCommand(SEND_JPEG_DATA);
					sleep(500);
					int total = 0;
					int read = 0; 
					do {
						buf.clear();
						read = receive(buf, dataTimeout);
						if ( read>0 ) {
							total += read;
							if ( isFiner ) log.finer("scan().jpeg: " + total + "/" + size + " bytes");
							if ( !jpeg.receive(buffer, 0, read) ) break;
						}
					} while ( total<size && read>0 );
					if ( read==0 ) return null;
					if ( read <0 ) return EOF;
				} finally {
					jpeg.receive(EOF,  0, 0);
				}
			}
				
			return SCAN_READY;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "scan()", e);
			throw e;
		}
		finally {
			closeSocket();
		}
	}
	
	
	/**
	 * @return <code>true</code> if the response is one of the public response constants (not {@link #EOF}).
	 */
	public static boolean isKnownResponse(byte[] buffer)
	{
		for ( byte[] res : RESPONSES ) if ( res==buffer ) return true;
		return false;
	}
	

	/**
	 * Supports byte[] to String conversion by returning the index of the first zero byte in the response.
	 * @return The index of the first 0-byte or the length of the byte buffer if no 0-byte is found. 
	 */
	public static int getResponseLength(byte[] buffer)
	{
		int l = 0;
		while ( l<buffer.length && buffer[l]!=0 ) l++;
		return l;
	}
	
	
	/**
	 * Convenience method to convert a response buffer into a string with US-ASCII encoding.
	 * @return The converted response.
	 */
	public static String toString(byte[] buffer)
	{
		return buffer==null ? "<no data>"
			: buffer==EOF ? "<EOF>"
			: new String(buffer, 0, getResponseLength(buffer), StandardCharsets.US_ASCII);
	}

	
	/**
	 * Opens a socket to the target.
	 * 
	 * @throws IOException If the socket cannot be opened and set up correctly.
	 */
	private void openSocket() throws IOException, InterruptedIOException
	{
		if ( log.isLoggable(Level.FINE) ) log.fine("opening socket to " + _targetHost + ":" + _targetPort);
		_selector = Selector.open();
		_socket = SocketChannel.open();
		_socket.configureBlocking(false);
		if ( !_socket.connect(new InetSocketAddress(_targetHost, _targetPort)) ) {
			int timeout = Integer.getInteger(PROPERTY_KEY + ".timeout.connect", 5000);
			SelectionKey key = _socket.register(_selector, SelectionKey.OP_CONNECT);
			select(timeout);
			if ( !_socket.finishConnect() ) throw new IOException("Couldn't connect to scanner");
			key.interestOps(SelectionKey.OP_READ);
		} else {
			_socket.register(_selector, SelectionKey.OP_READ);
		}
	}
	
	
	/**
	 * Closes the target connection.
	 */
	private void closeSocket()
	{
		if ( _socket  !=null ) try { _socket  .close(); } catch (Exception e) {}
		if ( _selector!=null ) try { _selector.close(); } catch (Exception e) {} 
		_socket = null;
		_selector = null;
		
	}
	
	
	/**
	 * Sends a command to the target. Sleeps before and after sending.
	 * 
	 * @param command the command.
	 * @throws IOException if sending the buffer fails.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	private void sendCommand(byte[] command) throws IOException, InterruptedIOException
	{
		if ( log.isLoggable(Level.FINE) ) log.fine("sendCommand(" + Arrays.toString(command) + ")");
		sleep(200);
		if ( _socket.write(ByteBuffer.wrap(command))>0 ) sleep(200);
	}
	

	/**
	 * Equivalent to {@link #receive(ByteBuffer, int)} with the buffer wrapped into a {@link ByteBuffer}.
	 */
	private int receive(byte[] buffer, int timeout) throws IOException
	{
		return receive(ByteBuffer.wrap(buffer), timeout);
	}
	
	
	/**
	 * Reads a number of bytes, not necessarily as many as the buffer has space for.
	 * 
	 * @param buffer the recieve buffer
	 * @param timeout time to wait for a bytes to arrive in seconds
	 * @return Number of bytes read, 0 if timeout reached, or -1 for EOF.
	 * @throws IOException If IO errors occurred.
	 */
	private int receive(ByteBuffer buffer, long timeout) throws IOException
	{
		if ( select(timeout)>0 ) {
			int r = _socket.read(buffer);
			if ( log.isLoggable(Level.FINER) ) log.finer("receive(): read " + r + " bytes");
			return r; // shouldn't be 0
		}
		if ( log.isLoggable(Level.FINER) ) log.finer("receive(): no data");
		return 0;
	}

	
	private int select(long timeout) throws IOException
	{
		for ( Iterator<SelectionKey> it = _selector.selectedKeys().iterator(); it.hasNext(); it.remove() ) it.next();
		
		final long bogusTimeout = Long.getLong(PROPERTY_KEY + ".timeout.bogus", 100);
		long start = System.currentTimeMillis(), end = start + timeout;
		while ( timeout>0 ) {
			int result = _selector.select(timeout);
			if ( result!=0 ) return result;

			long now = System.currentTimeMillis();
			timeout = end - now;
			
			// spurious wakeup detected, possible faulty jvm with immediate wakeup
			if ( timeout>0 ) {
				if ( start - now < bogusTimeout ) {
					try {
						Thread.sleep(Math.min(timeout, bogusTimeout));
						now = System.currentTimeMillis();
						timeout = Math.max(1, end - now);
					} catch (InterruptedException e) {
						throw (InterruptedIOException)new InterruptedIOException(e.getMessage()).initCause(e.getCause()); 
					}
				}
				start = now;
			}
		}
		return 0;
	}
	

	/**
	 * Matches a response to the know responses and returns the constant, so that <code>==</code> can be used later on.
	 * 
	 * @param buffer the original response
	 * @param length number of bytes set in <code>buffer</code>
	 * @return response constant matching the response or <code>buffer</code> if unknown response. 
	 */
	private byte[] detectResponse(byte[] buffer, int offset, int length)
	{
		if ( length==0 ) return null;
		if ( length <0 ) return EOF;
		if ( length<=_buffer.length ) {
			for ( int i = 0; i<RESPONSES.length; i++ ) {
				if ( length>=RESPONSES[i].length && cmp(buffer, offset, RESPONSES[i], 0, RESPONSES[i].length) ) return RESPONSES[i];
			}
		}
		return buffer;
	}	
	

	/**
	 * Equivalent to {@link #readResponse(int)} with {@link #_timeout} as timeout.
	 */
	private byte[] readResponse() throws IOException
	{
		return readResponse(_timeout);
	}	
	
	
	/**
	 * Reads in a short response using a zerored {@link #_buffer},
	 * {@link #receive(byte[], int)} and {@link #detectResponse(byte[], int)}.
	 *  
	 * @param timeout seconds to wait for a response
	 * @return the detected response 
	 * @throws IOException If errors occurred while reading the response
	 */
	private byte[] readResponse(int timeout) throws IOException
	{
		Arrays.fill(_buffer, (byte)0);
		return detectResponse(_buffer, 0, receive(_buffer, timeout));
	}	

	
	/**
	 * Convenience method for response logging.
	 */
	private void logResponse(String method, byte[] response)
	{
		if ( log.isLoggable(Level.FINE) ) log.fine(method + ": " + toString(response));
	}

	
	/**
	 * Compares parts of two arrays for equality.
	 * @return <code>true</code> if the both parts match
	 */
	private static boolean cmp(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length)
	{
		if ( length>0 && srcOffset>=0 && dstOffset>=0 && srcOffset + length <= src.length && dstOffset + length <= dst.length ) {
			while ( length-->0 ) if ( src[srcOffset++]!=dst[dstOffset++] ) return false;
			return true;
		}
		return false;
	}
	
	
	/**
	 * <code>Thread.sleep</code> wrapper.
	 */
	public static void sleep(int ms) throws InterruptedIOException
	{
		// i had some trouble using normal sleep, probably due to to deep sleep states on the cpu
		//long then = ms*1000000L + System.nanoTime();
		//do {
		//	Thread.sleep(ms / 10 + 1);
		//} while ( System.nanoTime()<then );
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			throw (InterruptedIOException)new InterruptedIOException("Interrupted timeout").initCause(e);
		}
	}

}
