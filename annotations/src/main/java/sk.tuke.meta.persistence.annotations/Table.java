package sk.tuke.meta.persistence.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {
    String name() default ""; // Predvolená hodnota, indikuje použitie názvu triedy, ak nie je poskytnutý názov tabuľky
}


