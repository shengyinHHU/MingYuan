package com.ruoyi.system.shop;

import java.math.BigDecimal;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class ShopRepository {
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    public ShopRepository(DataSource source) {
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    public JdbcTemplate jdbc() {return jdbc;}
    public TransactionTemplate transactions() {return tx;}
    public List<Map<String,Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs,n) -> {
            Map<String,Object> row = new LinkedHashMap<>();
            ResultSetMetaData md=rs.getMetaData();
            for(int i=1;i<=md.getColumnCount();i++) {
                String key=camel(md.getColumnLabel(i));
                Object value=rs.getObject(i);
                if(value instanceof BigDecimal b) value=b.setScale(2).toPlainString();
                else if(value instanceof Timestamp t) value=t.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                else if(value instanceof java.time.LocalDateTime t) value=t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                else if(value instanceof Number && (key.endsWith("Id") || key.equals("fileSize") || key.equals("assetSize"))) value=value.toString();
                row.put(key,value);
            }
            return row;
        },args);
    }
    public Map<String,Object> one(String sql,Object... args) {
        var rows=rows(sql,args);
        if(rows.isEmpty()) throw new com.ruoyi.common.exception.ServiceException("记录不存在或无权访问");
        return rows.get(0);
    }
    public long insert(String table,Map<String,Object> values) {
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        String sql="INSERT INTO "+table+"("+String.join(",",values.keySet())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")";
        jdbc.update(c -> {
            PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            int i=1;for(Object value:values.values()) p.setObject(i++,value);
            return p;
        },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    public void update(String table,String idColumn,long id,Map<String,Object> values) {
        if(values.isEmpty()) return;
        List<Object> args=new ArrayList<>(values.values());args.add(id);
        jdbc.update("UPDATE "+table+" SET "+String.join(",",values.keySet().stream().map(k->k+"=?").toList())+" WHERE "+idColumn+"=?",args.toArray());
    }
    static String camel(String value) {
        StringBuilder out=new StringBuilder();boolean upper=false;
        for(char c:value.toCharArray()) {if(c=='_')upper=true;else {out.append(upper?Character.toUpperCase(c):c);upper=false;}}
        return out.toString();
    }
}
