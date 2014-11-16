/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).  
 *
 * Created 2014-10-12 by bastel.
 */

package hwfs400w;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * 
 * @author bastel
 */
public class S400WConnection
{
	private final static Logger log = Logger.getLogger(S400WConnection.class.getName());
	
	public interface Receiver
	{
		public boolean receive(byte[] data, int offset, int length) throws IOException;
	}
	
	
	/** Firmware version necessary to set (higher) dpi. */
	public final static int MIN_DPI600_FW = 26;
	
	// Commands
	public final static byte[] GET_VERSION       = {  0x30,  0x30, 0x20,  0x20 };
	public final static byte[] GET_STATUS        = {  0x00,  0x60, 0x00,  0x50 };
	public final static byte[] START_CLEANING    = { -0x80, -0x80, 0x70,  0x70 };
	public final static byte[] START_CALIBRATION = {  0x00, -0x50, 0x00, -0x60 };
	public final static byte[] SET_DPI_300       = {  0x40,  0x30, 0x20,  0x10 };
	public final static byte[] SET_DPI_600       = { -0x80,  0x70, 0x60,  0x50 };
	public final static byte[] START_SCAN        = {  0x00,  0x20, 0x00,  0x10 };
	public final static byte[] GET_PREVIEW_DATA  = {  0x40,  0x40, 0x30,  0x30 };
	public final static byte[] GET_JPEG_SIZE     = {  0x00, -0x30, 0x00, -0x40 };
	public final static byte[] GET_JPEG_DATA     = {  0x00, -0x10, 0x00, -0x20 };

	// Responses (note that no response starts with another response, so no terminating character necessary)
	public final static byte[] DEVICE_BUSY       = { 'd','e','v','b','u','s','y' };
	public final static byte[] BATTERY_LOW       = { 'b','a','t','t','l','o','w' };
	public final static byte[] NOPAPER           = { 'n','o','p','a','p','e','r' };
	public final static byte[] SCAN_READY        = { 's','c','a','n','r','e','a','d','y' };
	public final static byte[] CALIBRATE_GO      = { 'c','a','l','g','o' };
	public final static byte[] CALIBRATE_END     = { 'c','a','l','i','b','r','a','t','e' };
	public final static byte[] CLEAN_GO          = { 'c','l','e','a','n','g','o' };
	public final static byte[] CLEAN_END         = { 'c','l','e','a','n','e','n','d' };
	public final static byte[] DPI_STANDARD      = { 'd','p','i','s','t','d' };
	public final static byte[] DPI_HIGH          = { 'd','p','i','f','i','n','e' };
	public final static byte[] SCAN_GO           = { 's','c','a','n','g','o' };
	public final static byte[] PREVIEW_END       = { 'p','r','e','v','i','e','w','e','n','d' };
	public final static byte[] JPEG_SIZE         = { 'j','p','e','g','s','i','z','e' };
	
	// for mapping raw to constants
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
	
	public final static byte[] EOF = {};

	/** Internal receive buffer, default responses should fit in 16 bytes */
	private byte[] _buf = new byte[16];
	
	/** To check socket for incoming bytes */ 
	private Selector _selector;
	
	/** IO Channel */
	volatile private SocketChannel _socket = null;
	
	private int _timeout = 10000;
	
	private String _targetHost;
	private int _targetPort;
	
	
	public S400WConnection()
	{
		this("192.168.18.33", 23);
	}
	

	public S400WConnection(String host, int port)
	{
		_targetHost = host;
		_targetPort = port;
	}
	
	
	private void openSocket() throws IOException
	{
		if ( log.isLoggable(Level.FINE) ) log.fine("opening socket to " + _targetHost + ":" + _targetPort);
		_selector = Selector.open();
		_socket = SocketChannel.open();
		_socket.connect(new InetSocketAddress(_targetHost, _targetPort));
		_socket.finishConnect();
		_socket.configureBlocking(false);
		_socket.register(_selector, SelectionKey.OP_READ);
	}
	
	
	private void closeSocket()
	{
		if ( _socket  !=null ) try { _socket  .close(); } catch (Exception e) {}
		if ( _selector!=null ) try { _selector.close(); } catch (Exception e) {} 
		_socket = null;
		_selector = null;
		
	}
	
	
	protected void sendCommand(byte[] cmd) throws InterruptedException, IOException
	{
		if ( log.isLoggable(Level.FINE) ) log.fine("sendCommand(" + Arrays.toString(cmd) + ")");
		_socket.write(ByteBuffer.wrap(cmd));
		sleep(100);
	}
	

