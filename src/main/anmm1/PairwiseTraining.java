package main.anmm1;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import com.FileUtil;
import com.MatrixUtil;

import main.core.QADocuments;
import main.core.ThreadComputeGradaNMM1;
import main.core.QADocuments.Document;

import conf.ModelParams;

/**Class for pair-wise training to learn optimal weights
 * 
 * @author Liu Yang
 * @email  lyang@cs.umass.edu
 */

public class PairwiseTraining {

	//Randomly initialize the kDeminsion weight wk
	//Randomly initialize the gate parameter vp
	public void initWeights(String modelName, String binNum, String vectorDimen) {
		// TODO Auto-generated method stub
		//init vp
		ModelParams.vp = new double[Integer.valueOf(vectorDimen)];
		for(int i = 0; i < ModelParams.vp.length; i++){
			ModelParams.vp[i] = Math.random();
		}
		
		//init rt
		ModelParams.rt = new double[ModelParams.addedHiddenLayerRTDim];
		for(int i = 0; i < ModelParams.rt.length; i++){
			ModelParams.rt[i] = Math.random();
		}
		
		//init wk
		if(modelName.equals("V0") || modelName.equals("V1") || modelName.equals("V2")) {
			ModelParams.wk = new double[ModelParams.maxPoolingSizeK];
		} else {
			ModelParams.wk = new double[Integer.valueOf(binNum)];
		}
		for(int i = 0; i < ModelParams.wk.length; i++){
			ModelParams.wk[i] = Math.random();
		}
	}
	
	//Construct QA positive/negative triples (S_x, S_y+, S_y-)
	//Make two answers a pair as long as they have different labels
	//Note that there may be no positive/negative answer sentences for some questions
	//Just compute the query number from the actual data instead of passing parameters
	public Set<String> constructQAPNTriples(QADocuments qaDocSet) {
		// TODO Auto-generated method stub
		Set<String> qaTriples = new HashSet<String>(); // A list of IDs for QATriples (S_x, S_y+, S_y-) QID PosAnswerID NegAnswerID
		int queryNum = computeQueryNumFromQADocSet(qaDocSet);
		
		int [] queryAnswersBoundary = new int[queryNum];
		findDiffQueryAnswerBounday(qaDocSet, queryAnswersBoundary, queryNum);
		int startIndex, endIndex, curQueryIndex=0;
		while(curQueryIndex < queryNum){
			//System.out.println("Construct QA Pos/Neg triples for Query: " + curQueryIndex);
			startIndex = queryAnswersBoundary[curQueryIndex];
			if(curQueryIndex <= (queryNum-2)) endIndex = queryAnswersBoundary[curQueryIndex + 1] - 1;
			else endIndex = qaDocSet.docs.size()-1;
			constructQATriplesForAQuery(qaDocSet, startIndex, endIndex, qaTriples);
			curQueryIndex++;
		}
		//System.out.println("Finished constructing QA Pos/Neg triples and qaTriples.size(): " + qaTriples.size());
		return qaTriples;
	}
	
	private int computeQueryNumFromQADocSet(QADocuments qaDocSet) {
		// TODO Auto-generated method stub
		Set<String> qidSet = new HashSet<String>();
		for(Document doc: qaDocSet.docs){
			qidSet.add(doc.qid);
		}
		return qidSet.size();
	}

	private void constructQATriplesForAQuery(QADocuments qaDocSet,
			int startIndex, int endIndex, Set<String> qaTriples) {
		// TODO Auto-generated method stub
		//System.out.println("qaDocSet.docs.size: " + qaDocSet.docs.size());
		for(int i = startIndex; i <= endIndex; i++){
 			Document qaDoc1 = qaDocSet.docs.get(i);
 			//System.out.println("qid: " + qaDoc1.qid + "\t" + "aid: " + qaDoc1.answerSentId + "\t" + "label: " + qaDoc1.label);
			//Consider all pairs
			//if qid1 == qid2 && label1 != label2 && notStoredBefore(maintain order for S_y+/S_y-)
			for(int j = startIndex; j < endIndex; j++){
				Document qaDoc2 = qaDocSet.docs.get(j);
				if(!qaDoc2.qid.equals(qaDoc1.qid)){
					System.err.println("Error: find different qid for qaDoc1 and qaDoc2: qaDoc1.qid = " + qaDoc1.qid + " qaDoc2.qid = " + qaDoc2.qid);
					return;
				}
				if(qaDoc1.label ==  qaDoc2.label) continue;
				//If the difference between two labels >=2, continue; only pair (0,1) (1,2) (2,3) (3,4)
				if(Math.abs(qaDoc1.label - qaDoc2.label) >= 2) continue;
				String qaTripleString = qaDoc1.qid;
				if(qaDoc1.label > qaDoc2.label){ // qid posAid negAid
					qaTripleString += "\t" + qaDoc1.answerSentId + "\t" + qaDoc2.answerSentId;
				} else if(qaDoc1.label < qaDoc2.label){
					qaTripleString += "\t" + qaDoc2.answerSentId + "\t" + qaDoc1.answerSentId;
				} else {
					System.err.println("Error: find qaTripleString with the same LabelScore. ");
					return;
				}
				//S_x \t S_y+ \t S_y-
				if(!qaTriples.contains(qaTripleString)){
					//System.out.println("Test: add new qaTripleString: " + qaTripleString);
					//System.out.println("qaDoc1.label: " + qaDoc1.label);
					//System.out.println("qaDoc2.label: " + qaDoc2.label);
					qaTriples.add(qaTripleString);
				}
			}
		}
	}

