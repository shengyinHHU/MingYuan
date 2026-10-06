package com.ruoyi.system.domain;

import java.util.Date;
import com.ruoyi.common.core.domain.BaseEntity;

public class EduHomeworkSubmission extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long submissionId; private Long homeworkId; private Long enrollmentId; private Long parentId;
    private Date submitTime; private Integer autoScore; private Integer manualScore; private Integer finalScore;
    private String status; private String teacherComment; private Long reviewerId; private Date reviewTime;
    private String description;
    private String studentName; private String homeworkTitle;
    public Long getSubmissionId(){return submissionId;} public void setSubmissionId(Long v){submissionId=v;}
    public Long getHomeworkId(){return homeworkId;} public void setHomeworkId(Long v){homeworkId=v;}
    public Long getEnrollmentId(){return enrollmentId;} public void setEnrollmentId(Long v){enrollmentId=v;}
    public Long getParentId(){return parentId;} public void setParentId(Long v){parentId=v;}
    public Date getSubmitTime(){return submitTime;} public void setSubmitTime(Date v){submitTime=v;}
    public Integer getAutoScore(){return autoScore;} public void setAutoScore(Integer v){autoScore=v;}
    public Integer getManualScore(){return manualScore;} public void setManualScore(Integer v){manualScore=v;}
    public Integer getFinalScore(){return finalScore;} public void setFinalScore(Integer v){finalScore=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getTeacherComment(){return teacherComment;} public void setTeacherComment(String v){teacherComment=v;}
    public Long getReviewerId(){return reviewerId;} public void setReviewerId(Long v){reviewerId=v;}
    public Date getReviewTime(){return reviewTime;} public void setReviewTime(Date v){reviewTime=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getStudentName(){return studentName;} public void setStudentName(String v){studentName=v;}
    public String getHomeworkTitle(){return homeworkTitle;} public void setHomeworkTitle(String v){homeworkTitle=v;}
}
