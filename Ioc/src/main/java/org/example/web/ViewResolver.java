package org.example.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

/**
 * @Author pw7563
 * @Date 2024/7/2 10:10
 * usage
 */
public interface ViewResolver {

    void init();

    void render(String viewName, Map<String, Object> model, HttpServletRequest req, HttpServletResponse resp) throws Exception;

}
