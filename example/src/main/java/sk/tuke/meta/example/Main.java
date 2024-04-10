package sk.tuke.meta.example;

import sk.tuke.meta.persistence.PersistenceManager;
import sk.tuke.meta.persistence.ReflectivePersistenceManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;

public class Main {
    public static final String DB_PATH = "test.db";

    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);

        PersistenceManager manager = new ReflectivePersistenceManager(conn);

        manager.createTables(Person.class, Department.class);

        exampleOperations(manager);

        conn.close();
    }

    private static void exampleOperations(PersistenceManager manager) {
        Department development = new Department("Development", "DVLP");
        manager.save(development);

        Person hrasko = new Person("Janko", "Hrasko", 30);
        hrasko.setDepartment(development);
        manager.save(hrasko);

        List<Person> persons = manager.getAll(Person.class);
        for (Person person : persons) {
            System.out.println(person);
            System.out.println("  " + person.getDepartment());
        }
        Optional<Department> anotherDepartment = manager.get(Department.class, 100);
        System.out.println(anotherDepartment.isPresent());
    }
}