	//Find the boundary between answerSent under different queries
	//Store the first line index of each query
	private void findDiffQueryAnswerBounday(QADocuments qaDocSet,
			int[] queryAnswersBoundary, int queryNum) {
		// TODO Auto-generated method stub
		String curQid = qaDocSet.docs.get(0).qid;
		int i = 0;
		int j = 0;
		queryAnswersBoundary[j] = 0;
		while(i < qaDocSet.docs.size()){
			if(!curQid.equals(qaDocSet.docs.get(i).qid)){
				//System.out.println("found new qid: " + qaDocSet.docs.get(i).qid);
				curQid = qaDocSet.docs.get(i).qid;
				j++;
				//System.out.println("current j and i: " + j + "\t" + i);
				queryAnswersBoundary[j] = i;
			}
			i++;
		}
		
		if(j != queryNum - 1) {
			System.err.println("Error: find boundary error: j = " + j);
		}
	}

	//save QA triples to a file
	public void saveQATripleFile(Set<String> qaTriples, String qaTripleFile) throws IOException {
		// TODO Auto-generated method stub
		FileWriter qaTripleWriter = new FileWriter(qaTripleFile);
		for(String qat : qaTriples){
			qaTripleWriter.append(qat + "\n");
			qaTripleWriter.flush();
		}
		qaTripleWriter.close();
	}
	
