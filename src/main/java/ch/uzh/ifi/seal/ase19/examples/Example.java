package ch.uzh.ifi.seal.ase19.examples;

import cc.kave.commons.model.naming.codeelements.IMemberName;
import ch.uzh.ifi.seal.ase19.core.IPersistenceManager;
import ch.uzh.ifi.seal.ase19.core.InMemoryPersistenceManager;
import ch.uzh.ifi.seal.ase19.core.models.*;
import ch.uzh.ifi.seal.ase19.miner.ContextProcessor;
import ch.uzh.ifi.seal.ase19.recommender.MethodCallRecommender;
import com.google.common.io.Files;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Set;

public class Example {

    private static Logger logger = LogManager.getLogger(Example.class);

    public static void main(String[] args) {
        String modelDirectory = Files.createTempDir().getAbsolutePath();

        IPersistenceManager persistence = new InMemoryPersistenceManager(modelDirectory);
        ContextProcessor processor = new ContextProcessor(persistence);

        MethodCallRecommender recommender = new MethodCallRecommender(processor, persistence);

        doRecommendation(recommender, getSecondModel().getQuery());
        learnModels(recommender, getFirstModel());
        doRecommendation(recommender, getSecondModel().getQuery());
        learnModels(recommender, getSecondModel());
        doRecommendation(recommender, getSecondModel().getQuery());
    }

    private static void doRecommendation(MethodCallRecommender recommender, Query query) {
        Set<Pair<IMemberName, Double>> result = recommender.query(query);
        System.out.printf("%-30s%-30s\n", "Method name", "Similarity measure");
        System.out.printf("%-30s%-30s\n", "------------", "--------------------");
        result.forEach(p -> {
            System.out.printf("%-30s%-30s\n", p.getLeft().getName(), p.getRight());
        });
        System.out.printf("\n");
    }

    private static void learnModels(MethodCallRecommender recommender, QuerySelection querySelection) {
        recommender.persist(querySelection);
    }

    private static QuerySelection getFirstModel() {
        ResultType resultType = ResultType.METHOD_INVOCATION;
        String receiverType = "System.IO.StreamReader";
        SurroundingExpression surroundingExpression = SurroundingExpression.ASSIGNMENT;
        ObjectOrigin objectOrigin = ObjectOrigin.CLASS;
        String requiredType = "System.String";
        EnclosingMethodSignature enclosingMethodSignature = new EnclosingMethodSignature("SampleApp.TimeSeries", "loadDataButton_Click", "System.Void", new ArrayList<>(), null);
        Query query = new Query(resultType, receiverType, surroundingExpression, objectOrigin, requiredType, enclosingMethodSignature);
        IMemberName selection = new MyMemberName("0M:[p:string] [System.IO.StreamReader, mscorlib, 4.0.0.0].ReadLine()");
        return new QuerySelection(query, selection, 1);
    }

    private static QuerySelection getSecondModel() {
        ResultType resultType = ResultType.METHOD_INVOCATION;
        String receiverType = "System.IO.StreamReader";
        SurroundingExpression surroundingExpression = SurroundingExpression.RETURN_STATEMENT;
        ObjectOrigin objectOrigin = ObjectOrigin.CLASS;
        String requiredType = "System.String";
        EnclosingMethodSignature enclosingMethodSignature = new EnclosingMethodSignature("SampleApp.DeltaRuleForm", "loadButton_Click", "System.Void", new ArrayList<>(), null);
        Query query = new Query(resultType, receiverType, surroundingExpression, objectOrigin, requiredType, enclosingMethodSignature);
        IMemberName selection = new MyMemberName("0M:[p:void] [System.IO.StreamReader, mscorlib, 4.0.0.0].Close()");
        return new QuerySelection(query, selection, 1);
    }
}