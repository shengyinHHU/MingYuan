package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.EduTeacherSalaryConfig;

/**
 * 教师薪资标准 数据层
 *
 * @author ruoyi
 * @date 2026-09-04
 */
public interface EduTeacherSalaryConfigMapper
{
    public List<EduTeacherSalaryConfig> selectConfigList(EduTeacherSalaryConfig config);

    public EduTeacherSalaryConfig selectConfigById(Long configId);

    public EduTeacherSalaryConfig selectConfigByTeacherId(@Param("teacherId") Long teacherId);

    public int insertConfig(EduTeacherSalaryConfig config);

    public int updateConfig(EduTeacherSalaryConfig config);

    public int deleteConfigByIds(Long[] configIds);
}
