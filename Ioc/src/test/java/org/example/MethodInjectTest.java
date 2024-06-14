package org.example;

import java.lang.reflect.Method;

/**
 * @Author pw7563
 * @Date 2024/6/14 14:12
 * usage
 */

public class MethodInjectTest {
    private Method method;
    private Object target;

    public void setMethod(Method method, Object target) {
        this.method = method;
        this.target = target;
    }

    public void executeMethod() {
        try {
            method.invoke(target);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            // 创建MethodProvider的实例
            MethodProvider methodProvider = new MethodProvider();
            // 获取MethodProvider的myMethod方法对象
            Method method = MethodProvider.class.getMethod("myMethod");


            // 创建MethodInjectTest的实例
            MethodInjectTest methodInjectTest = new MethodInjectTest();
            // 将方法对象和MethodProvider实例注入到MethodInjectTest中
            methodInjectTest.setMethod(method, methodProvider);
            // 执行方法
            methodInjectTest.executeMethod();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }
}

class MethodProvider {
    public void myMethod() {
        System.out.println("My method is called!");
    }
}

