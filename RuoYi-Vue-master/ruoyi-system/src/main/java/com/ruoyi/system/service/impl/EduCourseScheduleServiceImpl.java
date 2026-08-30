package com.ruoyi.system.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.mapper.EduCourseScheduleMapper;
import com.ruoyi.system.domain.EduCourseSchedule;
import com.ruoyi.system.service.IEduCourseScheduleService;

/**
 * 课程排课Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-08-01
 */
@Service
public class EduCourseScheduleServiceImpl implements IEduCourseScheduleService 
{
    @Autowired
    private EduCourseScheduleMapper eduCourseScheduleMapper;

    /**
     * 查询课程排课
     * 
     * @param scheduleId 课程排课主键
     * @return 课程排课
     */
    @Override
    public EduCourseSchedule selectEduCourseScheduleByScheduleId(Long scheduleId)
    {
        return eduCourseScheduleMapper.selectEduCourseScheduleByScheduleId(scheduleId);
    }

    /**
     * 查询课程排课列表
     * 
     * @param eduCourseSchedule 课程排课
     * @return 课程排课
     */
    @Override
    public List<EduCourseSchedule> selectEduCourseScheduleList(EduCourseSchedule eduCourseSchedule)
    {
        return eduCourseScheduleMapper.selectEduCourseScheduleList(eduCourseSchedule);
    }

    /**
     * 新增课程排课
     * 
     * @param eduCourseSchedule 课程排课
     * @return 结果
     */
    @Override
    public int insertEduCourseSchedule(EduCourseSchedule eduCourseSchedule)
    {
        eduCourseSchedule.setCreateTime(DateUtils.getNowDate());
        return eduCourseScheduleMapper.insertEduCourseSchedule(eduCourseSchedule);
    }

    /**
     * 修改课程排课
     * 
     * @param eduCourseSchedule 课程排课
     * @return 结果
     */
    @Override
    public int updateEduCourseSchedule(EduCourseSchedule eduCourseSchedule)
    {
        eduCourseSchedule.setUpdateTime(DateUtils.getNowDate());
        return eduCourseScheduleMapper.updateEduCourseSchedule(eduCourseSchedule);
    }

    /**
     * 批量删除课程排课
     * 
     * @param scheduleIds 需要删除的课程排课主键
     * @return 结果
     */
    @Override
    public int deleteEduCourseScheduleByScheduleIds(Long[] scheduleIds)
    {
        return eduCourseScheduleMapper.deleteEduCourseScheduleByScheduleIds(scheduleIds);
    }

    /**
     * 删除课程排课信息
     * 
     * @param scheduleId 课程排课主键
     * @return 结果
     */
    @Override
    public int deleteEduCourseScheduleByScheduleId(Long scheduleId)
    {
        return eduCourseScheduleMapper.deleteEduCourseScheduleByScheduleId(scheduleId);
    }

    @Override
    public List<EduCourseSchedule> selectTimetableGrid(EduCourseSchedule eduCourseSchedule)
    {
        return eduCourseScheduleMapper.selectTimetableGrid(eduCourseSchedule);
    }
}
