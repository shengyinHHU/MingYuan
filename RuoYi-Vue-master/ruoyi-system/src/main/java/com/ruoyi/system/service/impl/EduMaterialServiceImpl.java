package com.ruoyi.system.service.impl;
import java.util.List;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.model.MiniAppMaterialBuyBody;
import com.ruoyi.common.core.domain.model.MiniAppMaterialUploadBody;
import com.ruoyi.system.domain.EduMaterial;
import com.ruoyi.system.domain.EduMaterialOrder;
import com.ruoyi.system.service.IEduMaterialService;

/** Retained only for binary compatibility; all commerce now uses ShopService. */
@Service
@Deprecated
public class EduMaterialServiceImpl implements IEduMaterialService {
    @Override public EduMaterial selectEduMaterialByMaterialId(Long id) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public List<EduMaterial> selectEduMaterialList(EduMaterial query) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int insertEduMaterial(EduMaterial material) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int updateEduMaterial(EduMaterial material) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int deleteEduMaterialByMaterialIds(Long[] ids) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
    @Override public int deleteEduMaterialByMaterialId(Long id) {throw new ServiceException("资料商城接口已升级，请使用新的商品与订单流程");}
}
