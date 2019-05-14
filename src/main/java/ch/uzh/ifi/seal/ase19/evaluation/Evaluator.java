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
import ch.uzh.ifi.seal.ase19.recommender.ExampleRecommender;
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

    private static Logger logger = LogManager.getLogger(ExampleRecommender.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("Not enough arguments provided! Syntax: modelDirectory eventDirectory\n");
            System.exit(1);
        }

        String modelDirectory = args[0];
        String eventDirectory = args[1];

        logger.info("Model directory is: " + modelDirectory + eventDirectory + "\n");

        IPersistenceManager persistence = new InMemoryPersistenceManager(modelDirectory);
        ContextProcessor processor = new ContextProcessor(persistence);
        ContextProcessor completionProcessor = new CompletionEventContextProcessor(persistence);
        MethodCallRecommender recommender = new MethodCallRecommender(processor, persistence);

        int counter = 0;

        Set<String> zips = IoHelper.findAllZips(eventDirectory);
        List<EvaluationResult> evaluationResultList = new ArrayList<>();
        for (String zip : zips) {
            File zipFile = Paths.get(eventDirectory, zip).toFile();

            try (IReadingArchive ra = new ReadingArchive(zipFile)) {
                while (ra.hasNext()) {

                    // We are only interested in CompletionEvents
                    IIDEEvent e = ra.getNext(IIDEEvent.class);
                    if (!(e instanceof CompletionEvent)) {
                        continue;
                    }

                    CompletionEvent ce = (CompletionEvent) e;
                    Context c = ((CompletionEvent) e).context;

                    // We are only interested if the recommendation got applied
                    if (!ce.getTerminatedState().name().equals("Applied")) {
                        continue;
                    }

                    //
                    IProposal selection = ce.getLastSelectedProposal();
                    if (!(selection.getName() instanceof MethodName)) {
                        continue;
                    }
                    String selectedMethod = ((MethodName) selection.getName()).getName();

                    List<QuerySelection> completionEventQuerySelection = completionProcessor.run(c);

                    Set<Pair<IMemberName, SimilarityDto>> result = null;
                    if (completionEventQuerySelection.size() > 0) {
                        Query query = completionEventQuerySelection.get(completionEventQuerySelection.size() - 1).getQuery();
                        String requiredType = SSTUtils.getFullyQualifiedNameWithoutGenerics(((MethodName) selection.getName()).getReturnType().getFullName());
                        query.setRequiredType(requiredType);
                        String receiverType = SSTUtils.getFullyQualifiedNameWithoutGenerics(((MethodName) selection.getName()).getDeclaringType().getFullName());
                        query.setReceiverType(receiverType);
                        result = recommender.queryWithDetails(query);
                    }else{
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

            logger.info(counter + " CompletionEvent processed");
        }
        writeToCSV(evaluationResultList);
    }

    private static void writeToCSV(List<EvaluationResult> evaluationResultList) {
        try {
            FileWriter fileWriter = new FileWriter("ASE_Evaluation.csv");

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
                    fileWriter.append(selectedMethod);
                } else {
                    fileWriter.append("True");
                    fileWriter.append(",");
                    fileWriter.append(selectedMethod);
                    Iterator<Pair<IMemberName, SimilarityDto>> iterator = resultSet.iterator();
                    int count = 0;
                    while (iterator.hasNext() && count < 10) {
                        Pair<IMemberName, SimilarityDto> result = iterator.next();
                        String fullName = result.getKey().getFullName();
                        SimilarityDto similarityDto = result.getValue();

                        fileWriter.append(",");
                        fileWriter.append(fullName);
                        fileWriter.append(",");
                        fileWriter.append(similarityDto.similarity.toString());
                        fileWriter.append(",");
                        fileWriter.append(new Double(similarityDto.similarityReceiverType).toString());
                        fileWriter.append(",");
                        fileWriter.append(new Double(similarityDto.similarityRequiredType).toString());
                        fileWriter.append(",");
                        fileWriter.append(new Double(similarityDto.similarityObjectOrigin).toString());
                        fileWriter.append(",");
                        fileWriter.append(new Double(similarityDto.similaritySurroundingExpression).toString());
                        fileWriter.append(",");
                        fileWriter.append(new Double(similarityDto.similarityEnclosingMethodReturnType).toString());
                        fileWriter.append(",");
                        fileWriter.append(new Double(similarityDto.similarityEnclosingMethodParameterSize).toString());
                        fileWriter.append(",");
                        fileWriter.append(new Double(similarityDto.similarityEnclosingMethodParameters).toString());
                        fileWriter.append(",");
                        fileWriter.append(new Double(similarityDto.similarityEnclosingMethodSuper).toString());

                        count++;
                    }
                }
                fileWriter.append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
