package ch.uzh.ifi.seal.ase19.examples;

import cc.kave.commons.model.naming.impl.v0.codeelements.MemberName;

class MyMemberName extends MemberName {
    MyMemberName(String identifier) {
        super(identifier);
    }

    @Override
    public boolean isUnknown() {
        return false;
    }
}
