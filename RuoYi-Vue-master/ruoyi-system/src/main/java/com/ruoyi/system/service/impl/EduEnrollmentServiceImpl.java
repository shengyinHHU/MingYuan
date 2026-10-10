package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    @Autowired private com.ruoyi.system.mapper.EduCourseScheduleMapper scheduleMapper;

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
        var schedule = scheduleMapper.selectEduCourseScheduleByScheduleId(eduEnrollment.getScheduleId());
        if (schedule != null && "2".equals(schedule.getClassMode())) throw new ServiceException("一对一须选择具体日期提交，不能作为班课代报名");
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
        if (eduEnrollment.getEnrollmentId() == null)
        {
            throw new ServiceException("报名ID不能为空");
        }
        EduEnrollment current = eduEnrollmentMapper.selectEduEnrollmentByEnrollmentId(eduEnrollment.getEnrollmentId());
        if (current == null)
        {
            throw new ServiceException("报名记录不存在");
        }
        if ((eduEnrollment.getEnrollmentCode() != null && !Objects.equals(eduEnrollment.getEnrollmentCode(), current.getEnrollmentCode()))
                || (eduEnrollment.getScheduleId() != null && !Objects.equals(eduEnrollment.getScheduleId(), current.getScheduleId()))
                || (eduEnrollment.getParentId() != null && !Objects.equals(eduEnrollment.getParentId(), current.getParentId())))
        {
            throw new ServiceException("报名编码、排课和家长不可通过修改报名信息变更");
        }
        if (current.getClassDate() != null) {
            if (eduEnrollment.getPayStatus() != null && !java.util.List.of("0","未支付").contains(eduEnrollment.getPayStatus())) throw new ServiceException("单次一对一当前不处理支付");
            eduEnrollment.setPayStatus(null);
        }
        // 支付状态等局部更新不会提交这些字段；普通编辑也不允许修改报名归属。
        eduEnrollment.setEnrollmentCode(null);
        eduEnrollment.setScheduleId(null);
        eduEnrollment.setParentId(null);
        // 取消状态及时间只能由取消事务维护，避免旧编辑窗口恢复已取消报名。
        eduEnrollment.setEnrollmentStatus(null);
        eduEnrollment.setDelFlag(null);
        eduEnrollment.setCancelTime(null);
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
    public int cancelEduEnrollmentByEnrollmentIds(Long[] enrollmentIds)
    {
        if (enrollmentIds == null || enrollmentIds.length == 0
                || Arrays.stream(enrollmentIds).anyMatch(Objects::isNull))
        {
            throw new ServiceException("请选择要取消的报名");
        }
        String updateBy = SecurityUtils.getUsername();
        int count = 0;
        // 固定批量加锁顺序；去掉重复 ID，避免重复释放名额。
        for (Long enrollmentId : Arrays.stream(enrollmentIds).distinct().sorted().toArray(Long[]::new))
        {
            EduEnrollment current = eduEnrollmentMapper.selectEduEnrollmentByEnrollmentId(enrollmentId);
            if (current == null || !"0".equals(current.getDelFlag()))
            {
                throw new ServiceException("报名记录不存在或已被删除：" + enrollmentId);
            }
            if (current.getClassDate() != null) throw new ServiceException("单次一对一须由教师处理取消申请，不能直接取消");
            // 条件更新持有报名行锁；只有赢得首次取消的事务才处理名额和考勤。
            if (eduEnrollmentMapper.cancelEnrollment(enrollmentId, updateBy) == 0)
            {
                continue;
            }
            if (eduEnrollmentMapper.releaseScheduleSeat(current.getScheduleId(), updateBy) == 0)
            {
                throw new ServiceException("课程不存在，取消失败");
            }
            eduEnrollmentMapper.invalidateUnusedAttendance(enrollmentId, updateBy);
            count++;
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEduEnrollmentByEnrollmentIds(Long[] enrollmentIds)
    {
        return cancelEduEnrollmentByEnrollmentIds(enrollmentIds);
    }

    /**
     * 删除课程报名信息
     * 
     * @param enrollmentId 课程报名主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEduEnrollmentByEnrollmentId(Long enrollmentId)
    {
        return cancelEduEnrollmentByEnrollmentIds(new Long[] { enrollmentId });
    }
}
