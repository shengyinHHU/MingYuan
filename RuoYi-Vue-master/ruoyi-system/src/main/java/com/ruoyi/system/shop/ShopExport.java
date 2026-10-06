package com.ruoyi.system.shop;
import java.util.*;
import java.util.function.Function;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.ruoyi.common.utils.file.FileUtils;
public final class ShopExport {
    private ShopExport(){}
    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> collect(Map<String,String> query,Function<Map<String,String>,Map<String,Object>> load) {
        List<Map<String,Object>> rows=new ArrayList<>();var q=new HashMap<>(query);q.put("pageSize","100");
        for(int page=1;;page++){q.put("pageNum",String.valueOf(page));var data=load.apply(q);rows.addAll((List<Map<String,Object>>)data.get("rows"));if(rows.size()>=((Number)data.get("total")).longValue())return rows;}
    }
    public static void write(HttpServletResponse response,String name,List<Map<String,Object>> data,List<String> keys)throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        FileUtils.setAttachmentResponseHeader(response,name+".xlsx");
        try(var workbook=new XSSFWorkbook()){
            var sheet=workbook.createSheet(name);var header=sheet.createRow(0);
            for(int i=0;i<keys.size();i++)header.createCell(i).setCellValue(keys.get(i));
            for(int n=0;n<data.size();n++){var row=sheet.createRow(n+1);for(int i=0;i<keys.size();i++)row.createCell(i).setCellValue(ShopService.text(data.get(n).get(keys.get(i))));}
            sheet.createFreezePane(0,1);workbook.write(response.getOutputStream());
        }
    }
}
