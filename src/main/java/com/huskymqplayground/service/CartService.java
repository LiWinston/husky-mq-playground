package com.huskymqplayground.service;

import com.huskymqplayground.domain.CartItem;
import com.huskymqplayground.domain.CartTransaction;
import com.huskymqplayground.dto.CartDTO;
import com.huskymqplayground.mapper.CartItemMapper;
import com.huskymqplayground.mapper.CartTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final CartTransactionMapper cartTransactionMapper;

    @Transactional(rollbackFor = Exception.class)
    public void addToCartWithTxLog(CartDTO cartDTO, String txId) {
        CartItem item = new CartItem();
        item.setUsername(cartDTO.getUsername());
        item.setItemName(cartDTO.getItemName());
        item.setQuantity(cartDTO.getQuantity());
        item.setCreateTime(LocalDateTime.now());
        cartItemMapper.insert(item);

        CartTransaction tx = new CartTransaction();
        tx.setTxId(txId);
        tx.setUsername(cartDTO.getUsername());
        tx.setCreateTime(LocalDateTime.now());
        cartTransactionMapper.insert(tx);
    }
}
