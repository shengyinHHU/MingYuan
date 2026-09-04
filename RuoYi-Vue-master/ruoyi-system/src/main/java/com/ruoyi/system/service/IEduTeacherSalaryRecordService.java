package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.EduTeacherSalaryRecord;

/**
 * 教师薪资结算 服务层
 *
 * @author ruoyi
 * @date 2026-09-04
 */
public interface IEduTeacherSalaryRecordService
{
    /** 结算单列表 */
    public List<EduTeacherSalaryRecord> selectRecordList(EduTeacherSalaryRecord query);

    public EduTeacherSalaryRecord selectRecordById(Long recordId);

    /** 按月预览（实时统计，不入库）：含已结算状态标记 */
    public List<EduTeacherSalaryRecord> previewMonth(String salaryMonth);

    /** 生成/重算月度结算（已确认的月份锁定），返回生成条数 */
    public int generateMonth(String salaryMonth);

    /** 确认结算单 */
    public int confirmRecord(Long recordId);

    /** 删除（仅待确认） */
    public int deleteRecordByIds(Long[] recordIds);
}
