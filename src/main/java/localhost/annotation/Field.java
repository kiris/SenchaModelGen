package localhost.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.SOURCE)
public @interface Field {
    enum Required {
        DEFAULT,
        FALSE,
        TRUE
    }

    String name() default "";

    String type() default "";

    Required required() default Required.DEFAULT;

    boolean exclude() default false;
}
