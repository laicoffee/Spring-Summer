package org.example.aop测试;


import org.example.AOP.ProxyResolver;

public class ProxyResolverTest {

    public void testProxyResovler() {
        OriginBean origin = new OriginBean();
        origin.name = "Bob";


        // create proxy:
        OriginBean proxy = new ProxyResolver().createProxy(origin, new PoliteInvocationHandler());

        // Proxy类名,类似OriginBean$ByteBuddy$9hQwRy3T:
        System.out.println(proxy.getClass().getName());
        System.out.println(proxy.hello());

    }

    public static void main(String[] args) {
        ProxyResolverTest proxyResolverTest = new ProxyResolverTest();
        proxyResolverTest.testProxyResovler();
    }

}
