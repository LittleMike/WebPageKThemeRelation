package mike;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextSimilarityAnalyzer {

	
	public TextSimilarityAnalyzer() {
		// TODO Auto-generated constructor stub
	}
	public static double Control(double num){
		return num - 0.1;
	}
	/**
	 * convert text to VSM
	 * 
	 * @param String
	 * @return Map<String, Integer>
	 */
	public Map<String, Integer> GetDictionary(String text) {
		// construct a hashtable contains word and weight
		Map<String, Integer> map = new Hashtable<String, Integer>();
		// regular expression
		Pattern pattern = Pattern.compile("[a-zA-Z]+");
		// match the text
		Matcher matcher = pattern.matcher(text);
		// init
		int initWeight = 1;
		// has matched word
		while (matcher.find()) {
			// log out
//			System.out.println(matcher.group());
			// add weight
			if (map.containsKey(matcher.group())) {
				map.put(matcher.group(), map.get(matcher.group()) + 1);
			} else {
				// give initweight
				map.put(matcher.group(), initWeight);
			}
		}
		// using hashtable to store VSM to return
		return map;
	}

	/**
	 * calculate cosine similarity
	 * 
	 * @param1 Map<String, Integer>
	 * @param2 Map<String, Integer>
	 * @return double similarity
	 */
	public double VSM_Similarity(final Map<String, Integer> map1,
			final Map<String, Integer> map2) {
		// intermediate variable
		double similarity = 0.0, numerator = 0.0, denominator1 = 0.0, denominator2 = 0.0;
		// to store weight
		int itmp1, itmp2;
		// get the two hashtables
		Map<String, Integer> dict1 = new Hashtable<String, Integer>(map1);
		Map<String, Integer> dict2 = new Hashtable<String, Integer>(map2);
		// check
		if (dict1.isEmpty() || dict2.isEmpty()) {
			return 0.0;
		}

		// get dict1's key set
		Set<String> keys = dict1.keySet();
		// calculate for every key
		for (String key : keys) {
			// get weight of the key
			itmp1 = dict1.get(key);
			// if dict2 has the same key, get the weight; else 0
			itmp2 = dict2.containsKey(key) ? dict2.get(key) : 0;
			// remove the same key from dict2
			dict2.remove(key);
			// calculate numerator
			numerator += itmp1 * itmp2;
			denominator1 += itmp1 * itmp1;
			denominator2 += itmp2 * itmp2;
		}
		keys = dict2.keySet();
		// to the rest keys in dict2
		for (String key : keys) {
			itmp2 = dict2.get(key);
			// continue to calculate denominator2
			denominator2 += itmp2 * itmp2;
		}
		// cosine similarity and return
		similarity = numerator / (Math.sqrt(denominator1 * denominator2));
		return similarity;
	}
}