	//pair wise SGD training given all (S_x, S_y+, S_y-) triples   S_x \t S_y+ \t S_y-
	//Settings: with gate
	public void pairwiseSGDTraining(QADocuments trainQADocSet, 
			Set<String> trainQATriples, QADocuments testQADocSet,
			Set<String> testQATriples, Map<String, Double[]> termToWordVectorMap,
			 String ModelResDataFolder, String modelName,
			String binNum, String vecDim, String runModelType) {
		// TODO Auto-generated method stub
		int iterations = ModelParams.iterations, saveStep = ModelParams.saveStep, beginSaveIters = ModelParams.beginSaveIters;
		Map<String, ArrayList<ArrayList<Double>>> qaMatchMatrixMap = new HashMap<String, ArrayList<ArrayList<Double>>>(); //Key qid \t aid   Value qaMatchMatrix
		Map<String, String []> qidToqTermsMap = new HashMap<String, String []>(); //Key:qid   Value: qTermsArray
		Map<String, Double[]> qidToUPrimeArrayMap = new HashMap<String, Double[]>();//Key:qid  Value: uPrimeArray
		Map<String, Double[]> qidToQueryMeanVecMap = new HashMap<String, Double[]>();//Key:qid Value: queryMeanVector
		Map<String, Double> qidToSumExpMap = new HashMap<String, Double>();//Key:qid Value: sumExp
		Map<String, Double> termToIDFMap = new HashMap<String, Double>();//Key:term Value: idf value
		//!Need to compute qidToQueryMeanVecMap once and store it to avoid duplicate computations
		initQidToQueryMeanVecMap(qidToQueryMeanVecMap, trainQADocSet, testQADocSet, termToWordVectorMap, Integer.valueOf(vecDim));
		initQidToqTermsMap(qidToqTermsMap, trainQADocSet, testQADocSet);
		initTermToIDFMap(termToIDFMap, ModelResDataFolder);
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		//Note that to speed up the training process, we can change the structure of qaMatchMatrixMap
		//We can map each row of qaMatchMatrixMap into B bins
		initQAMatchMatrixMap(trainQADocSet, qaMatchMatrixMap, modelName, Integer.valueOf(binNum));
		initQAMatchMatrixMap(testQADocSet, qaMatchMatrixMap, modelName, Integer.valueOf(binNum));
		
		//!!!Note that UPrimeArray need to be updated in each iteration/triple since v_p is updated in each iteration
		updateQidToUPrimeArrayMap(trainQADocSet, testQADocSet, termToWordVectorMap, qidToUPrimeArrayMap, qidToSumExpMap, qidToQueryMeanVecMap, modelName, qidToqTermsMap);
		
		if(iterations < saveStep + beginSaveIters){
			System.err.println("Error: the number of iterations should be larger than " + (saveStep + beginSaveIters));
			System.exit(0);
		}
		//System.out.println("Begin pairwise training: num of train triples and test triples: " + trainQATriples.size() + "  " + testQATriples.size());
		//!Note which part need to be updated in each iteration,
		for(int i = 1; i <= iterations; i++){
			System.out.println("Iteration/Epoch " + i);
			Date dateObj = new Date();
			System.out.println("Test1 Time: " + df.format(dateObj));
			if((i >= beginSaveIters) && (((i - beginSaveIters) % saveStep) == 0)){
				//Saving the model
				System.out.println("Saving model at iteration " + i + " ... ");
				saveIteratedModel(i, trainQADocSet, ModelResDataFolder);
			}
			double oldLoss = 0;
			//In every iteration, recompute the loss and see whether it decreases
			if(i == 1){
				oldLoss = computeHingeloss(trainQATriples, qaMatchMatrixMap, qidToUPrimeArrayMap, qidToSumExpMap,  modelName, termToIDFMap, qidToqTermsMap);
				System.out.println("Current loss on training data: " + oldLoss);
			} else {
				double curLoss = computeHingeloss(trainQATriples, qaMatchMatrixMap, qidToUPrimeArrayMap, qidToSumExpMap, modelName, termToIDFMap, qidToqTermsMap);
				System.out.println("Current loss on training data: " + curLoss);
				double curTestLoss = computeHingeloss(testQATriples, qaMatchMatrixMap, qidToUPrimeArrayMap, qidToSumExpMap, modelName, termToIDFMap, qidToqTermsMap);
				if(runModelType.equals("Testing")) {
					System.out.println("Current loss on testing data: " + curTestLoss);
				} else {
					System.out.println("Current loss on validation/dev data: " + curTestLoss);
				}
				
				if(i % 5 == 0){
					//In every 5 iterations, compute IR metrics on testing data with current model parameters w_k and v_p and write predicted score to file
					//To combine with other additional features like WO/QL/BM25, we need to print out score for all data including train/valid/test
					computeIRMetricsEval(trainQADocSet, testQADocSet, qidToUPrimeArrayMap, qidToSumExpMap,  i, ModelResDataFolder, qaMatchMatrixMap, modelName, termToIDFMap, qidToqTermsMap);
					//In every 5 iterations, print learnt query term importance for training/testing data by gate function
					if(!modelName.equals("V4-4")){
						printGateLearntQueryTermImportance(trainQADocSet, testQADocSet, qidToUPrimeArrayMap,  qidToqTermsMap, i, ModelResDataFolder, modelName);
					}
				}
				if(curLoss - oldLoss < ModelParams.lossChangeThreshold){
					System.out.println("curLoss - oldLoss is less than lossChangeThreshold. Stop. curLoss - oldLoss  and lossChangeThreshold are : " + (curLoss - oldLoss) + "\t" + ModelParams.lossChangeThreshold);
					break;
				}
				oldLoss = curLoss;
			}
			
			double adaptiveEta1 = computeAdaptiveLR(ModelParams.eta1, i, ModelParams.iterations);
			double adaptiveEta2 = computeAdaptiveLR(ModelParams.eta2, i, ModelParams.iterations);
			
			double[] oldWk = new double[ModelParams.wk.length];
			for(int t = 0; t < oldWk.length; t++){
				oldWk[t] = ModelParams.wk[t];
			}
			
			double[] oldVp = new double[ModelParams.vp.length];
			if(!modelName.equals("V4-4")){
				for(int p = 0; p < oldVp.length; p++){
					oldVp[p] = ModelParams.vp[p];
				}
			}
			
			double wkChangeSumSquare = 0;
			double vpChangeSumSquare = 0;
			
			//Use SGD to update weight w_k and v_p
			//Update 03302016 
			//Use mini-batch gradient decent and multi-thread implementation
			//Java multi-thread implementation based on extending thread class
			dateObj = new Date();
			System.out.println("Test2 Time: " + df.format(dateObj));
			int curQATripleIndex = 0;
			double[] batchWKGrad = new double[ModelParams.wk.length];
			double[] batchVPGrad = new double[ModelParams.vp.length];
			CountDownLatch countDownLatch = new CountDownLatch(ModelParams.batchSize);
			
			for(String qaTriple : trainQATriples){
				curQATripleIndex++;
				
				ThreadComputeGradaNMM1 T1 = new ThreadComputeGradaNMM1( "Thread-" + curQATripleIndex, qaMatchMatrixMap,
						qidToqTermsMap, qidToUPrimeArrayMap, qidToQueryMeanVecMap, qidToSumExpMap, termToWordVectorMap, batchWKGrad,
						batchVPGrad,  modelName, termToIDFMap, qaTriple, countDownLatch);
                T1.start();
				
				if(curQATripleIndex % ModelParams.batchSize == 0 || curQATripleIndex == trainQATriples.size()) { 
					//mini-batch gradient decent
					if(curQATripleIndex == trainQATriples.size()) System.out.println("Finish the last batch, update model parameters");
					else System.out.println("Finish batch " + curQATripleIndex / ModelParams.batchSize + ", update model parameters");
					
					//The main thread will wait all sub-threads in the same batch to finish and then move forward
	                try {
						countDownLatch.await();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block3
						e.printStackTrace();
					}
	                //dateObj = new Date();
	    			//System.out.println("Test3 Time when finished one batch: " + df.format(dateObj));
					
					//update parameters
					//Update w_k
					//compute the gradient of w_k and v_p
					for(int k = 0; k < ModelParams.wk.length; k++){
						ModelParams.wk[k] -= adaptiveEta1 * batchWKGrad[k];
						batchWKGrad[k] = 0;
					}
					
					//Update v_p
					if(!modelName.equals("V4-4")){
						for(int p = 0; p < ModelParams.vp.length; p++){
							//System.out.println("Test: gradients for vp: " + computeCradientVp(QAMatchMatrixPos, QAMatchMatrixNeg, p, uPrimeArray, termToWordVectorMap, qidToqWordsMap.get(qid), qaDocSet));
							//Notice that we can't use uPrimeArray when compute  GradientVp. Instead, we need to use the latest value
							ModelParams.vp[p] -= adaptiveEta2 * batchVPGrad[p];
							batchVPGrad[p] = 0;
						}
					updateQidToUPrimeArrayMap(trainQADocSet, testQADocSet, termToWordVectorMap, qidToUPrimeArrayMap, qidToSumExpMap, qidToQueryMeanVecMap, modelName, qidToqTermsMap);
					}
					
					if(curQATripleIndex == trainQATriples.size()){
						System.out.println("Finish one Epoch!");
					} else if(curQATripleIndex >= (trainQATriples.size() / ModelParams.batchSize) * ModelParams.batchSize){
						//begin last batch
						countDownLatch = new CountDownLatch(trainQATriples.size() - (trainQATriples.size() / ModelParams.batchSize) * ModelParams.batchSize);
					} else {
						//not the last batch
						countDownLatch = new CountDownLatch(ModelParams.batchSize);
					}
				}		
			}
			
			for(int k = 0; k < ModelParams.wk.length; k++){
				wkChangeSumSquare += Math.pow(ModelParams.wk[k] - oldWk[k], 2);
			}
			System.out.println("After scan all the triples, wkChangeSumSquare = " + wkChangeSumSquare);
			
			if(!modelName.equals("V4-4")){
				for(int p = 0; p < ModelParams.vp.length; p++){
					vpChangeSumSquare += Math.pow(ModelParams.vp[p] - oldVp[p], 2);
					
				}
				System.out.println("After scan all the triples, vpChangeSumSquare = " + vpChangeSumSquare);
			}
		}
	}

