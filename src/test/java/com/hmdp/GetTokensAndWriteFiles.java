package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import javax.annotation.Resource;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.*;

@SpringBootTest
public class GetTokensAndWriteFiles {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void getTokensAndWriteFiles() throws IOException {
        //查询用户信息 限制1000个
        List<User> users = userService.lambdaQuery().last("limit 1000").list();
        FileWriter fr = null;
        try {
            //文件位置
            fr = new FileWriter("D:\\Desktop\\jmeter_test\\tokens.txt");
            //遍历每一个用户，并生成token
            for (User user : users) {
                //保存用户信息到 Redis 中
                //随机生成 token 作为登录令牌
                String token = UUID.randomUUID().toString(true);
                //将 user 对象 转化为 hashmap 存储
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                //字段名 字段值的类型都改成 String
                //userDTO有Long id 需要字段值转换，通过setFieldValueEditor配置
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,
                        new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
                //存储
                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                //设置 token 有效期 comment out it temporarily
//                stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
                //将Redis中token写入文件中
                fr.append(token);
                fr.append("\n");
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            fr.close();
        }
    }
}
