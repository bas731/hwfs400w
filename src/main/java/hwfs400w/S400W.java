/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).  
 *
 * Created 2014-10-12 by bastel.
 */

package hwfs400w;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements a low level interface to the Mustek<sup>&#174;</sup> S400W iScanAir&#8482; scanner.
 * <br>
 * This is a clean room implementation based on written down specs (see commands.txt)
 * and does not contain any code by mustek. Using simple java, constants and non blocking I/O,
 * it's easy to understand, clean and easily portable to other programming languages.
 * <p>
 * Scanner related methods usually return a {@link S400WResponse} containing the scanner's response.
 * Returned known responses are mapped to their static equivalent and can be compared with <code>==</code>.
 * <p>
 * This class is not thread safe.
 * <p>
 * Note: The class supports {@link Logger} logging.
 * <p>
 * This file is licensed under the <a href="http://creativecommons.org/publicdomain/zero/1.0/">Creative Commons License CC-CC0 1.0</a>.
 * 
 * @author bastel
 * @since 2014-10-12
 */
public class S400W
{
	private final static Logger log = Logger.getLogger(S400W.class.getName());

	// Commands (little endian)
	private final static int GET_VERSION       = 0x20203030;
	private final static int GET_STATUS        = 0x50006000;
	private final static int START_CLEANING    = 0x70708080;
	private final static int START_CALIBRATION = 0xA000B000;
	private final static int SET_DPI_STANDARD  = 0x10203040;
	private final static int SET_DPI_HIGH      = 0x50607080;
	private final static int START_SCAN        = 0x10002000;
	private final static int SEND_PREVIEW_DATA = 0x30304040;
	private final static int GET_JPEG_SIZE     = 0xC000D000;
	private final static int SEND_JPEG_DATA    = 0xE000F000;
	private final static int GET_BATTERY_STATE = 0x40405050;
	private final static int POWER_OFF         = 0x70008000;

	
	/** Version */
	public final static String VERSION = "1.0-SNAPSHOT";
	
	/** Firmware version necessary to set (higher) resolution. */
	public final static int MIN_SET_RESOLUTION_FW = 26;

	private final S400WSettings _settings;
	
	/** Internal receive buffer, default responses should fit in 16 bytes */
	private final byte[] _buffer = new byte[32];
	
	/** To check socket for incoming bytes */ 
	private Selector _selector;
	
	/** IO Channel */
	private SocketChannel _socket = null;

	
	/**
	 * Creates a new S400W connection with the default {@link S400WSettings}.
	 */
	public S400W()
	{
		_settings = new S400WSettings();
	}
	

	/**
	 * Creates a new S400W connection to the given host and port.
	 */
	public S400W(String host, int port)
	{
		_settings = new S400WSettings().with(host, port);
	}

	
	/**
	 * Creates a new S400W connection to the given settings.
	 */
	public S400W(S400WSettings settings)
	{
		Objects.requireNonNull(settings, "settings");
		_settings = settings.clone();
	}
	

