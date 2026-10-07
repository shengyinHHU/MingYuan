package com.ruoyi.system.service.impl;
import java.util.List;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.model.MiniAppMaterialBuyBody;
import com.ruoyi.common.core.domain.model.MiniAppMaterialUploadBody;
import com.ruoyi.system.domain.EduMaterial;
import com.ruoyi.system.domain.EduMaterialOrder;
import com.ruoyi.system.service.IEduMaterialOrderService;

/** Retained only for binary compatibility; all commerce now uses ShopService. */
@Service
@Deprecated
public class EduMaterialOrderServiceImpl implements IEduMaterialOrderService {
    @Override public EduMaterialOrder selectEduMaterialOrderByOrderId(Long id) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public List<EduMaterialOrder> selectEduMaterialOrderList(EduMaterialOrder query) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int insertEduMaterialOrder(EduMaterialOrder order) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int updateEduMaterialOrder(EduMaterialOrder order) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int deleteEduMaterialOrderByOrderIds(Long[] ids) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int deleteEduMaterialOrderByOrderId(Long id) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
}