	protected int receive(byte[] buf) throws IOException
	{
		return receive(buf, _timeout);
	}

	
	protected int receive(byte[] buf, int timeout) throws IOException
	{
		return receive(ByteBuffer.wrap(buf), timeout);
	}
	
	
	/**
	 * @return Number of bytes read (0 possible) or -1 for EOF.
	 */
	protected int receive(ByteBuffer buf, int timeout) throws IOException
	{
		if ( _selector.select(timeout)>0 ) {
			int r = _socket.read(buf);
			if ( log.isLoggable(Level.FINER) ) log.finer("receive(): read " + r + " bytes");
			_selector.selectedKeys().clear();
			return r; // shouldn't be 0
		}
		if ( log.isLoggable(Level.FINER) ) log.finer("receive(): no data");
		return 0;
	}

	
	/**
	 * @return Response constant matching the response or <code>null</code> if unknown response. 
	 */
	protected byte[] detectResponse(byte[] buf, int length)
	{
		if ( length <0 ) return EOF;
		if ( length==0 ) return null;
		for ( int i = 0; i<RESPONSES.length; i++ ) {
			if ( length>=RESPONSES[i].length && cmp(buf, 0, RESPONSES[i], 0, RESPONSES[i].length) ) return RESPONSES[i];
		}
		return buf;
	}	
	

