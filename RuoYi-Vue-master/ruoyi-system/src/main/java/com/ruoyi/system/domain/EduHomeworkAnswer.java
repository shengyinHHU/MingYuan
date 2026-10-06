package com.ruoyi.system.domain;

import java.util.List;
import com.ruoyi.common.core.domain.BaseEntity;

public class EduHomeworkAnswer extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long answerId; private Long submissionId; private Long questionId; private String answerText;
    private String correctFlag; private Integer score; private String teacherComment; private List<EduHomeworkFile> files;
    public Long getAnswerId(){return answerId;} public void setAnswerId(Long v){answerId=v;}
    public Long getSubmissionId(){return submissionId;} public void setSubmissionId(Long v){submissionId=v;}
    public Long getQuestionId(){return questionId;} public void setQuestionId(Long v){questionId=v;}
    public String getAnswerText(){return answerText;} public void setAnswerText(String v){answerText=v;}
    public String getCorrectFlag(){return correctFlag;} public void setCorrectFlag(String v){correctFlag=v;}
    public Integer getScore(){return score;} public void setScore(Integer v){score=v;}
    public String getTeacherComment(){return teacherComment;} public void setTeacherComment(String v){teacherComment=v;}
    public List<EduHomeworkFile> getFiles(){return files;} public void setFiles(List<EduHomeworkFile> v){files=v;}
}
