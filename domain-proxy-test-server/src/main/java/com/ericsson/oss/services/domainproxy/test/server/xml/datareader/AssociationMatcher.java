package com.ericsson.oss.services.domainproxy.test.server.xml.datareader;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AssociationMatcher {

    public static final ValueAssociation SRC_VALUE_IS_TRG_PARENT = ((source, target) -> target.isParent(source.getValue()));

    public static final ValueAssociation SRC_VALUE_CONTAINS_TRG_PARENT = ((source, target) -> source.getValue().startsWith(target.getParentFdn()));

    public static final ValueAssociation SAME_PARENT = ((source, target) -> source.isParent(target.getParentFdn()));

    public static final ValueAssociation TRG_PARENT_CONTAINS_SRC_PARENT = ((source, target) -> target.getParentFdn().contains(source.getParentFdn()));

}
