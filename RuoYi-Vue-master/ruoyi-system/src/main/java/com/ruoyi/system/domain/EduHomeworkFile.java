package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class EduHomeworkFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long fileId; private Long homeworkId; private Long submissionId; private Long answerId;
    private String fileType; private String fileName; private String filePath; private Long fileSize;
    public Long getFileId(){return fileId;} public void setFileId(Long v){fileId=v;}
    public Long getHomeworkId(){return homeworkId;} public void setHomeworkId(Long v){homeworkId=v;}
    public Long getSubmissionId(){return submissionId;} public void setSubmissionId(Long v){submissionId=v;}
    public Long getAnswerId(){return answerId;} public void setAnswerId(Long v){answerId=v;}
    public String getFileType(){return fileType;} public void setFileType(String v){fileType=v;}
    public String getFileName(){return fileName;} public void setFileName(String v){fileName=v;}
    public String getFilePath(){return filePath;} public void setFilePath(String v){filePath=v;}
    public Long getFileSize(){return fileSize;} public void setFileSize(Long v){fileSize=v;}
}
