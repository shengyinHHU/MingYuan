package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.EduMaterial;

/**
 * 资料商品Service接口
 *
 * @author ruoyi
 * @date 2026-08-21
 */
public interface IEduMaterialService
{
    /**
     * 查询资料商品
     *
     * @param materialId 资料商品主键
     * @return 资料商品
     */
    public EduMaterial selectEduMaterialByMaterialId(Long materialId);

    /**
     * 查询资料商品列表
     *
     * @param eduMaterial 资料商品
     * @return 资料商品集合
     */
    public List<EduMaterial> selectEduMaterialList(EduMaterial eduMaterial);

    /**
     * 新增资料商品
     *
     * @param eduMaterial 资料商品
     * @return 结果
     */
    public int insertEduMaterial(EduMaterial eduMaterial);

    /**
     * 修改资料商品
     *
     * @param eduMaterial 资料商品
     * @return 结果
     */
    public int updateEduMaterial(EduMaterial eduMaterial);

    /**
     * 批量删除资料商品
     *
     * @param materialIds 需要删除的资料商品主键集合
     * @return 结果
     */
    public int deleteEduMaterialByMaterialIds(Long[] materialIds);

    /**
     * 删除资料商品
     *
     * @param materialId 资料商品主键
     * @return 结果
     */
    public int deleteEduMaterialByMaterialId(Long materialId);
}
