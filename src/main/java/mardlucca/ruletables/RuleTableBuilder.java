/*
 * File: RuleTableBuilder.java
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

import mardlucca.ruletables.RuleTable.Chain;
import mardlucca.ruletables.RuleTable.Rule;
import mardlucca.ruletables.RuleTableBuilder.ChainBuilder.RuleBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RuleTableBuilder<C, R> {
    private String name;
    private Set<String> builtInChains;
    private Map<String, ChainBuilder> chainBuilders = new HashMap<>();
    
    public RuleTableBuilder(String aInName, Set<String> aInBuiltInChains) {
        name = aInName.trim();
        if (aInBuiltInChains != null) {
            builtInChains = aInBuiltInChains.stream()
                    .filter(Objects::nonNull)
                    .map(aInChain -> aInChain.trim().toUpperCase())
                    .filter(aInChain -> aInChain.length() > 0)
                    .collect(Collectors.toSet());
        }
    }

    public ChainBuilder chain(String aInChainName) {
        aInChainName = aInChainName.trim().toUpperCase();
        ChainBuilder lChainBuilder = chainBuilders.get(aInChainName);
        if (lChainBuilder == null) {
            lChainBuilder = new ChainBuilder();
            chainBuilders.put(aInChainName, lChainBuilder);
        }
        return lChainBuilder;
    }

    public RuleTable<C, R> build() throws RuleTableException {
        validate();

        RuleTable<C, R> lRuleTable = new RuleTable<>(name);

        for (Map.Entry<String, ChainBuilder> lEntry
                : chainBuilders.entrySet()) {

            lRuleTable.addChain(lEntry.getKey(), new Chain<>());

            if (builtInChains.contains(lEntry.getKey())) {
                lRuleTable.addPolicy(lEntry.getKey(), lEntry.getValue().policy);
            }
        }

        for (Map.Entry<String, ChainBuilder> lEntry
                : chainBuilders.entrySet()) {

            Chain<C, R> lChain = lRuleTable.getChain(lEntry.getKey());
            for (RuleBuilder lRuleBuilder : lEntry.getValue().ruleBuilders) {
                Predicate<? super C> lPredicate = lRuleBuilder.predicate == null
                        ? aInContext -> true
                        : lRuleBuilder.predicate;

                if (lRuleBuilder.action != null) {
                    lChain.addRule(new Rule<>(
                            lPredicate,
                            aInContext -> {
                                lRuleBuilder.action.accept(aInContext);
                                return null;
                            },
                            false));
                } else if (lRuleBuilder.result != null) {
                    lChain.addRule(new Rule<>(
                            lPredicate,
                            aInContext -> lRuleBuilder.result,
                            true));
                } else if (lRuleBuilder.goTo != null) {
                    lChain.addRule(new Rule<>(
                            lPredicate,
                            aInContext ->
                                    lRuleTable.getChain(lRuleBuilder.goTo)
                                            .execute(aInContext),
                            true));
                } else if (lRuleBuilder.jumpTo != null) {
                    lChain.addRule(new Rule<>(
                            lPredicate,
                            aInContext ->
                                    lRuleTable.getChain(lRuleBuilder.jumpTo)
                                            .execute(aInContext),
                            false));
                }
            }
        }

        return lRuleTable;
    }

    private void validate() throws RuleTableException {
        if (name == null || name.trim().isEmpty()) {
            throw new RuleTableException(
                    "Cannot create table with empty name");
        }

        if (builtInChains == null || builtInChains.isEmpty()) {
            throw new RuleTableException(String.format(
                    "Table '%s' must contain at least one built-in chain",
                    name));
        }

        for (Map.Entry<String, ChainBuilder> lEntry
                : chainBuilders.entrySet()) {
            if (lEntry.getKey().isEmpty()) {
                throw new RuleTableException(String.format(
                        "Table '%s' may not contain chain with empty name",
                        name));
            }

            // Make sure only builtin chains contain policies
            if (lEntry.getValue().policy != null
                    && !builtInChains.contains(lEntry.getKey())) {
                throw new RuleTableException(String.format(
                        "Cannot add policy to user chain '%s' in table '%s'",
                        lEntry.getKey(), name));
            }

            // Make sure all target chains in goTo and jumpTo rules exist and
            // are not built-in.
            for (RuleBuilder lRule : lEntry.getValue().ruleBuilders) {
                String lNextChain = lRule.goTo != null
                        ? lRule.goTo
                        : lRule.jumpTo;
                if (lNextChain != null && builtInChains.contains(lNextChain)) {
                    throw new RuleTableException(String.format(
                            "Cannot jump to or go to built-in chain '%s' in " +
                                    "table '%s', chain '%s'",
                            lNextChain, name, lEntry.getKey()));
                }
            }
        }
    }

    public class ChainBuilder {
        private R policy;
        private List<RuleBuilder> ruleBuilders = new ArrayList<>();

        public ChainBuilder policy(R aInResult) {
            policy = aInResult;
            return this;
        }

        public RuleBuilder when(Predicate<? super C> aInPredicate) {
            RuleBuilder lRuleBuilder = new RuleBuilder();
            lRuleBuilder.predicate = aInPredicate;
            ruleBuilders.add(lRuleBuilder);
            return lRuleBuilder;
        }

        public ChainBuilder execute(Consumer<? super C> aInAction) {
            RuleBuilder lRuleBuilder = new RuleBuilder();
            lRuleBuilder.action = aInAction;
            ruleBuilders.add(lRuleBuilder);
            return this;
        }

        public RuleTableBuilder<C, R> exitWith(R aInResult) {
            RuleBuilder lRuleBuilder = new RuleBuilder();
            lRuleBuilder.result = aInResult;
            ruleBuilders.add(lRuleBuilder);
            return RuleTableBuilder.this;
        }

        public ChainBuilder jumpTo(String aInChain) {
            RuleBuilder lRuleBuilder = new RuleBuilder();
            lRuleBuilder.jumpTo = aInChain.trim().toUpperCase();
            ruleBuilders.add(lRuleBuilder);
            return this;
        }

        public RuleTableBuilder<C, R> goTo(String aInChain) {
            RuleBuilder lRuleBuilder = new RuleBuilder();
            lRuleBuilder.goTo = aInChain.trim().toUpperCase();
            ruleBuilders.add(lRuleBuilder);
            return RuleTableBuilder.this;
        }

        public class RuleBuilder {
            private Predicate<? super C> predicate;
            private Consumer<? super C> action;
            private R result;
            private String goTo;
            private String jumpTo;
            
            public ChainBuilder execute(Consumer<? super C> aInAction) {
                action = aInAction;
                return ChainBuilder.this;
            }

            public ChainBuilder exitWith(R aInResult) {
                result = aInResult;
                return ChainBuilder.this;
            }

            public ChainBuilder jumpTo(String aInChain) {
                jumpTo = aInChain.trim().toUpperCase();
                return ChainBuilder.this;
            }

            public ChainBuilder goTo(String aInChain) {
                goTo = aInChain.trim().toUpperCase();
                return ChainBuilder.this;
            }
        }
    }
}