	private void initTermToIDFMap(Map<String, Double> termToIDFMap, String modelResDataFolder) {
		// TODO Auto-generated method stub
		String idfFileName = modelResDataFolder + "term.idf";
		ArrayList<String> lines = new ArrayList<String>();
		FileUtil.readLines(idfFileName, lines);
		for(String line : lines){
			String[] tokens = line.split("\t");
			termToIDFMap.put(tokens[0], Double.valueOf(tokens[2]));
		}
	}

	private void initQidToqTermsMap(Map<String, String[]> qidToqTermsMap, QADocuments trainQADocSet,
			QADocuments testQADocSet) {
		// TODO Auto-generated method stub
		for(Document doc : trainQADocSet.docs){
			if(!qidToqTermsMap.containsKey(doc.qid)){
				String [] qWordsCopy = new String [doc.questionSentWords.length];
				for(int j = 0; j < doc.questionSentWords.length; j++){
					qWordsCopy[j] = trainQADocSet.indexToTermMap.get(doc.questionSentWords[j]);
				}
				qidToqTermsMap.put(doc.qid, qWordsCopy);
			}
		}
		for(Document doc : testQADocSet.docs){
			if(!qidToqTermsMap.containsKey(doc.qid)){
				String [] qWordsCopy = new String [doc.questionSentWords.length];
				for(int j = 0; j < doc.questionSentWords.length; j++){
					qWordsCopy[j] = testQADocSet.indexToTermMap.get(doc.questionSentWords[j]);
				}
				qidToqTermsMap.put(doc.qid, qWordsCopy);
			}
		}
	}

	private void initQidToQueryMeanVecMap(Map<String, Double[]> qidToQueryMeanVecMap, QADocuments trainQADocSet,
			QADocuments testQADocSet, Map<String, Double[]> termToWordVectorMap, Integer vecDim) {
		// TODO Auto-generated method stub
		//qidToQueryMeanVecMap  key: qid  value:queryMeanVec
		//Compute the mean of all word vectors of terms in qid
		Set<String> qidSet = new HashSet<String>();
		for(Document doc : trainQADocSet.docs){
			if(qidSet.contains(doc.qid)) continue;
			addNewQidQueryMeanVecPair(trainQADocSet.indexToTermMap, doc, qidToQueryMeanVecMap, termToWordVectorMap, vecDim);
			qidSet.add(doc.qid);
		}
		for(Document doc : testQADocSet.docs){
			if(qidSet.contains(doc.qid)) continue;
			addNewQidQueryMeanVecPair(testQADocSet.indexToTermMap, doc, qidToQueryMeanVecMap, termToWordVectorMap, vecDim);
			qidSet.add(doc.qid);
		}
	}

	private void addNewQidQueryMeanVecPair(ArrayList<String> indexToTermMap, Document doc, Map<String, Double[]> qidToQueryMeanVecMap,
			Map<String, Double[]> termToWordVectorMap, Integer vecDim) {
		// TODO Auto-generated method stub
		Double [] queryMeanVec = new Double[vecDim];
		for(int z = 0; z < queryMeanVec.length; z++){
			queryMeanVec[z] = 0.0;
		}
		for(int j = 0; j < doc.questionSentWords.length; j++){
			Double[] queryTermWordVec = termToWordVectorMap.get(indexToTermMap.get(doc.questionSentWords[j]));
			for(int z = 0; z < vecDim; z++){
				queryMeanVec[z] += queryTermWordVec[z];
			}
		}
		Double[] queryMeanVecCopy = new Double[vecDim];
		for(int z = 0; z < vecDim; z++){
			queryMeanVec[z] /= (double) doc.questionSentWords.length;
			queryMeanVecCopy[z] = queryMeanVec[z] ;
		}
		qidToQueryMeanVecMap.put(doc.qid, queryMeanVecCopy);
	}
	

