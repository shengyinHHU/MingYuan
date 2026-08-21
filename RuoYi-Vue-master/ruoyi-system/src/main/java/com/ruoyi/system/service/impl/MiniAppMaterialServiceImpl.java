package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.model.MiniAppMaterialBuyBody;
import com.ruoyi.common.core.domain.model.MiniAppMaterialUploadBody;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.domain.EduMaterial;
import com.ruoyi.system.domain.EduMaterialOrder;
import com.ruoyi.system.mapper.EduMaterialMapper;
import com.ruoyi.system.mapper.EduMaterialOrderMapper;
import com.ruoyi.system.service.IMiniAppMaterialService;

/**
 * MiniApp material service implementation.
 */
@Service
public class MiniAppMaterialServiceImpl implements IMiniAppMaterialService
{
    @Autowired
    private EduMaterialMapper eduMaterialMapper;

    @Autowired
    private EduMaterialOrderMapper eduMaterialOrderMapper;

    @Override
    public List<EduMaterial> selectOnSaleMaterialsForParent(String title, String subjectName, String gradeName)
    {
        EduMaterial query = new EduMaterial();
        query.setTitle(title);
        query.setSubjectName(subjectName);
        query.setGradeName(gradeName);
        return eduMaterialMapper.selectOnSaleMaterialsForParent(query, SecurityUtils.getUserId());
    }

    @Override
    public EduMaterial selectMaterialDetailForParent(Long materialId)
    {
        if (materialId == null)
        {
            throw new ServiceException("资料ID不能为空");
        }
        EduMaterial material = eduMaterialMapper.selectEduMaterialDetail(materialId, SecurityUtils.getUserId());
        if (material == null)
        {
            throw new ServiceException("资料不存在或已下架");
        }
        return material;
    }

