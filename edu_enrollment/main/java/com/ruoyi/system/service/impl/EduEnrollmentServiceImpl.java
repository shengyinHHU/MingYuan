package com.ruoyi.system.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.mapper.EduEnrollmentMapper;
import com.ruoyi.system.domain.EduEnrollment;
import com.ruoyi.system.service.IEduEnrollmentService;

/**
 * 课程报名Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-08-05
 */
@Service
public class EduEnrollmentServiceImpl implements IEduEnrollmentService 
{
    @Autowired
    private EduEnrollmentMapper eduEnrollmentMapper;

    /**
     * 查询课程报名
     * 
     * @param enrollmentId 课程报名主键
     * @return 课程报名
     */
    @Override
    public EduEnrollment selectEduEnrollmentByEnrollmentId(Long enrollmentId)
    {
        return eduEnrollmentMapper.selectEduEnrollmentByEnrollmentId(enrollmentId);
    }

    /**
     * 查询课程报名列表
     * 
     * @param eduEnrollment 课程报名
     * @return 课程报名
     */
    @Override
    public List<EduEnrollment> selectEduEnrollmentList(EduEnrollment eduEnrollment)
    {
        return eduEnrollmentMapper.selectEduEnrollmentList(eduEnrollment);
    }

    /**
     * 新增课程报名
     * 
     * @param eduEnrollment 课程报名
     * @return 结果
     */
    @Override
    public int insertEduEnrollment(EduEnrollment eduEnrollment)
    {
        eduEnrollment.setCreateTime(DateUtils.getNowDate());
        return eduEnrollmentMapper.insertEduEnrollment(eduEnrollment);
    }

    /**
     * 修改课程报名
     * 
     * @param eduEnrollment 课程报名
     * @return 结果
     */
    @Override
    public int updateEduEnrollment(EduEnrollment eduEnrollment)
    {
        eduEnrollment.setUpdateTime(DateUtils.getNowDate());
        return eduEnrollmentMapper.updateEduEnrollment(eduEnrollment);
    }

    /**
     * 批量删除课程报名
     * 
     * @param enrollmentIds 需要删除的课程报名主键
     * @return 结果
     */
    @Override
    public int deleteEduEnrollmentByEnrollmentIds(Long[] enrollmentIds)
    {
        return eduEnrollmentMapper.deleteEduEnrollmentByEnrollmentIds(enrollmentIds);
    }

    /**
     * 删除课程报名信息
     * 
     * @param enrollmentId 课程报名主键
     * @return 结果
     */
    @Override
    public int deleteEduEnrollmentByEnrollmentId(Long enrollmentId)
    {
        return eduEnrollmentMapper.deleteEduEnrollmentByEnrollmentId(enrollmentId);
    }
}
