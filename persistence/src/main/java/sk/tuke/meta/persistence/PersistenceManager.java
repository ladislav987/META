package sk.tuke.meta.persistence;

import java.util.List;
import java.util.Optional;

/**
 * PersistenceManager allows to persist a set of entities into a database.
 * <p>
 * All methods of the interface may throw
 * {@link sk.tuke.meta.persistence.PersistenceException}.
 * <p>
 * Implementations of this interface requireс database connection
 * as а constructor argument.
 */
public interface PersistenceManager {

    /**
     * Create database tables for specified entity classes
     * or all entity classes in the project.
     * If tables already exist, do nothing.
     * @param types classes to create tables for.
     *              If empty then autodetect all data entity classes.
     */
    void createTables(Class<?>... types);

    /**
     * Get a specific entity based on the primary key.
     *
     * @param type entity class
     * @param id   primary key (id) value
     * @return the found entity or <code>null</code> if the entity does not exist
     */
    <T> Optional<T> get(Class<T> type, long id);

    /**
     * Get all entities of specified type.
     *
     * @param type entity class
     * @return a list of all entities stored in the database.
     */
    <T> List<T> getAll(Class<T> type);

    /**
     * Save entity into a database.
     * If entity has a non-zero identifier, manager would try to perform
     * <code>UPDATE</code>, otherwise <code>INSERT</code> would be performed.
     *
     * @param entity the entity to be saved
     */
    <T> void save(T entity);

    /**
     * Delete the entity from the database.
     *
     * @param entity the entity to be deleted
     */
    void delete(Object entity);
}
