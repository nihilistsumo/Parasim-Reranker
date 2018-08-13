package edu.unh.cs.parasim;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class CandidateReader {
	
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
	
	/*
	 * This method returns those paragraphs in the runfile that are shared/common between two or more
	 * top level section of an article
	 * HashMap: Common paragraph ID --> List of hierarchical queries that retrieved this paragraph
	 */
	public HashMap<String, ArrayList<String>> getCommonParas(HashMap<String, HashMap<String, Double>> runfileObject) throws IOException {
		HashMap<String, ArrayList<String>> commonParasTopSectionMap = new HashMap<String, ArrayList<String>>();
		HashMap<String, ArrayList<String>> pageHierQueryMap = new HashMap<String, ArrayList<String>>();
		for(String hierQuery:runfileObject.keySet()) {
			String pageID = hierQuery.split("/")[0];
			if(pageHierQueryMap.keySet().contains(pageID))
				pageHierQueryMap.get(pageID).add(hierQuery);
			else {
				ArrayList<String> hierQueryList = new ArrayList<String>();
				hierQueryList.add(hierQuery);
				pageHierQueryMap.put(pageID, hierQueryList);
			}
		}
		for(String pageID:pageHierQueryMap.keySet()) {
			if(pageID.equals("enwiki:Food%20security"))
				System.out.println("this one");
			ArrayList<String> hierQueriesInPage = pageHierQueryMap.get(pageID);
			for(int i=0; i<hierQueriesInPage.size()-1; i++) {
				String hierQuery = hierQueriesInPage.get(i);
				String topLevelSection = hierQuery.split("/")[1];
				HashMap<String, Double> retrievedParas = runfileObject.get(hierQuery);
				for(String retrievedPara:retrievedParas.keySet()) {
					for(int j=i+1; j<hierQueriesInPage.size(); j++) {
						String hierQuery2 = hierQueriesInPage.get(j);
						if(hierQuery.equalsIgnoreCase(hierQuery2))
							continue;
						String topLevelSection2 = hierQuery2.split("/")[1];
						HashMap<String, Double> retrievedParas2 = runfileObject.get(hierQuery2);
						if(retrievedParas2.containsKey(retrievedPara) && 
								!topLevelSection.equalsIgnoreCase(topLevelSection2)) {
							if(commonParasTopSectionMap.containsKey(pageID+"_"+retrievedPara)) {
								ArrayList<String> sharedHierQueries = commonParasTopSectionMap.get(pageID+"_"+retrievedPara);
								if(!sharedHierQueries.contains(hierQuery))
									sharedHierQueries.add(hierQuery);
								if(!sharedHierQueries.contains(hierQuery2))
									sharedHierQueries.add(hierQuery2);
								commonParasTopSectionMap.put(pageID+"_"+retrievedPara, sharedHierQueries);
							}
							else {
								ArrayList<String> sharedHierQueries = new ArrayList<String>();
								sharedHierQueries.add(hierQuery);
								sharedHierQueries.add(hierQuery2);
								commonParasTopSectionMap.put(pageID+"_"+retrievedPara, sharedHierQueries);
							}
							
						}
					}
				}
			}
			System.out.print(".");
		}
		System.out.println();
		return commonParasTopSectionMap;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		CandidateReader cr = new CandidateReader();
		try {
			HashMap<String, ArrayList<String>> commons = cr.getCommonParas(cr.getRunfileObj("/home/sumanta/Documents/treccar-submission-try/combined-bm25-lmds-bool-classic-5cv-by1train-run"));
			int totalCalc = 0;
			for(String para:commons.keySet()) {
				ArrayList<String> queries = commons.get(para);
				System.out.println("ParaID: "+para+" No. of common queries: "+queries.size());
				for(String query:queries) {
					System.out.println(query);
				}
				totalCalc+=queries.size();
				System.out.println();
			}
			System.out.println("Total no. of para similarity calc: "+totalCalc);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
