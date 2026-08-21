package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.EduMaterial;
import com.ruoyi.system.mapper.EduMaterialMapper;
import com.ruoyi.system.service.IEduMaterialService;

/**
 * 资料商品Service实现
 *
 * @author ruoyi
 * @date 2026-08-21
 */
@Service
public class EduMaterialServiceImpl implements IEduMaterialService
{
    @Autowired
    private EduMaterialMapper eduMaterialMapper;

    @Override
    public EduMaterial selectEduMaterialByMaterialId(Long materialId)
    {
        return eduMaterialMapper.selectEduMaterialByMaterialId(materialId);
    }

    @Override
    public List<EduMaterial> selectEduMaterialList(EduMaterial eduMaterial)
    {
        return eduMaterialMapper.selectEduMaterialList(eduMaterial);
    }

    @Override
    public int insertEduMaterial(EduMaterial eduMaterial)
    {
        return eduMaterialMapper.insertEduMaterial(eduMaterial);
    }

    @Override
    public int updateEduMaterial(EduMaterial eduMaterial)
    {
        return eduMaterialMapper.updateEduMaterial(eduMaterial);
    }

    @Override
    public int deleteEduMaterialByMaterialIds(Long[] materialIds)
    {
        return eduMaterialMapper.deleteEduMaterialByMaterialIds(materialIds);
    }

    @Override
    public int deleteEduMaterialByMaterialId(Long materialId)
    {
        return eduMaterialMapper.deleteEduMaterialByMaterialId(materialId);
    }
}
