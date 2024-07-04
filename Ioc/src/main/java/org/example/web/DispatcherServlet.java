package org.example.web;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
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
import org.example.web.exception.ServerWebInputException;
import org.example.web.utils.JsonUtils;
import org.example.web.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.ldap.Control;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author pw7563
 * @Date 2024/7/1 14:53
 * usage
 */
public class DispatcherServlet extends HttpServlet {

    /**
     因为IOC容器的存在，所有controller也会变成一个个bean，mvc会在启动时，扫描所有的controller的bean，并且做如下的处理
     1.获取到类下的所有方法，并根据方法上的注解，将其添加到对应的dispatcher列表中
     2.再将每个方法中的参数以及参数上的注解，封装成Param对象，并将其添加到methodParameters列表中
     3.在doService中，根据请求的url匹配对应的dispather，并调用其process方法，处理请求
     4.返回处理结果

     dispatcher是一个mapping处理的对象，其中包括urlPattern，controller，handlerMethod，methodParameters等信息。

     Param是一个参数的封装类，包括参数类型，参数名称，参数默认值，参数注解类型等信息。

     Result是一个处理结果的封装类，包括是否处理成功，处理结果等信息。
     */
    Logger logger = LoggerFactory.getLogger(getClass());

    ApplicationContext applicationContext;

    ViewResolver viewResolver;

    List<Dispatcher> getDispatchers = new ArrayList<>();

    List<Dispatcher> postDispathcers = new ArrayList<>();

    public DispatcherServlet(ApplicationContext applicationContext, PropertyResolver propertyResolver) {
        this.applicationContext = applicationContext;
        this.viewResolver = this.applicationContext.getBean(viewResolver.getClass());
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        logger.info("init{}.", getClass().getName());
        ConfigurableApplicationContext configurableApplicationContext = (ConfigurableApplicationContext) this.applicationContext;
        List<BeanDefinition> beanDefinitions = configurableApplicationContext.findBeanDefinitions(Object.class);
        for (BeanDefinition def : beanDefinitions) {
            Class<?> beanClass = def.getBeanClass();
            Object bean = def.getRequiredInstance();
            Controller controller = beanClass.getAnnotation(Controller.class);
            RestController restController = beanClass.getAnnotation(RestController.class);
            if (controller != null && restController != null) {
                throw new ServletException("Controller and RestController can not be used at the same time." + beanClass.getName());
            }
            if (controller != null) {
                addController(false, def.getName(), bean);
            }else{
                addController(true, def.getName(), bean);
            }
        }
    }


    void addController(boolean isRest, String name, Object instance) throws ServletException {
        logger.info("add {} controller", name);
        addMethod(isRest, name, instance, instance.getClass());
    }

    void addMethod(boolean isRest, String name, Object instance, Class<?> type) throws ServletException {

        for (Method m : type.getDeclaredMethods()) {
            GetMapping get = m.getAnnotation(GetMapping.class);
            if (get != null) {
                checkMethod(m);
                getDispatchers.add(new Dispatcher("GET", isRest, instance, m, get.value()));
            }
            PostMapping post = m.getAnnotation(PostMapping.class);
            if (post != null) {
                checkMethod(m);
                postDispathcers.add(new Dispatcher("POST", isRest, instance, m, post.value()));
            }
        }
        Class<?> superClass = type.getSuperclass();
        if (superClass != null) {
            addMethod(isRest, name, instance, superClass);
        }


    }

