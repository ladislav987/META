//package sk.tuke.meta.persistence;
//
//import sk.tuke.meta.persistence.annotations.Column;
//import sk.tuke.meta.persistence.annotations.Id;
//import sk.tuke.meta.persistence.annotations.Table;
//
//import javax.annotation.processing.AbstractProcessor;
//import javax.annotation.processing.ProcessingEnvironment;
//import javax.annotation.processing.RoundEnvironment;
//import javax.lang.model.SourceVersion;
//import javax.lang.model.element.TypeElement;
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.util.Set;
//import javax.lang.model.element.Element;
//import javax.lang.model.type.TypeKind;
//import javax.lang.model.type.TypeMirror;
//import javax.lang.model.util.Elements;
//import javax.lang.model.util.Types;
//import javax.annotation.processing.Filer;
//import javax.tools.StandardLocation;
//
//
//public class Processora extends AbstractProcessor {
//
//    private Types typeUtils;
//    private Elements elementUtils;
//
//    @Override
//    public void init(ProcessingEnvironment processingEnv) {
//        super.init(processingEnv);
//        this.typeUtils = processingEnv.getTypeUtils();
//        this.elementUtils = processingEnv.getElementUtils();
//    }
//
//    @Override
//    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
//        for (Element element : roundEnv.getElementsAnnotatedWith(Table.class)) {
//
//            TypeElement typeElement = (TypeElement) element;
//
//            Table tableAnnotation = element.getAnnotation(Table.class);
//
//            for (Element enclosedElement : typeElement.getEnclosedElements()) {
//                Column columnAnnotation = enclosedElement.getAnnotation(Column.class);
//
//                if(columnAnnotation != null) {
//
////                    toto vo funkcii
//                    TypeMirror floatType = typeUtils.getPrimitiveType(TypeKind.FLOAT);
//                    TypeMirror fieldType = enclosedElement.asType();
//
//                    if(fieldType == floatType) {
//                        query += "REAL"
////                        return "REAL";
//                    }
//
//
//
//                    Id idAnnotation = enclosedElement.getAnnotation(Id.class);
//
//                    if(idAnnotation != null) {
//                        query += "PRIMARY KEY AUTO INCREMENT"
//                    }
//
//                    if(columnAnnotation.unique()) {
//                        query += "UNIQUE"
//                    }
//
//                    if(!columnAnnotation.nullable()) {
//                        query += "UNIQUE"
//                    }
//
////                    CREATE TABLE example_table (
////                            id INT PRIMARY KEY,
////                            name VARCHAR(50),
////                            age INT
////                    );
////                    REATE TABLE example_table (
////                            id INT PRIMARY KEY,
////                            name VARCHAR(50),
////                            age INT
////                    );
//                }
//            }
//
//
//            System.out.println("-- " + element.getSimpleName());
//        }
//
//        Filer filer = processingEnv.getFiler();
//
//        try (PrintWriter writer = new PrintWriter(filer.createResource(StandardLocation.CLASS_OUTPUT, "", "data.sql").openWriter())) {
//            writer.println(queryBuilder.toString());
//        } catch (IOException e) {
//            System.out.println("Nastala nejaka chybicka... ");
//            System.out.println(e.getMessage());
//        }
//
//        return false;
//    }
//
//    @Override
//    public Set<String> getSupportedAnnotationTypes() {
//        return Set.of(Table.class.getCanonicalName());
//    }
//
//    @Override
//    public SourceVersion getSupportedSourceVersion() {
//        return SourceVersion.latestSupported();
//    }
//}
