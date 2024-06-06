package org.example.utils;

import jakarta.annotation.Nullable;
import org.example.annotation.Bean;
import org.example.annotation.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Author pw7563
 * @Date 2024/6/4 16:49
 * usage
 */
public class ClassUtils {

    /**
     * 递归查找类上面的注解
     *
     * @param target
     * @param annoClass
     * @param <A>
     * @return
     */
    public static <A extends Annotation> A findAnnotation(Class<?> target, Class<A> annoClass) {
        A annotation = target.getAnnotation(annoClass);
        for (Annotation anno : target.getAnnotations()) {
            Class<? extends Annotation> annoType = anno.annotationType();
            if (!annoType.getPackageName().equals("java.lang.annotation")) {
                A found = findAnnotation(annoType, annoClass);
                if (found != null) {
                    if (annotation != null) {
                        throw new RuntimeException("注解重复");
                    }
                    annotation = found;
                }
            }
        }
        return annotation;
    }


    /**
     *
     * @param annotations
     * @param annoClass
     * @return
     * @param <A>
     */
    @Nullable
    public static <A extends Annotation> A getAnnotaion(Annotation[] annotations, Class<A> annoClass) {
        for (Annotation anno : annotations) {
            if (annoClass.isInstance(anno)) {
                return (A) anno;
            }
        }
        return null;
    }


    /**
     * 获取到方法上的bean的类型
     *
     * @param method
     * @param method
     * @return
     * @Bean public Hello hello(){
     * return new Hello();
     * }
     */
    public static String getBeanName(Method method) {
        Bean bean = method.getAnnotation(Bean.class);
        String name = bean.value();
        if (name.isEmpty()) {
            name = method.getName();
        }
        return name;
    }

    public static String getBeanName(Class<?> clazz) {
        String name = "";
        // 查找@Component
        Component component = clazz.getAnnotation(Component.class);
        if (component != null) {
            name = component.value();
        } else {
            // 如果当前类上没有@Component注解，则继续在其他注解中查找
            Annotation[] annotations = clazz.getAnnotations();
            for (Annotation anno : annotations) {
                Component annotation = findAnnotation(anno.annotationType(), Component.class);
                if (annotation != null) {
                    try {
                        name = (String) annotation.annotationType().getMethod("value").invoke(annotation);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        if (name.isEmpty()) {
            name = clazz.getSimpleName();
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }


    /**
     * 查找类上面，标记了指定注解的方法，并返回
     * @param clazz
     * @param annoClass
     * @return
     */
    @Nullable
    public static Method findAnnotationMethod(Class<?> clazz, Class<? extends Annotation> annoClass) {
        List<Method> ms = Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(annoClass)).map(m -> {
            if (m.getParameterCount() != 0) {
                throw new RuntimeException("注解方法参数数量必须为0");
            }
            return m;
        }).collect(Collectors.toList());
        if (ms.isEmpty()){
            return null;
        }
        if(ms.size() == 1){
            return ms.get(0);
        }
        throw new RuntimeException("注解方法数量必须为1");

    }

    public static Method getNameMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("没找到对应的无参方法");
        }
    }


}
