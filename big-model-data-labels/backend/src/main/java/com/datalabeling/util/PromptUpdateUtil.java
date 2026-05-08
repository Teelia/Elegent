package com.datalabeling.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 提示词更新工具
 */
public class PromptUpdateUtil {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);

            // 读取 SQL 文件
            String sqlFile = "src/main/resources/db/migration/sql/V20251230__update_prompts_with_detailed_rules.sql";
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

            int count = 0;
            for (String sql : sqlStatements) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    System.out.println("执行 SQL: " + sql.substring(0, Math.min(80, sql.length())) + "...");
                    stmt.execute(sql);
                    count++;
                }
            }

            stmt.close();
            conn.close();

            System.out.println("\n========================================");
            System.out.println("提示词更新完成！共执行 " + count + " 条 SQL");
            System.out.println("========================================\n");

            // 验证结果
            PromptVerificationUtil.main(args);

        } catch (Exception e) {
            System.err.println("更新失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
