package com.ruoyi.system.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.mapper.EduAttendanceMapper;
import com.ruoyi.system.domain.EduAttendance;
import com.ruoyi.system.service.IEduAttendanceService;

/**
 * 课程上课记录Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-08-06
 */
@Service
public class EduAttendanceServiceImpl implements IEduAttendanceService 
{
    @Autowired
    private EduAttendanceMapper eduAttendanceMapper;

    /**
     * 查询课程上课记录
     * 
     * @param attendanceId 课程上课记录主键
     * @return 课程上课记录
     */
    @Override
    public EduAttendance selectEduAttendanceByAttendanceId(Long attendanceId)
    {
        return eduAttendanceMapper.selectEduAttendanceByAttendanceId(attendanceId);
    }

    /**
     * 查询课程上课记录列表
     * 
     * @param eduAttendance 课程上课记录
     * @return 课程上课记录
     */
    @Override
    public List<EduAttendance> selectEduAttendanceList(EduAttendance eduAttendance)
    {
        return eduAttendanceMapper.selectEduAttendanceList(eduAttendance);
    }

    /**
     * 新增课程上课记录
     * 
     * @param eduAttendance 课程上课记录
     * @return 结果
     */
    @Override
    public int insertEduAttendance(EduAttendance eduAttendance)
    {
        eduAttendance.setCreateTime(DateUtils.getNowDate());
        return eduAttendanceMapper.insertEduAttendance(eduAttendance);
    }

    /**
     * 修改课程上课记录
     * 
     * @param eduAttendance 课程上课记录
     * @return 结果
     */
    @Override
    public int updateEduAttendance(EduAttendance eduAttendance)
    {
        eduAttendance.setUpdateTime(DateUtils.getNowDate());
        return eduAttendanceMapper.updateEduAttendance(eduAttendance);
    }

    /**
     * 批量删除课程上课记录
     * 
     * @param attendanceIds 需要删除的课程上课记录主键
     * @return 结果
     */
    @Override
    public int deleteEduAttendanceByAttendanceIds(Long[] attendanceIds)
    {
        return eduAttendanceMapper.deleteEduAttendanceByAttendanceIds(attendanceIds);
    }

    /**
     * 删除课程上课记录信息
     * 
     * @param attendanceId 课程上课记录主键
     * @return 结果
     */
    @Override
    public int deleteEduAttendanceByAttendanceId(Long attendanceId)
    {
        return eduAttendanceMapper.deleteEduAttendanceByAttendanceId(attendanceId);
    }
}
