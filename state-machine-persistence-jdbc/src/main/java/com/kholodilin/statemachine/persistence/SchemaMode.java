package com.kholodilin.statemachine.persistence;

/**
 * Startup behaviour for library tables. Bound from {@code state-machine.persistence.schema.mode}.
 */
public enum SchemaMode {
    /** Create missing tables from {@code state-machine-schema.sql}. */
    CREATE,
    /** Fail-fast if tables, columns or keys do not match. */
    VALIDATE,
    /** Application owns DDL (Flyway/Liquibase). */
    NONE
}
