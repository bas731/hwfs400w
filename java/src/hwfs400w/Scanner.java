/* This file is licensed under CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).  
 *
 * Created 2014-10-12 by bastel.
 */

package hwfs400w;

import hwfs400w.S400W.Receiver;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple command line scanner application for demonstration purposes.
 * <p>
 * This file is licensed under the <a href="http://creativecommons.org/publicdomain/zero/1.0/">Creative Commons License CC-CC0 1.0</a>.
 * 
 * @author bastel
 */
public class Scanner
{
	public static void main(final String[] args) throws InterruptedException, IOException
	{
		Handler h = new ConsoleHandler();
		h.setLevel(Level.FINE);
		Logger.getLogger(Scanner.class.getPackage().getName()).addHandler(h);
		Logger.getLogger(Scanner.class.getPackage().getName()).setLevel(Level.FINEST);
		S400W r = new S400W();
		byte[] buf;
		if ( "version".equals(args[0])  ) {
			buf = r.getVersion();
			if ( buf==null || buf==S400W.EOF || S400W.isKnownResponse(buf) ) System.exit(-1);
			System.out.println(S400W.toString(buf));
		}
		
		else if ( "status".equals(args[0]) ) {
			while ( true ) {
				buf = r.getStatus();
				if ( buf==null || buf==S400W.EOF ) System.exit(-1);
				System.out.println(S400W.toString(buf));
				S400W.sleep(5000);
			}
		}
		
		else if ( "dpi".equals(args[0]) ) {
			if ( args.length>1 ) {
				if ( "300".equals(args[1]) ) System.exit(r.setResolution(300) ? 0 : -1);
				if ( "600".equals(args[1]) ) System.exit(r.setResolution(600) ? 0 : -1);
			}
			System.exit(-1);
		}

		else if ( "clean".equals(args[0]) ) {
			buf = r.clean();
			if ( buf!=S400W.CLEAN_END ) System.exit(-1);
			System.out.println(S400W.toString(buf));
		}
		
		else if ( "calibrate".equals(args[0]) ) {
			buf = r.calibrate();
			if ( buf!=S400W.CALIBRATE_END ) System.exit(-1);
			System.out.println(S400W.toString(buf));
		}

		else if ( "preview".equals(args[0]) ) {
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			buf = r.scan(0, new Receiver() {
				@Override public boolean receive(byte[] data, int offset, int length) throws IOException {
					if ( data!=S400W.EOF && length>0 ) os.write(data,  offset, length);
					return true;
				}
			}, null);
			if ( buf!=S400W.SCAN_READY ) System.exit(-1);
			FileOutputStream fos = new FileOutputStream("./" + System.currentTimeMillis() + ".raw");
			fos.write(os.toByteArray());
			fos.close();
			System.out.println(S400W.toString(buf));
		}
			
		else if ( "scan".equals(args[0]) ) {
			final int dpi  = args.length <3  ? null : "dpi300".equals(args[1]) ? 300 : "dpi600".equals(args[1]) ? 600 : 0;
			final String  name = args.length==1 ? ("./" + System.currentTimeMillis() + ".jpg") : args.length==2 ? args[1] : args[2];
			buf = r.scan(dpi, null, new Receiver() {
				FileOutputStream o = null;
				@Override public boolean receive(byte[] data, int offset, int length) throws IOException {
					if ( data==S400W.JPEG_SIZE ) {
						o = new FileOutputStream(name);
					} else if ( data==S400W.EOF ) {
						o.close();
					} else {
						o.write(data, offset, length);
					}
					return true;
				}
			});
			if ( buf!=S400W.SCAN_READY ) System.exit(-1);
			System.out.println(S400W.toString(buf));
		}
	}
}
