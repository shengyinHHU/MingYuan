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

    EduCourseSchedule lockSchedule(Long scheduleId);

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

    /**
     * 可视化课表：按 (classroomId + timeSlot) 匹配某学期期次的所有排课
     * 用于网格渲染：先取 年+学期+期次+可选筛选 的所有正常排课
     *
     * @param eduCourseSchedule 查询条件（courseYear/termName/periodName 必带，其余 campus/subject/grade/teacher 可选）
     * @return 排课集合
     */
    public List<EduCourseSchedule> selectTimetableGrid(EduCourseSchedule eduCourseSchedule);
}
