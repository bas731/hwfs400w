/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).  
 *
 * Created 2022-12-28 by bastel.
 */

package hwfs400w;

import java.time.Duration;

/**
 * S400W settings.
 *
 * @author bastel
 * @since 2022-12-28
 */
public class S400WSettings implements Cloneable
{
	/** Prefix for properties, usually &lt;package-name&gt;.&lt;class-name&gt; of {@link S400W} class. */ 
	private final static String PROPERTY_KEY = S400W.class.getName();
	
	/** Scanners host name / address, default is {@literal 192.168.18.33}. */
	public String host = System.getProperty(PROPERTY_KEY + ".target.host", "192.168.18.33");
	
	/** Scanner's port, default is {@literal 23}. */
	public int port    = Integer.getInteger(PROPERTY_KEY + ".target.port", 23);
	
	/** Standard timeout for simple calls that don't start things, default: 10 seconds. */
	public Duration timeoutStandard = parse("", "10");
	
	/** Timeout for socket connect, default: 5 seconds. */
	public Duration timeoutConnect  = parse("connect", "5");
	
	/** Timeout for {@link S400W#clean()}, default: 40 seconds. */
	public Duration timeoutClean    = parse("scan.clean", "40");
	
	/** Timeout for {@link S400W#scan(int, ScanDataReceiver, ScanDataReceiver)}, default: 60 seconds. */
	public Duration timeoutCalibrate = parse("scan.calibrate", "60");
	
	/** Timeout for {@link S400W#scan(int, ScanDataReceiver, ScanDataReceiver)}, preview and jpeg data, default: 30 seconds. */
	public Duration timeoutData     = parse("scan.data", "30");
	
	/** Timeout for {@link S400W#GET_JPEG_SIZE} if preview was requested, default: 20 seconds */
	public Duration timeoutSize     = parse("scan.jpegSize", "20");
	
	/** Timeout for {@link S400W#GET_JPEG_SIZE} to add to {@link #timeoutSize} if no preview was requested, default: 40 seconds. */
	public Duration timeoutSkipped  = parse("scan.skipPreview", "40");
	
	/** Timeout to detect bogus select() wait times, default: 0.01 seconds. */
	public Duration timeoutSelect   = parse("select", "0.01");
	
	
	@SuppressWarnings("hiding")
	S400WSettings with(String host, int port)
	{
		this.host = host;
		this.port = port;
		return this;
	}
	
	
	@Override
	public S400WSettings clone()
	{
		try {
			return (S400WSettings)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
	}

	
	@Override
	public String toString()
	{
		return String.format(
			"Settings [host=%s:%s, timeouts: standard=%s, connect=%s, clean=%s, calibrate=%s, data=%s, size=%s, skipped=%s, select=%s]",
			host, port, timeoutStandard, timeoutConnect, timeoutClean, timeoutCalibrate, timeoutData, timeoutSize, timeoutSkipped, timeoutSelect);
	}
	
	
	private static Duration parse(String key, String defaultValue)
	{
		String raw = System.getProperty(PROPERTY_KEY + (key.isEmpty() ? ".timeout" : ".timeout.") + key, defaultValue);
		return Duration.parse("PT" + raw + "S");
	}

}
