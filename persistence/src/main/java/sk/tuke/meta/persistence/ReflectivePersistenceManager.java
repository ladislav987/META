package sk.tuke.meta.persistence;

import sk.tuke.meta.persistence.annotations.Column;
import sk.tuke.meta.persistence.annotations.Id;
import sk.tuke.meta.persistence.annotations.Table;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReflectivePersistenceManager implements PersistenceManager {
    private final Connection connection;

    public ReflectivePersistenceManager(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void createTables(Class<?>... types) {
        try {
            ClassLoader classLoader = getClass().getClassLoader();

            try (InputStream inputStream = classLoader.getResourceAsStream("data.sql")) {
                if (inputStream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        String query = reader.lines().collect(Collectors.joining(System.lineSeparator()));

                        String[] queries = query.split(";");

                        Statement statement = this.connection.createStatement();

                        for (String singleQuery : queries) {
                            statement.executeUpdate(singleQuery);
                        }
                    }
                } else {
                    System.out.println("data.sql sa nenaslo");
                    throw new PersistenceException("Chybicka");
                }
            } catch (IOException e) {
                throw new PersistenceException("Chybicka");
            }
        } catch (Exception e) {
            throw new PersistenceException(e.getMessage());
        }
//        // Loop through each class in the provided array
//        for (Class<?> cls : types) {
//            // Generate SQL field definitions based on entity class fields
//            List<String> fieldDefinitions = getFieldDefinitions(cls);
//            // Generate SQL foreign key constraints for fields that reference other entities
//            List<String> foreignKeys = getForeignKeyConstraints(cls);
//
//            // Build the SQL statement for creating a table with field definitions and foreign key constraints
//            String sql = buildCreateTableSQL(cls.getSimpleName(), fieldDefinitions, foreignKeys);
//            // Execute the SQL statement to create the table
//            executeSQL(sql);
//        }
    }



    /**
     * Retrieves a single entity of the specified type by its ID.
     *
     * @param type The Class of the entity to retrieve.
     * @param id   The ID of the entity to retrieve.
     * @param <T>  The type parameter of the entity.
     * @return An Optional containing the entity if found, otherwise an empty Optional.
     */
    @Override
    public <T> Optional<T> get(Class<T> type, long id) {
        // SQL statement to select all columns from a table matching the entity's class name where the ID matches the provided ID.
        String sql = "SELECT * FROM " + "\"" + type.getSimpleName() + "\"" + " WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id); // Set the ID parameter in the SQL query.
            ResultSet rs = stmt.executeQuery(); // Execute
            if (rs.next()) {
                T instance = type.getDeclaredConstructor().newInstance(); // Instantiate a new instance of the entity class.
                populateEntityFromResultSet(instance, type, rs); // Populate the instance with data from the result set.
                return Optional.of(instance); // Return the populated instance.
            }
        } catch (Exception e) {
            throw new PersistenceException("Failed to get entity \"" + type.getSimpleName() + "\" with ID: " + id, e);
        }
        return Optional.empty(); // If no entity was found, return an empty Optional.
    }


    /**
     * Retrieves all entities of the specified type.
     *
     * @param type The Class of the entities to retrieve.
     * @param <T>  The type parameter of the entities.
     * @return A List of entities of the specified type.
     */
    @Override
    public <T> List<T> getAll(Class<T> type) {
        List<T> resultList = new ArrayList<>(); // Initialize a list to hold the result entities.
        // SQL statement to select all columns from the table that matches the entity's class name.
        String sql = "SELECT * FROM \"" + type.getSimpleName() + "\"";
        try (Statement stmt = connection.createStatement(); // Create a statement object.
             ResultSet rs = stmt.executeQuery(sql)) { // Execute the query and get the result set.
            while (rs.next()) {
                T instance = type.getDeclaredConstructor().newInstance(); // Instantiate a new instance of the entity class for each row.
                populateEntityFromResultSet(instance, type, rs); // Populate the instance with data from the current row in the result set.
                resultList.add(instance); // Add the populated instance to the result list.
            }
        } catch (Exception e) {
            throw new PersistenceException("Error retrieving all entities of type \"" + type.getSimpleName() + "\"", e);
        }
        return resultList; // Return the list of populated instances.
    }


    /**
     * Saves an entity to the database. This method will insert a new entity if it does not already exist
     * or update an existing entity. It also handles saving entities referenced by the current entity to ensure
     * database integrity.
     *
     * @param entity The entity to be saved.
     * @param <T>    The type of the entity.
     */
    @Override
    public <T> void save(T entity) {
        try {
            Class<?> clazz = entity.getClass(); // Get the class of the entity.
            String tableName = "\"" + clazz.getSimpleName() + "\""; // Format table name to match SQL standards.
            Long entityId = getEntityId(entity); // Retrieve the entity's ID.

            // Iterate over all fields of the class to check for referenced entities that need to be saved first.
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true); // Make private fields accessible.
                if (isReferenceField(field)) { // Check if the field is a reference to another entity.
                    Object referencedEntity = field.get(entity); // Get the referenced entity object.
                    Long referencedEntityId = getEntityId(referencedEntity); // Get the ID of the referenced entity.
                    // If the referenced entity does not have a valid ID, throw an exception to ensure referential integrity.
                    if (referencedEntityId == null || referencedEntityId == 0) {
                        throw new PersistenceException("Referenced entity must be saved before saving " + clazz.getSimpleName());
                    }
                }
            }

            if (entityId == null || entityId == 0) { // If the entity does not have an ID, it's considered new and needs to be inserted.
                entityId = insertEntity(entity, tableName, clazz); // Insert the new entity and retrieve the generated ID.
                setEntityId(entity, entityId); // Update the entity's ID field with the generated ID.
            } else {
                // If the entity has an ID, it exists in the database and needs to be updated.
                updateEntity(entity, tableName, clazz, entityId);
            }
        } catch (Exception e) {
            throw new PersistenceException("Error during save operation", e);
        }
    }

    /**
     * Deletes an entity from the database by its ID.
     *
     * @param entity The entity to delete.
     */
    @Override
    public void delete(Object entity) {
        Class<?> clazz = entity.getClass(); // Obtain the class of the entity to be deleted.
        String tableName = "\"" + clazz.getSimpleName() + "\""; // Determine the table name based on the entity class name.
        try {
            Field idField = clazz.getDeclaredField("id"); // Assuming 'id' is the name of the primary key field.
            idField.setAccessible(true); // Make the id field accessible, even if it is private.
            long id = idField.getLong(entity); // Retrieve the value of the id field from the entity.

            // Construct a SQL statement to delete the entity from the table based on its id.
            String sql = "DELETE FROM " + tableName + " WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, id); // Set the id in the prepared statement.
                int affectedRows = stmt.executeUpdate(); // Execute the update and get the number of affected rows.
                if (affectedRows == 0) {
                    // If no rows were affected, throw an exception indicating the delete operation failed.
                    throw new PersistenceException("Deleting entity failed, no rows affected.");
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // If accessing the id field fails due to it not existing or being inaccessible, wrap and throw as a PersistenceException.
            throw new PersistenceException("Failed to access id field on \"" + clazz.getSimpleName() + "\"", e);
        } catch (SQLException e) {
            // If executing the SQL statement fails, wrap and throw as a PersistenceException.
            throw new PersistenceException("Failed to delete entity \"" + clazz.getSimpleName() + "\"", e);
        }
    }



    // ------------------------------------------------------
    // helper function


    /**
     * Populates an entity's fields from a ResultSet. This includes setting both primitive and reference fields.
     * Reference fields are loaded by their IDs and fetched recursively.
     *
     * @param instance The instance of the entity to populate.
     * @param type     The Class of the entity.
     * @param rs       The ResultSet containing the database query results.
     * @param <T>      The type parameter of the entity.
     * @throws IllegalAccessException when illegal access to a field occurs.
     * @throws SQLException           when a database access error occurs.
     */
    // get getAll
    private <T> void populateEntityFromResultSet(T instance, Class<T> type, ResultSet rs) throws IllegalAccessException, SQLException {
        for (Field field : type.getDeclaredFields()) { // Loop through each field of the entity class.
            field.setAccessible(true); // Make the field accessible, even if it is private.
            if (!field.getType().isPrimitive() && !field.getType().equals(String.class) && !Collection.class.isAssignableFrom(field.getType())) {
                long relatedEntityId = rs.getLong(field.getName()); // Get the ID of the related entity from the ResultSet.
                if (relatedEntityId > 0) {
                    Optional<?> relatedEntity = get(field.getType(), relatedEntityId); // Recursively fetch the related entity.
                    relatedEntity.ifPresent(value -> {
                        try {
                            field.set(instance, value); // Set the related entity instance to the field.
                        } catch (IllegalAccessException e) {
                            throw new PersistenceException(e.getMessage(), e);
                        }
                    });
                }
            } else {
                // For primitive, String, and Collection fields...
                Object value = rs.getObject(field.getName()); // Get the value directly from the ResultSet.
                if (value != null) {
                    field.set(instance, value); // Set the value to the field.
                }
            }
        }
    }





    /**
     * Maps Java types to SQL types for use in table creation. This simplifies the creation of table
     * definitions based on the fields of entity classes.
     *
     * @param type The Java Class type to map to SQL.
     * @return A String representing the SQL type equivalent of the Java type.
     */
    // createTables
    private String javaTypeToSQLType(Class<?> type) {
        if (int.class.equals(type) || long.class.equals(type)) {
            return "INTEGER"; // Map Java int and long types to SQL INTEGER type.
        } else if (double.class.equals(type) || float.class.equals(type)) {
            return "REAL"; // Map Java double and float types to SQL REAL type.
        } else if (String.class.equals(type)) {
            return "TEXT"; // Map Java String type to SQL TEXT type.
        }
        // Default to TEXT for types not explicitly handled.
        return "TEXT";
    }


    /**
     * Inserts a new entity into the database. It constructs an INSERT SQL statement based on the entity's fields,
     * excluding the ID, and executes it, returning the generated key if available.
     *
     * @param entity    The entity to insert.
     * @param tableName The name of the table where the entity is to be inserted.
     * @param clazz     The Class of the entity.
     * @param <T>       The type parameter of the entity.
     * @return The generated ID of the inserted entity, if available.
     * @throws IllegalAccessException when illegal access to a field occurs.
     * @throws SQLException           when a database access error occurs.
     */
    // save
    private <T> Long insertEntity(T entity, String tableName, Class<?> clazz) throws IllegalAccessException, SQLException {
        List<Object> values = new ArrayList<>(); // List to hold the values to be inserted.
        StringBuilder sql = new StringBuilder("INSERT INTO " + tableName + " (");
        StringBuilder placeholders = new StringBuilder(); // For use in the prepared statement.

        Field[] fields = clazz.getDeclaredFields();
        boolean first = true; // Flag to help format the SQL statement correctly.
        for (Field field : fields) {
            field.setAccessible(true); // Make the field accessible, even if it is private.
            if (!"id".equals(field.getName())) { // Exclude the 'id' field as it's typically auto-generated by the database.
                if (!first) {
                    sql.append(", ");
                    placeholders.append(", ");
                } else {
                    first = false; // Update flag after the first field is processed.
                }
                sql.append("\"").append(field.getName()).append("\""); // Append field name to SQL statement.
                placeholders.append("?"); // Use placeholders for values to be bound later.
                Object value = field.get(entity); // Retrieve the field value from the entity.
                if (isReferenceField(field) && value != null) {
                    // If the field is a reference to another entity, retrieve the ID of the referenced entity.
                    value = getEntityId(value);
                }
                values.add(value); // Add the value (or referenced entity ID) to the values list.
            }
        }

        sql.append(") VALUES (").append(placeholders).append(")"); // Complete the SQL statement.

        return executeInsert(sql.toString(), values.toArray()); // Execute the insert operation and return the generated ID.
    }

    /**
     * Updates an existing entity in the database. It constructs an UPDATE SQL statement based on the entity's fields
     * and executes it.
     *
     * @param entity    The entity to update.
     * @param tableName The name of the table where the entity exists.
     * @param clazz     The Class of the entity.
     * @param entityId  The ID of the entity to update.
     * @throws IllegalAccessException when illegal access to a field occurs.
     * @throws SQLException           when a database access error occurs.
     */
    // save
    private void updateEntity(Object entity, String tableName, Class<?> clazz, Long entityId) throws IllegalAccessException, SQLException {
        List<Object> values = new ArrayList<>(); // Prepare a list to hold the values to be updated.
        StringBuilder sql = new StringBuilder("UPDATE " + tableName + " SET "); // Start building the UPDATE SQL statement.

        Field[] fields = clazz.getDeclaredFields();
        boolean first = true; // Flag to ensure correct comma placement in the SQL statement.
        for (Field field : fields) {
            field.setAccessible(true); // Make the field accessible, even if it is private.
            if (!"id".equals(field.getName())) { // Skip the 'id' field as it's used in the WHERE clause, not SET.
                if (!first) {
                    sql.append(", "); // Append comma before adding the next field-value pair, except for the first pair.
                } else {
                    first = false; // After handling the first field-value pair, change the flag.
                }
                sql.append("\"").append(field.getName()).append("\" = ?"); // Add the field name and placeholder to the SQL statement.
                Object value = field.get(entity); // Retrieve the field value from the entity.
                if (isReferenceField(field) && value != null) {
                    // If the field is a reference to another entity, retrieve the ID of the referenced entity as the value.
                    value = getEntityId(value);
                }
                values.add(value); // Add the field value (or referenced entity ID) to the values list.
            }
        }

        sql.append(" WHERE id = ?"); // Complete the SQL statement with the WHERE clause to target the specific entity.
        values.add(entityId); // Add the entity ID as the last value to bind, for the WHERE clause.

        executeUpdate(sql.toString(), values.toArray()); // Execute the update operation with the built SQL statement and values.
    }

    /**
     * Executes an SQL INSERT statement to store a new entity in the database and returns the generated key.
     *
     * @param sql    The SQL INSERT statement to execute.
     * @param values The values to be bound to the INSERT statement's placeholders.
     * @return The generated key for the newly inserted entity, typically an ID.
     * @throws SQLException If executing the INSERT statement fails.
     */
    // insertEntity
    private Long executeInsert(String sql, Object[] values) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) {
                stmt.setObject(i + 1, values[i]); // Bind each value to its corresponding placeholder in the INSERT statement.
            }
            int affectedRows = stmt.executeUpdate(); // Execute the INSERT operation.
            if (affectedRows == 0) {
                throw new SQLException("Creating entity failed, no rows affected."); // Check if the INSERT operation affected any rows.
            }
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1); // Retrieve and return the generated key (ID) for the inserted entity.
                } else {
                    throw new SQLException("Creating entity failed, no ID obtained."); // Handle case where no generated key was obtained.
                }
            }
        }
    }

    /**
     * Sets the ID field of an entity to a specified value. This method is used after inserting a new entity
     * to update the entity's ID field with the generated key.
     *
     * @param entity The entity whose ID field is to be set.
     * @param id     The value to set the ID field to.
     * @throws NoSuchFieldException   If the entity class does not have an 'id' field.
     * @throws IllegalAccessException If the 'id' field is inaccessible.
     */
    // save
    private void setEntityId(Object entity, long id) throws NoSuchFieldException, IllegalAccessException {
        Field idField = entity.getClass().getDeclaredField("id"); // Locate the 'id' field in the entity class.
        idField.setAccessible(true); // Ensure the field is accessible, even if it's private.
        idField.set(entity, id); // Set the 'id' field of the entity to the specified value (the generated key).
    }


    /**
     * Retrieves the value of the 'id' field of an entity. This method is used to obtain the entity's ID
     * for database operations that require an entity's ID, such as updates or deletes.
     *
     * @param entity The entity whose ID is to be retrieved.
     * @return The value of the entity's ID field.
     * @throws PersistenceException If the entity class does not have an 'id' field or if access to the field is denied.
     */
    // save updateEntity insertEntity
    private Long getEntityId(Object entity) {
        try {
            Field idField = entity.getClass().getDeclaredField("id"); // Attempt to locate the 'id' field in the entity class.
            idField.setAccessible(true); // Make sure the field is accessible, regardless of its access modifier.
            return (Long) idField.get(entity); // Retrieve and return the value of the 'id' field.
        } catch (NoSuchFieldException e) {
            // If no 'id' field exists in the entity class, throw a PersistenceException.
            throw new PersistenceException("Entity class " + entity.getClass().getSimpleName() + " does not have an 'id' field.", e);
        } catch (IllegalAccessException e) {
            // If the 'id' field is inaccessible, throw a PersistenceException.
            throw new PersistenceException("Access to 'id' field in entity class " + entity.getClass().getSimpleName() + " was denied.", e);
        }
    }


    /**
     * Determines whether a given field is a reference to another entity. This is used to handle foreign keys
     * in relational database operations.
     *
     * @param field The field to check.
     * @return true if the field is considered a reference to another entity; false otherwise.
     */
    // save updateEntity insertEntity
    private boolean isReferenceField(Field field) {
        // Assuming non-primitive and non-String fields are references to other entities.
        return !field.getType().isPrimitive() && !field.getType().equals(String.class);
    }

    /**
     * Executes an SQL UPDATE statement to modify an existing entity in the database.
     *
     * @param sql    The SQL UPDATE statement to execute.
     * @param values The values to be bound to the UPDATE statement's placeholders.
     * @throws SQLException If executing the UPDATE statement fails.
     */
    // updateEntity
    private void executeUpdate(String sql, Object[] values) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) { // Prepare the UPDATE statement with the given SQL.
            for (int i = 0; i < values.length; i++) {
                stmt.setObject(i + 1, values[i]); // Bind each value to its corresponding placeholder in the statement.
            }
            stmt.executeUpdate(); // Execute the UPDATE operation.
        }
    }

    /**
     * Executes a general SQL statement, typically used for table creation.
     *
     * @param sql The SQL statement to execute.
     * @throws PersistenceException If executing the SQL statement fails.
     */

    // createTables
    private void executeSQL(String sql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql); // Execute the given SQL statement, which is typically used for DDL commands like creating tables.
        } catch (SQLException e) {
            // If any SQLException occurs during the execution, wrap it in a custom PersistenceException.
            throw new PersistenceException("Error executing SQL: " + sql, e);
        }
    }

    /**
     * Generates SQL field definitions for creating tables based on entity class fields.
     *
     * @param cls The entity class to generate field definitions for.
     * @return A list of SQL field definitions.
     */

    // createTables
    private List<String> getFieldDefinitions(Class<?> cls) {
        List<String> fieldDefs = new ArrayList<>(); // Initialize a list to hold the field definitions.
        for (Field field : cls.getDeclaredFields()) { // Iterate over each field declared in the entity class.
            boolean isReference = !field.getType().isPrimitive() && !field.getType().equals(String.class) && !Collection.class.isAssignableFrom(field.getType());
            // Determine if the field is a reference to another entity
            String fieldType = isReference ? "INTEGER" : javaTypeToSQLType(field.getType());
            // Map the Java type to an SQL type. References are typically represented as INTEGER foreign keys.

            String fieldDef = "\"" + field.getName() + "\" " + fieldType; // Create the field definition string.
            if ("id".equals(field.getName())) {
                fieldDef += " PRIMARY KEY AUTOINCREMENT"; // If the field is 'id', add PRIMARY KEY AUTOINCREMENT.
            }
            fieldDefs.add(fieldDef); // Add the field definition to the list.
        }
        return fieldDefs; // Return the list of field definitions.
    }

    /**
     * Generates SQL foreign key constraints for creating tables, based on entity class fields that reference other entities.
     *
     * @param cls The entity class to generate foreign key constraints for.
     * @return A list of SQL foreign key constraints.
     */

    // createTables
    private List<String> getForeignKeyConstraints(Class<?> cls) {
        List<String> foreignKeys = new ArrayList<>(); // Initialize a list to hold foreign key constraints.
        for (Field field : cls.getDeclaredFields()) { // Iterate over each declared field in the class.
            if (!field.getType().isPrimitive() && !field.getType().equals(String.class) && !Collection.class.isAssignableFrom(field.getType())) {
                // Check if the field is a reference to another entity (excluding primitives, Strings, and Collections).
                String fkConstraint = "FOREIGN KEY (\"" + field.getName() + "\") REFERENCES " + field.getType().getSimpleName() + "(id)";
                // Construct the foreign key constraint SQL string.
                foreignKeys.add(fkConstraint); // Add the constructed constraint to the list.
            }
        }
        return foreignKeys; // Return the list of foreign key constraints.
    }

    /**
     * Builds the SQL statement for creating a table based on entity class fields and foreign key constraints.
     *
     * @param tableName        The name of the table to create.
     * @param fieldDefinitions A list of field definitions for the table.
     * @param foreignKeys      A list of foreign key constraints for the table.
     * @return The SQL statement for creating the table.
     */

    // createTables
    private String buildCreateTableSQL(String tableName, List<String> fieldDefinitions, List<String> foreignKeys) {
        String fieldDefsString = String.join(", ", fieldDefinitions); // Combine all field definitions into a single string.
        String foreignKeysString = String.join(", ", foreignKeys); // Combine all foreign key constraints into a single string.

        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS "); // Start building the CREATE TABLE SQL statement.
        sql.append("\"").append(tableName).append("\"").append(" ("); // Append the table name.
        sql.append(fieldDefsString); // Append the field definitions.

        if (!foreignKeys.isEmpty()) {
            sql.append(", ").append(foreignKeysString); // If there are foreign key constraints, append them to the statement.
        }

        sql.append(");"); // Close the SQL statement.
        return sql.toString(); // Return the complete SQL statement as a string.
    }

}
