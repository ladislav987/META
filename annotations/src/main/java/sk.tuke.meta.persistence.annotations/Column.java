package sk.tuke.meta.persistence.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    String name() default ""; // Ak sa nezadá, použije sa názov premennej
    boolean nullable() default true;
    boolean unique() default false;
}