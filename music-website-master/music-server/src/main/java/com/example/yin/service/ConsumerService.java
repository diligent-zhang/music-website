package com.example.yin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.yin.common.R;
import com.example.yin.model.domain.Consumer;
import com.example.yin.model.request.ConsumerRequest;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

import javax.servlet.http.HttpSession;

public interface ConsumerService extends IService<Consumer> {

    R addUser(ConsumerRequest registryRequest);

    R updateUserMsg(ConsumerRequest updateRequest);

    R updateUserAvator(MultipartFile avatorFile, int id);

    R updatePassword(ConsumerRequest updatePasswordRequest);

    boolean existUser(String username);

    boolean verityPasswd(String username, String password);

    R deleteUser(Integer id);

    R allUser();

    R userOfId(Integer id);

    R loginStatus(ConsumerRequest loginRequest, HttpSession session);
    R loginEmailStatus(ConsumerRequest loginRequest, HttpSession session);
    Consumer findByEmail (String email);
    R updatePassword01(ConsumerRequest updatePasswordRequest);

    //接口
    R getYinbi(Integer userId);    //获取余额
    R rechargeYinbi(Integer userId, BigDecimal amount, String transactionNo);   //充值
    R payByYinbi(Integer userId, BigDecimal amount);    //付款
}
