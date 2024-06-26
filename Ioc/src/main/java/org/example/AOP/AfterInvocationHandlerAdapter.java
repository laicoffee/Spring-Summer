package org.example.AOP;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @Author pw7563
 * @Date 2024/6/26 14:49
 * usage
 */
public abstract class AfterInvocationHandlerAdapter implements InvocationHandler {

    public abstract Object after(Object proxy, Object returnValue, Method method, Object[] args);

    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object ret = method.invoke(proxy, args);
        return after(proxy, ret, method, args);
    }
}

