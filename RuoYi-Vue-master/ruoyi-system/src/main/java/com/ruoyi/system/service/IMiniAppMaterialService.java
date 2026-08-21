package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.common.core.domain.model.MiniAppMaterialBuyBody;
import com.ruoyi.common.core.domain.model.MiniAppMaterialUploadBody;
import com.ruoyi.system.domain.EduMaterial;
import com.ruoyi.system.domain.EduMaterialOrder;

/**
 * MiniApp material service.
 */
public interface IMiniAppMaterialService
{
    /**
     * 商城列表（家长端，含 bought 标记）
     */
    public List<EduMaterial> selectOnSaleMaterialsForParent(String title, String subjectName, String gradeName);

    /**
     * 资料详情（家长端，含 bought 标记 + 上传者昵称）
     */
    public EduMaterial selectMaterialDetailForParent(Long materialId);

    /**
     * 我的购买记录（家长端）
     */
    public List<EduMaterialOrder> selectMyOrders();

    /**
     * 模拟支付购买资料（家长端）
     *
     * @return 订单ID
     */
    public Long buyMaterial(MiniAppMaterialBuyBody body);

    /**
     * 查询可下载订单（家长端，已支付）
     */
    public EduMaterialOrder selectDownloadableOrder(Long orderId);

    /**
     * 我上传的资料（教师/管理员端）
     */
    public List<EduMaterial> selectMyUploads(String title, String subjectName, String gradeName);

    /**
     * 新增资料（教师/管理员端，直接上架）
     */
    public int uploadMaterial(MiniAppMaterialUploadBody body);

    /**
     * 修改资料（教师/管理员端，仅自己上传或管理员可改）
     */
    public int updateMyMaterial(EduMaterial material);

    /**
     * 删除资料（教师/管理员端，仅自己上传或管理员可删）
     */
    public int deleteMyMaterial(Long materialId);
}
