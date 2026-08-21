package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.EduMaterialOrder;

/**
 * 资料订单Mapper接口
 *
 * @author ruoyi
 * @date 2026-08-21
 */
public interface EduMaterialOrderMapper
{
    /**
     * 查询资料订单
     *
     * @param orderId 资料订单主键
     * @return 资料订单
     */
    public EduMaterialOrder selectEduMaterialOrderByOrderId(Long orderId);

    /**
     * 查询家长某资料的订单（用于判断是否已购买）
     *
     * @param materialId 资料ID
     * @param parentId   家长ID
     * @return 订单
     */
    public EduMaterialOrder selectOrderByMaterialAndParent(@Param("materialId") Long materialId, @Param("parentId") Long parentId);

    /**
     * 查询资料订单列表
     *
     * @param eduMaterialOrder 资料订单
     * @return 资料订单集合
     */
    public List<EduMaterialOrder> selectEduMaterialOrderList(EduMaterialOrder eduMaterialOrder);

    /**
     * 查询家长购买记录（含资料标题）
     *
     * @param parentId 家长ID
     * @return 订单集合
     */
    public List<EduMaterialOrder> selectParentOrders(@Param("parentId") Long parentId);

    /**
     * 查询家长已支付的可下载订单（含资料文件路径）
     *
     * @param orderId   订单ID
     * @param parentId  家长ID
     * @return 订单（含文件路径）
     */
    public EduMaterialOrder selectDownloadableOrder(@Param("orderId") Long orderId, @Param("parentId") Long parentId);

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
     * 删除资料订单
     *
     * @param orderId 资料订单主键
     * @return 结果
     */
    public int deleteEduMaterialOrderByOrderId(Long orderId);

    /**
     * 批量删除资料订单
     *
     * @param orderIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteEduMaterialOrderByOrderIds(Long[] orderIds);
}
