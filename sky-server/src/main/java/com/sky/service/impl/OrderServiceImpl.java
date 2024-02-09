package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    AddressBookMapper addressBookMapper;

    @Autowired
    OrderMapper orderMapper;

    @Autowired
    OrderDetailMapper orderDetailMapper;

    @Autowired
    ShoppingCartMapper shoppingCartMapper;

    @Autowired
    UserMapper userMapper;
    @Autowired
    WeChatPayUtil weChatPayUtil;

    @Autowired
    WebSocketServer webSocketServer;
    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //异常情况的处理（收货地址为空、购物车为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());

        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //查询当前用户的购物车数据
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
        if (shoppingCarts == null || shoppingCarts.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }


        //构造订单数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, order);
        order.setNumber(UUID.randomUUID().toString());
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setUserId(BaseContext.getCurrentId());
        order.setOrderTime(LocalDateTime.now());
        order.setPayStatus(Orders.UN_PAID);
        order.setPhone(addressBook.getPhone());
        order.setConsignee(addressBook.getConsignee());
        orderMapper.insert(order);

        //订单明细数据
        List<OrderDetail> list = new ArrayList<>();
        for (ShoppingCart cart : shoppingCarts) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(order.getId());
            list.add(orderDetail);
        }
        orderDetailMapper.insertBatch(list);

        //清理购物车中的数据
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());

        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();
        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

//        //调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
//
//        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
//            throw new OrderBusinessException("该订单已支付");
//        }

//        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
//        vo.setPackageStr(jsonObject.getString("package"));


        OrderPaymentVO vo = new OrderPaymentVO();

        String tradeNo = ordersPaymentDTO.getOrderNumber();
        paySuccess(tradeNo);
        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        Map map = new HashMap();
        map.put("type", 1); // 来单提醒
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号: " + outTradeNo);

        String jsonString = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(jsonString);

    }

    /**
     * 查询订单
     * @param id
     * @return
     */
    public OrderVO getOrderDetailById(Long id) {
        Orders orders = orderMapper.getById(id);
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);

        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetails);
        return orderVO;
    }

    /**
     * 催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        Orders orders = orderMapper.getById(id);

        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Map map = new HashMap();
        map.put("type", 2); // 催单
        map.put("orderId", orders.getId());
        map.put("content", "订单号: " + orders.getNumber());

        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 历史订单查询
     * @return
     */
    @Override
    public PageResult getHistoryOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        List<OrderVO> list = new ArrayList<>();

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        for (Orders order : page) {
            List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(order.getId());
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(order, orderVO);
            orderVO.setOrderDetailList(orderDetails);
            list.add(orderVO);
        }

        return new PageResult(page.getTotal(),list);
    }

    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult getOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        for (Orders order : page) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(order, orderVO);
            orderVO.setOrderDishes(getOrderDishesStr(order));
            orderVOList.add(orderVO);
        }

        return new PageResult(page.getTotal(),orderVOList);
    }


    private String getOrderDishesStr(Orders orders) {
        // 查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    public void confirmOrder(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersConfirmDTO, orders);
        orders.setStatus(Orders.CONFIRMED);
        orderMapper.update(orders);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    public OrderStatisticsVO getOrdersStatistics() {
        Map map = new HashMap();
        map.put("status", Orders.CONFIRMED);
        Integer confirmed = orderMapper.countByMap(map);
        map.replace("status",Orders.DELIVERY_IN_PROGRESS);
        Integer deliveryInProgress = orderMapper.countByMap(map);
        map.replace("status", Orders.TO_BE_CONFIRMED);
        Integer toBeConfirmed = orderMapper.countByMap(map);


        return new OrderStatisticsVO().builder()
                .confirmed(confirmed)
                .deliveryInProgress(deliveryInProgress)
                .toBeConfirmed(toBeConfirmed)
                .build();
    }

    /**
     * 派单
     * @param id
     */
    public void deliveryOrder(Long id) {
        Orders order = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为3
        if (order == null || !order.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        order.setStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(order);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    public OrderVO getOrderDetails(Long id) {
        Orders orders = orderMapper.getById(id);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        orderVO.setOrderDishes(getOrderDishesStr(orders));
        return orderVO;
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    public void rejectOrder(OrdersRejectionDTO ordersRejectionDTO) {
        Orders order = orderMapper.getById(ordersRejectionDTO.getId());
        if (order == null || !order.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        if (order.getPayStatus() == Orders.PAID) {
            // 微信退款
            log.info("微信退款");

            order.setPayStatus(Orders.REFUND);
        }

        order.setStatus(Orders.CANCELLED);
        order.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        order.setCancelReason(ordersRejectionDTO.getRejectionReason());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    /**
     * 完成订单
     * @param id
     */
    public void completeOrder(Long id) {
        Orders order = orderMapper.getById(id);
        if (order == null || !order.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        order.setStatus(Orders.COMPLETED);
        order.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    public void cancelOrder(OrdersCancelDTO ordersCancelDTO) {
        Orders order = orderMapper.getById(ordersCancelDTO.getId());

        if (order.getPayStatus() == Orders.PAID) {
            // 微信退款
            log.info("微信退款");

            order.setPayStatus(Orders.REFUND);
        }


        order.setStatus(Orders.CANCELLED);
        order.setCancelReason(ordersCancelDTO.getCancelReason());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }
}
