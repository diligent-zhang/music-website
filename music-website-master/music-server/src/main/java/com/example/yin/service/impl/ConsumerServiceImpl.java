package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.yin.common.R;
import com.example.yin.constant.CacheConstant;
import com.example.yin.constant.Constants;
import com.example.yin.mapper.ConsumerMapper;
import com.example.yin.model.domain.Consumer;
import com.example.yin.model.request.ConsumerRequest;
import com.example.yin.service.ConsumerService;
import com.example.yin.utils.CacheProtectionUtil;
import com.example.yin.utils.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.example.yin.constant.Constants.SALT;

/**
 * 用户服务 — 仅对高频全量查询 allUser 使用缓存防护
 * 登录/密码验证等涉及安全的方法不走缓存
 */
@Service
public class ConsumerServiceImpl extends ServiceImpl<ConsumerMapper, Consumer>
        implements ConsumerService {

    @Autowired
    private ConsumerMapper consumerMapper;

    @Autowired
    private CacheProtectionUtil cacheUtil;

    // ==================== 读操作（高频，走缓存）====================

    @Override
    public R allUser() {
        List<Consumer> users = cacheUtil.getWithProtection(
                CacheConstant.consumerAllKey(),
                List.class,
                () -> consumerMapper.selectList(null),
                CacheConstant.TTL_CONSUMER
        );
        return R.success(null, users);
    }

    @Override
    public R userOfId(Integer id) {
        List<Consumer> users = consumerMapper.selectList(new QueryWrapper<Consumer>().eq("id", id));
        return R.success(null, users);
    }
    //实现
    @Override
    public R getYinbi(Integer userId){
        Consumer consumer = consumerMapper.selectById(userId);
        if(consumer == null)
            return R.error("用户不存在");
        return R.success("成功", Map.of("yinbi",consumer.getYinbi()));
    }
    @Override
    public R rechargeYinbi(Integer userId, BigDecimal amount, String transactionNo){
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            return R.error("金额不合法");
        if (transactionNo == null || transactionNo.trim().length() < 6)
            return R.error("交易流水号格式不正确");
        Consumer consumer = consumerMapper.selectById(userId);
        if (consumer==null)
            return R.error("用户不存在");
        if (consumer.getYinbi() == null) consumer.setYinbi(BigDecimal.ZERO);
        consumer.setYinbi(consumer.getYinbi().add(amount));
        consumerMapper.updateById(consumer);
        return R.success("充值成功",Map.of("balance",consumer.getYinbi()));
    }
    @Override
    public R payByYinbi(Integer userId, BigDecimal amount){
        Consumer consumer = consumerMapper.selectById(userId);
        if (consumer == null)
            return R.error("用户不存在");
        if (consumer.getYinbi() == null) consumer.setYinbi(BigDecimal.ZERO);
        if(consumer.getYinbi().compareTo(amount) < 0)
            return R.error("余额不足");
        consumer.setYinbi(consumer.getYinbi().subtract(amount));
        consumerMapper.updateById(consumer);
        return R.success("支付成功",Map.of("balance",consumer.getYinbi()));
    }

    // ==================== 写操作（更新DB后清理缓存）====================


    /**
     * 新增用户
     */
    @Override
    public R addUser(ConsumerRequest registryRequest) {
        if (this.existUser(registryRequest.getUsername())) {
            return R.warning("用户名已注册");
        }
        Consumer consumer = new Consumer();
        BeanUtils.copyProperties(registryRequest, consumer);
        //MD5加密
        String password = DigestUtils.md5DigestAsHex((SALT + registryRequest.getPassword()).getBytes(StandardCharsets.UTF_8));
        consumer.setPassword(password);
        //都用用
        if (StringUtils.isBlank(consumer.getPhoneNum())) {
            consumer.setPhoneNum(null);
        }
        if ("".equals(consumer.getEmail())) {
            consumer.setEmail(null);
        }
        consumer.setAvator("img/avatorImages/user.jpg");
        try {
            QueryWrapper<Consumer> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("email", consumer.getEmail());
            Consumer one = consumerMapper.selectOne(queryWrapper);
            if (one != null) {
                return R.fatal("邮箱不允许重复");
            }
            if (consumerMapper.insert(consumer) > 0) {
                // 新增用户 → 全量用户列表缓存失效
                cacheUtil.evict(CacheConstant.consumerAllKey());
                return R.success("注册成功");
            } else {
                return R.error("注册失败");
            }
        } catch (DuplicateKeyException e) {
            return R.fatal(e.getMessage());
        }
    }

    @Override
    public R updateUserMsg(ConsumerRequest updateRequest) {
        Consumer consumer = new Consumer();
        BeanUtils.copyProperties(updateRequest, consumer);
        if (consumerMapper.updateById(consumer) > 0) {
            cacheUtil.evict(CacheConstant.consumerAllKey());
            return R.success("修改成功");
        } else {
            return R.error("修改失败");
        }
    }

    @Override
    public R updateUserAvator(MultipartFile avatorFile, int id) {
        String s = null;
        try {
            s = FileUtils.saveToMinio(avatorFile, "img/avatorImages");
        } catch (IOException e) {
            e.printStackTrace();
            return R.error("文件上传失败: " + e.getMessage());
        }

        if (s != null) {
            Consumer consumer = new Consumer();
            consumer.setId(id);
            consumer.setAvator(s);
            if (consumerMapper.updateById(consumer) > 0) {
                cacheUtil.evict(CacheConstant.consumerAllKey());
                return R.success("上传成功", s);
            } else {
                return R.error("上传失败");
            }
        } else {
            return R.error("上传失败");
        }
    }

    // 删除用户
    @Override
    public R deleteUser(Integer id) {
        if (consumerMapper.deleteById(id) > 0) {
            cacheUtil.evict(CacheConstant.consumerAllKey());
            return R.success("删除成功");
        } else {
            return R.error("删除失败");
        }
    }

    // ==================== 以下方法涉及密码/登录，不走缓存 ====================

    @Override
    public R updatePassword(ConsumerRequest updatePasswordRequest) {
        if (!this.verityPasswd(updatePasswordRequest.getUsername(), updatePasswordRequest.getOldPassword())) {
            return R.error("密码输入错误");
        }
        Consumer consumer = new Consumer();
        consumer.setId(updatePasswordRequest.getId());
        String secretPassword = DigestUtils.md5DigestAsHex((SALT + updatePasswordRequest.getPassword()).getBytes(StandardCharsets.UTF_8));
        consumer.setPassword(secretPassword);
        if (consumerMapper.updateById(consumer) > 0) {
            return R.success("密码修改成功");
        } else {
            return R.error("密码修改失败");
        }
    }

    @Override
    public R updatePassword01(ConsumerRequest updatePasswordRequest) {
        Consumer consumer = new Consumer();
        consumer.setId(updatePasswordRequest.getId());
        String secretPassword = DigestUtils.md5DigestAsHex((SALT + updatePasswordRequest.getPassword()).getBytes(StandardCharsets.UTF_8));
        consumer.setPassword(secretPassword);
        if (consumerMapper.updateById(consumer) > 0) {
            return R.success("密码修改成功");
        } else {
            return R.error("密码修改失败");
        }
    }

    @Override
    public boolean existUser(String username) {
        QueryWrapper<Consumer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        return consumerMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public boolean verityPasswd(String username, String password) {
        QueryWrapper<Consumer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        String secretPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes(StandardCharsets.UTF_8));
        queryWrapper.eq("password", secretPassword);
        return consumerMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public R loginStatus(ConsumerRequest loginRequest, HttpSession session) {
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();
        if (this.verityPasswd(username, password)) {
            session.setAttribute("username", username);
            Consumer consumer = new Consumer();
            consumer.setUsername(username);
            return R.success("登录成功", consumerMapper.selectList(new QueryWrapper<>(consumer)));
        } else {
            return R.error("用户名或密码错误");
        }
    }

    @Override
    public R loginEmailStatus(ConsumerRequest loginRequest, HttpSession session) {
        String email = loginRequest.getEmail();
        String password = loginRequest.getPassword();
        Consumer consumer1 = findByEmail(email);
        if (this.verityPasswd(consumer1.getUsername(), password)) {
            session.setAttribute("username", consumer1.getUsername());
            Consumer consumer = new Consumer();
            consumer.setUsername(consumer1.getUsername());
            return R.success("登录成功", consumerMapper.selectList(new QueryWrapper<>(consumer)));
        } else {
            return R.error("用户名或密码错误");
        }
    }

    @Override
    public Consumer findByEmail(String email) {
        QueryWrapper<Consumer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", email);
        Consumer consumer = consumerMapper.selectOne(queryWrapper);
        return consumer;
    }
}
