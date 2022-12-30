/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).  
 *
 * Created 2014-10-12 by bastel.
 */

package hwfs400w;

import java.io.IOException;

/**
 * A simple callback interface used for receiving scan data data.
 *
 * @author bastel
 * @since 2014-10-12
 */
public interface ScanDataReceiver
{
	/**
	 * Notifies clients of begin of preview or jpeg data.
	 * 
	 * @param length {@code >=0L} if content length is known, {@code -1L} otherwise.
	 * This value is always known for jpeg data and unknown for preview data.
	 * @throws IOException If something goes wrong while processing the data.
	 */
	default void open(long length) throws IOException {
	}

	
	/**
	 * Notifies clients of preview or jepg data.
	 * 
	 * @param array data
	 * @param offset the offset inside the given array. 
	 * @param length number of bytes to read from data.
	 * @throws IOException If something goes wrong while processing the data.
	 */
	void write(byte[] array, int offset, int length) throws IOException;
	
	
	/**
	 * Notifies clients of end of preview or jpeg data.<br>
	 * Note: Guaranteed to be called if {@link #open(long)} has successfully been called (as to close file handles etc).
	 * @throws IOException If something goes wrong while processing the data.
	 */
	default void close() throws IOException {
	}
	
}
