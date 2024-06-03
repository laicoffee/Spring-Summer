package org.example.context;

import jakarta.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * @Author pw7563
 * @Date 2024/6/3 15:48
 * usage
 */
public class BeanDefinition implements Comparable<BeanDefinition>{

    private final String name;

    private final Class<?> beanClass;

    private Object instance = null;

    private final Constructor<?> constructor;

    private final String factoryName;

    private final Method factoryMethod;

    private final int order;

    private final boolean primary;

    private boolean init = false;

    private String initMethodName;
    private String destroyMethodName;

    private Method initMethod;
    private Method destroyMethod;


    public BeanDefinition(String name, Class<?> beanClass, Constructor<?> constructor, int order, boolean primary, String initMethodName,
                          String destroyMethodName, Method initMethod, Method destroyMethod) {
        this.name = name;
        this.beanClass = beanClass;
        this.constructor = constructor;
        this.factoryName = null;
        this.factoryMethod = null;
        this.order = order;
        this.primary = primary;
        constructor.setAccessible(true);
        setInitAndDestroyMethod(initMethodName, destroyMethodName, initMethod, destroyMethod);
    }


    public BeanDefinition(String name, Class<?> beanClass, String factoryName, Method factoryMethod, int order, boolean primary, String initMethodName,
                          String destroyMethodName, Method initMethod, Method destroyMethod) {
        this.name = name;
        this.beanClass = beanClass;
        this.constructor = null;
        this.factoryName = factoryName;
        this.factoryMethod = factoryMethod;
        this.order = order;
        this.primary = primary;
        factoryMethod.setAccessible(true);
        setInitAndDestroyMethod(initMethodName, destroyMethodName, initMethod, destroyMethod);
    }

    private void setInitAndDestroyMethod(String initMethodName, String destroyMethodName, Method initMethod, Method destroyMethod) {
        this.initMethodName = initMethodName;
        this.destroyMethodName = destroyMethodName;
        if(initMethod != null){
            initMethod.setAccessible(true);
        }
        if(destroyMethod != null){
            destroyMethod.setAccessible(true);
        }

        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }


    @Nullable
    public Constructor<?> getConstructor() {
        return this.constructor;
    }

    @Nullable
    public String getFactoryName() {
        return this.factoryName;
    }

    @Nullable
    public Method getFactoryMethod() {
        return this.factoryMethod;
    }

    @Nullable
    public Method getInitMethod() {
        return this.initMethod;
    }

    @Nullable
    public Method getDestroyMethod() {
        return this.destroyMethod;
    }

    @Nullable
    public String getInitMethodName() {
        return this.initMethodName;
    }

    @Nullable
    public String getDestroyMethodName() {
        return this.destroyMethodName;
    }

    public String getName() {
        return this.name;
    }

    public Class<?> getBeanClass() {
        return this.beanClass;
    }

    @Nullable
    public Object getInstance() {
        return this.instance;
    }

    public Object getRequiredInstance() {
        if(this.instance == null){
            throw new RuntimeException("bean实例方法为空");
        }
        return this.instance;
    }

    public void setInstance(Object instance){
        Objects.requireNonNull(instance,"Bean instance is null");
        if (!this.beanClass.isAssignableFrom(instance.getClass())){
            throw new RuntimeException("Bean instance type is not compatible with bean class");
        }
        this.instance = instance;
    }


    public boolean isInit() {
        return this.init;
    }

    public void setInit() {
        this.init = true;
    }

    public boolean isPrimary() {
        return this.primary;
    }

    @Override
    public String toString() {
        return "BeanDefinition{" +
                "name='" + name + '\'' +
                ", beanClass=" + beanClass +
                ", instance=" + instance +
                ", constructor=" + constructor +
                ", factoryName='" + factoryName + '\'' +
                ", factoryMethod=" + factoryMethod +
                ", order=" + order +
                ", primary=" + primary +
                ", init=" + init +
                ", initMethodName='" + initMethodName + '\'' +
                ", destroyMethodName='" + destroyMethodName + '\'' +
                ", initMethod=" + initMethod +
                ", destroyMethod=" + destroyMethod +
                '}';
    }

    String getCreateDetail(){
        if (this.factoryMethod != null) {
            String params = String.join(", ", Arrays.stream(this.factoryMethod.getParameterTypes()).map(t -> t.getSimpleName()).toArray(String[]::new));
            return this.factoryMethod.getDeclaringClass().getSimpleName() + "." + this.factoryMethod.getName() + "(" + params + ")";
        }
        return null;
    }

    /**
     * 比较两个bean的优先级，如果优先级相等，则按照bean名称排序
     * @param o the object to be compared.
     * @return
     */
    @Override
    public int compareTo(BeanDefinition o) {
        int cmp = Integer.compare(this.order,o.order);
        if(cmp != 0){
            return cmp;
        }
        return this.name.compareTo(o.name);
    }
}
