package com.yyl.loginservice.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.yyl.loginservice.bean.LoginTicket;
import com.yyl.loginservice.bean.User;
import com.yyl.loginservice.mapper.UserLoginMapper;
import com.yyl.loginservice.service.UserLoginService;
import com.yyl.loginservice.utils.CommonConstant;
import com.yyl.loginservice.utils.CommonUtil;
import com.yyl.loginservice.utils.MailClient;
import com.yyl.loginservice.utils.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.yyl.loginservice.utils.CommonConstant.AUTHORITY_ADMIN;
import static com.yyl.loginservice.utils.CommonConstant.AUTHORITY_MODERATOR;
import static com.yyl.loginservice.utils.CommonConstant.AUTHORITY_USER;


@DubboService(timeout = 3000)
@Component
public class UserLoginServiceImpl implements UserLoginService {

    @Autowired
    private UserLoginMapper userMapper;

    @Autowired
    private MailClient mailClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${community.path.domain}")
    private String domain;

//    @Value("${server.servlet.context-path}")
//    private String contextPath;
    @Override
    public User findUserById(int userId) {
        User user = getUserFromCache(userId);
        if (user == null){
            user = initUserCache(userId);
        }
        return user;
    }

    @Override
    public Map<String, Object> register(User user,String contextPath) {
        System.out.println("registerImpl---------");
        Map<String, Object> map = new HashMap<>();
        if (user == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        if (StringUtils.isBlank(user.getUsername())) {
            map.put("usernameMsg", "用户名不能为空");
            return map;
        }
        if (StringUtils.isBlank(user.getPassword())) {
            map.put("passwordMsg", "密码不能为空");
            return map;
        }
        if (StringUtils.isBlank(user.getEmail())) {
            map.put("emailMsg", "邮箱不能为空");
            return map;
        }
        // 验证账号
        User u = userMapper.selectByName(user.getUsername());
        if (u != null) {
            map.put("usernameMsg", "该账号已存在");
            return map;
        }
        // 验证邮箱
        u = userMapper.selectByEmail(user.getEmail());
        if (u != null) {
            map.put("emailMsg", "该邮箱已被注册");
        }
        // 设置注册用户信息
        user.setSalt(CommonUtil.generateUUID().substring(0, 5));// 设置salt
        user.setPassword(CommonUtil.md5(user.getPassword() + user.getSalt()));//设置加密后的密码
        user.setType(0);// 普通用户
        user.setStatus(0);//未激活
        user.setActivationCode(CommonUtil.generateUUID());//激活码
        user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        user.setCreateTime(new Date());
        //System.out.println(user);
        userMapper.insertUser(user);

        // 发送激活邮件
        Context context = new Context();
        context.setVariable("email", user.getEmail());
        // 链接格式 http://localhost:8080/community/activation/{id}/code
        String activeUrl = domain + contextPath + "/activation/" + user.getId() + "/" + user.getActivationCode();
        context.setVariable("activeUrl", activeUrl);
        String content = templateEngine.process("/mail/activation", context);
        mailClient.sendMail(user.getEmail(), "激活账号", content);
        return map;
    }

    @Override
    public int activation(int userId, String code) {
        User user = userMapper.selectById(userId);
        if (user.getStatus() == 1) {
            return CommonConstant.ACTIVATION_REPEAT;
        } else if (user.getActivationCode().equals(code)) {
            userMapper.updateStatus(userId, 1);
            //  数据发生改变，清理缓存
            clearUserCache(userId);
            return CommonConstant.ACTIVATION_SUCCESS;
        } else {
            return CommonConstant.ACTIVATION_FAILURE;
        }
    }

    @Override
    public Map<String, Object> login(String username, String password, int expiredSeconds) {
        Map<String, Object> map = new HashMap<>();

        //空值处理
        if (StringUtils.isBlank(username)) {
            map.put("usernameMsg", "账号不能为空");
            return map;
        }
        if (StringUtils.isBlank(username)) {
            map.put("passwordMsg", "密码不能为空");
            return map;
        }

        //验证账号,使用用户名登录
        User user = userMapper.selectByName(username);
        if (user == null) {
            // 使用email作为账户登录
            user = userMapper.selectByEmail(username);
            if (user == null) {
                map.put("usernameMsg", "该账号不存在");
                return map;
            }
        }
        if (user.getStatus() == 0) {
            map.put("usernameMsg", "该账号未激活");
            return map;
        }

        //验证密码
        password = CommonUtil.md5(password + user.getSalt());
        if (!user.getPassword().equals(password)) {
            map.put("passwordMsg", "密码不正确!");
            return map;
        }

        //生成登录凭证
        LoginTicket loginTicket = new LoginTicket();
        loginTicket.setUserId(user.getId());
        loginTicket.setTicket(CommonUtil.generateUUID());
        loginTicket.setStatus(0);
        loginTicket.setExpired(new Date(System.currentTimeMillis() + expiredSeconds * 1000));

        // 获取loginTicket的RedisKey
        String loginTicketKey = RedisKeyUtil.getLoginTicketKey(loginTicket.getTicket());
        redisTemplate.opsForValue().set(loginTicketKey,loginTicket);
        map.put("ticket",loginTicket.getTicket());
        return map;
    }

    @Override
    public void logout(String ticket) {
        String loginTicketKey = RedisKeyUtil.getLoginTicketKey(ticket);
        LoginTicket loginTicket = (LoginTicket) redisTemplate.opsForValue().get(loginTicketKey);
        loginTicket.setStatus(1);
        redisTemplate.opsForValue().set(loginTicketKey,loginTicket);
    }

    @Override
    public LoginTicket findLoginTicket(String ticket) {
        String loginTicketKey = RedisKeyUtil.getLoginTicketKey(ticket);
        return (LoginTicket) redisTemplate.opsForValue().get(loginTicketKey);
    }

    @Override
    public int updateHeaderUrl(int userId, String headerUrl) {
        int rows = userMapper.updateHeader(userId,headerUrl);
        // 更新头像，数据发生改变
        clearUserCache(userId);
        return rows;
    }

    @Override
    public int updatePassword(int userId, String password) {
        // 旧密码验证成功，设置新密码以及salt
        String salt = CommonUtil.generateUUID().substring(0, 5);
        //设置加密后的密码
        password = CommonUtil.md5(password + salt);
        // 清除缓存
        clearUserCache(userId);
        return userMapper.updatePassword(userId,password,salt);
    }

    @Override
    public User findUserByName(String username) {
        return userMapper.selectByName(username);
    }

    @Override
    public User getUserFromCache(int userId) {
        String userKey = RedisKeyUtil.getUserKey(userId);
        return (User) redisTemplate.opsForValue().get(userKey);
    }

    @Override
    public User initUserCache(int userId) {
        User user = userMapper.selectById(userId);
        String userKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.opsForValue().set(userKey,user,3600, TimeUnit.SECONDS);
        return user;
    }

    @Override
    public void clearUserCache(int userId) {
        String userKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.delete(userKey);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(int userId) {
        User user = this.findUserById(userId);
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new GrantedAuthority() {
            @Override
            public String getAuthority() {
                switch (user.getType()){
                    case 1:
                        return AUTHORITY_ADMIN;
                    case 2:
                        return AUTHORITY_MODERATOR;
                    default:
                        return AUTHORITY_USER;
                }
            }
        });
        return list;
    }

    @Override
    public User findUserByEmail(String email) {
        return userMapper.selectByEmail(email);
    }
}
