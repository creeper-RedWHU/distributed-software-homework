package com.example.demo.mapper;

import com.example.demo.model.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    Product selectById(@Param("id") Long id);

    List<Product> selectList(@Param("offset") Integer offset, @Param("limit") Integer limit);

    int insert(Product product);

    int updateStock(@Param("id") Long id, @Param("stock") Integer stock);

    int decrStock(@Param("id") Long id);

    int incrStock(@Param("id") Long id);
}
