package edu.unh.cs.parasim;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import edu.unh.cs.util.DataUtilities;

public class AspectSimilarity {
	
	public void insertEntitiesInDB(String paraEntitiesFile, Connection con) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File(paraEntitiesFile)));
			String line = br.readLine();
			System.out.println("Starting...");
			int j=0;
			while(line!=null) {
				String[] elements = line.split("\t");
				if(elements.length>1) {
					String paraID = elements[0];
					PreparedStatement preparedStatement = con.prepareStatement("select * from paraent where paraid = ?");
					preparedStatement.setString(1, paraID);
					ResultSet resultSet = preparedStatement.executeQuery();
					if(!resultSet.next()) {
						String entString = elements[1];
						preparedStatement = con.prepareStatement("insert into paraent values (?,?)");
						preparedStatement.setString(1, paraID);
						preparedStatement.setString(2, entString);
						try {
							preparedStatement.executeUpdate();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							System.out.println("\ntried to process: "+line);
							e.printStackTrace();
						}
					}
				}
				j++;
				if (j % 10000 == 0) {
	                System.out.print('.');
	            }
				line = br.readLine();
			}
			System.out.println("\nDone");
			br.close();
		} catch (IOException | SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/*
	public double aspectRelationScore(TopDocs keyAspects, TopDocs retAspects, IndexSearcher is, IndexSearcher aspIs, Connection con, String option, String print) throws IOException, ParseException, SQLException {
		double score = 0;
		int count = 0;
		QueryParser qpLead = new QueryParser("LeadText", new StandardAnalyzer());
		QueryParser qpText = new QueryParser("Text", new StandardAnalyzer());
		for(ScoreDoc keyAsp:keyAspects.scoreDocs) {
			Document keyAspDoc = aspIs.doc(keyAsp.doc);
			String keyLeadText = keyAspDoc.getField("LeadText").stringValue();
			String keyText = keyAspDoc.getField("Text").stringValue();
			String keyAspParas = keyAspDoc.getField("ParasInSection").stringValue();
			String[] keyAspEntities = this.retrieveEntitiesFromAspParas(keyAspParas, con);
			if(print.equalsIgnoreCase("print")) {
				System.out.println("\nEntities from Key aspect "+keyAspDoc.getField("Id").stringValue());
				for(String ent:keyAspEntities)
					System.out.print(ent+" ");
			}
			Query q = null;
			if(option.equalsIgnoreCase("asptext"))
				q = qpText.parse(QueryParser.escape(keyText));
			else if(option.equalsIgnoreCase("asplead"))
				q = qpLead.parse(QueryParser.escape(keyLeadText));
			for(ScoreDoc retAsp:retAspects.scoreDocs) {
				Document retAspDoc = aspIs.doc(retAsp.doc);
				//String retAspText = retAspDoc.getField("Text").stringValue();
				//String[] retAspEntities = this.retrieveEntitiesFromAspText(retAspText, is, con);
				String retAspParas = retAspDoc.getField("ParasInSection").stringValue();
				String[] retAspEntities = this.retrieveEntitiesFromAspParas(retAspParas, con);
				if(print.equalsIgnoreCase("print")) {
					System.out.println("\nEntities from aspect "+retAspDoc.getField("Id").stringValue());
					for(String ent:retAspEntities)
						System.out.print(ent+" ");
				}
				double currSimScore = 0;
				if(option.equalsIgnoreCase("ent"))
					currSimScore = this.entitySimilarityScore(Arrays.asList(keyAspEntities), Arrays.asList(retAspEntities));
				else if(option.equalsIgnoreCase("asptext")) {
					currSimScore = aspIs.explain(q, retAsp.doc).getValue();
				}
				else if(option.equalsIgnoreCase("asplead")) {
					currSimScore = aspIs.explain(q, retAsp.doc).getValue();
				}
				
				if(option.equalsIgnoreCase("rel"))
					System.out.println("\nRelevant");
				else
					System.out.println("\nNon-relevant");
				System.out.println("Key aspect importance = "+keyAsp.score/keyAspects.getMaxScore());
				System.out.println("Ret aspect importance = "+retAsp.score/retAspects.getMaxScore());
				System.out.println("Entity similarity score = "+currEntSimScore);
				
				score+=currSimScore*(keyAsp.score/keyAspects.getMaxScore())*(retAsp.score/retAspects.getMaxScore());
				count++;
			}
		}
		if(print.equalsIgnoreCase("print")) {
			System.out.println("Asp rel score = "+score);
		}
		return score/count;
	}
	*/
	
	// returned is a double array: [normalized ent sim, norm txt sim, norm lead sim]
	public double[] aspectRelationScore(TopDocs keyAspects, TopDocs retAspects, IndexSearcher is, IndexSearcher aspIs, Connection con, String print) throws IOException, ParseException, SQLException, InterruptedException {
		double[] score = new double[3];
		int count = keyAspects.scoreDocs.length*retAspects.scoreDocs.length;
		ExecutorService exec = Executors.newCachedThreadPool();
		double[][] individualScores = new double[count][3];
		int i = 0;
		for(ScoreDoc keyDoc:keyAspects.scoreDocs) {
			for(ScoreDoc retDoc:retAspects.scoreDocs) {
				Runnable aspectCalcThread = new AspectRelationCalculationThread(keyDoc, retDoc, keyAspects.getMaxScore(), retAspects.getMaxScore(), is, aspIs, con, individualScores, i);
				exec.execute(aspectCalcThread);
				i++;
			}
		}
		exec.shutdown();
		exec.awaitTermination(1, TimeUnit.DAYS);
		for(int k=0; k<individualScores.length; k++) {
			for(int f=0; f<3; f++) {
				score[f]+=individualScores[k][f];
			}
		}
		for(int f=0; f<3; f++) {
			score[f] = score[f]/count;
		}
		return score;
	}
	
	public class AspectRelationCalculationThread implements Runnable {
		
		public AspectRelationCalculationThread(ScoreDoc keyDoc, ScoreDoc retDoc, float keyMaxScore, float retMaxScore, IndexSearcher is, IndexSearcher aspIs, Connection con, double[][] score, int index) {
			// TODO Auto-generated constructor stub
			try {
				Document keyAspDoc = aspIs.doc(keyDoc.doc);
				String keyLeadText = keyAspDoc.getField("LeadText").stringValue();
				String keyText = keyAspDoc.getField("Text").stringValue();
				String keyAspParas = keyAspDoc.getField("ParasInSection").stringValue();
				Document retAspDoc = aspIs.doc(retDoc.doc);
				String retAspParas = retAspDoc.getField("ParasInSection").stringValue();
				String[] keyAspEntities = this.retrieveEntitiesFromAspParas(keyAspParas, con);
				String[] retAspEntities = this.retrieveEntitiesFromAspParas(retAspParas, con);
				QueryParser qpLead = new QueryParser("LeadText", new StandardAnalyzer());
				QueryParser qpText = new QueryParser("Text", new StandardAnalyzer());
				Query qt = qpText.parse(QueryParser.escape(keyText));
				Query ql = qpLead.parse(QueryParser.escape(keyLeadText));
				
				double currEntSimScore = this.entitySimilarityScore(Arrays.asList(keyAspEntities), Arrays.asList(retAspEntities));
				double currTxtSimScore = aspIs.explain(qt, retDoc.doc).getValue();
				double currLeadSimScore = aspIs.explain(ql, retDoc.doc).getValue();
				
				
				//System.out.println("Key aspect importance = "+keyAsp.score/keyAspects.getMaxScore());
				//System.out.println("Ret aspect importance = "+retAsp.score/retAspects.getMaxScore());
				//System.out.println("Entity similarity score = "+currEntSimScore);
				
				score[index][0]=currEntSimScore*(keyDoc.score/keyMaxScore)*(retDoc.score/retMaxScore);
				score[index][1]=currTxtSimScore*(keyDoc.score/keyMaxScore)*(retDoc.score/retMaxScore);
				score[index][2]=currLeadSimScore*(keyDoc.score/keyMaxScore)*(retDoc.score/retMaxScore);
				//System.out.print(".");
			} catch (IOException | ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public String[] retrieveEntitiesFromAspParas(String paras, Connection con) {
			String ent = "";
			for(String paraID:paras.split(" ")) {
				try {
					PreparedStatement preparedStatement = con.prepareStatement("select ent from paraent where paraid = ?");
					preparedStatement.setString(1, paraID);
					ResultSet resultSet = preparedStatement.executeQuery();
					if(resultSet.next())
						ent += " "+resultSet.getString(1);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return ent.trim().split(" ");
		}
		
		public double entitySimilarityScore(List<String> entListKey, List<String> entListRet) {
			double score = 0;
			HashSet<String> keySet = new HashSet<String>(entListKey);
			HashSet<String> retSet = new HashSet<String>(entListRet);
			for(String ent:keySet) {
				if(retSet.contains(ent))
					score+=1.0;
			}
			score/=keySet.size();
			return score;
		}
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	public String[] retrieveEntitiesFromAspText(String aspText, IndexSearcher is, Connection con) throws ParseException, IOException, SQLException {
		String ent = "";
		String[] aspParas = aspText.split("\n\n\n")[0].split("\n");
		QueryParser qp = new QueryParser("parabody", new StandardAnalyzer());
		//BooleanQuery.setMaxClauseCount(65536);
		
		for(String aspPara:aspParas) {
			if(aspPara.endsWith(".")) {
				Query q = qp.parse(QueryParser.escape(aspPara));
				//Query q = qp.parse(aspPara);
				Document paraDoc = is.doc(is.search(q, 1).scoreDocs[0].doc);
				String paraText = paraDoc.get("parabody");
				if(paraText.equalsIgnoreCase(aspPara)) {
					String paraID = paraDoc.get("paraid");
					PreparedStatement preparedStatement = con.prepareStatement("select ent from paraent where paraid = ?");
					preparedStatement.setString(1, paraID);
					ResultSet resultSet = preparedStatement.executeQuery();
					if(resultSet.next())
						ent += " "+resultSet.getString(1);
				}
			}
		}
		//System.out.println("Entites: "+ent);
		return ent.trim().split(" ");
	}
	
	public String[] retrieveEntitiesFromAspParas(String paras, Connection con) {
		String ent = "";
		for(String paraID:paras.split(" ")) {
			try {
				PreparedStatement preparedStatement = con.prepareStatement("select ent from paraent where paraid = ?");
				preparedStatement.setString(1, paraID);
				ResultSet resultSet = preparedStatement.executeQuery();
				if(resultSet.next())
					ent += " "+resultSet.getString(1);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return ent.trim().split(" ");
	}
	
	public double entitySimilarityScore(List<String> entListKey, List<String> entListRet) {
		double score = 0;
		HashSet<String> keySet = new HashSet<String>(entListKey);
		HashSet<String> retSet = new HashSet<String>(entListRet);
		for(String ent:keySet) {
			if(retSet.contains(ent))
				score+=1.0;
		}
		score/=keySet.size();
		return score;
	}
	
	public double aspectMatchRatio(ScoreDoc[] keyAspects, ScoreDoc[] retAspects) {
		int match = 0;
		for(ScoreDoc d:keyAspects) {
			for(ScoreDoc retD:retAspects) {
				if(d.doc==retD.doc)
					match++;
			}
		}
		return (double)match/keyAspects.length;
	}
	
	public double entityMatchRatio(String paraID1, String paraID2, Connection con, String print) {
		ArrayList<String> commonEntities = new ArrayList<String>();
		double ratio = 0.0;
		try {
			PreparedStatement preparedStatement = con.prepareStatement("select ent from paraent where paraid = ?");
			preparedStatement.setString(1, paraID1);
			ResultSet resultSet = preparedStatement.executeQuery();
			if(!resultSet.next()) {
				if(print.equalsIgnoreCase("print"))
					System.out.println("No entities linked in Keypara "+paraID1);
				return 0.0;
			}
			String ent1 = resultSet.getString(1);
			if(print.equalsIgnoreCase("print")) {
				System.out.println("\nKeypara "+paraID1+" entities: "+ent1);
			}
			preparedStatement.setString(1, paraID2);
			resultSet = preparedStatement.executeQuery();
			if(!resultSet.next()) {
				if(print.equalsIgnoreCase("print"))
					System.out.println("No entities linked in ret para "+paraID2);
				return 0.0;
			}
			String ent2 = resultSet.getString(1);
			if(print.equalsIgnoreCase("print")) {
				System.out.println("Ret para "+paraID2+" entities: "+ent2);
			}
			List<String> entList1 = Arrays.asList(ent1.split(" "));
			List<String> entList2 = Arrays.asList(ent2.split(" "));
			for(String e:entList1) {
				if(entList2.contains(e))
					commonEntities.add(e);
			}
			ratio = ((double)commonEntities.size())/entList1.size();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ratio;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
