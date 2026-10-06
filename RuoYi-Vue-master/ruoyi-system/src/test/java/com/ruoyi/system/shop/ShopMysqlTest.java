package com.ruoyi.system.shop;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShopMysqlTest {
    private static final String SCHEMA="codex_material_shop_test_20261004";
    ShopRepository db;
    ShopService shop;
    Path fileRoot;
    PrivateStorage storage;
    @BeforeAll void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("shop.mysql"), "Enable disposable MySQL suite with -Dshop.mysql=true");
        try(Connection c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/?serverTimezone=Asia/Shanghai","root","")) {
            c.createStatement().execute("DROP DATABASE IF EXISTS "+SCHEMA);
            c.createStatement().execute("CREATE DATABASE "+SCHEMA+" CHARACTER SET utf8mb4");
        }
        db=new ShopRepository(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/"+SCHEMA+"?serverTimezone=Asia/Shanghai","root",""));
        db.jdbc.execute("CREATE TABLE sys_user(user_id bigint PRIMARY KEY,user_name varchar(64),nick_name varchar(64))");
        db.jdbc.execute("INSERT INTO sys_user VALUES(1,'admin','管理员'),(2,'buyer','家长'),(3,'other','其他家长')");
        String dump=Files.readString(Path.of("../../database/Dump20260924.sql"));
        for(String table:List.of("sys_dict_type","sys_dict_data","edu_material","edu_material_order")) {
            Matcher m=Pattern.compile("CREATE TABLE `"+table+"` \\(.*?\\) ENGINE=.*?;",Pattern.DOTALL).matcher(dump);
            assertTrue(m.find());db.jdbc.execute(m.group());
        }
        db.jdbc.execute("INSERT INTO sys_dict_data(dict_type,dict_value,dict_label,status,dict_sort,remark) VALUES('edu_subject','math','数学','0',1,NULL),('edu_grade','g1','一年级','0',1,NULL),('edu_material_type','handout','运营讲义','1',73,'保留运营数据')");
        db.jdbc.update("INSERT INTO edu_material(material_id,material_code,title,subject_name,grade_name,price,file_path,file_name,upload_user_id,del_flag) VALUES(1,'LEGACY','已删除免费资料','数学','一年级',0,'/profile/missing.pdf','missing.pdf',1,'2')");
        db.jdbc.update("INSERT INTO edu_material_order(order_code,material_id,parent_id,amount,pay_status,pay_way) VALUES('LPAID',1,2,0,'1','0'),('LREFUND',1,3,12,'2','0'),('LUNPAID',1,1,5,'0','0')");
        db.jdbc.update("INSERT INTO edu_material(material_id,material_code,title,subject_name,grade_name,price,file_path,file_name,upload_user_id) VALUES(2,'LEGACY_REAL','真实支付历史PDF','math','g1',20,'/profile/upload/legacy-real.pdf','legacy-real.pdf',1)");
        db.jdbc.update("INSERT INTO edu_material_order(order_code,material_id,parent_id,amount,pay_status,pay_way,wx_transaction_id) VALUES('LWECHAT',2,2,20,'1','1','wx-trusted-historical-fixture')");
        String unapproved=runMigration("");
        assertTrue(unapproved.contains("Review and approve historical unpaid closure"),"Unapproved migration must refuse legacy unpaid closure: "+unapproved);
        assertEquals("0",db.one("SELECT pay_status FROM edu_material_order WHERE order_code='LUNPAID'").get("payStatus"));
        assertEquals("5.00",db.one("SELECT amount FROM edu_material_order WHERE order_code='LUNPAID'").get("amount"));
        assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM edu_shop_migration",Integer.class));
        String staleApproval=runMigration("SET @shop_legacy_unpaid_close_approval='REVIEWED_AND_APPROVED'; SET @shop_legacy_unpaid_reviewed_count=2; SET @shop_legacy_unpaid_review_note='operator reviewed an outdated count';\n");
        assertTrue(staleApproval.contains("Review and approve historical unpaid closure"),"A stale reviewed count must fail");
        migrate();migrate();
        fileRoot=Files.createTempDirectory(Path.of("/private/tmp"),"codex-material-shop-test-");
        new com.ruoyi.common.config.RuoYiConfig().setProfile(fileRoot.resolve("public").toString());
        Files.createDirectories(fileRoot.resolve("public/upload"));
        Files.writeString(fileRoot.resolve("public/upload/legacy-real.pdf"),"%PDF-1.7\nlegacy real payment fixture");
        storage=new PrivateStorage(fileRoot.resolve("private").toString());
        shop=new ShopService(db,storage,true);
    }
    void migrate() throws Exception {
        String output=runMigration("SET @shop_legacy_unpaid_close_approval='REVIEWED_AND_APPROVED'; SET @shop_legacy_unpaid_reviewed_count=1; SET @shop_legacy_unpaid_review_note='test operator reviewed LUNPAID expiry and approved closure';\n");
        assertTrue(output.isEmpty(),output);
    }
    String runMigration(String approval) throws Exception {
        Process p=new ProcessBuilder("mysql","--protocol=TCP","-h127.0.0.1","-uroot",SCHEMA).redirectErrorStream(true).start();
        try(var in=p.getOutputStream()) {in.write(approval.getBytes(java.nio.charset.StandardCharsets.UTF_8));in.write(Files.readAllBytes(Path.of("../../database/migrations/20261004_material_shop.sql")));}
        String output=new String(p.getInputStream().readAllBytes());
        p.waitFor();return output;
    }
    @AfterAll void cleanup() throws Exception {
        if(db!=null)try(Connection c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/","root","")) {
            c.createStatement().execute("DROP DATABASE IF EXISTS "+SCHEMA);
        }
        if(fileRoot!=null)try(var files=Files.walk(fileRoot)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}
    }
    long product(int stock) {
        db.jdbc.update("INSERT INTO edu_material(material_code,title,subject_name,grade_name,subject_code,grade_code,price,shipping_fee,file_path,file_name,upload_user_id,delivery_type,stock_quantity,purchase_limit,shelf_status,cover_url,detail_text) VALUES(?,'练习册','math','g1','math','g1',20,5,NULL,NULL,1,'PHYSICAL',?,99,'1','/profile/cover.png','详细介绍')",UUID.randomUUID().toString().replace("-",""),stock);
        return db.jdbc.queryForObject("SELECT MAX(material_id) FROM edu_material",Long.class);
    }
    Map<String,Object> address() {
        return new HashMap<>(Map.of("receiverName","张三","receiverPhone","13800138000","provinceName","江苏","cityName","南京","districtName","鼓楼","detailAddress","一号楼101"));
    }
    Map<String,Object> request(long id,int quantity,String key) {
        return new HashMap<>(Map.of("materialId",String.valueOf(id),"quantity",quantity,"expectedPrice","20.00","address",address(),"clientRequestId",key));
    }
    @Test void migrationPreservesDeletedFreeRefundedAndUnpaidFacts() {
        assertEquals(3,db.jdbc.queryForObject("SELECT COUNT(*) FROM edu_material_order_item WHERE material_id=1",Integer.class));
        assertEquals("COMPLETED",db.one("SELECT * FROM edu_material_order WHERE order_code='LPAID'").get("orderStatus"));
        assertEquals("12.00",db.one("SELECT * FROM edu_material_order WHERE order_code='LREFUND'").get("refundedAmount"));
        assertEquals("0.00",db.one("SELECT * FROM edu_material_order WHERE order_code='LUNPAID'").get("amount"));
        assertEquals("math",db.one("SELECT * FROM edu_material WHERE material_id=1").get("subjectCode"));
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM edu_shop_migration",Integer.class));
        assertTrue(ShopService.text(db.one("SELECT close_reason FROM edu_material_order WHERE order_code='LUNPAID'").get("closeReason")).contains("test operator reviewed LUNPAID expiry"));
        assertTrue(ShopService.text(db.one("SELECT l.remark FROM edu_material_order_log l JOIN edu_material_order o USING(order_id) WHERE o.order_code='LUNPAID' AND event_type='MIGRATION'").get("remark")).contains("REVIEWED_UNPAID_CLOSURE"));
    }
    @Test void materialTypeMigrationAddsOnlyMissingDefaultsAndPreservesOperatorRowsOnReplay() throws Exception {
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_type WHERE dict_type='edu_material_type'",Integer.class),"Migration must provision the material type dictionary");
        assertEquals("资料类型",db.one("SELECT dict_name FROM sys_dict_type WHERE dict_type='edu_material_type'").get("dictName"));
        assertEquals(4,db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_data WHERE dict_type='edu_material_type'",Integer.class));
        var handout=db.one("SELECT dict_label,status,dict_sort,remark FROM sys_dict_data WHERE dict_type='edu_material_type' AND dict_value='handout'");
        assertEquals("运营讲义",handout.get("dictLabel"));assertEquals("1",handout.get("status"));assertEquals(73,handout.get("dictSort"));assertEquals("保留运营数据",handout.get("remark"));
        for(var entry:Map.of("exam","试卷","workbook","练习册","other","其他").entrySet())
            assertEquals(entry.getValue(),db.one("SELECT dict_label FROM sys_dict_data WHERE dict_type='edu_material_type' AND dict_value=?",entry.getKey()).get("dictLabel"));
        db.jdbc.update("UPDATE sys_dict_type SET dict_name='运营自定类型',status='1',remark='不可重置' WHERE dict_type='edu_material_type'");
        db.jdbc.update("UPDATE sys_dict_data SET dict_label='运营试卷',status='1',dict_sort=88,remark='不可重置' WHERE dict_type='edu_material_type' AND dict_value='exam'");
        try {
            migrate();migrate();
            var type=db.one("SELECT dict_name,status,remark FROM sys_dict_type WHERE dict_type='edu_material_type'");
            assertEquals("运营自定类型",type.get("dictName"));assertEquals("1",type.get("status"));assertEquals("不可重置",type.get("remark"));
            var exam=db.one("SELECT dict_label,status,dict_sort,remark FROM sys_dict_data WHERE dict_type='edu_material_type' AND dict_value='exam'");
            assertEquals("运营试卷",exam.get("dictLabel"));assertEquals("1",exam.get("status"));assertEquals(88,exam.get("dictSort"));assertEquals("不可重置",exam.get("remark"));
            assertEquals(4,db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_data WHERE dict_type='edu_material_type'",Integer.class));
            assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_type WHERE dict_type='edu_material_type'",Integer.class));
        } finally {
            db.jdbc.update("UPDATE sys_dict_type SET dict_name='资料类型',status='0',remark=NULL WHERE dict_type='edu_material_type'");
            db.jdbc.update("UPDATE sys_dict_data SET dict_label='试卷',status='0',dict_sort=2,remark=NULL WHERE dict_type='edu_material_type' AND dict_value='exam'");
        }
    }
    @Test void miniMaterialDictionaryReturnsOnlyActiveMaterialTypeCodesFromRealMapper() throws Exception {
        var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("dict-test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),db.jdbc().getDataSource()));
        config.getTypeAliasRegistry().registerAlias("SysDictData",com.ruoyi.common.core.domain.entity.SysDictData.class);
        try(var xml=Files.newInputStream(Path.of("src/main/resources/mapper/system/SysDictDataMapper.xml"))) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(xml,config,"mapper/system/SysDictDataMapper.xml",config.getSqlFragments()).parse();
        }
        try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config).openSession()) {
            var dictService=new com.ruoyi.system.service.impl.SysDictDataServiceImpl();
            var field=dictService.getClass().getDeclaredField("dictDataMapper");field.setAccessible(true);
            field.set(dictService,session.getMapper(com.ruoyi.system.mapper.SysDictDataMapper.class));
            var controller=new com.ruoyi.system.controller.MiniAppMaterialController(shop,storage,dictService);
            var data=(Map<String,Object>)controller.dict().get("data");
            assertTrue(data.containsKey("materialTypes"),"Dictionary API must expose materialTypes");
            var types=(List<com.ruoyi.common.core.domain.entity.SysDictData>)data.get("materialTypes");
            assertEquals(List.of("exam","workbook","other"),types.stream().map(com.ruoyi.common.core.domain.entity.SysDictData::getDictValue).toList());
            assertEquals(List.of("试卷","练习册","其他"),types.stream().map(com.ruoyi.common.core.domain.entity.SysDictData::getDictLabel).toList());
            assertEquals("math",((List<com.ruoyi.common.core.domain.entity.SysDictData>)data.get("subjects")).get(0).getDictValue());
            assertEquals("g1",((List<com.ruoyi.common.core.domain.entity.SysDictData>)data.get("grades")).get(0).getDictValue());
        }
    }
    @Test void suppliedMaterialTypeRequiresActiveDictionaryCodeForAdminAndTeacherButBlankAndOmittedRemainCompatible() {
        for(boolean teacher:List.of(false,true)) {
            long user=teacher?3:1;
            int before=db.jdbc.queryForObject("SELECT COUNT(*) FROM edu_material",Integer.class);
            for(String invalid:List.of("unrestricted text","试卷","handout"))
                assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.saveProduct(user,new HashMap<>(Map.of("materialType",invalid)),teacher));
            assertEquals(before,db.jdbc.queryForObject("SELECT COUNT(*) FROM edu_material",Integer.class));
            var draft=shop.saveProduct(user,new HashMap<>(Map.of("materialType","exam")),teacher);
            assertEquals("exam",draft.get("materialType"));
            assertDoesNotThrow(()->shop.saveProduct(user,new HashMap<>(),teacher));
            var blank=shop.saveProduct(user,new HashMap<>(Map.of("materialType","")),teacher);assertEquals("",blank.get("materialType"));
            long material=ShopService.id(draft.get("materialId"));
            db.jdbc.update("UPDATE edu_material SET material_type='legacy-operator-text' WHERE material_id=?",material);
            var omitted=shop.saveProduct(user,new HashMap<>(Map.of("materialId",draft.get("materialId"),"version",draft.get("version"),"title","只改名称")),teacher);
            assertEquals("legacy-operator-text",omitted.get("materialType"));
            assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.saveProduct(user,new HashMap<>(Map.of("materialId",omitted.get("materialId"),"version",omitted.get("version"),"materialType","handout")),teacher));
        }
    }
    @Test void materialTypePersistsValidatedTrimmedCodeForAdminAndTeacher() {
        for(boolean teacher:List.of(false,true)) {
            long user=teacher?3:1;
            var created=shop.saveProduct(user,new HashMap<>(Map.of("materialType"," exam ")),teacher);
            assertEquals("exam",created.get("materialType"));
            assertEquals("exam",db.one("SELECT material_type FROM edu_material WHERE material_id=?",ShopService.id(created.get("materialId"))).get("materialType"));
            var edited=shop.saveProduct(user,new HashMap<>(Map.of("materialId",created.get("materialId"),"version",created.get("version"),"materialType"," workbook ")),teacher);
            assertEquals("workbook",edited.get("materialType"));
            var blank=shop.saveProduct(user,new HashMap<>(Map.of("materialId",edited.get("materialId"),"version",edited.get("version"),"materialType","   ")),teacher);
            assertEquals("",blank.get("materialType"));
        }
    }
    @Test void adminListOmitsDetailOnlyAddressButAuthorizedDetailPreservesIt() {
        long material=product(2);long order=ShopService.id(shop.createOrder(2,request(material,1,"list-privacy")).get("orderId"));
        var detail=shop.order(order,1,true);
        var list=shop.orders(Map.of("orderCode",detail.get("orderCode").toString()),1,true);
        var row=((List<Map<String,Object>>)list.get("rows")).get(0);
        for(String key:List.of("provinceName","cityName","districtName","detailAddress","adminRemark","buyerRemark","logs"))
            assertFalse(row.containsKey(key),"List permission must not expose detail field "+key);
        assertEquals("江苏",detail.get("provinceName"));assertEquals("一号楼101",detail.get("detailAddress"));
        assertEquals("138****8000",row.get("receiverPhone"));assertEquals("13800138000",detail.get("receiverPhone"));
        assertTrue(row.containsKey("items"));assertTrue(row.containsKey("payment"));assertTrue(row.containsKey("shipment"));
    }
    @Test void adminListMasksEveryAcceptedTelephoneLengthAndFormatting() {
        long material=product(40);
        for(int length=6;length<=32;length++) {
            String phone="1234567890".repeat(4).substring(0,length);
            var b=request(material,1,"phone-length-"+length);var a=address();a.put("receiverPhone",phone);b.put("address",a);
            var order=shop.createOrder(2,b);
            var row=((List<Map<String,Object>>)shop.orders(Map.of("orderCode",order.get("orderCode").toString()),1,true).get("rows")).get(0);
            assertNotEquals(phone,row.get("receiverPhone"),"Phone length "+length+" must be masked");
            assertTrue(row.get("receiverPhone").toString().contains("****"));
            assertEquals(phone,shop.order(ShopService.id(order.get("orderId")),1,true).get("receiverPhone"));
            if(length==6)assertEquals("1****6",row.get("receiverPhone"));
            if(length==7)assertEquals("1****7",row.get("receiverPhone"));
        }
        var b=request(material,1,"phone-format");var a=address();a.put("receiverPhone","123-4567");b.put("address",a);
        var o=shop.createOrder(2,b);
        var row=((List<Map<String,Object>>)shop.orders(Map.of("orderCode",o.get("orderCode").toString()),1,true).get("rows")).get(0);
        assertEquals("1****7",row.get("receiverPhone"));
    }
    @Test void migratedTrustedWechatCanDownloadInProductionWhileMockCannot() throws Exception {
        var production=new ShopService(db,storage,false);
        long wechat=ShopService.id(db.one("SELECT order_id FROM edu_material_order WHERE order_code='LWECHAT'").get("orderId"));
        assertEquals("%PDF-1.7\nlegacy real payment fixture",Files.readString(assertDoesNotThrow(()->production.download(2,wechat))));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->production.download(3,wechat));
        long mock=ShopService.id(db.one("SELECT order_id FROM edu_material_order WHERE order_code='LPAID'").get("orderId"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->production.download(2,mock));
        db.jdbc.update("UPDATE edu_material_payment SET wx_transaction_id=NULL WHERE order_id=?",wechat);
        try{assertThrows(com.ruoyi.common.exception.ServiceException.class,()->production.download(2,wechat));}
        finally{db.jdbc.update("UPDATE edu_material_payment SET wx_transaction_id='wx-trusted-historical-fixture' WHERE order_id=?",wechat);}
        db.jdbc.update("UPDATE edu_material_order SET pay_status='2',aftersale_status='REFUNDED',refunded_amount=amount WHERE order_id=?",wechat);
        try{assertThrows(com.ruoyi.common.exception.ServiceException.class,()->production.download(2,wechat));}
        finally{db.jdbc.update("UPDATE edu_material_order SET pay_status='1',aftersale_status='NONE',refunded_amount=0 WHERE order_id=?",wechat);}
    }
    @Test void purchasedDictionaryLabelsRemainStableAfterDictionaryChanges() {
        var p=shop.saveProduct(1,new HashMap<>(Map.of("title","字典快照","subjectCode","math","gradeCode","g1","deliveryType","PHYSICAL","price","20.00","coverUrl","/profile/cover.png","detailText","详情","stockQuantity",2,"shelfStatus","1")),false);
        long material=ShopService.id(p.get("materialId"));
        assertEquals("math",db.one("SELECT subject_name FROM edu_material WHERE material_id=?",material).get("subjectName"));
        long order=ShopService.id(shop.createOrder(2,request(material,1,"labels")).get("orderId"));
        var snapshot=db.one("SELECT subject_label,grade_label FROM edu_material_order_item WHERE order_id=?",order);
        assertEquals("数学",snapshot.get("subjectLabel"));assertEquals("一年级",snapshot.get("gradeLabel"));
        db.jdbc.update("UPDATE sys_dict_data SET dict_label='新数学' WHERE dict_type='edu_subject' AND dict_value='math'");
        db.jdbc.update("UPDATE sys_dict_data SET dict_label='新一年级' WHERE dict_type='edu_grade' AND dict_value='g1'");
        try {
            assertEquals("新数学",shop.product(material,2,false).get("subjectName"));
            var unchanged=db.one("SELECT subject_label,grade_label FROM edu_material_order_item WHERE order_id=?",order);
            assertEquals("数学",unchanged.get("subjectLabel"));assertEquals("一年级",unchanged.get("gradeLabel"));
            var historical=((List<Map<String,Object>>)shop.order(order,2,false).get("items")).get(0);
            assertEquals("数学",historical.get("subjectLabel"));assertEquals("一年级",historical.get("gradeLabel"));
        }finally {
            db.jdbc.update("UPDATE sys_dict_data SET dict_label='数学' WHERE dict_type='edu_subject' AND dict_value='math'");
            db.jdbc.update("UPDATE sys_dict_data SET dict_label='一年级' WHERE dict_type='edu_grade' AND dict_value='g1'");
        }
    }
    @Test void repositoryExceptionTranslationProxyPreservesTransactions() {
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(javax.sql.DataSource.class,()->db.jdbc.getDataSource());
            context.registerBean(org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor.class,
                ()->{var post=new org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor();post.setProxyTargetClass(true);return post;});
            context.register(ShopRepository.class);context.refresh();
            var proxied=context.getBean(ShopRepository.class);
            var service=new ShopService(proxied,storage,true);long material=product(2);
            var order=service.createOrder(2,request(material,1,"proxy"));
            assertEquals("WAIT_PAY",order.get("orderStatus"));
            assertEquals("25.00",service.pay(2,ShopService.id(order.get("orderId"))).get("amount"));
        }
    }
    @Test void privatePdfUploadIsOwnedSnapshotImmutableAndRefundRevokesAccess() throws Exception {
        var pdf=storage.upload(3,new TestPdf("first.pdf","%PDF-1.7\nfirst"));
        var body=new HashMap<String,Object>(Map.of("title","电子练习","deliveryType","DIGITAL","subjectCode","math","gradeCode","g1","coverUrl","/profile/cover.png","detailText","详情","price","20.00","shelfStatus","2"));
        body.putAll(pdf);body.put("fileSize","9999999");
        var draft=shop.saveProduct(3,body,true);long material=ShopService.id(draft.get("materialId"));
        assertEquals("14",draft.get("fileSize"));
        shop.shelf(1,material,"1");
        assertFalse(shop.product(material,2,false).containsKey("filePath"));
        var request=request(material,1,"digital");request.remove("address");
        long order=ShopService.id(shop.createOrder(2,request).get("orderId"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.download(2,order));
        shop.pay(2,order);
        assertEquals("%PDF-1.7\nfirst",Files.readString(shop.download(2,order)));
        var replacement=storage.upload(1,new TestPdf("second.pdf","%PDF-1.7\nsecond"));
        var updated=new HashMap<>(shop.product(material,1,true));updated.putAll(replacement);shop.saveProduct(1,updated,false);
        assertEquals("%PDF-1.7\nfirst",Files.readString(shop.download(2,order)));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.download(3,order));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.createOrder(2,new HashMap<>(Map.of("materialId",String.valueOf(material),"quantity",1,"expectedPrice","20.00","clientRequestId","digital-again"))));
        shop.deleteProduct(1,material,false);
        assertEquals("%PDF-1.7\nfirst",Files.readString(shop.download(2,order)));
        shop.refund(1,order,"演示");assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.download(2,order));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->storage.validateKey(ShopService.text(pdf.get("filePath")),2,false));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->storage.resolve("shop/3/../../etc/passwd"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->storage.upload(3,new TestPdf("fake.pdf","not pdf")));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->storage.resolve("/profile/missing.pdf"));
    }
    @Test void payingTwiceConsumesExactlyOnceAndKeepsHistoricalSnapshots() {
        long material=product(3);var request=request(material,2,"pay-snapshot");
        long order=ShopService.id(shop.createOrder(2,request).get("orderId"));
        db.jdbc.update("UPDATE edu_material SET title='改名',price=99,cover_url='/profile/new.png' WHERE material_id=?",material);
        shop.pay(2,order);var paid=shop.pay(2,order);
        assertEquals("45.00",paid.get("amount"));assertEquals("WAIT_SHIP",paid.get("orderStatus"));
        var item=((List<Map<String,Object>>)paid.get("items")).get(0);
        assertEquals("练习册",item.get("materialTitle"));assertEquals("20.00",item.get("unitPrice"));assertEquals("张三",paid.get("receiverName"));
        var p=db.one("SELECT * FROM edu_material WHERE material_id=?",material);
        assertEquals(1,p.get("stockQuantity"));assertEquals(0,p.get("stockLocked"));assertEquals(2,p.get("saleCount"));
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM edu_material_payment WHERE order_id=?",Integer.class,order));
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM edu_material_order_log WHERE order_id=? AND event_type='PAY_SUCCESS'",Integer.class,order));
        assertFalse(paid.containsKey("adminRemark"));assertFalse(item.containsKey("assetPath"));
    }
    @Test void rejectsFractionalOverflowNegativeAndTamperedRequestsWithoutReservation() {
        long material=product(10);
        for(Object quantity:List.of(0,-1,1.5,"999999999999999999","abc",100)) {
            var b=request(material,1,"bad-"+UUID.randomUUID());b.put("quantity",quantity);
            assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.createOrder(2,b));
        }
        for(Object price:List.of("bad","20.001","-2","NaN","999999999999999999")) {
            var b=request(material,1,"bad-price");b.put("expectedPrice",price);
            assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.createOrder(2,b));
        }
        var missing=request(material,1,"address-missing");missing.remove("address");
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.createOrder(2,missing));
        var priceChanged=request(material,1,"price-change");priceChanged.put("expectedPrice","19.00");
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.createOrder(2,priceChanged));
        var valid=request(material,1,"different-payload");shop.createOrder(2,valid);valid.put("quantity",2);
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.createOrder(2,valid));
        assertEquals(1,db.jdbc.queryForObject("SELECT stock_locked FROM edu_material WHERE material_id=?",Integer.class,material));
    }
    @Test void cancelledAndExpiredOrdersReleaseOnlyOnceAndCannotBePaid() {
        long material=product(4);long order=ShopService.id(shop.createOrder(2,request(material,2,"cancel")).get("orderId"));
        shop.cancel(2,order);shop.cancel(2,order);assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.pay(2,order));
        long expired=ShopService.id(shop.createOrder(2,request(material,2,"expire")).get("orderId"));
        db.jdbc.update("UPDATE edu_material_order SET expire_time=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE order_id=?",expired);
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.pay(2,expired));
        shop.expireOrders();shop.expireOrders();
        assertEquals("CLOSED",shop.order(expired,2,false).get("orderStatus"));
        assertEquals(0,db.jdbc.queryForObject("SELECT stock_locked FROM edu_material WHERE material_id=?",Integer.class,material));
        assertEquals(4,db.jdbc.queryForObject("SELECT stock_quantity FROM edu_material WHERE material_id=?",Integer.class,material));
    }
    @Test void oneRemainingUnitCannotBeSoldToTwoConcurrentBuyers() throws Exception {
        long material=product(1);var start=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Boolean>> attempts=new ArrayList<>();
            for(long user:List.of(2L,3L))attempts.add(pool.submit(()->{start.await();try{shop.createOrder(user,request(material,1,"race-"+user));return true;}catch(com.ruoyi.common.exception.ServiceException e){return false;}}));
            start.countDown();int successes=0;for(var f:attempts)if(f.get())successes++;
            assertEquals(1,successes);assertEquals(1,db.jdbc.queryForObject("SELECT stock_locked FROM edu_material WHERE material_id=?",Integer.class,material));
        }finally{pool.shutdownNow();}
    }
    @Test void payAndCancelRaceCannotLeaveHalfPaidInventory() throws Exception {
        long material=product(1);long order=ShopService.id(shop.createOrder(2,request(material,1,"pay-cancel-race")).get("orderId"));
        var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var pay=pool.submit(()->{start.await();try{shop.pay(2,order);}catch(com.ruoyi.common.exception.ServiceException ignored){}return null;});
            var cancel=pool.submit(()->{start.await();try{shop.cancel(2,order);}catch(com.ruoyi.common.exception.ServiceException ignored){}return null;});
            start.countDown();pay.get();cancel.get();
            var o=shop.order(order,2,false);var p=db.one("SELECT * FROM edu_material WHERE material_id=?",material);
            assertEquals(0,p.get("stockLocked"));
            if("1".equals(o.get("payStatus"))){assertEquals("WAIT_SHIP",o.get("orderStatus"));assertEquals(0,p.get("stockQuantity"));assertEquals(1,p.get("saleCount"));}
            else {assertEquals("CANCELLED",o.get("orderStatus"));assertEquals(1,p.get("stockQuantity"));assertEquals(0,p.get("saleCount"));}
        }finally{pool.shutdownNow();}
    }
    @Test void shippingAndReceivingAreIdempotentAndRefundAfterShippingIsRejected() {
        long material=product(3);long order=ShopService.id(shop.createOrder(2,request(material,2,"ship")).get("orderId"));
        var parcel=Map.<String,Object>of("carrierCode","SF","carrierName","顺丰","trackingNo","00123");
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.ship(1,order,parcel));
        shop.pay(2,order);shop.ship(1,order,parcel);shop.ship(1,order,parcel);
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.refund(1,order,"已发货"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.receive(3,order));
        shop.receive(2,order);var finished=shop.receive(2,order);assertEquals("COMPLETED",finished.get("orderStatus"));
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM edu_material_shipment WHERE order_id=?",Integer.class,order));
    }
    @Test void refundRestoresQuantityOnceAndNeverReversesHistoricalSales() {
        long material=product(3);long order=ShopService.id(shop.createOrder(2,request(material,2,"refund")).get("orderId"));
        shop.pay(2,order);shop.refund(1,order,"演示");var refunded=shop.refund(1,order,"演示重试");
        assertEquals("45.00",refunded.get("amount"));assertEquals("45.00",refunded.get("refundedAmount"));assertEquals("2",refunded.get("payStatus"));
        var p=db.one("SELECT * FROM edu_material WHERE material_id=?",material);
        assertEquals(3,p.get("stockQuantity"));assertEquals(0,p.get("stockLocked"));assertEquals(2,p.get("saleCount"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.pay(2,order));
    }
    @Test void addressesBelongToBuyerHaveOneDefaultAndNeverRewriteOrderSnapshot() {
        var a=shop.saveAddress(2,null,address());long address=ShopService.id(a.get("addressId"));
        var b=shop.saveAddress(2,null,address());long other=ShopService.id(b.get("addressId"));
        shop.defaultAddress(2,address);shop.defaultAddress(2,other);
        assertEquals(1,shop.addresses(2).stream().filter(row->"1".equals(String.valueOf(row.get("isDefault")))).count());
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.defaultAddress(3,address));
        long material=product(3);var request=request(material,1,"owned-address");request.remove("address");request.put("addressId",String.valueOf(address));
        long order=ShopService.id(shop.createOrder(2,request).get("orderId"));
        var changed=address();changed.put("receiverName","李四");shop.saveAddress(2,address,changed);shop.deleteAddress(2,address);
        assertEquals("张三",shop.order(order,2,false).get("receiverName"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.order(order,3,false));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.cancel(3,order));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.pay(3,order));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.deleteAddress(3,other));
        var foreign=request(material,1,"foreign-address");foreign.put("addressId",String.valueOf(other));foreign.remove("address");
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.createOrder(3,foreign));
    }
    @Test void draftsCanBeIncompleteButTeachersCannotPublishAndStockCannotUndercutReservations() {
        var draft=shop.saveProduct(3,new HashMap<>(),true);long draftId=ShopService.id(draft.get("materialId"));
        assertEquals("2",draft.get("shelfStatus"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.shelf(1,draftId,"1"));
        var edit=new HashMap<String,Object>();edit.put("materialId",draft.get("materialId"));edit.put("version",draft.get("version"));edit.put("shelfStatus","1");
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.saveProduct(3,edit,true));
        edit.put("shelfStatus","2");assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.saveProduct(2,edit,true));
        long material=product(3);shop.createOrder(2,request(material,2,"stock-floor"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.stock(1,material,Map.of("delta",-2,"remark","盘点")));
        var adjusted=shop.stock(1,material,Map.of("delta",1,"remark","补货"));assertEquals(4,adjusted.get("stockQuantity"));assertEquals(2,adjusted.get("stockLocked"));
        var stale=new HashMap<>(adjusted);shop.stock(1,material,Map.of("delta",1,"remark","补货"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.saveProduct(1,stale,false));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->shop.products(Map.of("sort","price;drop table"),2,false,false));
    }
    @Test void productionConfigurationCannotSimulatePaymentRefundOrDownload() {
        long material=product(3);long order=ShopService.id(shop.createOrder(2,request(material,1,"prod-gate")).get("orderId"));
        var production=new ShopService(db,new PrivateStorage("/private/tmp/codex-material-shop-private-tests"),false);
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->production.preparePayment(2,order));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->production.pay(2,order));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->production.refund(1,order,"演示"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->production.download(2,order));
    }

    @Test void createsUnpaidOrderWithServerMoneyAndOneReservation() {
        long id=product(3);
        Map<String,Object> order=assertDoesNotThrow(()->shop.createOrder(2,request(id,2,"create")));
        assertEquals("45.00",order.get("payableAmount"));
        assertEquals("0.00",order.get("amount"));
        assertEquals("WAIT_PAY",order.get("orderStatus"));
        assertEquals(2,db.jdbc.queryForObject("SELECT stock_locked FROM edu_material WHERE material_id=?",Integer.class,id));
        assertEquals(order.get("orderId"),shop.createOrder(2,request(id,2,"create")).get("orderId"));
        assertEquals(2,db.jdbc.queryForObject("SELECT stock_locked FROM edu_material WHERE material_id=?",Integer.class,id));
    }
}
