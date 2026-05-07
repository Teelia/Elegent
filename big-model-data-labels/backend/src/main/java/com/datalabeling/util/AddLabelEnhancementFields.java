package com.datalabeling.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 添加 labels 表的增强功能字段
 */
public class AddLabelEnhancementFields {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        try {
            System.out.println("开始添加 labels 表的增强功能字段...\n");

            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            Statement stmt = conn.createStatement();

            // 检查并添加 enable_enhancement 字段
            boolean enableEnhancementExists = checkColumnExists(conn, "labels", "enable_enhancement");
            if (!enableEnhancementExists) {
                String sql = "ALTER TABLE labels ADD COLUMN enable_enhancement BIT(1) DEFAULT NULL COMMENT '是否启用二次强化分析'";
                System.out.println("执行: " + sql);
                stmt.execute(sql);
                System.out.println("✓ 添加 enable_enhancement 字段");
            } else {
                System.out.println("- enable_enhancement 字段已存在");
            }

            // 检查并添加 enhancement_config 字段
            boolean enhancementConfigExists = checkColumnExists(conn, "labels", "enhancement_config");
            if (!enhancementConfigExists) {
                String sql = "ALTER TABLE labels ADD COLUMN enhancement_config JSON COMMENT '二次强化分析配置'";
                System.out.println("执行: " + sql);
                stmt.execute(sql);
                System.out.println("✓ 添加 enhancement_config 字段");
            } else {
                System.out.println("- enhancement_config 字段已存在");
            }

            // 检查并添加 preprocessing_mode 字段
            boolean preprocessingModeExists = checkColumnExists(conn, "labels", "preprocessing_mode");
            if (!preprocessingModeExists) {
                String sql = "ALTER TABLE labels ADD COLUMN preprocessing_mode VARCHAR(20) DEFAULT 'none' COMMENT '预处理模式: none/rule/llm/hybrid'";
                System.out.println("执行: " + sql);
                stmt.execute(sql);
                System.out.println("✓ 添加 preprocessing_mode 字段");
            } else {
                System.out.println("- preprocessing_mode 字段已存在");
            }

            // 检查并添加 preprocessor_config 字段
            boolean preprocessorConfigExists = checkColumnExists(conn, "labels", "preprocessor_config");
            if (!preprocessorConfigExists) {
                String sql = "ALTER TABLE labels ADD COLUMN preprocessor_config JSON COMMENT '预处理配置'";
                System.out.println("执行: " + sql);
                stmt.execute(sql);
                System.out.println("✓ 添加 preprocessor_config 字段");
            } else {
                System.out.println("- preprocessor_config 字段已存在");
            }

            // 检查并添加 prompt_id 字段
            boolean promptIdExists = checkColumnExists(conn, "labels", "prompt_id");
            if (!promptIdExists) {
                String sql = "ALTER TABLE labels ADD COLUMN prompt_id INT COMMENT '自定义提示词ID'";
                System.out.println("执行: " + sql);
                stmt.execute(sql);
                System.out.println("✓ 添加 prompt_id 字段");
            } else {
                System.out.println("- prompt_id 字段已存在");
            }

            stmt.close();
            conn.close();

            System.out.println("\n========================================");
            System.out.println("labels 表字段添加完成！");
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("添加字段失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean checkColumnExists(Connection conn, String tableName, String columnName) throws Exception {
        java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName);
        boolean exists = rs.next();
        rs.close();
        return exists;
    }
}
