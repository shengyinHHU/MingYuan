package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.EduMaterialOrder;
import com.ruoyi.system.mapper.EduMaterialOrderMapper;
import com.ruoyi.system.service.IEduMaterialOrderService;

/**
 * 资料订单Service实现
 *
 * @author ruoyi
 * @date 2026-08-21
 */
@Service
public class EduMaterialOrderServiceImpl implements IEduMaterialOrderService
{
    @Autowired
    private EduMaterialOrderMapper eduMaterialOrderMapper;

    @Override
    public EduMaterialOrder selectEduMaterialOrderByOrderId(Long orderId)
    {
        return eduMaterialOrderMapper.selectEduMaterialOrderByOrderId(orderId);
    }

    @Override
    public List<EduMaterialOrder> selectEduMaterialOrderList(EduMaterialOrder eduMaterialOrder)
    {
        return eduMaterialOrderMapper.selectEduMaterialOrderList(eduMaterialOrder);
    }

    @Override
    public int insertEduMaterialOrder(EduMaterialOrder eduMaterialOrder)
    {
        return eduMaterialOrderMapper.insertEduMaterialOrder(eduMaterialOrder);
    }

    @Override
    public int updateEduMaterialOrder(EduMaterialOrder eduMaterialOrder)
    {
        return eduMaterialOrderMapper.updateEduMaterialOrder(eduMaterialOrder);
    }

    @Override
    public int deleteEduMaterialOrderByOrderIds(Long[] orderIds)
    {
        return eduMaterialOrderMapper.deleteEduMaterialOrderByOrderIds(orderIds);
    }

    @Override
    public int deleteEduMaterialOrderByOrderId(Long orderId)
    {
        return eduMaterialOrderMapper.deleteEduMaterialOrderByOrderId(orderId);
    }
}
