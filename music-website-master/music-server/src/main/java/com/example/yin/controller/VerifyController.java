package com.example.yin.controller;

import com.example.yin.common.R;
import com.example.yin.model.request.VerifyRequest;
import com.example.yin.service.VerifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 核验端接口
 * 供现场工作人员使用的核验功能：
 *   - 扫描用户入场二维码 → 查看订单信息 → 确认核验
 * 此接口通常由独立的核验设备或核验页面调用
 */
@RestController
@RequestMapping("/verify")
public class VerifyController {

    @Autowired
    private VerifyService verifyService;

    /**
     * 扫描二维码后获取核验信息
     * 用户二维码内容为 /verify/{qrCodeToken}，工作人员扫描后调用此接口
     * @param qrCodeToken 二维码中携带的 token（UUID 去横线格式）
     * @return 订单关键信息（身份证/手机号脱敏），供人工比对
     */
    @GetMapping("/{qrCodeToken}")
    public R getInfo(@PathVariable String qrCodeToken) {
        return verifyService.getVerifyInfo(qrCodeToken);
    }

    /**
     * 确认核验入场
     * 工作人员人工核对身份信息无误后，点击确认完成入场登记
     * @param request { qrCodeToken, operatorId }
     *                 operatorId 为当前登录的工作人员 ID，可选
     */
    @PostMapping("/confirm")
    public R confirm(@RequestBody VerifyRequest request) {
        return verifyService.confirm(request.getQrCodeToken(), request.getOperatorId());
    }
}