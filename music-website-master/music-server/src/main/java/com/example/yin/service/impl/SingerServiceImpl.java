package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.yin.common.R;
import com.example.yin.constant.CacheConstant;
import com.example.yin.mapper.SingerMapper;
import com.example.yin.model.domain.Singer;
import com.example.yin.model.request.SingerRequest;
import com.example.yin.service.SingerService;
import com.example.yin.utils.CacheProtectionUtil;
import com.example.yin.utils.FileUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 歌手服务 — 仅全量查询 allSinger 使用缓存防护
 */
@Service
public class SingerServiceImpl extends ServiceImpl<SingerMapper, Singer> implements SingerService {

    @Autowired
    private SingerMapper singerMapper;

    @Autowired
    private CacheProtectionUtil cacheUtil;

    // ==================== 读操作 ====================

    @Override
    public R allSinger() {
        List<Singer> singers = cacheUtil.getWithProtection(
                CacheConstant.singerAllKey(),
                List.class,
                () -> singerMapper.selectList(null),
                CacheConstant.TTL_SINGER
        );
        return R.success(null, singers);
    }

    @Override
    public R singerOfName(String name) {
        List<Singer> singers = singerMapper.selectList(new QueryWrapper<Singer>().like("name", name));
        return R.success(null, singers);
    }

    @Override
    public R singerOfSex(Integer sex) {
        List<Singer> singers = singerMapper.selectList(new QueryWrapper<Singer>().like("sex", sex));
        return R.success(null, singers);
    }

    // ==================== 写操作 ====================

    @Override
    public R addSinger(SingerRequest addSingerRequest) {
        Singer singer = new Singer();
        BeanUtils.copyProperties(addSingerRequest, singer);
        String pic = "/img/avatorImages/user.jpg";
        singer.setPic(pic);
        if (singerMapper.insert(singer) > 0) {
            // 新增歌手 → 全量列表缓存失效
            cacheUtil.evict(CacheConstant.singerAllKey());
            return R.success("添加成功");
        } else {
            return R.error("添加失败");
        }
    }

    @Override
    public R updateSingerMsg(SingerRequest updateSingerRequest) {
        Singer singer = new Singer();
        BeanUtils.copyProperties(updateSingerRequest, singer);
        if (singerMapper.updateById(singer) > 0) {
            // 修改歌手 → 全量缓存失效（影响面大，下次请求统一重建）
            cacheUtil.evict(CacheConstant.singerAllKey());
            return R.success("修改成功");
        } else {
            return R.error("修改失败");
        }
    }

    @Override
    public R updateSingerPic(MultipartFile avatorFile, int id) {
        String s = null;
        try {
            s = FileUtils.saveToMinio(avatorFile, "img/singerPic");
        } catch (IOException e) {
            e.printStackTrace();
            return R.error("文件上传失败: " + e.getMessage());
        }

        if (s != null) {
            Singer singer = new Singer();
            singer.setId(id);
            singer.setPic(s);
            if (singerMapper.updateById(singer) > 0) {
                cacheUtil.evict(CacheConstant.singerAllKey());
                return R.success("上传成功", s);
            } else {
                return R.error("上传失败");
            }
        } else {
            return R.error("上传失败");
        }
    }

    @Override
    public R deleteSinger(Integer id) {
        if (singerMapper.deleteById(id) > 0) {
            cacheUtil.evict(CacheConstant.singerAllKey());
            return R.success("删除成功");
        } else {
            return R.error("删除失败");
        }
    }
}
