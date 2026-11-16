package com.example.bill.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bill.common.Result;
import com.example.bill.entity.Category;
import com.example.bill.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类接口
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 查询全部分类，按排序值升序
     */
    @GetMapping
    public Result<List<Category>> list() {
        List<Category> categories = categoryService.list(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
        return Result.ok(categories);
    }
}
