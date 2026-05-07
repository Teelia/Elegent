package com.datalabeling.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 查询任务相关表的结构和数据
 */
public class QueryTaskTableStructure {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            Statement stmt = conn.createStatement();

            // 1. 查询 analysis_task_labels 表结构
            System.out.println("=== analysis_task_labels 表结构 ===");
            java.sql.ResultSet rs = stmt.executeQuery("DESCRIBE analysis_task_labels");
            while (rs.next()) {
                System.out.println(rs.getString("Field") + " - " + rs.getString("Type"));
            }
            rs.close();
            System.out.println();

            // 2. 查询所有任务标签关联数据
            System.out.println("=== analysis_task_labels 所有数据 ===");
            rs = stmt.executeQuery("SELECT * FROM analysis_task_labels");
            while (rs.next()) {
                // 获取所有列
                java.sql.ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                for (int i = 1; i <= colCount; i++) {
                    System.out.println(meta.getColumnName(i) + ": " + rs.getString(i));
                }
                System.out.println("---");
            }
            rs.close();
            System.out.println();

            // 3. 查询 analysis_tasks 表结构
            System.out.println("=== analysis_tasks 表结构 ===");
            rs = stmt.executeQuery("DESCRIBE analysis_tasks");
            while (rs.next()) {
                System.out.println(rs.getString("Field") + " - " + rs.getString("Type"));
            }
            rs.close();
            System.out.println();

            // 4. 查询任务20的标签信息
            System.out.println("=== 任务20信息 ===");
            rs = stmt.executeQuery("SELECT id, name, status, label_ids FROM analysis_tasks WHERE id = 20");
            while (rs.next()) {
                System.out.println("id: " + rs.getString("id"));
                System.out.println("name: " + rs.getString("name"));
                System.out.println("status: " + rs.getString("status"));
                System.out.println("label_ids: " + rs.getString("label_ids"));
            }
            rs.close();

            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.err.println("查询失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
