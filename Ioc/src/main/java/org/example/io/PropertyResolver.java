package org.example.io;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;
import java.util.function.Function;

/**
 * @Author pw7563
 * @Date 2024/5/29 10:39
 * usage
 */
public class PropertyResolver {

    Logger logger = LoggerFactory.getLogger(getClass());

    Map<String, String> properties = new HashMap<>();

    Map<Class<?>, Function<String, Object>> converters = new HashMap<>();

    public PropertyResolver(Properties props) {
        this.properties.putAll(System.getenv());
        Set<String> names = props.stringPropertyNames();
        for (String name : names) {
            this.properties.put(name,props.getProperty(name));
        }
        if(logger.isDebugEnabled()){
            List<String> keys = new ArrayList<>(this.properties.keySet());
            Collections.sort(keys);
            for(String key:keys){
                logger.debug("property:{},value:{}",key,this.properties.get(key));
            }
        }

        // register converters:
        converters.put(String.class, s -> s);
        converters.put(boolean.class, s -> Boolean.parseBoolean(s));
        converters.put(Boolean.class, s -> Boolean.valueOf(s));

        converters.put(byte.class, s -> Byte.parseByte(s));
        converters.put(Byte.class, s -> Byte.valueOf(s));

        converters.put(short.class, s -> Short.parseShort(s));
        converters.put(Short.class, s -> Short.valueOf(s));

        converters.put(int.class, s -> Integer.parseInt(s));
        converters.put(Integer.class, s -> Integer.valueOf(s));

        converters.put(long.class, s -> Long.parseLong(s));
        converters.put(Long.class, s -> Long.valueOf(s));

        converters.put(float.class, s -> Float.parseFloat(s));
        converters.put(Float.class, s -> Float.valueOf(s));

        converters.put(double.class, s -> Double.parseDouble(s));
        converters.put(Double.class, s -> Double.valueOf(s));

        converters.put(LocalDate.class, s -> LocalDate.parse(s));
        converters.put(LocalTime.class, s -> LocalTime.parse(s));
        converters.put(LocalDateTime.class, s -> LocalDateTime.parse(s));
        converters.put(ZonedDateTime.class, s -> ZonedDateTime.parse(s));
        converters.put(Duration.class, s -> Duration.parse(s));
        converters.put(ZoneId.class, s -> ZoneId.of(s));
    }

    public boolean containsProperty(String key){
        return this.properties.containsKey(key);
    }

    /**
     * 递归调用
     * @param key
     * @return
     */
    @Nullable
    public String getProperty(String key){
        PropertyExpr keyExpr = parsePropertyExpr(key);
        if (keyExpr != null){
            if (keyExpr.defaultValue() != null){
                return getProperty(keyExpr.key(),keyExpr.defaultValue());
            } else{
                return getRequiredProperty(keyExpr.key());
            }
        }
        String value = this.properties.get(key);
        if(value != null){
            return parseValue(value);
        }
        return value;
    }

    String parseValue(String value){
        PropertyExpr expr = parsePropertyExpr(value);
        if(expr == null){
            return value;
        }
        if(expr.defaultValue() != null){
            return getProperty(expr.key(), expr.defaultValue());
        }else{
            return getRequiredProperty(expr.key());
        }

    }

    public String getProperty(String key, String defaultValue){
        String value = getProperty(key);
        return value!= null? value : defaultValue;
    }

    public String getRequiredProperty(String key){
        String value = getProperty(key);
        return Objects.requireNonNull(value,"Property '" + key + "' is required");
    }



    /**
     * ${key:defaultValue}
     * 解析属性表达式，解析出key和默认值
     * @param key
     * @return
     */
    PropertyExpr parsePropertyExpr(String key){
        if(key.startsWith("${") && key.endsWith("}")){
            int n = key.indexOf(":");
            if(n == -1){
                // 说明没有默认值
                String k = notEmpty(key.substring(2,key.length()-1));
                return new PropertyExpr(k,null);
            } else{
                // 有默认值的情况
                String k = notEmpty(key.substring(2,n));
                return new PropertyExpr(k,key.substring(n+1,key.length()-1));
            }
        }
        return null;
    }

    String notEmpty(String key){
        if(key.isEmpty()){
            throw new IllegalArgumentException("key is empty, key:" + key);
        }
        return key;
    }



}
record PropertyExpr(String key, String defaultValue) {

}