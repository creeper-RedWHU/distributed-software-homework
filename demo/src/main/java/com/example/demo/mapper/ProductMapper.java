package com.example.demo.mapper;

import com.example.demo.model.entity.Product;
import com.example.demo.model.vo.SeckillActivityVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ProductMapper {

    @Select("SELECT * FROM t_product WHERE id = #{id}")
    Product selectById(Long id);

    @Select("SELECT * FROM t_product WHERE category_id = #{categoryId} AND status = 1 ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<Product> selectByCategory(@Param("categoryId") Long categoryId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    @Select("SELECT * FROM t_product WHERE status = 1 ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<Product> selectAll(@Param("offset") int offset, @Param("limit") int limit);

    @Insert("INSERT INTO t_product (name, description, price, image_url, category_id, status) " +
            "VALUES (#{name}, #{description}, #{price}, #{imageUrl}, #{categoryId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);

    @Update("UPDATE t_product SET name=#{name}, description=#{description}, price=#{price}, " +
            "image_url=#{imageUrl}, category_id=#{categoryId} WHERE id=#{id}")
    int update(Product product);

    @Update("UPDATE t_product SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
