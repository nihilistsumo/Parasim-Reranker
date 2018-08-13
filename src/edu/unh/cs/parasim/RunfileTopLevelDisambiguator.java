package edu.unh.cs.parasim;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;

import edu.unh.cs.parasim.AspectSimilarity;

public class RunfileTopLevelDisambiguator {
	
	/*topK
	 * It decides which toplevelsection contained in commonHierQueries is best for paraID using para similarity
	 */
	public String getBestMatchTopLevelSection(HashMap<String, HashMap<String, Double>> runfileObject, ArrayList<String> commonHierQueries, String paraID, int topK,
			IndexSearcher is, IndexSearcher isNoStops, IndexSearcher aspectIs, Connection con, int retAspNo, double[] optW) {
		String bestMatchTopLevelSection = "";
		HashMap<String, ArrayList<String>> topSectionHierQueries = new HashMap<String, ArrayList<String>>();
		for(String hierQuery:commonHierQueries) {
			String topQuery = hierQuery.split("/")[0]+"/"+hierQuery.split("/")[1];
			if(!topSectionHierQueries.containsKey(topQuery)) {
				ArrayList<String> hierQueryList = new ArrayList<String>();
				hierQueryList.add(hierQuery);
				for(String hierQuery2:commonHierQueries) {
					String topQuery2 = hierQuery2.split("/")[0]+"/"+hierQuery2.split("/")[1];
					if(!hierQuery.equalsIgnoreCase(hierQuery2) &&
							topQuery.equalsIgnoreCase(topQuery2)) {
						hierQueryList.add(hierQuery2);
					}
				}
				topSectionHierQueries.put(topQuery, hierQueryList);
			}
		}
		for(String t:topSectionHierQueries.keySet()) {
			System.out.println("Top: "+t);
			for(String hier:topSectionHierQueries.get(t))
				System.out.println(hier);
			System.out.println();
		}
		HashMap<String, ArrayList<String>> topSectionTopPassages = new HashMap<String, ArrayList<String>>();
		for(String topSection:topSectionHierQueries.keySet()) {
			ArrayList<String> hierQueries = topSectionHierQueries.get(topSection);
			ArrayList<String> topPassages = new ArrayList<String>();
			for(String hierQuery:hierQueries) {
				HashMap<String, Double> retParas = runfileObject.get(hierQuery);
				int n = topK;
				if(hierQueries.size()>1)
					n = (int)Math.ceil((double)topK/hierQueries.size());
				ArrayList<String> topNParas = this.topN(retParas, n);
				topPassages.addAll(topNParas);
			}
			topSectionTopPassages.put(topSection, topPassages);
		}
		HashMap<String, Double> topSectionParasimScores = new HashMap<String, Double>();
		for(String topSection:topSectionTopPassages.keySet()) {
			ArrayList<String> topPassages = topSectionTopPassages.get(topSection);
			double simScore = 0;
			for(String retPara:topPassages) {
				try {
					simScore+=this.getParasimScore(paraID, retPara, is, isNoStops, aspectIs, con, retAspNo, optW);
				} catch (IOException | ParseException | SQLException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			simScore/=topPassages.size();
			topSectionParasimScores.put(topSection, simScore);
		}
		double maxSimScore = 0;
		for(String topSec:topSectionParasimScores.keySet()) {
			if(bestMatchTopLevelSection.equalsIgnoreCase(""))
				bestMatchTopLevelSection = topSec;
			if(topSectionParasimScores.get(topSec)>maxSimScore) {
				maxSimScore = topSectionParasimScores.get(topSec);
				bestMatchTopLevelSection = topSec;
			}
		}
		return bestMatchTopLevelSection;
	}
	
	public double getParasimScore(String keyPara, String retPara, IndexSearcher is, IndexSearcher isNoStops, IndexSearcher aspectIs, Connection con, int retAspNo, double[] optW) throws IOException, ParseException, SQLException, InterruptedException {
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
	
	public void disambiguate(String runfilePath) throws IOException {
		CandidateReader cr = new CandidateReader();
		HashMap<String, HashMap<String, Double>> runfileObject = cr.getRunfileObj(runfilePath);
		//this.getBestMatchTopLevelSection(runfileObject, cr.getCommonParas(runfileObject).get("enwiki:Food%20security_a4abffb231de1054e13dfff9081a88444e0b9ccc"), "a4abffb231de1054e13dfff9081a88444e0b9ccc", 10);
	}
	
	public ArrayList<String> topN(HashMap<String, Double> paraWithScores, int n) {
		ArrayList<String> sortedTopN = new ArrayList<String>();
		HashMap<String, Double> temp = new HashMap<String, Double>();
		for(String k:paraWithScores.keySet())
			temp.put(k, paraWithScores.get(k));
		for(int i=0; i<n; i++) {
			double max = 0;
			String topP = "";
			for(String p:temp.keySet()) {
				if(temp.get(p)>max) {
					topP = p;
					max = temp.get(p);
				}
			}
			sortedTopN.add(topP);
			temp.remove(topP);
		}
		return sortedTopN;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		RunfileTopLevelDisambiguator dis = new RunfileTopLevelDisambiguator();
		try {
			dis.disambiguate("/home/sumanta/Documents/treccar-submission-try/combined-bm25-lmds-bool-classic-5cv-by1train-run");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