	/**
	 * Turns the scanner off.
	 * 
	 * @return {@link S400WResponse#EOF}
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public S400WResponse poweroff() throws IOException, InterruptedIOException
	{
		try ( UncheckedCloseable connection = open() ) {
			sendCommand(POWER_OFF);
			S400WResponse response = S400WResponse.EOF;
			logResponse("poweroff()", response);
			return response;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "poweroff()", e);
			throw e;
		}
	}
	
	
	/**
	 * Reads the scanner's version.
	 * 
	 * @return a version string, {@link S400WResponse#EOF}, or {@link S400WResponse#EMPTY} if timeout.
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public S400WResponse getVersion() throws IOException, InterruptedIOException
	{
		try ( UncheckedCloseable connection = open() ) {
			sendCommand(GET_VERSION);
			S400WResponse response = readResponse();
			logResponse("getVersion", response);
			return response;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "getVersion()", e);
			throw e;
		}
	}

	
	/**
	 * Reads scanner's current status.
	 * 
	 * @return response, {@link S400WResponse#EOF}, or {@link S400WResponse#EMPTY} if timeout.
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public S400WResponse getStatus() throws IOException, InterruptedIOException
	{
		try ( UncheckedCloseable connection = open() ) {
			sendCommand(GET_STATUS);
			S400WResponse response = readResponse();
			logResponse("getStatus()", response);
			return response;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "getStatus()", e);
			throw e;
		}
	}

	
	/**
	 * Reads scanner's current battery state.
	 * Returns a hex number string.
	 * 
	 * @return response, {@link S400WResponse#EOF}, or {@link S400WResponse#EMPTY} if timeout.
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public S400WResponse getBatteryState() throws IOException, InterruptedIOException
	{
		try ( UncheckedCloseable connection = open() ) {
			sendCommand(GET_BATTERY_STATE);
			S400WResponse response = readResponse();
			logResponse("getBatteryState()", response);
			return response;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "getBatteryState()", e);
			throw e;
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
		try ( UncheckedCloseable connection = open() ) {
			sendCommand(dpi==600 ? SET_DPI_HIGH : SET_DPI_STANDARD);
			S400WResponse response = readResponse();
			logResponse("setResolution(" + dpi + ")", response);
			return dpi==600 && response==S400WResponse.DPI_HIGH || dpi!=600 && response==S400WResponse.DPI_STANDARD;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "setResolution()", e);
			throw e;
		}
	}


	/**
	 * Executes the scanner's cleaning routine.
	 * 
	 * @return {@link S400WResponse#CLEAN_END} if sucessfully finished, any other otherwise, 
	 *  including {@link S400WResponse#EOF} and {@link S400WResponse#EMPTY} for timeouts,
	 *  ({@link S400WResponse#NOPAPER} if cleaning sheet is not inserted).
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public S400WResponse clean() throws IOException, InterruptedIOException
	{
		try ( UncheckedCloseable connection = open() ) {
			sendCommand(GET_STATUS);
			S400WResponse response = readResponse();
			logResponse("clean().check", response);
			if ( response!=S400WResponse.SCAN_READY ) return response;

			sendCommand(START_CLEANING);
			sleep(500);
			response = readResponse();
			logResponse("clean().go", response);
			if ( response!=S400WResponse.CLEAN_GO ) return response;
			
			response = readResponse(_settings.timeoutClean);
			logResponse("clean().end", response);
			return response;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "clean()", e);
			throw e;
		}
	}

	
	/**
	 * Executes the scanner's calibration routine.
	 * 
	 * @return {@link S400WResponse#CALIBRATE_END} if sucessfully finished, any other otherwise
	 *  including {@link S400WResponse#EOF} and {@link S400WResponse#EMPTY} for timeouts,
	 *  ({@link S400WResponse#NOPAPER} if calibration sheet is not inserted).
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public S400WResponse calibrate() throws IOException, InterruptedIOException
	{
		try ( UncheckedCloseable connection = open() ) {
			sendCommand(GET_STATUS);
			S400WResponse response = readResponse();
			logResponse("calibrate().check", response);
			if ( response!=S400WResponse.SCAN_READY ) return response;
			
			sendCommand(START_CALIBRATION);
			sleep(500);
			response = readResponse();
			logResponse("calibrate().go", response);
			if ( response!=S400WResponse.CALIBRATE_GO ) return response;
			
			response = readResponse(_settings.timeoutCalibrate);
			logResponse("calibrate().end", response);
			return response;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "calibrate()", e);
			throw e;
		}
	}
	

	/**
	 * Executes the scanner's scanning procedure.
	 * 
	 * @param resolution resolution setting, or <code>0</code> if no setting is supported / desired
	 * @param preview callback handler for preview data, or {@link S400WResponse#EMPTY} if no preview should be read
	 * @param jpeg callback handler for jpeg data
	 * @return {@link S400WResponse#SCAN_READY} if sucessfully finished, any other response otherwise.
	 * 	including {@link S400WResponse#EOF} and  {@link S400WResponse#EMPTY} for timeouts
	 * @throws IOException if IO errors occurred.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	public S400WResponse scan(int resolution, ScanDataReceiver preview, ScanDataReceiver jpeg) throws IOException, InterruptedIOException
	{
		try ( UncheckedCloseable connection = open() ) {
			sendCommand(GET_STATUS);
			S400WResponse response = readResponse();
			logResponse("scan().check", response);
			if ( response!=S400WResponse.SCAN_READY ) return response;

			if ( resolution>0 ) {
				sendCommand(resolution==600 ? SET_DPI_HIGH : SET_DPI_STANDARD);
				response = readResponse();
				logResponse("scan().dpi" + resolution, response);
				if ( resolution==600 && response!=S400WResponse.DPI_HIGH || resolution!=600 && response!=S400WResponse.DPI_STANDARD ) return response;
			}
			
			sendCommand(START_SCAN);
			response = readResponse();
			logResponse("scan().go", response);
			if ( response!=S400WResponse.SCAN_GO ) return response;

			final boolean isFiner = log.isLoggable(Level.FINER);
			final int tagLength   = S400WResponse.PREVIEW_END.length() + 1;
			final byte[] buffer   = new byte[61440 + tagLength];  // buffer size arbitrarily chosen to be 32 lines
			
			if ( preview!=null ) {
				sendCommand(SEND_PREVIEW_DATA);
				sleep(1000);
				ByteBuffer partBuf = ByteBuffer.wrap(buffer, tagLength, buffer.length - tagLength).slice();
				int read = receive(partBuf, _settings.timeoutData);

				// if known response = error
				if ( read>0 ) {
					response = S400WResponse.find(buffer, partBuf.arrayOffset(), read);
					if ( response!=null ) {
						logResponse("scan().preview", response);
						return response;
					}
					preview.open(-1);
				}

				// this is a bit tricky. we need to carry over bytes in between fetching 
				// so we can detect the end marker even if it is torn apart.
				int total = 0;
				while ( read>0 ) {
					total += read;
					if ( isFiner ) log.finer("scan().preview: " + total + " ( " + (total / (1920)) + " lines)");
					preview.write(buffer, partBuf.arrayOffset(), read);
					System.arraycopy(buffer, read, buffer, 0, tagLength);
					if ( S400WResponse.PREVIEW_END.matches(buffer, 0, tagLength) ) break;
					partBuf.clear();
					read = receive(partBuf, _settings.timeoutData);
				}
				// TODO: not in finally, hmm
				preview.close();
				if ( read==0 ) return S400WResponse.EMPTY;
				if ( read <0 ) return S400WResponse.EOF;
			}
			
			if ( jpeg!=null ) {
				if ( preview!=null ) sleep(1000);
				final Duration sizeTimeout = preview==null ? _settings.timeoutSize.plus(_settings.timeoutSkipped) : _settings.timeoutSize;

				sendCommand(GET_JPEG_SIZE);
				response = readResponse(sizeTimeout);
				logResponse("scan().jpegsize", response);
				if ( response!=S400WResponse.JPEG_SIZE ) return response;
				
				// TODO: a bit lazy here, not checking if size read == JPEG_SIZE.length + 4...
				// TODO: also a little endian byte buffer has a getInteger method...
				final int size
					= 0x000000FF & (_buffer[S400WResponse.JPEG_SIZE.length()]) 
					| 0x0000FF00 & (_buffer[S400WResponse.JPEG_SIZE.length() + 1] << 8)
					| 0x00FF0000 & (_buffer[S400WResponse.JPEG_SIZE.length() + 2] << 16)
					| 0xFF000000 & (_buffer[S400WResponse.JPEG_SIZE.length() + 3] << 24);
				if ( log.isLoggable(Level.FINE) ) log.fine(String.format(Locale.ROOT, "scan().jpeg: %,d bytes", size));

				jpeg.open(size);
				try {
					ByteBuffer buf = ByteBuffer.wrap(buffer);
					sendCommand(SEND_JPEG_DATA);
					sleep(500);
					int total = 0;
					int read = 0; 
					do {
						buf.clear();
						read = receive(buf, _settings.timeoutData);
						if ( read>0 ) {
							total += read;
							if ( isFiner ) log.finer("scan().jpeg: " + total + "/" + size + " bytes");
							jpeg.write(buffer, 0, read);
						}
					} while ( total<size && read>0 );
					if ( read==0 ) return S400WResponse.EMPTY;
					if ( read <0 ) return S400WResponse.EOF;
				} finally {
					jpeg.close();
				}
			}
				
			return S400WResponse.SCAN_READY;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "scan()", e);
			throw e;
		}
	}
	
	
	/**
	 * Opens a socket to the target.
	 * 
	 * @throws IOException If the socket cannot be opened and set up correctly.
	 */
	private UncheckedCloseable open() throws IOException, InterruptedIOException
	{
		if ( log.isLoggable(Level.FINE) ) log.fine("opening socket to " + _settings.host + ":" + _settings.port);
		_selector = Selector.open();
		_socket = SocketChannel.open();
		_socket.configureBlocking(false);
		if ( !_socket.connect(new InetSocketAddress(_settings.host, _settings.port)) ) {
			SelectionKey key = _socket.register(_selector, SelectionKey.OP_CONNECT);
			select(_settings.timeoutConnect);
			if ( !_socket.finishConnect() ) throw new IOException("Couldn't connect to scanner");
			key.interestOps(SelectionKey.OP_READ);
		} else {
			_socket.register(_selector, SelectionKey.OP_READ);
		}
		return this::close;
	}
	
	
	/**
	 * Closes the target connection.
	 */
	private void close()
	{
		if ( _socket  !=null ) try { _socket  .close(); } catch (Exception e) {}
		if ( _selector!=null ) try { _selector.close(); } catch (Exception e) {} 
		_socket = null;
		_selector = null;
		
	}
	
	
	/**
	 * Sends a command to the target. Sleeps before and after sending.
	 * 
	 * @param command the command, a little endian unsigned integer.
	 * @throws IOException if sending the buffer fails.
	 * @throws InterruptedIOException if interrupted while sleeping.
	 */
	private void sendCommand(int command) throws IOException, InterruptedIOException
	{
		if ( log.isLoggable(Level.FINE) ) log.fine(String.format(Locale.ROOT, "sendCommand(%08X)", command));
		sleep(200);
		if ( _socket.write(ByteBuffer.allocate(4).putInt(0, Integer.reverseBytes(command)))>0 ) sleep(200);
	}
	

