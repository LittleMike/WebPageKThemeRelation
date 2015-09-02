package mike;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebContextExtractor {

	/**
	 * extract main context from wikipedia
	 * 
	 * @param String
	 * @return String
	 * @throws IOException
	 */
	public String ExtractContext(String WebUrl) throws IOException {
		/* get html context */
		URL url = new URL(WebUrl);
		BufferedReader br = new BufferedReader(new InputStreamReader(
				url.openStream()));
		String s = "";
		StringBuffer sb = new StringBuffer("");
		// read and add to sb
		while ((s = br.readLine()) != null) {
			sb.append(s + "\r\n");
		}
		/* extract main context */
		String str = sb.toString();
		// end location
		int textEnd = str.indexOf("<table id=\"toc\" class=\"toc\">");
		str = str.substring(0, textEnd);
		// begin location
		int textBegin = 0;
		if (str.indexOf("</table>") != -1) {
			textBegin = str.lastIndexOf("</table>");
		}
		str = str.substring(textBegin);
		StringBuffer strBuf = new StringBuffer();
		// regular expression and match text
		String regex = "<p>.+</p>";
		Matcher matcher = Pattern.compile(regex).matcher(str);
		while (matcher.find()) {
			strBuf.append(matcher.group() + "\r\n");
		}
		// log out
		System.out.println(strBuf.toString());
		str = strBuf.toString();
		// remove tag
		str = str.replaceAll("<[^>]+>", "");
		// log out
		System.out.println(str);
		return str;
	}
}
