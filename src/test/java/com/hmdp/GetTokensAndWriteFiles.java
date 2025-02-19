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
        // Query user information, limit to 1000
        List<User> users = userService.lambdaQuery().last("limit 1000").list();
        FileWriter fr = null;
        try {
            // File location
            fr = new FileWriter("D:\\Desktop\\jmeter_test\\tokens.txt");
            // Iterate through each user and generate tokens
            for (User user : users) {
                // Save user information to Redis
                // Randomly generate token as a login token
                String token = UUID.randomUUID().toString(true);
                // Convert user object to hashmap for storage
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                // Change both field names and field values to String type
                // userDTO has a Long id that needs value conversion, configured via setFieldValueEditor
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,
                        new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
                // Store the data
                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                // Set token expiration time (commented out temporarily)
//            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
                // Write the token stored in Redis into the file
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
