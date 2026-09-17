package com.example.yin.config;

import com.example.yin.common.R;
import com.example.yin.util.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * JWT 认证拦截器
 * - 公开接口直接放行（不校验令牌，避免过期 token 影响静态资源/列表页）
 * - 用户操作接口（写操作/个人数据）要求有效 user 令牌
 * - /admin/** 及管理端写接口要求有效 admin 令牌
 * 校验通过后将 userId / username / role 放入 request attribute 供接口使用。
 */
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    /** 免鉴权的公开接口前缀（放行逻辑优先于 USER/ADMIN 规则，避免误伤登录等接口） */
    private static final List<String> PUBLIC_PREFIX = Arrays.asList(
            "/user/login/status",
            "/user/email/status",
            "/user/add",
            "/user/sendVerificationCode",
            "/user/resetPassword",
            "/admin/login/status"
    );

    /** 需要用户登录的接口前缀 */
    private static final List<String> USER_PREFIX = Arrays.asList(
            "/user/me",
            "/user/update",
            "/user/updatePassword",
            "/user/avatar/update",
            "/user/delete",
            "/user/yinbi",
            "/collection/add",
            "/collection/delete",
            "/collection/status",
            "/comment/add",
            "/comment/like",
            "/rankList/add",
            "/userSupport/insert",
            "/userSupport/delete",
            "/ticket/buy",
            "/ticket/my-orders",
            "/ticket/order"
    );

    /** 需要管理员权限的接口前缀 */
    private static final List<String> ADMIN_PREFIX = Arrays.asList(
            "/singer/add",
            "/singer/update",
            "/singer/delete",
            "/singer/avatar/update",
            "/song/add",
            "/song/update",
            "/song/delete",
            "/song/url/update",
            "/song/img/update",
            "/song/lrc/update",
            "/songList/add",
            "/songList/update",
            "/songList/delete",
            "/songList/img/update",
            "/listSong/add",
            "/listSong/update",
            "/listSong/delete",
            "/comment/delete",
            "/verify/confirm"
    );

    /** 需要管理员权限的接口（精确匹配，防止 "/user" 前缀误伤 /user/me 等用户接口） */
    private static final List<String> ADMIN_EXACT = Arrays.asList(
            "/user"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (match(path, PUBLIC_PREFIX)) {
            return true;
        }
        boolean needUser = match(path, USER_PREFIX);
        boolean needAdmin = path.startsWith("/admin/") || match(path, ADMIN_PREFIX) || ADMIN_EXACT.contains(path);

        // 公开接口：放行且不校验令牌
        if (!needUser && !needAdmin) {
            return true;
        }

        String token = resolveToken(request);
        if (StringUtils.isEmpty(token)) {
            return reject(response, 401, "未登录或登录已过期");
        }
        Claims claims;
        try {
            claims = jwtUtils.parseToken(token);
        } catch (Exception e) {
            return reject(response, 401, "登录已过期，请重新登录");
        }
        String role = jwtUtils.getRole(claims);
        if (needAdmin && !"admin".equals(role)) {
            return reject(response, 403, "无管理员权限");
        }
        if (needUser && !"admin".equals(role) && !"user".equals(role)) {
            return reject(response, 401, "未登录或登录已过期");
        }

        request.setAttribute("userId", jwtUtils.getUserId(claims));
        request.setAttribute("username", jwtUtils.getUsername(claims));
        request.setAttribute("role", role);
        return true;
    }

    /** 从请求中提取令牌：优先 Authorization 头，其次 query 参数 token（兼容 el-upload 等非 axios 上传） */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.isEmpty(header)) {
            return header.startsWith("Bearer ") ? header.substring(7) : header;
        }
        String param = request.getParameter("token");
        if (!StringUtils.isEmpty(param)) {
            return param;
        }
        return request.getHeader("token");
    }

    private boolean match(String path, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        R r = R.error(message);
        r.setCode(code);
        response.getWriter().write(new ObjectMapper().writeValueAsString(r));
        return false;
    }
}