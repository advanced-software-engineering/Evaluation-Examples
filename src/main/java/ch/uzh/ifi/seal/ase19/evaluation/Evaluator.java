package ch.uzh.ifi.seal.ase19.evaluation;

import cc.kave.commons.model.events.IIDEEvent;
import cc.kave.commons.model.events.completionevents.CompletionEvent;
import cc.kave.commons.model.events.completionevents.Context;
import cc.kave.commons.model.events.completionevents.IProposal;
import cc.kave.commons.model.naming.codeelements.IMemberName;
import cc.kave.commons.model.naming.impl.v0.codeelements.MethodName;
import cc.kave.commons.utils.io.IReadingArchive;
import cc.kave.commons.utils.io.ReadingArchive;
import ch.uzh.ifi.seal.ase19.core.IPersistenceManager;
import ch.uzh.ifi.seal.ase19.core.InMemoryPersistenceManager;
import ch.uzh.ifi.seal.ase19.core.models.Query;
import ch.uzh.ifi.seal.ase19.core.models.QuerySelection;
import ch.uzh.ifi.seal.ase19.core.utils.IoHelper;
import ch.uzh.ifi.seal.ase19.core.utils.SSTUtils;
import ch.uzh.ifi.seal.ase19.miner.ContextProcessor;
import ch.uzh.ifi.seal.ase19.recommender.MethodCallRecommender;
import ch.uzh.ifi.seal.ase19.recommender.SimilarityDto;
import ch.uzh.ifi.seal.ase19.utils.EvaluationResult;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class Evaluator {

    private static Logger logger = LogManager.getLogger(Evaluator.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("Not enough arguments provided! Syntax: modelDirectory eventDirectory\n");
            System.exit(1);
        }

        String modelDirectory = args[0];
        String eventDirectory = args[1];

        logger.info("Model directory is: " + modelDirectory + eventDirectory + "\n");

        Set<String> zips = IoHelper.findAllZips(eventDirectory);
        IPersistenceManager persistence = new InMemoryPersistenceManager(modelDirectory);
        ContextProcessor processor = new ContextProcessor(persistence);
        ContextProcessor completionProcessor = new CompletionEventContextProcessor(persistence);


        HashMap<String, Double> weights = new HashMap<>();
        weights.put("receiverType", 1.0);
        weights.put("requiredType", 1.0);
        weights.put("objectOrigin", 1.0);
        weights.put("surroundingExpression", 1.0);
        weights.put("enclosingMethodReturnType", 1.0);
        weights.put("enclosingMethodParameterSize", 1.0);
        weights.put("enclosingMethodParameters", 1.0);
        weights.put("enclosingMethodSuper", 1.0);

        // Evaluate baseline weights
        MethodCallRecommender recommender = new MethodCallRecommender(processor, persistence, weights);
        evaluateRecommender("baseline", eventDirectory, zips, processor, completionProcessor, recommender);

        // Evaluate with different weights
        for (String weightName : weights.keySet()) {
            for (double i = 0; i <= 2; i += 0.25) {

                // We only want to generate the evaluation for weights 1,1,1,1,1,1,1,1 once.
                if (i == 1) {
                    continue;
                }

                weights.replace(weightName, i);
                String name = weightName + "_" + i;
                recommender = new MethodCallRecommender(processor, persistence, weights);
                evaluateRecommender(name, eventDirectory, zips, processor, completionProcessor, recommender);
            }

            // Reset the weight
            weights.replace(weightName, 1.0);
        }
    }

    private static void evaluateRecommender(String name, String eventDirectory, Set<String> zips, ContextProcessor processor, ContextProcessor completionProcessor, MethodCallRecommender recommender) {
        List<EvaluationResult> evaluationResultList = new ArrayList<>();
        int counter = 0;
        for (String zip : zips) {
            File zipFile = Paths.get(eventDirectory, zip).toFile();
            try (IReadingArchive ra = new ReadingArchive(zipFile)) {
                while (ra.hasNext()) {

                    // We are only interested in CompletionEvents
                    IIDEEvent e = ra.getNext(IIDEEvent.class);
                    if (!(e instanceof CompletionEvent)) {
                        continue;
                    }

                    // We are only interested if the recommendation got applied
                    CompletionEvent ce = (CompletionEvent) e;
                    if (!ce.getTerminatedState().name().equals("Applied")) {
                        continue;
                    }

                    // We are only interested in method completions
                    IProposal selection = ce.getLastSelectedProposal();
                    if (!(selection.getName() instanceof MethodName)) {
                        continue;
                    }
                    String selectedMethod = ((MethodName) selection.getName()).getName();

                    Context c = ((CompletionEvent) e).context;
                    List<QuerySelection> completionEventQuerySelection = completionProcessor.run(c);

                    Set<Pair<IMemberName, SimilarityDto>> result = null;
                    if (completionEventQuerySelection.size() > 0) {
                        Query query = completionEventQuerySelection.get(completionEventQuerySelection.size() - 1).getQuery();
                        String requiredType = SSTUtils.getFullyQualifiedNameWithoutGenerics(((MethodName) selection.getName()).getReturnType().getFullName());
                        query.setRequiredType(requiredType);
                        String receiverType = SSTUtils.getFullyQualifiedNameWithoutGenerics(((MethodName) selection.getName()).getDeclaringType().getFullName());
                        query.setReceiverType(receiverType);
                        result = recommender.queryWithDetails(query);
                    } else {
                        List<QuerySelection> querySelections = processor.run(c);
                        List<Integer> indexes = new ArrayList<>();
                        for (int i = 0; i < querySelections.size(); i++) {
                            if (querySelections.get(i) != null) {
                                String test = querySelections.get(i).getSelection().getName();
                                if (test.equals(((MethodName) selection.getName()).getName())) {
                                    indexes.add(i);
                                }
                            }
                        }
                        if (indexes.size() > 0) {
                            result = recommender.queryWithDetails(querySelections.get(indexes.get(indexes.size() - 1)).getQuery());
                        }
                    }
                    counter++;
                    evaluationResultList.add(new EvaluationResult(selectedMethod, result));
                }
            }
            logger.info(name + ": " + counter + " CompletionEvent processed");
        }
        writeToCSV(name, evaluationResultList);
    }

    private static void writeToCSV(String name, List<EvaluationResult> evaluationResultList) {
        try {
            int linesCount = 1;
            FileWriter fileWriter = new FileWriter("evaluations/ASE_Evaluation_" + name + ".csv");

            fileWriter.append("evaluated");
            fileWriter.append(",");
            fileWriter.append("selectedMethod");
            for (int i = 1; i <= 10; i++) {
                fileWriter.append(",");
                fileWriter.append("recommendedMethod_" + i);
                fileWriter.append(",");
                fileWriter.append("similarity_" + i);
                fileWriter.append(",");
                fileWriter.append("similarityReceiverType_" + i);
                fileWriter.append(",");
                fileWriter.append("similarityRequiredType_" + i);
                fileWriter.append(",");
                fileWriter.append("similarityObjectOrigin_" + i);
                fileWriter.append(",");
                fileWriter.append("similaritySurroundingExpression_" + i);
                fileWriter.append(",");
                fileWriter.append("similarityEnclosingMethodReturnType_" + i);
                fileWriter.append(",");
                fileWriter.append("similarityEnclosingMethodParameterSize_" + i);
                fileWriter.append(",");
                fileWriter.append("similarityEnclosingMethodParameters_" + i);
                fileWriter.append(",");
                fileWriter.append("similarityEnclosingMethodSuper_" + i);
            }
            fileWriter.append("\n");

            for (EvaluationResult evaluationResult : evaluationResultList) {
                String selectedMethod = evaluationResult.getSelectedMethod();
                Set<Pair<IMemberName, SimilarityDto>> resultSet = evaluationResult.getResultSet();
                if (resultSet == null) {
                    fileWriter.append("False");
                    fileWriter.append(",");
                    fileWriter.append('"' + selectedMethod + '"');
                } else {
                    fileWriter.append("True");
                    fileWriter.append(",");
                    fileWriter.append('"' + selectedMethod + '"');
                    Iterator<Pair<IMemberName, SimilarityDto>> iterator = resultSet.iterator();
                    int count = 0;
                    while (iterator.hasNext() && count < 10) {
                        Pair<IMemberName, SimilarityDto> result = iterator.next();
                        String methodName = result.getKey().getName();
                        SimilarityDto similarityDto = result.getValue();

                        fileWriter.append(",");
                        fileWriter.append('"' + methodName + '"');
                        fileWriter.append(",");
                        fileWriter.append(similarityDto.similarity.toString());
                        fileWriter.append(",");
                        fileWriter.append(Double.toString(similarityDto.similarityReceiverType));
                        fileWriter.append(",");
                        fileWriter.append(Double.toString(similarityDto.similarityRequiredType));
                        fileWriter.append(",");
                        fileWriter.append(Double.toString(similarityDto.similarityObjectOrigin));
                        fileWriter.append(",");
                        fileWriter.append(Double.toString(similarityDto.similaritySurroundingExpression));
                        fileWriter.append(",");
                        fileWriter.append(Double.toString(similarityDto.similarityEnclosingMethodReturnType));
                        fileWriter.append(",");
                        fileWriter.append(Double.toString(similarityDto.similarityEnclosingMethodParameterSize));
                        fileWriter.append(",");
                        fileWriter.append(Double.toString(similarityDto.similarityEnclosingMethodParameters));
                        fileWriter.append(",");
                        fileWriter.append(Double.toString(similarityDto.similarityEnclosingMethodSuper));

                        count++;
                    }
                }
                fileWriter.append("\n");
                linesCount += 1;
            }
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
