package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Transactional
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;


    /**
     * 秒杀卷抢购
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        // select Voucher
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // time is valid
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now()) || seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("TIME not allowed");
        }

        // stock is enough
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("STOCK not enough");
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1").eq("voucher_id", voucherId).update();
        if (!success) {
            return Result.fail("STOCK not enough");
        }

        // create order
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("seckillOrder");
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .userId(userId)
                .voucherId(voucherId)
                .id(orderId)
                .build();

        save(voucherOrder);

        return Result.ok(orderId);
    }
}