	/**
	 * Equivalent to {@link #readResponse(int)} with {@link S400WSettings#timeoutStandard} as timeout.
	 */
	private S400WResponse readResponse() throws IOException
	{
		return readResponse(_settings.timeoutStandard);
	}	
	
	
	/**
	 * Reads in a short response using a {@link #_buffer},
	 * {@link #receive(byte[], Duration)} and {@link S400WResponse#get(byte[], int, int)}.
	 *  
	 * @param timeout seconds to wait for a response
	 * @return the detected response 
	 * @throws IOException If errors occurred while reading the response
	 */
	private S400WResponse readResponse(Duration timeout) throws IOException
	{
		Arrays.fill(_buffer, (byte)0);
		return S400WResponse.get(_buffer, 0, receive(_buffer, timeout));
	}	

	
	/**
	 * Convenience method for response logging.
	 */
	private void logResponse(String method, S400WResponse response)
	{
		if ( log.isLoggable(Level.FINE) ) log.fine(method + ": " + response + ", known=" + response.isKnown());
	}

	
	/**
	 * Equivalent to {@link #receive(ByteBuffer, int)} with the buffer wrapped into a {@link ByteBuffer}.
	 */
	private int receive(byte[] buffer, Duration timeout) throws IOException
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
	private int receive(ByteBuffer buffer, Duration timeout) throws IOException
	{
		if ( select(timeout)>0 ) {
			int r = _socket.read(buffer);
			if ( log.isLoggable(Level.FINER) ) log.finer("receive(): read " + r + " bytes");
			return r; // shouldn't be 0
		}
		if ( log.isLoggable(Level.FINER) ) log.finer("receive(): no data");
		return 0;
	}

	
	private int select(final Duration duration) throws IOException
	{
		for ( Iterator<SelectionKey> it = _selector.selectedKeys().iterator(); it.hasNext(); it.remove() ) it.next();
		final long bogusTimeout = _settings.timeoutSelect.toMillis();
		long timeout = duration.toMillis();
		long start = System.currentTimeMillis(), end = start + timeout;
		while ( timeout>0 ) {
			int result = _selector.select(timeout);
			if ( result!=0 ) return result;

			long now = System.currentTimeMillis();
			timeout = end - now;

			// spurious wakeup detected, possible faulty jvm with immediate wakeup
			if ( timeout>0 ) {
				if ( start - now < bogusTimeout ) {
					sleep(Math.min(timeout, bogusTimeout));
					now = System.currentTimeMillis();
					timeout = Math.max(1, end - now);
				}
				start = now;
			}
		}
		return 0;
	}
	

	/**
	 * <code>Thread.sleep</code> wrapper.
	 */
	private static void sleep(long ms) throws InterruptedIOException
	{
		// i had some trouble using normal sleep, probably due to to deep sleep states on the cpu
		//long then = ms*1000000L + System.nanoTime();
		//do {
		//	Thread.sleep(ms / 10 + 1);
		//} while ( System.nanoTime()<then );
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			throw (InterruptedIOException)new InterruptedIOException("Interrupted timeout: " + e.getMessage()).initCause(e.getCause());
		}
	}

	
	private interface UncheckedCloseable extends Closeable
	{
		@Override
		void close();
	}
	
}
