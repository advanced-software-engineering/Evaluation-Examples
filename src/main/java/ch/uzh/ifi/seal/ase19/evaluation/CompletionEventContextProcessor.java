package ch.uzh.ifi.seal.ase19.evaluation;

import cc.kave.commons.model.ssts.ISST;
import ch.uzh.ifi.seal.ase19.core.IPersistenceManager;
import ch.uzh.ifi.seal.ase19.core.MethodInvocationContext;
import ch.uzh.ifi.seal.ase19.miner.ContextProcessor;
import ch.uzh.ifi.seal.ase19.miner.MethodInvocationContextVisitor;

import java.util.ArrayList;
import java.util.List;

public class CompletionEventContextProcessor extends ContextProcessor {

    CompletionEventContextProcessor(IPersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    protected List<MethodInvocationContext> getMethodContext(ISST sst) {
        try {
            MethodInvocationContextVisitor visitor = new MethodInvocationContextVisitor();
            sst.accept(visitor, new MethodInvocationContext());
            return visitor.getCompletionEvent();
        } catch (Exception e) {
        }

        return new ArrayList<>();
    }
}