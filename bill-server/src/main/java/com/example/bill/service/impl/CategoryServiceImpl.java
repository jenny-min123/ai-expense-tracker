package com.example.bill.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.bill.entity.Category;
import com.example.bill.mapper.CategoryMapper;
import com.example.bill.service.CategoryService;
import org.springframework.stereotype.Service;

/**
 * 分类业务实现
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
}
