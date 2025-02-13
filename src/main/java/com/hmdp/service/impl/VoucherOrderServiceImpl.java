package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jni.Time;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

//    asynchronous ordering
//    thread pool
    public static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

//    start right after the current class is constructed
    @PostConstruct
    private void init(){
//        submit the task
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while(true){
                try {
                    //   1. get the order information from the message queue:
                    //   XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //   2. check if succeed
                    if(list == null || list.isEmpty()){
                        continue;
                    }
                    //   3. get the order from the list
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //   4. if succ, create a order
                    handleVoucherOrder(voucherOrder);
                    //   5. ack   SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("order processing failed", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while(true){
                try {
                    //   1. get the order information from pending-list:
                    //   XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //   2. check if succeed
                    if(list == null || list.isEmpty()){
                        break;
                    }
                    //   3. get the order from the list
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //   4. if succ, create a order
                    handleVoucherOrder(voucherOrder);
                    //   5. ack   SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("pending-list processing failed", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }
//    thread task
    /*private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while(true){
                try {
                    //   get the order information from the queue
                    VoucherOrder voucherOrder = orderTasks.take();
                    //   create a order
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("order processing failed", e);
                }
            }
        }
    }*/

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //        create a lock object
//        use Redisson lock instead of custom lock
//        actually the lock can be deleted (have already check with lua script, just in case)
        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        get the lock
        boolean success = lock.tryLock();
        if (!success) {
            log.error("duplicate order");
            return;
        }
        try {
//            1. get a proxy object
//            2. add a dependency (aspectj)
//            3. add a annotation to the startup class
            proxy.createVoucherOrder(voucherOrder);
        } finally {
//            release the lock
            lock.unlock();
        }
    }


    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //        order id
        long orderId = redisIdWorker.nextId("order");
//        1. execute lua script, check the user can buy the voucher
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
//        2. check if return 0 (success)
        if(r != 0){
            return Result.fail(r == 1 ? "out of stock" : "duplicate orders from the same user");
        }
        //            1. get a proxy object
//            2. add a dependency (aspectj)
//            3. add a annotation to the startup class
        proxy = (IVoucherOrderService) AopContext.currentProxy();

//        return order id
        return Result.ok(orderId);
    }

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
//        1. execute lua script, check the user can buy the voucher
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
//        2. check if return 0 (success)
        if(r != 0){
            return Result.fail(r == 1 ? "out of stock" : "duplicate orders from the same user");
        }
//        if 0, save the order information to the blocking queue
        //        create order (encapsulation)
        VoucherOrder voucherOrder = new VoucherOrder();
//        order id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
//        user id
        voucherOrder.setUserId(userId);
//        voucher id
        voucherOrder.setVoucherId(voucherId);
//        save it to the blocking queue
        orderTasks.add(voucherOrder);

        //            1. get a proxy object
//            2. add a dependency (aspectj)
//            3. add a annotation to the startup class
        proxy = (IVoucherOrderService) AopContext.currentProxy();

//        return order id
        return Result.ok(orderId);
    }*/

   /* @Override
    public Result seckillVoucher(Long voucherId) {
//        1. query voucher
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        2. check if flash sale started
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("the flash sale has not started yet!");
        }
//        3. check if flash sale stopped
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("the flash sale has ended!");
        }
//        4. check if it is in stock
        if (voucher.getStock() < 1) {
            return Result.fail("it is out of stock!");
        }

        Long userId = UserHolder.getUser().getId();

//        create a lock object
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        use Redisson lock instead of custom lock
        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        get the lock
        boolean success = lock.tryLock();
        if (!success) {
            return Result.fail("each user can only place one order!");
        }
        try {
//            1. get a proxy object
//            2. add a dependency (aspectj)
//            3. add a annotation to the startup class
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
//            release the lock
            lock.unlock();
        }
    }*/

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //        1 pax 1 order
        Long userId = voucherOrder.getUserId();
//        1. query order
        int count = query().eq("voucher_id", voucherOrder.getVoucherId()).eq("user_id", userId).count();
//        2. check if exists
        if (count > 0) {
            log.error("duplicate order");
            return;
        }

        //        5. reduce stock
        boolean isSuccessful = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();

        if (!isSuccessful) {
            log.error("out of stock");
            return;
        }

//        save it to db
        save(voucherOrder);
    }
}