    @Override
    public List<EduMaterialOrder> selectMyOrders()
    {
        return eduMaterialOrderMapper.selectParentOrders(SecurityUtils.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long buyMaterial(MiniAppMaterialBuyBody body)
    {
        if (body == null || body.getMaterialId() == null)
        {
            throw new ServiceException("请选择要购买的资料");
        }
        Long parentId = SecurityUtils.getUserId();
        EduMaterial material = eduMaterialMapper.selectEduMaterialByMaterialId(body.getMaterialId());
        if (material == null || "1".equals(material.getStatus()) || "2".equals(material.getDelFlag()))
        {
            throw new ServiceException("资料不存在或已下架");
        }
        if (!"1".equals(material.getShelfStatus()))
        {
            throw new ServiceException("资料已下架");
        }

        // 重复购买：直接返回既有已支付订单
        EduMaterialOrder exist = eduMaterialOrderMapper.selectOrderByMaterialAndParent(body.getMaterialId(), parentId);
        if (exist != null)
        {
            if ("1".equals(exist.getPayStatus()))
            {
                return exist.getOrderId();
            }
            // 既有未支付订单，直接标记为已支付（模拟）
            EduMaterialOrder upd = new EduMaterialOrder();
            upd.setOrderId(exist.getOrderId());
            upd.setPayStatus("1");
            upd.setPayTime(new Date());
            upd.setPayWay("0");
            upd.setUpdateBy(SecurityUtils.getUsername());
            eduMaterialOrderMapper.updateEduMaterialOrder(upd);
            eduMaterialMapper.incrSaleCount(body.getMaterialId());
            return exist.getOrderId();
        }

        EduMaterialOrder order = new EduMaterialOrder();
        order.setOrderCode(generateOrderCode());
        order.setMaterialId(body.getMaterialId());
        order.setParentId(parentId);
        BigDecimal amount = material.getPrice() == null ? BigDecimal.ZERO : material.getPrice();
        order.setAmount(amount);
        order.setPayStatus("1");
        order.setPayTime(new Date());
        order.setPayWay("0");
        order.setWxTransactionId("");
        order.setStatus("0");
        order.setCreateBy(SecurityUtils.getUsername());
        order.setCreateTime(new Date());
        eduMaterialOrderMapper.insertEduMaterialOrder(order);
        eduMaterialMapper.incrSaleCount(body.getMaterialId());
        return order.getOrderId();
    }

    @Override
    public EduMaterialOrder selectDownloadableOrder(Long orderId)
    {
        if (orderId == null)
        {
            throw new ServiceException("订单ID不能为空");
        }
        EduMaterialOrder order = eduMaterialOrderMapper.selectDownloadableOrder(orderId, SecurityUtils.getUserId());
        if (order == null)
        {
            throw new ServiceException("订单不存在或未支付");
        }
        return order;
    }

    @Override
    public List<EduMaterial> selectMyUploads(String title, String subjectName, String gradeName)
    {
        EduMaterial query = new EduMaterial();
        query.setTitle(title);
        query.setSubjectName(subjectName);
        query.setGradeName(gradeName);
        query.setUploadUserId(SecurityUtils.getUserId());
        return eduMaterialMapper.selectEduMaterialList(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int uploadMaterial(MiniAppMaterialUploadBody body)
    {
        if (body == null)
        {
            throw new ServiceException("请求参数不能为空");
        }
        if (StringUtils.isBlank(body.getTitle()) || body.getTitle().length() > 100)
        {
            throw new ServiceException("资料标题必填且不能超过100字");
        }
        if (StringUtils.isBlank(body.getSubjectName()))
        {
            throw new ServiceException("请选择学科");
        }
        if (StringUtils.isBlank(body.getGradeName()))
        {
            throw new ServiceException("请选择年级");
        }
        if (StringUtils.isBlank(body.getFilePath()))
        {
            throw new ServiceException("请上传资料文件");
        }
        if (!body.getFileName().toLowerCase().endsWith(".pdf"))
        {
            throw new ServiceException("仅支持 PDF 文件");
        }
        if (body.getPrice() == null || body.getPrice().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("价格不能为负数");
        }
        if (body.getPrice().compareTo(new BigDecimal("999999.99")) > 0)
        {
            throw new ServiceException("价格过大");
        }

        EduMaterial material = new EduMaterial();
        material.setMaterialCode(generateMaterialCode());
        material.setTitle(body.getTitle().trim());
        material.setSubjectName(body.getSubjectName());
        material.setGradeName(body.getGradeName());
        material.setPrice(body.getPrice());
        material.setIntro(body.getIntro() == null ? "" : body.getIntro());
        material.setFilePath(body.getFilePath());
        material.setFileName(body.getFileName());
        material.setFileSize(body.getFileSize() == null ? 0L : body.getFileSize());
        material.setCoverUrl(body.getCoverUrl() == null ? "" : body.getCoverUrl());
        material.setShelfStatus("1"); // 直接上架
        material.setSaleCount(0);
        material.setUploadUserId(SecurityUtils.getUserId());
        material.setStatus("0");
        material.setDelFlag("0");
        material.setCreateBy(SecurityUtils.getUsername());
        material.setCreateTime(new Date());
        return eduMaterialMapper.insertEduMaterial(material);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMyMaterial(EduMaterial material)
    {
        if (material == null || material.getMaterialId() == null)
        {
            throw new ServiceException("资料ID不能为空");
        }
        EduMaterial origin = eduMaterialMapper.selectEduMaterialByMaterialId(material.getMaterialId());
        if (origin == null)
        {
            throw new ServiceException("资料不存在");
        }
        Long currentUserId = SecurityUtils.getUserId();
        boolean isAdmin = SecurityUtils.hasRole("admin") || SecurityUtils.hasRole("administrator") || SecurityUtils.isAdmin(currentUserId);
        if (!isAdmin && !currentUserId.equals(origin.getUploadUserId()))
        {
            throw new ServiceException("只能修改自己上传的资料");
        }
        material.setUpdateBy(SecurityUtils.getUsername());
        material.setUpdateTime(new Date());
        // 不允许通过此接口修改销量、上传者
        material.setSaleCount(null);
        material.setUploadUserId(null);
        return eduMaterialMapper.updateEduMaterial(material);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMyMaterial(Long materialId)
    {
        if (materialId == null)
        {
            throw new ServiceException("资料ID不能为空");
        }
        EduMaterial origin = eduMaterialMapper.selectEduMaterialByMaterialId(materialId);
        if (origin == null)
        {
            throw new ServiceException("资料不存在");
        }
        Long currentUserId = SecurityUtils.getUserId();
        boolean isAdmin = SecurityUtils.hasRole("admin") || SecurityUtils.hasRole("administrator") || SecurityUtils.isAdmin(currentUserId);
        if (!isAdmin && !currentUserId.equals(origin.getUploadUserId()))
        {
            throw new ServiceException("只能删除自己上传的资料");
        }
        return eduMaterialMapper.deleteEduMaterialByMaterialId(materialId);
    }

    private String generateMaterialCode()
    {
        return "M" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + IdUtils.simpleUUID().substring(0, 4).toUpperCase();
    }

    private String generateOrderCode()
    {
        return "O" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + IdUtils.simpleUUID().substring(0, 4).toUpperCase();
    }
}
