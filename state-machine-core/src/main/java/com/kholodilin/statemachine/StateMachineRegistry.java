package com.kholodilin.statemachine;

import com.kholodilin.statemachine.definition.StateMachineDefinition;
import com.kholodilin.statemachine.exception.InvalidDefinitionException;
import com.kholodilin.statemachine.exception.UnknownMachineTypeException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lookup of static {@link StateMachineDefinition} beans by {@code machineType}.
 */
public interface StateMachineRegistry {

    /**
     * @param machineType definition name
     * @return the registered definition
     * @throws UnknownMachineTypeException if no definition is registered
     */
    StateMachineDefinition<?, ?> getRequired(String machineType);

    /**
     * @param machineType definition name
     * @return empty when the type is unknown
     */
    Optional<StateMachineDefinition<?, ?>> find(String machineType);

    /**
     * @return all registered definitions in registration order
     */
    Collection<StateMachineDefinition<?, ?>> all();

    /**
     * Map-backed registry. Duplicate {@code machineType} values fail construction.
     */
    final class InMemory implements StateMachineRegistry {

        private final Map<String, StateMachineDefinition<?, ?>> definitions;

        /**
         * @param definitions application beans; {@code machineType} must be unique
         */
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

        /**
         * {@inheritDoc}
         */
        @Override
        public StateMachineDefinition<?, ?> getRequired(String machineType) {
            StateMachineDefinition<?, ?> definition = definitions.get(machineType);
            if (definition == null) {
                throw new UnknownMachineTypeException(machineType);
            }
            return definition;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<StateMachineDefinition<?, ?>> find(String machineType) {
            return Optional.ofNullable(definitions.get(machineType));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<StateMachineDefinition<?, ?>> all() {
            return List.copyOf(definitions.values());
        }
    }
}
