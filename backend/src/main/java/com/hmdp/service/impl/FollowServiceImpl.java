package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        if(isFollow){
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else{
//            delete from tb_follow where user_id = ? and follow_user_id = ?
//            QueryWrapper is used to build SQL query conditions, similar to the WHERE clause in SQL
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
//        select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        String key2 =  "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
//        get id from intersection
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
//        query user by id, and convert user to userDTO
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result getFollowingByUserId() {
        // 1. 从 UserHolder 获取当前登录用户
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("User not logged in");
        }
        Long userId = userDTO.getId();

        // 2. 查询 follow 表，找到该用户关注的所有用户 ID
        List<Long> followingIds = query()
                .select("follow_user_id")
                .eq("user_id", userId)
                .list()
                .stream()
                .map(Follow::getFollowUserId)
                .collect(Collectors.toList());

        // 3. 如果没有关注任何人，返回空列表
        if (followingIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 4. 查询 User 表获取详细用户信息，并转换为 UserDTO
        List<UserDTO> followingUsers = userService.listByIds(followingIds)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 5. 返回查询结果
        return Result.ok(followingUsers);
    }

    @Override
    public Result getFollowersByUserId() {
        // 1. 获取当前登录用户 ID
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("User not logged in");
        }
        Long userId = user.getId();

        // 2. 查询 follow 表，找到关注当前用户的所有用户 ID（粉丝 ID）
        List<Long> followerIds = query()
                .select("user_id")  // 这里是粉丝的 ID
                .eq("follow_user_id", userId)  // 关注当前用户
                .list()
                .stream()
                .map(Follow::getUserId) // 获取粉丝 ID
                .collect(Collectors.toList());

        // 3. 如果没有粉丝，返回空列表
        if (followerIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 4. 查询 User 表获取详细粉丝信息
        List<UserDTO> followers = userService.listByIds(followerIds)
                .stream()
                .map(userEntity -> BeanUtil.copyProperties(userEntity, UserDTO.class))
                .collect(Collectors.toList());

        // 5. 返回查询结果
        return Result.ok(followers);
    }
}
