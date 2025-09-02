package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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

        String redisKey = CACHE_SHOP_KEY + id;
        // get from redis
        String shopJson = stringRedisTemplate.opsForValue().get(redisKey);
        // if shop exist
        if (shopJson != null) {
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }

        // get from db
        Shop shop = getById(id);
        if (shop == null) {
            return Result.fail("SHOP not exist!");
        }

        // save in redis
        stringRedisTemplate.opsForValue().set(redisKey, JSONUtil.toJsonStr(shop));

        return Result.ok(shop);
    }
}
