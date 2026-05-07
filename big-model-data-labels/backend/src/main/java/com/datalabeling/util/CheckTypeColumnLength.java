package com.datalabeling.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 检查 labels 表 type 字段长度
 */
public class CheckTypeColumnLength {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            Statement stmt = conn.createStatement();

            java.sql.ResultSet rs = stmt.executeQuery(
                "SELECT CHARACTER_MAXIMUM_LENGTH as len FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'labels' AND column_name = 'type'"
            );

            if (rs.next()) {
                int length = rs.getInt("len");
                System.out.println("========================================");
                System.out.println("labels.type 字段长度检查");
                System.out.println("========================================");
                System.out.println("当前长度: " + length);

                if (length >= 30) {
                    System.out.println("状态: ✅ 字段长度足够，支持 structured_extraction");
                } else {
                    System.out.println("状态: ❌ 字段长度不足，需要修复");
                    System.out.println("");
                    System.out.println("修复 SQL:");
                    System.out.println("ALTER TABLE labels MODIFY COLUMN type VARCHAR(30) NOT NULL");
                }
                System.out.println("========================================");
            } else {
                System.out.println("未找到 type 字段");
            }

            rs.close();
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.err.println("检查失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
