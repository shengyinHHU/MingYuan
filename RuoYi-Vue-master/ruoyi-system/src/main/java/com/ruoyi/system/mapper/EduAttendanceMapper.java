package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.EduAttendance;

/**
 * 课程上课记录Mapper接口
 * 
 * @author ruoyi
 * @date 2026-08-06
 */
public interface EduAttendanceMapper 
{
    /**
     * 查询课程上课记录
     * 
     * @param attendanceId 课程上课记录主键
     * @return 课程上课记录
     */
    public EduAttendance selectEduAttendanceByAttendanceId(Long attendanceId);

    /**
     * 查询课程上课记录列表
     * 
     * @param eduAttendance 课程上课记录
     * @return 课程上课记录集合
     */
    public List<EduAttendance> selectEduAttendanceList(EduAttendance eduAttendance);

    /**
     * 新增课程上课记录
     * 
     * @param eduAttendance 课程上课记录
     * @return 结果
     */
    public int insertEduAttendance(EduAttendance eduAttendance);

    /**
     * 修改课程上课记录
     * 
     * @param eduAttendance 课程上课记录
     * @return 结果
     */
    public int updateEduAttendance(EduAttendance eduAttendance);

    /**
     * 删除课程上课记录
     * 
     * @param attendanceId 课程上课记录主键
     * @return 结果
     */
    public int deleteEduAttendanceByAttendanceId(Long attendanceId);

    /**
     * 批量删除课程上课记录
     * 
     * @param attendanceIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteEduAttendanceByAttendanceIds(Long[] attendanceIds);
}