	protected byte[] readResponse() throws IOException
	{
		return readResponse(_timeout);
	}	
	
	
	protected byte[] readResponse(int timeout) throws IOException
	{
		Arrays.fill(_buf, (byte)0);
		return detectResponse(_buf, receive(_buf, timeout));
	}	

	
	/**
	 * Reads version.
	 * @return Version. Can be converted to string with US-ASCII encoding.
	 */
	public byte[] getVersion() throws InterruptedException, IOException
	{
		try {
			openSocket();
			sendCommand(GET_VERSION);
			sleep(200);
			int read = receive(_buf);
			if ( read>0 ) {
				if ( log.isLoggable(Level.FINE) ) log.fine("getVersion(): " + new String(_buf, 0, read, "US-ASCII"));
				return Arrays.copyOf(_buf, read);
			}
			if ( log.isLoggable(Level.FINE) ) log.fine("getVersion(): no data");
			return read==0 ? null : EOF;
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
	 * Reads status.
	 * @return Response code. <code>null</code> if no data. Can be converted to string with US-ASCII encoding.
	 */
	public byte[] getStatus() throws InterruptedException, IOException
	{
		try {
			openSocket();
			sendCommand(GET_STATUS);
			sleep(200);
			int read = receive(_buf);
			byte[] response = detectResponse(_buf, read);
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
	
	
	public boolean setDPI(int dpi) throws InterruptedException, IOException
	{
		try {
			openSocket();
			sendCommand(dpi==600 ? SET_DPI_600 : SET_DPI_300);
			sleep(200);
			byte[] response = readResponse();
			logResponse("setDPI(" + dpi + ")", response);
			return dpi==600 && response==DPI_HIGH || dpi!=600 && response==DPI_STANDARD;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "setDPI()", e);
			throw e;
		}
		finally {
			closeSocket();
		}
	}


	public byte[] clean() throws InterruptedException, IOException
	{
		try {
			openSocket();
			sendCommand(GET_STATUS);
			sleep(200);
			byte[] response = readResponse();
			logResponse("clean().check", response);
			if ( response!=SCAN_READY ) return response;

			sendCommand(START_CLEANING);
			sleep(500);
			response = readResponse();
			logResponse("clean().go", response);
			if ( response!=CLEAN_GO ) return response;
			
			response = readResponse(Integer.getInteger(getClass().getName() + ".timeout.clean", 30000));
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

	
	public byte[] calibrate() throws InterruptedException, IOException
	{
		try {
			openSocket();
			sendCommand(GET_STATUS);
			sleep(200);
			byte[] response = readResponse();
			logResponse("calibrate().check", response);
			if ( response!=SCAN_READY ) return response;
			
			sendCommand(START_CALIBRATION);
			sleep(500);
			response = readResponse();
			logResponse("calibrate().go", response);
			if ( response!=CALIBRATE_GO ) return response;
			
			response = readResponse(Integer.getInteger(getClass().getName() + ".timeout.calibrate", 60000));
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
	

	public byte[] scan(Integer dpi, Receiver preview, Receiver jpeg) throws InterruptedException, IOException
	{
		try {
			openSocket();
			sendCommand(GET_STATUS);
			sleep(200);
			byte[] response = readResponse();
			logResponse("scan().check", response);
			if ( response!=SCAN_READY ) return response;

			if ( dpi!=null) {
				sendCommand(dpi==600 ? SET_DPI_600 : SET_DPI_300);
				sleep(200);
				response = readResponse();
				logResponse("scan().dpi" + dpi, response);
				if ( dpi==600 && response!=DPI_HIGH || dpi!=600 && response!=DPI_STANDARD ) return response;
			}
			
			sendCommand(START_SCAN);
			sleep(200);
			response = readResponse();
			logResponse("scan().go", response);
			if ( response!=SCAN_GO ) return response;

			boolean isFiner  = log.isLoggable(Level.FINER);
			
			if ( preview!=null ) {
				final int timeout = Integer.getInteger(getClass().getName() + ".timeout.preview", 60000);
				final int taglen  = PREVIEW_END.length + 1;

				sendCommand(GET_PREVIEW_DATA);
				sleep(1000);
				ByteBuffer buf = ByteBuffer.allocate(640*3*20 + taglen);
				int read = receive(buf, timeout);

				// if known response = error
				if ( read>0 && read<=16 && detectResponse(buf.array(), read)!=buf.array() ) {
					return detectResponse(buf.array(), read);
				}

				// this is a bit tricky. we need to carry over bytes in between fetching 
				// so we can detect the end marker even if it is torn apart.
				int total = 0;
				while ( read>0 ) {
					total += read;
					if ( isFiner ) log.finer("scan().preview: " + total + " ( " + (total / (640*3)) + " lines)");
					int end = buf.position();
					preview.receive(buf.array(), end - read, read);
					if ( end<taglen || cmp(PREVIEW_END, 0, buf.array(), end - taglen, PREVIEW_END.length) ) break; 					
					buf.position(end - taglen);
					buf.compact();
					read = receive(buf, timeout);
				}
				preview.receive(null,  0, 0);
				if ( read==0 ) return null;
				if ( read <0 ) return EOF;
			}
			
			if ( jpeg!=null ) {
				final int timeout = Integer.getInteger(getClass().getName() + ".timeout.jpeg", 60000);

				sendCommand(GET_JPEG_SIZE);
				sleep(200);
				response = readResponse(timeout);
				logResponse("scan().jpegsize", response);
				if ( response!=JPEG_SIZE ) return response;
				// TODO: a bit lazy here, not checking if size read == JPEG_SIZE.length + 4...
				final int size = 0x000000FF & _buf[JPEG_SIZE.length] 
					| 0xFF000000 & (_buf[JPEG_SIZE.length + 3] << 24)
					| 0x00FF0000 & (_buf[JPEG_SIZE.length + 2] << 16)
					| 0x0000FF00 & (_buf[JPEG_SIZE.length + 1] << 8);
				if ( log.isLoggable(Level.FINE) ) log.log(Level.FINE, "scan().jpeg: {0,number} bytes", size);

				// report size to jpeg receiver!
				jpeg.receive(null, 0, size);
				int total = 0;
				int read = 0; 
				try {
					sendCommand(GET_JPEG_DATA);
					sleep(500);
					ByteBuffer buf = ByteBuffer.allocate(32768);
					do {
						read = receive(buf, timeout);
						if ( read>0 ) {
							total += read;
							if ( isFiner ) log.finer("scan().jpeg: " + total + "/" + size + " bytes");
							jpeg.receive(buf.array(), 0, read);
							buf.clear();
						}
					} while ( total<size && read>0 );
				} finally {
					jpeg.receive(null,  0, 0);
				}
				if ( read==0 ) return null;
				if ( read <0 ) return EOF;
			}
				
			return SCAN_READY;
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "clean()", e);
			throw e;
		}
		finally {
			closeSocket();
		}
	}
	
	
	public static int getResponseLength(byte[] buf)
	{
		int l = 0;
		while ( l<buf.length && buf[l]!=0 ) l++;
		return l;
	}
	
	
	public static String toString(byte[] buf)
	{
		try {
			return new String(buf, 0, getResponseLength(buf), "US-ASCII");
		}
		catch (UnsupportedEncodingException e) {
			return new String(buf, 0, getResponseLength(buf));
		}
	}

	
	private void logResponse(String method, byte[] response)
	{
		if ( log.isLoggable(Level.FINE) ) {
			if ( response==null ) {
				log.fine(method + ": <no data>");
			} else {
				int i = 0;
				while ( i<response.length && response[i]!=0 ) i++;
				try {
					log.fine(method + ": " + (response==EOF ? "<EOF>" : new String(response, 0, i, "US-ASCII")));
				}
				catch (UnsupportedEncodingException e) {
					log.fine(method + ": " + (response==EOF ? "<EOF>" : new String(response, 0 , i)));
				}
			}
		}
	}

	
//	private boolean cmp(byte[] src, int srcOffset, byte[] dst, int dstOffset)
//	{
//		return cmp(src, srcOffset, dst, dstOffset, Math.min(src.length - srcOffset, dst.length - dstOffset));
//		
//	}

	private boolean cmp(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length)
	{
		if ( length>0 && srcOffset>=0 && dstOffset>=0 && srcOffset + length <= src.length && dstOffset + length <= dst.length ) {
			while ( length-->0 ) if ( src[srcOffset++]!=dst[dstOffset++] ) return false;
			return true;
		}
		return false;
	}
	
	
	static void sleep(int ms) throws InterruptedException
	{
		long then = ms*1000000L + System.nanoTime();
		do {
			Thread.sleep(ms / 10 + 1);
		} while ( System.nanoTime()<then );
	}
	
}
