package org.ruoyi.system.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.ruoyi.common.core.constant.CacheNames;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.ConfigService;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.core.utils.SpringUtils;
import org.ruoyi.common.core.utils.StreamUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.core.utils.file.FileUtils;
import org.ruoyi.common.oss.core.OssClient;
import org.ruoyi.common.oss.enumd.AccessPolicyType;
import org.ruoyi.common.oss.factory.OssFactory;
import org.ruoyi.core.page.PageQuery;
import org.ruoyi.core.page.TableDataInfo;
import org.ruoyi.system.domain.SysOss;
import org.ruoyi.system.domain.bo.SysOssBo;
import org.ruoyi.system.domain.vo.SysOssVo;
import org.ruoyi.system.mapper.SysOssMapper;
import org.ruoyi.system.service.ISysOssService;
import org.ruoyi.system.utils.QwenFileUploadUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 文件上传 服务层实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysOssServiceImpl implements ISysOssService, OssService {

    private final SysOssMapper baseMapper;

    private final ConfigService configService;

    // 文档解析判断前缀
    private static final String FILE_ID_PREFIX = "fileid://";

    // 服务名称
    private static final String DASH_SCOPE = "Qwen";

    // 默认服务
    private static final String CATEGORY = "file";

    // apiKey 配置名称
    private static final String CONFIG_NAME_KEY = "apiKey";

    // apiHost 配置名称
    private static final String CONFIG_NAME_URL = "apiHost";

    // 默认密钥 todo：请在系统配置中设置正确的密钥
    private static String API_KEY = "";

    // 默认api路径地址
    private static String API_HOST = "https://dashscope.aliyuncs.com/compatible-mode/v1/files";

    @Override
    public TableDataInfo<SysOssVo> queryPageList(SysOssBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysOss> lqw = buildQueryWrapper(bo);
        Page<SysOssVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        List<SysOssVo> filterResult = StreamUtils.toList(result.getRecords(), this::matchingUrl);
        result.setRecords(filterResult);
        return TableDataInfo.build(result);
    }

    @Override
    public List<SysOssVo> listByIds(Collection<Long> ossIds) {
        List<SysOssVo> list = new ArrayList<>();
        for (Long id : ossIds) {
            SysOssVo vo = SpringUtils.getAopProxy(this).getById(id);
            if (ObjectUtil.isNotNull(vo)) {
                list.add(this.matchingUrl(vo));
            }
        }
        return list;
    }

    @Override
    public String selectUrlByIds(String ossIds) {
        List<String> list = new ArrayList<>();
        for (Long id : StringUtils.splitTo(ossIds, Convert::toLong)) {
            SysOssVo vo = SpringUtils.getAopProxy(this).getById(id);
            if (ObjectUtil.isNotNull(vo)) {
                list.add(this.matchingUrl(vo).getUrl());
            }
        }
        return String.join(StringUtils.SEPARATOR, list);
    }

    private LambdaQueryWrapper<SysOss> buildQueryWrapper(SysOssBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<SysOss> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getFileName()), SysOss::getFileName, bo.getFileName());
        lqw.like(StringUtils.isNotBlank(bo.getOriginalName()), SysOss::getOriginalName,
                bo.getOriginalName());
        lqw.eq(StringUtils.isNotBlank(bo.getFileSuffix()), SysOss::getFileSuffix, bo.getFileSuffix());
        lqw.eq(StringUtils.isNotBlank(bo.getUrl()), SysOss::getUrl, bo.getUrl());
        lqw.between(params.get("beginCreateTime") != null && params.get("endCreateTime") != null,
                SysOss::getCreateTime, params.get("beginCreateTime"), params.get("endCreateTime"));
        lqw.eq(ObjectUtil.isNotNull(bo.getCreateBy()), SysOss::getCreateBy, bo.getCreateBy());
        lqw.eq(StringUtils.isNotBlank(bo.getService()), SysOss::getService, bo.getService());
        return lqw;
    }

    @Cacheable(cacheNames = CacheNames.SYS_OSS, key = "#ossId")
    @Override
    public SysOssVo getById(Long ossId) {
        return baseMapper.selectVoById(ossId);
    }

    @Override
    public void download(Long ossId, HttpServletResponse response) throws IOException {
        SysOssVo sysOss = SpringUtils.getAopProxy(this).getById(ossId);
        if (ObjectUtil.isNull(sysOss)) {
            throw new ServiceException("文件数据不存在!");
        }
        FileUtils.setAttachmentResponseHeader(response, sysOss.getOriginalName());
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE + "; charset=UTF-8");
        OssClient storage = OssFactory.instance();
        try (InputStream inputStream = storage.getObjectContent(sysOss.getUrl())) {
            int available = inputStream.available();
            IoUtil.copy(inputStream, response.getOutputStream(), available);
            response.setContentLength(available);
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
    }

    @Override
    public String downloadByByte(Long ossId) throws IOException {
        SysOssVo sysOss = SpringUtils.getAopProxy(this).getById(ossId);
        if (ObjectUtil.isNull(sysOss)) {
            throw new ServiceException("文件数据不存在!");
        }

        OssClient storage = OssFactory.instance();
        try (InputStream inputStream = storage.getObjectContent(sysOss.getUrl())) {
            // 读取输入流中的所有字节
            byte[] bytes = IoUtil.readBytes(inputStream);
            // 将字节数组转换为Base64编码的字符串
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
    }

    @Override
    public MultipartFile downloadByFile(Long ossId) throws IOException {
        SysOssVo sysOss = SpringUtils.getAopProxy(this).getById(ossId);
        if (ObjectUtil.isNull(sysOss)) {
            throw new ServiceException("文件数据不存在!");
        }

        OssClient storage = OssFactory.instance();
        try (InputStream inputStream = storage.getObjectContent(sysOss.getUrl())) {
            byte[] content = IoUtil.readBytes(inputStream);
            return new MockMultipartFile(
                    sysOss.getFileName(),
                    sysOss.getOriginalName(),
                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    content
            );
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
    }

    @Override
    public SysOssVo upload(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        int lastDotIndex = originalName != null ? originalName.lastIndexOf(".") : -1;
        String prefix = lastDotIndex > 0 ? "" : originalName.substring(0, lastDotIndex);
        String suffix = lastDotIndex > 0 ? "" : originalName.substring(lastDotIndex);
        File tempFile = null;
        try {
            // 创建临时文件来处理MultipartFile
            tempFile = File.createTempFile("upload_", suffix);
            file.transferTo(tempFile);
            // 获取配置
            initConfig();
            // 使用工具类上传文件到阿里云
            String fileId = QwenFileUploadUtils.uploadFile(tempFile, API_HOST, API_KEY);
            if (StringUtils.isEmpty(fileId)) {
                throw new ServiceException("文件上传失败，未获取到fileId");
            }
            // 保存文件信息到数据库
            SysOss oss = new SysOss();
            oss.setUrl(FILE_ID_PREFIX + fileId);
            oss.setFileSuffix(suffix);
            oss.setFileName(prefix);
            oss.setOriginalName(originalName);
            oss.setService(DASH_SCOPE);
            baseMapper.insert(oss);
            SysOssVo sysOssVo = new SysOssVo();
            BeanUtils.copyProperties(oss, sysOssVo);
            return sysOssVo;
        } catch (IOException e) {
            throw new ServiceException("文件上传失败: " + e.getMessage());
        } finally {
            // 删除临时文件
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // 做一些业务上的校验,判断是否需要校验
        }
        List<SysOss> list = baseMapper.selectBatchIds(ids);
        for (SysOss sysOss : list) {
            OssClient storage = OssFactory.instance(sysOss.getService());
            storage.delete(sysOss.getUrl());
        }
        return baseMapper.deleteBatchIds(ids) > 0;
    }

    /**
     * 匹配Url
     *
     * @param oss OSS对象
     * @return oss 匹配Url的OSS对象
     */
    private SysOssVo matchingUrl(SysOssVo oss) {
        OssClient storage = OssFactory.instance(oss.getService());
        // 仅修改桶类型为 private 的URL，临时URL时长为120s
        if (AccessPolicyType.PRIVATE == storage.getAccessPolicy()) {
            oss.setUrl(storage.getPrivateUrl(oss.getFileName(), 120));
        }
        return oss;
    }

    @Override
    public String downloadToTempPath(Long ossId) throws IOException {
        SysOssVo sysOss = SpringUtils.getAopProxy(this).getById(ossId);
        if (ObjectUtil.isNull(sysOss)) {
            throw new ServiceException("文件数据不存在!");
        }

        OssClient storage = OssFactory.instance();
        try (InputStream inputStream = storage.getObjectContent(sysOss.getUrl())) {
            // 创建临时文件
            String suffix = StringUtils.isNotEmpty(sysOss.getFileSuffix()) ? sysOss.getFileSuffix() : "";
            java.io.File tempFile = java.io.File.createTempFile("download_", suffix);
            // 确保临时文件在JVM退出时删除
            tempFile.deleteOnExit();
            // 将输入流内容写入临时文件
            cn.hutool.core.io.FileUtil.writeFromStream(inputStream, tempFile);
            // 返回临时文件的绝对路径
            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * 根据文件路径删除文件
     *
     * @param filePath 文件路径
     * @return 是否删除成功
     */
    @Override
    public boolean deleteFile(String filePath) {
        if (StringUtils.isEmpty(filePath)) {
            return false;
        }

        try {
            java.io.File file = new java.io.File(filePath);
            if (file.exists() && file.isFile()) {
                return file.delete();
            }
            return false;
        } catch (Exception e) {
            throw new ServiceException("删除文件失败: " + e.getMessage());
        }
    }

    /**
     * 初始化配置并返回API密钥和主机
     */
    private void initConfig() {
        String apiKey = configService.getConfigValue(CATEGORY, CONFIG_NAME_KEY);
        if (StringUtils.isEmpty(apiKey)) {
            throw new ServiceException("请先配置Qwen上传文件相关API_KEY");
        }
        API_KEY = apiKey;
        String apiHost = configService.getConfigValue(CATEGORY, CONFIG_NAME_URL);
        if (StringUtils.isEmpty(apiHost)) {
            throw new ServiceException("请先配置Qwen上传文件相关API_HOST");
        }
        API_HOST = apiHost;
    }
}
