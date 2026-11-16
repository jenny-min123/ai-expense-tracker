package com.example.bill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.bill.common.MonthlyStat;
import com.example.bill.entity.Bill;
import com.example.bill.mapper.BillMapper;
import com.example.bill.service.BillService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    @Override
    public Map<String, Object> monthlyStat(YearMonth startMonth, YearMonth endMonth) {
        Map<YearMonth, MonthlyStat> months = initMonths(startMonth, endMonth);
        List<Bill> bills = list(new LambdaQueryWrapper<Bill>()
                .ge(Bill::getBillDate, startMonth.atDay(1))
                .le(Bill::getBillDate, endMonth.atEndOfMonth()));
        bills.forEach(bill -> accumulate(months, bill));

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (MonthlyStat stat : months.values()) {
            stat.setNet(stat.getIncome().subtract(stat.getExpense()));
            totalIncome = totalIncome.add(stat.getIncome());
            totalExpense = totalExpense.add(stat.getExpense());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startMonth", startMonth.format(MONTH_FORMATTER));
        result.put("endMonth", endMonth.format(MONTH_FORMATTER));
        result.put("totalIncome", totalIncome);
        result.put("totalExpense", totalExpense);
        result.put("net", totalIncome.subtract(totalExpense));
        result.put("months", new ArrayList<>(months.values()));
        return result;
    }

    /**
     * 初始化区间内每个月的零值统计
     */
    private Map<YearMonth, MonthlyStat> initMonths(YearMonth startMonth, YearMonth endMonth) {
        Map<YearMonth, MonthlyStat> months = new TreeMap<>();
        for (YearMonth month = startMonth; !month.isAfter(endMonth); month = month.plusMonths(1)) {
            MonthlyStat stat = new MonthlyStat();
            stat.setMonth(month.format(MONTH_FORMATTER));
            months.put(month, stat);
        }
        return months;
    }

    /**
     * 按收支类型累加到对应月份
     */
    private void accumulate(Map<YearMonth, MonthlyStat> months, Bill bill) {
        MonthlyStat stat = months.get(YearMonth.from(bill.getBillDate()));
        if (stat == null) {
            return;
        }
        if ("INCOME".equals(bill.getBillType())) {
            stat.setIncome(stat.getIncome().add(bill.getAmount()));
        } else {
            stat.setExpense(stat.getExpense().add(bill.getAmount()));
        }
    }
}
