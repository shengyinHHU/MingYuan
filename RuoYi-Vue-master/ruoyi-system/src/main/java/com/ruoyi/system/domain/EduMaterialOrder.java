package com.ruoyi.system.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 资料订单对象 edu_material_order
 *
 * @author ruoyi
 * @date 2026-08-21
 */
public class EduMaterialOrder extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 订单ID */
    private Long orderId;

    /** 订单编码 */
    @Excel(name = "订单编码")
    private String orderCode;

    /** 资料ID */
    @Excel(name = "资料ID")
    private Long materialId;

    /** 家长用户ID */
    @Excel(name = "家长用户ID")
    private Long parentId;

    /** 实付金额 */
    @Excel(name = "实付金额")
    private BigDecimal amount;

    /** 支付状态（0未支付 1已支付 2已退款） */
    @Excel(name = "支付状态", readConverterExp = "0=未支付,1=已支付,2=已退款")
    private String payStatus;

    /** 支付时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "支付时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date payTime;

    /** 支付方式（0模拟 1微信） */
    @Excel(name = "支付方式", readConverterExp = "0=模拟,1=微信")
    private String payWay;

    /** 微信交易号 */
    @Excel(name = "微信交易号")
    private String wxTransactionId;

    /** 退款时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "退款时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date refundTime;

    /** 退款原因 */
    @Excel(name = "退款原因")
    private String refundReason;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 资料标题（关联查询，非表字段） */
    private String materialTitle;

    /** 资料文件路径（关联查询，非表字段，仅供家长下载使用） */
    private String materialFilePath;

    /** 资料原始文件名（关联查询，非表字段） */
    private String materialFileName;

    /** 家长昵称（关联查询，非表字段） */
    private String parentName;

    public void setOrderId(Long orderId)
    {
        this.orderId = orderId;
    }

    public Long getOrderId()
    {
        return orderId;
    }

    public void setOrderCode(String orderCode)
    {
        this.orderCode = orderCode;
    }

    public String getOrderCode()
    {
        return orderCode;
    }

    public void setMaterialId(Long materialId)
    {
        this.materialId = materialId;
    }

    public Long getMaterialId()
    {
        return materialId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }

    public BigDecimal getAmount()
    {
        return amount;
    }

    public void setPayStatus(String payStatus)
    {
        this.payStatus = payStatus;
    }

    public String getPayStatus()
    {
        return payStatus;
    }

    public void setPayTime(Date payTime)
    {
        this.payTime = payTime;
    }

    public Date getPayTime()
    {
        return payTime;
    }

    public void setPayWay(String payWay)
    {
        this.payWay = payWay;
    }

    public String getPayWay()
    {
        return payWay;
    }

    public void setWxTransactionId(String wxTransactionId)
    {
        this.wxTransactionId = wxTransactionId;
    }

    public String getWxTransactionId()
    {
        return wxTransactionId;
    }

    public void setRefundTime(Date refundTime)
    {
        this.refundTime = refundTime;
    }

    public Date getRefundTime()
    {
        return refundTime;
    }

    public void setRefundReason(String refundReason)
    {
        this.refundReason = refundReason;
    }

    public String getRefundReason()
    {
        return refundReason;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setMaterialTitle(String materialTitle)
    {
        this.materialTitle = materialTitle;
    }

    public String getMaterialTitle()
    {
        return materialTitle;
    }

    public void setMaterialFilePath(String materialFilePath)
    {
        this.materialFilePath = materialFilePath;
    }

    public String getMaterialFilePath()
    {
        return materialFilePath;
    }

    public void setMaterialFileName(String materialFileName)
    {
        this.materialFileName = materialFileName;
    }

    public String getMaterialFileName()
    {
        return materialFileName;
    }

    public void setParentName(String parentName)
    {
        this.parentName = parentName;
    }

    public String getParentName()
    {
        return parentName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("orderId", getOrderId())
            .append("orderCode", getOrderCode())
            .append("materialId", getMaterialId())
            .append("parentId", getParentId())
            .append("amount", getAmount())
            .append("payStatus", getPayStatus())
            .append("payTime", getPayTime())
            .append("payWay", getPayWay())
            .append("wxTransactionId", getWxTransactionId())
            .append("refundTime", getRefundTime())
            .append("refundReason", getRefundReason())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
