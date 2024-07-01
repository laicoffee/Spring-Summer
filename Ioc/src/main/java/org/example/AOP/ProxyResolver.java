package org.example.AOP;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;

/**
 * @Author pw7563
 * @Date 2024/6/21 15:06
 * usage
 */
public class ProxyResolver {

    // 创建ByteBuddy实例
    ByteBuddy byteBuddy = new ByteBuddy();


    private static ProxyResolver INSTANCE = null;

    public static ProxyResolver getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ProxyResolver();
        }
        return INSTANCE;
    }


    /**
     * 传入需要被代理的bean，以及代理处理器
     * 返回代理后的实例
     * @param bean
     * @param handler
     * @return
     * @param <T>
     */
    public <T> T createProxy(T bean, InvocationHandler handler){
        // 目标Bean的Class类型
        Class<?> targetClass = bean.getClass();
        // 动态创建Proxy类
        Class<?> proxyClas = this.byteBuddy
                // 子类用默认无参数构造方法
                .subclass(targetClass, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
                // 拦截所有public方法
                .method(ElementMatchers.isPublic())
                // 新的拦截器实例
                .intercept(InvocationHandlerAdapter.of((proxy, method, args) -> {
                    return handler.invoke(bean, method, args);
                }))
                .make()
                .load(targetClass.getClassLoader())
                .getLoaded();

        Object proxy;

        try{
            proxy = proxyClas.getConstructor().newInstance();
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return (T) proxy;
    }

}
