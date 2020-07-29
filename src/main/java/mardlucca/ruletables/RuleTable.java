/*
 * File: RuleTable.java
 *
 * Copyright 2020 Marcio D. Lucca
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mardlucca.ruletables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class RuleTable<C, R> {
    private String name;
    private Map<String, R> policies = new HashMap<>();
    private Map<String, Chain<C, R>> chains = new HashMap<>();

    RuleTable(String aInName) {
        name = aInName;
    }

    void addPolicy(String aInChain, R aInResult) {
        policies.put(aInChain, aInResult);
    }

    void addChain(String aInName, Chain<C, R> aInChain) {
        chains.put(aInName, aInChain);
    }

    Chain<C, R> getChain(String aInName) {
        return chains.get(aInName);
    }

    public R execute(String aInChain, C aInContext)
            throws RuleExecutionException {
        aInChain = aInChain.trim().toUpperCase();

        try {
            if (!policies.containsKey(aInChain)) {
                throw new RuleExecutionException(String.format(
                        "Cannot invoke user chain '%s' in table '%s'",
                        aInChain, name));
            }

            R lResult = chains.get(aInChain).execute(aInContext);
            return lResult == null ? policies.get(aInChain) : lResult;
        } catch (RuntimeException | StackOverflowError e) {
            throw new RuleExecutionException(String.format(
                    "Error executing rule table '%s', chain '%s'",
                    name, aInChain), e);
        }
    }

    static class Chain<C, R> {
        private List<Rule<C, R>> rules = new ArrayList<>();

        void addRule(Rule<C, R> aInRule) {
            rules.add(aInRule);
        }

        R execute(C aInContext) {
            for (Rule<C, R> lRule : rules) {
                if (lRule.predicate.test(aInContext)) {
                    R lResult = lRule.action.apply(aInContext);

                    if (lResult != null) { return lResult; }

                    if (lRule.stopsChain) {
                        // This is a "GoTo" type of rule
                        return null;
                    }
                }
            }

            return null;
        }
    }

    static class Rule<C, R> {
        private Predicate<? super C> predicate;
        private Function<? super C, R> action;
        private boolean stopsChain;

        Rule(Predicate<? super C> aInPredicate,
                Function<? super C, R> aInAction,
                boolean aInStopsChain) {
            predicate = aInPredicate;
            action = aInAction;
            stopsChain = aInStopsChain;
        }
    }
}
