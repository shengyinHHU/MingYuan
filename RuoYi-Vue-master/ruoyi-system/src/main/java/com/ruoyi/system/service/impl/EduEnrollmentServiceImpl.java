package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.finance.FinanceEnrollmentGuard;
import com.ruoyi.system.finance.TuitionService;
import com.ruoyi.system.shop.ShopRepository;
import org.springframework.transaction.annotation.Transactional;
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
    @Autowired private TuitionService tuition;
    @Autowired private ShopRepository db;

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
    @Transactional(rollbackFor = Exception.class)
    public int insertEduEnrollment(EduEnrollment eduEnrollment)
    {
        FinanceEnrollmentGuard.prepareNew(eduEnrollment);
        db.one("SELECT schedule_id FROM edu_course_schedule WHERE schedule_id=? FOR UPDATE",eduEnrollment.getScheduleId());
        int parents=db.jdbc().queryForObject("SELECT COUNT(DISTINCT u.user_id) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.user_id JOIN sys_role r ON r.role_id=ur.role_id WHERE u.user_id=? AND u.status='0' AND u.del_flag='0' AND r.role_key='parent' AND r.status='0' AND r.del_flag='0'",Integer.class,eduEnrollment.getParentId());
        if(parents!=1)throw new ServiceException("必须选择正常启用的现有家长账号");
        int capacity=db.jdbc().update("UPDATE edu_course_schedule s JOIN edu_classroom c ON c.classroom_id=s.classroom_id SET s.enrolled_count=IFNULL(s.enrolled_count,0)+1 WHERE s.schedule_id=? AND s.del_flag='0' AND s.status='0' AND s.recruit_status='0' AND c.status='0' AND IFNULL(s.enrolled_count,0)<IFNULL(c.capacity,999999)",eduEnrollment.getScheduleId());
        if(capacity!=1)throw new ServiceException("课程不可报名或名额已满");
        eduEnrollment.setCreateBy(SecurityUtils.getUsername());eduEnrollment.setCancelTime(null);eduEnrollment.setCancelReason(null);
        eduEnrollment.setCreateTime(DateUtils.getNowDate());
        int count=eduEnrollmentMapper.insertEduEnrollment(eduEnrollment);
        tuition.initializeBill(eduEnrollment.getEnrollmentId(),SecurityUtils.getUserId());
        return count;
    }

    /**
     * 修改课程报名
     * 
     * @param eduEnrollment 课程报名
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateEduEnrollment(EduEnrollment eduEnrollment)
    {
        var identity=db.one("SELECT schedule_id FROM edu_enrollment WHERE enrollment_id=?",eduEnrollment.getEnrollmentId());
        db.one("SELECT schedule_id FROM edu_course_schedule WHERE schedule_id=? FOR UPDATE",identity.get("scheduleId"));
        db.one("SELECT enrollment_id FROM edu_enrollment WHERE enrollment_id=? FOR UPDATE",eduEnrollment.getEnrollmentId());
        FinanceEnrollmentGuard.checkUpdate(eduEnrollmentMapper.selectEduEnrollmentByEnrollmentId(eduEnrollment.getEnrollmentId()),eduEnrollment);
        eduEnrollment.setUpdateBy(SecurityUtils.getUsername());
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
    @Transactional(rollbackFor = Exception.class)
    public int deleteEduEnrollmentByEnrollmentIds(Long[] enrollmentIds)
    {
        if(enrollmentIds==null||enrollmentIds.length==0)throw new ServiceException("请选择报名记录");
        var sorted=Arrays.stream(enrollmentIds).distinct().map(eduEnrollmentMapper::selectEduEnrollmentByEnrollmentId).toList();
        if(sorted.stream().anyMatch(Objects::isNull))throw new ServiceException("部分报名记录不存在");
        int count=0;
        for(var record:sorted.stream().sorted(Comparator.comparing(EduEnrollment::getScheduleId).thenComparing(EduEnrollment::getEnrollmentId)).toList()) {
            tuition.cancelEnrollment(record.getEnrollmentId(),SecurityUtils.getUserId(),true);count++;
        }
        return count;
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
        tuition.cancelEnrollment(enrollmentId,SecurityUtils.getUserId(),true);
        return 1;
    }
}
