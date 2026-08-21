package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.EduMaterial;

/**
 * 资料商品Mapper接口
 *
 * @author ruoyi
 * @date 2026-08-21
 */
public interface EduMaterialMapper
{
    /**
     * 查询资料商品
     *
     * @param materialId 资料商品主键
     * @return 资料商品
     */
    public EduMaterial selectEduMaterialByMaterialId(Long materialId);

    /**
     * 查询资料商品详情（含上传者昵称、当前家长购买状态）
     *
     * @param materialId   资料ID
     * @param parentId     家长ID（用于 bought 字段计算，可为 null）
     * @return 资料商品
     */
    public EduMaterial selectEduMaterialDetail(@Param("materialId") Long materialId, @Param("parentId") Long parentId);

    /**
     * 查询资料商品列表
     *
     * @param eduMaterial 资料商品
     * @return 资料商品集合
     */
    public List<EduMaterial> selectEduMaterialList(EduMaterial eduMaterial);

    /**
     * 商城首页查询（仅上架）
     *
     * @param eduMaterial 查询条件（subjectName / gradeName / title 模糊）
     * @return 资料商品集合
     */
    public List<EduMaterial> selectOnSaleMaterials(EduMaterial eduMaterial);

    /**
     * 商城首页查询（仅上架 + 当前家长已购买标记）
     *
     * @param eduMaterial 查询条件
     * @param parentId    家长ID
     * @return 资料商品集合
     */
    public List<EduMaterial> selectOnSaleMaterialsForParent(@Param("eduMaterial") EduMaterial eduMaterial, @Param("parentId") Long parentId);

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
     * 销量 +1
     *
     * @param materialId 资料ID
     * @return 结果
     */
    public int incrSaleCount(Long materialId);

    /**
     * 删除资料商品
     *
     * @param materialId 资料商品主键
     * @return 结果
     */
    public int deleteEduMaterialByMaterialId(Long materialId);

    /**
     * 批量删除资料商品
     *
     * @param materialIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteEduMaterialByMaterialIds(Long[] materialIds);
}
