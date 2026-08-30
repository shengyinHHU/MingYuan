package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.EduScheduleAdjustment;
import com.ruoyi.system.domain.EduScheduleAdjustmentBatch;

/**
 * 排课调课记录Service接口
 *
 * @author ruoyi
 * @date 2026-08-26
 */
public interface IEduScheduleAdjustmentService
{
    /**
     * 查询排课调课记录
     *
     * @param adjustmentId 排课调课记录主键
     * @return 排课调课记录
     */
    public EduScheduleAdjustment selectEduScheduleAdjustmentByAdjustmentId(Long adjustmentId);

    /**
     * 查询排课调课记录列表
     *
     * @param eduScheduleAdjustment 排课调课记录
     * @return 排课调课记录集合
     */
    public List<EduScheduleAdjustment> selectEduScheduleAdjustmentList(EduScheduleAdjustment eduScheduleAdjustment);

    /**
     * 新增排课调课记录
     *
     * @param eduScheduleAdjustment 排课调课记录
     * @return 结果
     */
    public int insertEduScheduleAdjustment(EduScheduleAdjustment eduScheduleAdjustment);

    /**
     * 修改排课调课记录
     *
     * @param eduScheduleAdjustment 排课调课记录
     * @return 结果
     */
    public int updateEduScheduleAdjustment(EduScheduleAdjustment eduScheduleAdjustment);

    /**
     * 批量删除排课调课记录
     *
     * @param adjustmentIds 需要删除的排课调课记录主键集合
     * @return 结果
     */
    public int deleteEduScheduleAdjustmentByIds(Long[] adjustmentIds);

    /**
     * 删除排课调课记录信息
     *
     * @param adjustmentId 排课调课记录主键
     * @return 结果
     */
    public int deleteEduScheduleAdjustmentByAdjustmentId(Long adjustmentId);

    /**
     * 批量调课：按学期+期次（可叠加年级/科目筛选）把原日期范围内的全部上课日
     * 统一调整到新日期或停课，逐排课×逐上课日生成调课记录
     *
     * @param batch 批量调课参数
     * @return 生成的调课记录条数
     */
    public int batchAdjust(EduScheduleAdjustmentBatch batch);
}
