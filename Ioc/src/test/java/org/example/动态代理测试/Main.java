package org.example.动态代理测试;

/**
 * @Author pw7563
 * @Date 2024/6/19 16:14
 * usage
 */
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class Main {
    public static void main(String[] args) {
        // 创建目标对象
        MyService target = new MyServiceImpl();

        // 创建 InvocationHandler
        InvocationHandler handler = new MyInvocationHandler(target);

        // 创建代理对象
        MyService proxy = (MyService) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                handler
        );

        // 使用代理对象
//        proxy.performTask();

        proxy.t();
    }
}
