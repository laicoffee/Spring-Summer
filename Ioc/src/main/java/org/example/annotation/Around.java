package org.example.annotation;

import java.lang.annotation.*;

/**
 * @Author pw7563
 * @Date 2024/6/26 10:36
 * usage
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Around {

    /**
     * Invocation handler bean name
     * 指定要注入的beanname
     * @return
     */
    String value();

}
