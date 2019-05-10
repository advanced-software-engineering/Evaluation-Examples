package ch.uzh.ifi.seal.ase19.utils;

import cc.kave.commons.model.naming.codeelements.IMemberName;
import ch.uzh.ifi.seal.ase19.recommender.SimilarityDto;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Set;

public class EvaluationResult {
    private String selectedMethod;
    private Set<Pair<IMemberName, SimilarityDto>> resultSet;

    public EvaluationResult(String selectedMethod, Set<Pair<IMemberName, SimilarityDto>> recommendations) {
        this.selectedMethod = selectedMethod;
        this.resultSet = recommendations;
    }

    public String getSelectedMethod() {
        return selectedMethod;
    }

    public Set<Pair<IMemberName, SimilarityDto>> getResultSet() {
        return resultSet;
    }
}