	//Compute IR metrics on both training and testing data with current model parameters w_k and v_p and write predicted score to file
	//Setting: with gate function
	private void computeIRMetricsEval(QADocuments trainQADocSet, QADocuments testQADocSet, Map<String, Double[]> qidToUPrimeArrayMap, Map<String, Double> qidToSumExpMap, 
			int iter, String modelResDataFolder, Map<String, ArrayList<ArrayList<Double>>> qaMatchMatrixMap, 
			String modelName, Map<String, Double> termToIDFMap, Map<String, String[]> qidToqTermsMap) {
		// TODO Auto-generated method stub
		String trainDataScoreFile = modelResDataFolder + "nntextmatch_iter_" + iter +"_train.score";
		String testDataScoreFile = modelResDataFolder + "nntextmatch_iter_" + iter +"_test.score";
		// 030  Q0     ZF08-175-870  0     4238   prise1 
	    // qid  iter   docno         rank  sim    run_id 
		// dev_1 Q0 dev_1_asent_1 0 0.282 nntextmatch
		ArrayList<String> scoreLines = new ArrayList<String>();
		String line = "";
		for(int i = 0; i < testQADocSet.docs.size(); i++){
			line = testQADocSet.docs.get(i).qid + " Q0 " + testQADocSet.docs.get(i).answerSentId + " 0 ";
			double nntextmatchScore = computeForwardPredictScore(qaMatchMatrixMap.get(testQADocSet.docs.get(i).qid + "\t" + testQADocSet.docs.get(i).answerSentId), 
					qidToUPrimeArrayMap.get(testQADocSet.docs.get(i).qid), qidToSumExpMap.get(testQADocSet.docs.get(i).qid), modelName, termToIDFMap, qidToqTermsMap.get(testQADocSet.docs.get(i).qid));
			line += nntextmatchScore + " nntextmatch";
			scoreLines.add(line);
		}
		FileUtil.writeLines(testDataScoreFile, scoreLines);
		
		scoreLines.clear();
		line = "";
		for(int i = 0; i < trainQADocSet.docs.size(); i++){
			line = trainQADocSet.docs.get(i).qid + " Q0 " + trainQADocSet.docs.get(i).answerSentId + " 0 ";
			double nntextmatchScore = computeForwardPredictScore(qaMatchMatrixMap.get(trainQADocSet.docs.get(i).qid + "\t" + trainQADocSet.docs.get(i).answerSentId), 
					qidToUPrimeArrayMap.get(trainQADocSet.docs.get(i).qid), qidToSumExpMap.get(trainQADocSet.docs.get(i).qid), modelName, termToIDFMap, qidToqTermsMap.get(trainQADocSet.docs.get(i).qid));
			line += nntextmatchScore + " nntextmatch";
			scoreLines.add(line);
		}
		FileUtil.writeLines(trainDataScoreFile, scoreLines);
	}
	
	//Genterate qrel file for compute IR metrics
	public void generateQrelFile(QADocuments testQADocSet, String ModelResDataFolder) {
		// TODO Auto-generated method stub
		//"data/" + dataName + "/ModelRes/"
		String testDataQrelFile = ModelResDataFolder + "nntextmatch.qrel";
		//  qid  iter  docno  rel 
		ArrayList<String> qrelLines = new ArrayList<String>();
		String line = "";
		for(int i = 0; i < testQADocSet.docs.size(); i++){
			line = testQADocSet.docs.get(i).qid + " Q0 " + testQADocSet.docs.get(i).answerSentId;
			line += " " + testQADocSet.docs.get(i).label;
			qrelLines.add(line);
		}
		FileUtil.writeLines(testDataQrelFile, qrelLines);
	}
	
	private double computeAdaptiveLR(double eta1, int i, int iterations) {
		// TODO Auto-generated method stub
		return eta1 * (1 - (double) i / (double) (iterations + 1));
	}