    void checkMethod(Method method) throws ServletException {
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers)) {
            throw new ServletException("Static method can not be used as a handler method." + method);
        }
        method.setAccessible(true);
    }


    @Override
    public void destroy() {
        this.applicationContext.close();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.info("{} {}", req.getMethod(), req.getRequestURI());
        String url = req.getRequestURI();


    }


    /**
     * 将指定的url分配给dispathcer进行处理
     *
     * @param req
     * @param resp
     * @param dispatchers
     * @throws ServletException
     * @throws IOException
     */
    void doService(HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers) throws ServletException, IOException {
        String url = req.getRequestURI();
        try {
            doService(url, req, resp, dispatchers);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void doService(String url, HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers) throws Exception {
        for (Dispatcher dispatcher : dispatchers){
            Result result = dispatcher.process(url, req, resp);
            if(result.processed()){
                Object r = result.returnObject();
                if(dispatcher.isRest){
                    if(!resp.isCommitted()){
                        resp.setContentType("application/json;charset=UTF-8");
                    }
                    if(dispatcher.isResponseBody){
                        if(r instanceof String s){
                            PrintWriter pw = resp.getWriter();
                            pw.write(s);
                            pw.flush();
                        }else if(r instanceof byte[] data){
                            ServletOutputStream output = resp.getOutputStream();
                            output.write(data);
                            output.flush();
                        }else{
                            throw new ServerErrorException("Unsupported return type: " + r.getClass());
                        }
                    }
                }else{
                    if( !resp.isCommitted()){
                        resp.setContentType("text/html");
                    }
                    if( r instanceof String s){
                        if (dispatcher.isResponseBody) {
                            // send as response body:
                            PrintWriter pw = resp.getWriter();
                            pw.write(s);
                            pw.flush();
                        } else if (s.startsWith("redirect:")) {
                            // send redirect:
                            resp.sendRedirect(s.substring(9));
                        } else {
                            // error:
                            throw new ServletException("Unable to process String result when handle url: " + url);
                        }
                    } else if (r instanceof byte[] data) {
                        if (dispatcher.isResponseBody) {
                            // send as response body:
                            ServletOutputStream output = resp.getOutputStream();
                            output.write(data);
                            output.flush();
                        } else {
                            // error:
                            throw new ServletException("Unable to process byte[] result when handle url: " + url);
                        }
                    }else if(r instanceof ModelAndView mv){
                        String view = mv.getViewName();
                        if (view.startsWith("redirect:")) {
                            // send redirect:
                            resp.sendRedirect(view.substring(9));
                        } else {
                            this.viewResolver.render(view, mv.getModel(), req, resp);
                        }
                    } else if (!dispatcher.isVoid && r != null) {
                        // error:
                        throw new ServletException("Unable to process " + r.getClass().getName() + " result when handle url: " + url);
                    }
                }
            }
            return;
        }

    }


    static class Dispatcher {

        final static Result NOT_PROCESSED = new Result(false, null);

        Logger logger = LoggerFactory.getLogger(getClass());

        boolean isRest;

        boolean isResponseBody;

        boolean isVoid;

        Pattern urlPattern;

        Object controller;

        Method handlerMethod;

        Param[] methodParameters;


        public Dispatcher(String httpMethod, boolean isRest, Object controller, Method method, String urlPattern) throws ServletException {
            this.isRest = isRest;
            this.isResponseBody = method.getAnnotation(ResponseBody.class) != null;
            this.isVoid = method.getReturnType() == void.class;
            this.urlPattern = Pattern.compile(urlPattern);
            this.controller = controller;
            this.handlerMethod = method;
            Parameter[] param = method.getParameters();
            Annotation[][] paramAnnos = method.getParameterAnnotations();
            this.methodParameters = new Param[param.length];

            for (int i = 0; i < param.length; i++) {
                methodParameters[i] = new Param(httpMethod, method, param[i], paramAnnos[i]);
            }
        }

        Result process(String url, HttpServletRequest req, HttpServletResponse resp) throws Exception {
            Matcher matcher = urlPattern.matcher(url);
            if (matcher.matches()) {
                Object[] arguments = new Object[this.methodParameters.length];
                for (int i = 0; i < arguments.length; i++) {
                    Param param = methodParameters[i];
                    arguments[i] = switch (param.paramType) {
                        case PATH_VARIABLE -> {
                            try {
                                String s = matcher.group(param.name);
                                yield convertToType(param.classType, s);
                            } catch (IllegalArgumentException e) {
                                throw new ServerWebInputException("Path variable '" + param.name + "' not found.");
                            }
                        }
                        case REQUEST_BODY -> {
                            BufferedReader reader = req.getReader();
                            yield JsonUtils.readJson(reader, param.classType);
                        }
                        case REQUEST_PARAM -> {
                            String s = getOrDefault(req, param.name, param.defaultValue);
                            yield convertToType(param.classType, s);
                        }
                        case SERVLET_VARIABLE -> {
                            Class<?> classType = param.classType;
                            if (classType == HttpServletRequest.class) {
                                yield req;
                            } else if (classType == HttpServletResponse.class) {
                                yield req;
                            } else if (classType == HttpSession.class) {
                                yield req.getSession();
                            } else if (classType == ServletContext.class) {
                                yield req.getServletContext();
                            } else {
                                throw new ServerErrorException("Could not determine argument type: " + classType);
                            }
                        }
                    };
                }

                Object result = null;
                try{
                    result = handlerMethod.invoke(controller, arguments);
                }catch (Exception e){
                    throw new ServerErrorException(e.getMessage());
                }
                return new Result(true, result);
            }
            return NOT_PROCESSED;
        }

        String getOrDefault(HttpServletRequest request, String name, String defaultValue) {
            String s = request.getParameter(name);
            if (s == null) {
                if (WebUtils.DEFAULT_PARAM_VALUE.equals(defaultValue)) {
                    throw new ServerWebInputException("Request parameter '" + name + "' not found.");
                }
                return defaultValue;
            }
            return s;
        }

        Object convertToType(Class<?> classType, String s) {
            if (classType == String.class) {
                return s;
            } else if (classType == boolean.class || classType == Boolean.class) {
                return Boolean.valueOf(s);
            } else if (classType == int.class || classType == Integer.class) {
                return Integer.valueOf(s);
            } else if (classType == long.class || classType == Long.class) {
                return Long.valueOf(s);
            } else if (classType == byte.class || classType == Byte.class) {
                return Byte.valueOf(s);
            } else if (classType == short.class || classType == Short.class) {
                return Short.valueOf(s);
            } else if (classType == float.class || classType == Float.class) {
                return Float.valueOf(s);
            } else if (classType == double.class || classType == Double.class) {
                return Double.valueOf(s);
            } else {
                throw new ServerErrorException("Could not determine argument type: " + classType);
            }
        }


//        String getOrDefault()


    }

    static class Param {
        String name;

        ParamType paramType;

        Class<?> classType;

        String defaultValue;

        /**
         * 对参数进行解析，并设置参数类型、参数名、参数默认值
         *
         * @param httpMethod
         * @param method
         * @param parameter
         * @param annotations
         * @throws ServletException
         */
        public Param(String httpMethod, Method method, Parameter parameter, Annotation[] annotations) throws ServletException {
            PathVariable pv = ClassUtils.getAnnotaion(annotations, PathVariable.class);
            RequestParam rq = ClassUtils.getAnnotaion(annotations, RequestParam.class);
            RequestBody rb = ClassUtils.getAnnotaion(annotations, RequestBody.class);
            int total = (pv == null ? 0 : 1) + (rq == null ? 0 : 1) + (rb == null ? 0 : 1);
            if (total > 1) {
                throw new ServletException("Only one annotation can be used in a parameter." + method);
            }

            this.classType = parameter.getType();

            if (pv != null) {
                this.name = pv.value();
                this.paramType = ParamType.PATH_VARIABLE;
            } else if (rq != null) {
                this.name = rq.value();
                this.defaultValue = rq.defaultValue();
                this.paramType = ParamType.REQUEST_PARAM;
            } else if (rb != null) {
                this.paramType = ParamType.REQUEST_BODY;
            } else {
                this.paramType = ParamType.SERVLET_VARIABLE;
                if (this.classType != HttpServletRequest.class && this.classType != HttpServletResponse.class &&
                        this.classType != HttpSession.class && this.classType != ServletContext.class) {
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
