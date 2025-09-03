package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
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
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);

        if (shop == null) {
            return Result.fail("SHOP not exist");
        }

        return Result.ok(shop);
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
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean gotLock = tryLock(lockKey);
            if(!gotLock) {
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


}
