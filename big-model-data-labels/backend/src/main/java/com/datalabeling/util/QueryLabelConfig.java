package com.datalabeling.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 查询标签配置
 */
public class QueryLabelConfig {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        String labelName = args.length > 0 ? args[0] : "";

        if (labelName.isEmpty()) {
            System.out.println("请提供标签名称作为参数");
            return;
        }

        try {
            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            Statement stmt = conn.createStatement();

            // 查询标签配置
            java.sql.ResultSet rs = stmt.executeQuery(
                "SELECT id, name, type, extractor_config, extract_fields, focus_columns " +
                "FROM labels " +
                "WHERE name LIKE '%" + labelName + "%' " +
                "ORDER BY version DESC"
            );

            System.out.println("=== 标签配置查询结果 ===");
            System.out.println();

            boolean found = false;
            while (rs.next()) {
                found = true;
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String type = rs.getString("type");
                String extractorConfig = rs.getString("extractor_config");
                String extractFields = rs.getString("extract_fields");
                String focusColumns = rs.getString("focus_columns");

                System.out.println("标签ID: " + id);
                System.out.println("标签名称: " + name);
                System.out.println("标签类型: " + type);
                System.out.println("extractor_config: " + extractorConfig);
                System.out.println("extract_fields: " + extractFields);
                System.out.println("focus_columns: " + focusColumns);
                System.out.println("---");

                // 解析extractorConfig
                if (extractorConfig != null && !extractorConfig.isEmpty()) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode config = mapper.readTree(extractorConfig);
                        System.out.println("解析配置:");
                        System.out.println("  extractorType: " + config.path("extractorType").asText());

                        if (config.has("options")) {
                            com.fasterxml.jackson.databind.JsonNode options = config.path("options");
                            System.out.println("  include18Digit: " + options.path("include18Digit").asBoolean());
                            System.out.println("  include15Digit: " + options.path("include15Digit").asBoolean());
                            System.out.println("  includeLoose: " + options.path("includeLoose").asBoolean());
                        }
                    } catch (Exception e) {
                        System.out.println("配置解析失败: " + e.getMessage());
                    }
                }
                System.out.println();
                System.out.println();
            }

            if (!found) {
                System.out.println("未找到标签: " + labelName);
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
