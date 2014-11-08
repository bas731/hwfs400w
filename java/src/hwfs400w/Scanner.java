/* Copyright (c) 2014 bastel
 *
 * file - created: 12.10.2014 by bastel
 *        id:      $Id$
 */

package hwfs400w;

import hwfs400w.S400WConnection.Receiver;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * 
 * @author bastel
 */
public class Scanner
{
	final static int FW300 = 25; // Last version not support DPI setting.
	
	int scan(OutputStream so, InputStream si, int dpi, String out)
	{
		return 0;
	}
	
	
	public static void main(final String[] args) throws InterruptedException, IOException
	{
		Handler h = new ConsoleHandler();
		h.setLevel(Level.FINEST);
		Logger.getLogger(Scanner.class.getPackage().getName()).addHandler(h);
		Logger.getLogger(Scanner.class.getPackage().getName()).setLevel(Level.FINEST);
		S400WConnection r = new S400WConnection();
		byte[] buf;
		if ( "version".equals(args[0])  ) {
			buf = r.getVersion();
			if ( buf==null || buf==S400WConnection.EOF ) System.exit(-1);
			System.out.println(S400WConnection.toString(buf));
		}
		
		else if ( "status".equals(args[0]) ) {
			while ( true ) {
				buf = r.getVersion();
				if ( buf==null || buf==S400WConnection.EOF ) System.exit(-1);
				System.out.println(S400WConnection.toString(buf));
				S400WConnection.sleep(5000);
			}
		}
		
		else if ( "dpi300".equals(args[0]) ) {
			if ( !r.setDPI(300) ) System.exit(-1);
		}

		else if ( "dpi600".equals(args[0]) ) {
			if ( !r.setDPI(600) ) System.exit(-1);
		}

		else if ( "clean".equals(args[0]) ) {
			buf = r.clean();
			if ( buf!=S400WConnection.CLEAN_END ) System.exit(-1);
			System.out.println(S400WConnection.toString(buf));
		}
		
		else if ( "calibrate".equals(args[0]) ) {
			buf = r.calibrate();
			if ( buf!=S400WConnection.CLEAN_END ) System.exit(-1);
			System.out.println(S400WConnection.toString(buf));
		}

		else if ( "preview".equals(args[0]) ) {
			buf = r.scan(null, new Receiver() {
				@Override public boolean receive(byte[] data, int offset, int length) throws IOException { return true; }
			}, null);
			if ( buf!=S400WConnection.SCAN_READY ) System.exit(-1);
			System.out.println(S400WConnection.toString(buf));
		}
			
		else if ( "scan".equals(args[0]) ) {
			final Integer dpi  = args.length <3  ? null : "dpi300".equals(args[1]) ? 300 : "dpi600".equals(args[1]) ? 600 : null;
			final String  name = args.length==1 ? ("./" + System.currentTimeMillis() + ".jpg") : args.length==2 ? args[1] : args[2];
			buf = r.scan(dpi, null, new Receiver() {
				FileOutputStream o = null;
				@Override public boolean receive(byte[] data, int offset, int length) throws IOException {
					if ( data==null && length>0 && o==null ) {
						o = new FileOutputStream(name);
					} else if ( data==null ) {
						o.close();
					} else {
						o.write(data, offset, length);
					}
					return true;
				}
			});
			if ( buf!=S400WConnection.SCAN_READY ) System.exit(-1);
			System.out.println(S400WConnection.toString(buf));
		}
	}
}
