package edu.unh.cs.parasim;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import edu.unh.cs.util.DataUtilities;
import edu.unh.cs.util.MapUtil;

public class ParasimReranker {
	
	public HashMap<String, HashMap<String, Double>> getRunfileObj(String runfilePath) throws IOException{
		HashMap<String, HashMap<String, Double>> rfObj = new HashMap<String, HashMap<String, Double>>();
		BufferedReader br = new BufferedReader(new FileReader(new File(runfilePath)));
		String line = br.readLine();
		String q,p,s;
		while(line!=null){
			q = line.split(" ")[0];
			p = line.split(" ")[2];
			s = line.split(" ")[4];
			if(rfObj.keySet().contains(q)){
				rfObj.get(q).put(p, Double.parseDouble(s));
			}
			else{
				HashMap<String, Double> psmap = new HashMap<String, Double>();
				psmap.put(p, Double.parseDouble(s));
				rfObj.put(q, psmap);
			}
			line = br.readLine();
		}
		br.close();
		return rfObj;
	}
	
	public double getParasimScore(String keyPara, String retPara, IndexSearcher is, IndexSearcher isNoStops, IndexSearcher aspectIs, Connection con, int retAspNo, double[] optW) 
			throws IOException, ParseException, SQLException, InterruptedException {
		double score = 0;
		QueryParser qpID = new QueryParser("paraid", new StandardAnalyzer());
		QueryParser qpAspText = new QueryParser("Text", new StandardAnalyzer());
		AspectSimilarity aspSim = new AspectSimilarity();
		
		String keyQueryString = isNoStops.doc(isNoStops.search(qpID.parse(keyPara), 1).scoreDocs[0].doc).get("parabody");
		String retQueryString = isNoStops.doc(isNoStops.search(qpID.parse(retPara), 1).scoreDocs[0].doc).get("parabody");
		BooleanQuery.setMaxClauseCount(65536);
		Query qKey = qpAspText.parse(QueryParser.escape(keyQueryString));
		Query qRet = qpAspText.parse(QueryParser.escape(retQueryString));
		
		TopDocs retAspectsKeyPara = aspectIs.search(qKey, retAspNo);
		TopDocs retAspectsRetPara = aspectIs.search(qRet, retAspNo);

		double[] aspScore = aspSim.aspectRelationScore(retAspectsKeyPara, retAspectsRetPara, is, aspectIs, con, "na");
		double aspectMatchRatio = aspSim.aspectMatchRatio(retAspectsKeyPara.scoreDocs, retAspectsRetPara.scoreDocs);
		double entMatchRatio = aspSim.entityMatchRatio(keyPara, retPara, con, "na");
		
		//The order of the aspect features and optWeight from previously trained aspect rlib model is according to
		//asp-features property of project.properties file of Porcupine
		score = (aspScore[0]*optW[0] + aspScore[1]*optW[1] + aspScore[2]*optW[2] + aspectMatchRatio*optW[3] + entMatchRatio*optW[4])/5;
		return score;
	}
	
	public HashMap<String, HashMap<String, Double>> reRank(HashMap<String, HashMap<String, Double>> runFileObj, IndexSearcher is, IndexSearcher isNoStops, IndexSearcher aspectIs, 
			Connection con, int retAspNo, double[] optW) throws IOException, ParseException, SQLException, InterruptedException {
		HashMap<String, HashMap<String, Double>> rerankedRunfile = new HashMap<String, HashMap<String, Double>>();
		System.out.println("Reranking started");
		for(String query:runFileObj.keySet()) {
			HashMap<String, Double> newRanks = new HashMap<String, Double>();
			HashMap<String, Double> ranks = runFileObj.get(query);
			System.out.println("\nQuery: "+query+"\n");
			String topRetPara = "";
			double topScore = 0;
			for(String ret:ranks.keySet()) {
				if(ranks.get(ret)>topScore) {
					topScore = ranks.get(ret);
					topRetPara = ret;
				}
			}
			
			for(String retPara:ranks.keySet()) {
				if(!retPara.equalsIgnoreCase(topRetPara)) {
					double parasimScore = this.getParasimScore(topRetPara, retPara, is, isNoStops, aspectIs, con, retAspNo, optW);
					newRanks.put(retPara, parasimScore);
					System.out.print(".");
				}
			}
			rerankedRunfile.put(query, newRanks);
		}
		return rerankedRunfile;
	}
	
	public void writeRerankedRunfile(Properties prop, String runfilePath, String isPath, String isNoStopPath, String aspIsPath, int retAspNo, String rlibModelPath, String outputRunfilePath) {
		try {
			IndexSearcher is = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(isPath).toPath()))));
			IndexSearcher isNoStops = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(isNoStopPath).toPath()))));
			IndexSearcher aspectIs = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(aspIsPath).toPath()))));
			Connection con = DataUtilities.getDBConnection(prop.getProperty("dbip"), prop.getProperty("db"), "paraent", prop.getProperty("dbuser"), prop.getProperty("dbpwd"));
			double[] optW = DataUtilities.getWeightVecFromRlibModel(rlibModelPath);
			HashMap<String, HashMap<String, Double>> runFileObj = this.getRunfileObj(runfilePath);
			HashMap<String, HashMap<String, Double>> rerankedRunFile = this.reRank(runFileObj, is, isNoStops, aspectIs, con, retAspNo, optW);
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputRunfilePath)));
			for(String query:rerankedRunFile.keySet()) {
				for(String retPara:rerankedRunFile.get(query).keySet()) {
					bw.write(query+" Q0 "+retPara+" 0 "+rerankedRunFile.get(query).get(retPara)+" PARASIM-RERANKED\n");
				}
			}
			bw.close();
		} catch (ClassNotFoundException | SQLException | IOException | ParseException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			Properties prop = new Properties();
			prop.load(new FileReader(new File("project.properties")));
			String runfilePath = args[0];
			String isPath = args[1];
			String isNoStopPath = args[2];
			String aspIsPath = args[3];
			int retAspNo = Integer.parseInt(args[4]);
			String rlibModelPath = args[5];
			String outputRunfilePath = args[6];
			
			ParasimReranker reranker = new ParasimReranker();
			reranker.writeRerankedRunfile(prop, runfilePath, isPath, isNoStopPath, aspIsPath, retAspNo, rlibModelPath, outputRunfilePath);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
