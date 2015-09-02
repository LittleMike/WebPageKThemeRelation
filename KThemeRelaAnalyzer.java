package mike;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.util.FileManager;

public class KThemeRelaAnalyzer {

	static final String WORDNET_GLOSSARY = "wn20basic/wordnet-glossary.rdf";
	static final String WORDNET_SYNSET = "wn20basic/wordnet-synset.rdf";
	static final String WORDNET_HYPONYM = "wn20basic/wordnet-hyponym.rdf";

	static final String schemaURI = "http://www.w3.org/2006/03/wn/wn20/schema/";
	static final String instancesURI = "http://www.w3.org/2006/03/wn/wn20/instances/";
	static final String rdfsURI = "http://www.w3.org/2000/01/rdf-schema#";

	static int KLevel = 4;
	//static final int KRelation = KLevel;
	static double Range = 0.6;
	
	// K的动态控制变量
	static final int dy_KLevel = 20; // 根据资料，名词链长最大为16
	static final double KRelation_dy = 0.87; // K的动态控制系数	
	static final double BaseRange = 0.5; // 相似度基准值

//	static final String TEXT1_NAME = "cat";
//	static final String TEXT2_NAME = "dog";

	Model model = null;
	static final int FloorNum = 4 + 1; 
	// gloss属性
	Property pGloss;
	Property pLabel;
	Property pHyponym;
	Property pSynsetId;

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub		
		KThemeRelaAnalyzer MikeKTRA = new KThemeRelaAnalyzer();

		ArrayList<Node> ani_list = MikeKTRA.GetNodesFromTextFile("data/animal.txt",0);
		ArrayList<Node> cou_list = MikeKTRA.GetNodesFromTextFile("data/country.txt",1);

		ArrayList<Node> geo_pla_list = MikeKTRA.GetNodesFromTextFile("data/Geography and places.txt",0);
		ArrayList<Node> hea_list = MikeKTRA.GetNodesFromTextFile("data/Health and fitness.txt",0);
		
		ArrayList<Node> nat_list = MikeKTRA.GetNodesFromTextFile("data/Natural and physical sciences.txt",0);
		
		ArrayList<Node> nodes1 = MikeKTRA.ConstructNodeList(ani_list);
		ArrayList<Node> nodes2 = MikeKTRA.ConstructNodeList(cou_list);
		
