package org.example.web;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.context.ApplicationContext;
import org.example.context.BeanDefinition;
import org.example.context.ConfigurableApplicationContext;
import org.example.io.PropertyResolver;
import org.example.utils.ClassUtils;
import org.example.web.annotation.*;
import org.example.web.exception.ServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.ldap.Control;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @Author pw7563
 * @Date 2024/7/1 14:53
 * usage
 */
public class DispatcherServlet extends HttpServlet {

    Logger logger = LoggerFactory.getLogger(getClass());

    ApplicationContext applicationContext;

    ViewResolver viewResolver;

    List<Dispatcher> getDispatchers = new ArrayList<>();

    List<Dispatcher> postDispathcers = new ArrayList<>();

    public DispatcherServlet(ApplicationContext applicationContext, PropertyResolver propertyResolver){
        this.applicationContext = applicationContext;
        this.viewResolver = this.applicationContext.getBean(viewResolver.getClass());
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        logger.info("init{}.",getClass().getName());
        ConfigurableApplicationContext configurableApplicationContext = (ConfigurableApplicationContext) this.applicationContext;
        List<BeanDefinition> beanDefinitions = configurableApplicationContext.findBeanDefinitions(Object.class);
        for(BeanDefinition def:beanDefinitions){
            Class<?> beanClass = def.getBeanClass();
            Object bean = def.getRequiredInstance();
            Controller controller = beanClass.getAnnotation(Controller.class);
            RestController restController = beanClass.getAnnotation(RestController.class);
            if(controller != null && restController != null){
                throw new ServletException("Controller and RestController can not be used at the same time."+beanClass.getName());
            }
            if(controller != null){

            }



        }


    }

    @Override
    public void destroy() {
        this.applicationContext.close();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.info("{} {}",req.getMethod(),req.getRequestURI());

        PrintWriter writer = resp.getWriter();
        writer.write("Hello World!");
        writer.flush();

    }

    static class Dispatcher{

        final static Result NOT_PROCESSED = new Result(false, null);

        Logger logger = LoggerFactory.getLogger(getClass());

        boolean isRest;

        boolean isResponseBody;

        boolean isVoid;

        Pattern urlPattern;

        Object controller;

        Method handlerMethod;

        Param[] methodParameters;



    }

    static class Param{
        String name;

        ParamType paramType;

        Class<?> classType;

        String defaultValue;

        public Param(String httpMethod, Method method, Parameter parameter, Annotation[] annotations) throws ServletException {
            PathVariable pv = ClassUtils.getAnnotaion(annotations, PathVariable.class);
            RequestParam rq = ClassUtils.getAnnotaion(annotations, RequestParam.class);
            RequestBody rb = ClassUtils.getAnnotaion(annotations, RequestBody.class);
            int total = (pv == null ? 0 : 1) + (rq == null ? 0 : 1) + (rb == null ? 0 : 1);
            if(total > 1){
                throw new ServletException("Only one annotation can be used in a parameter." + method);
            }

            this.classType = parameter.getType();

            if(pv != null){
                this.name = pv.value();
                this.paramType = ParamType.PATH_VARIABLE;
            }else if(rq != null){
                this.name = rq.value();
                this.defaultValue = rq.defaultValue();
                this.paramType = ParamType.REQUEST_PARAM;
            }else if(rb != null){
                this.paramType = ParamType.REQUEST_BODY;
            }else{
                this.paramType = ParamType.SERVLET_VARIABLE;
                if(this.classType != HttpServletRequest.class && this.classType != HttpServletResponse.class &&
                this.classType != HttpSession.class && this.classType != ServletContext.class){
                    throw new ServerErrorException("请给参数标记注解，不支持的参数类型 " + classType + " at method " + method);
                }
            }
        }

        @Override
        public String toString() {
            return "Param{" +
                    "name='" + name + '\'' +
                    ", paramType=" + paramType +
                    ", classType=" + classType +
                    ", defaultValue='" + defaultValue + '\'' +
                    '}';
        }
    }

    static enum ParamType {
        PATH_VARIABLE, REQUEST_PARAM, REQUEST_BODY, SERVLET_VARIABLE;
    }


    static record Result(boolean processed, Object returnObject) {
    }

}
