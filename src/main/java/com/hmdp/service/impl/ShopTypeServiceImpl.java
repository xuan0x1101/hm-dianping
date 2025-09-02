package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST_TTL;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryTypeList() {
        String redisKey = CACHE_SHOP_LIST;

        // 1. 从Redis Hash中获取所有数据
        List<ShopType> shopTypeList = new ArrayList<>();
        Long hashSize = stringRedisTemplate.opsForHash().size(redisKey);

        if (hashSize > 0) {
            // 获取所有值并反序列化
            List<Object> values = stringRedisTemplate.opsForHash().values(redisKey);
            for (Object value : values) {
                ShopType shopType = JSONUtil.toBean((String) value, ShopType.class);
                shopTypeList.add(shopType);
            }
            return shopTypeList.stream()
                    .sorted((s1, s2) -> s1.getSort().compareTo(s2.getSort()))
                    .collect(Collectors.toList());
        }

        // 2. Redis中没有数据，从数据库查询
        shopTypeList = query().orderByAsc("sort").list();

        // 3. 将数据存入Redis Hash结构
        if (!shopTypeList.isEmpty()) {
            for (ShopType shopType : shopTypeList) {
                stringRedisTemplate.opsForHash().put(
                        redisKey,
                        String.valueOf(shopType.getId()),
                        JSONUtil.toJsonStr(shopType)
                );
            }
            stringRedisTemplate.expire(redisKey, CACHE_SHOP_LIST_TTL, TimeUnit.DAYS);
        }

        return shopTypeList;
    }
}
