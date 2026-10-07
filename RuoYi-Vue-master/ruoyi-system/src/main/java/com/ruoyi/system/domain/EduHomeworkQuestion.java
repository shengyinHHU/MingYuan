package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class EduHomeworkQuestion extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long questionId; private Long homeworkId; private String questionType; private String questionText;
    private String optionsJson; private String correctAnswer; private Integer score; private Integer sortNo;
    public Long getQuestionId(){return questionId;} public void setQuestionId(Long v){questionId=v;}
    public Long getHomeworkId(){return homeworkId;} public void setHomeworkId(Long v){homeworkId=v;}
    public String getQuestionType(){return questionType;} public void setQuestionType(String v){questionType=v;}
    public String getQuestionText(){return questionText;} public void setQuestionText(String v){questionText=v;}
    public String getOptionsJson(){return optionsJson;} public void setOptionsJson(String v){optionsJson=v;}
    public String getCorrectAnswer(){return correctAnswer;} public void setCorrectAnswer(String v){correctAnswer=v;}
    public Integer getScore(){return score;} public void setScore(Integer v){score=v;}
    public Integer getSortNo(){return sortNo;} public void setSortNo(Integer v){sortNo=v;}
}
