package org.example.web.utils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.example.context.ApplicationContextUtils;
import org.example.io.PropertyResolver;
import org.example.utils.ClassPathUtils;
import org.example.utils.YamlUtils;
import org.example.web.DispatcherServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Properties;

/**
 * @Author pw7563
 * @Date 2024/7/1 15:20
 * usage
 */
public class WebUtils {
    static final Logger logger = LoggerFactory.getLogger(WebUtils.class);

    static final String CONFIG_APP_YAML = "/application.yml";
    static final String CONFIG_APP_PROP = "/application.properties";



    public static void registerDispatcherServlet(ServletContext servletContext, PropertyResolver propertyResolver){
        DispatcherServlet dispatcherServlet = new DispatcherServlet(ApplicationContextUtils.getRequiredApplicationContext(), propertyResolver);
        ServletRegistration.Dynamic dispatcherReg = servletContext.addServlet("dispatcherServlet", dispatcherServlet);

        dispatcherReg.addMapping("/");
        dispatcherReg.setLoadOnStartup(0);


    }





    /**
     * 从yaml中加载配置
     * @return
     */
    public static PropertyResolver createPropertyResolver() {
        final Properties props = new Properties();
        try {
            Map<String, Object> yamlMap = YamlUtils.loadYamlAsPlainMap(CONFIG_APP_YAML);
            for (String key : yamlMap.keySet()) {
                Object value = yamlMap.get(key);
                if (value instanceof String strValue) {
                    props.put(key, strValue);
                }
            }
        } catch (Exception e) {
            if (e.getCause() instanceof FileNotFoundException) {
                // try load application.properties:
                ClassPathUtils.readInputStream(CONFIG_APP_PROP, (input) -> {
                    logger.info("load config: {}", CONFIG_APP_PROP);
                    props.load(input);
                    return true;
                });
            }
        }
        return new PropertyResolver(props);
    }


}
