/* This file is licensed under CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).
 *
 * Created 2014-10-12 by bastel.
 */

package hwfs400w;

import hwfs400w.S400W.Response;
import hwfs400w.S400W.ScanDataReceiver;

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
	public static void main(final String[] args) throws IOException, InterruptedException
	{
		Handler h = new ConsoleHandler();
		h.setLevel(Level.FINE);
		Logger.getLogger(Scanner.class.getPackage().getName()).addHandler(h);
		Logger.getLogger(Scanner.class.getPackage().getName()).setLevel(Level.FINEST);
		S400W device = new S400W();
		Response response;
		if ( "version".equals(args[0])  ) {
			response = device.getVersion();
			if ( response.isEmpty() || response.isEOF() || response.isKnown() ) System.exit(-1);
			System.out.println(response);
		}

		else if ( "poweroff".equals(args[0])  ) {
			response = device.poweroff();
			if ( response.isEmpty() || response.isEOF() || response.isKnown() ) System.exit(-1);
			System.out.println(response);
		}

		else if ( "status".equals(args[0]) ) {
			while ( true ) {
				response = device.getStatus();
				if ( response.isEmpty() || response.isEOF() ) System.exit(-1);
				System.out.println(response);
				Thread.sleep(5000);
			}
		}

		else if ( "battery".equals(args[0]) ) {
			while ( true ) {
				response = device.getBatteryState();
				if ( response.isEmpty() || response.isEOF() ) System.exit(-1);
				System.out.println(response);
				if ( response.isKnown() ) System.exit(-1);
				Thread.sleep(5000);
			}
		}

		else if ( "dpi".equals(args[0]) ) {
			if ( args.length>1 ) {
				if ( "300".equals(args[1]) ) System.exit(device.setResolution(300) ? 0 : -1);
				if ( "600".equals(args[1]) ) System.exit(device.setResolution(600) ? 0 : -1);
			}
			System.exit(-1);
		}

		else if ( "clean".equals(args[0]) ) {
			response = device.clean();
			if ( response!=Response.CLEAN_END ) System.exit(-1);
			System.out.println(response);
		}

		else if ( "calibrate".equals(args[0]) ) {
			response = device.calibrate();
			if ( response!=Response.CALIBRATE_END ) System.exit(-1);
			System.out.println(response);
		}

		else if ( "preview".equals(args[0]) ) {
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			response = device.scan(0, os::write, null);
			if ( response!=Response.SCAN_READY ) System.exit(-1);
			FileOutputStream fos = new FileOutputStream("./" + System.currentTimeMillis() + ".raw");
			fos.write(os.toByteArray());
			fos.close();
			System.out.println(response);
		}

		else if ( "scan".equals(args[0]) ) {
			final int dpi  = args.length <3  ? 0 : "dpi300".equals(args[1]) ? 300 : "dpi600".equals(args[1]) ? 600 : 0;
			final String name = args.length==1 ? ("./" + System.currentTimeMillis() + ".jpg") : args.length==2 ? args[1] : args[2];
			response = device.scan(dpi, null, new ScanDataReceiver() {
				FileOutputStream o = null;
				@Override
				public void open(long length) throws IOException {
					o = new FileOutputStream(name);
				}
				@Override
				public void write(byte[] array, int offset, int length) throws IOException {
					o.write(array, offset, length);
				}
				@Override
				public void close() throws IOException {
					o.close();
				}
			});
			if ( response!=Response.SCAN_READY ) System.exit(-1);
			System.out.println(response);
		}
	}
}
