package edu.unh.cs.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import co.nstant.in.cbor.CborException;
import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;

public class DataUtilities {
	
	public static HashMap<String, double[]> getParaRandomVecMap(Properties pr, ArrayList<String> paraids){
		HashMap<String, double[]> paraVecMap = new HashMap<String, double[]>();
		Random r = new Random(100);
		for(String paraid:paraids) {
			double[] randomVec = new double[50];
			for(int i=0; i<randomVec.length; i++)
				randomVec[i] = r.nextDouble();
			paraVecMap.put(paraid, randomVec);
		}
		return paraVecMap;
	}
	
	public static HashMap<String, double[]> getParaTFIDFVecMap(Properties pr, ArrayList<String> paraids){
		HashMap<String, double[]> paraVecMap = new HashMap<String, double[]>();
		
		return paraVecMap;
	}
	
	public static HashMap<String, double[]> getParaTMVecMap(Properties pr, ArrayList<String> paraids){
		HashMap<String, double[]> paraVecMap = new HashMap<String, double[]>();
		
		return paraVecMap;
	}
	
	public static HashMap<String, double[]> getParaW2VVecMap(Properties pr, ArrayList<String> paraids, HashMap<String, double[]> tokenVecMap, int vecSize) throws IOException, ParseException{
		HashMap<String, double[]> paraVecMap = new HashMap<String, double[]>();
		IndexSearcher is = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(pr.getProperty("index-dir")).toPath()))));
		Analyzer analyzer = new StandardAnalyzer();
		QueryParser qp = new QueryParser("paraid", analyzer);
		Document para;
		int p = 1;
		//System.out.println("Getting para vectors from glove");
		for(String paraid:paraids){
			para = is.doc(is.search(qp.parse(paraid), 1).scoreDocs[0].doc);
			HashMap<String, Double> tokenTfidfMap = tokenTfidfMap(is, analyzer, para.get("parabody"));
			//getAvgParaVec() is expensive op
			paraVecMap.put(paraid, getAvgParaVec(tokenTfidfMap, tokenVecMap, vecSize));
			p++;
		}
		return paraVecMap;
	}
	
	public static double[] getParaW2VVec(Properties pr, String paraid, HashMap<String, double[]> tokenVecMap, int vecSize) throws IOException, ParseException{
		double[] paraVec = new double[vecSize];
		IndexSearcher is = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(pr.getProperty("index-dir")).toPath()))));
		Analyzer analyzer = new StandardAnalyzer();
		QueryParser qp = new QueryParser("paraid", analyzer);
		Document para;
		//int p = 1;
		//System.out.println("Getting para vectors from glove");
		para = is.doc(is.search(qp.parse(paraid), 1).scoreDocs[0].doc);
		HashMap<String, Double> tokenTfidfMap = tokenTfidfMap(is, analyzer, para.get("parabody"));
		//getAvgParaVec() is expensive op
		paraVec = getAvgParaVec(tokenTfidfMap, tokenVecMap, vecSize);
		//p++;
		return paraVec;
	}
	
	public static Connection getDBConnection(String ip, String db, String table, String dbUser, String dbPwd) throws ClassNotFoundException, SQLException {
		Class.forName("org.postgresql.Driver");
		// DriverManager.getConnection("jdbc:postgresql://localhost:5432/testdb", "postgres", "123");
		Connection connect = DriverManager.getConnection("jdbc:postgresql://"+ip+"/"+db, dbUser, dbPwd);
		return connect;
	}
	
	public static HashMap<String, double[]> getSecVecMap(Properties pr, ArrayList<String> secids, HashMap<String, double[]> tokenVecMap, int vecSize) throws IOException, ParseException{
		HashMap<String, double[]> secVecMap = new HashMap<String, double[]>();
		Analyzer analyzer = new StandardAnalyzer();
		//System.out.println("Getting para vectors from glove");
		for(String secid:secids){
			String[] tokens = secid.split(":")[1].replaceAll("%20", " ").replaceAll("/", " ").split(" ");
			double[] secVec = new double[vecSize];
			ArrayList<String> alreadySeen = new ArrayList<String>();
			ArrayList<double[]> vecs = new ArrayList<double[]>();
			for(String t:tokens){
				if(alreadySeen.contains(t.toLowerCase()) || DataUtilities.stopwords.contains(t.toLowerCase()))
					continue;
				else if(tokenVecMap.keySet().contains(t.toLowerCase())){
					vecs.add(tokenVecMap.get(t.toLowerCase()));
					alreadySeen.add(t.toLowerCase());
				}
			}
			for(double[] vec:vecs){
				for(int i=0; i<vecSize; i++)
					secVec[i]+=vec[i];
			}
			if(vecs.size()>0){
				for(int i=0; i<vecSize; i++)
					secVec[i]/=vecs.size();
			}
			secVecMap.put(secid, secVec);
		}
		return secVecMap;
	}
	
	public static HashMap<String, double[]> readGloveFile(Properties pr) throws IOException{
		HashMap<String, double[]> tokenVecMap = new HashMap<String, double[]>();
		String gloveFilePath = pr.getProperty("glove-dir")+"/"+pr.getProperty("glove-file");
		BufferedReader br = new BufferedReader(new FileReader(new File(gloveFilePath)));
		String token;
		String line = br.readLine();
		int word2vecSize = line.split(" ").length-1;
		double vec[];
		while(line!=null){
			token = line.split(" ")[0];
			vec = new double[word2vecSize];
			String[] vals = line.split(" ");
			for(int i=1; i<vals.length; i++)
				vec[i-1]+= Double.parseDouble(vals[i]);
			tokenVecMap.put(token, vec);
			line = br.readLine();
		}
		br.close();
		System.out.println("Read glove file");
		return tokenVecMap;
	}
	
	private static double[] getAvgParaVec(HashMap<String, Double> tokenTfidfMap, HashMap<String, double[]> tokenVecMap, int vecSize) throws IOException{
		double[] avgVec = new double[vecSize];
		int count = 0;
		for(String token:tokenTfidfMap.keySet()){
			if(tokenVecMap.containsKey(token)){
				for(int i=0; i<tokenVecMap.get(token).length; i++)
					avgVec[i]+= tokenVecMap.get(token)[i]*tokenTfidfMap.get(token);
				count++;
			}
		}
		if(count>1){
			for(int i=0; i<avgVec.length; i++)
				avgVec[i]/=count;
		}
		return avgVec;
	}
	
	private static HashMap<String, Double> tokenTfidfMap(IndexSearcher is, Analyzer analyzer, String string) throws IOException{
		HashMap<String, Double> result = new HashMap<String, Double>();
	    Map<String, Long> termFreq = new HashMap<String, Long>();
	    Map<String, Long> docFreq = new HashMap<String, Long>();
	    
	    String token;
	    try {
	    	TokenStream stream  = analyzer.tokenStream(null, new StringReader(string));
	    	stream.reset();
	    	while (stream.incrementToken()){
	    		token = stream.getAttribute(CharTermAttribute.class).toString().toLowerCase();
	    		if(DataUtilities.stopwords.contains(token))
	    			continue;
	    		if(termFreq.keySet().contains(token)){
	    			termFreq.put(token, termFreq.get(token)+1);
	    		}
	    		else{
	    			termFreq.put(token, (long) 1);
	    			docFreq.put(token, (long) is.getIndexReader().docFreq(new Term("parabody",token)));
	    		}
	    	}
	    	stream.close();
	    } catch (IOException e) {
	    	throw new RuntimeException(e);
	    }
	    for(String term:termFreq.keySet()){
	    	result.put(term, 
	    			termFreq.get(term)*(1+Math.log(is.getIndexReader().getDocCount("parabody")/(1+docFreq.get(term)))));
	    }
	    return getNormalizedTfidf(result);
	}
	
	private static HashMap<String, Double> getNormalizedTfidf(HashMap<String, Double> tfidfMap){
		HashMap<String, Double> normMap = new HashMap<String, Double>();
		double total = 0;
		for(String term:tfidfMap.keySet())
			total+=tfidfMap.get(term);
		for(String term:tfidfMap.keySet())
			normMap.put(term, tfidfMap.get(term)/total);
		return normMap;
	}
	
	public static HashMap<String, Data.Paragraph> getParaMapFromPath(String path){
		HashMap<String, Data.Paragraph> paras = new HashMap<String, Data.Paragraph>();
		try {
			FileInputStream fis = new FileInputStream(new File(path));
			final Iterator<Data.Paragraph> paraIt = DeserializeData.iterParagraphs(fis);
			for (int i=1; paraIt.hasNext(); i++) {
				Data.Paragraph p = paraIt.next();
				paras.put(p.getParaId(), p);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return paras;
	}
	
	public static HashMap<String, ArrayList<String>> getReducedParaMap(HashMap<String, ArrayList<String>> map){
		HashMap<String, ArrayList<String>> reduced = new HashMap<String, ArrayList<String>>();
		
		return reduced;
	}
	
	public static HashMap<String, ArrayList<String>> getPreprocessedParaMap(HashMap<String, Data.Paragraph> paras){
		HashMap<String, ArrayList<String>> processed = new HashMap<String, ArrayList<String>>();
		for(String pid:paras.keySet()){
			String paratext = paras.get(pid).getTextOnly();
			ArrayList<String> paratokens = new ArrayList<String>();
			for(String w:paratext.replaceAll("%20", " ").split(" ")){
				w = w.toLowerCase().replaceAll("[^a-zA-Z]", "");
				if(!DataUtilities.stopwords.contains(w) && w.length()>1)
					paratokens.add(w);
			}
			processed.put(pid, paratokens);
		}
		return processed;
	}
	
	public static HashMap<String, ArrayList<String>> getTrueArticleParasMapFromPath(String path){
		HashMap<String, ArrayList<String>> articleMap = new HashMap<String, ArrayList<String>>();
		BufferedReader br;
		try{
			br = new BufferedReader(new FileReader(path));
			String line;
			String[] lineData = new String[4];
			while((line = br.readLine()) != null){
				lineData = line.split(" ");
				if(articleMap.containsKey(lineData[0])){
					articleMap.get(lineData[0]).add(lineData[2]);
				} else{
					ArrayList<String> paraList = new ArrayList<String>();
					paraList.add(lineData[2]);
					articleMap.put(lineData[0], paraList);
				}	
			}
			br.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		return articleMap;
	}
	
	/*
	public static HashMap<String, ArrayList<String>> getArticleSecMapFromPath(String path){
		HashMap<String, ArrayList<String>> articleSecMap = new HashMap<String, ArrayList<String>>();
		BufferedReader br;
		try{
			br = new BufferedReader(new FileReader(path));
			String line, pageid;
			String[] lineData = new String[4];
			while((line = br.readLine()) != null){
				lineData = line.split(" ");
				pageid = lineData[0].split("/")[0];
				if(articleSecMap.containsKey(pageid)){
					articleSecMap.get(pageid).add(lineData[0]);
				} else{
					ArrayList<String> secIDList = new ArrayList<String>();
					secIDList.add(lineData[2]);
					articleSecMap.put(pageid, secIDList);
				}	
			}
			br.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		return articleSecMap;
	}
	*/
	
	public static HashMap<String, ArrayList<String>> getArticleToplevelSecMap(String outlinePath){
		HashMap<String, ArrayList<String>> articleSecMap = new HashMap<String, ArrayList<String>>();
		try {
			FileInputStream fis = new FileInputStream(new File(outlinePath));
			final Iterator<Data.Page> pageIt = DeserializeData.iterAnnotations(fis);
			for(int i=0; pageIt.hasNext(); i++){
				Data.Page page = pageIt.next();
				ArrayList<String> secIDsInPage = new ArrayList<String>();
				for(Data.Section sec:page.getChildSections())
					secIDsInPage.add(page.getPageId()+"/"+sec.getHeadingId());
				articleSecMap.put(page.getPageId(), secIDsInPage);
			}
		} catch(IOException e){
			e.printStackTrace();
		}
		return articleSecMap;
	}
	
	public static HashMap<String, ArrayList<String>> getArticleSecMap(String outlinePath){
		HashMap<String, ArrayList<String>> articleSecMap = new HashMap<String, ArrayList<String>>();
		try {
			FileInputStream fis = new FileInputStream(new File(outlinePath));
			final Iterator<Data.Page> pageIt = DeserializeData.iterAnnotations(fis);
			for(int i=0; pageIt.hasNext(); i++){
				Data.Page page = pageIt.next();
				ArrayList<String> secIDsInPage = new ArrayList<String>();
				for(Data.Section sec:getAllSections(page))
					secIDsInPage.add(page.getPageId()+"/"+sec.getHeadingId());
				articleSecMap.put(page.getPageId(), secIDsInPage);
			}
		} catch(IOException e){
			e.printStackTrace();
		}
		return articleSecMap;
	}
	
	public static ArrayList<Data.Section> getAllSections(Data.Page page){
		ArrayList<Data.Section> secList = new ArrayList<Data.Section>();
		for(Data.Section sec:page.getChildSections())
			addSectionToList(sec, secList);
		return secList;
	}
	
	private static void addSectionToList(Data.Section sec, ArrayList<Data.Section> secList){
		if(sec.getChildSections() == null || sec.getChildSections().size() == 0)
			secList.add(sec);
		else{
			for(Data.Section child:sec.getChildSections())
				addSectionToList(child, secList);
			secList.add(sec);
		}
	}
	// Converts arraylist of para objects into their corresponding para id array
	public static ArrayList<String> getOrderedParaIDArray(ArrayList<Data.Paragraph> paras){
		ArrayList<String> ids = new ArrayList<String>(paras.size());
		for(Data.Paragraph p:paras)
			ids.add(p.getParaId());
		return ids;
	}
	
	public static HashMap<String,ArrayList<String>> getGTMapQrels(String qrelsPath){
		HashMap<String, ArrayList<String>> gtMap = new HashMap<String, ArrayList<String>>();
		BufferedReader br;
		try{
			br = new BufferedReader(new FileReader(qrelsPath));
			String line;
			String[] lineData = new String[4];
			while((line = br.readLine()) != null){
				lineData = line.split(" ");
				if(gtMap.containsKey(lineData[0])){
					gtMap.get(lineData[0]).add(lineData[2]);
				} else{
					ArrayList<String> paraList = new ArrayList<String>();
					paraList.add(lineData[2]);
					gtMap.put(lineData[0], paraList);
				}	
			}
			br.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		return gtMap;
	}
	
	public static ArrayList<ArrayList<String>> getGTClusters(String pageid, String qrelsPath){
		HashMap<String, ArrayList<String>> gtMap = DataUtilities.getGTMapQrels(qrelsPath);
		ArrayList<ArrayList<String>> clusters = new ArrayList<ArrayList<String>>();
		for(String secID:gtMap.keySet()){
			if(secID.startsWith(pageid)){
				ArrayList<String> clust = gtMap.get(secID);
				clusters.add(clust);
			}
		}
		return clusters;
	}
	
	public static HashMap<String, ArrayList<String>> getPageParaMapFromRunfile(String runfile){
		HashMap<String, ArrayList<String>> pageParaMap = new HashMap<String, ArrayList<String>>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File(runfile)));
			String line = br.readLine();
			while(line!=null){
				String pageid = line.split(" ")[0];
				String paraid = line.split(" ")[2];
				if(pageParaMap.keySet().contains(pageid))
					pageParaMap.get(pageid).add(paraid);
				else{
					ArrayList<String> paralist = new ArrayList<String>();
					paralist.add(paraid);
					pageParaMap.put(pageid, paralist);
				}
				line = br.readLine();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return pageParaMap;
	}
	
	public static HashMap<String, ArrayList<String>> getPageParaMapWithScoresFromRunfile(String runfile){
		HashMap<String, ArrayList<String>> pageParaMap = new HashMap<String, ArrayList<String>>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File(runfile)));
			String line = br.readLine();
			while(line!=null){
				String pageid = line.split(" ")[0];
				String paraid = line.split(" ")[2];
				String score = line.split(" ")[4];
				if(pageParaMap.keySet().contains(pageid))
					pageParaMap.get(pageid).add(paraid+" "+score);
				else{
					ArrayList<String> paralist = new ArrayList<String>();
					paralist.add(paraid+" "+score);
					pageParaMap.put(pageid, paralist);
				}
				line = br.readLine();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return pageParaMap;
	}
	
	public static double getDotProduct(double[] a, double[] b){
		double val = 0;
		for(int i=0; i<a.length; i++)
			val+=a[i]*b[i];
		val = val/a.length;
		return val;
	}
	
	public static double[] getWeightVecFromRlibModel(String modelFilePath) throws IOException{
		double[] weightVec;
		BufferedReader br = new BufferedReader(new FileReader(new File(modelFilePath)));
		String line = br.readLine();
		while(line!=null && line.startsWith("#"))
			line = br.readLine();
		String[] values = line.split(" ");
		weightVec = new double[values.length];
		for(int i=0; i<values.length; i++)
			weightVec[i] = Double.parseDouble(values[i].split(":")[1]);
		return weightVec;
	}
	
	/**
	 * This method converts string to list of tokens sorted on term frequency 
	 * @param analyzer
	 * @param string
	 * @return
	 */
	public static ArrayList<String> getTopFrequentTokens(Analyzer analyzer, String string, int topNum){
	    ArrayList<String> result = new ArrayList<String>();
	    Map<String, Integer> termFreq = new HashMap<String, Integer>();
	    String token;
	    try {
	    	TokenStream stream  = analyzer.tokenStream(null, new StringReader(string));
	    	stream.reset();
	    	while (stream.incrementToken()){
	    		token = stream.getAttribute(CharTermAttribute.class).toString();
	    		if(termFreq.keySet().contains(token))
	    			termFreq.put(token, termFreq.get(token)+1);
	    		else
	    			termFreq.put(token, 1);
	    	}
	    	stream.close();
	    } catch (IOException e) {
	    	throw new RuntimeException(e);
	    }
	    Map<String, Integer> sortedTermFreq = MapUtil.sortByValue(termFreq);
	    int count = 0;
	    for(Map.Entry<String, Integer> entry : sortedTermFreq.entrySet()){
	    	result.add(entry.getKey());
	    	count++;
	    	if(count>=topNum)
	    		break;
	    }
	    return result;
	}
	
	public static List<String> stopwords = Arrays.asList("a", "as", "able", "about",
				"above", "according", "accordingly", "across", "actually",
				"after", "afterwards", "again", "against", "aint", "all",
				"allow", "allows", "almost", "alone", "along", "already",
				"also", "although", "always", "am", "among", "amongst", "an",
				"and", "another", "any", "anybody", "anyhow", "anyone", "anything",
				"anyway", "anyways", "anywhere", "apart", "appear", "appreciate",
				"appropriate", "are", "arent", "around", "as", "aside", "ask", "asking",
				"associated", "at", "available", "away", "awfully", "be", "became", "because",
				"become", "becomes", "becoming", "been", "before", "beforehand", "behind", "being",
				"believe", "below", "beside", "besides", "best", "better", "between", "beyond", "both",
				"brief", "but", "by", "cmon", "cs", "came", "can", "cant", "cannot", "cant", "cause", "causes",
				"certain", "certainly", "changes", "clearly", "co", "com", "come",
				"comes", "concerning", "consequently", "consider", "considering", "contain",
				"containing",    "contains","corresponding","could", "couldnt", "course", "currently",
				"definitely", "described", "despite", "did", "didnt", "different", "do", "does",
				"doesnt", "doing", "dont", "done", "down", "downwards", "during", "each", "edu",
				"eg", "eight", "either", "else", "elsewhere", "enough", "entirely", "especially",
				"et", "etc", "even", "ever", "every", "everybody", "everyone", "everything", "everywhere",
				"ex", "exactly", "example", "except", "far", "few", "ff", "fifth", "first", "five", "followed",   
				"following", "follows", "for", "former", "formerly", "forth", "four", "from", "further",
				"furthermore", "get", "gets", "getting", "given", "gives", "go", "goes", "going", "gone"
				    , "got", "gotten", "greetings", "had", "hadnt", "happens", "hardly", "has", "hasnt", "have",
				    "havent", "having", "he", "hes", "hello", "help", "hence", "her", "here", "heres", "hereafter",
				    "hereby", "herein", "hereupon", "hers", "herself", "hi", "him", "himself", "his", "hither", 
				    "hopefully", "how", "howbeit", "however", "i", "id", "ill", "im", "ive", "ie", "if", "ignored", 
				    "immediate", "in", "inasmuch", "inc", "indeed", "indicate", "indicated", "indicates", "inner", 
				    "insofar", "instead", "into", "inward", "is", "isnt", "it", "itd", "itll", "its", "its", "itself", 
				    "just", "keep", "keeps", "kept", "know", "knows", "known", "last", "lately", "later", "latter", 
				    "latterly", "least", "less", "lest", "let", "lets", "like", "liked", "likely", "little", "look", 
				    "looking", "looks", "ltd", "mainly", "many", "may", "maybe", "me", "mean", "meanwhile", "merely", 
				    "might", "more", "moreover", "most", "mostly", "much", "must", "my", "myself", "name", "namely", 
				    "nd", "near", "nearly", "necessary", "need", "needs", "neither", "never", "nevertheless", "new", 
				    "next", "nine", "no", "nobody", "non", "none", "noone", "nor", "normally", "not", "nothing", "novel", 
				    "now", "nowhere", "obviously", "of", "off", "often", "oh", "ok", "okay", "old", "on", "once", 
				    "one", "ones", "only", "onto", "or", "other", "others", "otherwise", "ought", "our", "ours", 
				    "ourselves", "out", "outside", "over", "overall", "own", "particular", "particularly", "per", 
				    "perhaps", "placed", "please", "plus", "possible", "presumably", "probably", "provides", "que", 
				    "quite", "qv", "rather", "rd", "re", "really", "reasonably", "regarding", "regardless", "regards", 
				    "relatively", "respectively", "right", "said", "same", "saw", "say", "saying", "says", "second", 
				    "secondly", "see", "seeing", "seem", "seemed", "seeming", "seems", "seen", "self", "selves", 
				    "sensible", "sent", "serious", "seriously", "seven", "several", "shall", "she", "should", "shouldnt", 
				    "since", "six", "so", "some", "somebody", "somehow", "someone", "something", "sometime", "sometimes", 
				    "somewhat", "somewhere", "soon", "sorry", "specified", "specify", "specifying", "still", "sub", "such", 
				    "sup", "sure", "ts", "take", "taken", "tell", "tends", "th", "than", "thank", "thanks", "thanx", "that", 
				    "thats", "thats", "the", "their", "theirs", "them", "themselves", "then", "thence", "there", "theres", 
				    "thereafter", "thereby", "therefore", "therein", "theres", "thereupon", "these", "they", "theyd", "theyll", 
				    "theyre", "theyve", "think", "third", "this", "thorough", "thoroughly", "those", "though", "three", 
				    "through", "throughout", "thru", "thus", "to", "together", "too", "took", "toward", "towards", "tried", 
				    "tries", "truly", "try", "trying", "twice", "two", "un", "under", "unfortunately", "unless", "unlikely", 
				    "until", "unto", "up", "upon", "us", "use", "used", "useful", "uses", "using", "usually", "value", "various", 
				    "very", "via", "viz", "vs", "want", "wants", "was", "wasnt", "way", "we", "wed", "well", "were", "weve", 
				    "welcome", "well", "went", "were", "werent", "what", "whats", "whatever", "when", "whence", "whenever", 
				    "where", "wheres", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", 
				    "which", "while", "whither", "who", "whos", "whoever", "whole", "whom", "whose", "why", "will", "willing", 
				    "wish", "with", "within", "without", "wont", "wonder", "would", "would", "wouldnt", "yes", "yet", "you", 
				    "youd", "youll", "youre", "youve", "your", "yours", "yourself", "yourselves", "zero");
}
