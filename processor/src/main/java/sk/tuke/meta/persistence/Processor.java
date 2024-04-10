package sk.tuke.meta.persistence;

import sk.tuke.meta.persistence.annotations.Column;
import sk.tuke.meta.persistence.annotations.Id;
import sk.tuke.meta.persistence.annotations.Table;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

public class Processor extends AbstractProcessor {
    private Types typeUtils;
    private Elements elementUtils;

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Table.class)) {
            TypeElement typeElement = (TypeElement) element;
            Table tableAnnotation = element.getAnnotation(Table.class);

            StringBuilder queryBuilder = new StringBuilder();

            // Start building the query
            queryBuilder.append("CREATE TABLE IF NOT EXISTS ")
                    .append(getTableName(tableAnnotation, typeElement)) // Pass typeElement
                    .append(" (");

            for (Element enclosedElement : typeElement.getEnclosedElements()) {
                Column columnAnnotation = enclosedElement.getAnnotation(Column.class);

                if (columnAnnotation != null) {
                    TypeMirror fieldType = enclosedElement.asType();

                    queryBuilder.append(enclosedElement.getSimpleName()).append(" ");
                    queryBuilder.append(javaTypeToSQLType(fieldType)).append(", ");

                    if (enclosedElement.getAnnotation(Id.class) != null) {
                        queryBuilder.append(" PRIMARY KEY AUTOINCREMENT, ");
                    } else if (columnAnnotation.unique()) {
                        queryBuilder.append(" UNIQUE, ");
                    }
                    if (!columnAnnotation.nullable()) {
                        queryBuilder.append(" NOT NULL, ");
                    }
                }
            }

            // Remove trailing comma, close the statement
            if (queryBuilder.lastIndexOf(", ") > 0) {
                queryBuilder.delete(queryBuilder.length() - 2, queryBuilder.length());
            }
            queryBuilder.append(");");

            // Write the query to data.sql
            writeSQLToFile(this.processingEnv.getFiler(), queryBuilder.toString());
        }

        return true;
    }

    // Helper method to convert Java types to SQL types
    private String javaTypeToSQLType(TypeMirror typeMirror) {
        if (typeUtils.isSameType(typeMirror, typeUtils.getPrimitiveType(TypeKind.INT))) {
            return "INTEGER";
        } else if (typeUtils.isSameType(typeMirror, typeUtils.getPrimitiveType(TypeKind.FLOAT))) {
            return "REAL";
        } else if (typeUtils.isSameType(typeMirror, typeUtils.getPrimitiveType(TypeKind.DOUBLE))) {
            return "REAL";
        } else if (typeUtils.isSameType(typeMirror, typeUtils.getDeclaredType(elementUtils.getTypeElement("java.lang.String")))) {
            return "TEXT";
        } else {
            return "TEXT";
        }
    }

    // Helper method to get the table name (use class name if Table annotation name is empty)
    private String getTableName(Table tableAnnotation, TypeElement typeElement) { // Accept typeElement
        return tableAnnotation.name().isEmpty() ? typeElement.getSimpleName().toString() : tableAnnotation.name();
    }

    // Helper method to write SQL to file
    private void writeSQLToFile(Filer filer, String sqlQuery) {
        try (PrintWriter writer = new PrintWriter(filer.createResource(StandardLocation.CLASS_OUTPUT, "", "data.sql").openWriter())) {
            writer.println(sqlQuery);
        } catch (IOException e) {
            // Improve error reporting here as needed.
            System.out.println("Error writing data.sql: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Table.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
