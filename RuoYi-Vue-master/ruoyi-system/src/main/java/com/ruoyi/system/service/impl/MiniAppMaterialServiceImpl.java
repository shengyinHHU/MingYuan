package com.ruoyi.system.service.impl;
import java.util.List;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.model.MiniAppMaterialBuyBody;
import com.ruoyi.common.core.domain.model.MiniAppMaterialUploadBody;
import com.ruoyi.system.domain.EduMaterial;
import com.ruoyi.system.domain.EduMaterialOrder;
import com.ruoyi.system.service.IMiniAppMaterialService;

/** Retained only for binary compatibility; all commerce now uses ShopService. */
@Service
@Deprecated
public class MiniAppMaterialServiceImpl implements IMiniAppMaterialService {
    @Override public List<EduMaterial> selectOnSaleMaterialsForParent(String title,String subject,String grade) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public EduMaterial selectMaterialDetailForParent(Long id) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public List<EduMaterialOrder> selectMyOrders() {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public Long buyMaterial(MiniAppMaterialBuyBody body) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public EduMaterialOrder selectDownloadableOrder(Long id) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public List<EduMaterial> selectMyUploads(String title,String subject,String grade) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int uploadMaterial(MiniAppMaterialUploadBody body) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int updateMyMaterial(EduMaterial material) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int deleteMyMaterial(Long id) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
}
