package org.example.context;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.annotation.*;
import org.example.io.PropertyResolver;
import org.example.io.ResourceResolver;
import org.example.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

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

        this.beans.values().stream()
                .filter(this::isConfigurationDefinition).sorted().map(def->{
                    createBeanAsEarlySingleton(def);
                    return def.getName();
                }).collect(Collectors.toList());





    }

    /**
     * 创建一个bean，然后使用beanPostProcessor进行处理
     * 这是用于创建@Configuration标注的注解类的过程，总体是根据构造方法或者工厂方法进行进行创建，
     * 其实主要目的就是拿到需要初始化的属性参数集合，并判断每个属性上是否有@Value和@Autowired注解，若有则对他们进行注入
     * 最后再创建bean的实例
     * @param def
     * @return
     */
    public Object createBeanAsEarlySingleton(BeanDefinition def){
        if(!this.creatingBeanNames.add(def.getName())){
            throw new RuntimeException("bean is created: " + def.getName());
        }

        Executable createFn = null;
        if(def.getFactoryName() == null){
            createFn = def.getConstructor();
        }else{
            createFn = def.getFactoryMethod();
        }

        // 创建参数:
        final Parameter[] parameters = createFn.getParameters();
        final Annotation[][] parametesAnnos = createFn.getParameterAnnotations();
        Object[] args = new Object[parameters.length];
        for(int i=0; i < parameters.length; i++){
            final Parameter param = parameters[i];
            final Annotation[] paramAnnos = parametesAnnos[i];
            Value value = ClassUtils.getAnnotaion(paramAnnos, Value.class);
            Autowired autowired = ClassUtils.getAnnotaion(paramAnnos, Autowired.class);

            boolean isConfiguration = isConfigurationDefinition(def);
            if (isConfiguration && autowired != null){
                throw new RuntimeException("@Autowired 不能在配置类上使用");
            }

            // BeanPostProcessor不能依赖其他Bean，不允许使用@Autowired创建:
            final boolean isBeanPostProcessor = isBeanPostProcessorDefinition(def);
            if (isBeanPostProcessor && autowired != null) {
                throw new RuntimeException(
                        String.format("Cannot specify @Autowired when create BeanPostProcessor '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }

            // 参数需要@Value或@Autowired两者之一:
            if (value != null && autowired != null) {
                throw new RuntimeException(
                        String.format("Cannot specify both @Autowired and @Value when create bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }
            if (value == null && autowired == null) {
                throw new RuntimeException(
                        String.format("Must specify @Autowired or @Value when create bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }

            if(value != null && autowired != null){
                throw new RuntimeException("value 和 autowired两个注解不能同时存在");
            }
            if(value == null && autowired == null){
                throw new RuntimeException("value 和 autowired两个注解必须存在一个");
            }

            Class<?> type = param.getType();
            if (value != null){
                // 当参数是@Value时，说明应该从配置文件中获取值:
                args[i] = this.propertyResolver.getRequiredProperty(value.value(), type);
            } else{
                // 当参数是@Autowired时，说明应该从容器中获取值:
                String name = autowired.name();
                boolean required = autowired.value();
                // 查找beandefinition
                BeanDefinition dependsOnDef = name.isEmpty() ? findBeanDefinition(type) : findBeanDefinition(name, type);
                // 判断required是否为true
                if ( required && dependsOnDef == null){
                    throw new RuntimeException("required bean not found: " + type.getName());
                }
                if(dependsOnDef != null){
                    // 获取依赖的bean
                    Object instance = dependsOnDef.getInstance();
                    if(instance == null && !isConfiguration && !isBeanPostProcessor){
                        // 说明当前类还没有初始化，需要先初始化
                        instance  = createBeanAsEarlySingleton(dependsOnDef);
                    }
                    args[i] = i;
                }else {
                    args[i] = null;
                }
            }

            // 创建bean实例
            Object instance = null;
            if(def.getFactoryName() == null){
                // 用构造方法创建
                try{
                     instance = def.getConstructor().newInstance(args);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }else{
                Object configInstance = getBean(def.getFactoryName());
                try {
                    def.getFactoryMethod().invoke(configInstance,args);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
            def.setInstance(instance);

        }
        return def.getInstance();

    }

    public <T> T getBean(String name){
        BeanDefinition def = this.beans.get(name);
        if(def == null){
            throw new RuntimeException("bean not found: " + name);
        }
        return (T) def.getRequiredInstance();

    }


    public BeanDefinition findBeanDefinition(String name){
        return this.beans.get(name);
    }

    public BeanDefinition findBeanDefinition(String name, Class<?> type){
        BeanDefinition def = findBeanDefinition(name);
        if ( def == null){
            return null;
        }
        if(!type.isAssignableFrom(def.getBeanClass())){
            throw new RuntimeException("bean type not match: " + name + " " + type.getName());
        }
        return def;

    }

    public BeanDefinition findBeanDefinition(Class<?> type){
        List<BeanDefinition> defs = findBeanDefinitions(type);
        if(defs.isEmpty()){
            return null;
        }
        if(defs.size() == 1){
            return defs.get(0);
        }
        // 多个bean，需要找到有@Primary的
        List<BeanDefinition> primaryDefs = defs.stream().filter(def -> def.isPrimary()).collect(Collectors.toList());
        if (primaryDefs.size() == 1)
            return primaryDefs.get(0);
        throw new RuntimeException("more than one primary bean found: " + type.getName());

    }

    public List<BeanDefinition> findBeanDefinitions(Class<?> type){
        return this.beans.values().stream()
                .filter(def->type.isAssignableFrom(def.getBeanClass())).collect(Collectors.toList());
    }


    boolean isBeanPostProcessorDefinition(BeanDefinition def){
        return BeanPostProcessor.class.isAssignableFrom(def.getBeanClass());
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
