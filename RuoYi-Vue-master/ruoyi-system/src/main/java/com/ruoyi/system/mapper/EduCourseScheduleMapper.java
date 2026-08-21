package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.EduCourseSchedule;

/**
 * 课程排课Mapper接口
 * 
 * @author ruoyi
 * @date 2026-08-01
 */
public interface EduCourseScheduleMapper 
{
    /**
     * 查询课程排课
     * 
     * @param scheduleId 课程排课主键
     * @return 课程排课
     */
    public EduCourseSchedule selectEduCourseScheduleByScheduleId(Long scheduleId);

    /**
     * 查询课程排课列表
     * 
     * @param eduCourseSchedule 课程排课
     * @return 课程排课集合
     */
    public List<EduCourseSchedule> selectEduCourseScheduleList(EduCourseSchedule eduCourseSchedule);

    /**
     * 新增课程排课
     * 
     * @param eduCourseSchedule 课程排课
     * @return 结果
     */
    public int insertEduCourseSchedule(EduCourseSchedule eduCourseSchedule);

    /**
     * 修改课程排课
     * 
     * @param eduCourseSchedule 课程排课
     * @return 结果
     */
    public int updateEduCourseSchedule(EduCourseSchedule eduCourseSchedule);

    /**
     * 删除课程排课
     * 
     * @param scheduleId 课程排课主键
     * @return 结果
     */
    public int deleteEduCourseScheduleByScheduleId(Long scheduleId);

    /**
     * 批量删除课程排课
     * 
     * @param scheduleIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteEduCourseScheduleByScheduleIds(Long[] scheduleIds);
}
