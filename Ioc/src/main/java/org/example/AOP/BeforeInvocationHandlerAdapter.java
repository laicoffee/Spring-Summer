package org.example.AOP;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @Author pw7563
 * @Date 2024/6/26 14:47
 * usage
 */
public abstract class BeforeInvocationHandlerAdapter implements InvocationHandler {

    public abstract void before(Object proxy, Method method, Object[] args);

    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        before(proxy, method, args);
        return method.invoke(proxy, args);
    }
}