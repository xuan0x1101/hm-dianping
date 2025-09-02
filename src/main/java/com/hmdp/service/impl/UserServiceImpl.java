package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_SESSION;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {

        // check valid phone
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Phone Invalid");
        }

        // get code
        String code = RandomUtil.randomNumbers(6);
        // save code
        session.setAttribute("code", code);

        // send code
        log.debug("👌 CODE SENT SUCCESS: {}", code);

        return Result.ok();
    }


    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        // check valid phone
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Phone Invalid");
        }

        // check code
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.toString().equals(code)) {
            return Result.fail("Code Dispatch");
        }

        // get user
        User user = query().eq("phone", phone).one();
        // if user exist
        if (user == null) {
            user = createUserByPhone(phone);
        }
        // save user in session
        session.setAttribute(USER_SESSION, user);

        return Result.ok();
    }

    private User createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("用户" + phone);
        // save user
        save(user);
        return user;
    }
}
