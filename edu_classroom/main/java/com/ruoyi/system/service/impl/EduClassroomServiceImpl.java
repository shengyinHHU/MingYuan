package com.ruoyi.system.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.mapper.EduClassroomMapper;
import com.ruoyi.system.domain.EduClassroom;
import com.ruoyi.system.service.IEduClassroomService;

/**
 * 教室信息Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-08-01
 */
@Service
public class EduClassroomServiceImpl implements IEduClassroomService 
{
    @Autowired
    private EduClassroomMapper eduClassroomMapper;

    /**
     * 查询教室信息
     * 
     * @param classroomId 教室信息主键
     * @return 教室信息
     */
    @Override
    public EduClassroom selectEduClassroomByClassroomId(Long classroomId)
    {
        return eduClassroomMapper.selectEduClassroomByClassroomId(classroomId);
    }

    /**
     * 查询教室信息列表
     * 
     * @param eduClassroom 教室信息
     * @return 教室信息
     */
    @Override
    public List<EduClassroom> selectEduClassroomList(EduClassroom eduClassroom)
    {
        return eduClassroomMapper.selectEduClassroomList(eduClassroom);
    }

    /**
     * 新增教室信息
     * 
     * @param eduClassroom 教室信息
     * @return 结果
     */
    @Override
    public int insertEduClassroom(EduClassroom eduClassroom)
    {
        eduClassroom.setCreateTime(DateUtils.getNowDate());
        return eduClassroomMapper.insertEduClassroom(eduClassroom);
    }

    /**
     * 修改教室信息
     * 
     * @param eduClassroom 教室信息
     * @return 结果
     */
    @Override
    public int updateEduClassroom(EduClassroom eduClassroom)
    {
        eduClassroom.setUpdateTime(DateUtils.getNowDate());
        return eduClassroomMapper.updateEduClassroom(eduClassroom);
    }

    /**
     * 批量删除教室信息
     * 
     * @param classroomIds 需要删除的教室信息主键
     * @return 结果
     */
    @Override
    public int deleteEduClassroomByClassroomIds(Long[] classroomIds)
    {
        return eduClassroomMapper.deleteEduClassroomByClassroomIds(classroomIds);
    }

    /**
     * 删除教室信息信息
     * 
     * @param classroomId 教室信息主键
     * @return 结果
     */
    @Override
    public int deleteEduClassroomByClassroomId(Long classroomId)
    {
        return eduClassroomMapper.deleteEduClassroomByClassroomId(classroomId);
    }
}
