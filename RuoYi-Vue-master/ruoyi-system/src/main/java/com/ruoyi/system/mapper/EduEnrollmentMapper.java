package com.ruoyi.system.mapper;

import java.util.List;
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

    /**
     * 删除课程报名
     * 
     * @param enrollmentId 课程报名主键
     * @return 结果
     */
    public int deleteEduEnrollmentByEnrollmentId(Long enrollmentId);

    /**
     * 批量删除课程报名
     * 
     * @param enrollmentIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteEduEnrollmentByEnrollmentIds(Long[] enrollmentIds);
}
