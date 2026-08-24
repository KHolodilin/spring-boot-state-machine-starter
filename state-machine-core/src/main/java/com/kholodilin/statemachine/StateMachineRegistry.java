package com.kholodilin.statemachine;

import com.kholodilin.statemachine.definition.StateMachineDefinition;
import com.kholodilin.statemachine.exception.InvalidDefinitionException;
import com.kholodilin.statemachine.exception.UnknownMachineTypeException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface StateMachineRegistry {

    StateMachineDefinition<?, ?> getRequired(String machineType);

    Optional<StateMachineDefinition<?, ?>> find(String machineType);

    Collection<StateMachineDefinition<?, ?>> all();

    final class InMemory implements StateMachineRegistry {

        private final Map<String, StateMachineDefinition<?, ?>> definitions;

        public InMemory(Collection<StateMachineDefinition<?, ?>> definitions) {
            Map<String, StateMachineDefinition<?, ?>> byType = new LinkedHashMap<>();
            for (StateMachineDefinition<?, ?> definition : definitions) {
                StateMachineDefinition<?, ?> previous = byType.put(definition.machineType(), definition);
                if (previous != null) {
                    throw new InvalidDefinitionException(
                            "Duplicate machineType '" + definition.machineType() + "'");
                }
            }
            this.definitions = Map.copyOf(byType);
        }

        @Override
        public StateMachineDefinition<?, ?> getRequired(String machineType) {
            StateMachineDefinition<?, ?> definition = definitions.get(machineType);
            if (definition == null) {
                throw new UnknownMachineTypeException(machineType);
            }
            return definition;
        }

        @Override
        public Optional<StateMachineDefinition<?, ?>> find(String machineType) {
            return Optional.ofNullable(definitions.get(machineType));
        }

        @Override
        public Collection<StateMachineDefinition<?, ?>> all() {
            return List.copyOf(definitions.values());
        }
    }
}
