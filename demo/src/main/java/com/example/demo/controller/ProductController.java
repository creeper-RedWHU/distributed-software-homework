package com.example.demo.controller;

import com.example.demo.common.ErrorCode;
import com.example.demo.common.Result;
import com.example.demo.model.entity.Product;
import com.example.demo.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Result<List<Product>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(productService.listByCategory(categoryId, page, size));
    }

    @GetMapping("/{productId}")
    public Result<Product> getById(@PathVariable Long productId) {
        Product product = productService.getById(productId);
        if (product == null) {
            return Result.fail(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return Result.success(product);
    }
}
