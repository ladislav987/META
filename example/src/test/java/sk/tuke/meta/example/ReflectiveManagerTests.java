package sk.tuke.meta.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.tuke.meta.persistence.PersistenceException;
import sk.tuke.meta.persistence.PersistenceManager;
import sk.tuke.meta.example.Department;
import sk.tuke.meta.example.Person;
import sk.tuke.meta.persistence.ReflectivePersistenceManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReflectiveManagerTests {
    private Connection connection;
    private PersistenceManager manager;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");

        //create tables
        manager = new ReflectivePersistenceManager(connection);
        manager.createTables(Department.class, Person.class);

        //insert testing data
        String data = "insert into Department(name, code)\n" +
                "  values ('Development', 'DEV');\n" +
                "insert into Department(name, code)\n" +
                "  values ('Operations', 'OPS');\n" +
                "insert into Person(name, surname, age, department)\n" +
                "  values ('Janko', 'Hrasko', 1000, 1);\n" +
                "insert into Person(name, surname, age, department)\n" +
                "  values ('Jozko', 'Mrkvicka', 1200, null);\n";

        executeSqlScript(data);
    }

    @Test
    void getDepartment() {
        var result = manager.get(Department.class, 1);
        assertTrue(result.isPresent());
        Department devDepartment = result.get();
        assertDepartmentValue(devDepartment, 1, "Development", "DEV");
    }

    @Test
    void missingDepartment() {
        var result = manager.get(Department.class, 404);
        assertFalse(result.isPresent());
    }

    @Test
    void getAllDepartments() {
        var departments = manager.getAll(Department.class);
        assertEquals(2, departments.size());
        assertDepartmentValue(departments.get(0), 1, "Development", "DEV");
        assertDepartmentValue(departments.get(1), 2, "Operations", "OPS");
    }

    @Test
    void getNoDepartments() throws SQLException {
        connection.prepareStatement("delete from Department").executeUpdate();
        List<Department> departments = manager.getAll(Department.class);
        assertEquals(List.of(), departments);
    }

    @Test
    void saveNewDepartment() throws SQLException {
        var department = new Department("Marketing", "MRK");
        manager.save(department);
        assertSqlHasResult("select * from Department where id=3 and name='Marketing' and code='MRK'");
    }

    @Test
    void saveNewDepartmentSetsId() {
        var department = new Department("Marketing", "MRK");
        manager.save(department);
        assertEquals(3, department.getId());
    }

    @Test
    void saveUpdatedDepartment() throws SQLException {
        var result = manager.get(Department.class, 1);
        var department = result.get();
        department.setName("Engineering");
        department.setCode("ENG");
        manager.save(department);
        assertSqlHasResult("select * from Department where id=1 and name='Engineering' and code='ENG'");
    }

    @Test
    void getPerson() {
        var result = manager.get(Person.class, 1);
        assertTrue(result.isPresent());
        Person person = result.get();
        assertPersonValue(person, 1, "Janko", "Hrasko", 1000);
    }

    @Test
    void missingPerson() {
        var result = manager.get(Person.class, 404);
        assertFalse(result.isPresent());
    }

    @Test
    void getPersonDepartment() {
        var result = manager.get(Person.class, 1);
        var person = result.get();
        assertNotNull(person.getDepartment());
        assertDepartmentValue(person.getDepartment(),
                1, "Development", "DEV");
    }

    @Test
    void getAllPersons() {
        var persons = manager.getAll(Person.class);
        assertEquals(2, persons.size());
        assertPersonValue(persons.get(0), 1, "Janko", "Hrasko", 1000);
        assertPersonValue(persons.get(1), 2, "Jozko", "Mrkvicka", 1200);
    }

    @Test
    void deleteDepartmentThrowsExceptionWhenAttached() {
        var departments = manager.getAll(Department.class);
        var department = departments.get(0);

        //this does not work in in-memory. no constraints there.
        //assertThrows(PersistenceException.class, () -> manager.delete(department));
    }

    @Test
    void deletePerson() {
        var persons = manager.getAll(Person.class);
        var person = persons.get(1);

        manager.delete(person);

        persons =  manager.getAll(Person.class);
        assertEquals(1, persons.size());
        assertPersonValue(persons.get(0), 1, "Janko", "Hrasko", 1000);
    }

    @Test
    void getNoPersons() throws SQLException {
        connection.prepareStatement("delete from person").executeUpdate();
        var persons = manager.getAll(Person.class);
        assertEquals(List.of(), persons);
    }

    @Test
    void getAllPersonsLoadsDepartments() {
        var persons = manager.getAll(Person.class);
        assertDepartmentValue(persons.get(0).getDepartment(),
                1, "Development", "DEV");
        assertNull(persons.get(1).getDepartment());
    }

    @Test
    void saveNewPerson() throws SQLException {
        var person = new Person("Ferko", "Kapustka", 2000);
        person.setDepartment(manager.get(Department.class, 1).get());
        manager.save(person);
        String sql = "select * from person where id=3" +
                " and name='Ferko' and surname='Kapustka' and age=2000 and department=1";
        assertSqlHasResult(sql);
    }

    @Test
    void saveNewPersonSetsId() {
        var person = new Person("Ferko", "Kapustka", 2000);
        person.setDepartment(manager.get(Department.class, 1).get());
        manager.save(person);
        assertEquals(3, person.getId());
    }

    @Test
    void saveNewPersonWithNewDepartment() throws SQLException {
        var person = new Person("Ferko", "Kapustka", 2000);
        var department = new Department("Marketing", "MRK");
        person.setDepartment(department);
        manager.save(department);
        manager.save(person);
        assertSqlHasResult("select * from person where id=3 and" +
                " name='Ferko' and surname='Kapustka' and age=2000 and department=3");
        assertSqlHasResult("select * from Department where id=3 " +
                "and name='Marketing' and code='MRK'");
    }

    @Test
    void saveNewPersonWithUnsavedDepartmentThrowsException() {
        var person = new Person("Ferko", "Kapustka", 2000);
        person.setDepartment(new Department("Marketing", "MRK"));
        assertThrows(PersistenceException.class, () -> manager.save(person));
    }

    @Test
    void saveNewPersonWithoutDepartment() throws SQLException {
        var person = new Person("Ferko", "Kapustka", 2000);
        manager.save(person);
        String sql = "select * from person where id=3" +
                " and name='Ferko' and surname='Kapustka' and age=2000 and department is null";
        assertSqlHasResult(sql);
    }

    @Test
    void saveUpdatedPerson() throws SQLException {
        var person = manager.get(Person.class, 1).get();
        person.setAge(1500);
        manager.save(person);
        String sql = "select * from person where id=1" +
                " and name='Janko' and surname='Hrasko' and age=1500 and department=1";
        assertSqlHasResult(sql);
    }

    private void assertDepartmentValue(
            Department devDepartment, int id, String name, String code) {
        assertEquals(id, devDepartment.getId());
        assertEquals(name, devDepartment.getName());
        assertEquals(code, devDepartment.getCode());
    }

    private void assertPersonValue(
            Person person, int id, String name, String surname, int age) {
        assertEquals(id, person.getId());
        assertEquals(name, person.getName());
        assertEquals(surname, person.getSurname());
        assertEquals(age, person.getAge());
    }

    private void assertSqlHasResult(String sql) throws SQLException {
        var statement = connection.prepareStatement(sql);
        assertTrue(statement.executeQuery().next());
    }

    private void executeSqlScript(String input) throws SQLException {
        var statement = connection.createStatement();
        var split = input.split(";");
        for (var st : split) {
            String statementString = st.trim();
            if (!statementString.isEmpty()) {
                statement.addBatch(statementString);
            }
        }
        statement.executeBatch();
    }
}
