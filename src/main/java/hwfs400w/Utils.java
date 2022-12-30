/* This file is licensed under Creative Commons License CC-CC0 1.0 (http://creativecommons.org/publicdomain/zero/1.0/).
 * 
 * Created 2022-12-27 by bastel.
 */

package hwfs400w;

import java.util.Collection;
import java.util.function.IntFunction;

import javax.servlet.http.HttpServletResponse;

/**
 * 
 * @author bastel
 * @since 27.12.2022
 */
public final class Utils
{
	private Utils()
	{
	}
	
	
	public static <T> T[] array(Collection<T> collection, IntFunction<T[]> supplier)
	{
		return collection.toArray(supplier.apply(collection.size()));
	}
	
	
	public static <T> T requireNonNullOrElse(T value, T defaultValue)
	{
		return value==null ? defaultValue : value;
	}

	
	public static String trim(String value, String defaultValue)
	{
		return value==null || (value = value.trim()).isEmpty() ? defaultValue : value;
	}

	
	public static String trim(String value)
	{
		return trim(value, "");
	}


	public static String trimToNull(String value)
	{
		return trim(value, null);
	}
	
	
	public static void noCache(HttpServletResponse response)
	{
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Cache-control", "no-store,no-cache");
		response.setHeader("Expires", "-1");
	}	
}
