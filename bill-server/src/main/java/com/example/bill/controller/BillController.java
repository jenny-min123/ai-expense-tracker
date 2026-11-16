package com.example.bill.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.bill.common.Result;
import com.example.bill.entity.Bill;
import com.example.bill.entity.Category;
import com.example.bill.service.BillService;
import com.example.bill.service.CategoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 账单接口
 */
@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillService billService;
    private final CategoryService categoryService;

    public BillController(BillService billService, CategoryService categoryService) {
        this.billService = billService;
        this.categoryService = categoryService;
    }

    /**
     * 分页查询账单
     */
    @GetMapping
    public Result<IPage<Bill>> page(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Long categoryId) {
        return Result.ok(billService.pageBills(page, size, keyword, categoryId));
    }

    /**
     * 新增账单
     */
    @PostMapping
    public Result<Bill> add(@RequestBody Bill bill) {
        fillDefaults(bill);
        String error = resolveCategory(bill);
        if (error != null) {
            return Result.fail(error);
        }
        billService.save(bill);
        return Result.ok(bill);
    }

    /**
     * 更新账单
     */
    @PutMapping("/{id}")
    public Result<Bill> update(@PathVariable Long id, @RequestBody Bill bill) {
        bill.setId(id);
        String error = resolveCategory(bill);
        if (error != null) {
            return Result.fail(error);
        }
        return billService.updateById(bill) ? Result.ok(bill) : Result.fail("账单不存在");
    }

    /**
     * 删除账单
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return billService.removeById(id) ? Result.ok() : Result.fail("账单不存在");
    }

    /**
     * 统计接口
     */
    @GetMapping("/stat")
    public Result<Map<String, Object>> stat() {
        return Result.ok(billService.stat());
    }

    /**
     * 填充默认值
     */
    private void fillDefaults(Bill bill) {
        if (bill.getBillDate() == null) {
            bill.setBillDate(LocalDate.now());
        }
        if (bill.getSourceType() == null || bill.getSourceType().isBlank()) {
            bill.setSourceType("MANUAL");
        }
        if (bill.getBillType() == null || bill.getBillType().isBlank()) {
            bill.setBillType("EXPENSE");
        }
    }

    /**
     * 前端只传分类名称时，根据名称补齐分类 ID
     *
     * @return 错误信息，成功时返回 null
     */
    private String resolveCategory(Bill bill) {
        if (bill.getCategoryName() == null || bill.getCategoryName().isBlank()) {
            return null;
        }
        Category category = categoryService.getOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, bill.getCategoryName()), false);
        if (category == null) {
            return "分类不存在：" + bill.getCategoryName();
        }
        bill.setCategoryId(category.getId());
        return null;
    }
}
