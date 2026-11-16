package com.example.bill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.bill.entity.Bill;
import com.example.bill.mapper.BillMapper;
import com.example.bill.service.BillService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 账单业务实现
 */
@Service
public class BillServiceImpl extends ServiceImpl<BillMapper, Bill> implements BillService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public IPage<Bill> pageBills(long page, long size, String keyword, Long categoryId) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Bill::getNote, keyword)
                    .or().like(Bill::getCategoryName, keyword));
        }
        if (categoryId != null) {
            wrapper.eq(Bill::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(Bill::getBillDate).orderByDesc(Bill::getId);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Map<String, Object> stat() {
        List<Bill> bills = list();
        BigDecimal total = bills.stream()
                .map(Bill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Double> byCategory = bills.stream()
                .collect(Collectors.groupingBy(Bill::getCategoryName,
                        Collectors.summingDouble(b -> b.getAmount().doubleValue())));

        Map<String, Double> byMonth = bills.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getBillDate().format(MONTH_FORMATTER),
                        TreeMap::new,
                        Collectors.summingDouble(b -> b.getAmount().doubleValue())));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("byCategory", byCategory);
        result.put("byMonth", byMonth);
        return result;
    }
}
