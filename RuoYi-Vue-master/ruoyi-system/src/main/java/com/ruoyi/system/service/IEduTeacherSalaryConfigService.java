package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.EduTeacherSalaryConfig;

/**
 * 教师薪资标准 服务层
 *
 * @author ruoyi
 * @date 2026-09-04
 */
public interface IEduTeacherSalaryConfigService
{
    public List<EduTeacherSalaryConfig> selectConfigList(EduTeacherSalaryConfig config);

    public EduTeacherSalaryConfig selectConfigById(Long configId);

    public int insertConfig(EduTeacherSalaryConfig config);

    public int updateConfig(EduTeacherSalaryConfig config);

    public int deleteConfigByIds(Long[] configIds);
}
