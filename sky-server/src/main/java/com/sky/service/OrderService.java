package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;


public interface OrderService {


    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    /**
     * 查询订单
     * @param id
     * @return
     */
    OrderVO getOrderDetailById(Long id);

    /**
     * 催单
     * @param id
     */
    void reminder(Long id);

    /**
     * 历史订单查询
     * @return
     */
    PageResult getHistoryOrders(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult getOrders(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    void confirmOrder(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 各个状态的订单数量统计
     * @return
     */
    OrderStatisticsVO getOrdersStatistics();

    /**
     * 派单
     * @param id
     */
    void deliveryOrder(Long id);

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    OrderVO getOrderDetails(Long id);

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    void rejectOrder(OrdersRejectionDTO ordersRejectionDTO);

    /**
     * 完成订单
     * @param id
     */
    void completeOrder(Long id);

    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    void cancelOrder(OrdersCancelDTO ordersCancelDTO);
}
