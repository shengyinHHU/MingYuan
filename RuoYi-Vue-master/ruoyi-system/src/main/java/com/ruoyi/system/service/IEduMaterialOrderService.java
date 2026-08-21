package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.EduMaterialOrder;

/**
 * 资料订单Service接口
 *
 * @author ruoyi
 * @date 2026-08-21
 */
public interface IEduMaterialOrderService
{
    /**
     * 查询资料订单
     *
     * @param orderId 资料订单主键
     * @return 资料订单
     */
    public EduMaterialOrder selectEduMaterialOrderByOrderId(Long orderId);

    /**
     * 查询资料订单列表
     *
     * @param eduMaterialOrder 资料订单
     * @return 资料订单集合
     */
    public List<EduMaterialOrder> selectEduMaterialOrderList(EduMaterialOrder eduMaterialOrder);

    /**
     * 新增资料订单
     *
     * @param eduMaterialOrder 资料订单
     * @return 结果
     */
    public int insertEduMaterialOrder(EduMaterialOrder eduMaterialOrder);

    /**
     * 修改资料订单
     *
     * @param eduMaterialOrder 资料订单
     * @return 结果
     */
    public int updateEduMaterialOrder(EduMaterialOrder eduMaterialOrder);

    /**
     * 批量删除资料订单
     *
     * @param orderIds 需要删除的资料订单主键集合
     * @return 结果
     */
    public int deleteEduMaterialOrderByOrderIds(Long[] orderIds);

    /**
     * 删除资料订单
     *
     * @param orderId 资料订单主键
     * @return 结果
     */
    public int deleteEduMaterialOrderByOrderId(Long orderId);
}
