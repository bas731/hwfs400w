/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).  
 *
 * Created 2022-12-27 by bastel.
 */

package hwfs400w;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 
 * @author bastel
 * @since 27.12.2022
 */
public class S400WServlet extends HttpServlet
{
	private static final long serialVersionUID = -375822703462858974L;

	@Override
	public void init() throws ServletException
	{
		Logger.getLogger(S400WServlet.class.getPackage().getName()).setLevel(Level.FINER);
	}

	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		Utils.noCache(response);
		final String path = Utils.requireNonNullOrElse(request.getPathInfo(), "/");
		switch (path.substring(1)) {
			case "version":
				writeResponse(response, new S400W().getVersion());
				break;

			case "status":
				writeResponse(response, new S400W().getStatus());
				break;

			case "battery":
				writeResponse(response, new S400W().getBatteryState());
				break;
				
			default:
				response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, path.substring(1));
		}
	}

	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		Utils.noCache(response);
		final String path = Utils.requireNonNullOrElse(request.getPathInfo(), "/");
		switch (path.substring(1)) {
			case "poweroff":
				writeResponse(response, new S400W().poweroff());
				break;

			case "clean":
				writeResponse(response, new S400W().clean(), S400WResponse.CLEAN_END);
				break;

			case "calibrate":
				writeResponse(response, new S400W().calibrate(), S400WResponse.CALIBRATE_END);
				break;
				
			case "scan":
				doScan(request, response);
				break;
				
			default:
				response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, path.substring(1));
		}
	}


	private void doScan(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		final int dpi = Integer.parseInt(Utils.trim(request.getParameter("dpi"), "0"));
		final S400W device = new S400W();
		
		final ScanDataReceiver receiver = new ScanDataReceiver() {
			ServletOutputStream out;
			@Override
			public void open(long length) throws IOException {
				response.setContentLengthLong(length);
				response.setBufferSize((int)length);
				out = response.getOutputStream();
			}
			
			@Override
			public void write(byte[] array, int offset, int length) throws IOException 	{
				out.write(array, offset, length);
			}
		};
		S400WResponse result = device.scan(dpi, null, receiver);
		if ( result!=S400WResponse.SCAN_READY ) writeResponse(response, result, S400WResponse.SCAN_READY);
	}
	
	
	protected void writeResponse(HttpServletResponse response, S400WResponse scanResponse, S400WResponse... good) throws IOException
	{
		if ( scanResponse.isEmpty() ) {
			response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
		}
		else if ( scanResponse.isEOF() ) {
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
		}
		else {
			if ( good.length!=0 && !Arrays.asList(good).contains(scanResponse) ) {
				response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
			}
			response.getWriter().write(scanResponse.toString());
		}
	}
}
