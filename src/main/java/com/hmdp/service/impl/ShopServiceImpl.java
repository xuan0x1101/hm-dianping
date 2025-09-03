package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;


    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithLogicalExpire(id);

        if (shop == null) {
            return Result.fail("SHOP not exist");
        }

        return Result.ok(shop);
    }

    // 逻辑过期解决缓存击穿
    private Shop queryWithLogicalExpire(Long id) {
        String redisKey = CACHE_SHOP_KEY + id;
        // get from redis
        String shopJson = stringRedisTemplate.opsForValue().get(redisKey);
        // if shop not exist
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        // hit, judge if expire
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // not expire
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }

        // expire
        /*
         redis rebuild
        */
        // get lock
        String lockKey = LOCK_SHOP + id;
        boolean gotLock = tryLock(lockKey);
        if (gotLock) {
            // success, retry redis
            shopJson = stringRedisTemplate.opsForValue().get(redisKey);
            // if shop exist
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
            }

            // fail, rebuild
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    savaShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }

        return shop;
    }

    // 解决缓存穿透
    private Shop queryWithPassThrough(Long id) {
        String redisKey = CACHE_SHOP_KEY + id;
        // get from redis
        String shopJson = stringRedisTemplate.opsForValue().get(redisKey);
        // if shop exist
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // shop not exist in DB
        // 解决缓存穿透
        if (shopJson != null) {
            return null;
        }

        // get from db
        Shop shop = getById(id);
        if (shop == null) {
            // 解决缓存穿透
            stringRedisTemplate.opsForValue().set(redisKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // save in redis
        stringRedisTemplate.opsForValue().set(redisKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }

    // 解决缓存击穿
    private Shop queryWithMutex(Long id) {
        String redisKey = CACHE_SHOP_KEY + id;
        // get from redis
        String shopJson = stringRedisTemplate.opsForValue().get(redisKey);
        // if shop exist
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // shop not exist in DB
        // 解决缓存穿透
        if (shopJson != null) {
            return null;
        }

        /*
         redis rebuild
        */
        // get lock
        String lockKey = LOCK_SHOP + id;
        Shop shop = null;
        try {
            boolean gotLock = tryLock(lockKey);
            if (!gotLock) {
                // fail, sleep retry
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // success, retry redis
            shopJson = stringRedisTemplate.opsForValue().get(redisKey);
            // if shop exist
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            // not in redis, get from db
            shop = getById(id);
            if (shop == null) {
                // 解决缓存穿透
                stringRedisTemplate.opsForValue().set(redisKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // save in redis
            stringRedisTemplate.opsForValue().set(redisKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // unlock
            unLock(lockKey);
        }

        return shop;
    }


    /**
     * 更新商铺信息
     *
     * @param shop 商铺数据
     * @return 无
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("SHOP id is NULL");
        }

        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }


    // 获取线程锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放线程锁
    private void unLock(String key) {
        Boolean flag = stringRedisTemplate.delete(key);
    }

    // 逻辑过期时间
    private void savaShop2Redis(Long id, Long expireSec) {
        Shop shop = getById(id);

        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSec));
        redisData.setData(shop);

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

}
