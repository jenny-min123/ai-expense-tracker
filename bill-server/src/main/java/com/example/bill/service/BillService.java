package com.example.bill.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.example.bill.entity.Bill;

import java.time.YearMonth;
import java.util.Map;

/**
 * 账单业务接口
 */
public interface BillService extends IService<Bill> {

    /**
     * 分页查询账单
     *
     * @param page       页码
     * @param size       每页条数
     * @param keyword    备注/分类名称模糊匹配，可为空
     * @param categoryId 分类 ID 精确匹配，可为空
     */
    IPage<Bill> pageBills(long page, long size, String keyword, Long categoryId);

    /**
     * 统计总金额、分类汇总、月度汇总
     */
    Map<String, Object> stat();

    /**
     * 按月统计收入与支出
     *
     * @param startMonth 开始月份
     * @param endMonth   结束月份
     */
    Map<String, Object> monthlyStat(YearMonth startMonth, YearMonth endMonth);
}
