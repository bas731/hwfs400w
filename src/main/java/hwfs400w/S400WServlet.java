/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).  
 *
 * Created 2022-12-27 by bastel.
 */

package hwfs400w;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Simple servlet to interface a S400W scanner.<br>
 * Does not support preview data (yet).
 * <p>
 * Create a virtual {@code /s400/} directory in the servlet context
 * providing all services. Also provides a demo application at {@code /s400w/} 
 * 
 * @author bastel
 * @since 27.12.2022
 */
@WebServlet(
	name="s400w",
	urlPatterns = "/s400w/*",
	displayName = "S400W Scanner Services",
	initParams = { @WebInitParam(name = "address", value = S400WSettings.DEFAULT_ADDR)}
)
public class S400WServlet extends HttpServlet
{
	private final static long serialVersionUID = -375822703462858974L;
	
	
	private final S400WSettings _settings = new S400WSettings();

	
	@Override
	public void init() throws ServletException
	{
		final String addr = trim(getInitParameter("address"), S400WSettings.DEFAULT_ADDR);
		try {
			_settings.with(addr);
		} catch (IllegalArgumentException e) {
			throw new ServletException("Invalid S400W address: " + addr);
		}
	}

	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		noCache(response);
		final String path = requireNonNullOrElse(request.getPathInfo(), "/");
		switch (path.substring(1)) {
			case "version":
				writeResponse(response, getDevice().getVersion());
				break;

			case "status":
				writeResponse(response, getDevice().getStatus());
				break;

			case "battery":
				writeResponse(response, getDevice().getBatteryState());
				break;
				
			default:
				response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, path.substring(1));
				break;
				
			case "":
			case "index.htm":
			case "index.html":
				try (InputStream in = S400W.class.getResourceAsStream("index.html")) {
					if ( in!=null ) {
						response.setContentType("text/html; charset=utf-8");
						OutputStream out = response.getOutputStream();
						byte[] buf = new byte[4096];
						for ( int r = in.read(buf); r!=-1; r = in.read(buf) ) out.write(buf, 0, r);
					}
				}
		}
	}

	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		noCache(response);
		final String path = requireNonNullOrElse(request.getPathInfo(), "/");
		switch (path.substring(1)) {
			case "poweroff":
				writeResponse(response, getDevice().poweroff());
				break;

			case "clean":
				writeResponse(response, getDevice().clean(), S400WResponse.CLEAN_END);
				break;

			case "calibrate":
				writeResponse(response, getDevice().calibrate(), S400WResponse.CALIBRATE_END);
				break;
				
			case "scan":
				doScan(request, response);
				break;
				
			default:
				response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, path.substring(1));
				break;
		}
	}


	private void doScan(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		final int dpi = Integer.parseInt(trim(request.getParameter("dpi"), "0"));
		final S400W device = getDevice();
		
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
	
	
	private S400W getDevice()
	{
		return new S400W(_settings);
	}
	
	
	private static void writeResponse(HttpServletResponse response, S400WResponse scanResponse, S400WResponse... good) throws IOException
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
	
	
	private static void noCache(HttpServletResponse response)
	{
		response.setHeader("Pragma",        "no-cache");
		response.setHeader("Cache-control", "no-store,no-cache");
		response.setHeader("Expires",       "-1");
	}	
	

	private static <T> T requireNonNullOrElse(T value, T defaultValue)
	{
		return value==null ? defaultValue : value;
	}

	
	private static String trim(String value, String defaultValue)
	{
		return value==null || (value = value.trim()).isEmpty() ? defaultValue : value;
	}
	
}
