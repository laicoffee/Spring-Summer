package org.example.context;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.annotation.*;
import org.example.io.PropertyResolver;
import org.example.io.ResourceResolver;
import org.example.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @Author pw7563
 * @Date 2024/6/5 14:10
 * usage
 */
public class AnnotationConfigApplicationContext {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final PropertyResolver propertyResolver;

    protected final Map<String, BeanDefinition> beans;

    private List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    private Set<String> creatingBeanNames;

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {
        this.propertyResolver = propertyResolver;

        final Set<String> beanClassNames = scanForClassNames(configClass);

        this.beans = createBeanDefinitions(beanClassNames);

        this.creatingBeanNames = new HashSet<>();

//        this.beans.values().stream()
//                .filter(this::isConfigurationDefinition).sorted().map(def->{
//
//                })



    }

    boolean isConfigurationDefinition(BeanDefinition def){
        return ClassUtils.findAnnotationMethod(def.getBeanClass(), Configuration.class) != null;
    }

    /**
     * 根据类名集合，创建BeanDefinition集合
     * @param classNameSet
     * @return
     */
    Map<String, BeanDefinition> createBeanDefinitions(Set<String> classNameSet){
        Map<String, BeanDefinition> defs = new HashMap<>();
        for(String className:classNameSet){
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            if(clazz.isAnnotation() || clazz.isInterface() || clazz.isEnum()){
                continue;
            }

            // 是否有@Component注解
            Component component = ClassUtils.findAnnotation(clazz, Component.class);
            if(component != null){
                int modifiers = clazz.getModifiers();
                if(Modifier.isAbstract(modifiers)){
                    throw new RuntimeException("abstract class not allowed: " + className);
                }
                if(Modifier.isPrivate(modifiers)){
                    throw new RuntimeException("private class not allowed: " + className);
                }

                String beanName = ClassUtils.getBeanName(clazz);
                var def = new BeanDefinition(beanName,clazz,getSuitableConstructor(clazz),getOrder(clazz)
                        , clazz.isAnnotationPresent(Primary.class),null,null
                ,ClassUtils.findAnnotationMethod(clazz, PostConstruct.class)
                ,ClassUtils.findAnnotationMethod(clazz, PreDestroy.class));
                addBeanDefinitions(defs,def);

                // 判断是否是配置类，配置类上不允许有前置处理
                Configuration configuration = ClassUtils.findAnnotation(clazz, Configuration.class);
                if(configuration != null){
                    if(BeanPostProcessor.class.isAssignableFrom(clazz)){
                        throw new RuntimeException("配置类上不允许有前置处理");
                    }
                    scanFactoryMethod(beanName,clazz,defs);
                }

            }
        }
        return defs;

    }

    /**
     * 扫描配置类上的@Bean注解，并创建BeanDefinition集合
     * 动作：
     * 1.获取类中的所有方法
     * 2.判断方法是否有@Bean注解
     * 3.如果有，则创建BeanDefinition，并添加到defs中
     * @param factoryBeanName
     * @param clazz
     * @param defs
     */
    void scanFactoryMethod(String factoryBeanName, Class<?> clazz, Map<String,BeanDefinition> defs){
        for(Method method:clazz.getMethods()){
            Bean bean = method.getAnnotation(Bean.class);
            if(bean != null){
                int mod = method.getModifiers();

                if (Modifier.isAbstract(mod)) {
                    throw new RuntimeException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be abstract.");
                }
                if (Modifier.isFinal(mod)) {
                    throw new RuntimeException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be final.");
                }
                if (Modifier.isPrivate(mod)) {
                    throw new RuntimeException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be private.");
                }
                Class<?> beanClass = method.getReturnType();
                if(beanClass.isPrimitive()){
                    throw new RuntimeException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return a primitive type.");
                }
                if(beanClass == void.class || beanClass == Void.class){
                    throw new RuntimeException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return void.");
                }

                var def = new BeanDefinition(ClassUtils.getBeanName(method), beanClass, factoryBeanName,method,getOrder(method),
                        method.isAnnotationPresent(Primary.class),
                        bean.initMethod().isEmpty() ? null : bean.initMethod(),
                        bean.destroyMethod().isEmpty() ? null : bean.destroyMethod(),
                        null,null);
                addBeanDefinitions(defs,def);

            }

        }
    }

    void addBeanDefinitions(Map<String, BeanDefinition> defs, BeanDefinition def){
        if(defs.put(def.getName(),def) != null){
            throw new RuntimeException("重复的bean");
        }
    }

    /**
     * 返回类上的@Order注解的值，如果没有，则返回Integer.MAX_VALUE
     * @param clazz
     * @return
     */
    int getOrder(Class<?> clazz){
        Order order = clazz.getAnnotation(Order.class);
        return order == null? Integer.MAX_VALUE : order.value();
    }

    int getOrder(Method method){

        Order order = method.getAnnotation(Order.class);
        return order == null? Integer.MAX_VALUE : order.value();

    }


    /**
     * 获取给定类的构造器，如果没有默认构造器，则抛出异常，只允许有一个构造器
     * @param clazz
     * @return
     */
    Constructor<?> getSuitableConstructor(Class<?> clazz){
        Constructor<?>[] cons = clazz.getConstructors();
        if (cons.length == 0){
            cons = clazz.getDeclaredConstructors();
            if(cons.length != 1){
                throw new RuntimeException("more than one constructor found: ");
            }
        }
        if(cons.length != 1){
            throw new RuntimeException("more than one constructor found: ");
        }
        return cons[0];

    }


    /**
     * 给定一个配置类，扫描这个配置类指定的所有包,并返回所有类名集合
     *
     * @param configClass
     * @return
     */
    protected Set<String> scanForClassNames(Class<?> configClass) {
        ComponentScan scan = ClassUtils.findAnnotation(configClass, ComponentScan.class);
        final String[] scanPackages = scan == null || scan.value().length == 0 ? new String[]{configClass.getPackage().getName()} : scan.value();

        Set<String> classNameSet = new HashSet<>();
        for (String pkg : scanPackages) {
            ResourceResolver resourceResolver = new ResourceResolver(pkg);
            List<String> classList = resourceResolver.scan(res -> {
                String name = res.name();
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                }
                return null;
            });
            classNameSet.addAll(classList);
        }

        // 查找@Import(Xyz.class):
        Import importConfig = configClass.getAnnotation(Import.class);
        if (importConfig != null) {
            for (Class<?> importConfigClass : importConfig.value()) {
                String importClassName = importConfigClass.getName();
                if (classNameSet.contains(importClassName)) {
                    logger.warn("ignore import: " + importClassName + " for it is already been scanned.");
                } else {
                    logger.debug("class found by import: {}", importClassName);
                    classNameSet.add(importClassName);
                }
            }
        }
        return classNameSet;

    }

}
