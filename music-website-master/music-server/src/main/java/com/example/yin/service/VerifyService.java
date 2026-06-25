package com.example.yin.service;

import com.example.yin.common.R;

/**
 * 核验服务接口
 * 用于现场工作人员扫描用户二维码、确认入场
 */
public interface VerifyService {

    /**
     * 根据二维码 token 查询核验信息
     * 展示订单基本信息（身份证/手机号脱敏），供工作人员核对
     * @param qrCodeToken 二维码中的 token
     * @return { orderNo, concertTitle, tierName, idCard(脱敏), phone(脱敏), qrCodeToken }
     */
    R getVerifyInfo(String qrCodeToken);

    /**
     * 确认入场核验
     * 技术要点：使用 @Transactional 保证核验状态更新与核验记录插入的原子性
     * @param qrCodeToken 二维码 token
     * @param operatorId  核验操作员 ID（可为 null）
     * @return 核验结果
     */
    R confirm(String qrCodeToken, Integer operatorId);
}
