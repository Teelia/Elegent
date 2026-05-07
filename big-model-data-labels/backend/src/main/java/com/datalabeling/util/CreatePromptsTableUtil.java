package com.datalabeling.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 创建 system_prompts 表
 */
public class CreatePromptsTableUtil {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        try {
            System.out.println("开始执行 system_prompts 表创建脚本...\n");

            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);

            // 读取 SQL 文件
            String sqlFile = "src/main/resources/db/migration/sql/V20251230__create_system_prompts_table.sql";
            BufferedReader reader = new BufferedReader(new FileReader(sqlFile));

            StringBuilder sqlBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                // 跳过注释行
                if (line.trim().startsWith("--")) {
                    continue;
                }
                sqlBuilder.append(line).append("\n");
            }
            reader.close();

            // 分割并执行 SQL 语句
            String[] sqlStatements = sqlBuilder.toString().split(";");
            Statement stmt = conn.createStatement();

            for (String sql : sqlStatements) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    System.out.println("执行 SQL: " + sql.substring(0, Math.min(100, sql.length())) + "...");
                    stmt.execute(sql);
                }
            }

            stmt.close();
            conn.close();

            System.out.println("\n========================================");
            System.out.println("system_prompts 表创建成功！");
            System.out.println("========================================");

            // 现在执行更新脚本
            System.out.println("\n开始执行提示词更新脚本...\n");
            PromptUpdateUtil.main(args);

        } catch (Exception e) {
            System.err.println("创建失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