	//Update U' and sumExp array for all qids
	private void updateQidToUPrimeArrayMap(QADocuments trainQADocSet,
			QADocuments testQADocSet, Map<String, Double[]> termToWordVectorMap, 
			Map<String, Double[]> qidToUPrimeArrayMap, Map<String, Double> qidToSumExpMap, 
			Map<String, Double[]> qidToQueryMeanVecMap,  String modelName, Map<String, String []> qidToqTermsMap) {
		// TODO Auto-generated method stub
		qidToUPrimeArrayMap.clear(); // Clear old values 
		qidToSumExpMap.clear();
		for(String qid : qidToQueryMeanVecMap.keySet()){
			String [] queryTerms = qidToqTermsMap.get(qid);
			Double [] uPrimeArray = new Double [queryTerms.length];
			for(int j = 0; j < queryTerms.length; j++){
				Double[] queryTermWordVec = termToWordVectorMap.get(queryTerms[j]);
				double uPrime = 0;
				//If there no word embedding for this word Set u' = 0
				if(queryTermWordVec != null){
					if(modelName.equals("V2") || modelName.equals("V3-3") || modelName.equals("V4-2")) { // V2(q_j - q_bar)*v
						for(int p = 0; p < queryTermWordVec.length; p++){
							uPrime += (queryTermWordVec[p] - qidToQueryMeanVecMap.get(qid)[p]) * ModelParams.vp[p];
						}
					} else if(modelName.equals("V1") || modelName.equals("V3-2") || modelName.equals("V4-1") || modelName.equals("V4-4")){// V1 q_j*v
						for(int p = 0; p < queryTermWordVec.length; p++){
							uPrime += queryTermWordVec[p] * ModelParams.vp[p];
						}
					} else if (modelName.equals("V4-3")) {//V4-3 q_j*v but we normalized the query term vector
						MatrixUtil.norm2(queryTermWordVec);
						for(int p = 0; p < queryTermWordVec.length; p++){
							uPrime += queryTermWordVec[p] * ModelParams.vp[p];
						}
					} else {
						System.err.println("In updateQidToUPrimeArrayMap, unsupported modelName type: " + modelName);
						System.exit(1);
					}
				}
				uPrimeArray[j] = uPrime;
			}
			double sumExp = 0;
			for(double up : uPrimeArray){
				sumExp += Math.exp(up);
			}
			qidToUPrimeArrayMap.put(qid, uPrimeArray);
			qidToSumExpMap.put(qid, sumExp);
		}
	}

	//print out learnt query term importance for training/testing data by gate function
	private void printGateLearntQueryTermImportance(QADocuments trainQADocSet, QADocuments testQADocSet,
			Map<String, Double[]> qidToUPrimeArrayMap,  Map<String, String[]> qidToqTermsMap, int i, String modelResDataFolder, String modelName) {
		// TODO Auto-generated method stub
		String queryTermImportFile = modelResDataFolder + "queryTermImportance_iter_" + i + ".txt";
		ArrayList<String> lines = new ArrayList<String>();
		for(String qid : qidToUPrimeArrayMap.keySet()){
			String line = qid + "\t";
			for(String queryTerm : qidToqTermsMap.get(qid)){
				line += queryTerm + " ";
			}
			line += "\t";
			for(Double uprime : qidToUPrimeArrayMap.get(qid)){
				line += uprime + " ";
			}
			line += "\t";
			if(modelName.equals("V4-1") || modelName.equals("V4-2") || modelName.equals("V4-3")){
				double sumExp = 0;
				for(Double uprime : qidToUPrimeArrayMap.get(qid)){
					sumExp += Math.exp(uprime);
				}
				for(Double uprime : qidToUPrimeArrayMap.get(qid)){
					line += Math.exp(uprime)/sumExp + " ";
				}
			} else {
				for(Double uprime : qidToUPrimeArrayMap.get(qid)){
					line += sigmoid(uprime) + " ";
				}
			}
			lines.add(line);
		}
		FileUtil.writeLines(queryTermImportFile, lines);
	}

	//Setting: with gate function
	private double computeHingeloss(Set<String> qaTriples,
			Map<String, ArrayList<ArrayList<Double>>> qaMatchMatrixMap, Map<String, Double[]> qidToUPrimeArrayMap, Map<String, Double> qidToSumExpMap,  String modelName,
			Map<String, Double> termToIDFMap, Map<String, String[]> qidToqTermsMap) {
		// TODO Auto-generated method stub
		double curLoss = 0;
		for(String qaTriple : qaTriples){
			String [] qaTripleTokens = qaTriple.split("\t");
			String qid = qaTripleTokens[0], posAid = qaTripleTokens[1], negAid = qaTripleTokens[2];
			Double [] uPrimeArray = qidToUPrimeArrayMap.get(qid);
			double sumExp = qidToSumExpMap.get(qid);
			//Use a hashMap to speed up look up QAMatchMatrix
			ArrayList<ArrayList<Double>> QAMatchMatrixPos = getQAMatchMatrixByQidAid(qid, posAid, qaMatchMatrixMap);
			ArrayList<ArrayList<Double>> QAMatchMatrixNeg = getQAMatchMatrixByQidAid(qid, negAid, qaMatchMatrixMap);
			double deltaY = 1.0 - computeForwardPredictScore(QAMatchMatrixPos, uPrimeArray, sumExp, modelName, termToIDFMap, qidToqTermsMap.get(qid)) 
					+ computeForwardPredictScore(QAMatchMatrixNeg, uPrimeArray, sumExp, modelName, termToIDFMap, qidToqTermsMap.get(qid));
			curLoss += Math.max(deltaY, 0);
			//System.out.println("In computeHingeloss, the value of curLoss: " + curLoss);
		}
		return curLoss;
	}
	
