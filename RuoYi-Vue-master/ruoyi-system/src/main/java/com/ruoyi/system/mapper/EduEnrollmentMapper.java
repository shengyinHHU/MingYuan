package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.EduEnrollment;

/**
 * 课程报名Mapper接口
 * 
 * @author ruoyi
 * @date 2026-08-05
 */
public interface EduEnrollmentMapper 
{
    /**
     * 查询课程报名
     * 
     * @param enrollmentId 课程报名主键
     * @return 课程报名
     */
    public EduEnrollment selectEduEnrollmentByEnrollmentId(Long enrollmentId);

    /**
     * 查询课程报名列表
     * 
     * @param eduEnrollment 课程报名
     * @return 课程报名集合
     */
    public List<EduEnrollment> selectEduEnrollmentList(EduEnrollment eduEnrollment);

    /**
     * 新增课程报名
     * 
     * @param eduEnrollment 课程报名
     * @return 结果
     */
    public int insertEduEnrollment(EduEnrollment eduEnrollment);

    /**
     * 修改课程报名
     * 
     * @param eduEnrollment 课程报名
     * @return 结果
     */
    public int updateEduEnrollment(EduEnrollment eduEnrollment);

    /** 原子取消；返回 0 表示未变更，不能再释放名额。 */
    public int cancelEnrollment(@Param("enrollmentId") Long enrollmentId, @Param("updateBy") String updateBy);

    /** 首次取消后释放一个名额，人数不低于零，满班有空位时恢复招生。 */
    public int releaseScheduleSeat(@Param("scheduleId") Long scheduleId, @Param("updateBy") String updateBy);

    /** 仅作废未使用的自动考勤，保留已出勤及课次签到历史。 */
    public int invalidateUnusedAttendance(@Param("enrollmentId") Long enrollmentId, @Param("updateBy") String updateBy);
}
