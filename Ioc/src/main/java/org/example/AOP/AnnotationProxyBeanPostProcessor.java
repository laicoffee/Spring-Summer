package org.example.AOP;

import org.example.annotation.Autowired;
import org.example.context.ApplicationContext;
import org.example.context.BeanDefinition;
import org.example.context.BeanPostProcessor;
import org.example.context.ConfigurableApplicationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author pw7563
 * @Date 2024/6/26 10:43
 * usage
 */
public abstract class AnnotationProxyBeanPostProcessor<A extends Annotation> implements BeanPostProcessor {

    Map<String, Object> originBeans = new HashMap<>();

    Class<A> annotationClass;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ConfigurableApplicationContext configurableApplicationContext;

    public AnnotationProxyBeanPostProcessor(){
        this.annotationClass = getParameterizedType();
    }

    public Object postProcessBeforeInitalization(Object bean, String beanName){
        Class<?> beanClass = bean.getClass();

        A anno = beanClass.getAnnotation(annotationClass);
        if(anno != null){
            String handlerName;
            try{
                handlerName = (String) anno.annotationType().getMethod("value").invoke(anno);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            Object proxy = createProxy(beanClass, bean, handlerName);
            originBeans.put(beanName, bean);
            return proxy;
        }else{
            return bean;
        }
    }

    Object createProxy(Class<?> beanClass, Object bean, String handlerName){
        BeanDefinition def = configurableApplicationContext.findBeanDefinition(handlerName);

        if(def == null){
            throw new RuntimeException("Handler not found: " + handlerName);
        }

        Object handlerBean = def.getInstance();
        if(handlerBean == null){
            handlerBean = configurableApplicationContext.createBeanAsEarlySingleton(def);
        }
        if(handlerBean instanceof InvocationHandler handler){
            return ProxyResolver.getInstance().createProxy(handlerBean,handler);
        }else{
            throw new RuntimeException("Handler is not type of InvocationHandler");
        }

    }


    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName) {
        Object origin = this.originBeans.get(beanName);
        return origin != null ? origin : bean;
    }

    private Class<A> getParameterizedType(){
        Type type = getClass().getGenericSuperclass();
        if (!(type instanceof ParameterizedType)){
            throw new RuntimeException("AnnotationProxyBeanPostProcessor must be parameterized");
        }
        ParameterizedType pt = (ParameterizedType) type;
        Type[] types = pt.getActualTypeArguments();
        if( types.length != 1){
            throw new RuntimeException("AnnotationProxyBeanPostProcessor must be parameterized with one type");
        }
        Type r = types[0];

        if(!(r instanceof Class<?>)){
            throw new RuntimeException("AnnotationProxyBeanPostProcessor must be parameterized with a class");
        }
        return (Class<A>) r;



    }


}
