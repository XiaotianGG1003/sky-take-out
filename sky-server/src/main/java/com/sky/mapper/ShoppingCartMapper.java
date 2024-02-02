package com.sky.mapper;


import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    /**
     * 查询购物车
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);


    /**
     * 插入购物车
     * @param cart
     */
    void insert(ShoppingCart cart);

    /**
     * 更新购物车
     * @param cart
     */
    void update(ShoppingCart cart);
}
