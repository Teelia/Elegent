package com.datalabeling.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 数据库迁移工具
 * 用于执行SQL迁移脚本
 */
public class DatabaseMigrationUtil {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        try {
            System.out.println("开始执行数据库迁移...");

            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);

            // 1. 检查字段是否存在
            boolean columnExists = checkColumnExists(conn);
            System.out.println("字段 extractor_config 是否存在: " + columnExists);

            // 2. 添加字段（如果不存在）
            if (!columnExists) {
                addColumn(conn);
            } else {
                System.out.println("字段已存在，跳过添加");
            }

            // 3. 检查并创建索引
            createIndexIfNotExists(conn);

            // 4. 修复 type 字段长度
            fixTypeColumnLength(conn);

            System.out.println("数据库迁移执行成功！");

            conn.close();
        } catch (Exception e) {
            System.err.println("数据库迁移失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean checkColumnExists(Connection conn) throws Exception {
        java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "labels", "extractor_config");
        boolean exists = rs.next();
        rs.close();
        return exists;
    }

    private static void addColumn(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        String sql = "ALTER TABLE labels ADD COLUMN extractor_config JSON COMMENT '提取器配置（仅structured_extraction类型使用）'";
        System.out.println("执行SQL: " + sql);
        stmt.execute(sql);
        stmt.close();
        System.out.println("字段添加成功");
    }

    private static void createIndexIfNotExists(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        java.sql.ResultSet rs = stmt.executeQuery(
            "SELECT COUNT(*) as cnt FROM information_schema.statistics " +
            "WHERE table_schema = DATABASE() AND table_name = 'labels' AND index_name = 'idx_labels_type'"
        );
        rs.next();
        boolean exists = rs.getInt("cnt") > 0;
        rs.close();

        if (!exists) {
            String sql = "CREATE INDEX idx_labels_type ON labels(type)";
            System.out.println("执行SQL: " + sql);
            stmt.execute(sql);
            System.out.println("索引创建成功");
        } else {
            System.out.println("索引已存在，跳过创建");
        }
        stmt.close();
    }

    /**
     * 修复 type 字段长度（从 VARCHAR(20) 改为 VARCHAR(30)）
     * 用于支持 structured_extraction 类型
     */
    private static void fixTypeColumnLength(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        java.sql.ResultSet rs = stmt.executeQuery(
            "SELECT CHARACTER_MAXIMUM_LENGTH as len FROM information_schema.columns " +
            "WHERE table_schema = DATABASE() AND table_name = 'labels' AND column_name = 'type'"
        );
        rs.next();
        int currentLength = rs.getInt("len");
        rs.close();

        if (currentLength < 30) {
            String sql = "ALTER TABLE labels MODIFY COLUMN type VARCHAR(30) NOT NULL COMMENT '标签类型：classification(分类判断), extraction(LLM通用提取), structured_extraction(结构化号码提取)'";
            System.out.println("执行SQL: " + sql);
            stmt.execute(sql);
            System.out.println("type 字段长度修复成功（从 " + currentLength + " 改为 30）");
        } else {
            System.out.println("type 字段长度已是 " + currentLength + "，无需修复");
        }
        stmt.close();
    }
}