	//Compute the gradient to update w_k
	//Added gate into the model
	public double computeGradientWk(
			ArrayList<ArrayList<Double>> qAMatchMatrixPos,
			ArrayList<ArrayList<Double>> qAMatchMatrixNeg, int k, Double[] uPrimeArray, double sumExp, String modelName, Map<String, Double> termToIDFMap, String[] qTerms) {
		// TODO Auto-generated method stub
		double grad = 0;
		for(int j = 0; j < qAMatchMatrixPos.size(); j++){
			double uPos = computeU(qAMatchMatrixPos.get(j));
			double uNeg = computeU(qAMatchMatrixNeg.get(j));
			if(modelName.equals("V4-1") || modelName.equals("V4-2") || modelName.equals("V4-3")){ // softmax gate
				grad += (Math.exp(uPrimeArray[j]) / sumExp) * (-sigmoid(uPos)*(1-sigmoid(uPos))* qAMatchMatrixPos.get(j).get(k) + sigmoid(uNeg)*(1-sigmoid(uNeg))* qAMatchMatrixNeg.get(j).get(k));
			} else if(modelName.equals("V4-4")) {//idf as gate
				//Test print out 
				//System.out.println("qAMatchMatrixPos.get(j) len: " + qAMatchMatrixPos.get(j).size() + "\t k" + k);
				grad += termToIDFMap.get(qTerms[j]) * (-sigmoid(uPos)*(1-sigmoid(uPos))* qAMatchMatrixPos.get(j).get(k) + sigmoid(uNeg)*(1-sigmoid(uNeg))* qAMatchMatrixNeg.get(j).get(k));
			} else {// sigmoid gate
				grad += sigmoid(uPrimeArray[j]) * (-sigmoid(uPos)*(1-sigmoid(uPos))* qAMatchMatrixPos.get(j).get(k) + sigmoid(uNeg)*(1-sigmoid(uNeg))* qAMatchMatrixNeg.get(j).get(k));
			}
		}
		//System.out.println("Test: grad = " + grad);
		return grad;
	}

	//Compute the gradient to update v_p
	//Added gate into the model
	public double computeGradientVp(
			ArrayList<ArrayList<Double>> qAMatchMatrixPos,
			ArrayList<ArrayList<Double>> qAMatchMatrixNeg, int p,
			Double[] uPrimeArray, double sumExp, Map<String, Double[]> termToWordVectorMap, String [] qWords, Double[] queryMeanVec, String modelName) {
		// TODO Auto-generated method stub
		double sumExpL = 0;
		for(int l = 0; l < qAMatchMatrixPos.size(); l++){
			sumExpL += Math.exp(uPrimeArray[l]) * termToWordVectorMap.get(qWords[l])[p];
		}
		double grad = 0;
		for(int j = 0; j < qAMatchMatrixPos.size(); j++){
			double uPos = computeU(qAMatchMatrixPos.get(j));
			double uNeg = computeU(qAMatchMatrixNeg.get(j));
			if(!termToWordVectorMap.containsKey(qWords[j])){
				continue; // For query word without word embedding, don't consider them when computing gradients of Vp
			}
			double qbarp = queryMeanVec[p];
			double qjp = termToWordVectorMap.get(qWords[j])[p];
			//System.out.println("sigmoid(uPrimeArray[j]) * (1 - sigmoid(uPrimeArray[j])): " + sigmoid(uPrimeArray[j]) * (1 - sigmoid(uPrimeArray[j])));
			//System.out.println("qjp: " + qjp);
			//System.out.println("(-sigmoid(uPos) + sigmoid(uNeg): " + ((-sigmoid(uPos) + sigmoid(uNeg))));
			double uPrimej = uPrimeArray[j];
			if(modelName.equals("V2") || modelName.equals("V3-3")){//V2 minus query bar
				grad += sigmoid(uPrimej) * (1 - sigmoid(uPrimej)) * (qjp - qbarp) * (-sigmoid(uPos) + sigmoid(uNeg));
			} else if(modelName.equals("V1") || modelName.equals("V3-2")){//V1 v*q_j
				grad += sigmoid(uPrimej) * (1 - sigmoid(uPrimej)) * qjp * (-sigmoid(uPos) + sigmoid(uNeg));
			} else if(modelName.equals("V4-1") || modelName.equals("V4-2") || modelName.equals("V4-3")) { //softmatx gate
				double bigX = (Math.exp(uPrimej) * qjp * sumExp - Math.exp(uPrimej) * sumExpL) / Math.pow(sumExp, 2);
				grad += bigX* (-sigmoid(uPos) + sigmoid(uNeg));
			}
			  else{
				System.err.println("In computeGradientVp, unsupported modelName type: " + modelName);
			}
		}
		return grad;
	}

	//Compute U = \sum_k w_k * x_jk
	private double computeU(ArrayList<Double> xj) {
		// TODO Auto-generated method stub
		double u = 0;
		for(int k = 0; k < xj.size(); k++){
			u += ModelParams.wk[k] * xj.get(k);
		}
		return u;
	}

