package com.ruoyi.system.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.MiniAppMaterialBuyBody;
import com.ruoyi.common.core.domain.model.MiniAppMaterialUploadBody;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.system.domain.EduMaterial;
import com.ruoyi.system.domain.EduMaterialOrder;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.service.IMiniAppMaterialService;
import com.ruoyi.system.service.ISysDictDataService;

/**
 * MiniApp material APIs.
 *
 * Parent: browse / buy / download / my orders.
 * Teacher & Admin: upload / update / delete my materials.
 */
@RestController
@RequestMapping("/miniapp")
public class MiniAppMaterialController extends BaseController
{
    @Autowired
    private IMiniAppMaterialService miniAppMaterialService;

    @Autowired
    private ISysDictDataService sysDictDataService;

    /**
     * 资料商城字典：返回学科、年级选项。家长、教师、管理员均可访问。
     */
    @PreAuthorize("@ss.hasAnyRole('parent','teacher','admin')")
    @GetMapping("/material/dict")
    public AjaxResult dict()
    {
        SysDictData subjectQuery = new SysDictData();
        subjectQuery.setDictType("edu_subject");
        subjectQuery.setStatus("0");
        List<SysDictData> subjects = sysDictDataService.selectDictDataList(subjectQuery);

        SysDictData gradeQuery = new SysDictData();
        gradeQuery.setDictType("edu_grade");
        gradeQuery.setStatus("0");
        List<SysDictData> grades = sysDictDataService.selectDictDataList(gradeQuery);

        Map<String, Object> data = new HashMap<>(2);
        data.put("subjects", subjects);
        data.put("grades", grades);
        return success(data);
    }

    // ============== 家长端 ==============

    @PreAuthorize("@ss.hasRole('parent')")
    @GetMapping("/parent/material/list")
    public AjaxResult list(@RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "subjectName", required = false) String subjectName,
            @RequestParam(value = "gradeName", required = false) String gradeName)
    {
        return success(miniAppMaterialService.selectOnSaleMaterialsForParent(title, subjectName, gradeName));
    }

    @PreAuthorize("@ss.hasRole('parent')")
    @GetMapping("/parent/material/{materialId}")
    public AjaxResult detail(@PathVariable("materialId") Long materialId)
    {
        return success(miniAppMaterialService.selectMaterialDetailForParent(materialId));
    }

    @PreAuthorize("@ss.hasRole('parent')")
    @GetMapping("/parent/material/orders")
    public AjaxResult myOrders()
    {
        return success(miniAppMaterialService.selectMyOrders());
    }

    @PreAuthorize("@ss.hasRole('parent')")
    @PostMapping("/parent/material/buy")
    public AjaxResult buy(@RequestBody MiniAppMaterialBuyBody body)
    {
        Long orderId = miniAppMaterialService.buyMaterial(body);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("orderId", orderId);
        ajax.put("payStatus", "1");
        return ajax;
    }

    /**
     * 家长下载已购买的资料。校验订单已支付后输出文件流。
     */
    @PreAuthorize("@ss.hasRole('parent')")
    @GetMapping("/parent/material/download/{orderId}")
    public void download(@PathVariable("orderId") Long orderId,
            HttpServletRequest request, HttpServletResponse response)
    {
        try
        {
            EduMaterialOrder order = miniAppMaterialService.selectDownloadableOrder(orderId);
            String resource = order.getMaterialFilePath();
            if (!FileUtils.checkAllowDownload(resource))
            {
                throw new RuntimeException("资料文件（" + resource + "）非法，不允许下载");
            }
            String localPath = RuoYiConfig.getProfile();
            String downloadPath = localPath + FileUtils.stripPrefix(resource);
            String downloadName = order.getMaterialFileName();
            if (downloadName == null || downloadName.isEmpty())
            {
                downloadName = com.ruoyi.common.utils.StringUtils
                        .substringAfterLast(downloadPath, "/");
            }
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            FileUtils.writeBytes(downloadPath, response.getOutputStream());
        }
        catch (Exception e)
        {
            logger.error("下载资料文件失败", e);
            try
            {
                response.reset();
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().print("{\"code\":500,\"msg\":\"" + e.getMessage() + "\"}");
            }
            catch (Exception ignored)
            {
            }
        }
    }

    // ============== 教师 / 管理员端 ==============

    @PreAuthorize("@ss.hasAnyRole('teacher','admin')")
    @GetMapping("/teacher/material/list")
    public AjaxResult myUploads(@RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "subjectName", required = false) String subjectName,
            @RequestParam(value = "gradeName", required = false) String gradeName)
    {
        return success(miniAppMaterialService.selectMyUploads(title, subjectName, gradeName));
    }

    @PreAuthorize("@ss.hasAnyRole('teacher','admin')")
    @PostMapping("/teacher/material")
    public AjaxResult upload(@RequestBody MiniAppMaterialUploadBody body)
    {
        return toAjax(miniAppMaterialService.uploadMaterial(body));
    }

    @PreAuthorize("@ss.hasAnyRole('teacher','admin')")
    @PutMapping("/teacher/material")
    public AjaxResult edit(@RequestBody EduMaterial material)
    {
        return toAjax(miniAppMaterialService.updateMyMaterial(material));
    }

    @PreAuthorize("@ss.hasAnyRole('teacher','admin')")
    @DeleteMapping("/teacher/material/{materialId}")
    public AjaxResult remove(@PathVariable("materialId") Long materialId)
    {
        return toAjax(miniAppMaterialService.deleteMyMaterial(materialId));
    }
}