		MikeKTRA.CalculateAndPrintRelation(nodes1, 1);
	}

	public KThemeRelaAnalyzer() {
		// TODO Auto-generated constructor stub

		// create an empty model
		model = ModelFactory.createDefaultModel();
		InputStream in = null;
		Range = TextSimilarityAnalyzer.Control(Range);
		in = FileManager.get().open(WORDNET_SYNSET);
		if (in == null) {
			throw new IllegalArgumentException("File: " + WORDNET_SYNSET
					+ " not found! ");
		}
		// read the RDF file
		model.read(in, "");
		in = FileManager.get().open(WORDNET_GLOSSARY);
		if (in == null) {
			throw new IllegalArgumentException("File: " + WORDNET_GLOSSARY
					+ " not found! ");
		}
		// read the RDF file
		model.read(in, "");
		in = FileManager.get().open(WORDNET_HYPONYM);
		if (in == null) {
			throw new IllegalArgumentException("File: " + WORDNET_HYPONYM
					+ " not found! ");
		}
		// read the RDF file
		model.read(in, "");

		pGloss = model.getProperty(schemaURI + "gloss");
		pLabel = model.getProperty(rdfsURI + "label");
		pHyponym = model.getProperty(schemaURI + "hyponymOf");
		pSynsetId = model.getProperty(schemaURI + "synsetId");
	}

	// 根据关键词和网页文本内容，进行词义标注，得到结点
	/**
	 * construct theme concept node
	 * 
	 * @param1 String
	 * @param2 String
	 * @return Node
	 */
	public Node ConstructNode(final String ThemeWord, final String text) {
		// returned node
		Node node = new Node();
		// to store resource in ontology
		Resource tmpRes = null;
		// to store all the glossaries of the themeword
		ArrayList<String> glossList = new ArrayList<String>();

		// search the glossary
		// get resource iterator from ontology model based on synsetid
		ResIterator rIter = model.listResourcesWithProperty(pSynsetId);
		// if has a resource
		while (rIter.hasNext()) {
			// get current resource
			tmpRes = rIter.nextResource();
			// start with 1 means the word is noun
			if (tmpRes.getProperty(pSynsetId).getString().startsWith("1")
					&& tmpRes.getProperty(pLabel).getString().equals(ThemeWord)) {
				// add one glossary of themeword to glosslist
				glossList.add(tmpRes.getProperty(pGloss).getString());
			}
		}
		// if has found the glossary
		if (!glossList.isEmpty()) {
			// get the exect glossary and filled with info
			node.SetText(GetCorrectText(text, glossList));
			node.SetThemeWord(ThemeWord);
			// return theme concept node
			return node;
		} else {
			// log out
//			System.out.println("didn't find the themeword in ontology:"
//					+ ThemeWord);
			// if the themeword isn't in ontology, maybe incorrect
			return null;
		}
	}

	// 分析得到相对最准确的释义并返回
	/**
	 * get the most suitable glossary according to main text
	 * 
	 * @param String
	 * @param ArrayList<String>
	 * @return String
	 */
	public String GetCorrectText(final String Text,
			final ArrayList<String> GlossList) {
		// apply to calculate the similarity
		TextSimilarityAnalyzer TSA = new TextSimilarityAnalyzer();
		// similarity result
		double bestScore = 0, tmpScore = 0;
		// to store the glossary text
		String tmpStr = null;
		// the best glossary to return
		String str = null;
		// get the iterator
		Iterator<String> sIter = GlossList.iterator();
		// if has a glossary
		while (sIter.hasNext()) {
			// get the glossary
			tmpStr = sIter.next();
			// calculate
			tmpScore = TSA.VSM_Similarity(TSA.GetDictionary(Text),
					TSA.GetDictionary(tmpStr));
			// log out
//			System.out.println(tmpScore);
			// choose the highest score
			if (tmpScore >= bestScore) {
				bestScore = tmpScore;
				str = tmpStr;
			}
		}
		// log out
//		System.out.println(str);
		// return the proper glossary
		return str;
	}

	// 构造上位概念链(K级) 主题概念链
	/**
	 * Construct superior concept chain(K levels)
	 * 
	 * @param Node
	 * @return ArrayList<Node>
	 */
	public ArrayList<Node> ConstructHyponymList(final Node node) {
		// temporary resource
		Resource tmpRes = null;
		// temporary superior concept resource
		Resource tmpResh = null;
		// returned nodelist
		ArrayList<Node> nodeList = new ArrayList<Node>();
		// level counter
		int level = 1;

		// insert param node as first node
		nodeList.add(node);

		// construct process
		// resource iterator based on synsetid
		ResIterator rIter = model.listResourcesWithProperty(pSynsetId);
		// if still have a resource
		while (rIter.hasNext()) {
			// get current resource
			tmpRes = rIter.nextResource();
			// find themeWord and its glossary in node from WordNet
			if (tmpRes.getProperty(pLabel).getString().equals(node.msThemeWord)
					&& tmpRes.getProperty(pGloss).getString()
							.equals(node.msText)) {
				// if the resource has superior concept and if length of
				// nodelist is beyond K
				while (tmpRes.hasProperty(pHyponym) && (level++) <= KLevel) {
					// get superior concept resource
					tmpResh = (Resource) tmpRes.getProperty(pHyponym)
							.getObject();
					// construct node filled info and insert to nodelist
					nodeList.add(new Node(tmpResh.getProperty(pLabel)
							.getString(), tmpResh.getProperty(pGloss)
							.getString()));
					// continue to find superior concept
					tmpRes = tmpResh;
				}
			}
		}
		// the nodelist contains at least one node and its length is K+1 at most
		return nodeList;
	}

	// 构造上位概念链(K级) 主题概念链 全长
	public ArrayList<Node> ConstructHyponymList_dy(final Node node) {
		// temporary resource
		Resource tmpRes = null;
		// temporary superior concept resource
		Resource tmpResh = null;
		// returned nodelist
		ArrayList<Node> nodeList = new ArrayList<Node>();
		// level counter
		int level = 1;

		// insert param node as first node
		nodeList.add(node);

		// construct process
		// resource iterator based on synsetid
		ResIterator rIter = model.listResourcesWithProperty(pSynsetId);
		// if still have a resource
		while (rIter.hasNext()) {
			// get current resource
			tmpRes = rIter.nextResource();
			// find themeWord and its glossary in node from WordNet
			if (tmpRes.getProperty(pLabel).getString().equals(node.msThemeWord)
					&& tmpRes.getProperty(pGloss).getString()
							.equals(node.msText)) {
				// if the resource has superior concept and if length of
				// nodelist is beyond K
				while (tmpRes.hasProperty(pHyponym) && (level++) <= dy_KLevel) {
					// get superior concept resource
					tmpResh = (Resource) tmpRes.getProperty(pHyponym)
							.getObject();
					// construct node filled info and insert to nodelist
					nodeList.add(new Node(tmpResh.getProperty(pLabel)
							.getString(), tmpResh.getProperty(pGloss)
							.getString()));
					// continue to find superior concept
					tmpRes = tmpResh;
				}
			}
		}
		// the nodelist contains at least one node and its length is K+1 at most
		
		//this.PrintNodeList(nodeList);
		
		return nodeList;
	}
	
	// K相关性匹配函数，完全对准
	/**
	 * 
	 * @param NodeList1
	 * @param NodeList2
	 * @return
	 */
	public String KRelationMatch(final ArrayList<Node> NodeList1,
			final ArrayList<Node> NodeList2) {
		int p1, p2;
		Node tmpNode1 = null;
		Node tmpNode2 = null;
		Iterator<Node> iter1 = null;
		Iterator<Node> iter2 = null;
		StringBuilder str = new StringBuilder();

		iter1 = NodeList1.iterator();
		p1 = 0;
		while (iter1.hasNext()) {
			tmpNode1 = iter1.next();

			// log
			System.out.println(tmpNode1.msThemeWord + " | " + tmpNode1.msText);

			iter2 = NodeList2.iterator();
			p2 = 0;
			while (iter2.hasNext()) {
				tmpNode2 = iter2.next();

				// log
				System.out.println(tmpNode2.msThemeWord + " | "
						+ tmpNode2.msText);

				if (tmpNode1.msThemeWord.equals(tmpNode2.msThemeWord)
						&& tmpNode1.msText.equals(tmpNode2.msText)) {
					// str.append("<" + p1 + "," + p2 + ">").append("");
					str.append("<" + p1 + "," + p2 + ">");
					return str.toString();
				}
				p2++;
			}
			p1++;
		}
		return "<None>";
	}

	// K相关性匹配函数，看相似度
	/**
	 * K-relativity of theme match
	 * 
	 * @param1 ArrayList<Node>
	 * @param2 ArrayList<Node>
	 * @return String
	 */
	public String KRelationMatchRange(final ArrayList<Node> NodeList1,
			final ArrayList<Node> NodeList2) {
		// level counter
		int p1, p2;
		// node in nodelist1
		Node tmpNode1 = null;
		// node in nodelist2
		Node tmpNode2 = null;
		// iterator of nodelist1
		Iterator<Node> iter1 = null;
		// iterator of nodelist2
		Iterator<Node> iter2 = null;
		// result to display
		StringBuilder str = new StringBuilder();
		// apply to calculate the similarity of glossary text
		TextSimilarityAnalyzer TAS = new TextSimilarityAnalyzer();
		// result of similarity
		double sim = 0;

		// get the iterator
		iter1 = NodeList1.iterator();
		// init
		p1 = 0;
		// if has node in iter1
		while (iter1.hasNext()) {
			// get a node
			tmpNode1 = iter1.next();
			// log out
//			System.out.println(tmpNode1.msThemeWord + " | " + tmpNode1.msText);
			// get the iterator
			iter2 = NodeList2.iterator();
			// init
			p2 = 0;
			// if has node in iter2
			while (iter2.hasNext()) {
				// get a node
				tmpNode2 = iter2.next();
				// log out
//				System.out.println(tmpNode2.msThemeWord + " | "
//						+ tmpNode2.msText);
				// calculate the similarity
				sim = TAS.VSM_Similarity(TAS.GetDictionary(tmpNode1.msText),
						TAS.GetDictionary(tmpNode2.msText));
				if (sim > Range) {
					// construct the result string
					str.append("<" + p1 + "," + p2 + ">");
					return str.toString();
				}
				// add the level_1
				p2++;
			}
			// add the level_2
			p1++;
		}
		// if have no relativity
		return "<None>";
	}
	
	// K相关性匹配函数，看相似度 动态控制K
	public String KRelationMatchRange_dy(final ArrayList<Node> NodeList1,
			final ArrayList<Node> NodeList2) {
		// level counter
		int p1, p2;
		// node in nodelist1
		Node tmpNode1 = null;
		// node in nodelist2
		Node tmpNode2 = null;
		// iterator of nodelist1
		Iterator<Node> iter1 = null;
		// iterator of nodelist2
		Iterator<Node> iter2 = null;
		// result to display
		StringBuilder str = new StringBuilder();
		// apply to calculate the similarity of glossary text
		TextSimilarityAnalyzer TAS = new TextSimilarityAnalyzer();
		// result of similarity
		double sim = 0;
		// 匹配范围变量
		int KRela1, KRela2;
		// 层级控制量
		int k1, k2;
		// 动态阈值
		double range;

		// init
		KRela1 = NodeList1.size() <= FloorNum ? FloorNum : (int) (NodeList1
				.size() * KRelation_dy + 1);
		KRela2 = NodeList2.size() <= FloorNum ? FloorNum : (int) (NodeList2
				.size() * KRelation_dy + 1);
		k1 = 0;
		p1 = 0;
		range = Math.abs(NodeList1.size() - NodeList2.size()) >= 7 ? 0.9
				: (Math.abs(NodeList1.size() - NodeList2.size()) / 2) / 10.0 + BaseRange;
		if(range >= 1) range = 0.99;
//		range = 0.5;
		// log out
		System.out.println("l1:" + NodeList1.size() + " " +"l2:" + NodeList2.size());
		System.out.println("KRela1:" + KRela1 + " " + "KRela2:" + KRela2);
		System.out.println("range:" + range);
		// get the iterator
		iter1 = NodeList1.iterator();
		// if has node in iter1
		while (iter1.hasNext() && k1 < KRela1) {
			// get a node
			tmpNode1 = iter1.next();
			// log out
//			System.out.println(tmpNode1.msThemeWord + " | " + tmpNode1.msText);
			// get the iterator
			iter2 = NodeList2.iterator();
			// init
			p2 = 0;
			k2 = 0;
			// if has node in iter2
			while (iter2.hasNext() && k2 < KRela2) {
				// get a node
				tmpNode2 = iter2.next();
				// log out
//				System.out.println(tmpNode2.msThemeWord + " | "
//						+ tmpNode2.msText);
				// calculate the similarity
				sim = TAS.VSM_Similarity(TAS.GetDictionary(tmpNode1.msText),
						TAS.GetDictionary(tmpNode2.msText));
				if (sim > range) {
					// construct the result string
					str.append("<" + p1 + "," + p2 + ">");
					return str.toString();
				}
				// add the level_1
				p2++;
				k2++;
			}
			// add the level_2
			p1++;
			k1++;
		}
		// if have no relativity
		return "<None>";
	}

	public void PrintNodeList(final ArrayList<Node> NodeList) {
		Iterator<Node> iter = NodeList.iterator();
		Node tmpNode = null;
		while (iter.hasNext()) {
			tmpNode = iter.next();
			System.out.println(tmpNode.msThemeWord + " | " + tmpNode.msText);
		}
	}

	// 将文件内容组织成结点组，dl为0表示转化为小写，不为0不转化
	public ArrayList<Node> GetNodesFromTextFile(String filepath, int dl) throws IOException{
		
		InputStream is = new FileInputStream(filepath);
		BufferedReader bufr = new BufferedReader(new InputStreamReader(is));
		String readStr = "";
		// String url = "";
		// String tpStr = "";
		Node node = null;
		ArrayList<Node> nodelist = new ArrayList<Node>();
		
		StringBuffer strbuf = new StringBuffer("");
		while ((readStr = bufr.readLine()) != null) {
			if (readStr.startsWith("***")) {
				node = new Node();
				// node.msThemeWord =
				// readStr.substring(3).toLowerCase(Locale.ENGLISH); // "***"
				node.msThemeWord = (dl == 0) ? readStr.substring(3)
						.toLowerCase(Locale.ENGLISH) : readStr.substring(3);
				strbuf.delete(0, strbuf.length());
			}
			else if(readStr.startsWith("+++")){
				node.msText = strbuf.toString().toLowerCase(Locale.ENGLISH);
				nodelist.add(node);
			}
			else{
				strbuf.append(readStr + "\r\n");
			}			
		}
		bufr.close();
		return nodelist;
	}
	
	public ArrayList<Node> ConstructNodeList(final ArrayList<Node> al){
		
		ArrayList<Node> alist = new ArrayList<Node>();
		Node node = null;
		Node tmpNd = null;
		
		Iterator<Node> iter = al.iterator();
		while(iter.hasNext()){
			tmpNd = iter.next();
			node = ConstructNode(tmpNd.msThemeWord, tmpNd.msText);
			if(node != null){
				alist.add(node);
			}
		}
		if(alist.isEmpty()){
			return null;
		}else{
			return alist;
		}
	}
	
	// 计算单组内的相关度, choice为0则完全对准，不为0则放宽相似性
	public void CalculateAndPrintRelation(final ArrayList<Node> nodes,int choice){
		
		if(nodes.isEmpty()){
			System.out.println("无资源！");
			return;
		}
		
		Node node = null;
		ArrayList<Node> al = null;
		int index = 0;
		String result = null;
		int numyes = 0, numno = 0;
		
		//Queue<Object> q = new Queue<Object>();
		LinkedList< ArrayList<Node> > ll = new LinkedList< ArrayList<Node> >();
		
		Iterator<Node> iter = nodes.iterator();
		while(iter.hasNext()){
			node = iter.next();
			al = this.ConstructHyponymList(node);
			ll.add(al);
		}
		while(!ll.isEmpty()){
			al = ll.removeFirst();
			index = 0;
			while(index < ll.size()){
				if(choice == 0){
					result = this.KRelationMatch(al, ll.get(index++));
				}else{
					result = this.KRelationMatchRange(al, ll.get(index++));
				}
				if(result.equalsIgnoreCase("<None>")){
					numno++;
					System.out.print("不相关 - ");
				}else{
					numyes++;
					System.out.print("相关 - ");
				}
				System.out.println(result);
			}
		}
		System.out.println("该主题领域下的相关性统计：");
//		System.out.println("相关:" + numyes + " " + "不相关:" + numno);
		System.out.println("网页间的相关数目：" + numyes + ".");
		System.out.println("网页间的不相关数目：" + numno + ".");
	}
	
	// 计算单组内的相关度, choice为0则完全对准，不为0则放宽相似性 动态
	public void CalculateAndPrintRelation_dy(final ArrayList<Node> nodes,
			int choice) {

		if (nodes.isEmpty()) {
			System.out.println("无资源！");
			return;
		}

		Node node = null;
		ArrayList<Node> al = null;
		int index = 0;
		String result = null;
		int numyes = 0, numno = 0;

		// Queue<Object> q = new Queue<Object>();
		LinkedList<ArrayList<Node>> ll = new LinkedList<ArrayList<Node>>();

		Iterator<Node> iter = nodes.iterator();
		while (iter.hasNext()) {
			node = iter.next();
			al = this.ConstructHyponymList_dy(node);
			ll.add(al);
		}
		while (!ll.isEmpty()) {
			al = ll.removeFirst();
			index = 0;
			while (index < ll.size()) {
				if (choice == 0) {
					result = this.KRelationMatch(al, ll.get(index++));
				} else {
					result = this.KRelationMatchRange_dy(al, ll.get(index++));
				}
				if (result.equalsIgnoreCase("<None>")) {
					numno++;
				} else {
					numyes++;
				}
				System.out.println(result);
			}
		}
		System.out.println("相关:" + numyes + " " + "不相关:" + numno);
	}
	
	// 计算两组间的相似度，choice为0则完全对准，不为0则放宽相似性
	public void CalculateAndPrintRelation(final ArrayList<Node> nodes1,final ArrayList<Node> nodes2,int choice){
		
		if(nodes1.isEmpty() || nodes2.isEmpty()){
			System.out.println("无资源！");
			return;
		}
		// 构建上位概念链集
		LinkedList<ArrayList<Node>> L1 = new LinkedList<ArrayList<Node>>();
		ArrayList<Node> a1 = null;
		Iterator<Node> iter1 = nodes1.iterator();
		while (iter1.hasNext()) {
			a1 = this.ConstructHyponymList(iter1.next());
			L1.add(a1);
		}
		LinkedList<ArrayList<Node>> L2 = new LinkedList<ArrayList<Node>>();
		ArrayList<Node> a2 = null;
		Iterator<Node> iter2 = nodes2.iterator();
		while (iter2.hasNext()) {
			a2 = this.ConstructHyponymList(iter2.next());
			L2.add(a2);
		}
		
		String result = null;
		int numyes = 0, numno = 0;
		ArrayList<Node> tmpArraylist = null;
		Iterator<ArrayList<Node>> aiter1 = L1.iterator();
		Iterator<ArrayList<Node>> aiter2 = null;
		while(aiter1.hasNext()){
			tmpArraylist = aiter1.next();
			aiter2 = L2.iterator();
			while (aiter2.hasNext()) {
				//node = iter2.next();
				if (choice == 0) {
					result = this.KRelationMatch(tmpArraylist, aiter2.next());
				} else {
					result = this.KRelationMatchRange(tmpArraylist, aiter2.next());
				}
				if(result.equalsIgnoreCase("<None>")){
					numno++;
				}else{
					numyes++;
				}
				System.out.println(result);
			}
		}
		System.out.println("相关:" + numyes + " " + "不相关:" + numno);
	}
	
	// 计算两组间的相似度，choice为0则完全对准，不为0则放宽相似性 动态
	public void CalculateAndPrintRelation_dy(final ArrayList<Node> nodes1,
			final ArrayList<Node> nodes2, int choice) {

		if (nodes1.isEmpty() || nodes2.isEmpty()) {
			System.out.println("无资源！");
			return;
		}
		// 构建上位概念链集
		LinkedList<ArrayList<Node>> L1 = new LinkedList<ArrayList<Node>>();
		ArrayList<Node> a1 = null;
		Iterator<Node> iter1 = nodes1.iterator();
		while (iter1.hasNext()) {
			a1 = this.ConstructHyponymList_dy(iter1.next());
			L1.add(a1);
		}
		LinkedList<ArrayList<Node>> L2 = new LinkedList<ArrayList<Node>>();
		ArrayList<Node> a2 = null;
		Iterator<Node> iter2 = nodes2.iterator();
		while (iter2.hasNext()) {
			a2 = this.ConstructHyponymList_dy(iter2.next());
			L2.add(a2);
		}

		String result = null;
		int numyes = 0, numno = 0;
		ArrayList<Node> tmpArraylist = null;
		Iterator<ArrayList<Node>> aiter1 = L1.iterator();
		Iterator<ArrayList<Node>> aiter2 = null;
		while (aiter1.hasNext()) {
			tmpArraylist = aiter1.next();
			aiter2 = L2.iterator();
			while (aiter2.hasNext()) {
				// node = iter2.next();
				if (choice == 0) {
					result = this.KRelationMatch(tmpArraylist, aiter2.next());
				} else {
					result = this.KRelationMatchRange_dy(tmpArraylist,
							aiter2.next());
				}
				if (result.equalsIgnoreCase("<None>")) {
					numno++;
				} else {
					numyes++;
				}
				System.out.println(result);
			}
		}
		System.out.println("相关:" + numyes + " " + "不相关:" + numno);
	}
}

class Node {
	String msThemeWord = null;
	String msText = null;

	public Node() {
	}

	public Node(final String ThemeWord, final String text) {
		// TODO Auto-generated constructor stub
		msThemeWord = ThemeWord;
		msText = text;
	}

	public void SetThemeWord(final String themeWord) {
		msThemeWord = themeWord;
	}

	public void SetText(final String text) {
		msText = text;
	}
}