	//Compute the forward predicted matching score given the QAMatch matrix and the given weight w_k, v_p
	//Setting: with gate function
	public double computeForwardPredictScore(
			ArrayList<ArrayList<Double>> qAMatchMatrix, Double[] uPrimeArray, double sumExp, 
			String modelName, Map<String, Double> termToIDFMap, String[] qTermsArray) {
		// TODO Auto-generated method stub
		double [] wk = ModelParams.wk;
		double predictedScore = 0;
		for(int j = 0; j < qAMatchMatrix.size(); j++){
			double curQueryScore = 0;
			for(int k = 0; k < qAMatchMatrix.get(j).size(); k++){
				curQueryScore += wk[k] * qAMatchMatrix.get(j).get(k);
			}
			if(modelName.equals("V4-1") || modelName.equals("V4-2") || modelName.equals("V4-3")){//Use softmax function as the gate function
				predictedScore += (Math.exp(uPrimeArray[j]) / sumExp) * sigmoid(curQueryScore);
			} else if(modelName.equals("V4-4")) { //Use idf as the query term importance weight
				if(termToIDFMap.containsKey(qTermsArray[j])){
					predictedScore += termToIDFMap.get(qTermsArray[j]) * sigmoid(curQueryScore);
				} else {
					//System.err.println("can't find rare query term in idfMap: qTerm " + qTermsArray[j] + "use default idf value!");
					predictedScore += ModelParams.defaultIDFValue * sigmoid(curQueryScore);
				}
			} else {//Use sigmoid function as the default gate function
				predictedScore += sigmoid(uPrimeArray[j]) * sigmoid(curQueryScore);
			}
		}
		return predictedScore;
	}
	
	//Return the index of the corresponding Wk dimension given the qaMatchScore
	//if qaMatchScore == 1 wkIndex =20;
	//otherwise wkIndex = 0-19
	private  int getWKIndexByQAMatchScore(Double qaMatchScore, int BinNum) {
		// TODO Auto-generated method stub
		int wkIndex;
		int BinNumWithoutExactMatch = BinNum - 1;
		if(qaMatchScore == 1.0){
			wkIndex = BinNumWithoutExactMatch;
		} else {
			wkIndex = new BigDecimal((qaMatchScore + 1.0) * 0.5 * BinNumWithoutExactMatch).setScale(0, BigDecimal.ROUND_DOWN).intValue();
		}
		return wkIndex;
	}

	private double sigmoid(double curQueryScore) {
		// TODO Auto-generated method stub
		return (1/( 1 + Math.pow(Math.E,(-1*curQueryScore))));
	}
	
	private void initQAMatchMatrixMap(QADocuments qaDocSet,
			Map<String, ArrayList<ArrayList<Double>>> qaMatchMatrixMap, String modelName, int binNum) {
		// TODO Auto-generated method stub
		//Key qid \t aid
		//Value qaMatchMatrix
		if(modelName.equals("V0") || modelName.equals("V1") || modelName.equals("V2")){
			for(Document doc : qaDocSet.docs){
				ArrayList<ArrayList<Double>> qaMatchMatrixCopy = new ArrayList<ArrayList<Double>>(doc.QAMatchMatrix);
				qaMatchMatrixMap.put(doc.qid + "\t" + doc.answerSentId, qaMatchMatrixCopy);
			}
		} else {//default: add bin weight
			for(Document doc : qaDocSet.docs){
				ArrayList<ArrayList<Double>> qaMatchMatrixCopyBin = new ArrayList<ArrayList<Double>>(); //M * Bin
				for(int i = 0; i < doc.QAMatchMatrix.size(); i++){
					ArrayList<Double> qaMatchMatrixCopyBinRow = new ArrayList<Double>();
					for(int bin = 0; bin < binNum; bin++){
						qaMatchMatrixCopyBinRow.add(0.0);
					}
					for(int j = 0; j < doc.QAMatchMatrix.get(i).size(); j++){
						double mScore = doc.QAMatchMatrix.get(i).get(j);
						int binIndex = getWKIndexByQAMatchScore(mScore, binNum);
						qaMatchMatrixCopyBinRow.set(binIndex, qaMatchMatrixCopyBinRow.get(binIndex) + mScore);
					}
					ArrayList<Double> aListCopy = new ArrayList<Double>(qaMatchMatrixCopyBinRow);
					qaMatchMatrixCopyBin.add(aListCopy);
				}
				qaMatchMatrixMap.put(doc.qid + "\t" + doc.answerSentId, qaMatchMatrixCopyBin);
			}
		}
	}

	//get QAMatchMatrix by Qid and Aid
	public ArrayList<ArrayList<Double>> getQAMatchMatrixByQidAid(String qid,
			String posAid, Map<String, ArrayList<ArrayList<Double>>> qaMatchMatrixMap) {
		// TODO Auto-generated method stub
		return qaMatchMatrixMap.get(qid + "\t" + posAid);
	}

	private void saveIteratedModel(int i, QADocuments qaDocSet, String modelResDataFolder) {
		// TODO Auto-generated method stub
		//Save weight w_k
		String paramWkFile = modelResDataFolder + "wk_iter_" + i + ".wk";
		ArrayList<String> lines = new ArrayList<String>();
		String line = "";
		double [] wk = ModelParams.wk;
		for(int j = 0; j < wk.length; j++){
			line += wk[j] + "\t";
		}
		lines.add(line);
		FileUtil.writeLines(paramWkFile, lines);
		
		//Save weight v_p
		String paramVPFile = modelResDataFolder + "vp_iter_" + i + ".vp";
		lines.clear();
		line = "";
		double [] vp = ModelParams.vp;
		for(int j = 0; j < vp.length; j++){
			line += vp[j] + "\t";
		}
		lines.add(line);
		FileUtil.writeLines(paramVPFile, lines);
	}
}
