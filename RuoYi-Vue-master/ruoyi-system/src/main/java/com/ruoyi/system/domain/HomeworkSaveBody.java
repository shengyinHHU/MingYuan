package com.ruoyi.system.domain;

import java.util.List;

public class HomeworkSaveBody extends EduHomework
{
    private static final long serialVersionUID = 1L;
    private List<EduHomeworkQuestion> questions;
    private List<EduHomeworkFile> files;
    public List<EduHomeworkQuestion> getQuestions(){return questions;} public void setQuestions(List<EduHomeworkQuestion> v){questions=v;}
    public List<EduHomeworkFile> getFiles(){return files;} public void setFiles(List<EduHomeworkFile> v){files=v;}
}
