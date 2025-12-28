package com.huskymqplayground.service;

import com.huskymqplayground.domain.OrderTransaction;
import com.huskymqplayground.domain.PurchaseOrder;
import com.huskymqplayground.dto.OrderDTO;
import com.huskymqplayground.mapper.OrderTransactionMapper;
import com.huskymqplayground.mapper.PurchaseOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final OrderTransactionMapper orderTransactionMapper;

    @Transactional(rollbackFor = Exception.class)
    public void createOrderWithTxLog(OrderDTO orderDTO, String txId) {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(orderDTO.getOrderNo());
        order.setBuyer(orderDTO.getBuyer());
        order.setItemName(orderDTO.getItemName());
        order.setQuantity(orderDTO.getQuantity());
        order.setAmount(orderDTO.getAmount());
        order.setCreateTime(LocalDateTime.now());
        purchaseOrderMapper.insert(order);

        OrderTransaction tx = new OrderTransaction();
        tx.setTxId(txId);
        tx.setOrderNo(orderDTO.getOrderNo());
        tx.setCreateTime(LocalDateTime.now());
        orderTransactionMapper.insert(tx);
    }
}
