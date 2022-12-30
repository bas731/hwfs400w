/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).  
 *
 * Created 2022-12-28 by bastel.
 */

package hwfs400w;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A S400W non-data response.
 * Known responses can be compared with {@code ==}.
 * 
 * @author bastel
 * @since 2022-12-28
 */
public final class S400WResponse
{
	// Responses (note that no response starts with another response, so no terminating character necessary)
	/** Response: Device busy */
	public final static S400WResponse DEVICE_BUSY   = new S400WResponse("devbusy");
	
	/** Response: Battery low */
	public final static S400WResponse BATTERY_LOW   = new S400WResponse("battlow");
	
	/** Response: No paper inserted */
	public final static S400WResponse NOPAPER       = new S400WResponse("nopaper");
	
	/** Response: Paper inserted, ready to scan, calibrate, clean */
	public final static S400WResponse SCAN_READY    = new S400WResponse("scanready");
	
	/** Response: Calibration has started */
	public final static S400WResponse CALIBRATE_GO  = new S400WResponse("calgo");
	
	/** Response: Calibration has finished */
	public final static S400WResponse CALIBRATE_END = new S400WResponse("calibrate");
	
	/** Response: Cleaning has started */
	public final static S400WResponse CLEAN_GO      = new S400WResponse("cleango");
	
	/** Response: Cleaning has finished */
	public final static S400WResponse CLEAN_END     = new S400WResponse("cleanend");
	
	/** Response: Standard DPI selected */
	public final static S400WResponse DPI_STANDARD  = new S400WResponse("dpistd");
	
	/** Response: High DPI selected */
	public final static S400WResponse DPI_HIGH      = new S400WResponse("dpifine");
	
	/** Response: Scanning has started */
	public final static S400WResponse SCAN_GO       = new S400WResponse("scango");
	
	/** Response: Preview data in stream end marker */
	public final static S400WResponse PREVIEW_END   = new S400WResponse("previewend");
	
	/** Response: JPEG size */
	public final static S400WResponse JPEG_SIZE     = new S400WResponse("jpegsize");
	
	/** Artifical response: EOF */
	final static S400WResponse EOF          = new S400WResponse(new byte[0]);
	
	/** Artifical response: EMPTY */
	final static S400WResponse EMPTY        = new S400WResponse(new byte[0]);
	
	
	private final static S400WResponse[] KNOWN = Arrays.stream(S400WResponse.class.getDeclaredFields())
		.filter(f -> Modifier.isStatic(f.getModifiers()) && S400WResponse.class.equals(f.getType()))
		.map(f -> { try {
				return (S400WResponse)f.get(null);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				return null;
			}})
		.filter(r -> r!=null && r.isKnown())
		.toArray(S400WResponse[]::new);
	
	private final boolean known;
	private final byte[] data;
	
	private S400WResponse(String data)
	{
		this.known = true;
		this.data = data.getBytes(StandardCharsets.US_ASCII);
	}

	
	private S400WResponse(byte[] data)
	{
		this.known = false;
		this.data = data;
	}
	
	
	@Override
	public String toString()
	{
		return this==EOF ? "<EOF>" : data.length==0 ? "<empty>" : new String(data, StandardCharsets.US_ASCII);
	}

	
	public boolean isKnown()
	{
		return known;
	}
	
	public boolean isEOF()
	{
		return this==EOF;
	}
	
	
	public boolean isEmpty()
	{
		return this==EMPTY;
	}
	
	
	int length()
	{
		return data.length;
	}
	
	
	boolean matches(byte[] array, int off, int limit)
	{
		return known && data.length<=limit && equals(data, 0, array, off, data.length);
	}
	
	
	static S400WResponse get(final byte[] array, int offset, int limit)
	{
		if ( limit==-1 ) return EOF;
		if ( limit== 0 ) return EMPTY;
		for ( S400WResponse r : KNOWN ) if ( r.matches(array, offset, limit) ) return r;
		limit = strlen(array, offset, limit);
		return new S400WResponse(limit==0 ? EMPTY.data : Arrays.copyOfRange(array, offset, limit));
	}
	
	
	static S400WResponse find(final byte[] array, int offset, int limit)
	{
		if ( limit==-1 ) return null;
		if ( limit== 0 ) return null;
		for ( S400WResponse r : KNOWN ) if ( r.matches(array, offset, limit) ) return r;
		return null;
	}
	
	
	private static int strlen(final byte[] array, final int offset, int length)
	{
		length += offset;
		int idx = offset;
		while ( idx<length && array[idx]!=0 ) idx++;
		return idx - offset;
	}
	
	
	/**
	 * Compares parts of two arrays for equality.
	 * @return <code>true</code> if the both parts match
	 */
	private static boolean equals(byte[] array1, int offset1, byte[] array2, int offset2, int length)
	{
		if ( length>0 && offset1>=0 && offset2>=0 && offset1 + length <= array1.length && offset2 + length <= array2.length ) {
			while ( length-->0 ) {
				if ( array1[offset1++]!=array2[offset2++] ) return false;
			}
			return true;
		}
		return false;
	}
}
