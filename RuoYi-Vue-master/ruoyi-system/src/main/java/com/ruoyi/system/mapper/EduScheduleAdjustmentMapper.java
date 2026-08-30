package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.EduScheduleAdjustment;

/**
 * 排课调课记录Mapper接口
 *
 * @author ruoyi
 * @date 2026-08-26
 */
public interface EduScheduleAdjustmentMapper
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
     * 删除排课调课记录
     *
     * @param adjustmentId 排课调课记录主键
     * @return 结果
     */
    public int deleteEduScheduleAdjustmentByAdjustmentId(Long adjustmentId);

    /**
     * 批量删除排课调课记录
     *
     * @param adjustmentIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteEduScheduleAdjustmentByIds(Long[] adjustmentIds);

    /**
     * 按学期/期次（可叠加年级/科目筛选）查询参与批量调课的排课
     * 仅返回日期计算所需字段：scheduleId/startDate/endDate/classPattern/periodName
     */
    public List<Map<String, Object>> selectSchedulesForBatch(@Param("termName") String termName,
            @Param("periodName") String periodName, @Param("gradeName") String gradeName,
            @Param("subjectName") String subjectName);

    /**
     * 查询某排课已有调课记录的原日期集合（批量调课去重用）
     */
    public List<Date> selectAdjustedOriginalDates(@Param("scheduleId") Long scheduleId);
}
