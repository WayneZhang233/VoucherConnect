package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.nio.file.CopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    public StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        1. validate the phone number
        if (RegexUtils.isPhoneInvalid(phone)) {
            //        2. if invalid, return an error message
            return Result.fail("the phone number is invalid");
        }

//        3. if valid, generate a verification
        String code = RandomUtil.randomNumbers(6);

//        4. save it to redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

//        5， send it(not implemented)
        log.debug("verification code sent! {}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        1. validate the phone number
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //        if invalid, return an error message
            return Result.fail("the phone number is invalid");
        }
//        2. validate the verification code
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)) {
            //        3. if inconsistent, return an error message
            return Result.fail("the code is invalid");
        }

//        4. if consistent, query user by phone number
        User user = query().eq("phone", phone).one();
//        5. check if the user exists
        if (user == null) {
            //        6. if not exists, create a new user and save it to the database
            user = createUserWithPhone(phone);
        }
//        7. save user information to redis
//        7.1 randomly generate a login token
        String token = UUID.randomUUID().toString(true);
//        7.2 convert the User Object to Hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
//        7.3 save it to redis
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
//        7.4 set expiration period
//        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
//        8. return token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
//        1. get the current logged-in user
        Long userId = UserHolder.getUser().getId();
//        2. get the date
        LocalDateTime now = LocalDateTime.now();
//        3. concatenate key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
//        4. get which day of the month today is
        int dayOfMonth = now.getDayOfMonth();
//        5. write it to redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
//        1. get the current logged-in user
        Long userId = UserHolder.getUser().getId();
//        2. get the date
        LocalDateTime now = LocalDateTime.now();
//        3. concatenate key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
//        4. get which day of the month today is
        int dayOfMonth = now.getDayOfMonth();
//        5. get all check-in records for this month, a decimal number
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result == null || result.size() == 0) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num == null || num == 0) {
            return Result.ok(0);
        }
//        iterate over this number in a loop
        int count = 0;
        while(true){
            //        perform a bitwise AND operation between this number and 1
            if((num & 1) == 0){
                break;
            }else{
                count++;
            }
            //        right shift this number by one
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
//        1. create
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
//        2. save
        save(user);
        return user;
    }
}
